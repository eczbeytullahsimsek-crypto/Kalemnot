package com.example.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class InkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var config = PenConfig()
    var inkColor = Color.BLACK
    var backgroundColorInt = Color.WHITE
    var allowFinger = false // true yaparsanız parmakla da çizilir (palm rejection zayıflar)

    private class Stroke(
        val color: Int,
        val config: PenConfig,
        val points: ArrayList<InputPoint> = ArrayList()
    )

    private val strokes = ArrayList<Stroke>()
    private val redoStack = ArrayList<Stroke>()
    private var current: Stroke? = null
    private var engine: PenEngine? = null
    private var lastPt: StrokePoint? = null

    private var bmp: Bitmap? = null
    private var bmpCanvas: Canvas? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isDither = true
    }

    // ---------- Genel API ----------

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.lastIndex))
        redraw()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.lastIndex))
        redraw()
    }

    fun clear() {
        strokes.clear()
        redoStack.clear()
        redraw()
    }

    // ---------- View yaşam döngüsü ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        bmp?.recycle()
        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmpCanvas = Canvas(bmp!!)
        redraw()
    }

    override fun onDraw(canvas: Canvas) {
        bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    // ---------- Dokunma ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val type = e.getToolType(0)
                val accepted = type == MotionEvent.TOOL_TYPE_STYLUS ||
                        type == MotionEvent.TOOL_TYPE_MOUSE ||
                        (allowFinger && type == MotionEvent.TOOL_TYPE_FINGER)
                if (!accepted) return false

                parent?.requestDisallowInterceptTouchEvent(true)
                requestUnbufferedDispatch(e) // olay gruplamasını azaltır, gecikmeyi düşürür
                beginStroke()
                feed(e, finish = false)
            }
            MotionEvent.ACTION_MOVE -> if (current != null) feed(e, finish = false)
            MotionEvent.ACTION_UP -> if (current != null) {
                feed(e, finish = true)
                commitStroke()
            }
            MotionEvent.ACTION_CANCEL -> cancelStroke() // avuç içi vb. iptal
        }
        return true
    }

    private fun beginStroke() {
        current = Stroke(inkColor, config)
        engine = PenEngine(config).also { it.begin() }
        lastPt = null
        paint.color = inkColor
    }

    private fun commitStroke() {
        current?.let {
            strokes.add(it)
            redoStack.clear()
        }
        current = null
        engine = null
        lastPt = null
    }

    private fun cancelStroke() {
        current = null
        engine = null
        lastPt = null
        redraw()
    }

    private fun pressureOf(e: MotionEvent, p: Float): Float =
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) p else 0.5f

    private fun feed(e: MotionEvent, finish: Boolean) {
        val s = current ?: return
        val eng = engine ?: return
        val c = bmpCanvas ?: return
        val dirty = RectF()

        // Gruplanmış (historical) örnekleri de işle: hızlı çizimde poligon görünümünü engeller.
        for (h in 0 until e.historySize) {
            val ip = InputPoint(
                e.getHistoricalX(h), e.getHistoricalY(h),
                pressureOf(e, e.getHistoricalPressure(h)),
                e.getHistoricalEventTime(h)
            )
            s.points.add(ip)
            drawPoints(c, eng.add(ip), dirty)
        }
        val ip = InputPoint(e.x, e.y, pressureOf(e, e.pressure), e.eventTime)
        s.points.add(ip)
        drawPoints(c, eng.add(ip), dirty)

        if (finish) drawPoints(c, eng.end(), dirty)

        if (!dirty.isEmpty) {
            val r = Rect()
            dirty.roundOut(r)
            r.inset(-3, -3)
            invalidate(r)
        }
    }

    // ---------- Çizim ----------

    /**
     * Her çift ardışık yoğun nokta arasında yuvarlak uçlu (round cap) bir çizgi çizilir.
     * Ofset-poligon yöntemindeki gibi normal ters dönmesi olmadığı için keskin
     * dönüşlerde bile kırılma/boşluk oluşmaz.
     */
    private fun drawPoints(c: Canvas, pts: List<StrokePoint>, dirty: RectF?) {
        for (p in pts) {
            val l = lastPt
            if (l == null) {
                paint.style = Paint.Style.FILL
                c.drawCircle(p.x, p.y, p.w / 2f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (l.w + p.w) / 2f
                c.drawLine(l.x, l.y, p.x, p.y, paint)
            }
            dirty?.union(p.x - p.w, p.y - p.w, p.x + p.w, p.y + p.w)
            lastPt = p
        }
    }

    private fun redraw() {
        val b = bmp ?: return
        val c = bmpCanvas ?: return
        b.eraseColor(backgroundColorInt)
        for (s in strokes) {
            paint.color = s.color
            val eng = PenEngine(s.config).also { it.begin() }
            lastPt = null
            for (p in s.points) drawPoints(c, eng.add(p), null)
            drawPoints(c, eng.end(), null)
            lastPt = null
        }
        invalidate()
    }
}
