package com.example.odysseyglyph.e2e.fixtures

import org.junit.Assert.assertTrue

/**
 * Ergonomics & Accessibility Test Helper.
 * Validates WCAG 2.1 AA 48dp minimum touch bounds, one-handed thumb-zone reachability,
 * and touch delegate hit-box expansions.
 */
object ErgonomicsTestHelper {

    const val MIN_TOUCH_TARGET_DP = 48.0f
    const val THUMB_ZONE_THRESHOLD_Y = 0.65f // Lower 35% of the viewport

    /**
     * Converts dp units to physical pixels for a given display density.
     */
    fun dpToPx(dp: Float, density: Float = 3.0f): Int {
        return (dp * density + 0.5f).toInt()
    }

    /**
     * Asserts that a touch target meets or exceeds WCAG 2.1 AA 48dp x 48dp bounds.
     */
    fun assertWCAG48dp(widthDp: Float, heightDp: Float) {
        assertTrue(
            "Width $widthDp dp violates WCAG 2.1 AA minimum 48dp requirement",
            widthDp >= MIN_TOUCH_TARGET_DP
        )
        assertTrue(
            "Height $heightDp dp violates WCAG 2.1 AA minimum 48dp requirement",
            heightDp >= MIN_TOUCH_TARGET_DP
        )
    }

    /**
     * Asserts that a measured pixel dimension meets or exceeds 48dp at the given density.
     */
    fun assertWCAG48dpPx(widthPx: Int, heightPx: Int, density: Float = 3.0f) {
        val minPx = dpToPx(MIN_TOUCH_TARGET_DP, density)
        assertTrue(
            "Width $widthPx px is smaller than minimum 48dp ($minPx px at density $density)",
            widthPx >= minPx
        )
        assertTrue(
            "Height $heightPx px is smaller than minimum 48dp ($minPx px at density $density)",
            heightPx >= minPx
        )
    }

    /**
     * Asserts that a primary interactive control is placed within the natural one-handed
     * thumb reach zone (vertical viewport fraction y >= 0.65).
     */
    fun assertThumbZoneReachability(yFraction: Float) {
        assertTrue(
            "Touch target at yFraction=$yFraction is outside ergonomic thumb zone ($THUMB_ZONE_THRESHOLD_Y..1.0)",
            yFraction >= THUMB_ZONE_THRESHOLD_Y
        )
    }

    /**
     * Validates that touch delegate bounds expand an undersized view to meet minimum hitbox size.
     */
    fun assertTouchDelegateExpansion(
        viewWidth: Int,
        viewHeight: Int,
        delegateWidth: Int,
        delegateHeight: Int,
        minTargetPx: Int
    ) {
        assertTrue(
            "Touch delegate width $delegateWidth does not meet target $minTargetPx",
            delegateWidth >= minTargetPx
        )
        assertTrue(
            "Touch delegate height $delegateHeight does not meet target $minTargetPx",
            delegateHeight >= minTargetPx
        )
        assertTrue(
            "Touch delegate width must be at least view width",
            delegateWidth >= viewWidth
        )
        assertTrue(
            "Touch delegate height must be at least view height",
            delegateHeight >= viewHeight
        )
    }
}
