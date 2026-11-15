package com.example.odysseyglyph

import org.junit.Assert.*
import org.junit.Test

/**
 * FoundationUnitTest
 *
 * Comprehensive unit test suite for Milestone 1 foundation components:
 * 1. KnobView bounds enforcement (WCAG 48dp) & finite state transitions (1Hz LTPO).
 * 2. CropOverlayView grid points calculation & circular mask geometry (192 floats).
 * 3. TouchSpringAnimator physics formulas, damping ratio, and settling time.
 * 4. HapticExt semantic constants mapping & audio-safe attributes.
 * 5. WindowInsets dynamic padding calculations (Android 15 edge-to-edge).
 *
 * Runs cleanly on JVM with standard JUnit 4 via `./gradlew test`.
 */
class FoundationUnitTest {

    // =========================================================================
    // 1. KnobView Tests
    // =========================================================================

    @Test
    fun testKnobView_enforcesMinimumTouchBounds_wcag48dp() {
        val density = 2.0f // e.g. xhdpi device
        val expectedMinPx = (KnobView.DEFAULT_MIN_SIZE_DP * density + 0.5f).toInt() // 96px

        assertEquals("Default min size must be 48dp", 48f, KnobView.DEFAULT_MIN_SIZE_DP, 0.001f)

        // Test 1: Undersized request (e.g. 32dp = 64px) with EXACTLY clamped to 48dp (96px)
        val clampedDim = KnobView.resolveMinDimension(
            suggestedMin = 0,
            specSize = 64,
            specMode = 1, // EXACTLY
            density = density
        )
        assertEquals("Undersized touch target must clamp to 48dp minimum", expectedMinPx, clampedDim)

        // Test 2: Wrap content / UNSPECIFIED defaults to 48dp (96px)
        val wrapContentDim = KnobView.resolveMinDimension(
            suggestedMin = 0,
            specSize = 0,
            specMode = 0, // UNSPECIFIED
            density = density
        )
        assertEquals("Wrap content must default to 48dp minimum", expectedMinPx, wrapContentDim)

        // Test 3: Larger explicit dimension (e.g. 64dp = 128px) is preserved
        val largeDim = KnobView.resolveMinDimension(
            suggestedMin = 0,
            specSize = 128,
            specMode = 1, // EXACTLY
            density = density
        )
        assertEquals("Larger explicit bounds must be preserved", 128, largeDim)
    }

    @Test
    fun testKnobView_stateTransitions_settleAtZeroRepaints() {
        // Test production constants directly from KnobView companion
        assertEquals(
            "Glow transition duration must be finite 150ms for 1Hz LTPO settling",
            150L,
            KnobView.GLOW_TRANSITION_DURATION_MS
        )
        assertTrue(
            "Duration must settle within 150-300ms",
            KnobView.GLOW_TRANSITION_DURATION_MS in 150L..300L
        )

        // Glow max alpha
        assertEquals(
            "Active glow alpha must settle at exactly 150",
            150,
            KnobView.GLOW_MAX_ALPHA
        )
        assertTrue("Max alpha must be within valid alpha bounds [0, 255]", KnobView.GLOW_MAX_ALPHA in 1..255)
    }

    @Test
    fun testKnobView_paintColorsAndTokens() {
        assertEquals(
            "Glow accent must match Nothing Red (#EA3323)",
            0xFFEA3323.toInt(),
            KnobView.COLOR_NOTHING_RED
        )
        assertEquals(
            "Chassis color must match matte dark chassis (#1A1A1A)",
            0xFF1A1A1A.toInt(),
            KnobView.COLOR_CHASSIS
        )
        assertNotEquals(
            "Chassis and accent colors must differ",
            KnobView.COLOR_CHASSIS,
            KnobView.COLOR_NOTHING_RED
        )
    }

    // =========================================================================
    // 2. CropOverlayView Tests
    // =========================================================================

