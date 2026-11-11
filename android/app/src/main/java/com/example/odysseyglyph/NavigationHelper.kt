package com.example.odysseyglyph

import android.app.Activity
import android.content.Intent

/**
 * NavigationHelper
 *
 * Provides Apple/Nothing-grade directional spatial menu transitions and back-stack handling:
 * - Entering a deeper/peer menu: slides in from right (push).
 * - Returning to parent/previous menu: slides out to right (pop).
 * - Avoids duplicate activity instances with single-top intent flags.
 */
object NavigationHelper {

    fun navigateTo(current: Activity, targetClass: Class<*>, activeIndex: Int, targetIndex: Int) {
        if (current.javaClass == targetClass) return

        val intent = Intent(current, targetClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        current.startActivity(intent)

        if (targetIndex >= activeIndex) {
            // Forward/Deeper navigation: slide in from right
            current.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            // Backward/Return navigation: slide in from left
            current.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    fun finishWithPop(activity: Activity) {
        activity.finish()
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
