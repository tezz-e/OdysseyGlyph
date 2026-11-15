package com.example.odysseyglyph

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 25x25 LED rendering: area downsample + base/detail tone mapping done AT THE LED RESOLUTION.
 * Used for enhancement modes 0 and 1. Mode 2 (legacy) stays as it is.
 *
 * Pipeline: area-average -> split into "lighting" (blur) and "detail" -> squash lighting, boost detail
 *           -> centre-weighted black/white points -> steep S-curve -> gamma pre-compensation for the LED.
 */
object LedTone {
    const val N = 25
    private const val C = (N - 1) / 2f
    private const val R_SQ = (N / 2f) * (N / 2f)

    val mask = BooleanArray(N * N) { i ->
        val x = i % N; val y = i / N
        (x - C) * (x - C) + (y - C) * (y - C) <= R_SQ
    }

    /**
     * @param sigma       scale (in LEDs) separating "lighting" from "detail". Smaller = finer detail kept.
     * @param illum       how much lighting difference survives (1 = all, 0 = none). Lower = shadow side stays readable.
     * @param gain        detail boost.
     * @param punch       S-curve steepness (0 = off, 7 = punchy, 10 = nearly on/off).
     * @param gamma       LED pre-compensation. ~2.2 if the LED is linear in the value you send. Calibrate with calibrationRamp().
     * @param centerSigma black/white points are measured mostly from the centre (the subject), not the background.
     */
    data class Params(
        val sigma: Float = 2.0f,
        val illum: Float = 0.4f,
        val gain: Float = 3.2f,
        val punch: Float = 8f,
        val gamma: Float = 2.2f,
        val centerSigma: Float = 8f,
        val lowPct: Float = 0.03f,
        val highPct: Float = 0.97f,
        val minRange: Float = 30f,
        val smoothing: Float = 0.2f
    )

    val SOFT = Params(sigma = 2.5f, gain = 2.6f, punch = 7f)
    val NORMAL = Params()
    val MAX = Params(gain = 4.2f, punch = 10f)

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

    /** Pixels (ARGB) -> 25x25 luma bytes, so it drops into the existing rawFrames list. */
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

    /** Percentiles weighted towards the centre of the circle. */
    private fun weightedRange(o: FloatArray, p: Params): Pair<Float, Float> {
        val idx = (0 until N * N).filter { mask[it] }.sortedBy { o[it] }
        val w = FloatArray(idx.size) { k ->
            val x = idx[k] % N - C; val y = idx[k] / N - C
            exp(-(x * x + y * y) / (2f * p.centerSigma * p.centerSigma))
        }
        val total = w.sum()
        var cum = 0f
        var lo = o[idx.first()]; var hi = o[idx.last()]
        var gotLo = false
        for (k in idx.indices) {
            cum += w[k] / total
            if (!gotLo && cum >= p.lowPct) { lo = o[idx[k]]; gotLo = true }
            if (cum >= p.highPct) { hi = o[idx[k]]; break }
        }
        return lo to hi
    }

    /** Smoothed black/white points so video doesn't flicker when the frame content shifts. */
    class ToneState { var lo = Float.NaN; var hi = Float.NaN }

    /** frame = 25x25 luma bytes -> 25x25 LED values 0..255 (corners 0). Apply invert/brightness after this. */
    fun toneMap(frame: ByteArray, state: ToneState? = null, p: Params = NORMAL): FloatArray {
        val g = FloatArray(N * N) { (frame[it].toInt() and 0xFF).toFloat() }
        var gm = 0f; var n = 0
        for (i in g.indices) if (mask[i]) { gm += g[i]; n++ }
        gm /= n

        val base = maskedGaussian(g, p.sigma)
        val o = FloatArray(N * N)
        for (i in g.indices) {
            if (!mask[i]) continue
            o[i] = gm + (base[i] - gm) * p.illum + (g[i] - base[i]) * p.gain
        }
        var (lo, hi) = weightedRange(o, p)
        if (state != null) {
            if (state.lo.isNaN()) { state.lo = lo; state.hi = hi }
            else { state.lo += (lo - state.lo) * p.smoothing; state.hi += (hi - state.hi) * p.smoothing }
            lo = state.lo; hi = state.hi
        }
        val range = max(hi - lo, p.minRange)   // flat/dark frames stay dark instead of being stretched into noise

        val s0 = 1f / (1f + exp(p.punch / 2f))
        val s1 = 1f / (1f + exp(-p.punch / 2f))
        return FloatArray(N * N) { i ->
            if (!mask[i]) 0f
            else {
                var v = ((o[i] - lo) / range).coerceIn(0f, 1f)
                if (p.punch > 0f) v = ((1f / (1f + exp(-p.punch * (v - 0.5f))) - s0) / (s1 - s0)).coerceIn(0f, 1f)
                v.pow(p.gamma) * 255f
            }
        }
    }

    /**
     * Calibration frame: 25 columns ramping 0..255 (column 0 = off, column 24 = full).
     * Push it with GlyphFrameBus.postFrame(LedTone.calibrationRamp()).
     * Look at the matrix and pick the column that looks HALF as bright as the brightest column,
     * then pass it to gammaFromHalfColumn(col) and use that as Params.gamma.
     */
    fun calibrationRamp(): ByteArray = ByteArray(N * N) { i ->
        if (!mask[i]) 0 else ((i % N) * 255 / (N - 1)).toByte()
    }

    fun gammaFromHalfColumn(col: Int): Float {
        val v = col.coerceIn(1, N - 1) * 255f / (N - 1)
        return ln(255f / v) / ln(2f)
    }
}
