package com.example.odysseyglyph

import android.content.Context
import android.os.Build

/**
 * VirtualGlyphMatrixManager
 *
 * Provides on-screen 25×25 Glyph Matrix simulation for non-Nothing devices and emulators
 * to support Google Play Store closed testing without physical Nothing Phone (3) hardware.
 */
object VirtualGlyphMatrixManager {

    private const val PREFS_NAME = "glyph_virtual_sim_prefs"
    private const val KEY_FORCE_VIRTUAL = "key_force_virtual_simulation"

    fun isNothingHardware(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("nothing") || brand.contains("nothing")
    }

    /**
     * Returns true if simulation mode is active (always true on emulators / non-Nothing hardware,
     * or if explicitly enabled by the user).
     */
    fun isVirtualSimulationActive(context: Context): Boolean {
        if (!isNothingHardware()) return true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FORCE_VIRTUAL, false)
    }

    fun setForceVirtualSimulation(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FORCE_VIRTUAL, enabled).apply()
    }
}
