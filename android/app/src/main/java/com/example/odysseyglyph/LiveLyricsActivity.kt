package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlin.concurrent.thread

class LiveLyricsActivity : AppCompatActivity(), MusicPlaybackState.StateChangeListener {

    private lateinit var prefs: SharedPreferences
    
    private lateinit var btnBack: ImageView
    private lateinit var heroTelemetryCard: FrameLayout
    private lateinit var liveMatrixMirror: GlyphMatrixMirrorView
    private lateinit var liveStatusDot: View
    
    private lateinit var switchMaster: MaterialSwitch
    private lateinit var tvMasterState: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTrackInfo: TextView
    private lateinit var ivAlbumArt: ImageView
    
    private lateinit var permissionCard: LinearLayout
    private lateinit var btnGrantPermission: MaterialButton
    
    private lateinit var toggleAnimation: LiquidGlassSegmentedBar
    private lateinit var toggleTypography: LiquidGlassSegmentedBar
    private lateinit var btnResetSync: TextView
    private lateinit var switchFallback: MaterialSwitch
    private lateinit var tvFallbackDesc: TextView
    private lateinit var btnOpenManager: MaterialButton
    
    private lateinit var optionsContainer: LinearLayout
    
    private var currentFontStyle = GlyphFontEngine.FontStyle.PIXEL_TINY
    private var isNotificationAccessGranted = false
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var frameBusSubscription: ((ByteArray) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_live_lyrics)
        
        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBarLayout, applyTop = true)
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollView)
        scrollView.isSmoothScrollingEnabled = true
        scrollView.overScrollMode = View.OVER_SCROLL_NEVER
        WindowInsetsUtil.applySystemBarsPadding(scrollView, applyBottom = true)
        
        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
        
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.finishWithPop(this@LiveLyricsActivity)
            }
        })

        heroTelemetryCard = findViewById(R.id.heroTelemetryCard)
        liveMatrixMirror = findViewById(R.id.liveMatrixMirror)
        liveStatusDot = findViewById(R.id.liveStatusDot)

        heroTelemetryCard.let {
            it.foreground = null
        }

        // Frame bus subscription for live mirror
        frameBusSubscription = { frame ->
            runOnUiThread {
                liveMatrixMirror.updateFrame(frame)
            }
        }
        frameBusSubscription?.let { GlyphFrameBus.subscribe(it) }

        // Touch spring physics
        TouchSpringAnimator.attach(btnBack)
        
        switchMaster = findViewById(R.id.switchMaster)
        tvMasterState = findViewById(R.id.tvMasterState)
        tvStatus = findViewById(R.id.tvStatus)
        tvTrackInfo = findViewById(R.id.tvTrackInfo)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        
        permissionCard = findViewById(R.id.permissionCard)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        
        toggleAnimation = findViewById(R.id.toggleAnimation)
        toggleTypography = findViewById(R.id.toggleTypography)
        btnResetSync = findViewById(R.id.btnResetSync)
        switchFallback = findViewById(R.id.switchFallback)
        tvFallbackDesc = findViewById(R.id.tvFallbackDesc)
        btnOpenManager = findViewById(R.id.btnOpenManager)
        
        val btnChooseMatch: TextView? = findViewById(R.id.btnChooseMatch)
        val btnViewSavedLyrics: TextView? = findViewById(R.id.btnViewSavedLyrics)

        btnChooseMatch?.setOnClickListener {
            val pickerIntent = Intent(this, TrackMatchPickerActivity::class.java).apply {
                putExtra("track_title", MusicPlaybackState.trackTitle)
                putExtra("artist", MusicPlaybackState.artist)
                putExtra("album", MusicPlaybackState.album)
                putExtra("duration_ms", MusicPlaybackState.durationMs)
                putExtra("package_name", MusicPlaybackState.packageName)
            }
            NavigationHelper.startWithPush(this, pickerIntent)
        }

        btnViewSavedLyrics?.setOnClickListener {
            NavigationHelper.startWithPush(this, Intent(this, SavedLyricsActivity::class.java))
        }

        btnChooseMatch?.let { TouchSpringAnimator.attach(it) }
        btnViewSavedLyrics?.let { TouchSpringAnimator.attach(it) }

        TouchSpringAnimator.attach(btnGrantPermission)
        TouchSpringAnimator.attach(btnOpenManager)
        TouchSpringAnimator.attach(btnResetSync)
        findViewById<View>(R.id.btnCustomizeFallback)?.let { TouchSpringAnimator.attach(it) }
        
        optionsContainer = findViewById(R.id.optionsContainer)
        
        switchMaster.isChecked = prefs.getBoolean("live_lyrics_enabled", false)
        optionsContainer.visibility = if (switchMaster.isChecked) View.VISIBLE else View.GONE
        
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("live_lyrics_enabled", isChecked).apply()
            optionsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateUIState()
            if (isChecked) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
                    }
                }
                prefs.edit().putString("last_used_service", "live_lyrics").apply()
                val stopVisualizer = Intent(this, VisualizerService::class.java).apply { action = "STOP_VISUALIZER" }
                startService(stopVisualizer)
                
                val startLyrics = Intent(this, LiveLyricsService::class.java).apply { action = "START_LIVE_LYRICS" }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(startLyrics)
                } else {
                    startService(startLyrics)
                }
            } else {
                liveMatrixMirror.clearFrame()
                GlyphFrameBus.postFrame(ByteArray(625))
                val stopLyrics = Intent(this, LiveLyricsService::class.java).apply { action = "STOP_LIVE_LYRICS" }
                startService(stopLyrics)
            }
        }
        
        val savedAnimStyle = prefs.getInt("live_anim_style", 0)
        val initialAnimIndex = when (savedAnimStyle) {
            1 -> 2 // Scroll
            2 -> 1 // Hybrid
            else -> 0 // Flash
        }
        toggleAnimation.setSegments(listOf("Flash", "Hybrid", "Scroll"), initialIndex = initialAnimIndex)
        toggleAnimation.onSegmentSelected = { index, _ ->
            val style = when (index) {
                1 -> 2 // Hybrid
                2 -> 1 // Scroll
                else -> 0 // Flash
            }
            prefs.edit().putInt("live_anim_style", style).apply()
            triggerMatrixStylePreview("STYLE", currentFontStyle, if (style == 0) 0 else 1)
        }
        
        val tvSyncOffset: TextView = findViewById(R.id.tvSyncOffset)
        val sliderSync: com.google.android.material.slider.Slider = findViewById(R.id.sliderSync)
        sliderSync.labelBehavior = com.google.android.material.slider.LabelFormatter.LABEL_GONE
        
        val restRadius = (10f * resources.displayMetrics.density).toInt()
        val liftedRadius = (16f * resources.displayMetrics.density).toInt() // Dynamic expansion (GlassSlider pattern)
        sliderSync.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                slider.vibrateTick()
                android.animation.ValueAnimator.ofInt(slider.thumbRadius, liftedRadius).apply {
                    duration = 180L
                    interpolator = android.view.animation.OvershootInterpolator(2.2f)
                    addUpdateListener { slider.thumbRadius = it.animatedValue as Int }
                    start()
                }
            }

            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                slider.vibrateTick()
                android.animation.ValueAnimator.ofInt(slider.thumbRadius, restRadius).apply {
                    duration = 260L
                    interpolator = android.view.animation.OvershootInterpolator(1.8f)
                    addUpdateListener { slider.thumbRadius = it.animatedValue as Int }
                    start()
                }
            }
        })
        
        val savedSyncOffset = prefs.getInt("live_sync_offset", 0)
        sliderSync.value = savedSyncOffset.toFloat()
        tvSyncOffset.text = when {
            savedSyncOffset > 0 -> "+${savedSyncOffset}ms (Delay)"
            savedSyncOffset < 0 -> "${savedSyncOffset}ms (Early)"
            else -> "0ms (Exact)"
        }
        btnResetSync.visibility = if (savedSyncOffset != 0) View.VISIBLE else View.GONE
        
        btnResetSync.setOnClickListener {
            sliderSync.value = 0f
            tvSyncOffset.text = "0ms (Exact)"
            btnResetSync.visibility = View.GONE
            prefs.edit().putInt("live_sync_offset", 0).apply()
            it.vibrateTick()
        }
        
        sliderSync.addOnChangeListener { slider, value, fromUser ->
            val offsetMs = value.toInt()
            tvSyncOffset.text = when {
                offsetMs > 0 -> "+${offsetMs}ms (Delay)"
                offsetMs < 0 -> "${offsetMs}ms (Early)"
                else -> "0ms (Exact)"
            }
            btnResetSync.visibility = if (offsetMs != 0) View.VISIBLE else View.GONE
            prefs.edit().putInt("live_sync_offset", offsetMs).apply()
            if (fromUser) {
                slider.vibrateTick()
            }
        }
        
        val savedFontStyle = prefs.getInt("live_font_style", 0)
        currentFontStyle = when (savedFontStyle) {
            0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
            1 -> GlyphFontEngine.FontStyle.SMOOTH
            2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
            else -> GlyphFontEngine.FontStyle.PIXEL_TINY
        }
        
        val initialFontIndex = when (currentFontStyle) {
            GlyphFontEngine.FontStyle.PIXEL_TINY -> 0
            GlyphFontEngine.FontStyle.SMOOTH -> 1
            GlyphFontEngine.FontStyle.BLOCK_BOLD -> 2
        }
        toggleTypography.setSegments(listOf("Pixel", "Smooth", "Blocky"), initialIndex = initialFontIndex)
        toggleTypography.onSegmentSelected = { index, _ ->
            currentFontStyle = when (index) {
                0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                1 -> GlyphFontEngine.FontStyle.SMOOTH
                2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                else -> GlyphFontEngine.FontStyle.PIXEL_TINY
            }
            val styleInt = when (currentFontStyle) {
                GlyphFontEngine.FontStyle.PIXEL_TINY -> 0
                GlyphFontEngine.FontStyle.SMOOTH -> 1
                GlyphFontEngine.FontStyle.BLOCK_BOLD -> 2
            }
            prefs.edit().putInt("live_font_style", styleInt).apply()

            // Transmit immediate style change to running LiveLyricsService (no restart needed)
            val updateIntent = Intent(this, LiveLyricsService::class.java).apply {
                action = "UPDATE_STYLE"
                putExtra("font_style", styleInt)
            }
            try {
                startService(updateIntent)
            } catch (_: Exception) {}

            val fontLabel = when (currentFontStyle) {
                GlyphFontEngine.FontStyle.PIXEL_TINY -> "PIXEL"
                GlyphFontEngine.FontStyle.SMOOTH -> "SMOOTH"
                GlyphFontEngine.FontStyle.BLOCK_BOLD -> "BLOCK"
            }
            val currentAnimPref = prefs.getInt("live_anim_style", 0)
            triggerMatrixStylePreview(fontLabel, currentFontStyle, if (currentAnimPref == 0) 0 else 1)
        }
        
        switchFallback.isChecked = prefs.getBoolean("fallback_enabled", true)
        val fallbackEnabled = switchFallback.isChecked
        
        val fallbackVisibility = if (fallbackEnabled) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnCustomizeFallback).visibility = fallbackVisibility
        tvFallbackDesc.visibility = fallbackVisibility
        
        switchFallback.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("fallback_enabled", isChecked).apply()
            val vis = if (isChecked) View.VISIBLE else View.GONE
            findViewById<View>(R.id.btnCustomizeFallback).visibility = vis
            tvFallbackDesc.visibility = vis
            
            if (isChecked) {
                // If it's turning on and a mic option is selected, check permission
                val style = prefs.getInt("fallback_style", 0)
                if (style == 2 || style == 3 || style == 5 || style == 8) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
                        }
                    }
                }
            }
        }
        
        findViewById<View>(R.id.btnCustomizeFallback).setOnClickListener {
            val bottomSheet = FallbackStyleBottomSheet()
            bottomSheet.setOnStyleSelectedListener { style ->
                if (style == 2 || style == 3 || style == 5 || style == 8) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
                        }
                    }
                }
            }
            bottomSheet.show(supportFragmentManager, "FallbackStyleBottomSheet")
        }
        
        btnGrantPermission.setOnClickListener {
            ProminentDisclosureDialog.showIfRequired(this) {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {}
            }
        }
        
        btnOpenManager.setOnClickListener {
            GlyphOSIntegration.openGlyphAppOrToys(this, findViewById(android.R.id.content))
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
        MusicPlaybackState.addListener(this)
        
        // Request active session validation from listener service
        try {
            startService(Intent(this, MediaSessionListenerService::class.java).apply {
                action = "REFRESH_SESSIONS"
            })
        } catch (_: Exception) {}

        updateUIState()
        
        if (MusicPlaybackState.hasActiveSession && 
            MusicPlaybackState.trackTitle.isNotEmpty() && 
            MusicPlaybackState.manualOverrideLyrics == null) {
            onMetadataChanged(MusicPlaybackState.trackTitle, MusicPlaybackState.artist)
        }
        
    }

    override fun onPause() {
        super.onPause()
        MusicPlaybackState.removeListener(this)
    }

    private var previewAnimator: ValueAnimator? = null

    override fun onDestroy() {
        super.onDestroy()
        previewAnimator?.cancel()
        frameBusSubscription?.let { GlyphFrameBus.unsubscribe(it) }
    }

    private fun triggerMatrixStylePreview(label: String, style: GlyphFontEngine.FontStyle, animMode: Int) {
        if (MusicPlaybackState.hasActiveSession && switchMaster.isChecked) return
        previewAnimator?.cancel()
        val textWidth = GlyphFontEngine.measureTextWidth(label, style, autoScale = false)
        if (animMode == 0) { // Flash
            val centeredX = (25f - textWidth) / 2f
            val frame = GlyphFontEngine.renderTextFrame(label, style, centeredX, autoScale = false)
            liveMatrixMirror.updateFrame(frame)
        } else { // Scroll / Hybrid
            val startX = 25f
            val endX = -textWidth
            previewAnimator = ValueAnimator.ofFloat(startX, endX).apply {
                duration = 1100L
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    val curX = va.animatedValue as Float
                    val frame = GlyphFontEngine.renderTextFrame(label, style, curX, autoScale = false)
                    liveMatrixMirror.updateFrame(frame)
                }
                start()
            }
        }
    }

    private fun checkNotificationPermission() {
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        isNotificationAccessGranted = enabledListeners?.contains(componentName.flattenToString()) == true
        
        if (isNotificationAccessGranted) {
            permissionCard.visibility = View.GONE
        } else {
            permissionCard.visibility = View.VISIBLE
        }
    }

    private fun updateUIState() {
        val isMasterOn = switchMaster.isChecked
        val btnChooseMatch: View? = findViewById(R.id.btnChooseMatch)
        if (!isMasterOn) {
            tvMasterState.text = "OFFLINE"
            tvMasterState.setTextColor(Color.parseColor("#888888"))
            tvStatus.text = "Live Lyrics Off"
            tvTrackInfo.text = "Turn on the switch above to start streaming lyrics."
            liveStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#444444"))
            ivAlbumArt.visibility = View.GONE
            btnChooseMatch?.visibility = View.GONE
            liveMatrixMirror.clearFrame()
            return
        }

        if (!isNotificationAccessGranted) {
            tvMasterState.text = "PERMISSION REQUIRED"
            tvMasterState.setTextColor(Color.parseColor("#EA3323"))
            tvStatus.text = "Notification Access Required"
            tvTrackInfo.text = "Enable Notification Access below to read media playback."
            liveStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA3323"))
            ivAlbumArt.visibility = View.GONE
            btnChooseMatch?.visibility = View.GONE
            liveMatrixMirror.clearFrame()
            return
        }
        
        if (!MusicPlaybackState.hasActiveSession || !MusicPlaybackState.isPlaying) {
            val hasSession = MusicPlaybackState.hasActiveSession && MusicPlaybackState.trackTitle.isNotEmpty()
            tvMasterState.text = if (hasSession) "PAUSED" else "STANDBY"
            tvMasterState.setTextColor(Color.parseColor("#888888"))
            tvStatus.text = if (hasSession) "Playback Paused" else "Waiting for music"
            tvTrackInfo.text = if (hasSession)
                "${MusicPlaybackState.trackTitle}: ${MusicPlaybackState.artist}"
            else
                "Play a track to begin."
            liveStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#888888"))
            if (MusicPlaybackState.albumArt != null && hasSession) {
                ivAlbumArt.setImageBitmap(MusicPlaybackState.albumArt)
                ivAlbumArt.visibility = View.VISIBLE
            } else {
                ivAlbumArt.visibility = View.GONE
            }
            btnChooseMatch?.visibility = if (hasSession) View.VISIBLE else View.GONE
            liveMatrixMirror.clearFrame()
        } else {
            val title = MusicPlaybackState.trackTitle
            val artist = MusicPlaybackState.artist
            tvMasterState.text = "STREAMING"
            tvMasterState.setTextColor(Color.parseColor("#EA3323"))
            liveStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA3323"))
            btnChooseMatch?.visibility = View.VISIBLE
            
            if (MusicPlaybackState.albumArt != null) {
                ivAlbumArt.setImageBitmap(MusicPlaybackState.albumArt)
                ivAlbumArt.visibility = View.VISIBLE
            } else {
                ivAlbumArt.visibility = View.GONE
            }
            
            if (MusicPlaybackState.manualOverrideLyrics != null) {
                val overrideName = MusicPlaybackState.manualOverrideTrackName.ifEmpty { title }
                tvStatus.text = "Streaming to rear matrix"
                tvTrackInfo.text = "$overrideName: $artist\n(Using saved lyrics)"
            } else if (title.isNotEmpty()) {
                tvStatus.text = "Streaming to rear matrix"
                tvTrackInfo.text = "$title: $artist\n(Resolving lyrics...)"
            } else {
                tvStatus.text = "Streaming to rear matrix"
                tvTrackInfo.text = "Active session connected."
            }
        }
    }

    override fun onMetadataChanged(title: String, artist: String) {
        onTrackIdentityChanged(MusicPlaybackState.currentIdentity())
    }

    override fun onTrackIdentityChanged(identity: MediaTrackIdentity) {
        mainHandler.post { updateUIState() }
        
        if (identity.title.isNotEmpty()) {
            val repo = SavedLyricsRepository.getInstance(this)
            val saved = repo.findMatch(identity)
            if (saved != null) {
                val parsed = LrcUtils.parseLrc(saved.syncedLyrics)
                if (parsed.isNotEmpty()) {
                    MusicPlaybackState.manualOverrideLyrics = parsed
                    MusicPlaybackState.manualOverrideTrackName = saved.matchedTrackName
                    mainHandler.post {
                        if (isFinishing || isDestroyed) return@post
                        updateUIState()
                    }
                    return
                }
            }

            thread {
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
                        MusicPlaybackState.manualOverrideLyrics = parsed
                        MusicPlaybackState.manualOverrideTrackName = exact.trackName
                        mainHandler.post {
                            if (isFinishing || isDestroyed) return@post
                            updateUIState()
                        }
                        return@thread
                    }
                }

                var candidates = LRCLibClient.searchCandidates(identity.cleanTitle(), identity.primaryArtist())
                if (candidates.isEmpty()) {
                    candidates = LRCLibClient.searchLyrics("${identity.cleanTitle()} ${identity.primaryArtist()}".trim())
                }
                val resolution = LyricMatchScorer.scoreCandidates(identity, candidates)
                if (resolution.status == LyricConfidenceLevel.HIGH_CONFIDENCE) {
                    val top = resolution.topMatch!!.candidate
                    val parsed = LrcUtils.parseLrc(top.syncedLyrics!!)
                    repo.saveAutoVerifiedMatch(identity, top, top.syncedLyrics, resolution.topMatch.totalScore)
                    MusicPlaybackState.manualOverrideLyrics = parsed
                    MusicPlaybackState.manualOverrideTrackName = top.trackName
                    mainHandler.post {
                        if (isFinishing || isDestroyed) return@post
                        updateUIState()
                    }
                } else {
                    MusicPlaybackState.manualOverrideLyrics = null
                    mainHandler.post {
                        if (isFinishing || isDestroyed) return@post
                        val note = if (resolution.status == LyricConfidenceLevel.AMBIGUOUS) {
                            "(Ambiguous match — tap Match / Fix to choose)"
                        } else {
                            "(No synced lyrics found — tap Match / Fix)"
                        }
                        tvTrackInfo.text = "${identity.title} — ${identity.artist}\n$note"
                    }
                }
            }
        }
    }





    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        mainHandler.post { updateUIState() }
    }

    private fun showOnboardingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nothing_onboarding, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "LIVE LYRICS"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = 
            "Project synced lyrics directly onto your Glyph matrix!\n\n" +
            "HOW DO YOU WANT TO RUN IT?\n\n" +
            "1. INDEFINITE BACKGROUND SERVICE\n" +
            "Toggle the master switch above. It runs infinitely in the background and can be quickly turned on/off using the 'Odyssey Glyph' Quick Settings Tile.\n\n" +
            "2. NATIVE GLYPH TOY\n" +
            "Tap 'TOYS MANAGER' to add it to your Nothing OS settings. You can launch it using the physical Glyph button on the back of your phone. It will play for a short time and automatically go to sleep."
        dialogView.findViewById<MaterialButton>(R.id.btnDialogAction).text = "GOT IT"
        dialogView.findViewById<MaterialButton>(R.id.btnDialogAction).setOnClickListener {
            prefs.edit().putBoolean("first_run_live_v2", false).apply()
            dialog.dismiss()
        }
        dialog.show()
    }
}
