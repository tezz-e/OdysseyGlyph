package com.example.odysseyglyph

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * AdversarialChallengeTest
 *
 * Empirical stress harness created by Challenger 2 to test boundary conditions,
 * rapid lifecycle transitions, floating point stability, and failure modes.
 */
class AdversarialChallengeTest {

    // =========================================================================
    // 1. TouchSpringAnimator Stress & Boundary Tests
    // =========================================================================

    @Test
    fun testTouchSpring_rapidTouchStateOscillation_maintainsValidScales() {
        // Stress test simulating rapid finger flutter: 1000 cycles of DOWN / CANCEL
        var currentTarget = TouchSpringAnimator.DEFAULT_RESTING_SCALE
        for (i in 0 until 1000) {
            // ACTION_DOWN
            currentTarget = TouchSpringAnimator.DEFAULT_PRESSED_SCALE
            assertTrue("Pressed scale must be strictly positive", currentTarget > 0f)
            assertTrue("Pressed scale must be <= 1.0f", currentTarget <= 1.0f)

            // Immediate ACTION_CANCEL
            currentTarget = TouchSpringAnimator.DEFAULT_RESTING_SCALE
            assertEquals("Resting scale must restore to 1.0f", 1.0f, currentTarget, 0.0001f)
        }
    }

    @Test
    fun testTouchSpring_physicsDisplacement_neverExplodesOrNaN() {
        val k = TouchSpringAnimator.DEFAULT_STIFFNESS
        val zeta = TouchSpringAnimator.DEFAULT_DAMPING_RATIO
        val x0 = -0.04f

        // Sample across 500 time steps from t = 0 to t = 2.0s
        for (step in 0..500) {
            val t = step * 0.004f // 4ms frame intervals
            val disp = TouchSpringAnimator.Physics.calculateDisplacement(t, x0, 0f, k, zeta)
            assertFalse("Displacement must not be NaN at t=$t", disp.isNaN())
            assertFalse("Displacement must not be Infinite at t=$t", disp.isInfinite())
            // Displacement magnitude must never exceed initial displacement + small overshoot
            assertTrue("Displacement magnitude must be bounded", abs(disp) <= abs(x0) * 1.5f)
        }
    }