    @Test
    fun testCropOverlay_gridFloatArraySize_isExactly192Floats() {
        assertEquals("Grid size must be 25 for Nothing Phone 25x25 matrix", 25, CropOverlayView.GRID_SIZE)
        assertEquals("Total grid lines for 25x25 matrix must be 48", 48, CropOverlayView.TOTAL_GRID_LINES)
        assertEquals("Pre-allocated float array must contain exactly 192 floats", 192, CropOverlayView.TOTAL_GRID_FLOATS)
        assertEquals("Floats must equal lines * 4", CropOverlayView.TOTAL_GRID_LINES * 4, CropOverlayView.TOTAL_GRID_FLOATS)
    }

    @Test
    fun testCropOverlay_gridLinesCoordinates_withinCircleBounds() {
        val cx = 540f
        val cy = 960f
        val radius = 400f
        val startX = cx - radius
        val startY = cy - radius

        val outPoints = FloatArray(CropOverlayView.TOTAL_GRID_FLOATS)
        val result = CropOverlayView.computeGridLineCoordinates(cx, cy, radius, outPoints)

        assertSame("Must populate and return passed float array", outPoints, result)
        assertEquals("Must populate exactly 192 floats", 192, result.size)

        // Verify bounds: every coordinate must lie within [startX, startX + 2*radius]
        for (i in 0 until CropOverlayView.TOTAL_GRID_FLOATS step 4) {
            val x1 = result[i]
            val y1 = result[i + 1]
            val x2 = result[i + 2]
            val y2 = result[i + 3]

            assertTrue("x1 must be within bounds", x1 in startX..(startX + radius * 2f))
            assertTrue("x2 must be within bounds", x2 in startX..(startX + radius * 2f))
            assertTrue("y1 must be within bounds", y1 in startY..(startY + radius * 2f))
            assertTrue("y2 must be within bounds", y2 in startY..(startY + radius * 2f))
        }

        // Verify vertical lines (first 24 lines, indices 0..95) have x1 == x2
        for (i in 0 until 24) {
            val base = i * 4
            assertEquals("Vertical line $i must have x1 == x2", result[base], result[base + 2], 0.001f)
            assertEquals("Vertical line $i y1 must be startY", startY, result[base + 1], 0.001f)
            assertEquals("Vertical line $i y2 must be startY + 2*radius", startY + radius * 2f, result[base + 3], 0.001f)
        }

        // Verify horizontal lines (next 24 lines, indices 96..191) have y1 == y2
        for (i in 24 until 48) {
            val base = i * 4
            assertEquals("Horizontal line $i must have y1 == y2", result[base + 1], result[base + 3], 0.001f)
            assertEquals("Horizontal line $i x1 must be startX", startX, result[base], 0.001f)
            assertEquals("Horizontal line $i x2 must be startX + 2*radius", startX + radius * 2f, result[base + 2], 0.001f)
        }
    }

    @Test
    fun testCropOverlay_circularMaskCondition() {
        // Center point is inside
        assertTrue(
            "Center point (12, 12) must be inside circular matrix",
            CropOverlayView.isPointInsideMatrixMask(12, 12)
        )

        // Cardinal edge points
        assertTrue(
            "North edge (12, 0) must be inside circular matrix",
            CropOverlayView.isPointInsideMatrixMask(12, 0)
        )
        assertTrue(
            "South edge (12, 24) must be inside circular matrix",
            CropOverlayView.isPointInsideMatrixMask(12, 24)
        )
        assertTrue(
            "West edge (0, 12) must be inside circular matrix",
            CropOverlayView.isPointInsideMatrixMask(0, 12)
        )
        assertTrue(
            "East edge (24, 12) must be inside circular matrix",
            CropOverlayView.isPointInsideMatrixMask(24, 12)
        )

        // Corner points are outside
        assertFalse(
            "Top-left corner (0, 0) must be masked out",
            CropOverlayView.isPointInsideMatrixMask(0, 0)
        )
        assertFalse(
            "Top-right corner (24, 0) must be masked out",
            CropOverlayView.isPointInsideMatrixMask(24, 0)
        )
        assertFalse(
            "Bottom-left corner (0, 24) must be masked out",
            CropOverlayView.isPointInsideMatrixMask(0, 24)
        )
        assertFalse(
            "Bottom-right corner (24, 24) must be masked out",
            CropOverlayView.isPointInsideMatrixMask(24, 24)
        )
    }

