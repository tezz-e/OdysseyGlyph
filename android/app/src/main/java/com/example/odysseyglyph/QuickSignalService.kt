package com.example.odysseyglyph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import java.util.concurrent.CopyOnWriteArrayList

class QuickSignalService : Service() {

    fun interface StateListener {
        fun onStateChanged(active: Boolean, text: String, remaining: Int)
    }

    companion object {
        const val CHANNEL_ID = "QuickSignalChannel"
        const val NOTIFICATION_ID = 42

        const val ACTION_START_SIGNAL = "com.example.odysseyglyph.ACTION_START_SIGNAL"
        const val ACTION_STOP_SIGNAL = "com.example.odysseyglyph.ACTION_STOP_SIGNAL"

        const val EXTRA_TEXT = "extra_signal_text"
        const val EXTRA_DURATION_SEC = "extra_duration_sec"
        const val EXTRA_FONT_STYLE = "extra_font_style"
        const val EXTRA_RESTORE_PREVIOUS = "extra_restore_previous"

        var isRunning: Boolean = false
            private set

        var activeText: String = ""
            private set

        var durationSec: Int = 15
            private set

        var remainingSec: Int = 15
            private set

        private val listeners = CopyOnWriteArrayList<StateListener>()

        fun addListener(listener: StateListener) {
            listeners.add(listener)
            listener.onStateChanged(isRunning, activeText, remainingSec)
        }

        fun removeListener(listener: StateListener) {
            listeners.remove(listener)
        }

        private fun notifyStateChanged() {
            for (l in listeners) {
                l.onStateChanged(isRunning, activeText, remainingSec)
            }
        }
    }

    private var glyphManager: GlyphMatrixManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRegistered = false

    private var wasLyricsActive = false
    private var wasVisualizerActive = false

    private var scrollOffset = 25f
    private var marqueeWidth = 0f
    private var isStationary = true
    private var fontStyle: GlyphFontEngine.FontStyle = GlyphFontEngine.FontStyle.BLOCK_BOLD

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val frameBytes: ByteArray
            if (isStationary) {
                frameBytes = QuickSignalRenderer.renderFrame(activeText, fontStyle)
            } else {
                scrollOffset -= 0.65f // Smooth readable scroll speed
                if (scrollOffset < -marqueeWidth - 4f) {
                    scrollOffset = 25f // Loop marquee smoothly
                }
                frameBytes = QuickSignalRenderer.renderFrame(activeText, fontStyle, scrollOffset)
            }

            GlyphFrameBus.postFrame(frameBytes)
            try {
                glyphManager?.setMatrixFrame(toIntArray(frameBytes))
            } catch (_: Exception) {}

