package com.example.odysseyglyph

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 25x25 LED rendering: area downsample + base/detail tone mapping done AT THE LED RESOLUTION.
 * Replaces downsampleSuperRes / calculateAdaptiveRange / S-curve for enhancement modes 0 and 1.
 * Mode 2 (legacy) stays exactly as it is.
 */
object LedTone {
    const val N = 25
    private const val C = (N - 1) / 2f
    private const val R_SQ = (N / 2f) * (N / 2f)

    val mask = BooleanArray(N * N) { i ->
        val x = i % N; val y = i / N
        (x - C) * (x - C) + (y - C) * (y - C) <= R_SQ
    }

    /** Fractional-coverage box filter: every source pixel contributes exactly its area share. */
    fun areaDownsample(luma: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(N * N)
        val sx = w.toFloat() / N
        val sy = h.toFloat() / N
        for (gy in 0 until N) {
            val y0 = gy * sy; val y1 = (gy + 1) * sy
            for (gx in 0 until N) {
                val x0 = gx * sx; val x1 = (gx + 1) * sx
                var sum = 0f; var wsum = 0f
                var y = y0.toInt()
                while (y < y1 && y < h) {
                    val wy = min(y + 1f, y1) - max(y.toFloat(), y0)
                    var x = x0.toInt()
                    while (x < x1 && x < w) {
                        val wx = min(x + 1f, x1) - max(x.toFloat(), x0)
                        val wt = wx * wy
                        sum += luma[y * w + x] * wt
                        wsum += wt
                        x++
                    }
                    y++
                }
                out[gy * N + gx] = if (wsum > 0f) sum / wsum else 0f
            }
        }
        return out
    }

    /** Pixels (ARGB) -> 25x25 luma, as bytes so it drops into the existing rawFrames list. */
    fun downsampleArea(pixels: IntArray, w: Int, h: Int): ByteArray {
        val luma = FloatArray(w * h)
        for (i in luma.indices) {
            val p = pixels[i]
            luma[i] = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
        }
        val g = areaDownsample(luma, w, h)
        return ByteArray(N * N) { g[it].toInt().coerceIn(0, 255).toByte() }
    }

    /** Gaussian blur that only looks at LEDs inside the circle (no dark corners bleeding in). */
    private fun maskedGaussian(src: FloatArray, sigma: Float): FloatArray {
        val r = Math.ceil(sigma * 3.0).toInt()
        val k = FloatArray(2 * r + 1) { i -> val d = (i - r).toFloat(); exp(-d * d / (2f * sigma * sigma)) }
        val out = FloatArray(N * N)
        for (y in 0 until N) for (x in 0 until N) {
            var s = 0f; var ws = 0f
            for (dy in -r..r) {
                val yy = y + dy; if (yy < 0 || yy >= N) continue
                for (dx in -r..r) {
                    val xx = x + dx; if (xx < 0 || xx >= N) continue
                    val i = yy * N + xx
                    if (!mask[i]) continue
                    val wt = k[dy + r] * k[dx + r]
                    s += src[i] * wt; ws += wt
                }
            }
            out[y * N + x] = if (ws > 0f) s / ws else src[y * N + x]
        }
        return out
    }

    /** Smoothed black/white points so video doesn't flicker when the frame content shifts. */
    class ToneState { var lo = Float.NaN; var hi = Float.NaN }

    /**
     * @param sigma   scale (in LEDs) that separates "lighting" from "detail". 2.5 = about a face feature.
     * @param illum   how much of the lighting difference to keep (1 = all, 0 = none). Lower = shadow side stays readable.
     * @param gain    detail boost.
     * @param gamma   >1 pushes mids down so the diffused LEDs don't merge into a grey wash.
     */
    fun toneMap(
        frame: ByteArray,
        state: ToneState? = null,
        sigma: Float = 2.5f, illum: Float = 0.4f, gain: Float = 2.2f, gamma: Float = 1.4f,
        smoothing: Float = 0.2f, minRange: Float = 30f
    ): FloatArray {
        val g = FloatArray(N * N) { (frame[it].toInt() and 0xFF).toFloat() }
        var gm = 0f; var n = 0
        for (i in g.indices) if (mask[i]) { gm += g[i]; n++ }
        gm /= n

        val base = maskedGaussian(g, sigma)
        val o = FloatArray(N * N)
        val active = ArrayList<Float>(n)
        for (i in g.indices) {
            if (!mask[i]) continue
            o[i] = gm + (base[i] - gm) * illum + (g[i] - base[i]) * gain
            active.add(o[i])
        }
        active.sort()
        var lo = active[(active.size * 0.02f).toInt()]
        var hi = active[min(active.size - 1, (active.size * 0.98f).toInt())]
        if (state != null) {
            if (state.lo.isNaN()) { state.lo = lo; state.hi = hi }
            else { state.lo += (lo - state.lo) * smoothing; state.hi += (hi - state.hi) * smoothing }
            lo = state.lo; hi = state.hi
        }
        val range = max(hi - lo, minRange)   // flat/dark frames stay dark instead of being stretched into noise

        return FloatArray(N * N) { i ->
            if (!mask[i]) 0f
            else (((o[i] - lo) / range).coerceIn(0f, 1f)).pow(gamma) * 255f
        }
    }
}