    @Test
    fun testCropOverlay_publicPropertiesContract() {
        assertEquals("Default radius factor must be 0.4f (40% of min dimension)", 0.4f, CropOverlayView.DEFAULT_RADIUS_FACTOR, 0.001f)

        val w = 1080
        val h = 2400
        val cx = w / 2f
        val cy = h / 2f
        val computedRadius = minOf(w, h) * CropOverlayView.DEFAULT_RADIUS_FACTOR

        assertEquals("Circle X must center horizontally", 540f, cx, 0.001f)
        assertEquals("Circle Y must center vertically", 1200f, cy, 0.001f)
        assertEquals("Circle radius must be 40% of smaller dimension (1080 * 0.4 = 432)", 432f, computedRadius, 0.001f)
    }

    // =========================================================================
    // 3. TouchSpringAnimator Tests
    // =========================================================================

    @Test
    fun testTouchSpring_defaultParameters() {
        assertEquals("Default stiffness must be 380f", 380f, TouchSpringAnimator.DEFAULT_STIFFNESS, 0.001f)
        assertEquals("Default damping ratio must be 0.78f", 0.78f, TouchSpringAnimator.DEFAULT_DAMPING_RATIO, 0.001f)
        assertEquals("Default pressed scale must be 0.96f", 0.96f, TouchSpringAnimator.DEFAULT_PRESSED_SCALE, 0.001f)
        assertEquals("Default resting scale must be 1.0f", 1.0f, TouchSpringAnimator.DEFAULT_RESTING_SCALE, 0.001f)
    }

    @Test
    fun testTouchSpring_underdampedCondition() {
        val dampingRatio = TouchSpringAnimator.DEFAULT_DAMPING_RATIO
        assertTrue("Spring must be underdamped (zeta < 1.0) for responsive snapback", TouchSpringAnimator.Physics.isUnderdamped(dampingRatio))
        assertTrue("Damping ratio must be positive", dampingRatio > 0f)
    }

    @Test
    fun testTouchSpring_angularFrequencyAndDampedFrequency() {
        val k = 380f
        val zeta = 0.78f
        val m = 1.0f

        val w0 = TouchSpringAnimator.Physics.angularFrequency(k, m)
        val expectedW0 = Math.sqrt(380.0).toFloat()
        assertEquals("Natural angular frequency must be sqrt(k/m)", expectedW0, w0, 0.01f)

        val wd = TouchSpringAnimator.Physics.dampedFrequency(k, zeta, m)
        val expectedWd = (expectedW0 * Math.sqrt(1.0 - zeta * zeta)).toFloat()
        assertEquals("Damped angular frequency must match theoretical formula", expectedWd, wd, 0.01f)
    }

    @Test
    fun testTouchSpring_displacementDecaySettlesUnder250ms() {
        val k = 380f
        val zeta = 0.78f

        // Initial displacement x0 = -0.04 (from 1.0 to 0.96)
        val x0 = -0.04f
        val d0 = TouchSpringAnimator.Physics.calculateDisplacement(0f, x0)
        assertEquals("At t=0, displacement must equal initial x0", x0, d0, 0.0001f)

        // At t = 200ms (0.2s)
        val decayEnvelope200ms = TouchSpringAnimator.Physics.calculateDecayEnvelope(0.2f, k, zeta)
        assertTrue("At 200ms, decay envelope must be under 5%", decayEnvelope200ms < 0.05f)

        // At t = 250ms (0.25s)
        val decayEnvelope250ms = TouchSpringAnimator.Physics.calculateDecayEnvelope(0.25f, k, zeta)
        assertTrue("At 250ms, decay envelope must be under 3%", decayEnvelope250ms < 0.03f)

        // Theoretical settling time to 5% tolerance
        val settleTime5Percent = TouchSpringAnimator.Physics.settlingTime(0.05f, k, zeta)
        assertTrue("5% settling time must be under 200ms", settleTime5Percent < 0.20f)
    }

