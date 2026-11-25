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
    private val onDeclined: (() -> Unit)? = null,
    private val badgeText: String? = null,
    private val titleText: String? = null,
    private val messageText: String? = null,
    private val acceptText: String? = null,
    private val launchNotificationSettings: Boolean = true
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

        badgeText?.let { findViewById<TextView>(R.id.tvDisclosureBadge)?.text = it }
        titleText?.let { findViewById<TextView>(R.id.tvDisclosureTitle)?.text = it }
        messageText?.let { findViewById<TextView>(R.id.tvDisclosureMessage)?.text = it }

        val btnAccept = findViewById<Button>(R.id.btnDisclosureAccept)
        val btnDecline = findViewById<Button>(R.id.btnDisclosureDecline)
        acceptText?.let { btnAccept?.text = it }

        btnAccept?.setOnClickListener {
            dismiss()
            onAccepted?.invoke()
            if (launchNotificationSettings) {
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
        }

        btnDecline?.setOnClickListener {
            dismiss()
            onDeclined?.invoke()
        }
    }

    companion object {
        fun showIfRequired(
            context: Context,
            onAccepted: (() -> Unit)? = null,
            onDeclined: (() -> Unit)? = null
        ): Boolean {
            val isEnabled = androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)

            if (!isEnabled) {
                ProminentDisclosureDialog(
                    context = context,
                    onAccepted = onAccepted,
                    onDeclined = onDeclined
                ).show()
                return true
            }
            return false
        }

        fun showAudioDisclosureIfRequired(
            context: Context,
            onAccepted: () -> Unit,
            onDeclined: (() -> Unit)? = null
        ): Boolean {
            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasMic) return false

            ProminentDisclosureDialog(
                context = context,
                onAccepted = onAccepted,
                onDeclined = onDeclined,
                badgeText = "MICROPHONE DISCLOSURE",
                titleText = "Real-Time Audio Visualizer",
                messageText = "Glyph Odyssey uses the microphone strictly on-device in volatile memory to drive live 25x25 Glyph LED patterns. Audio is never recorded, stored, or transmitted off your device.",
                acceptText = "CONTINUE",
                launchNotificationSettings = false
            ).show()
            return true
        }
    }
}
