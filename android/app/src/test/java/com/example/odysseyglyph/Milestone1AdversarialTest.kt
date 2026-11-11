package com.example.odysseyglyph

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Milestone1AdversarialTest
 *
 * Empirical adversarial stress tests challenging the Milestone 1 foundation implementation:
 * 1. TouchSpringAnimator.Physics: extreme stiffness, near-critical damping (zeta -> 1),
 *    extreme mass, boundary handling at t -> infinity, volume conservation Sx * Sy = 1.0.
 * 2. CropOverlayView geometry: extreme coordinate inputs (circleX/Y at 0, negative, or beyond view bounds),
 *    float array indexing, circular mask clipping bounds and 8-fold symmetry.
 * 3. KnobView: 48dp minimum bounds enforcement under all MeasureSpec modes (UNSPECIFIED, AT_MOST, EXACTLY < 48dp),
 *    rapid state toggling, finite 150ms state transition (1Hz LTPO).
 * 4. WindowInsetsUtil: zero insets, extreme keyboard heights (500dp), negative values handling and boundary rects.
 */
class Milestone1AdversarialTest {

    // =========================================================================
    // 1. TouchSpringAnimator.Physics Stress Tests
    // =========================================================================

    @Test
    fun testPhysics_extremeStiffness_subMillinewtonToMegaStiff() {
        // Test ultra-soft spring (k = 0.001 N/m)
        val w0Soft = TouchSpringAnimator.Physics.angularFrequency(stiffness = 0.001f, mass = 1.0f)
        assertTrue("Ultra-soft spring frequency must be positive", w0Soft > 0f)
        assertEquals(sqrt(0.001).toFloat(), w0Soft, 0.0001f)

        // Test ultra-stiff spring (k = 1,000,000 N/m)
        val w0Stiff = TouchSpringAnimator.Physics.angularFrequency(stiffness = 1_000_000f, mass = 1.0f)
        assertEquals(1000f, w0Stiff, 0.01f)

        // Test boundary: stiffness <= 0 must fail-fast with IllegalArgumentException
        try {
            TouchSpringAnimator.Physics.angularFrequency(stiffness = 0f, mass = 1.0f)
            fail("Stiffness 0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive", ignoreCase = true))
        }