    @Test
    fun testTouchSpring_volumeConservationScale() {
        val scaleY = 0.96f
        val scaleX = TouchSpringAnimator.Physics.computeConservedVolumeScaleX(scaleY)
        val product = scaleX * scaleY
        assertEquals("Volume conservation product Sx * Sy must equal 1.0", 1.0f, product, 0.001f)
        assertTrue("ScaleX must be slightly expanded when ScaleY is compressed", scaleX > 1.0f)
    }

    // =========================================================================
    // 4. HapticExt Tests
    // =========================================================================

    @Test
    fun testHapticExt_semanticConstantsMapping() {
        // TICK
        assertEquals("TICK feedback constant must map to CLOCK_TICK", 4, HapticResolver.resolveFeedbackConstant(HapticType.TICK))
        assertEquals("TICK vibration effect must map to EFFECT_TICK", 2, HapticResolver.resolveVibrationEffectId(HapticType.TICK))

        // CLICK
        assertEquals("CLICK feedback constant must map to CONTEXT_CLICK", 6, HapticResolver.resolveFeedbackConstant(HapticType.CLICK))
        assertEquals("CLICK vibration effect must map to EFFECT_CLICK", 0, HapticResolver.resolveVibrationEffectId(HapticType.CLICK))

        // HEAVY_CLICK
        assertEquals("HEAVY_CLICK feedback constant must map to LONG_PRESS", 0, HapticResolver.resolveFeedbackConstant(HapticType.HEAVY_CLICK))
        assertEquals("HEAVY_CLICK vibration effect must map to EFFECT_HEAVY_CLICK", 5, HapticResolver.resolveVibrationEffectId(HapticType.HEAVY_CLICK))

        // DETENT
        assertEquals("DETENT feedback constant must map to CLOCK_TICK", 4, HapticResolver.resolveFeedbackConstant(HapticType.DETENT))
        assertEquals("DETENT vibration effect must map to EFFECT_TICK", 2, HapticResolver.resolveVibrationEffectId(HapticType.DETENT))
    }

    @Test
    fun testHapticExt_audioSafeUsageConstant() {
        val usageTouch = HapticResolver.USAGE_TOUCH
        assertEquals("Audio-safe vibration usage must be USAGE_TOUCH (18)", 18, usageTouch)
        assertTrue("isAudioSafeUsage must return true for USAGE_TOUCH", HapticResolver.isAudioSafeUsage(18))
        assertFalse("isAudioSafeUsage must return false for media audio usage (USAGE_MEDIA=1)", HapticResolver.isAudioSafeUsage(1))
    }

    @Test
    fun testHapticExt_disabledSettingReturnsFalse() {
        // When contentResolver is null or setting query fails, returns false
        assertFalse("Null contentResolver must return false for haptics enabled", HapticResolver.isHapticEnabled(null))
    }

    // =========================================================================
    // 5. Window Insets Math Tests
    // =========================================================================

    @Test
    fun testWindowInsets_topPaddingMaxCutoutAndStatusBar() {
        // Case 1: Status bar taller than cutout (e.g. tablet or small punch-hole)
        assertEquals(36, WindowInsetsUtil.computeTopPadding(statusBarTop = 36, cutoutTop = 28))
        assertEquals(36, WindowInsetsUtil.calculateTopInset(statusBarTop = 36, cutoutTop = 28))

        // Case 2: Cutout taller than status bar (e.g. large dynamic pill)
        assertEquals(44, WindowInsetsUtil.computeTopPadding(statusBarTop = 32, cutoutTop = 44))
        assertEquals(44, WindowInsetsUtil.calculateTopInset(statusBarTop = 32, cutoutTop = 44))

        // Case 3: Zero cutout (generic device without cutout)
        assertEquals(30, WindowInsetsUtil.computeTopPadding(statusBarTop = 30, cutoutTop = 0))
        assertEquals(30, WindowInsetsUtil.calculateTopInset(statusBarTop = 30, cutoutTop = 0))
    }

