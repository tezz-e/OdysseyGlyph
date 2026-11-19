package com.example.odysseyglyph

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    
    // UI Elements
    private lateinit var toolbar: MaterialToolbar
    private lateinit var launcherGroup: LinearLayout
    private lateinit var btnSelectVideo: MaterialButton
    private lateinit var videoContainer: FrameLayout
    private lateinit var videoView: CenteredVideoView
    private lateinit var imageView: CenteredImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var settingsPanel: LinearLayout
    
    private lateinit var trimmerCard: LinearLayout
    private lateinit var rangeSlider: RangeSlider
    private lateinit var tvTrimTimes: TextView
    
    private lateinit var modeSpinner: AutoCompleteTextView
    private lateinit var slotSpinner: AutoCompleteTextView
    
    private lateinit var audioCard: LinearLayout
    private lateinit var switchIncludeAudio: com.google.android.material.materialswitch.MaterialSwitch
    
    private lateinit var btnAdvancedToggle: LinearLayout
    
    private lateinit var btnProcess: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var progressBar: GlyphProgressView
    private lateinit var successActionsContainer: View
    private lateinit var btnOpenManager: MaterialButton
    private lateinit var btnOpenGallerySuccess: MaterialButton

    private lateinit var btnLaunchVisualizer: MaterialButton
    private lateinit var btnLaunchLiveLyrics: MaterialButton
    private lateinit var bottomActionBar: LinearLayout

    private var selectedMediaUri: Uri? = null
    private var currentMediaType = 0 // 0=video, 1=gif, 2=static
    private var videoDurationMs: Long = 0
    private var isRendering = AtomicBoolean(false)

    // Advanced Settings state
    private var advFps = 12
    private var advInvert = false
    private var advSharpen = true
    private var advContrast = 1.0f
    private var advBrightness = 100.0f
    private var advDuration = 5.0f
    private var advEnhancementMode = 0 // 0 = Auto, 1 = On, 2 = Off

    fun loadMedia(uri: Uri) {
        VideoProcessor.clearFrameCache()
        selectedMediaUri = uri
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        val mimeType = contentResolver.getType(uri) ?: (if (uri.path?.endsWith(".gif") == true) "image/gif" else if (uri.path?.endsWith(".jpg") == true || uri.path?.endsWith(".png") == true) "image/jpeg" else "")
        when {
            mimeType.startsWith("image/gif") -> {
                currentMediaType = 1
                setupGif(uri)
            }
            mimeType.startsWith("image/") -> {
                currentMediaType = 2
                setupStaticImage(uri)
            }
            else -> {
                currentMediaType = 0
                setupVideo(uri)
            }
        }
        launcherGroup.visibility = View.GONE
        findViewById<View>(R.id.layoutHeaderTitle)?.visibility = View.GONE
        toolbar.visibility = View.VISIBLE
        toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        settingsPanel.visibility = View.VISIBLE
        btnAdvancedToggle.visibility = View.VISIBLE
        bottomActionBar.visibility = View.VISIBLE
        btnProcess.visibility = View.VISIBLE
        btnCancel.visibility = View.GONE
        progressBar.visibility = View.GONE
        successActionsContainer.visibility = View.GONE
        audioCard.visibility = if (currentMediaType == 0) View.VISIBLE else View.GONE
    }

    private val selectMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { loadMedia(it) }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_main)
        
        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)

        loadAdvancedSettings()
        bindViews()
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBar, applyTop = true, applyBottom = false)
        WindowInsetsUtil.applySystemBarsPadding(scrollView, applyBottom = true, overlayView = bottomActionBar)
        WindowInsetsUtil.applySystemBarsPadding(bottomActionBar, applyBottom = true)
        
        bottomActionBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if ((bottom - top) != (oldBottom - oldTop)) {
                updateScrollViewPadding()
            }
        }

        setupListeners()
        setupBentoConsole()
        syncSlotState()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (launcherGroup.visibility != View.VISIBLE) {
                    if (isRendering.get()) {
                        cancelProcessing()
                    }
                    resetToLauncher()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        handleIntent(intent)

        if (!prefs.getBoolean("spotlight_tour_done_v1", false)) {
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed && launcherGroup.visibility == View.VISIBLE) {
                    startFeatureSpotlightTour()
                }
            }, 500)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val testPath = intent.getStringExtra("load_test_image")
        if (testPath != null) {
            val file = java.io.File(testPath)
            if (file.exists()) {
                loadMedia(Uri.fromFile(file))
            }
        } else if (intent.data != null) {
            loadMedia(intent.data!!)
        }
        if (intent.getBooleanExtra("open_advanced_settings", false)) {
            window.decorView.postDelayed({ showAdvancedSettingsBottomSheet() }, 300)
        }
        if (intent.getBooleanExtra("auto_process", false)) {
            window.decorView.postDelayed({
                findViewById<android.widget.Button>(R.id.btnProcess)?.performClick()
            }, 1200)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupBentoConsole() {
        val heroCard = findViewById<FrameLayout>(R.id.heroLyricsCard)
        heroCard?.let {
            it.foreground = null
            it.applyLiquidGlass(pressedScale = 0.98f)
            it.setOnClickListener { btnLaunchLiveLyrics.performClick() }
        }

        findViewById<View>(R.id.cardMediaPipeline)?.let { card ->
            card.applyLiquidGlass(pressedScale = 0.98f)
            card.setOnClickListener { btnSelectVideo.performClick() }
        }

        findViewById<View>(R.id.cardVisualizer)?.let { card ->
            card.applyLiquidGlass(pressedScale = 0.98f)
            card.setOnClickListener { btnLaunchVisualizer.performClick() }
        }

        val cardQuickSignal = findViewById<View>(R.id.cardQuickSignal)
        val btnLaunchQuickSignal = findViewById<View>(R.id.btnLaunchQuickSignal)
        val btnClearQuickSignal = findViewById<View>(R.id.btnClearQuickSignalMain)

        val openQuickSignal = {
            NavigationHelper.startWithPush(this, Intent(this, QuickSignalActivity::class.java))
        }

        cardQuickSignal?.applyLiquidGlass(pressedScale = 0.98f)
        cardQuickSignal?.setOnClickListener { openQuickSignal() }
        btnLaunchQuickSignal?.setOnClickListener { openQuickSignal() }

        btnClearQuickSignal?.setOnClickListener {
            val stopIntent = Intent(this, QuickSignalService::class.java).apply {
                action = QuickSignalService.ACTION_STOP_SIGNAL
            }
            startService(stopIntent)
            syncQuickSignalState()
        }

        btnSelectVideo.applyLiquidGlass(pressedScale = 0.96f)
        btnLaunchVisualizer.applyLiquidGlass(pressedScale = 0.96f)
        btnLaunchLiveLyrics.applyLiquidGlass(pressedScale = 0.96f)
        findViewById<View>(R.id.btnAddTileMain)?.let { TouchSpringAnimator.attach(it) }
        findViewById<View>(R.id.btnAddWidgetMain)?.let { TouchSpringAnimator.attach(it) }
        findViewById<View>(R.id.btnVaultMain)?.let { TouchSpringAnimator.attach(it) }
        btnProcess.applyLiquidGlass(pressedScale = 0.96f)
        btnCancel.applyLiquidGlass(pressedScale = 0.96f)
    }

    private fun updateScrollViewPadding() {
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        scrollView.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        // PHONE CONTROLS section stays permanently visible so users can add both QS Tile and Widget anytime
        findViewById<View>(R.id.quickAccessSection).visibility = View.VISIBLE
        syncSlotState()
        syncQuickSignalState()
        checkToysManagerVisibility()
        if (currentMediaType == 0) {
            videoView.start()
        }
    }

    private fun syncQuickSignalState() {
        val tvBadge = findViewById<TextView>(R.id.tvQuickSignalBadge) ?: return
        val tvSub = findViewById<TextView>(R.id.tvQuickSignalSubtitle) ?: return
        val btnClear = findViewById<View>(R.id.btnClearQuickSignalMain) ?: return

        if (QuickSignalService.isRunning) {
            tvBadge.text = "ACTIVE"
            tvBadge.setTextColor(android.graphics.Color.parseColor("#EA3323"))
            tvSub.text = "Currently showing \"${QuickSignalService.activeText}\" on rear matrix"
            btnClear.visibility = View.VISIBLE
        } else {
            tvBadge.text = "DIRECT MESSAGE"
            tvBadge.setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
            tvSub.text = "Project short messages (WAIT, BRB, symbols) to the rear matrix"
            btnClear.visibility = View.GONE
        }
    }

    private fun processMedia() {
        checkToysManagerVisibility()
        if (currentMediaType == 0) {
            videoView.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentMediaType == 0) {
            videoView.pause()
        }
    }

    private fun loadAdvancedSettings() {
        advFps = prefs.getInt("adv_fps", 12)
        advInvert = prefs.getBoolean("adv_invert", false)
        advSharpen = prefs.getBoolean("adv_sharpen", true)
        advContrast = prefs.getFloat("adv_contrast", 1.0f)
        advBrightness = prefs.getFloat("adv_brightness", 100.0f)
        advDuration = prefs.getFloat("adv_duration", 5.0f)
        advEnhancementMode = prefs.getInt("adv_enhancement_mode", 0)
    }

    private fun saveAdvancedSettings() {
        prefs.edit()
            .putInt("adv_fps", advFps)
            .putBoolean("adv_invert", advInvert)
            .putBoolean("adv_sharpen", advSharpen)
            .putFloat("adv_contrast", advContrast)
            .putFloat("adv_brightness", advBrightness)
            .putFloat("adv_duration", advDuration)
            .putInt("adv_enhancement_mode", advEnhancementMode)
            .apply()
    }

    private fun bindViews() {
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnLaunchVisualizer = findViewById(R.id.btnLaunchVisualizer)
        btnLaunchLiveLyrics = findViewById(R.id.btnLaunchLiveLyrics)
        toolbar = findViewById(R.id.toolbar)
        launcherGroup = findViewById(R.id.launcherGroup)
        videoContainer = findViewById(R.id.videoContainer)
        videoView = findViewById(R.id.videoView)
        imageView = findViewById(R.id.imageView)
        cropOverlay = findViewById(R.id.cropOverlay)
        settingsPanel = findViewById(R.id.settingsPanel)
        
        trimmerCard = findViewById(R.id.trimmerCard)
        rangeSlider = findViewById(R.id.rangeSlider)
        tvTrimTimes = findViewById(R.id.tvTrimTimes)
        
        modeSpinner = findViewById(R.id.modeSpinner)
        slotSpinner = findViewById(R.id.slotSpinner)
        
        audioCard = findViewById(R.id.audioCard)
        switchIncludeAudio = findViewById(R.id.switchIncludeAudio)
        
        btnAdvancedToggle = findViewById(R.id.btnAdvancedToggle)
        
        btnProcess = findViewById(R.id.btnProcess)
        btnCancel = findViewById(R.id.btnCancel)
        progressBar = findViewById(R.id.progressBar)
        successActionsContainer = findViewById(R.id.successActionsContainer)
        btnOpenManager = findViewById(R.id.btnOpenManager)
        btnOpenGallerySuccess = findViewById(R.id.btnOpenGallerySuccess)

        bottomActionBar = findViewById(R.id.bottomActionBar)
    }

    private fun setupListeners() {
        val btnHeaderMenu = findViewById<ImageView>(R.id.btnHeaderMenu)
        btnHeaderMenu?.let { btn ->
            TouchSpringAnimator.attach(btn)
            btn.setOnClickListener {
                val popup = androidx.appcompat.widget.PopupMenu(this, btn)
                popup.menuInflater.inflate(R.menu.menu_main_overflow, popup.menu)
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menu_tour -> {
                            startFeatureSpotlightTour()
                            true
                        }
                        R.id.menu_add_tile -> {
                            requestAddQuickTile()
                            true
                        }
                        R.id.menu_add_widget -> {
                            requestAddHomeWidget()
                            true
                        }
                        R.id.menu_github -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tezz-e/OdysseyGlyph"))
                            startActivity(intent)
                            true
                        }
                        R.id.menu_about -> {
                            showAppInfoDialog()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }

        btnSelectVideo.setOnClickListener {
            selectMediaLauncher.launch(arrayOf("video/*", "image/*"))
        }
        
        toolbar.inflateMenu(R.menu.menu_info)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_info) {
                showAppInfoDialog()
                true
            } else {
                false
            }
        }
        
        toolbar.setNavigationOnClickListener {
            if (launcherGroup.visibility == View.GONE && !isRendering.get()) {
                resetToLauncher()
            }
        }

        val btnAddTileMain = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddTileMain)
        btnAddTileMain.setOnClickListener {
            requestAddQuickTile()
        }

        val btnAddWidgetMain = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddWidgetMain)
        btnAddWidgetMain.setOnClickListener {
            requestAddHomeWidget()
        }

        val btnVaultMain = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVaultMain)
        btnVaultMain?.let {
            TouchSpringAnimator.attach(it)
            it.setOnClickListener {
                startActivity(Intent(this, GalleryActivity::class.java))
            }
        }

        btnLaunchVisualizer.setOnClickListener {
            val isGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
            if (!isGranted) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_nothing_onboarding, null)
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                
                dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "PERMISSION REQUIRED"
                dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "The visualizer needs permission to read your music player state so it knows when to automatically start and stop the LEDs.\n\nPlease enable Odyssey Glyph in the next screen."
                dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogAction).apply {
                    text = "GRANT PERMISSION"
                    setOnClickListener {
                        dialog.dismiss()
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                }
                dialog.show()
                return@setOnClickListener
            }
            
            val bottomSheet = FallbackStyleBottomSheet()
            bottomSheet.setOnStyleSelectedListener { style ->
                val permissionsToRequest = mutableListOf<String>()
                
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                
                if (style == 2 || style == 3 || style == 5 || style == 8) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                
                if (permissionsToRequest.isNotEmpty()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        requestPermissions(permissionsToRequest.toTypedArray(), 101)
                        Snackbar.make(findViewById(android.R.id.content), "Please grant permissions, then try again.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
                        return@setOnStyleSelectedListener
                    }
                }
                
                prefs.edit().putString("last_used_service", "visualizer").apply()
                
                // Stop live lyrics first if it's running
                val stopLyrics = Intent(this@MainActivity, LiveLyricsService::class.java).apply { action = "STOP_LIVE_LYRICS" }
                startService(stopLyrics)
                
                val intent = Intent(this, VisualizerService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Snackbar.make(findViewById(android.R.id.content), "Visualizer Started! Check your notifications to stop it.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
            }
            bottomSheet.show(supportFragmentManager, "FallbackStyleBottomSheet")
        }

        btnLaunchLiveLyrics.setOnClickListener {
            if (ProminentDisclosureDialog.showIfRequired(this, onAccepted = {
                startActivity(Intent(this, LiveLyricsActivity::class.java))
            })) {
                return@setOnClickListener
            }
            startActivity(Intent(this, LiveLyricsActivity::class.java))
        }

        btnAdvancedToggle.setOnClickListener {
            showAdvancedSettingsBottomSheet()
        }

        // Audio listener removed

        rangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            tvTrimTimes.text = String.format("TRIM: %.1fs - %.1fs", values[0], values[1])
        }

        btnProcess.setOnClickListener { startProcessing() }
        btnCancel.setOnClickListener { cancelProcessing() }



        btnOpenManager.setOnClickListener {
            GlyphOSIntegration.openGlyphAppOrToys(this, findViewById(android.R.id.content))
        }

        btnOpenGallerySuccess.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        slotSpinner.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putInt("selected_slot", position + 1).apply()
        }
    }

    private fun showAdvancedSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_advanced_settings, null)
        dialog.setContentView(view)

        val etFps = view.findViewById<TextInputEditText>(R.id.etFps)
        val cbInvert = view.findViewById<MaterialSwitch>(R.id.cbInvert)
        val sharpenSwitch = view.findViewById<MaterialSwitch>(R.id.sharpenSwitch)
        val contrastSlider = view.findViewById<Slider>(R.id.contrastSlider)
        val contrastLabel = view.findViewById<TextView>(R.id.contrastLabel)
        val brightnessSlider = view.findViewById<Slider>(R.id.brightnessSlider)
        val brightnessLabel = view.findViewById<TextView>(R.id.brightnessLabel)
        val durationSlider = view.findViewById<Slider>(R.id.durationSlider)
        val durationLabel = view.findViewById<TextView>(R.id.durationLabel)

        if (currentMediaType == 2) {
            durationSlider.visibility = View.VISIBLE
            durationLabel.visibility = View.VISIBLE
        } else {
            durationSlider.visibility = View.GONE
            durationLabel.visibility = View.GONE
        }

        etFps.setText(advFps.toString())
        cbInvert.isChecked = advInvert
        sharpenSwitch.isChecked = advSharpen
        contrastSlider.value = advContrast
        brightnessSlider.valueFrom = 10f
        brightnessSlider.valueTo = 150f
        brightnessSlider.value = advBrightness.coerceIn(10f, 150f)
        durationSlider.value = advDuration

        contrastLabel.text = String.format("CONTRAST MULTIPLIER: %.1fx", advContrast)
        val initialTag = if (advBrightness > 100f) " (SUN OVERDRIVE)" else if (advBrightness >= 90f) " (CALIBRATED)" else ""
        brightnessLabel.text = String.format("LED BRIGHTNESS: %.0f%%%s", advBrightness, initialTag)
        durationLabel.text = String.format("IMAGE DURATION: %.0fs", advDuration)

        contrastSlider.addOnChangeListener { _, value, _ ->
            advContrast = value
            contrastLabel.text = String.format("CONTRAST MULTIPLIER: %.1fx", value)
        }
        brightnessSlider.addOnChangeListener { _, value, _ ->
            advBrightness = value
            val tag = if (value > 100f) " (SUN OVERDRIVE)" else if (value >= 90f) " (CALIBRATED)" else ""
            brightnessLabel.text = String.format("LED BRIGHTNESS: %.0f%%%s", value, tag)
        }
        durationSlider.addOnChangeListener { _, value, _ ->
            advDuration = value
            durationLabel.text = String.format("IMAGE DURATION: %.0fs", value)
        }
        cbInvert.setOnCheckedChangeListener { _, isChecked -> advInvert = isChecked }
        sharpenSwitch.setOnCheckedChangeListener { _, isChecked -> advSharpen = isChecked }
        val toggleEnhancement = view.findViewById<LiquidGlassSegmentedBar>(R.id.toggleEnhancement)
        toggleEnhancement?.setSegments(listOf("Auto", "On", "Off"), initialIndex = advEnhancementMode)
        toggleEnhancement?.onSegmentSelected = { index, _ ->
            advEnhancementMode = index
        }

        dialog.setOnDismissListener {
            val parsedFps = etFps.text.toString().toIntOrNull() ?: 12
            advFps = parsedFps.coerceIn(1, 30)
            saveAdvancedSettings()
        }

        dialog.show()
    }

    private fun showAppInfoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nothing_onboarding, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "ODYSSEY GLYPH"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = 
            "25×25 LED Matrix Audio and Media Console for Nothing OS.\n\n" +
            "• LIVE LYRICS: Real-time synced lyric projection\n" +
            "• MUSIC VISUALIZER: Audio-reactive LED patterns\n" +
            "• MEDIA IMPORT: Video and GIF matrix renderer\n" +
            "• QUICK SIGNAL: Instant rear matrix alerts and presets\n\n" +
            "GitHub: github.com/tezz-e/OdysseyGlyph"
            
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogAction).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogSecondary)?.apply {
            visibility = View.VISIBLE
            text = "REPLAY FEATURE TOUR"
            setOnClickListener {
                dialog.dismiss()
                startFeatureSpotlightTour()
            }
        }
        dialog.show()
    }

    private fun requestAddQuickTile() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(android.app.StatusBarManager::class.java)
            val componentName = android.content.ComponentName(this, LiveLyricsTileService::class.java)
            statusBarManager?.requestAddTileService(
                componentName,
                "Odyssey Glyph",
                android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher),
                mainExecutor
            ) { result ->
                if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || 
                    result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                    prefs.edit().putBoolean("tile_added", true).apply()
                    val msg = if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
                        "Tile added to notification shade."
                    else
                        "Tile is already in notification shade."
                    Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).applyNothingStyle().show()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), "Failed to add tile.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
                }
            }
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Pull down your notification shade and edit tiles to add 'Odyssey Glyph'.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
        }
    }

    private fun requestAddHomeWidget() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
            val myProvider = android.content.ComponentName(this, OdysseyWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val successCallback = android.app.PendingIntent.getBroadcast(
                    this, 0,
                    android.content.Intent(this, OdysseyWidgetProvider::class.java).apply { action = OdysseyWidgetProvider.ACTION_UPDATE_WIDGET },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Your launcher does not support automatic widget pinning. Please add it manually from your launcher's widget menu.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
            }
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Automatic widget pinning requires Android 8.0+. Please add it manually from your launcher's widget menu.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
        }
    }

    fun startFeatureSpotlightTour() {
        val heroLyricsCard = findViewById<View>(R.id.heroLyricsCard) ?: return
        val cardVisualizer = findViewById<View>(R.id.cardVisualizer) ?: return
        val cardMediaPipeline = findViewById<View>(R.id.cardMediaPipeline) ?: return
        val cardQuickSignal = findViewById<View>(R.id.cardQuickSignal) ?: return
        val quickAccessSection = findViewById<View>(R.id.quickAccessSection) ?: return
        val scrollView = findViewById<ScrollView>(R.id.scrollView)

        NothingSpotlightOverlay.attach(this, scrollView)
            .addStep(
                NothingSpotlightOverlay.Step(
                    targetView = heroLyricsCard,
                    title = "LIVE LYRICS",
                    description = "Streams synchronized lyrics to the rear 25×25 matrix while music plays on Spotify, YouTube Music, or other players. Turn on the master toggle inside to start tracking playback.",
                    cornerRadiusDp = 22f,
                    paddingDp = 4f
                )
            )
            .addStep(
                NothingSpotlightOverlay.Step(
                    targetView = cardVisualizer,
                    title = "MUSIC VISUALIZER",
                    description = "Audio-reactive LED patterns for your music. Tap Patterns to choose a style. The fallback visualizer also plays during instrumental intros and solos while waiting for lyrics to start, or whenever a song has no lyrics available.",
                    cornerRadiusDp = 22f,
                    paddingDp = 4f
                )
            )
            .addStep(
                NothingSpotlightOverlay.Step(
                    targetView = cardMediaPipeline,
                    title = "MEDIA IMPORT",
                    description = "Convert videos and GIFs into matrix animations. Pick a file from your gallery, align the circular crop area, and render it directly to your phone's Glyph Toys.",
                    cornerRadiusDp = 22f,
                    paddingDp = 4f
                )
            )
            .addStep(
                NothingSpotlightOverlay.Step(
                    targetView = cardQuickSignal,
                    title = "QUICK SIGNAL",
                    description = "Flash short messages like WAIT and BRB, or symbols like hearts and arrows, onto the back of your phone. Pick a preset or type custom text with an auto-off timer.",
                    cornerRadiusDp = 22f,
                    paddingDp = 4f
                )
            )
            .addStep(
                NothingSpotlightOverlay.Step(
                    targetView = quickAccessSection,
                    title = "PHONE CONTROLS",
                    description = "Pin the Quick Settings tile to toggle Live Lyrics straight from your notification shade. Drop the widget on your home screen to control the rear matrix with one tap without opening the app.",
                    cornerRadiusDp = 22f,
                    paddingDp = 4f
                )
            )
            .setOnCompleted {
                prefs.edit().putBoolean("spotlight_tour_done_v1", true).apply()
            }
            .setOnDismissed {
                prefs.edit().putBoolean("spotlight_tour_done_v1", true).apply()
            }
            .start()
    }

    private fun syncSlotState() {
        val slot = prefs.getInt("selected_slot", 1)
        val slotText = if (slot == 4) "Gallery Only" else "Slot $slot"
        slotSpinner.setText(slotText, false)
    }

    private fun checkToysManagerVisibility() {
        val slot = prefs.getInt("selected_slot", 1)
        val file = java.io.File(filesDir, "frames_slot$slot.bin")
        if (file.exists()) {
            successActionsContainer.visibility = View.VISIBLE
        } else {
            successActionsContainer.visibility = View.GONE
        }
    }



    private fun setupVideo(uri: Uri) {
        launcherGroup.visibility = View.GONE
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        videoView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
        trimmerCard.visibility = View.VISIBLE

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            videoDurationMs = mp.duration.toLong()
            mp.start()
            
            val maxSecs = Math.max(0.1f, videoDurationMs / 1000f)
            if (maxSecs > rangeSlider.valueTo) {
                rangeSlider.valueTo = maxSecs
                rangeSlider.values = listOf(0f, maxSecs)
            } else {
                rangeSlider.values = listOf(0f, maxSecs)
                rangeSlider.valueTo = maxSecs
            }
            tvTrimTimes.text = String.format("TRIM: 0.0s - %.1fs", maxSecs)
        }
    }

    private fun setupGif(uri: Uri) {
        launcherGroup.visibility = View.GONE
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        videoView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        trimmerCard.visibility = View.VISIBLE

        imageView.setImageURIWithAnim(uri)
        rangeSlider.valueFrom = 0f
        rangeSlider.valueTo = 10f
        rangeSlider.values = listOf(0f, 10f)
        tvTrimTimes.text = "TRIM: 0.0s - 10.0s"
    }

    private fun setupStaticImage(uri: Uri) {
        launcherGroup.visibility = View.GONE
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        videoView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        trimmerCard.visibility = View.GONE

        imageView.setImageURIWithAnim(uri)
    }
    
    private fun resetToLauncher() {
        VideoProcessor.clearFrameCache()
        selectedMediaUri = null
        currentMediaType = -1
        
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)
        
        findViewById<View>(R.id.layoutHeaderTitle)?.visibility = View.VISIBLE
        toolbar.visibility = View.GONE
        launcherGroup.visibility = View.VISIBLE
        settingsPanel.visibility = View.GONE
        trimmerCard.visibility = View.GONE
        btnProcess.visibility = View.GONE
        btnCancel.visibility = View.GONE
        progressBar.visibility = View.GONE
        btnAdvancedToggle.visibility = View.GONE
        audioCard.visibility = View.GONE
        bottomActionBar.visibility = View.GONE
        checkToysManagerVisibility()
        
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }

    private fun cancelProcessing() {
        isRendering.set(true)
        btnProcess.visibility = View.VISIBLE
        btnCancel.visibility = View.GONE
        progressBar.visibility = View.GONE
        progressBar.setProgress(0)
        Snackbar.make(findViewById(android.R.id.content), "Render cancelled.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
    }

    private fun startProcessing() {
        val uri = selectedMediaUri
        if (uri == null) {
            Snackbar.make(findViewById(android.R.id.content), "Please select a media file first.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
            return
        }

        isRendering.set(false)
        btnProcess.visibility = View.GONE
        btnCancel.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        progressBar.setProgress(0)
        successActionsContainer.visibility = View.GONE

        val startTimeMs = if (currentMediaType == 2) 0L else (rangeSlider.values[0] * 1000).toLong()
        val endTimeMs = if (currentMediaType == 2) 0L else (rangeSlider.values[1] * 1000).toLong()

        val fps = advFps
        val invert = advInvert
        val mode = if (modeSpinner.text.toString() == "LOOP") 1 else 0
        val slot = prefs.getInt("selected_slot", 1)

        val imageDurationSec = advDuration.toInt()
        val contrastMulti = advContrast
        val brightnessMulti = advBrightness / 100f
        val sharpen = advSharpen

        val inverseTransform = Matrix()
        if (currentMediaType == 0) {
            videoView.engine.matrix.invert(inverseTransform)
            videoView.pause()
        } else {
            imageView.engine.matrix.invert(inverseTransform)
            
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { 
                    android.graphics.BitmapFactory.decodeStream(it, null, opts) 
                }
                val rawW = opts.outWidth
                val rawH = opts.outHeight
                val drawable = imageView.drawable
                if (drawable != null && rawW > 0 && drawable.intrinsicWidth > 0) {
                    val scaleMatrix = Matrix()
                    scaleMatrix.setScale(rawW.toFloat() / drawable.intrinsicWidth, rawH.toFloat() / drawable.intrinsicHeight)
                    inverseTransform.postConcat(scaleMatrix)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        VideoProcessor.processMedia(
            context = this,
            mediaUri = uri,
            audioUri = if (currentMediaType == 0 && switchIncludeAudio.isChecked) uri else null,
            mediaType = currentMediaType,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            targetFps = fps,
            playbackMode = mode,
            invertColors = invert,
            contrastMulti = contrastMulti,
            brightnessMulti = brightnessMulti,
            imageDurationSec = imageDurationSec,
            sharpen = sharpen,
            enhancementMode = advEnhancementMode,
            cropCx = cropOverlay.circleX,
            cropCy = cropOverlay.circleY,
            cropRadius = cropOverlay.circleRadius,
            inverseTransform = inverseTransform,
            slotIndex = slot,
            isCancelled = isRendering,
            onProgress = { prog ->
                progressBar.setProgress(prog)
            },
            onComplete = { success, error ->
                btnProcess.visibility = View.VISIBLE
                btnCancel.visibility = View.GONE
                progressBar.visibility = View.GONE

                if (success) {
                    val msg = if (slot == 4) "SUCCESS! SAVED TO GALLERY." else "SUCCESS! RENDERED TO SLOT $slot."
                    Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).applyNothingStyle().show()
                    successActionsContainer.visibility = View.VISIBLE
                    sendBroadcast(Intent("com.example.odysseyglyph.RELOAD_FRAMES"))
                } else {
                    if (error != "Cancelled by user.") {
                        Snackbar.make(findViewById(android.R.id.content), "ERROR: $error", Snackbar.LENGTH_LONG).applyNothingStyle().show()
                    }
                }
            }
        )
    }
}
