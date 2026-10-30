package com.shelfsweep.app

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Sharp = RoundedCornerShape(3.dp)

fun soldUrl(q: String) = "https://www.ebay.com/sch/i.html?_nkw=" + URLEncoder.encode(q, "UTF-8") + "&LH_Sold=1&LH_Complete=1&_sop=13"
fun forSaleUrl(q: String) = "https://www.ebay.com/sch/i.html?_nkw=" + URLEncoder.encode(q, "UTF-8") + "&_sop=15"
private fun timeOf(t: Long) = SimpleDateFormat("h:mm a", Locale.US).format(Date(t))
private fun shortName(n: String): String = n.substringBefore(",").let { if (it.length > 32) it.take(30) + "…" else it }

// ============================================================ root

@Composable
fun ShelfSweeperApp(vm: AppViewModel, onAskCamera: () -> Unit) {
    BackHandler(enabled = vm.deck != null || vm.showSettings || vm.viewShot != null || vm.closeUpFor != null || vm.tab != "scan") {
        when {
            vm.showSettings -> vm.showSettings = false
            vm.deck != null -> vm.deck = null
            vm.viewShot != null -> vm.viewShot = null
            vm.closeUpFor != null -> vm.closeUpFor = null
            else -> vm.tab = "scan"
        }
    }
    Box(Modifier.fillMaxSize().background(Navy)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm)
            Box(Modifier.weight(1f)) {
                when (vm.tab) {
                    "scan" -> ScanScreen(vm, onAskCamera)
                    "shelves" -> ShelvesScreen(vm)
                    else -> ReceiptScreen(vm)
                }
            }
        }
        vm.viewShot?.let { ShelfDetail(vm, it) }
        vm.deck?.let { ItemDeck(vm, it) }
        if (vm.showSettings) SettingsScreen(vm)
        AlertBanners(vm, Modifier.align(Alignment.TopCenter))
        MessageBar(vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopBar(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 16.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Shelf Sweeper", fontFamily = Serif, fontSize = 23.sp, color = Ivory)
                Text("FIELD APPRAISAL", fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.ExtraBold, color = Gold)
            }
            Spacer(Modifier.weight(1f))
            if (vm.busy > 0) {
                Text(
                    "Scanning ${vm.busy}", fontSize = 12.sp, color = Steady, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(GoldSoft, Sharp).padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "Settings", fontSize = 13.sp, color = Ivory, fontWeight = FontWeight.Bold,
                modifier = Modifier.border(1.dp, Line, Sharp).clickable { vm.showSettings = true }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            val nShelves = vm.shots.count { it.pins.isNotEmpty() }
            listOf("scan" to "SCAN", "shelves" to "SHELVES" + if (nShelves > 0) " $nShelves" else "", "receipt" to "RECEIPT" + if (vm.items.isNotEmpty()) " ${vm.items.size}" else "")
                .forEach { (key, label) ->
                    val on = vm.tab == key
                    Column(Modifier.clickable { vm.tab = key; vm.viewShot = null }) {
                        Text(label, fontSize = 12.5.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.ExtraBold, color = if (on) Ivory else Muted, modifier = Modifier.padding(vertical = 6.dp))
                        Box(Modifier.height(2.dp).fillMaxWidth().background(if (on) Gold else Color.Transparent))
                    }
                }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

// ============================================================ shared pieces

@Composable
fun LotTag(lot: Int, it: Item, target: Double, modifier: Modifier = Modifier) {
    val hit = it.typ >= target && !it.grab
    val bg = when {
        it.grab -> CartTag
        hit -> Navy
        else -> Ivory
    }
    val fg = if (it.grab || hit) Ivory else Navy
    val border = when {
        hit -> Gold
        it.verify != null -> Check
        else -> Color.Transparent
    }
    Column(
        modifier.background(bg, RoundedCornerShape(2.dp)).border(1.dp, border, RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "LOT $lot" + when { it.grab -> " ✓"; it.verify != null -> " ?"; else -> "" },
            fontSize = 8.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold, color = if (hit) Gold else fg,
        )
        Text(money(it.typ), fontFamily = Serif, fontSize = 15.sp, color = fg)
    }
}

/** Places lot tags over a photo or the live camera. Pins are percent positions. */
@Composable
fun PinsLayer(vm: AppViewModel, pins: List<Pin>) {
    val pairs = pins.mapNotNull { p -> vm.item(p.itemId)?.let { p to it } }
    val order = pairs.map { it.second }.distinctBy { it.id }.sortedByDescending { it.typ }.map { it.id }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        pairs.distinctBy { pr -> pr.second.id }.forEach { (p, item) ->
            LotTag(
                order.indexOf(item.id) + 1, item, vm.settings.target,
                Modifier.offset(x = maxWidth * (p.x / 100f) - 30.dp, y = maxHeight * (p.y / 100f) - 18.dp)
                    .clickable { vm.openDeck(order, item.id) },
            )
        }
    }
}

@Composable
fun DemandLine(it: Item) {
    val d = it.demand() ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(d.p / 100f).background(gradeColor(d.p)))
        }
        Spacer(Modifier.width(6.dp))
        Text("${d.p}%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = gradeColor(d.p))
        Text(" ${gradeOf(d.p)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = gradeColor(d.p))
        d.listed?.let { l -> Text(" · ${if (d.real) "" else "~"}${String.format("%,d", l)} listed", fontSize = 12.sp, color = Muted) }
        if (d.real) Text(
            " YOUR COUNT", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Navy,
            modifier = Modifier.padding(start = 6.dp).background(Gold, Sharp).padding(horizontal = 4.dp),
        )
    }
}

@Composable
fun ItemRow(lot: Int, it: Item, target: Double, onClick: () -> Unit) {
    val sub = when {
        it.checking -> "Checking your close-up…"
        it.verify != null -> "Authenticate: " + it.verify.ask
        else -> it.where.ifBlank { it.details } + when { it.grab -> " · in cart"; it.pass -> " · passed"; else -> "" }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(lot.toString().padStart(2, '0'), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(24.dp).padding(top = 3.dp))
        Column(Modifier.weight(1f)) {
            Text(it.name, color = if (it.pass) Muted else Ivory, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Text(sub, color = if (it.verify != null || it.checking) Check else Muted, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            DemandLine(it)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(money(it.typ), fontFamily = Serif, fontSize = 18.sp, color = if (it.typ >= target) Gold else Ivory)
            Text("${money(it.low)}–${money(it.high)}", fontSize = 11.sp, color = Muted)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

@Composable
fun rememberShotBitmap(vm: AppViewModel, shotId: String, maxSide: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, shotId, maxSide) {
        value = withContext(Dispatchers.IO) { vm.loadBitmap(shotId, maxSide)?.asImageBitmap() }
    }
    return state.value
}

@Composable
private fun GoldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, shape = Sharp, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy), modifier = modifier) {
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LineButton(text: String, modifier: Modifier = Modifier, color: Color = Ivory, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = Sharp, border = BorderStroke(1.dp, Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = color), modifier = modifier) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

// ============================================================ scan

@Composable
private fun ScanScreen(vm: AppViewModel, onAskCamera: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Stage).padding(14.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(3f / 4f).border(1.dp, Gold).padding(4.dp)) {
                if (vm.cameraGranted) {
                    CameraPreview(vm, Modifier.fillMaxSize())
                    PinsLayer(vm, vm.livePins)
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Shelf Sweeper needs the camera to scan shelves.", color = Ivory, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                        GoldButton("Allow camera", onClick = onAskCamera)
                    }
                }
                val status = when {
                    vm.closeUpFor != null -> "CLOSE-UP: " + (vm.item(vm.closeUpFor!!)?.let { shortName(it.name) } ?: "") + " · tap the shutter"
                    vm.settings.apiKey.isBlank() -> "Add your Claude API key in Settings to start"
                    vm.settings.live -> "LIVE · hold steady on a shelf"
                    else -> "Tap the shutter to scan"
                }
                Text(
                    status, color = Ivory, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xB30B1524), Sharp).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        LiveList(vm)
        Dock(vm)
    }
}

@Composable
private fun LiveList(vm: AppViewModel) {
    val shown = vm.livePins.mapNotNull { vm.item(it.itemId) }.distinctBy { it.id }.sortedByDescending { it.typ }
    val total = vm.items.sumOf { it.typ }
    val alerts = vm.items.count { it.typ >= vm.settings.target }
    Column(Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    vm.liveShotId?.let { vm.shot(it)?.scene }?.uppercase() ?: "THIS VIEW",
                    color = Gold, fontSize = 10.5.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(money(shown.sumOf { it.typ }), fontFamily = Serif, fontSize = 28.sp, color = Ivory)
            }
            Spacer(Modifier.weight(1f))
            Text("${money(total)} today\n${vm.items.size} items · $alerts alert${if (alerts == 1) "" else "s"}", color = Muted, fontSize = 12.5.sp, textAlign = TextAlign.End)
        }
        if (shown.isEmpty()) {
            Text(
                if (vm.busy > 0) "Finding items and prices…" else "Point at a shelf and hold still. Lot tags appear on anything worth ${money(vm.settings.min)} or more.",
                color = Muted, fontSize = 13.5.sp, modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(shown, key = { it.id }) { item ->
                    ItemRow(shown.indexOf(item) + 1, item, vm.settings.target) { vm.openDeck(shown.map { it.id }, item.id) }
                }
            }
        }
    }
}