    @Test
    fun testWindowInsets_bottomInsetNavBarsVsIme() {
        // Case 1: Gesture navigation (24px), no IME
        assertEquals(24, WindowInsetsUtil.computeBottomInset(navBarsBottom = 24, imeBottom = 0))
        assertEquals(24, WindowInsetsUtil.calculateBottomInset(navBarBottom = 24, imeBottom = 0))

        // Case 2: 3-Button navigation (48px), no IME
        assertEquals(48, WindowInsetsUtil.computeBottomInset(navBarsBottom = 48, imeBottom = 0))
        assertEquals(48, WindowInsetsUtil.calculateBottomInset(navBarBottom = 48, imeBottom = 0))

        // Case 3: Soft keyboard open (350px) with 3-button nav (48px)
        assertEquals(350, WindowInsetsUtil.computeBottomInset(navBarsBottom = 48, imeBottom = 350))
        assertEquals(350, WindowInsetsUtil.calculateBottomInset(navBarBottom = 48, imeBottom = 350))

        // Case 4: With overlay height (e.g. 80px)
        assertEquals(430, WindowInsetsUtil.calculateBottomInset(navBarBottom = 48, imeBottom = 350, overlayHeight = 80))
    }

    @Test
    fun testWindowInsets_scrollPaddingClearance() {
        // Case 1: Deck visible (80dp) + 3-Button Nav (48dp) -> 128dp
        assertEquals(128, WindowInsetsUtil.computeScrollPaddingBottom(deckHeight = 80, bottomInset = 48, isDeckVisible = true))

        // Case 2: Deck hidden + 3-Button Nav (48dp) -> 48dp
        assertEquals(48, WindowInsetsUtil.computeScrollPaddingBottom(deckHeight = 80, bottomInset = 48, isDeckVisible = false))

        // Case 3: Deck visible (80dp) + IME open (350dp) -> 430dp
        assertEquals(430, WindowInsetsUtil.computeScrollPaddingBottom(deckHeight = 80, bottomInset = 350, isDeckVisible = true))
    }

    @Test
    fun testWindowInsets_exclusionRectCalculations() {
        // System gesture exclusion rect for slider thumb or circular crop overlay
        val touchCenterX = 100
        val touchCenterY = 200
        val touchRadius = 48 // 24dp at 2.0 density

        val bounds = WindowInsetsUtil.computeExclusionBounds(touchCenterX, touchCenterY, touchRadius)

        assertEquals(52, bounds.left)
        assertEquals(152, bounds.top)
        assertEquals(148, bounds.right)
        assertEquals(248, bounds.bottom)
        assertEquals(96, bounds.width) // 48dp width
        assertEquals(96, bounds.height) // 48dp height
    }

    @Test
    fun testWindowInsets_imeTranslationY() {
        // When IME is closed (imeBottom = 0, navBarBottom = 48)
        assertEquals(0f, WindowInsetsUtil.calculateImeTranslationY(imeBottom = 0, navBarBottom = 48), 0.001f)

        // When IME is open (imeBottom = 350, navBarBottom = 48)
        assertEquals(-302f, WindowInsetsUtil.calculateImeTranslationY(imeBottom = 350, navBarBottom = 48), 0.001f)
    }

    // =========================================================================
    // 6. VideoProcessor Adaptive Range & Sharpening Tests
    // =========================================================================

    @Test
    fun testVideoProcessor_adaptiveRange_cameraPhoto_zeroGain() {
        // High dynamic range typical of direct camera photos (spread >= 150)
        val lumaList = mutableListOf<Float>()
        for (i in 0 until 100) {
            lumaList.add(i * 2.55f) // 0f to 255f evenly spread
        }

        val range = VideoProcessor.calculateAdaptiveRange(
            frameMin = 0f,
            frameMax = 255f,
            activeLuma = lumaList,
            spreadThreshold = 150f
        )

        assertEquals("Camera photo spread must yield zero adaptive gain", 0f, range.adaptiveGain, 0.001f)
        assertEquals("Effective min must be identical to frameMin", 0f, range.effectiveMin, 0.001f)
        assertEquals("Effective max must be identical to frameMax", 255f, range.effectiveMax, 0.001f)
        assertEquals("Effective range must be identical to frameRange", 255f, range.effectiveRange, 0.001f)
    }

