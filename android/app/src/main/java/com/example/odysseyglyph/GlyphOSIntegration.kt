package com.example.odysseyglyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import com.google.android.material.snackbar.Snackbar

/**
 * GlyphOSIntegration
 *
 * Handles deep integration with Nothing OS:
 * - Nothing OS 5.0 / 3.0 Dedicated Glyph App (`com.nothing.glyph`)
 * - Nothing Glyph Toys Manager (`com.nothing.thirdparty`)
 * - System Settings Glyph Center (`com.android.settings.Settings$GlyphActivity` / `com.nothing.settings.GLYPH`)
 * - Nothing Glyph Composer (`com.nothing.glyph.composer`)
 */
object GlyphOSIntegration {

    /**
     * Attempts to open the dedicated Glyph application or Glyph Toys Manager in Nothing OS.
     * Falls back gracefully across all known Nothing OS builds and provides tactile feedback.
     */
    fun openGlyphAppOrToys(context: Context, anchorView: View? = null) {
        val pm = context.packageManager

        // Priority 1: Nothing OS 5.0 / 3.0 Dedicated Glyph App
        val glyphAppIntent = pm.getLaunchIntentForPackage("com.nothing.glyph")
        if (glyphAppIntent != null) {
            try {
                glyphAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(glyphAppIntent)
                return
            } catch (e: Exception) {
                // Continue to fallback
            }
        }

        // Priority 2: Nothing Glyph Toys Manager (Hardware Matrix Toys)
        try {
            val toysIntent = Intent().apply {
                component = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(toysIntent)
            return
        } catch (e: Exception) {
            // Continue to fallback
        }

        // Priority 3: Nothing OS Glyph Settings Intent
        try {
            val glyphSettingsIntent = Intent("com.nothing.settings.GLYPH").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(glyphSettingsIntent)
            return
        } catch (e: Exception) {
            // Continue to fallback
        }

        // Priority 4: Android Settings Glyph Activity
        try {
            val settingsIntent = Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.Settings\$GlyphActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return
        } catch (e: Exception) {
            // Continue to fallback
        }

        // Priority 5: Nothing Glyph Composer
        val composerIntent = pm.getLaunchIntentForPackage("com.nothing.glyph.composer")
        if (composerIntent != null) {
            try {
                composerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(composerIntent)
                return
            } catch (e: Exception) {
                // Continue to fallback
            }
        }

        // Graceful feedback for emulator / non-Nothing devices
        anchorView?.let { view ->
            Snackbar.make(view, "Glyph App not found. Compatible with Nothing OS.", Snackbar.LENGTH_LONG)
                .applyNothingStyle()
                .show()
        }
    }
}
