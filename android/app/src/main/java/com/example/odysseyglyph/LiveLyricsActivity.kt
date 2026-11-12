package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private lateinit var oscilloscopeView: OscilloscopeReticleView
    
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
    private lateinit var btnAddTile: MaterialButton
    
    private lateinit var optionsContainer: LinearLayout
    
    private var currentFontStyle = GlyphFontEngine.FontStyle.SMOOTH
    private var isNotificationAccessGranted = false
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var frameBusSubscription: ((ByteArray) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_live_lyrics)
        
        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBarLayout, applyTop = true)
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        val navDockContainer = findViewById<View>(R.id.navDockContainer)
        WindowInsetsUtil.applySystemBarsPadding(navDockContainer, applyBottom = true)
        WindowInsetsUtil.applySystemBarsPadding(scrollView, applyBottom = true, overlayView = navDockContainer)

        findViewById<LiquidGlassSegmentedBar>(R.id.globalNavDock)?.let { navBar ->
            OdysseyNavDockHelper.attach(this, navBar, 0)
        }
        
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
        oscilloscopeView = findViewById(R.id.oscilloscopeView)

        heroTelemetryCard.let {
            it.foreground = null
        }

        // Frame bus subscription for live mirror
        frameBusSubscription = { frame ->
            runOnUiThread {
                liveMatrixMirror.updateFrame(frame)
                oscilloscopeView.updateWaveform(0.7f, 1.4f)
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
        btnAddTile = findViewById(R.id.btnAddTile)
        
        btnGrantPermission.applyLiquidGlass(pressedScale = 0.96f)
        btnOpenManager.applyLiquidGlass(pressedScale = 0.96f)
        btnAddTile.applyLiquidGlass(pressedScale = 0.96f)
        heroTelemetryCard.applyLiquidGlass(pressedScale = 0.98f)
        findViewById<View>(R.id.btnCustomizeFallback).applyLiquidGlass(pressedScale = 0.96f)
        
        optionsContainer = findViewById(R.id.optionsContainer)
        
        switchMaster.isChecked = prefs.getBoolean("live_lyrics_enabled", false)
        optionsContainer.visibility = if (switchMaster.isChecked) View.VISIBLE else View.GONE
        
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("live_lyrics_enabled", isChecked).apply()
            optionsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateUIState()
            if (isChecked) {
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
        val liftedRadius = (15f * resources.displayMetrics.density).toInt() // 1.5x expansion (GlassSlider pattern)
        sliderSync.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                slider.vibrateTick()
                android.animation.ValueAnimator.ofInt(slider.thumbRadius, liftedRadius).apply {
                    duration = 160L
                    interpolator = android.view.animation.OvershootInterpolator(1.4f)
                    addUpdateListener { slider.thumbRadius = it.animatedValue as Int }
                    start()
                }
            }

            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                android.animation.ValueAnimator.ofInt(slider.thumbRadius, restRadius).apply {
                    duration = 200L
                    interpolator = android.view.animation.DecelerateInterpolator()
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
        
        btnResetSync.setOnClickListener {
            sliderSync.value = 0f
            tvSyncOffset.text = "0ms (Exact)"
            prefs.edit().putInt("live_sync_offset", 0).apply()
            it.vibrateTick()
        }
        
        sliderSync.addOnChangeListener { _, value, _ ->
            val offsetMs = value.toInt()
            tvSyncOffset.text = when {
                offsetMs > 0 -> "+${offsetMs}ms (Delay)"
                offsetMs < 0 -> "${offsetMs}ms (Early)"
                else -> "0ms (Exact)"
            }
            prefs.edit().putInt("live_sync_offset", offsetMs).apply()
        }
        
        val savedFontStyle = prefs.getInt("live_font_style", 0)
        currentFontStyle = when (savedFontStyle) {
            1 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
            2 -> GlyphFontEngine.FontStyle.PIXEL_TINY
            else -> GlyphFontEngine.FontStyle.SMOOTH
        }
        
        val initialFontIndex = when (currentFontStyle) {
            GlyphFontEngine.FontStyle.BLOCK_BOLD -> 1
            GlyphFontEngine.FontStyle.PIXEL_TINY -> 2
            else -> 0
        }
        toggleTypography.setSegments(listOf("Smooth", "Blocky", "Pixel"), initialIndex = initialFontIndex)
        toggleTypography.onSegmentSelected = { index, _ ->
            currentFontStyle = when (index) {
                1 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                2 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                else -> GlyphFontEngine.FontStyle.SMOOTH
            }
            val styleInt = when (currentFontStyle) {
                GlyphFontEngine.FontStyle.BLOCK_BOLD -> 1
                GlyphFontEngine.FontStyle.PIXEL_TINY -> 2
                else -> 0
            }
            prefs.edit().putInt("live_font_style", styleInt).apply()
            val fontLabel = when (currentFontStyle) {
                GlyphFontEngine.FontStyle.BLOCK_BOLD -> "BLOCK"
                GlyphFontEngine.FontStyle.PIXEL_TINY -> "PIXEL"
                else -> "SMOOTH"
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
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        
        btnOpenManager.setOnClickListener {
            GlyphOSIntegration.openGlyphAppOrToys(this, findViewById(android.R.id.content))
        }
        
        btnAddTile.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val statusBarManager = getSystemService(android.app.StatusBarManager::class.java)
                val componentName = ComponentName(this, LiveLyricsTileService::class.java)
                statusBarManager?.requestAddTileService(
                    componentName,
                    "Live Lyrics",
                    android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher),
                    mainExecutor,
                    { result ->
                        if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || 
                            result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                            prefs.edit().putBoolean("tile_added", true).apply()
                            findViewById<View>(R.id.layoutAlwaysOnMode).visibility = View.GONE
                        } else {
                            Snackbar.make(findViewById(android.R.id.content), "Failed to add tile.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
                        }
                    }
                )
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Pull down your notification shade and edit tiles to add 'Live Lyrics'.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
            }
        }
        
        if (prefs.getBoolean("first_run_live_v2", true)) {
            showOnboardingDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
        MusicPlaybackState.addListener(this)
        updateUIState()
        findViewById<LiquidGlassSegmentedBar>(R.id.globalNavDock)?.selectSegment(0, notify = false)
        
        val isTileAdded = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE).getBoolean("tile_added", false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            findViewById<View>(R.id.layoutAlwaysOnMode).visibility = if (isTileAdded) View.GONE else View.VISIBLE
        }
        
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
            oscilloscopeView.updateWaveform(0.5f, 1.2f)
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
                    oscilloscopeView.updateWaveform(0.6f, 1.4f)
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
        if (!isMasterOn) {
            tvMasterState.text = "OFFLINE"
            tvMasterState.setTextColor(Color.parseColor("#888888"))
            tvStatus.text = "Live Lyrics Off"
            tvTrackInfo.text = "Turn on the switch above to start streaming lyrics."
            oscilloscopeView.updateWaveform(0f, 0f)
            ivAlbumArt.visibility = View.GONE
            return
        }

        if (!isNotificationAccessGranted) {
            tvMasterState.text = "PERMISSION REQUIRED"
            tvMasterState.setTextColor(Color.parseColor("#EA3323"))
            tvStatus.text = "Notification Access Required"
            tvTrackInfo.text = "Enable Notification Access below to read media playback."
            ivAlbumArt.visibility = View.GONE
            return
        }
        
        if (!MusicPlaybackState.hasActiveSession) {
            tvMasterState.text = "READY"
            tvMasterState.setTextColor(Color.parseColor("#44BB44"))
            tvStatus.text = "Waiting for music"
            tvTrackInfo.text = "Play a track to begin."
            ivAlbumArt.visibility = View.GONE
        } else {
            val title = MusicPlaybackState.trackTitle
            val artist = MusicPlaybackState.artist
            tvMasterState.text = "STREAMING"
            tvMasterState.setTextColor(Color.parseColor("#44BB44"))
            
            if (MusicPlaybackState.albumArt != null) {
                ivAlbumArt.setImageBitmap(MusicPlaybackState.albumArt)
                ivAlbumArt.visibility = View.VISIBLE
            } else {
                ivAlbumArt.visibility = View.GONE
            }
            
            if (MusicPlaybackState.manualOverrideLyrics != null) {
                tvStatus.text = "Streaming to rear matrix"
                tvTrackInfo.text = "$title — $artist"
            } else if (title.isNotEmpty()) {
                tvStatus.text = "Streaming to rear matrix"
                tvTrackInfo.text = "$title — $artist\n(Searching for lyrics...)"
            } else {
                tvStatus.text = "Streaming to rear matrix"
                tvTrackInfo.text = "Active session connected."
            }
        }
    }

    override fun onMetadataChanged(title: String, artist: String) {
        mainHandler.post { updateUIState() }
        
        if (title.isNotEmpty()) {
            thread {
                val query = LrcQueryCleaner.clean("$title $artist")
                val results = LRCLibClient.searchLyrics(query)
                val syncedResult = results.firstOrNull { it.syncedLyrics != null }
                if (syncedResult != null) {
                    val parsed = LrcUtils.parseLrc(syncedResult.syncedLyrics!!)
                    MusicPlaybackState.manualOverrideLyrics = parsed
                    MusicPlaybackState.manualOverrideTrackName = syncedResult.trackName
                    mainHandler.post {
                        if (isFinishing || isDestroyed) return@post
                        updateUIState()
                    }
                } else {
                    MusicPlaybackState.manualOverrideLyrics = null
                    mainHandler.post { 
                        if (isFinishing || isDestroyed) return@post
                        if (MusicPlaybackState.trackTitle == title) {
                            tvTrackInfo.text = "$title — $artist\n(No synced lyrics found)"
                        }
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
