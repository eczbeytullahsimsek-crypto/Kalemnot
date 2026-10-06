package com.kalemnot.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke as InkStroke
import kotlin.math.ceil

val inkRenderer by lazy { CanvasStrokeRenderer.create() }

data class Tpl(val id: String, val name: String, val desc: String, val tag1: String, val tag2: String)
val TEMPLATES = listOf(
    Tpl("plain", "Boş Kağıt (Plain)", "Saf serbest çizim, eskiz ve zihin haritaları için minimum müdahale.", "0g Baskı", "Sonsuz Akış"),
    Tpl("lined", "Çizgili (Ruled 7mm)", "Klasik el yazısı, günlük tutma ve akıcı satır satır kompozisyon.", "7.1 mm", "Optik Çizgili"),
    Tpl("grid", "Izgara (Grid 5mm)", "Ölçekli mimari planlar, şematik çizim ve geometrik konstrüksiyon.", "5 x 5 mm", "Metrik Izgara"),
    Tpl("dot", "Noktalı (Dot Grid)", "Bullet journal, modüler düzen ve stylus için gizli referans matrisi.", "1.2pt Nokta", "BuJo Uyumlu"),
    Tpl("weekly", "Haftalık Ajanda (Planner)", "Sütunlu gün planı, görev blokları ve alışkanlık takibi.", "7 Gün Bloklu", "Zaman Çizelgesi"),
    Tpl("cornell", "Cornell Metodu", "İpucu sütunu, ana gövde ve alt özet alanı ile akademik not alma.", "3 Bölümlü", "Çalışma Odaklı"),
    Tpl("dark", "Siyah Tahta (Dark Canvas)", "Gece eskizleri, tebeşir ve parlak pastel çalışmaları için yüksek kontrast.", "Ters Kontrast", "OLED Dostu"),
)
fun tplName(id: String) = TEMPLATES.firstOrNull { it.id == id }?.name?.substringBefore(" (")?.substringBefore(" Metodu") ?: id

private const val SHEET_W = 1200f
private const val SHEET_H = 900f

/** Sayfa şablonunu, pan/zoom dönüşümüyle (ekran = içerik*z + off) çizer. */
fun DrawScope.drawPaper(tpl: String, z: Float, off: Offset, dark: Boolean, tm: TextMeasurer) {
    val dot = if (dark) Color(0x40E7E1DF) else Color(0x667E7570)
    val ln = if (dark) Color(0x26FFFFFF) else Color(0xFFE4E0DA)
    val lbl = if (dark) Color(0x99E7E1DF) else Color(0xFF7E7570)
    withTransform({ translate(off.x, off.y); scale(z, z, Offset.Zero) }) {
        val x0 = -off.x / z; val y0 = -off.y / z
        val x1 = (size.width - off.x) / z; val y1 = (size.height - off.y) / z
        val hw = 1f / z
        when (tpl) {
            "dot" -> {
                val st = if (z < .6f) 48f else 24f
                var gx = ceil(x0 / st) * st
                while (gx < x1) { var gy = ceil(y0 / st) * st; while (gy < y1) { drawCircle(dot, 1.2f * hw, Offset(gx, gy)); gy += st }; gx += st }
            }
            "grid" -> {
                val st = if (z < .5f) 48f else 24f
                var gx = ceil(x0 / st) * st; while (gx < x1) { drawLine(ln, Offset(gx, y0), Offset(gx, y1), hw); gx += st }
                var gy = ceil(y0 / st) * st; while (gy < y1) { drawLine(ln, Offset(x0, gy), Offset(x1, gy), hw); gy += st }
            }
            "lined" -> { val st = 30f; var gy = ceil(y0 / st) * st; while (gy < y1) { drawLine(ln, Offset(x0, gy), Offset(x1, gy), hw); gy += st } }
            "weekly" -> {
                val bw = 1.5f * hw
                drawRect(ln, Offset.Zero, Size(SHEET_W, SHEET_H), style = Stroke(bw))
                drawLine(ln, Offset(0f, 80f), Offset(SHEET_W, 80f), bw)
                drawText(tm, "HAFTALIK PLAN", Offset(24f, 28f), TextStyle(fontSize = 16.sp, color = lbl, fontFamily = Mono))
                val cw = SHEET_W / 4; val ch = (SHEET_H - 80f) / 2
                val names = listOf("PZT", "SAL", "ÇAR", "PER", "CUM", "CMT", "PAZ", "NOTLAR")
                for (i in 0 until 8) {
                    val cx = (i % 4) * cw; val cy = 80f + (i / 4) * ch
                    drawRect(ln, Offset(cx, cy), Size(cw, ch), style = Stroke(bw))
                    drawText(tm, names[i], Offset(cx + 14f, cy + 12f), TextStyle(fontSize = 12.sp, color = lbl, fontFamily = Mono))
                }
            }
            "cornell" -> {
                val bw = 1.5f * hw
                val h = 1000f
                drawRect(ln, Offset.Zero, Size(SHEET_W, h), style = Stroke(bw))
                drawLine(ln, Offset(0f, 90f), Offset(SHEET_W, 90f), bw)
                drawLine(ln, Offset(360f, 90f), Offset(360f, h - 170f), bw)
                drawLine(ln, Offset(0f, h - 170f), Offset(SHEET_W, h - 170f), bw)
                drawText(tm, "KONU / TARİH", Offset(24f, 32f), TextStyle(fontSize = 13.sp, color = lbl, fontFamily = Mono))
                drawText(tm, "İPUÇLARI", Offset(24f, 108f), TextStyle(fontSize = 12.sp, color = lbl, fontFamily = Mono))
                drawText(tm, "NOTLAR", Offset(384f, 108f), TextStyle(fontSize = 12.sp, color = lbl, fontFamily = Mono))
                drawText(tm, "ÖZET", Offset(24f, h - 150f), TextStyle(fontSize = 12.sp, color = lbl, fontFamily = Mono))
            }
        }
    }
}

