package com.example.odysseyglyph

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
import android.widget.*
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
    
    private lateinit var switchMaster: MaterialSwitch
    private lateinit var tvStatus: TextView
    private lateinit var tvTrackInfo: TextView
    private lateinit var ivAlbumArt: ImageView
    
    private lateinit var permissionCard: LinearLayout
    private lateinit var btnGrantPermission: MaterialButton
    
    private lateinit var toggleAnimation: MaterialButtonToggleGroup
    private lateinit var toggleTypography: MaterialButtonToggleGroup
    private lateinit var switchFallback: MaterialSwitch
    private lateinit var tvFallbackDesc: TextView
    private lateinit var btnOpenManager: MaterialButton
    private lateinit var btnAddTile: MaterialButton
    
    private lateinit var optionsContainer: LinearLayout
    
    private var currentFontStyle = GlyphFontEngine.FontStyle.SMOOTH
    private var isNotificationAccessGranted = false
    
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_live_lyrics)
        
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }
        
        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        
        toolbar.inflateMenu(R.menu.menu_info)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_info) {
                showOnboardingDialog()
                true
            } else {
                false
            }
        }
        
        switchMaster = findViewById(R.id.switchMaster)
        tvStatus = findViewById(R.id.tvStatus)
        tvTrackInfo = findViewById(R.id.tvTrackInfo)
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        
        permissionCard = findViewById(R.id.permissionCard)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        
        toggleAnimation = findViewById(R.id.toggleAnimation)
        toggleTypography = findViewById(R.id.toggleTypography)
        switchFallback = findViewById(R.id.switchFallback)
        tvFallbackDesc = findViewById(R.id.tvFallbackDesc)
        btnOpenManager = findViewById(R.id.btnOpenManager)
        btnAddTile = findViewById(R.id.btnAddTile)
        
        optionsContainer = findViewById(R.id.optionsContainer)
        
        switchMaster.isChecked = prefs.getBoolean("live_lyrics_enabled", false)
        optionsContainer.visibility = View.VISIBLE
        
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("live_lyrics_enabled", isChecked).apply()
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
                val stopLyrics = Intent(this, LiveLyricsService::class.java).apply { action = "STOP_LIVE_LYRICS" }
                startService(stopLyrics)
            }
        }
        
        val savedAnimStyle = prefs.getInt("live_anim_style", 0)
        when (savedAnimStyle) {
            1 -> toggleAnimation.check(R.id.btnAnimScroll)
            2 -> toggleAnimation.check(R.id.btnAnimHybrid)
            else -> toggleAnimation.check(R.id.btnAnimFlash)
        }
        
        toggleAnimation.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val style = when (checkedId) {
                    R.id.btnAnimScroll -> 1
                    R.id.btnAnimHybrid -> 2
                    else -> 0
                }
                prefs.edit().putInt("live_anim_style", style).apply()
            }
        }
        
        val tvSyncOffset: TextView = findViewById(R.id.tvSyncOffset)
        val sliderSync: com.google.android.material.slider.Slider = findViewById(R.id.sliderSync)
        
        val savedSyncOffset = prefs.getInt("live_sync_offset", 0)
        sliderSync.value = savedSyncOffset.toFloat()
        tvSyncOffset.text = "${savedSyncOffset}ms"
        
        sliderSync.addOnChangeListener { _, value, _ ->
            val offsetMs = value.toInt()
            tvSyncOffset.text = "${offsetMs}ms"
            prefs.edit().putInt("live_sync_offset", offsetMs).apply()
        }
        
        val savedFontStyle = prefs.getInt("live_font_style", 2)
        currentFontStyle = when (savedFontStyle) {
            1 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
            2 -> GlyphFontEngine.FontStyle.PIXEL_TINY
            else -> GlyphFontEngine.FontStyle.SMOOTH
        }
        
        when (currentFontStyle) {
            GlyphFontEngine.FontStyle.BLOCK_BOLD -> toggleTypography.check(R.id.btnBlocky)
            GlyphFontEngine.FontStyle.PIXEL_TINY -> toggleTypography.check(R.id.btnPixel)
            else -> toggleTypography.check(R.id.btnSmooth)
        }
        
        toggleTypography.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFontStyle = when (checkedId) {
                    R.id.btnBlocky -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                    R.id.btnPixel -> GlyphFontEngine.FontStyle.PIXEL_TINY
                    else -> GlyphFontEngine.FontStyle.SMOOTH
                }
                
                val styleInt = when (currentFontStyle) {
                    GlyphFontEngine.FontStyle.BLOCK_BOLD -> 1
                    GlyphFontEngine.FontStyle.PIXEL_TINY -> 2
                    else -> 0
                }
                prefs.edit().putInt("live_font_style", styleInt).apply()
            }
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
            try {
                val intent = Intent()
                intent.component = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
                startActivity(intent)
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), "Toys Manager not found.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
            }
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
        if (!isNotificationAccessGranted) {
            tvStatus.text = "Missing Permission"
            tvTrackInfo.text = "Enable Notification Access to read media playback."
            return
        }
        
        if (!MusicPlaybackState.hasActiveSession) {
            tvStatus.text = "Waiting for Music..."
            tvTrackInfo.text = "Play a track to begin."
            ivAlbumArt.visibility = View.GONE
        } else {
            val title = MusicPlaybackState.trackTitle
            val artist = MusicPlaybackState.artist
            
            if (MusicPlaybackState.albumArt != null) {
                ivAlbumArt.setImageBitmap(MusicPlaybackState.albumArt)
                ivAlbumArt.visibility = View.VISIBLE
            } else {
                ivAlbumArt.visibility = View.GONE
            }
            
            if (MusicPlaybackState.manualOverrideLyrics != null) {
                tvStatus.text = "Live Syncing"
                tvTrackInfo.text = "$title — $artist"
            } else if (title.isNotEmpty()) {
                tvStatus.text = "Active Session"
                tvTrackInfo.text = "$title — $artist\n(Searching for lyrics...)"
            } else {
                tvStatus.text = "Active Session"
                tvTrackInfo.text = "Unknown track."
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