@Composable
private fun Dock(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Stage).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val live = vm.settings.live
        Text(
            if (live) "LIVE\nON" else "LIVE\nOFF", textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 1.sp,
            fontWeight = FontWeight.ExtraBold, color = if (live) Gold else Muted,
            modifier = Modifier.size(56.dp).border(1.dp, if (live) Gold else Line, CircleShape).clip(CircleShape)
                .clickable { vm.updateSettings(vm.settings.copy(live = !live)) }.padding(top = 14.dp),
        )
        Box(
            Modifier.size(76.dp).border(2.dp, Gold, CircleShape).padding(6.dp).clip(CircleShape)
                .background(if (vm.closeUpFor != null) Check else Gold)
                .clickable { vm.tab = "scan"; vm.requestCapture() },
        )
        Text(
            "DONE", fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.ExtraBold, color = Gold,
            modifier = Modifier.border(1.dp, Gold, Sharp).clickable { vm.tab = "receipt" }.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}

// ============================================================ shelves (history)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelvesScreen(vm: AppViewModel) {
    val list = vm.shots.filter { it.pins.isNotEmpty() || it.status != "done" }.sortedByDescending { it.at }
    if (list.isEmpty()) {
        Text("No shelves yet. Every view you scan is kept here so you can look back later.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
        return
    }
    val leftAlerts = vm.items.count { it.typ >= vm.settings.target && !it.grab && !it.pass }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("${list.size} VIEWS SCANNED", color = Gold, fontSize = 10.5.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.ExtraBold)
                    Text(money(vm.items.sumOf { it.typ }), fontFamily = Serif, fontSize = 32.sp, color = Ivory)
                }
                Spacer(Modifier.weight(1f))
                if (leftAlerts > 0) Text("$leftAlerts alert${if (leftAlerts == 1) "" else "s"}\nstill on a shelf", color = Gold, fontSize = 12.5.sp, textAlign = TextAlign.End)
            }
        }
        items(list, key = { it.id }) { s ->
            val its = vm.itemsOfShot(s)
            val missed = its.filter { it.typ >= vm.settings.target && !it.grab && !it.pass }
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Navy2, RoundedCornerShape(4.dp))
                    .border(1.dp, if (missed.isNotEmpty()) Gold else Line, RoundedCornerShape(4.dp)).padding(12.dp),
            ) {
                Row(Modifier.fillMaxWidth().clickable { if (s.pins.isNotEmpty()) vm.viewShot = s.id }, verticalAlignment = Alignment.CenterVertically) {
                    val bmp = rememberShotBitmap(vm, s.id, 200)
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(2.dp)).background(Line)) {
                        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.scene ?: "Shelf view", fontFamily = Serif, fontSize = 18.sp, color = Ivory)
                        Text(
                            when (s.status) { "working" -> "Scanning…"; "error" -> s.err ?: "Scan failed"; else -> "${its.size} lots" } + " · " + timeOf(s.at),
                            color = if (s.status == "error") Bad else Muted, fontSize = 12.5.sp,
                        )
                    }
                    Text(money(its.sumOf { it.typ }), fontFamily = Serif, fontSize = 18.sp, color = Ivory)
                }
                s.issue?.let { Text(it, color = Check, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp)) }
                if (its.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        its.forEach { it ->
                            val hit = it.typ >= vm.settings.target && !it.grab
                            val d = it.demand()
                            Text(
                                (if (it.grab) "✓ " else "") + shortName(it.name) + " " + money(it.typ) + (if (it.verify != null) " ?" else "") + (d?.let { dd -> " · ${dd.p}%" } ?: ""),
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                color = when { hit -> Navy; it.grab -> Navy; it.pass -> Muted; else -> Ivory },
                                modifier = Modifier.background(when { hit -> Gold; it.grab -> Ivory; else -> Navy }, Sharp)
                                    .clickable { vm.openDeck(its.map { x -> x.id }, it.id) }.padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                missed.forEach { m ->
                    Text(
                        "Go back: ${shortName(m.name)}, ${m.where}" + (m.demand()?.let { " · ${it.p}% sell" } ?: ""),
                        color = Gold, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(GoldSoft, Sharp).clickable { vm.openDeck(listOf(m.id), m.id) }.padding(8.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ShelfDetail(vm: AppViewModel, shotId: String) {
    val s = vm.shot(shotId) ?: return
    val its = vm.itemsOfShot(s)
    Column(Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("← Back", color = Gold, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { vm.viewShot = null }.padding(vertical = 6.dp))
        Text((s.scene ?: "Shelf view") + " · " + timeOf(s.at), fontFamily = Serif, fontSize = 22.sp, color = Ivory, modifier = Modifier.padding(vertical = 8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(s.aspect).border(1.dp, Gold).padding(4.dp)) {
            val bmp = rememberShotBitmap(vm, s.id, 1400)
            if (bmp != null) Image(bmp, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            PinsLayer(vm, s.pins)
        }
        Spacer(Modifier.height(10.dp))
        its.forEachIndexed { i, it -> ItemRow(i + 1, it, vm.settings.target) { vm.openDeck(its.map { x -> x.id }, it.id) } }
        Spacer(Modifier.height(40.dp))
    }
}

// ============================================================ receipt (full log)

@Composable
private fun ReceiptScreen(vm: AppViewModel) {
    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("value") }
    val sp: (Item) -> Int = { it.demand()?.p ?: 50 }
    val cmp: Comparator<Item> = when (sort) {
        "bet" -> compareByDescending { it.typ * sp(it) }
        "sell" -> compareByDescending<Item> { sp(it) }.thenByDescending { it.typ }
        else -> compareByDescending { it.typ }
    }
    val keep: (Item) -> Boolean = { when (filter) { "hits" -> it.typ >= vm.settings.target; "check" -> vm.needsCheck(it); else -> true } }
    val cart = vm.items.filter { it.grab }.sortedWith(cmp)
    val left = vm.items.filter { !it.grab }.sortedWith(cmp)
    val cartF = cart.filter(keep)
    val leftF = left.filter(keep)
    val cartV = cart.sumOf { it.typ }
    val fees = cartV * vm.settings.fee / 100
    val tags = cart.sumOf { it.tag ?: 0.0 }
    val best = left.maxByOrNull { it.typ * sp(it) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "All", "hits" to "Alerts", "check" to "To check").forEach { (k, l) ->
                    Text(l, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (filter == k) Navy else Ivory,
                        modifier = Modifier.background(if (filter == k) Gold else Navy2, Sharp).clickable { filter = k }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                val next = mapOf("value" to "bet", "bet" to "sell", "sell" to "value")
                val label = mapOf("value" to "Top value", "bet" to "Best bet", "sell" to "Sells fastest")
                Text((label[sort] ?: "") + " ▾", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gold,
                    modifier = Modifier.border(1.dp, Line, Sharp).clickable { sort = next[sort] ?: "value" }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().border(1.dp, Gold, RoundedCornerShape(4.dp)).padding(10.dp).background(Ivory, RoundedCornerShape(2.dp)).padding(16.dp)) {
                Text("Shelf Sweeper", fontFamily = Serif, fontSize = 22.sp, color = PaperInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                PaperSmall("FIELD APPRAISAL · " + timeOf(System.currentTimeMillis()))
                PaperSmall("ALERT ≥ ${money(vm.settings.target)} · LOG ≥ ${money(vm.settings.min)}")
                if (vm.busy > 0) PaperSmall("STILL SCANNING ${vm.busy} · TOTALS WILL UPDATE", PaperGold)
                Dashes()
                ReceiptHead("IN CART")
                if (cartF.isEmpty()) PaperNote(if (cart.isEmpty()) "Nothing grabbed yet" else "None match this filter")
                cartF.forEach { ReceiptLine(vm, it, cartF.map { x -> x.id }) }
                Dashes()
                ReceiptHead("LEFT ON THE SHELF")
                if (leftF.isEmpty()) PaperNote(if (left.isEmpty()) "Nothing left behind" else "None match this filter")
                leftF.forEach { ReceiptLine(vm, it, leftF.map { x -> x.id }) }
                Dashes()
                TotalLine("CART VALUE", money(cartV))
                TotalLine("EBAY FEES ${vm.settings.fee}%", "-" + money(fees))
                if (tags > 0) TotalLine("TAG PRICES", "-" + money(tags))
                TotalLine("NET PROFIT", money(cartV - fees - tags), bold = true)
                TotalLine("MISSED", money(left.sumOf { it.typ }), bold = true, color = PaperGold, size = 15)
                best?.let { TotalLine("BEST BET LEFT", shortName(it.name).take(16) + " " + money(it.typ) + " @ ${sp(it)}%", color = PaperMuted) }
                Spacer(Modifier.height(8.dp))
                PaperSmall("** over alert · ? authenticate · x passed")
                PaperSmall("SELL = chance to sell within 90 days. Tap a line for details.")
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable private fun PaperSmall(t: String, c: Color = PaperMuted) =
    Text(t, fontFamily = Mono, fontSize = 11.sp, color = c, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@Composable private fun PaperNote(t: String) = Text(t, fontFamily = Mono, fontSize = 12.5.sp, color = PaperMuted, modifier = Modifier.padding(vertical = 2.dp))

@Composable private fun Dashes() =
    Text("- ".repeat(40), fontFamily = Mono, fontSize = 10.sp, color = PaperMuted, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(vertical = 6.dp))

@Composable private fun ReceiptHead(t: String) = Row(Modifier.fillMaxWidth()) {
    Text(t, fontFamily = Mono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PaperInk, modifier = Modifier.weight(1f))
    Text("SELL", fontFamily = Mono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PaperInk, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
    Text("VALUE", fontFamily = Mono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PaperInk, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
}

@Composable private fun ReceiptLine(vm: AppViewModel, it: Item, deck: List<String>) {
    val d = it.demand()
    val hit = it.typ >= vm.settings.target && !it.grab
    val mark = when { hit -> "** "; it.verify != null -> "?  "; it.pass -> "x  "; else -> "" }
    Row(Modifier.fillMaxWidth().clickable { vm.openDeck(deck, it.id) }.padding(vertical = 3.dp)) {
        Text(mark + shortName(it.name), fontFamily = Mono, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (hit) PaperGold else if (it.pass) PaperMuted else PaperInk, fontWeight = if (hit) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(d?.let { "${it.p}%" } ?: "–", fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
            color = d?.let { dd -> when { dd.p >= 60 -> Color(0xFF2F6B45); dd.p >= 30 -> PaperGold; else -> Color(0xFFA23B2F) } } ?: PaperMuted, modifier = Modifier.width(44.dp))
        Text(money(it.typ), fontFamily = Mono, fontSize = 13.sp, textAlign = TextAlign.End, color = PaperInk, modifier = Modifier.width(56.dp))
    }
}

@Composable private fun TotalLine(l: String, r: String, bold: Boolean = false, color: Color = PaperInk, size: Int = 13) = Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
    Text(l, fontFamily = Mono, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color, modifier = Modifier.weight(1f))
    Text(r, fontFamily = Mono, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
}

// ============================================================ item card (swipe deck)

@Composable
private fun ItemDeck(vm: AppViewModel, deck: Deck) {
    val n = deck.ids.size
    Column(Modifier.fillMaxSize().background(Stage).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("← Back", color = Gold, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clickable { vm.deck = null }.padding(vertical = 6.dp))
            Spacer(Modifier.weight(1f))
            Text("${minOf(deck.idx + 1, n)} of $n", color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(3.dp).background(Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(minOf(deck.idx + 1, n).toFloat() / n).background(Gold))
        }
        val cur = deck.ids.getOrNull(deck.idx)?.let { id -> vm.item(id) }
        if (cur == null) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp).background(Navy2, RoundedCornerShape(4.dp)).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("That's the pile", fontFamily = Serif, fontSize = 24.sp, color = Ivory)
                Text("You've gone through every item here.", color = Muted, modifier = Modifier.padding(vertical = 10.dp))
                GoldButton("Back to the list") { vm.deck = null }
            }
            return@Column
        }
        ItemCard(vm, cur)
    }
}

@Composable
private fun ItemCard(vm: AppViewModel, it: Item) {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val offset = remember(it.id) { Animatable(0f) }
    val shot = vm.shot(it.shotId)
    val lot = shot?.let { s -> vm.itemsOfShot(s).indexOfFirst { x -> x.id == it.id } + 1 } ?: 1
    val crop by produceState<Bitmap?>(initialValue = null, it.id) { value = withContext(Dispatchers.IO) { vm.loadCrop(it.shotId, it.x, it.y) } }
    val target = vm.settings.target

    Column(
        Modifier.fillMaxWidth()
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { rotationZ = offset.value / 45f }
            .background(Navy, RoundedCornerShape(4.dp)).border(1.dp, Line, RoundedCornerShape(4.dp))
            .pointerInput(it.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = offset.value
                            if (abs(v) > 220f) {
                                offset.animateTo(if (v > 0) 1600f else -1600f)
                                vm.decide(grab = v > 0)
                            } else offset.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag) }
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp).background(Line)) {
            crop?.let { b -> Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (it.typ >= target) Chip("Over ${money(target)} alert", Navy, Gold)
                if (it.grab) Chip("In cart", Ivory, Navy) else if (it.pass) Chip("Passed", Navy, Muted)
                it.verified?.let { v -> Chip(verdictLabel(v.verdict), if (v.verdict == "reproduction") Bad else Navy, if (v.verdict == "reproduction") Navy else Gold) }
                    ?: if (it.verify != null) Chip("Authenticate", Ivory, Navy) else Unit
            }
            if (offset.value > 60f) Chip("GRAB", Ivory, PaperGold, Modifier.align(Alignment.Center))
            if (offset.value < -60f) Chip("PASS", Ivory, Color(0xFFA23B2F), Modifier.align(Alignment.Center))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LOT $lot" + (shot?.scene?.let { " · " + it.uppercase() } ?: ""), color = Gold, fontSize = 10.5.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.Top) {
                Text(it.name, fontFamily = Serif, fontSize = 23.sp, color = Ivory, lineHeight = 27.sp, modifier = Modifier.weight(1f))
                Text(money(it.typ), fontFamily = Serif, fontSize = 32.sp, color = if (it.typ >= target) Gold else Ivory, modifier = Modifier.padding(start = 10.dp))
            }
            Text("Sold for ${money(it.low)}–${money(it.high)} · ${it.where}" + if (it.details.isNotBlank()) "\n${it.details}" else "", color = Muted, fontSize = 13.sp)
            Text("${it.conf.replaceFirstChar { c -> c.uppercase() }} confidence", color = Muted, fontSize = 12.sp)

            it.demand()?.let { d -> OddsBox(vm, it, d) }

            when {
                it.checking -> Note("Checking your close-up…", Check)
                it.verify != null -> Column(Modifier.fillMaxWidth().background(Color(0x22A9C3DF), Sharp).padding(12.dp)) {
                    Text("Authenticate: " + it.verify.ask, color = Check, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton("Take close-up") { vm.startCloseUp(it.id) }
                        TextButton(onClick = { vm.updateItem(it.id) { x -> x.copy(verify = null) } }) { Text("Skip", color = Check) }
                    }
                }
            }
            it.verified?.takeIf { v -> v.note.isNotBlank() }?.let { v ->
                Row {
                    Box(Modifier.width(3.dp).height(48.dp).background(if (v.verdict == "reproduction") Bad else Gold))
                    Text(verdictLabel(v.verdict) + ": " + v.note, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LineButton("Sold ↗", color = Gold) { runCatching { uri.openUri(soldUrl(it.query)) } }
                LineButton("For sale ↗") { runCatching { uri.openUri(forSaleUrl(it.query)) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                var tagText by remember(it.id) { mutableStateOf(it.tag?.let { t -> if (t % 1.0 == 0.0) t.toInt().toString() else t.toString() } ?: "") }
                OutlinedTextField(
                    value = tagText,
                    onValueChange = { v -> tagText = v; vm.updateItem(it.id) { x -> x.copy(tag = v.toDoubleOrNull()) } },
                    label = { Text("Tag price $") }, singleLine = true, modifier = Modifier.width(150.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                it.net(vm.settings.fee)?.let { n ->
                    Text((if (n >= 0) "+" else "−") + money(abs(n)) + " net", color = if (n >= 0) Good else Bad, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundAction("✕", Bad, Navy2, 62, it.pass) { vm.decide(grab = false) }
        RoundAction("⌕", Check, Navy2, 50, false) { vm.startCloseUp(it.id) }
        RoundAction("✓", Navy, Gold, 62, it.grab) { vm.decide(grab = true) }
    }
    Text("Swipe right to grab, left to pass · ⌕ takes a close-up", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    TextButton(onClick = {
        val d = vm.deck
        vm.deleteItem(it.id)
        if (d != null) { val rest = d.ids.filter { x -> x != it.id }; vm.deck = if (rest.isEmpty()) null else Deck(rest, minOf(d.idx, rest.size)) }
    }, modifier = Modifier.fillMaxWidth()) { Text("Remove this item", color = Muted) }
    Spacer(Modifier.height(30.dp))
}

@Composable
private fun OddsBox(vm: AppViewModel, it: Item, d: Demand) {
    val c = gradeColor(d.p)
    var showCounts by remember(it.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.copy(alpha = 0.13f), Sharp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Chance to sell in 90 days", color = Ivory, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${d.p}%", fontFamily = Serif, fontSize = 26.sp, color = c)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(d.p / 100f).background(c))
        }
        Row {
            Stat(d.sold?.let { s -> (if (d.real) "" else "~") + String.format("%,d", s) } ?: "–", "sold", Modifier.weight(1f))
            Stat(d.listed?.let { l -> (if (d.real) "" else "~") + String.format("%,d", l) } ?: "–", "for sale now", Modifier.weight(1f))
            Stat(gradeOf(d.p), if (d.real) "your count" else "estimate", Modifier.weight(1f), c)
        }
        if (!showCounts) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (d.real) "From the counts you entered." else "Claude's estimate.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCounts = true }) { Text(if (d.real) "Edit counts" else "Enter real counts", color = Gold) }
            }
        } else {
            Text("Open Sold and For sale below, then type the result counts eBay shows at the top.", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var soldText by remember(it.id) { mutableStateOf(it.realSold?.toString() ?: "") }
                var listedText by remember(it.id) { mutableStateOf(it.realListed?.toString() ?: "") }
                OutlinedTextField(soldText, { v -> soldText = v; vm.updateItem(it.id) { x -> x.copy(realSold = v.toIntOrNull()) } },
                    label = { Text("Sold") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(listedText, { v -> listedText = v; vm.updateItem(it.id) { x -> x.copy(realListed = v.toIntOrNull()) } },
                    label = { Text("For sale") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            TextButton(onClick = { showCounts = false }) { Text("Done", color = Gold) }
        }
    }
}

@Composable private fun Stat(v: String, l: String, modifier: Modifier, c: Color = Ivory) = Column(modifier) {
    Text(v, color = c, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    Text(l, color = Muted, fontSize = 12.sp)
}

@Composable private fun Chip(t: String, bg: Color, fg: Color, modifier: Modifier = Modifier) =
    Text(t, color = fg, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, modifier = modifier.background(bg, RoundedCornerShape(2.dp)).padding(horizontal = 8.dp, vertical = 4.dp))

@Composable private fun Note(t: String, c: Color) =
    Text(t, color = c, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, modifier = Modifier.fillMaxWidth().background(c.copy(alpha = 0.13f), Sharp).padding(12.dp))

@Composable private fun RoundAction(glyph: String, fg: Color, bg: Color, size: Int, on: Boolean, onClick: () -> Unit) =
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).border(if (on) 3.dp else 1.dp, if (on) Ivory else Line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = fg, fontSize = (size / 2.4).sp, fontWeight = FontWeight.Bold) }

private fun verdictLabel(v: String) = when (v) {
    "confirmed" -> "Authenticated"
    "revised" -> "Corrected"
    "reproduction" -> "Likely repro"
    else -> "Still unclear"
}

// ============================================================ alerts + messages

@Composable
private fun AlertBanners(vm: AppViewModel, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.alerts.toList().forEach { id ->
            val al = vm.item(id) ?: return@forEach
            Column(Modifier.fillMaxWidth().background(Navy2, RoundedCornerShape(4.dp)).border(1.dp, Gold, RoundedCornerShape(4.dp)).padding(12.dp)) {
                Text("OVER YOUR ${money(vm.settings.target)} ALERT" + if (al.verify != null) " · AUTHENTICATE FIRST" else "", color = Gold, fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(al.name, color = Ivory, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(al.where + (al.demand()?.let { d -> " · ${d.p}% chance to sell" } ?: ""), color = Muted, fontSize = 12.sp)
                    }
                    Text(money(al.typ), fontFamily = Serif, fontSize = 24.sp, color = Gold, modifier = Modifier.padding(start = 8.dp))
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("Look") { vm.dismissAlert(id); vm.openDeck(listOf(id), id) }
                    LineButton("Grab") { vm.updateItem(id) { x -> x.copy(grab = true, pass = false) }; vm.dismissAlert(id) }
                    LineButton("Dismiss", color = Muted) { vm.dismissAlert(id) }
                }
            }
        }
    }
}

@Composable
private fun MessageBar(vm: AppViewModel, modifier: Modifier) {
    val msg = vm.banner ?: return
    LaunchedEffect(msg) { delay(5000); if (vm.banner == msg) vm.banner = null }
    Text(
        msg, color = Ivory, fontSize = 13.5.sp,
        modifier = modifier.padding(16.dp).padding(bottom = 96.dp).fillMaxWidth().background(CartTag, Sharp).border(1.dp, Line, Sharp)
            .clickable { vm.banner = null }.padding(12.dp),
    )
}

// ============================================================ settings

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    var key by remember { mutableStateOf(s.apiKey) }
    var target by remember { mutableStateOf(s.target.toInt().toString()) }
    var min by remember { mutableStateOf(s.min.toInt().toString()) }
    var fee by remember { mutableStateOf(s.fee.toString()) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", fontFamily = Serif, fontSize = 26.sp, color = Ivory, modifier = Modifier.weight(1f))
            GoldButton("Done") {
                vm.updateSettings(
                    vm.settings.copy(
                        apiKey = key.trim(),
                        target = target.toDoubleOrNull()?.coerceAtLeast(1.0) ?: s.target,
                        min = min.toDoubleOrNull()?.coerceAtLeast(0.0) ?: s.min,
                        fee = fee.toDoubleOrNull()?.coerceIn(0.0, 40.0) ?: s.fee,
                    ),
                )
                vm.showSettings = false
            }
        }
        Text("CLAUDE API KEY", color = Gold, fontSize = 10.5.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.ExtraBold)
        OutlinedTextField(key, { key = it }, label = { Text("sk-ant-…") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Text("Get one at console.anthropic.com → API Keys. It stays on this phone. Set a monthly spend limit there so costs never surprise you.", color = Muted, fontSize = 12.5.sp)

        Text("PRICES", color = Gold, fontSize = 10.5.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(target, { target = it }, label = { Text("Alert at $") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(min, { min = it }, label = { Text("Log from $") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(fee, { fee = it }, label = { Text("eBay fees %") }, singleLine = true, modifier = Modifier.widthIn(max = 180.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

        Text("SCANNING", color = Gold, fontSize = 10.5.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Toggle("Live scanning", "Scans on its own whenever you hold steady on something new", s.live) { vm.updateSettings(vm.settings.copy(live = it)) }
        Toggle("Careful mode", "Slower and pricier per scan, better at reading marks and patterns", s.careful) { vm.updateSettings(vm.settings.copy(careful = it)) }
        Toggle("Alert sound", "A short beep when something is over your alert price", s.sound) { vm.updateSettings(vm.settings.copy(sound = it)) }
        Toggle("Alert vibration", "Two short buzzes for an alert", s.vibrate) { vm.updateSettings(vm.settings.copy(vibrate = it)) }

        Spacer(Modifier.height(8.dp))
        if (!confirm) LineButton("Start a new hunt", color = Bad) { confirm = true }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clear every scan and item?", color = Ivory, modifier = Modifier.weight(1f))
            Button(onClick = { vm.newHunt(); confirm = false; vm.showSettings = false }, shape = Sharp, colors = ButtonDefaults.buttonColors(containerColor = Bad, contentColor = Navy)) { Text("Clear", fontWeight = FontWeight.Bold) }
            LineButton("Keep") { confirm = false }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ivory, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 12.5.sp)
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Navy, checkedTrackColor = Gold))
    }
}
