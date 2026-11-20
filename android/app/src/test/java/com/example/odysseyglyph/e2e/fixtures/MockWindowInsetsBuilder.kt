package com.example.odysseyglyph.e2e.fixtures

import kotlin.math.max

/**
 * Mock Window Insets Builder and Pure Geometric Calculator.
 * Simulates Android 15 edge-to-edge window insets, status bar, navigation bar,
 * punch-hole camera cutouts, and 120Hz IME keyboard transitions for JVM execution.
 */
class MockWindowInsetsBuilder {

    var statusBarTop: Int = 36
    var navBarBottom: Int = 24 // Gesture nav (24dp) or 3-button nav (48dp)
    var cutoutTop: Int = 40
    var cutoutLeft: Int = 0
    var cutoutRight: Int = 0
    var imeBottom: Int = 0 // Soft keyboard height (0 to ~380dp)

    fun withStatusBar(top: Int) = apply { statusBarTop = top }
    fun withGestureNav() = apply { navBarBottom = 24 }
    fun withThreeButtonNav() = apply { navBarBottom = 48 }
    fun withCutout(top: Int, left: Int = 0, right: Int = 0) = apply {
        cutoutTop = top
        cutoutLeft = left
        cutoutRight = right
    }
    fun withImeKeyboard(height: Int) = apply { imeBottom = height }

    /**
     * Computes top padding combining status bar and punch-hole camera cutout.
     */
    fun computeTopInset(): Int = max(statusBarTop, cutoutTop)

    /**
     * Computes bottom inset combining navigation bar and soft keyboard.
     */
    fun computeBottomInset(): Int = max(navBarBottom, imeBottom)

    /**
     * Computes 120Hz translation offset for views anchored above keyboard.
     */
    fun computeImeTranslationY(): Float {
        return -(imeBottom - navBarBottom).coerceAtLeast(0).toFloat()
    }

    /**
     * Computes bottom padding for scroll views accounting for bottom deck and insets.
     */
    fun computeScrollPaddingBottom(deckHeight: Int, isDeckVisible: Boolean): Int {
        val bottom = computeBottomInset()
        return if (isDeckVisible) deckHeight + bottom else bottom
    }

    /**
     * Builds an insets state snapshot.
     */
    fun buildSnapshot(): InsetsSnapshot {
        return InsetsSnapshot(
            statusBarTop = statusBarTop,
            navBarBottom = navBarBottom,
            cutoutTop = cutoutTop,
            cutoutLeft = cutoutLeft,
            cutoutRight = cutoutRight,
            imeBottom = imeBottom,
            computedTop = computeTopInset(),
            computedBottom = computeBottomInset(),
            imeTranslationY = computeImeTranslationY()
        )
    }

    data class InsetsSnapshot(
        val statusBarTop: Int,
        val navBarBottom: Int,
        val cutoutTop: Int,
        val cutoutLeft: Int,
        val cutoutRight: Int,
        val imeBottom: Int,
        val computedTop: Int,
        val computedBottom: Int,
        val imeTranslationY: Float
    )
}
