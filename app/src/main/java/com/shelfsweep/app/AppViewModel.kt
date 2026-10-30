package com.shelfsweep.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Deck(val ids: List<String>, val idx: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val prefs = ctx.getSharedPreferences("shelf_sweeper", Context.MODE_PRIVATE)
    private val shotDir = File(ctx.filesDir, "shots").apply { mkdirs() }
    private val stateFile = File(ctx.filesDir, "state.json")

    var settings by mutableStateOf(loadSettings())
        private set
    val shots = mutableStateListOf<Shot>()
    val items = mutableStateListOf<Item>()

    var tab by mutableStateOf("scan")
    var livePins by mutableStateOf<List<Pin>>(emptyList())
    var liveShotId by mutableStateOf<String?>(null)
    var busy by mutableStateOf(0)
    var banner by mutableStateOf<String?>(null)
    val alerts = mutableStateListOf<String>()
    var deck by mutableStateOf<Deck?>(null)
    var closeUpFor by mutableStateOf<String?>(null)
    var viewShot by mutableStateOf<String?>(null)
    var showSettings by mutableStateOf(false)
    var cameraGranted by mutableStateOf(false)

    // read from the camera thread
    @Volatile var lastScanGrid: IntArray? = null
    @Volatile var lastScanAt: Long = 0L
    private val captureRequested = AtomicBoolean(false)
    private val inFlight = AtomicInteger(0)
    private val sem = Semaphore(2)

    init {
        loadState()
    }

    // ---------- settings ----------
    private fun loadSettings() = Settings(
        apiKey = prefs.getString("apiKey", "") ?: "",
        target = prefs.getFloat("target", 100f).toDouble(),
        min = prefs.getFloat("min", 10f).toDouble(),
        fee = prefs.getFloat("fee", 13.25f).toDouble(),
        live = prefs.getBoolean("live", true),
        careful = prefs.getBoolean("careful", false),
        sound = prefs.getBoolean("sound", true),
        vibrate = prefs.getBoolean("vibrate", true),
    )

    fun updateSettings(s: Settings) {
        settings = s
        prefs.edit()
            .putString("apiKey", s.apiKey.trim())
            .putFloat("target", s.target.toFloat())
            .putFloat("min", s.min.toFloat())
            .putFloat("fee", s.fee.toFloat())
            .putBoolean("live", s.live)
            .putBoolean("careful", s.careful)
            .putBoolean("sound", s.sound)
            .putBoolean("vibrate", s.vibrate)
            .apply()
    }

    // ---------- persistence ----------
    private fun loadState() {
        runCatching {
            if (!stateFile.exists()) return
            val o = JSONObject(stateFile.readText())
            val sa = o.optJSONArray("shots") ?: JSONArray()
            val ia = o.optJSONArray("items") ?: JSONArray()
            for (i in 0 until sa.length()) shots.add(shotFromJson(sa.getJSONObject(i)))
            for (i in 0 until ia.length()) items.add(itemFromJson(ia.getJSONObject(i)))
        }
    }

    private fun save() {
        val snapshot = JSONObject()
            .put("shots", JSONArray().apply { shots.forEach { put(it.toJson()) } })
            .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
            .toString()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val tmp = File(stateFile.path + ".tmp")
                tmp.writeText(snapshot)
                tmp.renameTo(stateFile)
            }
        }
    }

    fun newHunt() {
        shots.clear(); items.clear(); alerts.clear()
        livePins = emptyList(); liveShotId = null; deck = null; viewShot = null; closeUpFor = null
        lastScanGrid = null
        viewModelScope.launch(Dispatchers.IO) { shotDir.listFiles()?.forEach { it.delete() } }
        save()
    }

    // ---------- lookups ----------
    fun item(id: String): Item? = items.firstOrNull { it.id == id }
    fun shot(id: String): Shot? = shots.firstOrNull { it.id == id }
    fun shotFile(id: String) = File(shotDir, "$id.jpg")
    fun itemsOfShot(s: Shot): List<Item> =
        s.pins.mapNotNull { p -> item(p.itemId) }.distinctBy { it.id }.sortedByDescending { it.typ }

    fun updateItem(id: String, f: (Item) -> Item) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = f(items[i]); save() }
    }

    private fun updateShot(id: String, f: (Shot) -> Shot) {
        val i = shots.indexOfFirst { it.id == id }
        if (i >= 0) shots[i] = f(shots[i])
    }

    fun deleteItem(id: String) {
        items.removeAll { it.id == id }
        alerts.remove(id)
        livePins = livePins.filter { it.itemId != id }
        save()
    }

    fun needsCheck(it: Item) = it.verify != null || it.checking

    // ---------- camera hooks (called from the camera thread) ----------
    fun requestCapture() { captureRequested.set(true) }
    fun consumeCaptureRequest(): Boolean = captureRequested.getAndSet(false)
    fun canAutoScan(): Boolean = settings.apiKey.isNotBlank() && inFlight.get() < 2

    fun onViewChanged() {
        if (livePins.isNotEmpty()) viewModelScope.launch(Dispatchers.Main) { livePins = emptyList(); liveShotId = null }
    }

    fun onFrameCaptured(jpeg: ByteArray, aspect: Float, grid: IntArray) {
        lastScanGrid = grid
        lastScanAt = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.Main) {
            val forItem = closeUpFor
            if (forItem != null) {
                closeUpFor = null
                verify(forItem, jpeg)
            } else {
                scan(jpeg, aspect)
            }
        }
    }

    // ---------- scanning ----------
    private fun scan(jpeg: ByteArray, aspect: Float) {
        val key = settings.apiKey
        if (key.isBlank()) { banner = "Add your Claude API key in Settings first."; return }
        val shot = Shot(id = UUID.randomUUID().toString().take(10), at = System.currentTimeMillis(), aspect = aspect)
        shots.add(shot)
        inFlight.incrementAndGet(); busy = inFlight.get()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { shotFile(shot.id).writeBytes(jpeg) } }
            val s = settings
            val recentNames = items.filter { System.currentTimeMillis() - it.at < 20 * 60_000 }.map { it.name }.distinct()
            try {
                val res = sem.withPermit {
                    withContext(Dispatchers.IO) {
                        Claude.askJson(key, if (s.careful) Models.CAREFUL else Models.FAST, Claude.scanPrompt(s.min, recentNames), listOf(jpeg))
                    }
                }
                applyScan(shot.id, res, s)
            } catch (e: Throwable) {
                updateShot(shot.id) { it.copy(status = "error", err = Claude.friendly(e)) }
                banner = Claude.friendly(e)
                save()
            } finally {
                inFlight.decrementAndGet(); busy = inFlight.get()
            }
        }
    }

    private fun applyScan(shotId: String, res: JSONObject, s: Settings) {
        val arr = res.optJSONArray("items") ?: JSONArray()
        val pins = mutableListOf<Pin>()
        val fresh = mutableListOf<Item>()
        val now = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim().take(120)
            val typ = o.optDouble("typical", 0.0).let { if (it.isNaN()) 0.0 else it }
            if (name.isEmpty() || typ < s.min) continue
            val x = o.optDouble("x", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(4f, 96f)
            val y = o.optDouble("y", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(5f, 95f)
            // same physical item seen again recently? pin the existing one instead of logging a duplicate
            val match = items.filter { now - it.at < 30 * 60_000 }
                .maxByOrNull { similarity(it.name, name) }
                ?.takeIf { similarity(it.name, name) >= 0.6 }
            if (match != null) {
                if (x != null && y != null) pins.add(Pin(match.id, x, y))
                continue
            }
            val v = o.optJSONObject("verify")?.let { vv -> vv.optString("ask").takeIf { it.isNotBlank() }?.let { VerifyAsk(vv.optString("reason", "check"), it.take(160)) } }
            val newItem = Item(
                id = UUID.randomUUID().toString().take(10), shotId = shotId, x = x, y = y,
                name = name, details = o.optString("details").take(200), where = o.optString("where").take(80),
                low = o.optDouble("low", typ).let { if (it.isNaN()) typ else it }, typ = typ,
                high = o.optDouble("high", typ).let { if (it.isNaN()) typ else it },
                conf = o.optString("confidence", "low").let { if (it in setOf("high", "medium", "low")) it else "low" },
                query = o.optString("ebay_query", name).ifBlank { name }.take(100), at = now + i,
                str = o.optDouble("sell_through", Double.NaN).takeIf { !it.isNaN() }?.roundToInt()?.coerceIn(0, 100),
                listed = o.optDouble("listed", Double.NaN).takeIf { !it.isNaN() && it >= 0 }?.roundToInt(),
                verify = v,
            )
            items.add(newItem); fresh.add(newItem)
            if (x != null && y != null) pins.add(Pin(newItem.id, x, y))
        }
        updateShot(shotId) {
            it.copy(
                status = "done", pins = pins,
                scene = res.optString("scene").takeIf { s -> s.isNotBlank() && s != "null" }?.take(40),
                issue = res.optString("photo_issue").takeIf { s -> s.isNotBlank() && s != "null" }?.take(160),
            )
        }
        // the newest finished scan drives the tags on the live camera
        val newest = shots.filter { it.status == "done" }.maxByOrNull { it.at }
        if (newest?.id == shotId) { livePins = pins; liveShotId = shotId }
        fresh.filter { it.typ >= s.target }.sortedByDescending { it.typ }.forEach { alert(it) }
        save()
    }

    // ---------- authenticate (close-up) ----------
    fun startCloseUp(itemId: String) {
        closeUpFor = itemId
        deck = null; viewShot = null; tab = "scan"
    }

    private fun verify(itemId: String, jpeg: ByteArray) {
        val key = settings.apiKey
        val before = item(itemId) ?: return
        if (key.isBlank()) { banner = "Add your Claude API key in Settings first."; return }
        updateItem(itemId) { it.copy(checking = true) }
        banner = "Checking your close-up…"
        inFlight.incrementAndGet(); busy = inFlight.get()
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Claude.askJson(key, Models.CAREFUL, Claude.verifyPrompt(before), listOf(jpeg), 2048) }
                val verdict = r.optString("verdict").takeIf { it in setOf("confirmed", "revised", "reproduction", "unclear") } ?: "unclear"
                updateItem(itemId) { it0 ->
                    var cur = it0
                    if (verdict != "unclear") {
                        val typ = r.optDouble("typical", Double.NaN)
                        if (!typ.isNaN() && typ >= 0) cur = cur.copy(
                            typ = typ,
                            low = r.optDouble("low", typ).let { v -> if (v.isNaN()) typ else v },
                            high = r.optDouble("high", typ).let { v -> if (v.isNaN()) typ else v },
                        )
                        r.optString("name").takeIf { s -> s.isNotBlank() }?.let { s -> cur = cur.copy(name = s.take(120)) }
                        r.optString("details").takeIf { s -> s.isNotBlank() }?.let { s -> cur = cur.copy(details = s.take(200)) }
                        r.optString("confidence").takeIf { s -> s in setOf("high", "medium", "low") }?.let { s -> cur = cur.copy(conf = s) }
                        r.optString("ebay_query").takeIf { s -> s.isNotBlank() }?.let { s -> cur = cur.copy(query = s.take(100)) }
                        r.optDouble("sell_through", Double.NaN).takeIf { v -> !v.isNaN() }?.let { v -> cur = cur.copy(str = v.roundToInt().coerceIn(0, 100)) }
                        r.optDouble("listed", Double.NaN).takeIf { v -> !v.isNaN() && v >= 0 }?.let { v -> cur = cur.copy(listed = v.roundToInt()) }
                    }
                    val nv = r.optJSONObject("verify")?.let { vv -> vv.optString("ask").takeIf { a -> a.isNotBlank() }?.let { a -> VerifyAsk(vv.optString("reason", "check"), a.take(160)) } }
                    cur.copy(checking = false, verify = nv, verified = Verified(verdict, r.optString("note").take(280)))
                }
                banner = null
                val after = item(itemId)
                if (after != null && after.typ >= settings.target && before.typ < settings.target) alert(after)
                deck = Deck(listOf(itemId), 0)
            } catch (e: Throwable) {
                updateItem(itemId) { it.copy(checking = false) }
                banner = Claude.friendly(e)
            } finally {
                inFlight.decrementAndGet(); busy = inFlight.get()
            }
        }
    }

    // ---------- alerts ----------
    private fun alert(it: Item) {
        alerts.remove(it.id)
        alerts.add(0, it.id)
        while (alerts.size > 2) alerts.removeAt(alerts.size - 1)
        if (settings.vibrate) runCatching {
            val vib: Vibrator = vibrator()
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 70, 120), -1))
        }
        if (settings.sound) runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
            viewModelScope.launch { kotlinx.coroutines.delay(400); tg.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun dismissAlert(id: String) { alerts.remove(id) }

    // ---------- decisions ----------
    fun decide(grab: Boolean) {
        val d = deck ?: return
        val id = d.ids.getOrNull(d.idx) ?: return
        updateItem(id) { it.copy(grab = grab, pass = !grab) }
        alerts.remove(id)
        deck = d.copy(idx = d.idx + 1)
    }

    fun openDeck(ids: List<String>, id: String) {
        val valid = ids.filter { item(it) != null }
        if (valid.isEmpty()) return
        deck = Deck(valid, max(0, valid.indexOf(id)))
    }

    // ---------- images ----------
    fun loadBitmap(shotId: String, maxSide: Int): Bitmap? = runCatching {
        val f = shotFile(shotId)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** A zoomed crop around an item's position, for the item card. */
    fun loadCrop(shotId: String, x: Float?, y: Float?): Bitmap? {
        val full = loadBitmap(shotId, 1400) ?: return null
        if (x == null || y == null) return full
        val w = full.width
        val h = full.height
        val cw = (w * 0.46f).roundToInt()
        val ch = min(h, (cw * 0.62f).roundToInt())
        val cx = (w * x / 100f).roundToInt()
        val cy = (h * y / 100f).roundToInt()
        val left = (cx - cw / 2).coerceIn(0, max(0, w - cw))
        val top = (cy - ch / 2).coerceIn(0, max(0, h - ch))
        return Bitmap.createBitmap(full, left, top, min(cw, w - left), min(ch, h - top))
    }
}
