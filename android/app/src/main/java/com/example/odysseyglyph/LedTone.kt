package com.example.odysseyglyph

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 25x25 LED rendering engine:
 *  1. Chromatic Saliency Projection: Projects vivid hue contrast (e.g. pink donuts, red eyes/tongues)
 *     into perceptual luminance so color landmarks do not dilute into gray fur/skin.
 *  2. Retinex Macro-Illumination Equalization: Separable running-sum box blur compresses global
 *     lighting gradients (harsh sunlit mane vs shaded face, bright belly flood) to prevent optical bloom.
 *  3. Morphological Landmark Anchoring: Fast separable min/max pooling extracts bright micro-features
 *     (fangs, glints) via TopHat and deep dark crevices (pupils, nostrils, donut hole, open mouth) via BlackHat.
 *  4. Fractional Area Downsampling: Strict area-coverage downsampling to 25x25 LED grid.
 *  5. Two-Scale Spatial Frequency Decomposition: Separates fine detail (fangs, whiskers) from mid contours
 *     (facial features) and coarse lighting at the 25x25 matrix level.
 *  6. Median-Anchored Asymmetric Filmic Tone Mapping: Splits tonal range at the dynamic weighted median,
 *     steeply carving deep blacks for anatomical crevices and punchy highlights without blowout.
 *  7. Optical Lateral Inhibition (PSF Anti-Bloom): 3x3 deconvolution compensating for the physical
 *     Nothing Phone (3) matrix diffuser spread.
 */
object LedTone {
    const val N = 25
    private const val C = (N - 1) / 2f
    private const val R_SQ = (N / 2f) * (N / 2f)

    val mask = BooleanArray(N * N) { i ->
        val x = i % N; val y = i / N
        (x - C) * (x - C) + (y - C) * (y - C) <= R_SQ
    }

    // 3x3 Optical PSF de-blooming kernel calibrated for Nothing Phone (3) matrix diffuser
    private val PSF_KERNEL = floatArrayOf(
        0.06f, 0.12f, 0.06f,
        0.12f, 0.28f, 0.12f,
        0.06f, 0.12f, 0.06f
    )

    data class Params(
        val sigmaFine: Float = 1.0f,
        val sigmaCoarse: Float = 2.8f,
        val illum: Float = 0.30f,
        val midGain: Float = 2.4f,
        val fineGain: Float = 4.2f,
        val punch: Float = 8.0f,
        val gamma: Float = 2.2f,
        val centerSigma: Float = 8f,
        val lowPct: Float = 0.03f,
        val midPct: Float = 0.50f,
        val highPct: Float = 0.97f,
        val minRange: Float = 12f,
        val smoothing: Float = 0.2f,
        val debloom: Float = 0.40f,
        val maxBrightness: Float = 135f,
        // Legacy parameter aliases for backward compatibility
        val sigma: Float = 2.0f,
        val gain: Float = 3.2f
    )

    val SOFT = Params(
        illum = 0.40f, midGain = 2.0f, fineGain = 3.2f, punch = 6.5f, debloom = 0.30f, maxBrightness = 115f
    )
    val NORMAL = Params()
    val MAX = Params(
        illum = 0.25f, midGain = 2.8f, fineGain = 4.8f, punch = 9.0f, debloom = 0.45f, maxBrightness = 155f
    )

    /**
     * Fast running-sum separable box blur for Retinex illumination estimation.
     * Runs in strict O(W * H) time with zero allocations beyond the destination buffer.
     */
    private fun fastBoxBlur(src: FloatArray, w: Int, h: Int, radius: Int, scratch: ScratchBuffer? = null): FloatArray {
        if (radius <= 0) return src.clone()
        val temp = scratch?.temp ?: FloatArray(w * h)
        // Horizontal pass
        for (y in 0 until h) {
            val rowOffset = y * w
            var acc = 0f
            var cnt = 0
            val initEnd = min(w, radius + 1)
            for (x in 0 until initEnd) {
                acc += src[rowOffset + x]
                cnt++
            }
            for (x in 0 until w) {
                temp[rowOffset + x] = acc / cnt
                val xr = x + radius + 1
                if (xr < w) { acc += src[rowOffset + xr]; cnt++ }
                val xl = x - radius
                if (xl >= 0) { acc -= src[rowOffset + xl]; cnt-- }
            }
        }
        // Vertical pass
        val out = scratch?.illum ?: FloatArray(w * h)
        for (x in 0 until w) {
            var acc = 0f
            var cnt = 0
            val initEnd = min(h, radius + 1)
            for (y in 0 until initEnd) {
                acc += temp[y * w + x]
                cnt++
            }
            for (y in 0 until h) {
                out[y * w + x] = acc / cnt
                val yr = y + radius + 1
                if (yr < h) { acc += temp[yr * w + x]; cnt++ }
                val yl = y - radius
                if (yl >= 0) { acc -= temp[yl * w + x]; cnt-- }
            }
        }
        return out
    }

