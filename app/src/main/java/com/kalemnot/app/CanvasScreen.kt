package com.kalemnot.app

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.ImmutableVec
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class Tool(val label: String) { PEN("Dolma kalem"), MARKER("Tükenmez"), HIGHLIGHTER("Fosforlu"), ERASER("Silgi") }
private val PALETTE = listOf(INK_SENT, 0xFF78716C.toInt(), 0xFFEA580C.toInt(), 0xFF0051D5.toInt(), 0xFF16A34A.toInt())
private val SIZES = floatArrayOf(2f, 4f, 9f)

@Composable
fun CanvasScreen(
    vm: NotesVm, s: Session, focus: Boolean, setFocus: (Boolean) -> Unit,
    onLibrary: () -> Unit, onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val tm = rememberTextMeasurer()
    val page = s.pages[s.idx]
    val undo = remember { ArrayDeque<List<androidx.ink.strokes.Stroke>>() }
    val redo = remember { ArrayDeque<List<androidx.ink.strokes.Stroke>>() }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableIntStateOf(INK_SENT) }
    var weight by remember { mutableIntStateOf(1) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var showExport by remember { mutableStateOf(false) }
    val cardRects = remember { mutableMapOf<Long, Rect>() }

    LaunchedEffect(s.idx) { undo.clear(); redo.clear(); zoom = 1f; offset = Offset.Zero }

    fun cur() = s.pages[s.idx]
    fun setStrokes(l: List<androidx.ink.strokes.Stroke>) { s.pages[s.idx] = cur().copy(strokes = l); vm.scheduleSave() }
    fun setBoxes(l: List<NoteBox>) { s.pages[s.idx] = cur().copy(boxes = l); vm.scheduleSave() }
    fun toContent(p: Offset) = (p - offset) / zoom
    fun zoomAround(c: Offset, f: Float) { val ns = (zoom * f).coerceIn(0.3f, 8f); offset = c - (c - offset) * (ns / zoom); zoom = ns }
    fun center() = Offset(viewSize.width / 2f, viewSize.height / 2f)
    fun doUndo() { if (undo.isNotEmpty()) { redo.addLast(cur().strokes); setStrokes(undo.removeLast()) } }
    fun doRedo() { if (redo.isNotEmpty()) { undo.addLast(cur().strokes); setStrokes(redo.removeLast()) } }

    fun currentBrush(): Brush {
        val real = if (color == INK_SENT) Pal.inkArgb(cur().dark) else color
        val (fam, size, alpha) = when (tool) {
            Tool.HIGHLIGHTER -> Triple(Fam.HL, SIZES[weight] * 4f, 0x66)
            Tool.MARKER -> Triple(Fam.MARKER, SIZES[weight], 0xFF)
            else -> Triple(Fam.PEN, SIZES[weight] * 1.3f, 0xFF)
        }
        return makeBrush(fam, (alpha shl 24) or (real and 0xFFFFFF), size / zoom)
    }
    val brush = remember(tool, color, weight, zoom, page.dark) { currentBrush() }
    val toWorld = remember(zoom, offset) {
        Matrix(floatArrayOf(
            1f / zoom, 0f, 0f, 0f,
            0f, 1f / zoom, 0f, 0f,
            0f, 0f, 1f, 0f,
            -offset.x / zoom, -offset.y / zoom, 0f, 1f
        ))
    }

    Column(Modifier.fillMaxSize().background(Pal.bg)) {
        AnimatedVisibility(!focus, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            // ---------- Üst başlık ----------
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(Pal.bg).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Kitaplık", Pal.textDim, 20, 36, onLibrary)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Book, null, tint = Pal.textDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                var title by remember(s.meta.id) { mutableStateOf(s.meta.title) }
                LaunchedEffect(title) { if (title != s.meta.title) { delay(500); vm.updateMeta { it.copy(title = title) } } }
                BasicTextField(
                    title, { title = it }, singleLine = true, cursorBrush = SolidColor(Pal.primary),
                    textStyle = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Pal.text), modifier = Modifier.width(260.dp)
                )
                Lbl(s.meta.category, catColor(s.meta.category), 10, Modifier.clip(RoundedCornerShape(4.dp)).background(Pal.mid).padding(horizontal = 8.dp, vertical = 3.dp))
                Spacer(Modifier.weight(1f))
                PagePill(s, vm)
                Spacer(Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.low).padding(4.dp)) {
                    IconBtn(Icons.AutoMirrored.Filled.Undo, "Geri al", Pal.textDim, 19, 32) { doUndo() }
                    IconBtn(Icons.AutoMirrored.Filled.Redo, "İleri al", Pal.textDim, 19, 32) { doRedo() }
                }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.low).clickable { setFocus(true) }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Fullscreen, null, tint = Pal.text, modifier = Modifier.size(18.dp)); Lbl("Odak modu", Pal.text, 12)
                }
                Spacer(Modifier.width(8.dp))
                Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.mid).clickable { showExport = true }.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.IosShare, null, tint = Pal.text, modifier = Modifier.size(18.dp)); Lbl("Dışa aktar", Pal.text, 12)
                }
                IconBtn(Icons.Default.Settings, "Ayarlar", Pal.textDim, 20, 36, onSettings)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (focus) 12.dp else 20.dp, vertical = if (focus) 12.dp else 8.dp)) {
            val shape = RoundedCornerShape(12.dp)
            // ---------- Kağıt ----------
            Box(
                Modifier.fillMaxSize().shadow(if (Pal.dark) 0.dp else 6.dp, shape).clip(shape).background(Pal.paper(page.dark))
                    .onSizeChanged { viewSize = it }
                    // Parmak: kaydır + yakınlaştır. Dokunuşlar çizim yapmaz (avuç içi reddi).
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (down.type == PointerType.Touch && cardRects.values.any { it.contains(down.position) }) return@awaitEachGesture
                            if (down.type == PointerType.Touch) down.consume()
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val penOn = ev.changes.any { it.type != PointerType.Touch && it.pressed }
                                if (!penOn && down.type == PointerType.Touch) {
                                    val z = ev.calculateZoom(); val pan = ev.calculatePan(); val c = ev.calculateCentroid()
                                    if (c.isSpecified) { zoomAround(c, z); offset += pan }
                                }
                                ev.changes.forEach { if (it.type == PointerType.Touch) it.consume() }
                            } while (ev.changes.any { it.pressed })
                        }
                    }
            ) {
                // Bitmiş çizgiler + kağıt şablonu
                Canvas(
                    Modifier.fillMaxSize().pointerInput(tool) {
                        if (tool != Tool.ERASER) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.type == PointerType.Touch) return@awaitEachGesture
                            val before = cur().strokes
                            fun eraseAt(p: Offset) {
                                val w = toContent(p); val r = 20f / zoom
                                val box = ImmutableBox.fromCenterAndDimensions(ImmutableVec(w.x, w.y), 2 * r, 2 * r)
                                val hit = cur().strokes.filter { it.shape.computeCoverageIsGreaterThan(box = box, coverageThreshold = 0.01f) }
                                if (hit.isNotEmpty()) setStrokes(cur().strokes - hit.toSet())
                            }
                            eraseAt(down.position)
                            while (true) {
                                val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                eraseAt(c.position); c.consume()
                            }
                            if (cur().strokes.size != before.size) { undo.addLast(before); redo.clear() }
                        }
                    }
                ) {
                    val z = zoom; val off = offset
                    val pg = s.pages[s.idx]   // draw aşamasında okunur: yeniden kompozisyon gerekmez, bir kare boşluk olmaz
                    drawPaper(pg.template, z, off, pg.dark, tm)
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        val m = android.graphics.Matrix().apply { setScale(z, z); postTranslate(off.x, off.y) }
                        nc.save(); nc.concat(m)
                        pg.strokes.forEach { inkRenderer.draw(canvas = nc, stroke = it, strokeToScreenTransform = m) }
                        nc.restore()
                    }
                }
                // Google Ink: düşük gecikmeli "ıslak mürekkep" katmanı
                if (tool != Tool.ERASER) Box(Modifier.fillMaxSize()) {
                    InProgressStrokes(
                        defaultBrush = brush,
                        nextBrush = { currentBrush() },
                        pointerEventToWorldTransform = toWorld,
                        onStrokesFinished = { new ->
                            undo.addLast(cur().strokes); redo.clear()
                            setStrokes(cur().strokes + new)
                        }
                    )
                }
                // Yan not kutuları
                Box(Modifier.fillMaxSize()) {
                    page.boxes.forEach { b ->
                        key(b.id) {
                            NoteCard(
                                b, zoom, offset,
                                onChange = { nb -> setBoxes(cur().boxes.map { if (it.id == b.id) nb else it }) },
                                onDelete = { setBoxes(cur().boxes.filter { it.id != b.id }) },
                                onRect = { cardRects[b.id] = it }, onGone = { cardRects.remove(b.id) }
                            )
                        }
                    }
                }
                // Kağıt künyesi
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Lbl("ÖLÇEK ${(zoom * 100).roundToInt()}%", Pal.outline, 10)
                    Spacer(Modifier.width(10.dp)); Box(Modifier.size(5.dp).background(Pal.outlineV, CircleShape)); Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(6.dp).background(Pal.green, CircleShape)); Spacer(Modifier.width(5.dp))
                    Lbl("Basınç duyarlı: aktif", Pal.outline, 10)
                    Spacer(Modifier.weight(1f))
                    Lbl("Şablon: ${tplName(page.template)} — Tabakalar: ${s.pages.size}", Pal.outline, 10)
                }
            }

            // ---------- Yüzen kalem araç çubuğu ----------
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 28.dp), shape = RoundedCornerShape(32.dp),
                color = Pal.low.copy(alpha = .95f), shadowElevation = 14.dp, border = BorderStroke(1.dp, Pal.outlineV.copy(alpha = .3f))
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.clip(CircleShape).background(Pal.mid).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.FrontHand, null, tint = Pal.primary, modifier = Modifier.size(16.dp)); Lbl("Koruma", Pal.primary, 10)
                    }
                    Sep()
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToolBtn(Icons.Default.Create, tool == Tool.PEN) { tool = Tool.PEN }
                        ToolBtn(Icons.Default.Edit, tool == Tool.MARKER) { tool = Tool.MARKER }
                        ToolBtn(Icons.Default.Brush, tool == Tool.HIGHLIGHTER) { tool = Tool.HIGHLIGHTER }
                        ToolBtn(Icons.Default.AutoFixNormal, tool == Tool.ERASER) { tool = Tool.ERASER }
                    }
                    Sep()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PALETTE.forEach { c ->
                            val on = c == color && tool != Tool.ERASER
                            val shown = if (c == INK_SENT) Color(Pal.inkArgb(page.dark)) else Color(c)
                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                val sz by animateDpAsState(if (on) 22.dp else 20.dp, label = "dot")
                                Box(Modifier.size(sz).background(shown, CircleShape)
                                    .border(if (on) 2.dp else 0.dp, Pal.primary, CircleShape).padding(if (on) 2.dp else 0.dp)
                                    .clickable { color = c; if (tool == Tool.ERASER) tool = Tool.PEN })
                            }
                        }
                    }
                    Sep()
                    Row(Modifier.clip(CircleShape).background(Pal.mid).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(6, 10, 14).forEachIndexed { i, d ->
                            Box(Modifier.size(28.dp).clip(CircleShape).background(if (weight == i) Pal.highest else Color.Transparent).clickable { weight = i }, contentAlignment = Alignment.Center) {
                                Box(Modifier.size(d.dp).background(Pal.primary, CircleShape))
                            }
                        }
                    }
                    Sep()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Icons.AutoMirrored.Filled.Undo, "Geri al", Pal.textDim, 18, 34) { doUndo() }
                        IconBtn(Icons.AutoMirrored.Filled.Redo, "İleri al", Pal.textDim, 18, 34) { doRedo() }
                        IconBtn(Icons.Default.StickyNote2, "Not kutusu", Pal.textDim, 18, 34) {
                            val p = toContent(center()) - Offset(250f, 100f)
                            setBoxes(cur().boxes + NoteBox(p.x, p.y))
                        }
                        IconBtn(Icons.Default.OpenInFull, "Odak modu", Pal.textDim, 18, 34) { setFocus(true) }
                    }
                }
            }

            // ---------- Odak modu çıkış ----------
            if (focus) Row(
                Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 12.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                    .background(Pal.primary).clickable { setFocus(false) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloseFullscreen, null, tint = Pal.onPrimary, modifier = Modifier.size(18.dp))
                Lbl("Odak modunu kapat", Pal.onPrimary, 12)
            }

            // ---------- Alt sol: zoom ----------
            AnimatedVisibility(!focus, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 16.dp), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.shadow(3.dp, CircleShape).clip(CircleShape).background(Pal.low.copy(alpha = .92f)).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Default.Remove, "Uzaklaştır", Pal.textDim, 16, 30) { zoomAround(center(), 0.8f) }
                    Lbl("${(zoom * 100).roundToInt()}%", Pal.primary, 11, Modifier.clickable { zoom = 1f; offset = Offset.Zero }.padding(horizontal = 6.dp))
                    IconBtn(Icons.Default.Add, "Yakınlaştır", Pal.textDim, 16, 30) { zoomAround(center(), 1.25f) }
                    Sep(); Spacer(Modifier.width(2.dp))
                    IconBtn(Icons.Default.FitScreen, "Sığdır", Pal.textDim, 16, 30) { zoom = 1f; offset = Offset.Zero }
                }
            }
            // ---------- Alt sağ: sayfa gezgini ----------
            AnimatedVisibility(!focus, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.shadow(4.dp, CircleShape).clip(CircleShape).background(Pal.low.copy(alpha = .95f)).padding(start = 14.dp, top = 5.dp, bottom = 5.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lbl("Sayfa", Pal.outline, 10, weight = FontWeight.SemiBold)
                    IconBtn(Icons.Default.ChevronLeft, "Önceki", Pal.textDim, 16, 26) { if (s.idx > 0) s.idx-- }
                    Lbl("%02d".format(s.idx + 1), Pal.primary, 12, weight = FontWeight.SemiBold); Lbl("/", Pal.outline, 12); Lbl("%02d".format(s.pages.size), Pal.textDim, 12)
                    IconBtn(Icons.Default.ChevronRight, "Sonraki", Pal.textDim, 16, 26) { if (s.idx < s.pages.lastIndex) s.idx++ }
                    Sep()
                    Row(Modifier.clip(CircleShape).background(Pal.primary).clickable { vm.addPage(vm.defaultTemplate) }.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.Add, null, tint = Pal.onPrimary, modifier = Modifier.size(16.dp)); Lbl("Yeni sayfa", Pal.onPrimary, 10)
                    }
                }
            }
        }
    }
    if (showExport) ExportDialog(s, { showExport = false }) { pdf, all ->
        vm.flushNow()
        Toast.makeText(ctx, exportPages(ctx, s.meta.title, if (all) s.pages.toList() else listOf(s.pages[s.idx]), pdf), Toast.LENGTH_LONG).show()
        showExport = false
    }
}

