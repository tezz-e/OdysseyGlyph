package com.example.odysseyglyph

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView

/**
 * ProminentDisclosureDialog
 *
 * Mandatory Google Play Store compliance modal.
 * Explains on-device NOTIFICATION_LISTENER permission usage prior to redirecting
 * to Android Notification Access settings.
 *
 * Compliance Guarantees:
 * - Discloses exact purpose: Media playback metadata (title/artist) for rear Glyph LED matrix sync.
 * - Explicitly states zero storage, zero tracking, zero transmission of personal notifications.
 * - Styled in Nothing OS industrial monochrome minimalism.
 */
class ProminentDisclosureDialog(
    context: Context,
    private val onAccepted: (() -> Unit)? = null,
    private val onDeclined: (() -> Unit)? = null
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Industrial Nothing OS dark modal layout
        setContentView(R.layout.dialog_prominent_disclosure)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setGravity(Gravity.CENTER)

        val btnAccept = findViewById<Button>(R.id.btnDisclosureAccept)
        val btnDecline = findViewById<Button>(R.id.btnDisclosureDecline)

        btnAccept?.setOnClickListener {
            dismiss()
            onAccepted?.invoke()
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // Fallback to general settings if OEM lacks direct listener intent
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }

        btnDecline?.setOnClickListener {
            dismiss()
            onDeclined?.invoke()
        }
    }

    companion object {
        fun showIfRequired(context: Context, onAccepted: (() -> Unit)? = null): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            val isEnabled = enabledListeners.contains(context.packageName)

            if (!isEnabled) {
                ProminentDisclosureDialog(context, onAccepted).show()
                return true
            }
            return false
        }
    }
}