    /**
     * Fast separable min/max filter for morphological landmark extraction (TopHat / BlackHat).
     */
    private fun fastMinMaxFilter(src: FloatArray, w: Int, h: Int, radius: Int, scratch: ScratchBuffer? = null): Pair<FloatArray, FloatArray> {
        val tempMin = scratch?.tempMin ?: FloatArray(w * h)
        val tempMax = scratch?.tempMax ?: FloatArray(w * h)
        // Horizontal pass
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var minV = src[rowOffset + x]
                var maxV = minV
                val startX = max(0, x - radius)
                val endX = min(w - 1, x + radius)
                for (xx in startX..endX) {
                    val v = src[rowOffset + xx]
                    if (v < minV) minV = v
                    if (v > maxV) maxV = v
                }
                tempMin[rowOffset + x] = minV
                tempMax[rowOffset + x] = maxV
            }
        }
        // Vertical pass
        val outMin = scratch?.minF ?: FloatArray(w * h)
        val outMax = scratch?.maxF ?: FloatArray(w * h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var minV = tempMin[y * w + x]
                var maxV = tempMax[y * w + x]
                val startY = max(0, y - radius)
                val endY = min(h - 1, y + radius)
                for (yy in startY..endY) {
                    val vMin = tempMin[yy * w + x]
                    val vMax = tempMax[yy * w + x]
                    if (vMin < minV) minV = vMin
                    if (vMax > maxV) maxV = vMax
                }
                outMin[y * w + x] = minV
                outMax[y * w + x] = maxV
            }
        }
        return outMin to outMax
    }

    /**
     * Backward-compatible separable min-pooling filter for dark feature preservation.
     */
    fun protectDarkFeatures(luma: FloatArray, w: Int, h: Int, radius: Int = 1): FloatArray {
        if (radius <= 0 || w <= N || h <= N) return luma
        val (minF, _) = fastMinMaxFilter(luma, w, h, radius)
        val out = FloatArray(w * h)
        for (i in out.indices) {
            out[i] = luma[i] * 0.65f + minF[i] * 0.35f
        }
        return out
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

    /**
     * Full feature-preserving downsampling pipeline:
     * Chromatic Saliency -> Retinex Illumination Equalization -> Morphological Landmark Anchoring -> Area Downsample.
     */
    fun downsampleArea(pixels: IntArray, w: Int, h: Int, scratch: ScratchBuffer? = null): ByteArray {
        val total = w * h
        scratch?.ensureCapacity(total)
        val lumaSal = scratch?.lumaSal ?: FloatArray(total)
        for (i in 0 until total) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            val baseLuma = 0.299f * r + 0.587f * g + 0.114f * b
            // Chromatic saliency projection: separates red/pink landmarks (donuts, tongues, emojis) from tan/fur
            val chromaRG = max(0f, r - g)
            val chromaExcess = max(0f, chromaRG - 25f) // ignore baseline yellowish warmth of tan fur
            val chromaSal = min(120f, chromaExcess * 1.2f)
            lumaSal[i] = baseLuma + 0.60f * chromaSal
        }

        // If resolution is high, apply Retinex macro-illumination flattening + morphological feature anchoring
        if (w > 50 && h > 50) {
            val boxRadius = max(4, w / 14)
            val illum = fastBoxBlur(lumaSal, w, h, boxRadius, scratch)
            var sumIllum = 0f
            for (i in 0 until total) sumIllum += illum[i]
            val meanIllum = sumIllum / total

            val lumaEq = scratch?.lumaEq ?: FloatArray(total)
            for (i in 0 until total) {
                val il = illum[i]
                lumaEq[i] = (meanIllum + (il - meanIllum) * 0.35f + (lumaSal[i] - il) * 1.4f).coerceIn(0f, 255f)
            }

            // Morphological landmark anchoring: boost thin bright fangs/glints, carve deep dark pupils/holes
            val morphRadius = max(1, (w / 50f).roundToInt()).coerceIn(1, 3)
            val (minF, maxF) = fastMinMaxFilter(lumaEq, w, h, morphRadius, scratch)

            val anchored = scratch?.anchored ?: FloatArray(total)
            for (i in 0 until total) {
                val orig = lumaEq[i]
                val tophat = max(0f, orig - minF[i])
                val blackhat = max(0f, maxF[i] - orig)
                anchored[i] = (orig + tophat * 1.8f - blackhat * 2.2f).coerceIn(0f, 255f)
            }

            val g = areaDownsample(anchored, w, h)
            return ByteArray(N * N) { g[it].toInt().coerceIn(0, 255).toByte() }
        } else {
            val g = areaDownsample(lumaSal, w, h)
            return ByteArray(N * N) { g[it].toInt().coerceIn(0, 255).toByte() }
        }
    }

    /** Gaussian blur that only looks at LEDs inside the circle (no dark corners bleeding in). */
    private fun maskedGaussian(src: FloatArray, sigma: Float): FloatArray {
        val r = Math.ceil(sigma * 2.5).toInt()
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

    /** Percentiles weighted towards the centre of the circle, returning (lo, mid, hi). */
    private fun weightedRange(o: FloatArray, p: Params): Triple<Float, Float, Float> {
        val idx = (0 until N * N).filter { mask[it] }.sortedBy { o[it] }
        val w = FloatArray(idx.size) { k ->
            val x = idx[k] % N - C; val y = idx[k] / N - C
            exp(-(x * x + y * y) / (2f * p.centerSigma * p.centerSigma))
        }
        val total = w.sum()
        var cum = 0f
        var lo = o[idx.first()]
        var mid = o[idx[idx.size / 2]]
        var hi = o[idx.last()]
        var gotLo = false
        var gotMid = false
        for (k in idx.indices) {
            cum += w[k] / total
            if (!gotLo && cum >= p.lowPct) { lo = o[idx[k]]; gotLo = true }
            if (!gotMid && cum >= p.midPct) { mid = o[idx[k]]; gotMid = true }
            if (cum >= p.highPct) { hi = o[idx[k]]; break }
        }
        return Triple(lo, mid, hi)
    }

    class ScratchBuffer(size: Int = 0) {
        var capacity = size
        var lumaSal = FloatArray(size)
        var illum = FloatArray(size)
        var lumaEq = FloatArray(size)
        var minF = FloatArray(size)
        var maxF = FloatArray(size)
        var tempMin = FloatArray(size)
        var tempMax = FloatArray(size)
        var temp = FloatArray(size)
        var anchored = FloatArray(size)

        fun ensureCapacity(needed: Int) {
            if (needed > capacity) {
                capacity = needed
                lumaSal = FloatArray(needed)
                illum = FloatArray(needed)
                lumaEq = FloatArray(needed)
                minF = FloatArray(needed)
                maxF = FloatArray(needed)
                tempMin = FloatArray(needed)
                tempMax = FloatArray(needed)
                temp = FloatArray(needed)
                anchored = FloatArray(needed)
            }
        }
    }

    /** Smoothed black/mid/white points so video doesn't flicker when frame content shifts. */
    class ToneState {
        var lo = Float.NaN
        var mid = Float.NaN
        var hi = Float.NaN
    }

    /** frame = 25x25 luma bytes -> 25x25 LED values 0..255 (corners 0). Apply invert/brightness after this. */
    fun toneMap(
        frame: ByteArray,
        state: ToneState? = null,
        p: Params = NORMAL,
        brightnessMulti: Float = 1.0f
    ): FloatArray {
        val g = FloatArray(N * N) { (frame[it].toInt() and 0xFF).toFloat() }
        var gm = 0f; var n = 0
        for (i in g.indices) if (mask[i]) { gm += g[i]; n++ }
        gm /= n

        // Two-scale spatial frequency decomposition at LED resolution
        val fineBase = maskedGaussian(g, p.sigmaFine)
        val coarseBase = maskedGaussian(g, p.sigmaCoarse)
        val fineDetail = FloatArray(N * N) { g[it] - fineBase[it] }
        val midDetail = FloatArray(N * N) { fineBase[it] - coarseBase[it] }

        val o = FloatArray(N * N)
        for (i in g.indices) {
            if (!mask[i]) continue
            o[i] = gm + (coarseBase[i] - gm) * p.illum + midDetail[i] * p.midGain + fineDetail[i] * p.fineGain
        }

        var (lo, mid, hi) = weightedRange(o, p)
        if (state != null) {
            if (state.lo.isNaN()) {
                state.lo = lo; state.mid = mid; state.hi = hi
            } else {
                state.lo += (lo - state.lo) * p.smoothing
                state.mid += (mid - state.mid) * p.smoothing
                state.hi += (hi - state.hi) * p.smoothing
            }
            lo = state.lo; mid = state.mid; hi = state.hi
        }

        // Calibrated power budgeting:
        // Base flood headroom is scaled by brightnessMulti.
        // Isolated micro-landmarks (fangs, pupils, donut ring edges) get up to pinpointHeadroom.
        val multi = brightnessMulti.coerceIn(0.1f, 1.5f)
        val baseHeadroom = p.maxBrightness * multi
        val pinpointHeadroom = min(255f, (p.maxBrightness + 35f) * multi)

        val s0 = 1f / (1f + exp(p.punch / 2f))
        val s1 = 1f / (1f + exp(-p.punch / 2f))
        val res = FloatArray(N * N) { i ->
            if (!mask[i]) 0f
            else {
                val valO = o[i]
                val normV = if (valO <= mid) {
                    val t = ((valO - lo) / max(mid - lo, p.minRange)).coerceIn(0f, 1f)
                    t * 0.5f
                } else {
                    val t = ((valO - mid) / max(hi - mid, p.minRange)).coerceIn(0f, 1f)
                    0.5f + t * 0.5f
                }
                var v = normV
                if (p.punch > 0f) {
                    v = ((1f / (1f + exp(-p.punch * (v - 0.5f))) - s0) / (s1 - s0)).coerceIn(0f, 1f)
                }
                val fd = max(0f, fineDetail[i])
                val localCap = baseHeadroom + min(1f, fd / 20f) * (pinpointHeadroom - baseHeadroom)
                v.pow(p.gamma) * localCap
            }
        }

        // Lateral inhibition / PSF de-blooming scales dynamically with brightness
        val effectiveDebloom = (p.debloom + 0.10f * (multi - 1f)).coerceIn(0.25f, 0.55f)
        val maxCap = min(255f, pinpointHeadroom)
        if (effectiveDebloom > 0f) {
            val debloomed = FloatArray(N * N)
            for (y in 0 until N) {
                for (x in 0 until N) {
                    val idx = y * N + x
                    if (!mask[idx]) continue
                    var bSum = 0f
                    var wSum = 0f
                    for (dy in -1..1) {
                        val yy = y + dy
                        if (yy !in 0 until N) continue
                        for (dx in -1..1) {
                            val xx = x + dx
                            if (xx !in 0 until N) continue
                            val nIdx = yy * N + xx
                            if (!mask[nIdx]) continue
                            val wt = PSF_KERNEL[(dy + 1) * 3 + (dx + 1)]
                            bSum += res[nIdx] * wt
                            wSum += wt
                        }
                    }
                    val localBloom = if (wSum > 0f) bSum / wSum else res[idx]
                    debloomed[idx] = (res[idx] - effectiveDebloom * (localBloom - res[idx])).coerceIn(0f, maxCap)
                }
            }
            return debloomed
        }

        return res
    }

    /**
     * Calibration frame: 25 columns ramping 0..255.
     */
    fun calibrationRamp(): ByteArray = ByteArray(N * N) { i ->
        if (!mask[i]) 0 else ((i % N) * 255 / (N - 1)).toByte()
    }

    fun gammaFromHalfColumn(col: Int): Float {
        val v = col.coerceIn(1, N - 1) * 255f / (N - 1)
        return ln(255f / v) / ln(2f)
    }
}
