package com.example.odysseyglyph

import android.app.Activity

/**
 * OdysseyNavDockHelper
 *
 * Configures the unified Liquid Glass Navigation Dock across all primary activities,
 * enabling instant 1-tap bidirectional navigation between Console, Lyrics, Studio, and Vault
 * with state preservation (REORDER_TO_FRONT) and directional spatial animations.
 */
object OdysseyNavDockHelper {

    val MENU_ITEMS = listOf("Console", "Lyrics", "Studio", "Vault")

    fun attach(activity: Activity, navBar: LiquidGlassSegmentedBar, activeIndex: Int) {
        navBar.setSegments(MENU_ITEMS, initialIndex = activeIndex)
        navBar.onSegmentSelected = { index, _ ->
            if (index != activeIndex) {
                val targetClass = when (index) {
                    0 -> MainActivity::class.java
                    1 -> LiveLyricsActivity::class.java
                    2 -> LyricStudioActivity::class.java
                    3 -> GalleryActivity::class.java
                    else -> MainActivity::class.java
                }
                NavigationHelper.navigateTo(activity, targetClass, activeIndex, index)
            }
        }
    }
}