@Composable
private fun PagePill(s: Session, vm: NotesVm) {
    Row(Modifier.clip(CircleShape).background(Pal.low).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.Default.ChevronLeft, "Önceki", Pal.textDim, 18, 28) { if (s.idx > 0) s.idx-- }
        Lbl("%02d".format(s.idx + 1), Pal.text, 12, weight = FontWeight.SemiBold); Lbl(" / ", Pal.textDim, 12); Lbl("%02d".format(s.pages.size), Pal.textDim, 12)
        IconBtn(Icons.Default.ChevronRight, "Sonraki", Pal.textDim, 18, 28) { if (s.idx < s.pages.lastIndex) s.idx++ }
        IconBtn(Icons.Default.Add, "Yeni sayfa", Pal.textDim, 16, 28) { vm.addPage(vm.defaultTemplate) }
    }
}

@Composable
private fun NoteCard(b: NoteBox, zoom: Float, off: Offset, onChange: (NoteBox) -> Unit, onDelete: () -> Unit, onRect: (Rect) -> Unit, onGone: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val curB by rememberUpdatedState(b)
    val curChange by rememberUpdatedState(onChange)
    var title by remember(b.id) { mutableStateOf(b.title) }
    var text by remember(b.id) { mutableStateOf(b.text) }
    LaunchedEffect(title, text) { if (title != curB.title || text != curB.text) { delay(400); curChange(curB.copy(title = title, text = text)) } }
    DisposableEffect(b.id) {
        onDispose { onGone(); if (title != curB.title || text != curB.text) curChange(curB.copy(title = title, text = text)) }
    }
    Column(
        Modifier.offset { IntOffset((b.x * zoom + off.x).roundToInt(), (b.y * zoom + off.y).roundToInt()) }
            .graphicsLayer { scaleX = zoom; scaleY = zoom; transformOrigin = TransformOrigin(0f, 0f) }
            .onGloballyPositioned { onRect(it.boundsInParent()) }
            .width(240.dp).clip(shape).background(Pal.low.copy(alpha = .92f)).border(1.dp, Pal.outlineV.copy(alpha = .4f), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().pointerInput(b.id) { detectDragGestures { c, d -> c.consume(); curChange(curB.copy(x = curB.x + d.x, y = curB.y + d.y)) } }
                .padding(start = 12.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(title, { title = it }, singleLine = true, cursorBrush = SolidColor(Pal.primary),
                textStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = Pal.outline, letterSpacing = 1.sp), modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).background(Pal.blueC, CircleShape))
            IconBtn(Icons.Default.Close, "Sil", Pal.outline, 14, 28, onDelete)
        }
        BasicTextField(text, { text = it }, cursorBrush = SolidColor(Pal.primary),
            textStyle = TextStyle(fontFamily = Sans, fontSize = 14.sp, color = Pal.text, lineHeight = 20.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable private fun Sep() = Box(Modifier.width(1.dp).height(24.dp).background(Pal.highest))

@Composable
private fun ToolBtn(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val lift by animateDpAsState(if (active) (-4).dp else 0.dp, label = "lift")
    val bg by animateColorAsState(if (active) Pal.primary else Color.Transparent, label = "bg")
    Box(Modifier.offset(y = lift).size(if (active) 40.dp else 36.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (active) Pal.onPrimary else Pal.textDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExportDialog(s: Session, onDismiss: () -> Unit, onGo: (pdf: Boolean, all: Boolean) -> Unit) {
    var pdf by remember { mutableStateOf(true) }
    var all by remember { mutableStateOf(false) }
    Dialog(onDismiss) {
        Column(Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(20.dp)).background(Pal.lowest).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Txt("Eskizi dışa aktar", 18, Pal.text, FontWeight.SemiBold)
            Lbl("Dosya formatı", Pal.text, 11)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormatCard(Icons.Default.PictureAsPdf, "PDF Belge", "Çok sayfalı", pdf, Modifier.weight(1f)) { pdf = true }
                FormatCard(Icons.Default.Image, "PNG Resim", "Sayfa başına", !pdf, Modifier.weight(1f)) { pdf = false }
            }
            Lbl("Sayfa aralığı", Pal.text, 11)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Aktif sayfa (${"%02d".format(s.idx + 1)})", !all) { all = false }
                Chip("Tüm defter (${s.pages.size})", all) { all = true }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss) { Text("İptal", color = Pal.textDim) }
                Spacer(Modifier.width(8.dp))
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Pal.primary).clickable { onGo(pdf, all) }.padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Download, null, tint = Pal.onPrimary, modifier = Modifier.size(18.dp)); Txt("Şimdi indir", 14, Pal.onPrimary, FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun FormatCard(icon: ImageVector, title: String, sub: String, sel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) Pal.low else Pal.lowest).border(if (sel) 2.dp else 1.dp, if (sel) Pal.primary else Pal.outlineV, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = if (sel) Pal.primary else Pal.textDim, modifier = Modifier.size(26.dp))
        Txt(title, 13, Pal.text, FontWeight.SemiBold); Lbl(sub, Pal.outline, 10)
    }
}
