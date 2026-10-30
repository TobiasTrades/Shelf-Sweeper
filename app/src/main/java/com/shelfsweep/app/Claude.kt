package com.shelfsweep.app

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClaudeError(val code: Int, message: String) : Exception(message)

object Models {
    const val FAST = "claude-haiku-5-5"
    const val CAREFUL = "claude-sonnet-5-5"
}

object Claude {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Sends images + a prompt and returns the JSON object Claude replies with. Blocking: call off the main thread. */
    fun askJson(apiKey: String, model: String, prompt: String, images: List<ByteArray>, maxTokens: Int = 4096): JSONObject {
        val content = JSONArray()
        images.forEach { bytes ->
            content.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg")
                        .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
                ),
            )
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(txt).getJSONObject("error").getString("message") }
                    .getOrDefault("HTTP ${resp.code}")
                throw ClaudeError(resp.code, msg)
            }
            val arr = JSONObject(txt).optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                if (c.optString("type") == "text") sb.append(c.optString("text"))
            }
            val s = sb.toString()
            val a = s.indexOf('{')
            val b = s.lastIndexOf('}')
            if (a < 0 || b <= a) throw ClaudeError(0, "Claude's answer couldn't be read")
            return JSONObject(s.substring(a, b + 1))
        }
    }

    fun friendly(e: Throwable): String = when {
        e is ClaudeError && e.code == 401 -> "Your API key was rejected. Check it in Settings."
        e is ClaudeError && e.code == 400 && e.message.orEmpty().contains("credit", true) -> "Your Anthropic account is out of credit."
        e is ClaudeError && (e.code == 429 || e.code == 529 || e.code == 503) -> "Claude is busy. Try again in a moment."
        e is ClaudeError -> e.message ?: "Scan failed"
        e is java.io.IOException -> "No internet connection"
        else -> "Scan failed"
    }

    fun scanPrompt(min: Double, known: List<String>): String {
        val knownLine = if (known.isEmpty()) "" else
            "\nItems already logged nearby (if you see one of these same physical items again, use EXACTLY this name so it can be matched): " +
                known.take(40).joinToString("; ") + "\n"
        return """
You are pricing items for a reseller walking through a thrift store or garage sale. This is one camera frame of a shelf, cabinet, table, rack or bin.
Identify every distinct item that could be resold (up to 25). Treat a matching set (e.g. 4 identical plates) as one item with the count in the name.
For each, estimate what it typically SELLS for on eBay in used condition (recent sold listings, not asking prices), in US dollars, as a low/typical/high range. Be realistic and conservative. Read any visible maker marks, brands, pattern names or labels. If you can't identify it well, give a generic name, a wide range and confidence "low".
SKIP anything that typically sells for under ${'$'}${min.toInt()}. Do not list it at all.
POSITION: "x" and "y" are the item's center as a percent of the image width and height (0-100, 0 = left/top), so a price tag can be pinned on it.
Give the best eBay search phrase for sold comps, and "where" in plain words ("top shelf, second from left").
DEMAND: "listed" = roughly how many comparable listings are for sale on eBay right now (a round number); "sell_through" = the chance in percent (0-100) that one listed at the typical price sells within about 90 days, i.e. sold / (sold + still listed). Common items with hundreds sitting are low (under 25); sought-after items with few listings are high (60+).
VERIFY: set "verify" when a closer look could change the value a lot: blurry or partly hidden, value depends on a maker's mark, backstamp, signature, hallmark, label or model number you can't read, or fakes are common. "ask" tells the shopper exactly what close-up to take in one short sentence. Otherwise null.
Also give "scene": a 2-4 word name for what the frame shows ("China cabinet", "Glassware shelf"), and "photo_issue": one sentence if the frame is too blurry, dark or far away to identify things, else null.$knownLine
Reply with only JSON in this shape:
{"scene":"China cabinet","photo_issue":null,"items":[{"name":"Lenox Holiday dinner plates, set of 4","details":"maker, pattern, size, visible condition","where":"top shelf, middle","x":50,"y":26,"low":70,"typical":110,"high":140,"confidence":"high","ebay_query":"Lenox Holiday dinner plates set of 4","listed":150,"sell_through":48,"verify":null}]}
If nothing qualifies, reply with "items":[].
""".trim()
    }

    fun verifyPrompt(it: Item): String {
        val was = JSONObject()
            .put("name", it.name).put("details", it.details).put("low", it.low).put("typical", it.typ)
            .put("high", it.high).put("confidence", it.conf).put("what_was_asked", it.verify?.ask ?: "a closer look")
        return """
You are double-checking ONE item for a reseller in a thrift store. From a wide shelf photo it was identified as:
$was
The attached image is a close-up the shopper just took of that item (its base, label, maker's mark, signature or model number).
Read every marking you can. Decide whether it confirms the identification, changes it, or shows a reproduction or knock-off. Note visible damage (chips, cracks, crazing, repairs) that lowers the price. Then give an updated eBay sold-price estimate in US dollars for used condition, plus "listed" (roughly how many comparable listings are for sale on eBay now) and "sell_through" (chance in percent that one at the typical price sells within about 90 days).
If the close-up is still too blurry or shows the wrong thing, use verdict "unclear" and set "verify" with what to photograph next. Otherwise "verify": null.
Reply with only JSON:
{"verdict":"confirmed","note":"One or two plain sentences on what the markings show and why the price is what it is.","name":"...","details":"...","low":0,"typical":0,"high":0,"confidence":"high","ebay_query":"...","listed":0,"sell_through":0,"verify":null}
verdict is one of: confirmed, revised, reproduction, unclear.
""".trim()
    }
}