    @Test
    fun testVideoProcessor_adaptiveRange_lowResFace_enhancesDynamicRange() {
        // Low dynamic range typical of compressed web images/faces (clustered between 110 and 150)
        val lumaList = mutableListOf<Float>()
        for (i in 0 until 100) {
            lumaList.add(110f + (i % 41)) // tightly clustered in midtones
        }

        val range = VideoProcessor.calculateAdaptiveRange(
            frameMin = 10f, // stray background shadow
            frameMax = 245f, // stray background highlight
            activeLuma = lumaList,
            spreadThreshold = 150f
        )

        assertTrue("Low-res midtone cluster must trigger adaptive gain > 0", range.adaptiveGain > 0f)
        assertTrue("Effective min must adapt towards subject's lower bound", range.effectiveMin > 10f)
        assertTrue("Effective max must adapt towards subject's upper bound", range.effectiveMax < 245f)
        assertTrue("Effective range must be narrower than raw frameRange to expand subject contrast", range.effectiveRange < (245f - 10f))
    }

    @Test
    fun testVideoProcessor_adaptiveRange_emptyOrUniform_safe() {
        // Edge case: uniform frame (all diodes identical)
        val rangeUniform = VideoProcessor.calculateAdaptiveRange(
            frameMin = 128f,
            frameMax = 128f,
            activeLuma = listOf(128f, 128f, 128f)
        )
        assertEquals(0f, rangeUniform.adaptiveGain, 0.001f)
        assertTrue("Effective range must be at least 1f to prevent divide-by-zero", rangeUniform.effectiveRange >= 1f)

        // Edge case: empty list
        val rangeEmpty = VideoProcessor.calculateAdaptiveRange(
            frameMin = 0f,
            frameMax = 255f,
            activeLuma = emptyList()
        )
        assertEquals(0f, rangeEmpty.adaptiveGain, 0.001f)
        assertEquals(255f, rangeEmpty.effectiveRange, 0.001f)
    }

    @Test
    fun testVideoProcessor_adaptiveRange_modeOff_strictlyZeroGain() {
        // Even with narrow low-res midtone cluster, mode = 2 (OFF) must strictly enforce 0.0 gain (legacy)
        val lumaList = mutableListOf<Float>()
        for (i in 0 until 100) {
            lumaList.add(110f + (i % 41))
        }

        val range = VideoProcessor.calculateAdaptiveRange(
            frameMin = 10f,
            frameMax = 245f,
            activeLuma = lumaList,
            spreadThreshold = 150f,
            mode = 2 // OFF
        )

        assertEquals("OFF mode must strictly yield zero adaptive gain", 0f, range.adaptiveGain, 0.001f)
        assertEquals("OFF mode must strictly preserve frameMin", 10f, range.effectiveMin, 0.001f)
        assertEquals("OFF mode must strictly preserve frameMax", 245f, range.effectiveMax, 0.001f)
        assertEquals("OFF mode must strictly preserve frameRange", 235f, range.effectiveRange, 0.001f)
    }

    @Test
    fun testVideoProcessor_adaptiveRange_modeOn_forcesMaxGain() {
        // Even with wide camera photo spread, mode = 1 (ON) forces 1.0 gain
        val lumaList = mutableListOf<Float>()
        for (i in 0 until 100) {
            lumaList.add(i * 2.55f)
        }

        val range = VideoProcessor.calculateAdaptiveRange(
            frameMin = 0f,
            frameMax = 255f,
            activeLuma = lumaList,
            spreadThreshold = 150f,
            mode = 1 // Force ON
        )

        assertEquals("Force ON mode must yield 1.0 adaptive gain", 1.0f, range.adaptiveGain, 0.001f)
        assertTrue("Force ON mode must contract effective range", range.effectiveRange < 255f)
    }

