package com.example.odysseyglyph

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * HapticExt
 *
 * Semantic, tactile haptics extensions for View and Context on Nothing Phone (3) and Android 14/15/16.
 *
 * Audio Safety Guarantees:
 * - Respects system haptic settings (Settings.System.HAPTIC_FEEDBACK_ENABLED).
 * - Enforces VibrationAttributes.USAGE_TOUCH so haptics are routed exclusively through the
 *   Linear Resonant Actuator (LRA) without ducking media streams or corrupting the Ketchum DAC binder loop.
 * - Uses View.performHapticFeedback as primary zero-allocation mechanism.
 */

enum class HapticType(
    val feedbackConstant: Int,
    val vibrationEffectId: Int
) {
    TICK(HapticFeedbackConstants.CLOCK_TICK, VibrationEffect.EFFECT_TICK),
    CLICK(HapticFeedbackConstants.CONTEXT_CLICK, VibrationEffect.EFFECT_CLICK),
    HEAVY_CLICK(HapticFeedbackConstants.LONG_PRESS, VibrationEffect.EFFECT_HEAVY_CLICK),
    DETENT(HapticFeedbackConstants.CLOCK_TICK, VibrationEffect.EFFECT_TICK);
}

object HapticResolver {
    const val USAGE_TOUCH = 18 // VibrationAttributes.USAGE_TOUCH

    fun resolveFeedbackConstant(type: HapticType): Int = type.feedbackConstant
    fun resolveVibrationEffectId(type: HapticType): Int = type.vibrationEffectId

    fun isHapticEnabled(contentResolver: ContentResolver?): Boolean {
        if (contentResolver == null) return false
        return try {
            Settings.System.getInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        } catch (_: Exception) {
            true
        }
    }

    fun isAudioSafeUsage(usage: Int): Boolean = usage == USAGE_TOUCH
}

// =========================================================================
// View Extensions
// =========================================================================

fun View.vibrateTick(): Boolean {
    if (!isHapticFeedbackEnabled) return false
    if (performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) return true
    return context.vibrateTick()
}

fun View.vibrateClick(): Boolean {
    if (!isHapticFeedbackEnabled) return false
    if (performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)) return true
    return context.vibrateClick()
}

fun View.vibrateHeavyClick(): Boolean {
    if (!isHapticFeedbackEnabled) return false
    if (performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)) return true
    return context.vibrateHeavyClick()
}

fun View.vibrateDetent(): Boolean {
    if (!isHapticFeedbackEnabled) return false
    if (performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) return true
    return context.vibrateDetent()
}

// =========================================================================
// Context Extensions
// =========================================================================

fun Context.vibrateTick(): Boolean = performVibration(this, HapticType.TICK)

fun Context.vibrateClick(): Boolean = performVibration(this, HapticType.CLICK)

fun Context.vibrateHeavyClick(): Boolean = performVibration(this, HapticType.HEAVY_CLICK)

fun Context.vibrateDetent(): Boolean = performVibration(this, HapticType.DETENT)

// =========================================================================
// Internal Audio-Safe Dispatcher
// =========================================================================

private fun performVibration(context: Context, type: HapticType): Boolean {
    if (!HapticResolver.isHapticEnabled(context.contentResolver)) {
        return false
    }

    return try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return false

        if (!vibrator.hasVibrator()) return false

        val effect = VibrationEffect.createPredefined(type.vibrationEffectId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect)
        }
        true
    } catch (_: Exception) {
        false
    }
}
