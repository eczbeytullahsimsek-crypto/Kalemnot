package com.example.ink

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.pow

/** Kalem ayarları. Hissi değiştirmek için bunlarla oynayın. */
data class PenConfig(
    val minWidth: Float = 1.5f,          // en hafif basınçtaki kalınlık (px)
    val maxWidth: Float = 7f,            // en sert basınçtaki kalınlık (px)
    val pressureGamma: Float = 0.8f,     // <1: hafif basınçta daha kalın, >1: daha ince
    val pressureSmoothing: Float = 0.25f,// 0..1, küçük = daha yumuşak kalınlık geçişi
    val minCutoff: Float = 1.7f,         // küçük = yavaşken daha yumuşak (ama biraz gecikmeli)
    val beta: Float = 0.02f,             // büyük = hızlıyken daha az gecikme
    val sampleStep: Float = 1f,          // eğri üzerindeki örnekleme aralığı (px)
    val minPointDistance: Float = 0.5f   // bundan yakın noktaları yok say
)

data class InputPoint(val x: Float, val y: Float, val pressure: Float, val timeMs: Long)

data class StrokePoint(val x: Float, val y: Float, val w: Float)

/**
 * One Euro Filter: yavaş harekette titremeyi bastırır, hızlı harekette gecikme yapmaz.
 */
class OneEuroFilter(
    private val minCutoff: Float,
    private val beta: Float,
    private val dCutoff: Float = 1f
) {
    private var xPrev = 0f
    private var dxPrev = 0f
    private var tPrev = 0L
    private var initialized = false

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun filter(x: Float, timeMs: Long): Float {
        if (!initialized) {
            initialized = true
            xPrev = x
            dxPrev = 0f
            tPrev = timeMs
            return x
        }
        val dt = (timeMs - tPrev).coerceAtLeast(1L) / 1000f
        tPrev = timeMs
        val dx = (x - xPrev) / dt
        val aD = alpha(dCutoff, dt)
        val dxHat = aD * dx + (1f - aD) * dxPrev
        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(cutoff, dt)
        val xHat = a * x + (1f - a) * xPrev
        xPrev = xHat
        dxPrev = dxHat
        return xHat
    }
}

/**
 * Giriş noktalarını alır, yoğun ve pürüzsüz StrokePoint listesi üretir.
 * Android'e bağımlı değildir; birim test yazılabilir.
 *
 * Yöntem: filtrelenmiş noktaların orta noktaları arasında, noktanın kendisini
 * kontrol noktası alan ikinci derece Bezier eğrileri. Bu yöntem birleşim yerlerinde
 * teğet sürekliliği (C1) sağlar, yani köşe/kırılma oluşmaz.
 */
class PenEngine(private val cfg: PenConfig = PenConfig()) {

    private var fx = OneEuroFilter(cfg.minCutoff, cfg.beta)
    private var fy = OneEuroFilter(cfg.minCutoff, cfg.beta)
    private val ctrl = ArrayList<StrokePoint>(4)
    private var count = 0
    private var cursor: StrokePoint? = null
    private var pressure = 0f

    fun begin() {
        fx = OneEuroFilter(cfg.minCutoff, cfg.beta)
        fy = OneEuroFilter(cfg.minCutoff, cfg.beta)
        ctrl.clear()
        count = 0
        cursor = null
    }

    /** Yeni çizilmesi gereken yoğun noktaları döndürür. */
    fun add(p: InputPoint): List<StrokePoint> {
        val raw = p.pressure.coerceIn(0f, 1f)
        pressure = if (count == 0) raw else pressure + (raw - pressure) * cfg.pressureSmoothing

        val sp = StrokePoint(
            fx.filter(p.x, p.timeMs),
            fy.filter(p.y, p.timeMs),
            widthFor(pressure)
        )

        val last = ctrl.lastOrNull()
        if (last != null && hypot(sp.x - last.x, sp.y - last.y) < cfg.minPointDistance) {
            return emptyList()
        }

        ctrl.add(sp)
        if (ctrl.size > 3) ctrl.removeAt(0)
        count++

        val n = ctrl.size
        return when (count) {
            1 -> {
                cursor = sp
                listOf(sp) // başlangıç noktası (tek nokta = nokta/damla)
            }
            2 -> {
                val end = mid(ctrl[n - 2], ctrl[n - 1])
                val out = quad(cursor!!, mid(cursor!!, end), end)
                cursor = end
                out
            }
            else -> {
                val end = mid(ctrl[n - 2], ctrl[n - 1])
                val out = quad(cursor!!, ctrl[n - 2], end)
                cursor = end
                out
            }
        }
    }

    /** Kalem kalkınca çağırın: son parçayı gerçek son noktaya bağlar. */
    fun end(): List<StrokePoint> {
        val c = cursor ?: return emptyList()
        val last = ctrl.lastOrNull() ?: return emptyList()
        if (count < 2) return emptyList()
        val out = quad(c, mid(c, last), last)
        cursor = last
        return out
    }

    private fun widthFor(p: Float): Float =
        cfg.minWidth + (cfg.maxWidth - cfg.minWidth) * p.pow(cfg.pressureGamma)

    private fun mid(a: StrokePoint, b: StrokePoint) =
        StrokePoint((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.w + b.w) / 2f)

    /** Konum VE kalınlık aynı Bezier ile interpolasyon yapılır; kalınlık sıçraması olmaz. */
    private fun quad(a: StrokePoint, c: StrokePoint, b: StrokePoint): List<StrokePoint> {
        val len = hypot(c.x - a.x, c.y - a.y) + hypot(b.x - c.x, b.y - c.y)
        val n = ceil(len / cfg.sampleStep).toInt().coerceIn(1, 128)
        val out = ArrayList<StrokePoint>(n)
        for (i in 1..n) {
            val t = i / n.toFloat()
            val u = 1f - t
            val k0 = u * u
            val k1 = 2f * u * t
            val k2 = t * t
            out.add(
                StrokePoint(
                    k0 * a.x + k1 * c.x + k2 * b.x,
                    k0 * a.y + k1 * c.y + k2 * b.y,
                    k0 * a.w + k1 * c.w + k2 * b.w
                )
            )
        }
        return out
    }
}