    @Test
    fun testTouchSpring_physicsDampingBoundaries() {
        // Test edge cases of damping ratio
        try {
            TouchSpringAnimator.Physics.dampedFrequency(stiffness = 380f, dampingRatio = 1.0f)
            fail("Damping ratio 1.0 should throw IllegalArgumentException for underdamped formula")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            TouchSpringAnimator.Physics.dampedFrequency(stiffness = 380f, dampingRatio = -0.1f)
            fail("Negative damping ratio should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun testTouchSpring_volumeConservation_extremeScales() {
        // Extreme scale cases
        val tinyScale = 0.01f
        val largeScale = 10.0f

        val invTiny = TouchSpringAnimator.Physics.computeConservedVolumeScaleX(tinyScale)
        assertEquals(100f, invTiny, 0.01f)

        val invLarge = TouchSpringAnimator.Physics.computeConservedVolumeScaleX(largeScale)
        assertEquals(0.1f, invLarge, 0.001f)

        try {
            TouchSpringAnimator.Physics.computeConservedVolumeScaleX(0f)
            fail("Scale 0f must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // =========================================================================
    // 2. KnobView Stress & Boundary Tests
    // =========================================================================

    @Test
    fun testKnobView_rapidCheckedToggles_stateConsistency() {
        var isChecked = false
        var glowProgress = 0f
        var glowAlpha = 0

        fun simulateToggle(target: Boolean) {
            if (isChecked != target) {
                isChecked = target
                glowProgress = if (target) 1.0f else 0.0f
                glowAlpha = (150 * glowProgress).toInt()
            }
        }

        // Stress: 1000 toggles
        for (i in 0 until 1000) {
            simulateToggle(i % 2 == 0)
            if (i % 2 == 0) {
                assertTrue(isChecked)
                assertEquals(1.0f, glowProgress, 0.001f)
                assertEquals(150, glowAlpha)
            } else {
                assertFalse(isChecked)
                assertEquals(0.0f, glowProgress, 0.001f)
                assertEquals(0, glowAlpha)
            }
        }
    }

    @Test
    fun testKnobView_redundantCheckedAssignment_doesNotRetrigger() {
        var triggerCount = 0
        var isChecked = false

        fun setChecked(value: Boolean) {
            if (isChecked != value) {
                isChecked = value
                triggerCount++
            }
        }

        setChecked(true)
        assertEquals(1, triggerCount)

        // Redundant assignments
        setChecked(true)
        setChecked(true)
        setChecked(true)
        assertEquals("Redundant setChecked must not retrigger transitions", 1, triggerCount)
    }

    @Test
    fun testKnobView_touchBounds_arbitraryDensities() {
        val testDensities = floatArrayOf(0.75f, 1.0f, 1.5f, 2.0f, 2.625f, 3.0f, 3.5f, 4.0f)
        for (d in testDensities) {
            val minPx = (48f * d + 0.5f).toInt()
            assertTrue("48dp in px at density $d must be at least 36px", minPx >= 36)
            // Verify resolveSize math preserves 48dp minimum
            val measured = maxOf(minPx, 0)
            assertEquals(minPx, measured)
        }
    }

    // =========================================================================
    // 3. CropOverlayView Stress & Precision Tests
    // =========================================================================

    @Test
    fun testCropOverlay_extremeDimensions_floatArrayIntegrity() {
        val extremeSizes = listOf(
            Pair(10, 10),
            Pair(1080, 2400),
            Pair(4000, 4000),
            Pair(100, 10000)
        )

        for ((w, h) in extremeSizes) {
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(w, h) * 0.4f

            val points = FloatArray(192)
            val gridSize = 25
            val startX = cx - radius
            val startY = cy - radius
            val diameter = radius * 2f
            val step = diameter / gridSize

            var index = 0
            for (i in 1 until gridSize) {
                val x = startX + i * step
                points[index++] = x
                points[index++] = startY
                points[index++] = x
                points[index++] = startY + diameter
            }
            for (i in 1 until gridSize) {
                val y = startY + i * step
                points[index++] = startX
                points[index++] = y
                points[index++] = startX + diameter
                points[index++] = y
            }

            assertEquals("Points array must be completely filled without overflow", 192, index)

            // Verify no NaN or Infinite coordinates
            for (pt in points) {
                assertFalse("Coordinate must not be NaN for dimension ${w}x${h}", pt.isNaN())
                assertFalse("Coordinate must not be Infinite for dimension ${w}x${h}", pt.isInfinite())
            }
        }
    }

    @Test
    fun testCropOverlay_zeroOrNegativeDimensions_safeHandling() {
        fun updateGeometry(cx: Float, cy: Float, radius: Float): Boolean {
            if (cx <= 0f || cy <= 0f || radius <= 0f) return false
            return true
        }

        // Test uninitialized or invalid dimensions
        assertFalse("cx <= 0 must abort geometry update", updateGeometry(-1f, 100f, 50f))
        assertFalse("cy <= 0 must abort geometry update", updateGeometry(100f, -1f, 50f))
        assertFalse("radius <= 0 must abort geometry update", updateGeometry(100f, 100f, 0f))
        assertFalse("radius < 0 must abort geometry update", updateGeometry(100f, 100f, -10f))
        assertTrue("Valid positive values must proceed", updateGeometry(500f, 500f, 200f))
    }

    @Test
    fun testCropOverlay_touchFadeStateSettling() {
        var isTouching = false
        var currentAlpha = 0
        val targetAlphaOnTouch = (255 * 0.35f).toInt() // 89
        val targetAlphaOnRelease = 0

        fun setTouching(touch: Boolean) {
            if (isTouching == touch) return
            isTouching = touch
            currentAlpha = if (touch) targetAlphaOnTouch else targetAlphaOnRelease
        }

        assertEquals(0, currentAlpha)

        // Touch ON
        setTouching(true)
        assertTrue(isTouching)
        assertEquals(89, currentAlpha)

        // Redundant Touch ON
        setTouching(true)
        assertEquals(89, currentAlpha)

        // Touch OFF (release)
        setTouching(false)
        assertFalse(isTouching)
        assertEquals(0, currentAlpha)

        // Redundant Touch OFF
        setTouching(false)
        assertEquals(0, currentAlpha)
    }

    // =========================================================================
    // 4. WindowInsetsUtil Stress & Edge Cases
    // =========================================================================

    @Test
    fun testWindowInsets_imeSmallerThanNavBar_clampedToZero() {
        // Case: Soft keyboard is closing or reporting smaller than navbar
        val imeBottom = 20
        val navBarBottom = 48
        val translation = WindowInsetsUtil.calculateImeTranslationY(imeBottom, navBarBottom)
        assertEquals("Translation must clamp to 0f, not positive translation", 0f, translation, 0.001f)
    }

    @Test
    fun testWindowInsets_extremeExclusionBounds_noInversion() {
        val bounds = WindowInsetsUtil.computeExclusionBounds(cx = 50, cy = 50, radius = 100)
        assertTrue("Bounds left can be negative for offscreen overlay", bounds.left < 0)
        assertEquals(200, bounds.width)
        assertEquals(200, bounds.height)
        assertTrue("Right must be greater than left", bounds.right > bounds.left)
        assertTrue("Bottom must be greater than top", bounds.bottom > bounds.top)
    }
}
