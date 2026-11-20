package com.example.odysseyglyph

import android.app.Activity
import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Modern Edge-to-Edge Window Insets and 120Hz IME Keyboard Synchronization Utility.
 * Replaces static <Space> padding hacks with dynamic system bars, cutout, and keyboard insets.
 */
object WindowInsetsUtil {

    // --- Pure Mathematical Calculations (JVM Testable) ---

    /**
     * Calculates the top safe inset accounting for both status bar and punch-hole camera cutout.
     */
    fun computeTopPadding(statusBarTop: Int, cutoutTop: Int): Int = maxOf(statusBarTop, cutoutTop)

    fun calculateTopInset(statusBarTop: Int, cutoutTop: Int): Int = computeTopPadding(statusBarTop, cutoutTop)

    /**
     * Calculates the bottom safe inset combining navigation bar, soft keyboard, and optional overlay deck.
     */
    fun computeBottomInset(navBarsBottom: Int, imeBottom: Int): Int = maxOf(navBarsBottom, imeBottom)

    fun calculateBottomInset(navBarBottom: Int, imeBottom: Int, overlayHeight: Int = 0): Int =
        maxOf(navBarBottom, imeBottom) + overlayHeight

    fun computeScrollPaddingBottom(deckHeight: Int, bottomInset: Int, isDeckVisible: Boolean): Int {
        return if (isDeckVisible) deckHeight + bottomInset else bottomInset
    }

    data class ExclusionBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        fun toRect(): Rect = Rect(left, top, right, bottom)
    }

    fun computeExclusionBounds(cx: Int, cy: Int, radius: Int): ExclusionBounds =
        ExclusionBounds(cx - radius, cy - radius, cx + radius, cy + radius)

    fun computeExclusionRect(cx: Int, cy: Int, radius: Int): Rect {
        return Rect(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    /**
     * Calculates the 120Hz translation offset for views anchored above the keyboard.
     */
    fun calculateImeTranslationY(imeBottom: Int, navBarBottom: Int): Float =
        -(imeBottom - navBarBottom).coerceAtLeast(0).toFloat()

    // --- Android Runtime View Bindings ---

    /**
     * Enables edge-to-edge window decor layout for the activity.
     */
    fun setupEdgeToEdge(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    /**
     * Applies dynamic system bars and display cutout insets as view padding.
     * Preserves initial padding defined in XML to avoid compounding inflation.
     */
    fun applySystemBarsPadding(
        view: View,
        applyTop: Boolean = false,
        applyBottom: Boolean = true,
        applyLeft: Boolean = false,
        applyRight: Boolean = false,
        overlayView: View? = null,
        includeIme: Boolean = true
    ) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            val extraBottom = overlayView?.let {
                if (it.visibility == View.VISIBLE) it.height else 0
            } ?: 0

            val imeBottom = if (includeIme) ime.bottom else 0
            val computedBottom = if (applyBottom) {
                initialBottom + calculateBottomInset(systemBars.bottom, imeBottom, extraBottom)
            } else {
                v.paddingBottom
            }

            v.updatePadding(
                left = if (applyLeft) initialLeft + systemBars.left else v.paddingLeft,
                top = if (applyTop) initialTop + systemBars.top else v.paddingTop,
                right = if (applyRight) initialRight + systemBars.right else v.paddingRight,
                bottom = computedBottom
            )

            windowInsets
        }

        if (view.isAttachedToWindow) {
            view.requestApplyInsets()
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    v.requestApplyInsets()
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
    }

    /**
     * Applies dynamic system bar and cutout insets to activity layout views.
     */
    fun applyEdgeToEdge(
        rootView: View,
        appBar: View? = null,
        bottomDeck: View? = null,
        scrollView: View? = null
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            appBar?.updatePadding(
                top = computeTopPadding(statusBar.top, cutout.top),
                left = cutout.left,
                right = cutout.right
            )

            val bottomInset = computeBottomInset(navBars.bottom, ime.bottom)
            bottomDeck?.updatePadding(bottom = bottomInset)

            val isDeckVisible = bottomDeck?.visibility == View.VISIBLE
            val deckHeight = bottomDeck?.height ?: 0
            scrollView?.updatePadding(
                bottom = computeScrollPaddingBottom(deckHeight, bottomInset, isDeckVisible)
            )

            insets
        }
    }

    /**
     * Attaches a 120Hz WindowInsetsAnimationCompat callback to glide a target view
     * (e.g., sticky action bar, bottom search actions) smoothly in sync with the soft keyboard.
     */
    fun applyIme120HzSync(targetView: View) {
        ViewCompat.setWindowInsetsAnimationCallback(
            targetView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val translationY = calculateImeTranslationY(imeInsets.bottom, navInsets.bottom)
                    targetView.translationY = translationY
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        targetView.translationY = 0f
                    }
                }
            }
        )
    }

    /**
     * Glides the bottom deck view in locked 120Hz lockstep with the soft keyboard (IME).
     */
    fun applyImeAnimation(rootView: View, bottomDeck: View) {
        ViewCompat.setWindowInsetsAnimationCallback(
            rootView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val translationY = calculateImeTranslationY(imeInsets.bottom, navInsets.bottom)
                    bottomDeck.translationY = translationY
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        bottomDeck.translationY = 0f
                    }
                }
            }
        )
    }
}