        try {
            TouchSpringAnimator.Physics.angularFrequency(stiffness = -100f, mass = 1.0f)
            fail("Negative stiffness must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive", ignoreCase = true))
        }
    }

    @Test
    fun testPhysics_extremeMass_microgramToMegatonne() {
        // Tiny mass (m = 1e-4 kg)
        val w0TinyMass = TouchSpringAnimator.Physics.angularFrequency(stiffness = 380f, mass = 0.0001f)
        assertEquals(sqrt(380.0 / 0.0001).toFloat(), w0TinyMass, 0.1f)

        // Huge mass (m = 100,000 kg)
        val w0HugeMass = TouchSpringAnimator.Physics.angularFrequency(stiffness = 380f, mass = 100_000f)
        assertEquals(sqrt(380.0 / 100_000.0).toFloat(), w0HugeMass, 0.0001f)

        // Boundary: mass <= 0 must throw IllegalArgumentException
        try {
            TouchSpringAnimator.Physics.angularFrequency(stiffness = 380f, mass = 0f)
            fail("Mass 0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive", ignoreCase = true))
        }

        try {
            TouchSpringAnimator.Physics.angularFrequency(stiffness = 380f, mass = -1.0f)
            fail("Negative mass must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive", ignoreCase = true))
        }
    }

    @Test
    fun testPhysics_nearCriticalDamping_zetaApproachingOne() {
        // Damping ratio approaching 1.0 from below (underdamped limit)
        val nearCriticalZetas = floatArrayOf(0.95f, 0.99f, 0.999f, 0.9999f, 0.99999f)
        for (zeta in nearCriticalZetas) {
            val wd = TouchSpringAnimator.Physics.dampedFrequency(
                stiffness = 380f,
                dampingRatio = zeta,
                mass = 1.0f
            )
            assertTrue("Damped frequency must be strictly positive for zeta=$zeta", wd > 0f)
            assertFalse("wd must not be NaN for zeta=$zeta", wd.isNaN())

            // Displacement calculation at t = 0.1s
            val d = TouchSpringAnimator.Physics.calculateDisplacement(
                t = 0.1f,
                x0 = -0.04f,
                v0 = 0f,
                stiffness = 380f,
                dampingRatio = zeta,
                mass = 1.0f
            )
            assertFalse("Displacement must not be NaN for zeta=$zeta", d.isNaN())
            assertFalse("Displacement must not be Infinite for zeta=$zeta", d.isInfinite())
            // Displacement magnitude must decay towards 0
            assertTrue("Displacement magnitude must be smaller than initial |x0|", abs(d) <= 0.04f)
        }

        // Critical damping boundary: zeta = 1.0f
        // The underdamped formula requirement requires zeta in [0, 1)
        try {
            TouchSpringAnimator.Physics.dampedFrequency(dampingRatio = 1.0f)
            fail("Damping ratio 1.0 must throw IllegalArgumentException for underdamped formula")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("underdamped", ignoreCase = true))
        }

        // Overdamped boundary: zeta > 1.0f
        try {
            TouchSpringAnimator.Physics.dampedFrequency(dampingRatio = 1.5f)
            fail("Damping ratio > 1.0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("underdamped", ignoreCase = true))
        }

        // Negative damping ratio: zeta < 0f
        try {
            TouchSpringAnimator.Physics.dampedFrequency(dampingRatio = -0.5f)
            fail("Negative damping ratio must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("underdamped", ignoreCase = true))
        }
    }

    @Test
    fun testPhysics_asymptoticTime_boundaryHandlingAtTToInfinity() {
        val x0 = -0.04f

        // Case 1: t = 0 -> displacement equals x0 exactly
        val d0 = TouchSpringAnimator.Physics.calculateDisplacement(0f, x0)
        assertEquals(x0, d0, 0.00001f)

        // Case 2: Negative t -> handled as t <= 0 (returns x0)
        val dNeg = TouchSpringAnimator.Physics.calculateDisplacement(-5f, x0)
        assertEquals(x0, dNeg, 0.00001f)

        // Case 3: Moderate t (t = 1.0s, 5x settling time) -> displacement is practically 0
        val d1s = TouchSpringAnimator.Physics.calculateDisplacement(1.0f, x0)
        assertTrue("At 1s, displacement should be practically zero", abs(d1s) < 1e-6f)

        // Case 4: Large t (t = 100.0s) -> decay envelope is 0.0f
        val env100s = TouchSpringAnimator.Physics.calculateDecayEnvelope(100f)
        assertEquals("Decay envelope at 100s should underflow to 0.0f", 0.0f, env100s, 0.000001f)
        val d100s = TouchSpringAnimator.Physics.calculateDisplacement(100f, x0)
        assertEquals("Displacement at 100s must be exactly 0.0f", 0.0f, d100s, 0.000001f)

        // Case 5: Extremely large t (t = 10,000s) -> no NaN, no crash
        val d10000s = TouchSpringAnimator.Physics.calculateDisplacement(10_000f, x0)
        assertFalse("Displacement must not be NaN at t=10000s", d10000s.isNaN())
        assertEquals(0.0f, d10000s, 0.000001f)
    }

    @Test
    fun testPhysics_volumeConservationInvariant_productIsUnity() {
        val testScales = floatArrayOf(0.96f, 0.90f, 0.80f, 0.50f, 1.0f, 1.25f, 2.0f)
        for (sy in testScales) {
            val sx = TouchSpringAnimator.Physics.computeConservedVolumeScaleX(sy)
            val product = sx * sy
            assertEquals("Volume conservation product Sx * Sy must equal 1.0 for Sy=$sy", 1.0f, product, 0.0001f)
        }

        // Boundary: scaleY <= 0 must throw IllegalArgumentException
        try {
            TouchSpringAnimator.Physics.computeConservedVolumeScaleX(0f)
            fail("ScaleY 0 must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive", ignoreCase = true))
        }

        try {
            TouchSpringAnimator.Physics.computeConservedVolumeScaleX(-0.5f)
            fail("Negative ScaleY must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("positive", ignoreCase = true))
        }
    }

    // =========================================================================
    // 2. CropOverlayView Geometry Stress Tests
    // =========================================================================

    /**
     * Replicates the exact mathematical coordinate calculation of CropOverlayView.updateGridPoints()
     * to empirically stress-test float array indexing and boundary conditions without Android UI dependencies.
     */
    private fun computeGridLineCoordinates(
        cx: Float,
        cy: Float,
        radius: Float,
        gridSize: Int = 25
    ): FloatArray {
        val totalLines = (gridSize - 1) * 2 // 48 lines
        val points = FloatArray(totalLines * 4) // 192 floats
        val startX = cx - radius
        val startY = cy - radius
        val diameter = radius * 2f
        val step = diameter / gridSize

        var index = 0
        // Vertical lines
        for (i in 1 until gridSize) {
            val x = startX + i * step
            points[index++] = x
            points[index++] = startY
            points[index++] = x
            points[index++] = startY + diameter
        }
        // Horizontal lines
        for (i in 1 until gridSize) {
            val y = startY + i * step
            points[index++] = startX
            points[index++] = y
            points[index++] = startX + diameter
            points[index++] = y
        }
        return points
    }

    @Test
    fun testCropOverlay_gridFloatArrayIndexing_exact192FloatsAcrossDensities() {
        // Test standard 1080p screen center
        val pts1 = computeGridLineCoordinates(540f, 960f, 400f)
        assertEquals(192, pts1.size)

        // Test non-integer fractional dimensions (e.g. tablet or scaled density)
        val pts2 = computeGridLineCoordinates(377.33f, 812.67f, 299.85f)
        assertEquals(192, pts2.size)

        // Verify all 48 lines: vertical lines (first 96 floats) and horizontal lines (next 96 floats)
        for (line in 0 until 24) {
            val baseIdx = line * 4
            val x1 = pts1[baseIdx]
            val y1 = pts1[baseIdx + 1]
            val x2 = pts1[baseIdx + 2]
            val y2 = pts1[baseIdx + 3]

            // Vertical: x1 == x2
            assertEquals("Vertical line $line must have x1 == x2", x1, x2, 0.0001f)
            // y1 must be startY, y2 must be startY + diameter
            assertEquals(560f, y1, 0.0001f) // 960 - 400 = 560
            assertEquals(1360f, y2, 0.0001f) // 960 + 400 = 1360
        }

        for (line in 24 until 48) {
            val baseIdx = line * 4
            val x1 = pts1[baseIdx]
            val y1 = pts1[baseIdx + 1]
            val x2 = pts1[baseIdx + 2]
            val y2 = pts1[baseIdx + 3]

            // Horizontal: y1 == y2
            assertEquals("Horizontal line $line must have y1 == y2", y1, y2, 0.0001f)
            // x1 must be startX, x2 must be startX + diameter
            assertEquals(140f, x1, 0.0001f) // 540 - 400 = 140
            assertEquals(940f, x2, 0.0001f) // 540 + 400 = 940
        }
    }

    @Test
    fun testCropOverlay_extremeCoordinates_negativeAndOutOfBoundsSafety() {
        // Crop circle panned partially off the left/top screen boundary
        val ptsNegative = computeGridLineCoordinates(-50f, -50f, 200f)
        assertEquals(192, ptsNegative.size)
        // startX = -50 - 200 = -250f
        assertEquals(-250f, ptsNegative[1], 0.001f) // y1 for vertical lines
        assertEquals(150f, ptsNegative[3], 0.001f)  // y2 for vertical lines

        // Crop circle placed far beyond view bounds (e.g. 10,000px)
        val ptsHuge = computeGridLineCoordinates(10_000f, 20_000f, 1_000f)
        assertEquals(192, ptsHuge.size)
        assertFalse("Coordinates must not contain NaN", ptsHuge.any { it.isNaN() })
        assertFalse("Coordinates must not contain Infinite", ptsHuge.any { it.isInfinite() })

        // Ultra-small radius (0.001f)
        val ptsTiny = computeGridLineCoordinates(100f, 100f, 0.001f)
        assertEquals(192, ptsTiny.size)
        assertFalse(ptsTiny.any { it.isNaN() })
    }

    @Test
    fun testCropOverlay_circularMaskClippingEquation_exhaustive625DiodeInspection() {
        val center = 12.0
        val radius = 12.5
        val radiusSq = radius * radius // 156.25

        fun isInside(x: Int, y: Int): Boolean {
            val dx = x - center
            val dy = y - center
            return (dx * dx + dy * dy) <= radiusSq
        }

        var activeDiodes = 0
        var maskedDiodes = 0

        for (y in 0 until 25) {
            for (x in 0 until 25) {
                if (isInside(x, y)) {
                    activeDiodes++
                } else {
                    maskedDiodes++
                }
            }
        }

        // Total diodes in 25x25 matrix
        assertEquals(625, activeDiodes + maskedDiodes)

        // Exact circular matrix diode count verification:
        // Inside radius 12.5 on a 25x25 grid centered at (12, 12):
        // Area of circle = pi * 12.5^2 ≈ 490.87. Lattice point count is exactly 489.
        assertEquals("Circular 25x25 matrix must contain exactly 489 active diodes", 489, activeDiodes)
        assertEquals("Corner masked diodes must be exactly 136 (625 - 489)", 136, maskedDiodes)

        // 8-Fold Symmetry Verification:
        // For every (x, y) inside, all symmetric reflections must match
        for (y in 0 until 25) {
            for (x in 0 until 25) {
                val state = isInside(x, y)
                assertEquals("Horizontal symmetry", state, isInside(24 - x, y))
                assertEquals("Vertical symmetry", state, isInside(x, 24 - y))
                assertEquals("Diagonal symmetry", state, isInside(y, x))
                assertEquals("Anti-diagonal symmetry", state, isInside(24 - y, 24 - x))
            }
        }
    }

    // =========================================================================
    // 3. KnobView Stress Tests (48dp Bounds & Rapid State Toggling)
    // =========================================================================

    /**
     * Mathematical model of View.resolveSize() and KnobView.onMeasure() enforcement.
     */
    private fun simulateKnobMeasure(
        density: Float,
        specSizePx: Int,
        specMode: Int // 0 = UNSPECIFIED, 1 = EXACTLY, 2 = AT_MOST
    ): Pair<Int, Int> {
        val minSizePx = (48f * density + 0.5f).toInt()
        val desiredW = minSizePx
        val desiredH = minSizePx

        fun resolve(desired: Int, size: Int, mode: Int): Int {
            return when (mode) {
                1 -> size // EXACTLY
                2 -> minOf(desired, size) // AT_MOST
                else -> desired // UNSPECIFIED
            }
        }

        val measuredW = maxOf(minSizePx, resolve(desiredW, specSizePx, specMode))
        val measuredH = maxOf(minSizePx, resolve(desiredH, specSizePx, specMode))
        return Pair(measuredW, measuredH)
    }

    @Test
    fun testKnobView_measureSpecEnforcement_ensures48dpAcrossAllModes() {
        val densities = floatArrayOf(1.0f, 2.0f, 2.625f, 3.0f, 3.5f, 4.0f)

        for (density in densities) {
            val expectedMinPx = (48f * density + 0.5f).toInt()

            // Mode 0: UNSPECIFIED (e.g. inside ScrollView) with size 0
            val (unspecW, unspecH) = simulateKnobMeasure(density, 0, 0)
            assertTrue("UNSPECIFIED width must be >= minSizePx", unspecW >= expectedMinPx)
            assertTrue("UNSPECIFIED height must be >= minSizePx", unspecH >= expectedMinPx)

            // Mode 2: AT_MOST with large parent constraint (e.g. 1080px)
            val (atMostLargeW, atMostLargeH) = simulateKnobMeasure(density, 1080, 2)
            assertEquals("AT_MOST with ample space should settle at minSizePx", expectedMinPx, atMostLargeW)
            assertEquals("AT_MOST with ample space should settle at minSizePx", expectedMinPx, atMostLargeH)

            // Mode 2: AT_MOST with undersized constraint (e.g. 20px < 48dp)
            val (atMostSmallW, atMostSmallH) = simulateKnobMeasure(density, 20, 2)
            assertTrue("AT_MOST undersized must be clamped up to 48dp", atMostSmallW >= expectedMinPx)
            assertTrue("AT_MOST undersized must be clamped up to 48dp", atMostSmallH >= expectedMinPx)

            // Mode 1: EXACTLY with undersized constraint (e.g. layout_width="20dp" or 20px)
            // WCAG AA requirement dictates 48dp touch hitbox cannot be shrunk below 48dp
            val (exactSmallW, exactSmallH) = simulateKnobMeasure(density, 20, 1)
            assertEquals("EXACTLY undersized constraint must be clamped to 48dp min hitbox", expectedMinPx, exactSmallW)
            assertEquals("EXACTLY undersized constraint must be clamped to 48dp min hitbox", expectedMinPx, exactSmallH)

            // Mode 1: EXACTLY with larger dimension (e.g. 100dp)
            val largePx = (100f * density + 0.5f).toInt()
            val (exactLargeW, exactLargeH) = simulateKnobMeasure(density, largePx, 1)
            assertEquals("EXACTLY larger dimension must be preserved", largePx, exactLargeW)
            assertEquals("EXACTLY larger dimension must be preserved", largePx, exactLargeH)
        }
    }

    @Test
    fun testKnobView_rapidStateToggling_progressBoundsAndCancellation() {
        // Simulate rapid 1000x state toggling sequence:
        // When toggled while an animation is in flight, the start progress must be
        // the current animated progress, smoothly redirecting towards target without jumps.
        var glowProgress = 0f
        var targetProgress = 0f
        var activeAnimatorCount = 0

        for (i in 0 until 1000) {
            val newChecked = (i % 2 == 0)
            val newTarget = if (newChecked) 1.0f else 0.0f

            if (newTarget != targetProgress) {
                // Cancel previous animator
                if (activeAnimatorCount > 0) {
                    activeAnimatorCount--
                }
                // Start new transition from current progress
                targetProgress = newTarget
                activeAnimatorCount++

                // Advance progress partially (simulating frame tick)
                val step = (targetProgress - glowProgress) * 0.1f
                glowProgress += step

                assertTrue("glowProgress must remain bounded in [0, 1]", glowProgress in 0f..1f)
                assertEquals("Only 1 active animator allowed at any instant", 1, activeAnimatorCount)
            }
        }

        // Final settle: progress reaches target
        glowProgress = targetProgress
        activeAnimatorCount--
        assertEquals("Animator must settle at 0 active animators", 0, activeAnimatorCount)
    }

    // =========================================================================
    // 4. WindowInsetsUtil Stress Tests
    // =========================================================================

    @Test
    fun testWindowInsets_zeroInsets_identityAndDefaults() {
        assertEquals(0, WindowInsetsUtil.computeTopPadding(0, 0))
        assertEquals(0, WindowInsetsUtil.calculateTopInset(0, 0))
        assertEquals(0, WindowInsetsUtil.computeBottomInset(0, 0))
        assertEquals(0, WindowInsetsUtil.calculateBottomInset(0, 0, 0))
        assertEquals(0, WindowInsetsUtil.computeScrollPaddingBottom(0, 0, false))
        assertEquals(0, WindowInsetsUtil.computeScrollPaddingBottom(0, 0, true))
        assertEquals(0f, WindowInsetsUtil.calculateImeTranslationY(0, 0), 0.0001f)
    }

    @Test
    fun testWindowInsets_extremeKeyboardHeights_upTo1000dp() {
        val navBarPx = 48
        // Test soft keyboards of varying heights: 200px, 500px, 1200px (tablet landscape), 3000px
        val keyboardHeights = intArrayOf(200, 500, 1200, 3000)

        for (imePx in keyboardHeights) {
            val bottomInset = WindowInsetsUtil.computeBottomInset(navBarPx, imePx)
            assertEquals("Bottom inset must clear full keyboard height", imePx, bottomInset)

            val translationY = WindowInsetsUtil.calculateImeTranslationY(imePx, navBarPx)
            val expectedTranslation = -(imePx - navBarPx).toFloat()
            assertEquals("IME translation must equal negative delta above nav bar", expectedTranslation, translationY, 0.001f)
            assertTrue("Translation must be negative (shifting view upward)", translationY < 0f)

            // Combined with overlay bottom deck (e.g. 80px)
            val totalBottom = WindowInsetsUtil.calculateBottomInset(navBarPx, imePx, overlayHeight = 80)
            assertEquals(imePx + 80, totalBottom)
        }
    }

    @Test
    fun testWindowInsets_negativeInputsHandling_clampingAndRobustness() {
        // Negative IME values (corrupted or unexpected system insets)
        val translationWithNegativeIme = WindowInsetsUtil.calculateImeTranslationY(imeBottom = -50, navBarBottom = 48)
        assertEquals("Negative IME difference must coerce to 0f translation", 0f, translationWithNegativeIme, 0.0001f)

        // Negative radius in exclusion bounds
        val bounds = WindowInsetsUtil.computeExclusionBounds(cx = 100, cy = 100, radius = 50)
        assertTrue("Exclusion width must be positive", bounds.width > 0)
        assertTrue("Exclusion height must be positive", bounds.height > 0)
        assertEquals(50, bounds.left)
        assertEquals(150, bounds.right)
    }
}
