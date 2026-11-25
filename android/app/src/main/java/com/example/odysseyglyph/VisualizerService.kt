package com.example.odysseyglyph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager

class VisualizerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var isRunning = false
            private set
    }

    private var glyphManager: GlyphMatrixManager? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var visualizerHelper: AudioVisualizerHelper
    private var isRegistered = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFrameHash = -1
    private var activeStyle = 1

    private val visualizerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || !isRegistered) {
                if (!isRunning) return
                mainHandler.postDelayed(this, 16)
                return
            }

            if (!MusicPlaybackState.isPlaying) {
                if (lastFrameHash != -1) {
                    try { 
                        glyphManager?.turnOff() 
                        lastFrameHash = -1
                    } catch (e: Exception) {
                        try { glyphManager?.setMatrixFrame(IntArray(625) { 0 }) } catch (e2: Exception) {}
                    }
                }
                visualizerHelper.stop()
                mainHandler.postDelayed(this, 100L)
                return
            } else {
                if (isMicStyle(activeStyle)) {
                    visualizerHelper.start()
                }
            }

            val audioLevels = visualizerHelper.currentLevels
            val frameBytes = GlyphFontEngine.renderFallbackFrame(audioLevels, activeStyle, SystemClock.elapsedRealtime())
            
            val currentHash = frameBytes.contentHashCode()
            if (currentHash != lastFrameHash) {
                lastFrameHash = currentHash
                try {
                    glyphManager?.setMatrixFrame(toIntArray(frameBytes))
                } catch (e: GlyphException) {
                    e.printStackTrace()
                }
            }
            mainHandler.postDelayed(this, 16)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        visualizerHelper = AudioVisualizerHelper(this)
        
        activeStyle = prefs.getInt("fallback_style", 2) // Default to Mic Wave

        initGlyphManager()
    }

    private fun initGlyphManager() {
        val mCallback = object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: ComponentName) {
                try {
                    if (Common.is20111()) {
                        glyphManager?.register(Glyph.DEVICE_20111)
                    } else if (Common.is22111()) {
                        glyphManager?.register(Glyph.DEVICE_22111)
                    } else if (Common.is23112()) {
                        glyphManager?.register(Glyph.DEVICE_23112)
                    } else {
                        glyphManager?.register(Glyph.DEVICE_23112) // Fallback
                    }
                    isRegistered = true
                } catch (e: Exception) {}
                try {
                    glyphManager?.turnOff()
                } catch (e: Exception) {}
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                isRegistered = false
            }
        }
        glyphManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphManager?.init(mCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VISUALIZER") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        prefs.edit().putBoolean("live_lyrics_enabled", false).apply()
        activeStyle = prefs.getInt("fallback_style", 2)

        val notification = createNotification()
        startForegroundSafely(notification, isMicStyle(activeStyle))

        isRunning = true

        if (LiveLyricsService.isRunning) {
            try {
                val stopLyrics = Intent(this, LiveLyricsService::class.java).apply { action = "STOP_LIVE_LYRICS" }
                startService(stopLyrics)
            } catch (_: Exception) {}
        }
        if (QuickSignalService.isRunning) {
            try {
                val stopSignal = Intent(this, QuickSignalService::class.java).apply {
                    action = QuickSignalService.ACTION_STOP_SIGNAL
                    putExtra(QuickSignalService.EXTRA_RESTORE_PREVIOUS, false)
                }
                startService(stopSignal)
            } catch (_: Exception) {}
        }
        
        if (isMicStyle(activeStyle)) {
            visualizerHelper.start()
        }
        
        mainHandler.removeCallbacks(visualizerRunnable)
        mainHandler.post(visualizerRunnable)
        
        LiveLyricsTileService.requestTileUpdate(this)
        OdysseyWidgetProvider.updateAllWidgets(this)
        
        return START_NOT_STICKY
    }

    private fun isMicStyle(style: Int): Boolean {
        return style == 2 || style == 3 || style == 5 || style == 8
    }

    private fun startForegroundSafely(notification: android.app.Notification, needsMic: Boolean) {
        val hasMicPerm = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (needsMic && hasMicPerm) {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(2, notification, types)
            } catch (_: Exception) {
                // On API 34+ microphone FGS can fail if eligibility isn't active; stay valid on specialUse.
                startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else if (needsMic && hasMicPerm) {
            startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "fallback_style") {
            sharedPreferences?.let {
                activeStyle = it.getInt("fallback_style", 2)
                val needsMic = isMicStyle(activeStyle)
                if (isRunning) {
                    startForegroundSafely(createNotification(), needsMic)
                }
                if (needsMic) {
                    visualizerHelper.start()
                } else {
                    visualizerHelper.stop()
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        isRegistered = false
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        mainHandler.removeCallbacks(visualizerRunnable)
        visualizerHelper.stop()

        if (!LiveLyricsService.isRunning && !QuickSignalService.isRunning) {
            try { glyphManager?.turnOff() } catch (_: Exception) {}
            try { glyphManager?.unInit() } catch (_: Exception) {}
        }
        glyphManager = null
        
        LiveLyricsTileService.requestTileUpdate(this)
        OdysseyWidgetProvider.updateAllWidgets(this)
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val channelId = "VisualizerServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Standalone Visualizer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, VisualizerService::class.java).apply {
            action = "STOP_VISUALIZER"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Odyssey Visualizer Active")
            .setContentText("Tap Stop to turn off the matrix.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(0, "STOP", stopPendingIntent)
            .build()
    }

    private fun toIntArray(bytes: ByteArray): IntArray {
        val ints = IntArray(bytes.size)
        for (i in bytes.indices) {
            ints[i] = (bytes[i].toInt() and 0xFF) * 16
        }
        return ints
    }
}
