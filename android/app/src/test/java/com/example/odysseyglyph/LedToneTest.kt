package com.example.odysseyglyph

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LedToneTest {

    @Test
    fun testMaskGeometry_matchesNothingPhone3PhysicalLayout() {
        assertEquals("Matrix dimensions must be 25x25", 25 * 25, LedTone.mask.size)
        val activeLeds = LedTone.mask.count { it }
        // Circle of radius 12.5 centered at (12, 12) contains exactly 489 active LEDs
        assertEquals("Active circular LED count must be 489", 489, activeLeds)
        val inactiveCorners = LedTone.mask.count { !it }
        assertEquals("Inactive corner count must be 136", 136, inactiveCorners)
        // Corners must be inactive
        assertFalse("Top-left corner (0, 0) must be inactive", LedTone.mask[0])
        assertFalse("Top-right corner (0, 24) must be inactive", LedTone.mask[24])
        assertFalse("Bottom-left corner (24, 0) must be inactive", LedTone.mask[24 * 25])
        assertFalse("Bottom-right corner (24, 24) must be inactive", LedTone.mask[24 * 25 + 24])
        // Center must be active
        assertTrue("Center (12, 12) must be active", LedTone.mask[12 * 25 + 12])
    }

    @Test
    fun testDownsampleArea_uniformInput_producesUniformOutput() {
        val w = 100
        val h = 100
        // Uniform gray (128, 128, 128)
        val pixels = IntArray(w * h) { 0xFF808080.toInt() }
        val output = LedTone.downsampleArea(pixels, w, h)
        assertEquals("Output size must be 625 bytes", 625, output.size)
        // Center pixel should be around 128
        val centerVal = output[12 * 25 + 12].toInt() and 0xFF
        assertTrue("Uniform 128 input should produce ~128 center output, got $centerVal", abs(centerVal - 128) < 5)
    }

    @Test
    fun testDownsampleArea_chromaticSaliency_amplifiesRedPinkFeatures() {
        val w = 60
        val h = 60
        // Image A: Tan fur (R=180, G=140, B=90)
        val tanPixels = IntArray(w * h) { 
            (0xFF shl 24) or (180 shl 16) or (140 shl 8) or 90 
        }
        val tanOut = LedTone.downsampleArea(tanPixels, w, h)
        val tanLuma = tanOut[12 * 25 + 12].toInt() and 0xFF

        // Image B: Pink donut (R=220, G=120, B=150)
        // Under standard BT.601, tan fur = 146, pink donut = 153 (only 7 levels difference)
        // Under chromatic saliency, pink donut should receive a significant boost
        val pinkPixels = IntArray(w * h) { 
            (0xFF shl 24) or (220 shl 16) or (120 shl 8) or 150 
        }
        val pinkOut = LedTone.downsampleArea(pinkPixels, w, h)
        val pinkLuma = pinkOut[12 * 25 + 12].toInt() and 0xFF

        assertTrue(
            "Pink donut ($pinkLuma) must be significantly separated from tan fur ($tanLuma) by chromatic saliency",
            pinkLuma > tanLuma + 20
        )
    }

    @Test
    fun testToneMap_inactiveCornersAreStrictlyZero() {
        val frame = ByteArray(625) { 200.toByte() }
        val mapped = LedTone.toneMap(frame)
        for (i in 0 until 625) {
            if (!LedTone.mask[i]) {
                assertEquals("Inactive corner LED at $i must be strictly 0", 0f, mapped[i], 0.0001f)
            }
        }
    }

    @Test
    fun testToneMap_maxBrightnessHeadroomEnforced() {
        val frame = ByteArray(625) { 255.toByte() }
        val mapped = LedTone.toneMap(frame, p = LedTone.NORMAL)
        for (i in 0 until 625) {
            if (LedTone.mask[i]) {
                assertTrue(
                    "LED at $i exceeds maxBrightness headroom (${LedTone.NORMAL.maxBrightness}): ${mapped[i]}",
                    mapped[i] <= LedTone.NORMAL.maxBrightness + 0.1f
                )
            }
        }
    }

    @Test
    fun testToneMap_toneStateSmoothingAcrossFrames() {
        val state = LedTone.ToneState()
        val darkFrame = ByteArray(625) { 30.toByte() }
        val brightFrame = ByteArray(625) { 220.toByte() }

        LedTone.toneMap(darkFrame, state)
        val initialMid = state.mid
        assertFalse("Initial state mid must be initialized", initialMid.isNaN())

        LedTone.toneMap(brightFrame, state)
        val smoothedMid = state.mid
        // State mid should transition smoothly towards bright frame, not jump instantaneously
        assertTrue("Smoothed mid should increase", smoothedMid > initialMid)
        assertTrue("Smoothed mid must be damped by smoothing factor", smoothedMid < 200f)
    }

    @Test
    fun testProtectDarkFeatures_preservesSubpixelCrevices() {
        val w = 60
        val h = 60
        // White background with a 1px black pupil in the center
        val luma = FloatArray(w * h) { 240f }
        luma[30 * w + 30] = 0f

        val protected = LedTone.protectDarkFeatures(luma, w, h, radius = 1)
        // Center pixel should be kept dark
        val centerVal = protected[30 * w + 30]
        assertTrue("Protected center pixel must remain dark (< 100), got $centerVal", centerVal < 100f)
    }
}
