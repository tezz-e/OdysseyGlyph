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
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.Common
import kotlin.concurrent.thread

class LiveLyricsService : Service(), MusicPlaybackState.StateChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    companion object {
        const val CHANNEL_ID = "LiveLyricsServiceChannel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
    }

    private var glyphManager: GlyphMatrixManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var isRegistered = false
    private var pendingStopRunnable: Runnable? = null
    private var currentParsedLyrics: List<Pair<Long, String>> = emptyList()
    private var currentTrackId = ""
    private var fontStyle = GlyphFontEngine.FontStyle.SMOOTH
    
    private lateinit var prefs: SharedPreferences
    private var fallbackEnabled = true
    private var fallbackStyle = 1

    private val glyphCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName) {
            try {
                if (Common.is20111()) {
                    glyphManager?.register(Glyph.DEVICE_20111)
                } else if (Common.is22111()) {
                    glyphManager?.register(Glyph.DEVICE_22111)
                } else if (Common.is23112()) {
                    glyphManager?.register(Glyph.DEVICE_23112)
                } else {
                    glyphManager?.register(Glyph.DEVICE_23112)
                }
                isRegistered = true
            } catch (e: Exception) {}
            MusicPlaybackState.addListener(this@LiveLyricsService)
            
            // Re-read settings every time we connect, in case they changed
            val p = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
            fallbackEnabled = p.getBoolean("fallback_enabled", true)
            fallbackStyle = p.getInt("fallback_style", 1)
            val fontStyleInt = p.getInt("live_font_style", 0)
            fontStyle = when (fontStyleInt) {
                1 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                2 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                else -> GlyphFontEngine.FontStyle.SMOOTH
            }
            
            if (MusicPlaybackState.hasActiveSession) {
                onMetadataChanged(MusicPlaybackState.trackTitle, MusicPlaybackState.artist)
            }
            
            if (p.getBoolean("live_lyrics_enabled", false)) {
                flashReady()
            }
            
            // This is the ONLY place the playback loop starts
            mainHandler.removeCallbacks(playbackRunnable)
            mainHandler.post(playbackRunnable)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isRegistered = false
            MusicPlaybackState.removeListener(this@LiveLyricsService)
        }
    }
    
    private var lastFrameHash = -1
    
    private lateinit var visualizerHelper: AudioVisualizerHelper

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isRegistered) return
            
            val prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("live_lyrics_enabled", false)
            
            val lyricsToUse = MusicPlaybackState.manualOverrideLyrics ?: currentParsedLyrics
            
            if (!isEnabled || !MusicPlaybackState.hasActiveSession || !MusicPlaybackState.isPlaying) {
                if (lastFrameHash != -1) {
                    try { 
                        glyphManager?.turnOff() 
                        lastFrameHash = -1
                    } catch (e: Exception) {
                        try { glyphManager?.setMatrixFrame(IntArray(33) { 0 }) } catch (e2: Exception) {}
                    }
                }
                visualizerHelper.stop()
                mainHandler.postDelayed(this, 100L)
                return
            }
            
            if (lyricsToUse.isEmpty()) {
                if (fallbackEnabled) {
                    if (isMicStyle(fallbackStyle)) {
                        visualizerHelper.start()
                    } else {
                        visualizerHelper.stop()
                    }
                    val frameBytes = GlyphFontEngine.renderFallbackFrame(visualizerHelper.currentLevels, fallbackStyle, SystemClock.elapsedRealtime())
                    val currentHash = frameBytes.contentHashCode()
                    if (currentHash != lastFrameHash) {
                        lastFrameHash = currentHash
                        try { glyphManager?.setMatrixFrame(MatrixConfig.formatForHardware(frameBytes)) } catch (e: Exception) {}
                    }
                    mainHandler.postDelayed(this, 16L)
                } else {
                    if (lastFrameHash != -1) {
                        try { 
                            glyphManager?.turnOff() 
                            lastFrameHash = -1
                        } catch (e: Exception) {
                            try { glyphManager?.setMatrixFrame(IntArray(33) { 0 }) } catch (e2: Exception) {}
                        }
                    }
                    visualizerHelper.stop()
                    mainHandler.postDelayed(this, 100L)
                }
                return
            }
            
            val animStyle = prefs.getInt("live_anim_style", 0)
            val syncOffset = prefs.getInt("live_sync_offset", 0)
            
            var estimatedPositionMs = MusicPlaybackState.position
            if (MusicPlaybackState.isPlaying) {
                estimatedPositionMs += ((SystemClock.elapsedRealtime() - MusicPlaybackState.lastUpdateTime) * MusicPlaybackState.playbackSpeed).toLong()
            }
            
            estimatedPositionMs += syncOffset
            
            val frameData = LrcUtils.getFrameTextAtTime(lyricsToUse, estimatedPositionMs, fontStyle, animStyle)
            
            if (frameData == null) {
                if (fallbackEnabled) {
                    if (isMicStyle(fallbackStyle)) {
                        visualizerHelper.start()
                    } else {
                        visualizerHelper.stop()
                    }
                    val frameBytes = GlyphFontEngine.renderFallbackFrame(visualizerHelper.currentLevels, fallbackStyle, SystemClock.elapsedRealtime())
                    val currentHash = frameBytes.contentHashCode()
                    if (currentHash != lastFrameHash) {
                        lastFrameHash = currentHash
                        try { glyphManager?.setMatrixFrame(MatrixConfig.formatForHardware(frameBytes)) } catch (e: Exception) {}
                    }
                } else {
                    if (lastFrameHash != -1) {
                        try { 
                            glyphManager?.turnOff() 
                            lastFrameHash = -1
                        } catch (e: Exception) {
                            try { glyphManager?.setMatrixFrame(IntArray(33) { 0 }) } catch (e2: Exception) {}
                        }
                    }
                    visualizerHelper.stop()
                }
            } else {
                visualizerHelper.stop()
                val frameText = frameData.first
                val offsetX = frameData.second
                val frameBytes = GlyphFontEngine.renderTextFrame(frameText, fontStyle, offsetX, autoScale = false)
                
                val currentHash = frameBytes.contentHashCode()
                if (currentHash != lastFrameHash) {
                    lastFrameHash = currentHash
                    try { glyphManager?.setMatrixFrame(MatrixConfig.formatForHardware(frameBytes)) } catch (e: Exception) {}
                }
            }
            
            mainHandler.postDelayed(this, 16L)
        }
    }
    
    private fun toIntArray(bytes: ByteArray): IntArray {
        val ints = IntArray(bytes.size)
        for (i in bytes.indices) {
            ints[i] = (bytes[i].toInt() and 0xFF) * 16
        }
        return ints
    }

    override fun onCreate() {
        super.onCreate()
        
        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
        fallbackEnabled = prefs.getBoolean("fallback_enabled", true)
        fallbackStyle = prefs.getInt("fallback_style", 1)

        createNotificationChannel()

        visualizerHelper = AudioVisualizerHelper(this)
        glyphManager = GlyphMatrixManager.getInstance(applicationContext)
        prefs.registerOnSharedPreferenceChangeListener(this)
        // Don't call glyphManager.init() here — START_LIVE_LYRICS will force a fresh bind
        // Don't call updateServiceState() here — it reads live_lyrics_enabled=false
        // before the START intent arrives, causing an instant self-destruct
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "fallback_style" -> {
                fallbackStyle = sharedPreferences?.getInt("fallback_style", 1) ?: 1
            }
            "fallback_enabled" -> {
                fallbackEnabled = sharedPreferences?.getBoolean("fallback_enabled", true) ?: true
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "START_LIVE_LYRICS" -> {
                    // Cancel any pending delayed stop — user turned it back on quickly
                    pendingStopRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingStopRunnable = null

                    // Call startForeground IMMEDIATELY — before ANY pref reads or async work.
                    // This is critical on Android 14+ to avoid ForegroundServiceDidNotStartInTimeException.
                    createNotificationChannel()
                    val notification = buildNotification()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                            }
                        }
                        try {
                            startForeground(NOTIFICATION_ID, notification, types)
                        } catch (e: Exception) {
                            try {
                                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                            } catch (e2: Exception) {
                                startForeground(NOTIFICATION_ID, notification)
                            }
                        }
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    isRunning = true
                    getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE).edit().putBoolean("live_lyrics_enabled", true).apply()
                    
                    if (MusicPlaybackState.hasActiveSession) {
                        currentTrackId = ""
                        onMetadataChanged(MusicPlaybackState.trackTitle, MusicPlaybackState.artist)
                    }
                    
                    // Force a fresh hardware bind — onServiceConnected will start the playback loop
                    isRegistered = false
                    mainHandler.removeCallbacks(playbackRunnable)
                    try { glyphManager?.unInit() } catch (e: Exception) {}
                    glyphManager?.init(glyphCallback)
                    
                    LiveLyricsTileService.requestTileUpdate(this)
                    OdysseyWidgetProvider.updateAllWidgets(this)
                }
                "STOP_LIVE_LYRICS" -> {
                    getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE).edit().putBoolean("live_lyrics_enabled", false).apply()
                    isRunning = false
                    isRegistered = false
                    mainHandler.removeCallbacks(playbackRunnable)
                    visualizerHelper.stop()
                    try { glyphManager?.turnOff() } catch (e: Exception) {}
                    if (!VisualizerService.isRunning) {
                        try { glyphManager?.unInit() } catch (e: Exception) {}
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    LiveLyricsTileService.requestTileUpdate(this)
                    OdysseyWidgetProvider.updateAllWidgets(this)
                    // Delay actual destroy so a rapid restart can reuse this instance
                    val stopRunnable = Runnable {
                        pendingStopRunnable = null
                        stopSelf()
                    }
                    pendingStopRunnable = stopRunnable
                    mainHandler.postDelayed(stopRunnable, 600)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        // Cancel any pending delayed stop to avoid ghost callbacks
        pendingStopRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingStopRunnable = null
        LiveLyricsTileService.requestTileUpdate(this)
        OdysseyWidgetProvider.updateAllWidgets(this)
        
        isRegistered = false
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        MusicPlaybackState.removeListener(this)
        mainHandler.removeCallbacks(playbackRunnable)
        visualizerHelper.stop()
        try {
            glyphManager?.turnOff()
        } catch (e: Exception) {}
        
        if (!VisualizerService.isRunning) {
            try {
                glyphManager?.unInit()
            } catch (e: Exception) {
                // Ignore service not registered error on unbind
            }
        }
        glyphManager = null
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun flashReady() {
        try {
            val frame = IntArray(33) { 100 }
            glyphManager?.setMatrixFrame(frame)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { glyphManager?.turnOff() } catch (e: Exception) {}
            }, 150)
        } catch (e: Exception) {}
    }

    private fun updateNotification(trackName: String, hasLyrics: Boolean) {
        if (!isRunning) return
        val title = if (trackName.isNotEmpty()) "Live Lyrics: $trackName" else "Live Lyrics Active"
        val text = if (trackName.isNotEmpty()) {
            if (hasLyrics) "Syncing lyrics to Glyph matrix." else "No lyrics found. Running fallback visualizer."
        } else {
            "Waiting for music..."
        }
        val notification = buildNotification(title, text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String = "Live Lyrics Active", text: String = "Glyph matrix is rendering lyrics indefinitely."): Notification {
        val notificationIntent = Intent(this, LiveLyricsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Live Lyrics Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onMetadataChanged(title: String, artist: String) {
        val trackId = "$title-$artist"
        if (currentTrackId != trackId && title.isNotEmpty()) {
            currentTrackId = trackId
            currentParsedLyrics = emptyList()
            
            updateNotification(title, false) // Initial state while searching
            
            thread {
                val query = LrcQueryCleaner.clean("$title $artist")
                val results = LRCLibClient.searchLyrics(query)
                val syncedMatch = results.firstOrNull { it.syncedLyrics != null }
                if (syncedMatch != null) {
                    val parsed = LrcUtils.parseLrc(syncedMatch.syncedLyrics!!)
                    mainHandler.post {
                        if (currentTrackId == trackId) {
                            currentParsedLyrics = parsed
                            updateNotification(title, true)
                        }
                    }
                } else {
                    mainHandler.post {
                        if (currentTrackId == trackId) {
                            updateNotification(title, false)
                        }
                    }
                }
            }
        } else if (title.isEmpty()) {
            updateNotification("", false)
        }
    }

    private fun isMicStyle(style: Int): Boolean {
        return style == 2 || style == 3 || style == 5 || style == 8
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {}
}
