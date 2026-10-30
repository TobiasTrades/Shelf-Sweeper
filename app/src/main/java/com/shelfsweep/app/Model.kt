package com.shelfsweep.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class VerifyAsk(val reason: String, val ask: String)
data class Verified(val verdict: String, val note: String)

/** A price tag placed on a photo: which item, and where (percent of width/height). */
data class Pin(val itemId: String, val x: Float, val y: Float)

data class Item(
    val id: String,
    val shotId: String,
    val x: Float?,
    val y: Float?,
    val name: String,
    val details: String,
    val where: String,
    val low: Double,
    val typ: Double,
    val high: Double,
    val conf: String,
    val query: String,
    val at: Long,
    val grab: Boolean = false,
    val pass: Boolean = false,
    val tag: Double? = null,
    val str: Int? = null,
    val listed: Int? = null,
    val realSold: Int? = null,
    val realListed: Int? = null,
    val verify: VerifyAsk? = null,
    val verified: Verified? = null,
    val checking: Boolean = false,
)

data class Shot(
    val id: String,
    val at: Long,
    val scene: String? = null,
    val status: String = "working",   // working | done | error
    val err: String? = null,
    val issue: String? = null,
    val pins: List<Pin> = emptyList(),
    val aspect: Float = 0.75f,          // width / height
)

data class Settings(
    val apiKey: String = "",
    val target: Double = 100.0,
    val min: Double = 10.0,
    val fee: Double = 13.25,
    val live: Boolean = true,
    val careful: Boolean = false,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
)

data class Demand(val p: Int, val listed: Int?, val sold: Int?, val real: Boolean)

fun Item.demand(): Demand? {
    val rs = realSold
    val rl = realListed
    if (rs != null && rl != null && rs + rl > 0) {
        return Demand((100.0 * rs / (rs + rl)).roundToInt(), rl, rs, true)
    }
    val s = str ?: return null
    val l = listed
    val sold = if (l != null && s < 100) (l * s / (100.0 - s)).roundToInt() else null
    return Demand(s, l, sold, false)
}

fun gradeOf(p: Int): String = when {
    p >= 60 -> "Sells fast"
    p >= 30 -> "Steady"
    else -> "Slow seller"
}

fun Item.net(feePct: Double): Double? = tag?.let { typ * (1 - feePct / 100.0) - it }

fun money(v: Double): String = "$" + String.format("%,d", v.roundToInt())

private val STOP = setOf("of", "the", "a", "an", "and", "with", "set", "in", "on", "for")
fun tokens(s: String): Set<String> =
    s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(" ").filter { it.isNotEmpty() && it !in STOP }.toSet()

fun similarity(a: String, b: String): Double {
    val ta = tokens(a)
    val tb = tokens(b)
    if (ta.isEmpty() || tb.isEmpty()) return 0.0
    val inter = ta.intersect(tb).size
    return inter.toDouble() / (ta.size + tb.size - inter)
}

// ---------- JSON (de)serialization ----------

private fun JSONObject.optNullableDouble(k: String): Double? =
    if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

private fun JSONObject.optNullableInt(k: String): Int? =
    if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() }?.roundToInt() else null

private fun JSONObject.optNullableString(k: String): String? =
    if (has(k) && !isNull(k)) optString(k) else null

fun Item.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("shotId", shotId); put("x", x ?: JSONObject.NULL); put("y", y ?: JSONObject.NULL)
    put("name", name); put("details", details); put("where", where)
    put("low", low); put("typ", typ); put("high", high); put("conf", conf); put("query", query); put("at", at)
    put("grab", grab); put("pass", pass); put("tag", tag ?: JSONObject.NULL)
    put("str", str ?: JSONObject.NULL); put("listed", listed ?: JSONObject.NULL)
    put("realSold", realSold ?: JSONObject.NULL); put("realListed", realListed ?: JSONObject.NULL)
    put("verify", verify?.let { JSONObject().put("reason", it.reason).put("ask", it.ask) } ?: JSONObject.NULL)
    put("verified", verified?.let { JSONObject().put("verdict", it.verdict).put("note", it.note) } ?: JSONObject.NULL)
}

fun itemFromJson(o: JSONObject): Item = Item(
    id = o.getString("id"),
    shotId = o.getString("shotId"),
    x = o.optNullableDouble("x")?.toFloat(),
    y = o.optNullableDouble("y")?.toFloat(),
    name = o.optString("name"),
    details = o.optString("details"),
    where = o.optString("where"),
    low = o.optDouble("low", 0.0),
    typ = o.optDouble("typ", 0.0),
    high = o.optDouble("high", 0.0),
    conf = o.optString("conf", "low"),
    query = o.optString("query"),
    at = o.optLong("at"),
    grab = o.optBoolean("grab"),
    pass = o.optBoolean("pass"),
    tag = o.optNullableDouble("tag"),
    str = o.optNullableInt("str"),
    listed = o.optNullableInt("listed"),
    realSold = o.optNullableInt("realSold"),
    realListed = o.optNullableInt("realListed"),
    verify = o.optJSONObject("verify")?.let { VerifyAsk(it.optString("reason"), it.optString("ask")) },
    verified = o.optJSONObject("verified")?.let { Verified(it.optString("verdict"), it.optString("note")) },
)

fun Shot.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("at", at); put("scene", scene ?: JSONObject.NULL); put("status", status)
    put("err", err ?: JSONObject.NULL); put("issue", issue ?: JSONObject.NULL); put("aspect", aspect.toDouble())
    put("pins", JSONArray().apply { pins.forEach { put(JSONObject().put("itemId", it.itemId).put("x", it.x.toDouble()).put("y", it.y.toDouble())) } })
}

fun shotFromJson(o: JSONObject): Shot {
    val pa = o.optJSONArray("pins") ?: JSONArray()
    val pins = (0 until pa.length()).map { i ->
        val p = pa.getJSONObject(i)
        Pin(p.getString("itemId"), p.optDouble("x", 50.0).toFloat(), p.optDouble("y", 50.0).toFloat())
    }
    val status = o.optString("status", "done").let { if (it == "working") "error" else it }
    return Shot(
        id = o.getString("id"), at = o.optLong("at"), scene = o.optNullableString("scene"),
        status = status, err = o.optNullableString("err") ?: if (status == "error") "Scan was interrupted" else null,
        issue = o.optNullableString("issue"), pins = pins, aspect = o.optDouble("aspect", 0.75).toFloat(),
    )
}