            val delay = if (isStationary) 120L else 32L // ~30 fps marquee
            mainHandler.postDelayed(this, delay)
        }
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (durationSec > 0) {
                remainingSec--
                notifyStateChanged()
                updateNotification()

                if (remainingSec <= 0) {
                    stopSignal()
                    return
                }
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val glyphCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName) {
            try {
                if (Common.is20111()) {
                    glyphManager?.register(Glyph.DEVICE_20111)
                } else if (Common.is22111()) {
                    glyphManager?.register(Glyph.DEVICE_22111)
                } else {
                    glyphManager?.register(Glyph.DEVICE_23112)
                }
                isRegistered = true
            } catch (_: Exception) {}
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isRegistered = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        glyphManager = GlyphMatrixManager.getInstance(applicationContext)
        try {
            glyphManager?.init(glyphCallback)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START_SIGNAL -> {
                val text = intent.getStringExtra(EXTRA_TEXT)?.trim() ?: "WAIT"
                val duration = intent.getIntExtra(EXTRA_DURATION_SEC, 15)
                val fontStyleInt = intent.getIntExtra(EXTRA_FONT_STYLE, 2)

                startSignal(text, duration, fontStyleInt)
            }
            ACTION_STOP_SIGNAL -> {
                val restorePrevious = intent.getBooleanExtra(EXTRA_RESTORE_PREVIOUS, true)
                stopSignal(restorePrevious = restorePrevious)
            }
        }
        return START_NOT_STICKY
    }

    private fun startSignal(text: String, duration: Int, fontStyleInt: Int = 2) {
        // Suspend any competing background matrix services smoothly
        if (LiveLyricsService.isRunning) {
            wasLyricsActive = true
            try {
                startService(Intent(this, LiveLyricsService::class.java).apply { action = "STOP_LIVE_LYRICS" })
            } catch (_: Exception) {}
        }
        if (VisualizerService.isRunning) {
            wasVisualizerActive = true
            try {
                startService(Intent(this, VisualizerService::class.java).apply { action = "STOP_VISUALIZER" })
            } catch (_: Exception) {}
        }

        activeText = text
        durationSec = duration
        remainingSec = if (duration > 0) duration else -1
        isRunning = true

        fontStyle = when (fontStyleInt) {
            0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
            1 -> GlyphFontEngine.FontStyle.SMOOTH
            2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
            else -> GlyphFontEngine.FontStyle.BLOCK_BOLD
        }

        isStationary = activeText.length <= 4 || QuickSignalRenderer.isCuratedSymbol(activeText)
        marqueeWidth = QuickSignalRenderer.measureMarqueeWidth(activeText, fontStyle)
        scrollOffset = 25f

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                fgsType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                if (fgsType != 0) {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (_: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        mainHandler.removeCallbacks(frameRunnable)
        mainHandler.post(frameRunnable)

        mainHandler.removeCallbacks(countdownRunnable)
        if (durationSec > 0) {
            mainHandler.postDelayed(countdownRunnable, 1000L)
        }

        notifyStateChanged()
    }

    private fun stopSignal(restorePrevious: Boolean = true) {
        isRunning = false
        mainHandler.removeCallbacks(frameRunnable)
        mainHandler.removeCallbacks(countdownRunnable)

        if (!LiveLyricsService.isRunning && !VisualizerService.isRunning) {
            try {
                glyphManager?.turnOff()
            } catch (_: Exception) {}
            GlyphFrameBus.clear()
        }

        notifyStateChanged()
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Restore prior matrix owner only when user/timer cleared the signal
        if (restorePrevious) {
            if (wasLyricsActive) {
                wasLyricsActive = false
                try {
                    val startLyrics = Intent(this, LiveLyricsService::class.java).apply { action = "START_LIVE_LYRICS" }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(startLyrics)
                    } else {
                        startService(startLyrics)
                    }
                } catch (_: Exception) {}
            } else if (wasVisualizerActive) {
                wasVisualizerActive = false
                try {
                    val startVis = Intent(this, VisualizerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(startVis)
                    } else {
                        startService(startVis)
                    }
                } catch (_: Exception) {}
            }
        } else {
            wasLyricsActive = false
            wasVisualizerActive = false
        }

        stopSelf()
    }

    private fun updateNotification() {
        if (!isRunning) return
        val notification = buildNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, QuickSignalActivity::class.java)
        val pendingTap = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clearIntent = Intent(this, QuickSignalService::class.java).apply {
            action = ACTION_STOP_SIGNAL
        }
        val pendingClear = PendingIntent.getService(
            this,
            1,
            clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            durationSec > 0 -> "Displaying on rear matrix (${remainingSec}s remaining)"
            else -> "Displaying on rear matrix (Until cleared)"
        }

        val clearAction = NotificationCompat.Action.Builder(
            R.drawable.ic_delete,
            "Clear Signal",
            pendingClear
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quick Signal: \"$activeText\"")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingTap)
            .addAction(clearAction)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Signal Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun toIntArray(bytes: ByteArray): IntArray {
        val ints = IntArray(bytes.size)
        for (i in bytes.indices) {
            ints[i] = (bytes[i].toInt() and 0xFF) * 16
        }
        return ints
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacks(frameRunnable)
        mainHandler.removeCallbacks(countdownRunnable)
        if (!LiveLyricsService.isRunning && !VisualizerService.isRunning) {
            try {
                glyphManager?.turnOff()
                glyphManager?.unInit()
            } catch (_: Exception) {}
        }
        glyphManager = null
        notifyStateChanged()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
