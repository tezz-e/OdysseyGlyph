package com.example.odysseyglyph

import android.app.Activity

/**
 * OdysseyNavDockHelper
 *
 * Configures the Creative Suite Liquid Glass Navigation Dock across sub-screens,
 * enabling instant 1-tap peer navigation between Lyrics, Studio, and Vault
 * with state preservation (REORDER_TO_FRONT) and directional spatial animations,
 * while maintaining Console (MainActivity) as the clean Parent Command Center.
 */
object OdysseyNavDockHelper {

    val MENU_ITEMS = listOf("Lyrics", "Studio", "Vault")

    fun attach(activity: Activity, navBar: LiquidGlassSegmentedBar, activeIndex: Int) {
        navBar.setSegments(MENU_ITEMS, initialIndex = activeIndex)
        navBar.onSegmentSelected = { index, _ ->
            if (index != activeIndex) {
                val targetClass = when (index) {
                    0 -> LiveLyricsActivity::class.java
                    1 -> LyricStudioActivity::class.java
                    2 -> GalleryActivity::class.java
                    else -> LiveLyricsActivity::class.java
                }
                NavigationHelper.navigateTo(activity, targetClass, activeIndex, index)
            }
        }
    }
}
