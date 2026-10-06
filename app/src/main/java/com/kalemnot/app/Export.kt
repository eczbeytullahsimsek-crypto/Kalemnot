package com.kalemnot.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

private fun renderPage(ctx: Context, page: Page): Bitmap {
    val density = Density(ctx)
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    fun add(x0: Float, y0: Float, x1: Float, y1: Float) { minX = minOf(minX, x0); minY = minOf(minY, y0); maxX = maxOf(maxX, x1); maxY = maxOf(maxY, y1) }
    page.strokes.forEach { s -> s.shape.computeBoundingBox()?.let { add(it.xMin, it.yMin, it.xMax, it.yMax) } }
    val cardW = 240f * density.density
    page.boxes.forEach { add(it.x, it.y, it.x + cardW, it.y + 150f * density.density) }
    if (page.template == "weekly") add(0f, 0f, 1200f, 900f)
    if (page.template == "cornell") add(0f, 0f, 1200f, 1000f)
    if (minX > maxX) add(0f, 0f, 1280f, 960f)
    val pad = 64f
    minX -= pad; minY -= pad; maxX += pad; maxY += pad
    val w = maxX - minX; val h = maxY - minY
    val s = minOf(2400f / w, 2400f / h, 2f)
    val wpx = (w * s).toInt().coerceAtLeast(1); val hpx = (h * s).toInt().coerceAtLeast(1)
    val img = ImageBitmap(wpx, hpx)
    val tm = TextMeasurer(createFontFamilyResolver(ctx), density, LayoutDirection.Ltr)
    val off = Offset(-minX * s, -minY * s)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(wpx.toFloat(), hpx.toFloat())) {
        drawRect(Pal.paper(page.dark))
        drawPaper(page.template, s, off, page.dark, tm)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            val m = android.graphics.Matrix().apply { setScale(s, s); postTranslate(off.x, off.y) }
            nc.save(); nc.concat(m)
            page.strokes.forEach { inkRenderer.draw(canvas = nc, stroke = it, strokeToScreenTransform = m) }
            nc.restore()
        }
        withTransform({ translate(off.x, off.y); scale(s, s, Offset.Zero) }) {
            page.boxes.forEach { b ->
                drawRoundRect(if (page.dark) Color(0xFF2B2A28) else Color(0xFFF1EFEB), Offset(b.x, b.y), Size(cardW, 120f * density.density), androidx.compose.ui.geometry.CornerRadius(16f))
                drawText(tm, b.title.uppercase(), Offset(b.x + 16f, b.y + 14f), TextStyle(fontSize = 10.sp, color = Color(0xFF7E7570)))
                drawText(tm, b.text, Offset(b.x + 16f, b.y + 40f), TextStyle(fontSize = 13.sp, color = if (page.dark) Color(0xFFE8E5E1) else Color(0xFF1B1C1A)), size = Size(cardW - 32f, 400f))
            }
        }
    }
    return img.asAndroidBitmap()
}

/** Çıktıyı Pictures/KalemNot (PNG) veya Download/KalemNot (PDF) klasörüne yazar; kullanıcıya gösterilecek mesajı döndürür. */
fun exportPages(ctx: Context, title: String, pages: List<Page>, pdf: Boolean): String = runCatching {
    val safe = title.replace(Regex("[^A-Za-z0-9ÇĞİÖŞÜçğıöşü _-]"), "").ifBlank { "defter" }
    val r = ctx.contentResolver
    if (pdf) {
        val doc = PdfDocument()
        pages.forEachIndexed { i, p ->
            val bmp = renderPage(ctx, p)
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create())
            pg.canvas.drawBitmap(bmp, 0f, 0f, null); doc.finishPage(pg)
        }
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$safe.pdf"); put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KalemNot")
        }
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        r.openOutputStream(uri)!!.use { doc.writeTo(it) }; doc.close()
        "PDF kaydedildi: Download/KalemNot/$safe.pdf"
    } else {
        pages.forEachIndexed { i, p ->
            val bmp = renderPage(ctx, p)
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$safe-${i + 1}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KalemNot")
            }
            val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
            r.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        "${pages.size} PNG kaydedildi: Pictures/KalemNot"
    }
}.getOrElse { "Dışa aktarma başarısız: ${it.message}" }