/** Şablon kartlarındaki minyatür. */
@Composable
fun TplPreview(id: String, modifier: Modifier = Modifier) {
    val dk = id == "dark"
    Canvas(modifier.background(if (dk) Color(0xFF1F1E1D) else Color(0xFFFAF8F5))) {
        val ln = if (dk) Color(0x33FFFFFF) else Color(0xFFE0DDD8)
        val w = size.width; val h = size.height
        when (id) {
            "lined" -> { var y = h / 7; while (y < h) { drawLine(ln, Offset(4f, y), Offset(w - 4f, y), 1f); y += h / 7 } }
            "grid" -> { val s = 8.dp.toPx(); var x = 0f; while (x < w) { drawLine(ln, Offset(x, 0f), Offset(x, h), 1f); x += s }; var y = 0f; while (y < h) { drawLine(ln, Offset(0f, y), Offset(w, y), 1f); y += s } }
            "dot" -> { val s = 9.dp.toPx(); var x = s / 2; while (x < w) { var y = s / 2; while (y < h) { drawCircle(Color(0xFFCEC9C3), 1.4f, Offset(x, y)); y += s }; x += s } }
            "weekly" -> { val g = 3.dp.toPx(); for (r in 0..1) for (c in 0..1) drawRect(Color(0xFFE8E4DE), Offset(6f + c * (w / 2 - 3f), 16f + r * ((h - 20f) / 2)), Size(w / 2 - g - 6f, (h - 20f) / 2 - g)) }
            "cornell" -> { drawRect(Color(0xFFE5E0DA), Offset(6f, 6f), Size(w - 12f, 8f)); drawRect(Color(0xFFECE8E2), Offset(6f, 20f), Size(w / 3, h - 44f)); drawRect(Color(0xFFECE8E2), Offset(w / 3 + 10f, 20f), Size(w * 2 / 3 - 16f, h - 44f)); drawRect(Color(0xFFDFD9D2), Offset(6f, h - 18f), Size(w - 12f, 12f)) }
        }
    }
}

/** Defter kartı / sayfa kartı önizlemesi: içeriği sığdırarak çizer. */
@Composable
fun Thumb(page: Page, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Pal.paper(page.dark))) {
        val dc = if (page.dark) Color(0x22FFFFFF) else Color(0x227E7570)
        val st = 18.dp.toPx(); var x = st; while (x < size.width) { var y = st; while (y < size.height) { drawCircle(dc, 1.2f, Offset(x, y)); y += st }; x += st }
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        page.strokes.forEach { s -> s.shape.computeBoundingBox()?.let { b ->
            minX = minOf(minX, b.xMin); minY = minOf(minY, b.yMin); maxX = maxOf(maxX, b.xMax); maxY = maxOf(maxY, b.yMax) } }
        if (minX > maxX) return@Canvas
        val w = maxOf(maxX - minX, 1f); val h = maxOf(maxY - minY, 1f)
        val sc = minOf(size.width * .84f / w, size.height * .84f / h)
        val m = android.graphics.Matrix().apply {
            setScale(sc, sc); postTranslate(-minX * sc + (size.width - w * sc) / 2, -minY * sc + (size.height - h * sc) / 2)
        }
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save(); nc.concat(m)
            page.strokes.forEach { inkRenderer.draw(canvas = nc, stroke = it, strokeToScreenTransform = m) }
            nc.restore()
        }
    }
}
