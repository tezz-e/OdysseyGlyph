package com.example.odysseyglyph

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlin.concurrent.thread

class LiveLyricsToyService : Service(), MusicPlaybackState.StateChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private var glyphManager: GlyphMatrixManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            // no-op, but needed for Toy
        }
    })
    
    private var isRegistered = false
    private var isBound = false
    
    private lateinit var visualizerHelper: AudioVisualizerHelper
    private var currentParsedLyrics: List<Pair<Long, String>> = emptyList()
    private var currentTrackId = ""
    private var fontStyle = GlyphFontEngine.FontStyle.SMOOTH
    
    private lateinit var prefs: SharedPreferences
    private var fallbackEnabled = true
    private var fallbackStyle = 1
    
    private val glyphCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName) {
            if (LiveLyricsService.isRunning) {
                // Do not register toy if the Tile service is already controlling the matrix
                return
            }
            glyphManager?.register(Glyph.DEVICE_23112)
            isRegistered = true
            MusicPlaybackState.addListener(this@LiveLyricsToyService)
            
            val fontStyleInt = prefs.getInt("live_font_style", 0)
            fontStyle = when (fontStyleInt) {
                1 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                2 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                else -> GlyphFontEngine.FontStyle.SMOOTH
            }
            
            // Initial fetch if active
            if (MusicPlaybackState.hasActiveSession) {
                onMetadataChanged(MusicPlaybackState.trackTitle, MusicPlaybackState.artist)
            }
            
            mainHandler.post(playbackRunnable)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isRegistered = false
            MusicPlaybackState.removeListener(this@LiveLyricsToyService)
        }
    }
    
    private var lastFrameHash = 0

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!isRegistered) return
            
            val lyricsToUse = MusicPlaybackState.manualOverrideLyrics ?: currentParsedLyrics
            
            if (!MusicPlaybackState.hasActiveSession || !MusicPlaybackState.isPlaying) {
                if (lastFrameHash != -1) {
                    glyphManager?.turnOff()
                    lastFrameHash = -1
                }
                visualizerHelper.stop()
                mainHandler.postDelayed(this, 100L) // Slow poll when inactive/unmatched
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
                        glyphManager?.setMatrixFrame(MatrixConfig.formatForHardware(frameBytes))
                    }
                    mainHandler.postDelayed(this, 16L) // ~60fps poll
                } else {
                    if (lastFrameHash != -1) {
                        glyphManager?.turnOff()
                        lastFrameHash = -1
                    }
                    visualizerHelper.stop()
                    mainHandler.postDelayed(this, 100L)
                }
                return
            }
            
            val animStyle = prefs.getInt("live_anim_style", 0)
            val syncOffset = prefs.getInt("live_sync_offset", 0)
            
            val estimatedPositionMs = if (MusicPlaybackState.isPlaying) {
                val now = SystemClock.elapsedRealtime()
                MusicPlaybackState.position + ((now - MusicPlaybackState.lastUpdateTime) * MusicPlaybackState.playbackSpeed).toLong() + syncOffset
            } else {
                MusicPlaybackState.position + syncOffset
            }
            
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
                        glyphManager?.setMatrixFrame(MatrixConfig.formatForHardware(frameBytes))
                    }
                } else {
                    if (lastFrameHash != -1) {
                        glyphManager?.turnOff()
                        lastFrameHash = -1
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
                    glyphManager?.setMatrixFrame(MatrixConfig.formatForHardware(frameBytes))
                }
            }
            
            mainHandler.postDelayed(this, 16L) // ~60fps poll
        }
    }
    
    private fun toIntArray(bytes: ByteArray): IntArray {
        val ints = IntArray(bytes.size)
        for (i in bytes.indices) {
            // Scale 8-bit (0-255) to 12-bit (0-4095) for the Glyph SDK
            ints[i] = (bytes[i].toInt() and 0xFF) * 16
        }
        return ints
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
        fallbackEnabled = prefs.getBoolean("fallback_enabled", true)
        fallbackStyle = prefs.getInt("fallback_style", 1)
        prefs.registerOnSharedPreferenceChangeListener(this)
        
        visualizerHelper = AudioVisualizerHelper(this)
        glyphManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphManager?.init(glyphCallback)
    }

    override fun onDestroy() {
        isRegistered = false
        MusicPlaybackState.removeListener(this)
        mainHandler.removeCallbacks(playbackRunnable)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        visualizerHelper.stop()
        glyphManager?.turnOff()
        glyphManager?.unInit()
        glyphManager = null
        super.onDestroy()
    }
    
    private fun isMicStyle(style: Int): Boolean {
        return style == 2 || style == 3 || style == 5 || style == 8
    }
    
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "fallback_enabled" || key == "fallback_style") {
            sharedPreferences?.let {
                fallbackEnabled = it.getBoolean("fallback_enabled", true)
                fallbackStyle = it.getInt("fallback_style", 1)
                
                // Instantly force visualizer state change
                if (fallbackEnabled && isMicStyle(fallbackStyle)) {
                    visualizerHelper.start()
                } else {
                    visualizerHelper.stop()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return messenger.binder
    }

    override fun onMetadataChanged(title: String, artist: String) {
        val trackId = "$title-$artist"
        if (currentTrackId != trackId && title.isNotEmpty()) {
            currentTrackId = trackId
            currentParsedLyrics = emptyList() // clear old
            
            thread {
                val query = LrcQueryCleaner.clean("$title $artist")
                val results = LRCLibClient.searchLyrics(query)
                val syncedMatch = results.firstOrNull { it.syncedLyrics != null }
                if (syncedMatch != null) {
                    val parsed = LrcUtils.parseLrc(syncedMatch.syncedLyrics!!)
                    mainHandler.post {
                        if (currentTrackId == trackId) {
                            currentParsedLyrics = parsed
                        }
                    }
                }
            }
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        // State changes are smoothly handled by the polling loop reading from the global state
    }
}