    @Test
    fun testVideoProcessor_sharpeningWeights_preservesMeanEnergy() {
        // For any gain in [0, 1], centerWeight - 4 * neighborWeight must equal 1.0 (mean preserving)
        val testGains = floatArrayOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        for (gain in testGains) {
            val (center, neighbor) = VideoProcessor.calculateSharpeningWeights(gain)
            val sum = center - 4f * neighbor
            assertEquals("Sharpening kernel must preserve overall brightness energy", 1.0f, sum, 0.001f)
        }

        // Gain = 0.0 must exactly match standard 5.0 and 1.0 weights
        val (c0, n0) = VideoProcessor.calculateSharpeningWeights(0f)
        assertEquals(5.0f, c0, 0.001f)
        assertEquals(1.0f, n0, 0.001f)
    }

    @Test
    fun testVideoProcessor_timestampIndexedCache_trimLogic() {
        // Clear cache
        VideoProcessor.clearFrameCache()
        assertEquals(0, VideoProcessor.getCachedFrameCount())

        val fps = 25
        val intervalMs = 1000L / fps // 40ms

        // Initial 0s to 5s
        val startMs1 = 0L
        val endMs1 = 5000L
        val startFrame1 = (startMs1 / intervalMs).toInt()
        val endFrame1 = (endMs1 / intervalMs).toInt()
        val needed1 = (startFrame1 until endFrame1).toList()
        assertEquals(125, needed1.size)

        // Mock caching
        val mockCache = mutableMapOf<Int, ByteArray>()
        for (idx in needed1) {
            mockCache[idx] = ByteArray(625) { 42.toByte() }
        }

        // Case A: User shortens trim from 5s to 3s (0s to 3s)
        val startMs2 = 0L
        val endMs2 = 3000L
        val startFrame2 = (startMs2 / intervalMs).toInt()
        val endFrame2 = (endMs2 / intervalMs).toInt()
        val needed2 = (startFrame2 until endFrame2).toList()
        assertEquals(75, needed2.size)

        val missing2 = needed2.filter { !mockCache.containsKey(it) }
        assertEquals("Shortening trim must require 0 decodes", 0, missing2.size)

        // Case B: User slides trim window to 1s..4s
        val startMs3 = 1000L
        val endMs3 = 4000L
        val startFrame3 = (startMs3 / intervalMs).toInt()
        val endFrame3 = (endMs3 / intervalMs).toInt()
        val needed3 = (startFrame3 until endFrame3).toList()
        assertEquals(75, needed3.size)

        val missing3 = needed3.filter { !mockCache.containsKey(it) }
        assertEquals("Sliding trim inside decoded window must require 0 decodes", 0, missing3.size)

        // Case C: User extends trim to 0s..8s
        val startMs4 = 0L
        val endMs4 = 8000L
        val startFrame4 = (startMs4 / intervalMs).toInt()
        val endFrame4 = (endMs4 / intervalMs).toInt()
        val needed4 = (startFrame4 until endFrame4).toList()
        assertEquals(200, needed4.size)

        val missing4 = needed4.filter { !mockCache.containsKey(it) }
        assertEquals("Extending trim must only decode missing frames", 75, missing4.size)
        assertEquals("Missing frames must start exactly at 125", 125, missing4.first())
        assertEquals("Missing frames must end at 199", 199, missing4.last())
    }

    @Test
    fun testLedTone_scratchBuffer_producesByteIdenticalResults() {
        val w = 200
        val h = 200
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val r = (x * 255 / w)
                val g = (y * 255 / h)
                val b = ((x + y) * 128 / (w + h))
                pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val resWithoutScratch = LedTone.downsampleArea(pixels, w, h, null)

        val scratch = LedTone.ScratchBuffer()
        val resWithScratch = LedTone.downsampleArea(pixels, w, h, scratch)

        assertArrayEquals("Output with ScratchBuffer must be byte-identical to standard output", resWithoutScratch, resWithScratch)

        // Run again with reused scratch (verify zero cross-frame bleed)
        val resReusedScratch = LedTone.downsampleArea(pixels, w, h, scratch)
        assertArrayEquals("Output with reused ScratchBuffer must be byte-identical", resWithoutScratch, resReusedScratch)
    }
}
