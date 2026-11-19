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
    private var fontStyle = GlyphFontEngine.FontStyle.PIXEL_TINY
    
    private lateinit var prefs: SharedPreferences
    private lateinit var repo: SavedLyricsRepository
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
                0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                1 -> GlyphFontEngine.FontStyle.SMOOTH
                2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                else -> GlyphFontEngine.FontStyle.PIXEL_TINY
            }
            
            if (MusicPlaybackState.hasActiveSession) {
                onTrackIdentityChanged(MusicPlaybackState.currentIdentity())
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
                        GlyphFrameBus.postFrame(frameBytes)
                        try { glyphManager?.setMatrixFrame(toIntArray(frameBytes)) } catch (e: Exception) {}
                    }
                    mainHandler.postDelayed(this, 16L)
                } else {
                    if (lastFrameHash != -1) {
                        try { 
                            glyphManager?.turnOff() 
                            lastFrameHash = -1
                            GlyphFrameBus.clear()
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
                        GlyphFrameBus.postFrame(frameBytes)
                        try { glyphManager?.setMatrixFrame(toIntArray(frameBytes)) } catch (e: Exception) {}
                    }
                } else {
                    if (lastFrameHash != -1) {
                        try { 
                            glyphManager?.turnOff() 
                            lastFrameHash = -1
                            GlyphFrameBus.clear()
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
                    GlyphFrameBus.postFrame(frameBytes)
                    try { glyphManager?.setMatrixFrame(toIntArray(frameBytes)) } catch (e: Exception) {}
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
        repo = SavedLyricsRepository.getInstance(this)
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
            "live_font_style" -> {
                val fontStyleInt = sharedPreferences?.getInt("live_font_style", 0) ?: 0
                fontStyle = when (fontStyleInt) {
                    0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                    1 -> GlyphFontEngine.FontStyle.SMOOTH
                    2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                    else -> GlyphFontEngine.FontStyle.PIXEL_TINY
                }
            }
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
                        onTrackIdentityChanged(MusicPlaybackState.currentIdentity())
                    }
                    
                    // Force a fresh hardware bind — onServiceConnected will start the playback loop
                    isRegistered = false
                    mainHandler.removeCallbacks(playbackRunnable)
                    try { glyphManager?.unInit() } catch (e: Exception) {}
                    glyphManager?.init(glyphCallback)
                    
                    LiveLyricsTileService.requestTileUpdate(this)
                    OdysseyWidgetProvider.updateAllWidgets(this)
                }
                "REFRESH_MATCH" -> {
                    if (MusicPlaybackState.hasActiveSession) {
                        currentTrackId = ""
                        onTrackIdentityChanged(MusicPlaybackState.currentIdentity())
                    }
                }
                "STOP_LIVE_LYRICS" -> {
                    getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE).edit().putBoolean("live_lyrics_enabled", false).apply()
                    isRunning = false
                    isRegistered = false
                    mainHandler.removeCallbacks(playbackRunnable)
                    visualizerHelper.stop()
                    try { glyphManager?.turnOff() } catch (e: Exception) {}
                    GlyphFrameBus.postFrame(ByteArray(625))
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
                "UPDATE_STYLE" -> {
                    val fontStyleInt = intent.getIntExtra("font_style", getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE).getInt("live_font_style", 0))
                    fontStyle = when (fontStyleInt) {
                        0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                        1 -> GlyphFontEngine.FontStyle.SMOOTH
                        2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                        else -> GlyphFontEngine.FontStyle.PIXEL_TINY
                    }
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
            try { glyphManager?.unInit() } catch (_: Exception) {}
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

    private fun updateNotification(
        title: String,
        text: String,
        openPicker: Boolean = false
    ) {
        if (!isRunning) return
        val notification = buildNotification(title, text, openPicker)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        title: String = "Live Lyrics Active",
        text: String = "Glyph matrix is rendering lyrics indefinitely.",
        openPicker: Boolean = false
    ): Notification {
        val targetIntent = if (openPicker) {
            Intent(this, TrackMatchPickerActivity::class.java).apply {
                putExtra("track_title", MusicPlaybackState.trackTitle)
                putExtra("artist", MusicPlaybackState.artist)
                putExtra("album", MusicPlaybackState.album)
                putExtra("duration_ms", MusicPlaybackState.durationMs)
                putExtra("package_name", MusicPlaybackState.packageName)
            }
        } else {
            Intent(this, LiveLyricsActivity::class.java)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)

        if (openPicker) {
            val pickerAction = NotificationCompat.Action.Builder(
                R.drawable.ic_lyrics,
                "Choose Match",
                pendingIntent
            ).build()
            builder.addAction(pickerAction)
        }

        return builder.build()
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
        onTrackIdentityChanged(MusicPlaybackState.currentIdentity())
    }

    override fun onTrackIdentityChanged(identity: MediaTrackIdentity) {
        if (identity.isEmpty()) {
            currentTrackId = ""
            currentParsedLyrics = emptyList()
            updateNotification("Live Lyrics Active", "Waiting for music...", openPicker = false)
            return
        }

        val trackId = "${identity.title}-${identity.artist}"
        if (currentTrackId == trackId && currentParsedLyrics.isNotEmpty()) {
            return
        }
        currentTrackId = trackId

        // Priority 1: User manual override for this session
        val overrideLyrics = MusicPlaybackState.manualOverrideLyrics
        if (overrideLyrics != null && overrideLyrics.isNotEmpty()) {
            currentParsedLyrics = overrideLyrics
            val overrideName = MusicPlaybackState.manualOverrideTrackName.ifEmpty { identity.title }
            updateNotification("$overrideName — ${identity.artist}", "Using your saved lyrics", openPicker = false)
            return
        }

        // Priority 2: Persistent Saved Lyrics / My Matches (User-selected or verified)
        val saved = repo.findMatch(identity)
        if (saved != null) {
            val parsed = LrcUtils.parseLrc(saved.syncedLyrics)
            if (parsed.isNotEmpty()) {
                currentParsedLyrics = parsed
                val statusText = if (saved.isUserChoice) "Using your saved lyrics" else "Lyrics syncing to Glyph"
                updateNotification("${identity.title} — ${identity.artist}", statusText, openPicker = false)
                return
            }
        }

        // Priority 3: Online Resolver with Strict Confidence Ranking
        currentParsedLyrics = emptyList() // Keep fallback visualizer active while resolving
        updateNotification("${identity.title} — ${identity.artist}", "Resolving lyrics...", openPicker = false)

        thread {
            // Step 3a: Exact get lookup
            val exact = LRCLibClient.getExactLyrics(
                trackName = identity.cleanTitle(),
                artistName = identity.primaryArtist(),
                albumName = identity.album.ifEmpty { null },
                durationSeconds = if (identity.durationMs > 0) (identity.durationMs / 1000).toInt() else null
            )

            if (exact != null && !exact.syncedLyrics.isNullOrBlank()) {
                val parsed = LrcUtils.parseLrc(exact.syncedLyrics)
                if (parsed.isNotEmpty()) {
                    repo.saveAutoVerifiedMatch(identity, exact, exact.syncedLyrics, 95)
                    mainHandler.post {
                        if (currentTrackId == trackId) {
                            currentParsedLyrics = parsed
                            updateNotification("${identity.title} — ${identity.artist}", "Lyrics syncing to Glyph", openPicker = false)
                        }
                    }
                    return@thread
                }
            }

            // Step 3b: Candidate search & scoring
            var candidates = LRCLibClient.searchCandidates(identity.cleanTitle(), identity.primaryArtist())
            if (candidates.isEmpty()) {
                candidates = LRCLibClient.searchLyrics("${identity.cleanTitle()} ${identity.primaryArtist()}".trim())
            }

            val resolution = LyricMatchScorer.scoreCandidates(identity, candidates)

            mainHandler.post {
                if (currentTrackId != trackId) return@post
                when (resolution.status) {
                    LyricConfidenceLevel.HIGH_CONFIDENCE -> {
                        val top = resolution.topMatch!!.candidate
                        val parsed = LrcUtils.parseLrc(top.syncedLyrics!!)
                        currentParsedLyrics = parsed
                        repo.saveAutoVerifiedMatch(identity, top, top.syncedLyrics, resolution.topMatch.totalScore)
                        updateNotification("${identity.title} — ${identity.artist}", "Lyrics syncing to Glyph", openPicker = false)
                    }
                    LyricConfidenceLevel.AMBIGUOUS -> {
                        // High-value rule: Never show wrong lyrics. Fallback stays active.
                        currentParsedLyrics = emptyList()
                        updateNotification("Lyrics need confirmation", "Tap to choose correct match for ${identity.title}", openPicker = true)
                    }
                    LyricConfidenceLevel.NO_MATCH -> {
                        // Fallback stays active
                        currentParsedLyrics = emptyList()
                        updateNotification("No synced lyrics found", "Tap to choose lyrics or import .lrc", openPicker = true)
                    }
                }
            }
        }
    }

    private fun isMicStyle(style: Int): Boolean {
        return style == 2 || style == 3 || style == 5 || style == 8
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {}
}
