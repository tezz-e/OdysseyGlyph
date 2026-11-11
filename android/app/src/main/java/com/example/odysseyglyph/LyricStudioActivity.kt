package com.example.odysseyglyph

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.RangeSlider
import com.google.android.material.snackbar.Snackbar
import android.widget.EditText
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class LyricStudioActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    
    // UI Elements
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnBack: ImageView
    private lateinit var etSearch: EditText
    private lateinit var resultsContainer: LinearLayout
    private lateinit var previewCard: FrameLayout
    private lateinit var previewImage: CenteredImageView

    private lateinit var heroTeleprompterCard: FrameLayout
    private lateinit var cipherTitle: CipherTextView
    private lateinit var tvDecryptedSubtitle: TextView
    private lateinit var teleprompterMatrixMirror: GlyphMatrixMirrorView
    private lateinit var waveformScrubber: TactileWaveformScrubberView
    
    private lateinit var editorPanel: LinearLayout
    private lateinit var toggleTypography: LiquidGlassSegmentedBar
    private lateinit var toggleAnimation: LiquidGlassSegmentedBar
    
    private lateinit var tvTrimTimes: TextView
    private lateinit var tvLyricPreview: TextView
    private lateinit var rangeSlider: RangeSlider
    private lateinit var slotSpinner: AutoCompleteTextView
    
    private lateinit var audioCard: LinearLayout
    private lateinit var tvAudioStatus: TextView
    private lateinit var switchAudio: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var btnAttachAudio: MaterialButton
    private lateinit var btnDrmInfo: ImageButton
    
    private lateinit var btnRender: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var progressBar: GlyphProgressView
    private lateinit var successActionsContainer: View
    private lateinit var btnOpenManager: MaterialButton
    private lateinit var btnOpenGallerySuccess: MaterialButton

    // State
    private var selectedAudioUri: Uri? = null
    private var selectedTrack: LrcTrack? = null
    private var currentFontStyle = GlyphFontEngine.FontStyle.PIXEL_TINY
    private var currentAnimStyle = 0 // 0=Flash, 1=Scroll
    private var isProgrammaticTextChange = false
    private var searchJobId = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRendering = AtomicBoolean(false)
    private var parsedLyricsCache = mutableListOf<Pair<Long, String>>()

    private val selectAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedAudioUri = uri
            updateAudioStatus("Attached: Manual Selection", true)
            
            // Try to extract filename to auto-search
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            var name = cursor.getString(nameIndex)
                            name = LrcQueryCleaner.clean(name)
                            
                            etSearch.setText(name)
                            Snackbar.make(findViewById(android.R.id.content), "Searching for lyrics: $name", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            selectedTrack?.let { attemptAutoMatchAudio(it) }
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Audio permission denied. Cannot auto-match local audio.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_lyric_studio)
        
        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBarLayout, applyTop = true)
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        WindowInsetsUtil.applySystemBarsPadding(scrollView, applyBottom = true)
        
        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)

        bindViews()
        setupListeners()
        syncSlotState()
        
        if (prefs.getBoolean("first_run_studio_v2", true)) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_nothing_onboarding, null)
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            
            dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "LYRIC STUDIO"
            dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "Search for any song to automatically download synchronized lyrics. You can then render the lyrics into a scrolling or flashing hologram on the Glyph matrix. Make sure to select the matching local audio file so it plays in sync!"
            dialogView.findViewById<MaterialButton>(R.id.btnDialogAction).setOnClickListener {
                prefs.edit().putBoolean("first_run_studio_v2", false).apply()
                dialog.dismiss()
            }
            dialog.show()
        }
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbar)
        btnBack = findViewById(R.id.btnBack)
        heroTeleprompterCard = findViewById(R.id.heroTeleprompterCard)
        cipherTitle = findViewById(R.id.cipherTitle)
        tvDecryptedSubtitle = findViewById(R.id.tvDecryptedSubtitle)
        teleprompterMatrixMirror = findViewById(R.id.teleprompterMatrixMirror)
        waveformScrubber = findViewById(R.id.waveformScrubber)

        etSearch = findViewById(R.id.etSearch)
        resultsContainer = findViewById(R.id.resultsContainer)
        previewCard = findViewById(R.id.previewCard)
        previewImage = findViewById(R.id.previewImage)
        
        editorPanel = findViewById(R.id.editorPanel)
        toggleTypography = findViewById(R.id.toggleTypography)
        toggleAnimation = findViewById(R.id.toggleAnimation)
        
        tvTrimTimes = findViewById(R.id.tvTrimTimes)
        tvLyricPreview = findViewById(R.id.tvLyricPreview)
        rangeSlider = findViewById(R.id.rangeSlider)
        slotSpinner = findViewById(R.id.slotSpinner)
        
        audioCard = findViewById(R.id.audioCard)
        tvAudioStatus = findViewById(R.id.tvAudioStatus)
        switchAudio = findViewById(R.id.switchAudio)
        btnAttachAudio = findViewById(R.id.btnAttachAudio)
        
        btnRender = findViewById(R.id.btnRender)
        btnCancel = findViewById(R.id.btnCancel)
        progressBar = findViewById(R.id.progressBar)
        successActionsContainer = findViewById(R.id.successActionsContainer)
        btnOpenManager = findViewById(R.id.btnOpenManager)
        btnOpenGallerySuccess = findViewById(R.id.btnOpenGallerySuccess)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editorPanel.visibility == View.VISIBLE) {
                    // Navigate back from lyric editor to song search list
                    editorPanel.visibility = View.GONE
                    previewCard.visibility = View.GONE
                    successActionsContainer.visibility = View.GONE
                    selectedTrack = null
                    val currentQuery = etSearch.text.toString()
                    if (currentQuery.length > 2) {
                        performSearch(currentQuery)
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // Start glowing laser perimeter beam on hero teleprompter
        heroTeleprompterCard.let {
            val beam = BorderBeamDrawable()
            it.foreground = beam
            beam.start()
        }

        // Attach touch spring physics
        TouchSpringAnimator.attach(btnBack)

        // Waveform Scrubber
        waveformScrubber.setOnScrubListener { fraction ->
            val range = rangeSlider.valueTo - rangeSlider.valueFrom
            if (range > 0) {
                val newStart = rangeSlider.valueFrom + (range * fraction)
                val currentSpan = if (rangeSlider.values.size >= 2) (rangeSlider.values[1] - rangeSlider.values[0]) else 20f
                val newEnd = (newStart + currentSpan).coerceAtMost(rangeSlider.valueTo)
                rangeSlider.values = listOf(newStart, newEnd)
            }
        }

        // Search Debounce & Re-entrancy
        etSearch.addTextChangedListener(object : TextWatcher {
            private val searchRunnable = Runnable {
                val query = etSearch.text.toString()
                if (query.length > 2) {
                    performSearch(query)
                } else {
                    resultsContainer.removeAllViews()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticTextChange) return
                mainHandler.removeCallbacks(searchRunnable)
                mainHandler.postDelayed(searchRunnable, 400) // 400ms debounce
            }
        })

        // Apple Liquid Glass Segmented Bars
        toggleTypography.setSegments(listOf("Pixel", "Bold", "Smooth"), initialIndex = 0)
        toggleTypography.onSegmentSelected = { index, _ ->
            currentFontStyle = when (index) {
                1 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                2 -> GlyphFontEngine.FontStyle.SMOOTH
                else -> GlyphFontEngine.FontStyle.PIXEL_TINY
            }
            updateWysiwygPreview()
        }

        toggleAnimation.setSegments(listOf("Flash", "Scroll"), initialIndex = 0)
        toggleAnimation.onSegmentSelected = { index, _ ->
            currentAnimStyle = if (index == 1) 1 else 0
            updateWysiwygPreview()
        }

        heroTeleprompterCard.applyLiquidGlass(pressedScale = 0.98f)
        btnRender.applyLiquidGlass(pressedScale = 0.96f)
        btnAttachAudio.applyLiquidGlass(pressedScale = 0.96f)
        btnOpenManager.applyLiquidGlass(pressedScale = 0.96f)
        btnOpenGallerySuccess.applyLiquidGlass(pressedScale = 0.96f)

        // Sliders
        rangeSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            tvTrimTimes.text = String.format("%.1fs - %.1fs", values[0], values[1])
            updateLyricPreviewLine(values[0])
        }

        // Audio
        btnAttachAudio.setOnClickListener { selectAudioLauncher.launch(arrayOf("audio/*")) }
        switchAudio.setOnCheckedChangeListener { _, isChecked ->
            val visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
            tvAudioStatus.visibility = visibility
            btnAttachAudio.visibility = visibility
        }

        // Rendering
        btnRender.setOnClickListener { renderLyrics() }
        btnCancel.setOnClickListener { cancelProcessing() }

        btnOpenGallerySuccess.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        // Toys Manager
        btnOpenManager.setOnClickListener {
            try {
                val intent = Intent()
                intent.component = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
                startActivity(intent)
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), "Nothing OS Toys Manager not found.", Snackbar.LENGTH_LONG).applyNothingStyle().show()
            }
        }
        
        slotSpinner.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putInt("selected_slot", position + 1).apply()
        }
    }

    private fun syncSlotState() {
        val slot = prefs.getInt("selected_slot", 1)
        val slotText = if (slot == 4) "Gallery Only" else "Slot $slot"
        slotSpinner.setText(slotText, false)
    }

    private fun updateAudioStatus(text: String, attached: Boolean) {
        tvAudioStatus.text = text
        if (attached) {
            tvAudioStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess))
            btnAttachAudio.text = "CHANGE AUDIO"
            switchAudio.visibility = View.VISIBLE
        } else {
            tvAudioStatus.setTextColor(ContextCompat.getColor(this, R.color.colorOnSurface))
            btnAttachAudio.text = "SELECT LOCAL AUDIO"
            switchAudio.visibility = View.GONE
        }
    }

    private fun performSearch(query: String) {
        resultsContainer.removeAllViews()
        val jobId = ++searchJobId
        
        val loadingText = TextView(this).apply {
            text = "Searching..."
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        resultsContainer.addView(loadingText)
        
        thread {
            val results = LRCLibClient.searchLyrics(query)
            
            mainHandler.post {
                if (jobId != searchJobId) return@post // Stale response guard
                
                resultsContainer.removeAllViews()
                if (results.isEmpty()) {
                    resultsContainer.addView(TextView(this).apply {
                        text = "No matches found for \"$query\"."
                        setTextColor(ContextCompat.getColor(this@LyricStudioActivity, R.color.colorError))
                        gravity = android.view.Gravity.CENTER
                    })
                    return@post
                }
                
                val syncedResults = results.filter { it.syncedLyrics != null }
                if (syncedResults.isEmpty()) {
                    resultsContainer.addView(TextView(this).apply {
                        text = "Matches found, but none have synchronized lyrics."
                        setTextColor(ContextCompat.getColor(this@LyricStudioActivity, R.color.colorError))
                        gravity = android.view.Gravity.CENTER
                    })
                    return@post
                }
                
                for (track in syncedResults) {
                    val card = MaterialCardView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, 0, 16)
                        }
                        setCardBackgroundColor(ContextCompat.getColor(this@LyricStudioActivity, R.color.colorSurfaceVariant))
                        radius = 24f
                        
                        val inner = LinearLayout(this@LyricStudioActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(32, 32, 32, 32)
                        }
                        
                        inner.addView(TextView(this@LyricStudioActivity).apply {
                            text = track.trackName
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        })
                        
                        inner.addView(TextView(this@LyricStudioActivity).apply {
                            text = "${track.artistName} • ${track.albumName}"
                            setTextColor(ContextCompat.getColor(this@LyricStudioActivity, R.color.colorOnSurfaceVariant))
                            textSize = 12f
                        })
                        
                        addView(inner)
                        
                        setOnClickListener {
                            selectTrack(track)
                        }
                    }
                    resultsContainer.addView(card)
                }
            }
        }
    }

    private fun selectTrack(track: LrcTrack) {
        selectedTrack = track
        
        isProgrammaticTextChange = true
        etSearch.setText(track.trackName)
        isProgrammaticTextChange = false
        
        resultsContainer.removeAllViews()
        editorPanel.visibility = View.VISIBLE
        previewCard.visibility = View.VISIBLE
        successActionsContainer.visibility = View.GONE
        
        // Trigger cipher text scramble animation with clean artist - title
        val displayTitle = if (track.artistName.isNotBlank() && !track.trackName.contains(track.artistName, ignoreCase = true)) {
            "${track.artistName} — ${track.trackName}"
        } else {
            track.trackName
        }
        cipherTitle.animateDecryption(displayTitle)
        tvDecryptedSubtitle.text = "Synced lyrics • Ready to render"
        
        parseLyricsCache(track.syncedLyrics ?: "")
        updateWysiwygPreview()
        checkAndRequestAudioMatch(track)
    }

    private fun parseLyricsCache(syncedLyrics: String) {
        parsedLyricsCache.clear()
        parsedLyricsCache.addAll(LrcUtils.parseLrc(syncedLyrics))
        
        if (parsedLyricsCache.isNotEmpty()) {
            val firstLyricMs = parsedLyricsCache.first().first
            val lastLyricMs = parsedLyricsCache.last().first
            val maxSecs = Math.max(0.1f, (lastLyricMs + 5000L) / 1000f)
            
            val defaultStart = Math.max(0f, (firstLyricMs - 1000L) / 1000f)
            val defaultEnd = Math.min(maxSecs, defaultStart + 20f)
            
            if (maxSecs > rangeSlider.valueTo) {
                rangeSlider.valueTo = maxSecs
                rangeSlider.values = listOf(defaultStart, defaultEnd)
            } else {
                rangeSlider.values = listOf(defaultStart, defaultEnd)
                rangeSlider.valueTo = maxSecs
            }
            tvTrimTimes.text = String.format("%.1fs - %.1fs", defaultStart, defaultEnd)
            updateLyricPreviewLine(defaultStart)
        }
    }

    private fun updateLyricPreviewLine(timeSecs: Float) {
        val targetMs = (timeSecs * 1000).toLong()
        var currentText = "..."
        for (i in parsedLyricsCache.indices) {
            if (targetMs >= parsedLyricsCache[i].first) {
                currentText = parsedLyricsCache[i].second
            } else {
                break
            }
        }
        tvLyricPreview.text = "Preview: \"$currentText\""
    }

    private fun updateWysiwygPreview() {
        val textToPreview = parsedLyricsCache.firstOrNull()?.second ?: "Odyssey"
        
        val displayText = if (currentAnimStyle == 0) {
            val firstWord = textToPreview.split("\\s+".toRegex()).firstOrNull { it.isNotEmpty() } ?: textToPreview
            GlyphFontEngine.formatWordForDisplay(firstWord, currentFontStyle, 0f)
        } else {
            textToPreview
        }
        
        val offsetX = if (currentAnimStyle == 0) {
            val textWidth = GlyphFontEngine.measureTextWidth(displayText, currentFontStyle, autoScale = false)
            (25f - textWidth) / 2f
        } else {
            0f
        }
        
        val rawBytes = GlyphFontEngine.renderTextFrame(displayText, currentFontStyle, offsetX, autoScale = false)
        
        // Update teleprompter mirror and stream to hardware frame bus
        teleprompterMatrixMirror.updateFrame(rawBytes)
        GlyphFrameBus.postFrame(rawBytes)

        val bitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(625)
        for (i in 0 until 625) {
            val v = rawBytes[i].toInt() and 0xFF
            pixels[i] = Color.rgb(v, v, v)
        }
        bitmap.setPixels(pixels, 0, 25, 0, 0, 25, 25)
        
        previewImage.setImageBitmap(bitmap)
        // The bitmap is only 25x25 — without disabling the default bilinear
        // filter, ImageView smears it into a blurry blob when scaled up to
        // fill the preview bezel. Nearest-neighbor keeps it crisp, which is
        // also a more honest preview of what the actual LED grid looks like.
        (previewImage.drawable as? android.graphics.drawable.BitmapDrawable)?.paint?.isFilterBitmap = false
    }

    private fun checkAndRequestAudioMatch(track: LrcTrack) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                return
            }
        }
        attemptAutoMatchAudio(track)
    }

    private fun attemptAutoMatchAudio(track: LrcTrack) {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        
        // Simple heuristic: search for track name in TITLE
        val selection = "${MediaStore.Audio.Media.TITLE} LIKE ?"
        val selectionArgs = arrayOf("%${track.trackName}%")
        
        var foundUri: Uri? = null
        var foundName = ""
        
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val id = cursor.getLong(idIndex)
                foundName = cursor.getString(titleIndex)
                foundUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        
        if (foundUri != null) {
            selectedAudioUri = foundUri
            updateAudioStatus("Possible match: $foundName — tap to change", true)
        } else {
            updateAudioStatus("Audio: Unattached", false)
        }
    }

    private fun cancelProcessing() {
        isRendering.set(true)
        btnRender.visibility = View.VISIBLE
        btnCancel.visibility = View.GONE
        progressBar.visibility = View.GONE
        progressBar.setProgress(0)
        Snackbar.make(findViewById(android.R.id.content), "Render cancelled.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
    }

    private fun renderLyrics() {
        val lyrics = selectedTrack?.syncedLyrics ?: return
        
        isRendering.set(false)
        progressBar.visibility = View.VISIBLE
        progressBar.setProgress(0)
        btnRender.visibility = View.GONE
        btnCancel.visibility = View.VISIBLE
        successActionsContainer.visibility = View.GONE
        
        val slot = prefs.getInt("selected_slot", 1)
        val startTimeMs = (rangeSlider.values[0] * 1000).toLong()
        val endTimeMs = (rangeSlider.values[1] * 1000).toLong()
        
        VideoProcessor.processLyrics(
            context = this,
            syncedLyrics = lyrics,
            audioUri = if (switchAudio.visibility == View.VISIBLE && switchAudio.isChecked) selectedAudioUri else null,
            fontStyle = currentFontStyle,
            animationStyle = currentAnimStyle,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            targetFps = 12,
            slotIndex = slot,
            isCancelled = isRendering,
            onProgress = { prog ->
                progressBar.setProgress(prog)
            },
            onComplete = { success, error ->
                btnRender.visibility = View.VISIBLE
                btnCancel.visibility = View.GONE
                progressBar.visibility = View.GONE
                if (success) {
                    val msg = if (slot == 4) "SUCCESS! SAVED TO GALLERY." else "SUCCESS! RENDERED TO SLOT $slot."
                    Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).applyNothingStyle().show()
                    successActionsContainer.visibility = View.VISIBLE
                    sendBroadcast(Intent("com.example.odysseyglyph.RELOAD_FRAMES"))
                    
                    val sv = findViewById<ScrollView>(R.id.scrollView)
                    // Force the ScrollView and its container to immediately remeasure their bounds
                    sv.getChildAt(0).requestLayout()
                    sv.requestLayout()
                    
                    sv.postDelayed({
                        sv.smoothScrollTo(0, sv.getChildAt(0).bottom)
                    }, 100)
                } else {
                    if (error != "Cancelled by user.") {
                        Snackbar.make(findViewById(android.R.id.content), "Error: $error", Snackbar.LENGTH_LONG).applyNothingStyle().show()
                    }
                }
            }
        )
    }
}
