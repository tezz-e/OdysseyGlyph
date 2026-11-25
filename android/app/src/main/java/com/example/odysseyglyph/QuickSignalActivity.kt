package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

class QuickSignalActivity : AppCompatActivity(), QuickSignalService.StateListener {

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var matrixMirror: GlyphMatrixMirrorView
    private lateinit var signalStatusDot: View
    private lateinit var tvSignalStatus: TextView
    private lateinit var tvSignalCountdown: TextView
    private lateinit var tvSignalModeDesc: TextView

    private lateinit var etSignalText: TextInputEditText
    private lateinit var symbolsContainer: LinearLayout
    private lateinit var presetsContainer: LinearLayout
    private lateinit var fontSegmentedBar: LiquidGlassSegmentedBar
    private lateinit var durationSegmentedBar: LiquidGlassSegmentedBar
    private lateinit var btnToggleSignal: MaterialButton

    private var currentFontStyle: GlyphFontEngine.FontStyle = GlyphFontEngine.FontStyle.BLOCK_BOLD
    private var selectedDurationSec = 15
    private var previewAnimator: ValueAnimator? = null
    private var frameBusSubscription: ((ByteArray) -> Unit)? = null

    private val curatedSymbols = listOf("♥", "↑", "↓", "←", "→", "✓", "✕", "★", "☺", "!", "?")
    private val defaultPresets = listOf("WAIT", "BRB", "CALL ME", "YES", "NO", "ON MY WAY")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_quick_signal)

        val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBar, applyTop = true, applyBottom = false)
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollView)
        WindowInsetsUtil.applySystemBarsPadding(scrollView, applyTop = false, applyBottom = true)

        prefs = getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
        selectedDurationSec = savedInstanceState?.getInt("selected_duration_sec", 15) ?: 15

        bindViews()
        setupCuratedSymbols()
        setupPresets()
        setupFontSelector()
        setupDurationSelector()

        // Listen for live matrix frames when signal is actively streaming (subscribed in onStart / unsubscribed in onStop)
        frameBusSubscription = { frame ->
            if (QuickSignalService.isRunning) {
                runOnUiThread { matrixMirror.updateFrame(frame) }
            }
        }

        updateLocalPreview()
    }

    private fun bindViews() {
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        TouchSpringAnimator.attach(btnBack)
        btnBack.setOnClickListener { NavigationHelper.finishWithPop(this) }

        matrixMirror = findViewById(R.id.matrixMirror)
        signalStatusDot = findViewById(R.id.signalStatusDot)
        tvSignalStatus = findViewById(R.id.tvSignalStatus)
        tvSignalCountdown = findViewById(R.id.tvSignalCountdown)
        tvSignalModeDesc = findViewById(R.id.tvSignalModeDesc)

        etSignalText = findViewById(R.id.etSignalText)
        symbolsContainer = findViewById(R.id.symbolsContainer)
        presetsContainer = findViewById(R.id.presetsContainer)
        fontSegmentedBar = findViewById(R.id.fontSegmentedBar)
        durationSegmentedBar = findViewById(R.id.durationSegmentedBar)
        btnToggleSignal = findViewById(R.id.btnToggleSignal)

        TouchSpringAnimator.attach(btnToggleSignal)

        val lastSignal = prefs.getString("last_quick_signal", "WAIT") ?: "WAIT"
        etSignalText.setText(lastSignal)

        etSignalText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!QuickSignalService.isRunning) {
                    updateLocalPreview()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnToggleSignal.setOnClickListener {
            if (QuickSignalService.isRunning) {
                stopQuickSignal()
            } else {
                startQuickSignal()
            }
        }
    }

    private fun setupCuratedSymbols() {
        symbolsContainer.removeAllViews()
        val geist = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_geist)
        val currentText = etSignalText.text?.toString()?.trim() ?: ""

        for (sym in curatedSymbols) {
            val isSelected = sym == currentText
            val tv = TextView(this).apply {
                text = sym
                textSize = 17f
                typeface = geist
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#CCCCCC"))
                gravity = Gravity.CENTER
                setBackgroundResource(if (isSelected) R.drawable.bg_glass_pill_selected else R.drawable.bg_glass_pill)
                setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dpToPx(8) }
                layoutParams = lp
                isClickable = true
                isFocusable = true
            }
            TouchSpringAnimator.attach(tv)
            tv.setOnClickListener {
                etSignalText.setText(sym)
                etSignalText.setSelection(sym.length)
                it.vibrateTick()
                refreshPillSelections()
                if (!QuickSignalService.isRunning) {
                    updateLocalPreview()
                }
            }
            symbolsContainer.addView(tv)
        }
    }

    private fun setupPresets() {
        presetsContainer.removeAllViews()
        val geist = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_geist)
        val currentText = etSignalText.text?.toString()?.trim() ?: ""
        val recents = loadRecentSignals()
        val combined = (recents + defaultPresets).distinct().take(6)

        for (preset in combined) {
            val isSelected = preset.equals(currentText, ignoreCase = true)
            val tv = TextView(this).apply {
                text = preset
                textSize = 12f
                typeface = geist
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#CCCCCC"))
                gravity = Gravity.CENTER
                setBackgroundResource(if (isSelected) R.drawable.bg_glass_pill_selected else R.drawable.bg_glass_pill)
                setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dpToPx(8) }
                layoutParams = lp
                isClickable = true
                isFocusable = true
            }
            TouchSpringAnimator.attach(tv)
            tv.setOnClickListener {
                etSignalText.setText(preset)
                etSignalText.setSelection(preset.length)
                it.vibrateTick()
                refreshPillSelections()
                if (!QuickSignalService.isRunning) {
                    updateLocalPreview()
                }
            }
            presetsContainer.addView(tv)
        }
    }

    private fun refreshPillSelections() {
        val currentText = etSignalText.text?.toString()?.trim() ?: ""
        val geist = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_geist)

        for (i in 0 until symbolsContainer.childCount) {
            val tv = symbolsContainer.getChildAt(i) as? TextView ?: continue
            tv.typeface = geist
            val isSelected = tv.text.toString().trim() == currentText
            if (isSelected) {
                tv.setBackgroundResource(R.drawable.bg_glass_pill_selected)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_glass_pill)
                tv.setTextColor(Color.parseColor("#CCCCCC"))
            }
        }

        for (i in 0 until presetsContainer.childCount) {
            val tv = presetsContainer.getChildAt(i) as? TextView ?: continue
            tv.typeface = geist
            val isSelected = tv.text.toString().trim().equals(currentText, ignoreCase = true)
            if (isSelected) {
                tv.setBackgroundResource(R.drawable.bg_glass_pill_selected)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.setBackgroundResource(R.drawable.bg_glass_pill)
                tv.setTextColor(Color.parseColor("#CCCCCC"))
            }
        }
    }

    private fun setupFontSelector() {
        val savedStyleInt = prefs.getInt("quick_signal_font_style", 2)
        currentFontStyle = when (savedStyleInt) {
            0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
            1 -> GlyphFontEngine.FontStyle.SMOOTH
            2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
            else -> GlyphFontEngine.FontStyle.BLOCK_BOLD
        }
        fontSegmentedBar.setSegments(listOf("Pixel", "Smooth", "Blocky"), initialIndex = savedStyleInt)
        fontSegmentedBar.onSegmentSelected = { idx, _ ->
            currentFontStyle = when (idx) {
                0 -> GlyphFontEngine.FontStyle.PIXEL_TINY
                1 -> GlyphFontEngine.FontStyle.SMOOTH
                2 -> GlyphFontEngine.FontStyle.BLOCK_BOLD
                else -> GlyphFontEngine.FontStyle.BLOCK_BOLD
            }
            prefs.edit().putInt("quick_signal_font_style", idx).apply()
            if (!QuickSignalService.isRunning) {
                updateLocalPreview()
            }
        }
    }

    private fun setupDurationSelector() {
        val initialIdx = when (selectedDurationSec) {
            60 -> 1
            -1 -> 2
            else -> 0
        }
        durationSegmentedBar.setSegments(listOf("15 SEC", "1 MIN", "UNTIL CLEARED"), initialIndex = initialIdx)
        durationSegmentedBar.onSegmentSelected = { idx, _ ->
            selectedDurationSec = when (idx) {
                0 -> 15
                1 -> 60
                else -> -1
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_duration_sec", selectedDurationSec)
    }

    private fun updateLocalPreview() {
        previewAnimator?.cancel()
        refreshPillSelections()
        val text = etSignalText.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) {
            matrixMirror.updateFrame(ByteArray(QuickSignalRenderer.TOTAL_PIXELS))
            tvSignalModeDesc.text = "Enter text or select a symbol"
            return
        }

        val isStationary = text.length <= 4 || QuickSignalRenderer.isCuratedSymbol(text)
        if (isStationary) {
            tvSignalModeDesc.text = "Stationary 25×25 Display"
            val frame = QuickSignalRenderer.renderFrame(text, currentFontStyle)
            matrixMirror.updateFrame(frame)
        } else {
            tvSignalModeDesc.text = "Marquee Scrolling at ~24 FPS"
            val textWidth = QuickSignalRenderer.measureMarqueeWidth(text, currentFontStyle)
            val startX = 25f
            val endX = -textWidth - 4f

            val durationMs = ((textWidth + 29f) * 45f).toLong().coerceIn(1200L, 4500L)
            previewAnimator = ValueAnimator.ofFloat(startX, endX).apply {
                duration = durationMs
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { va ->
                    if (!QuickSignalService.isRunning) {
                        val offset = va.animatedValue as Float
                        val frame = QuickSignalRenderer.renderFrame(text, currentFontStyle, offset)
                        matrixMirror.updateFrame(frame)
                    }
                }
                start()
            }
        }
    }

    private fun startQuickSignal() {
        val text = etSignalText.text?.toString()?.trim() ?: ""
        if (text.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Please enter a message or choose a symbol.", Snackbar.LENGTH_SHORT)
                .applyNothingStyle()
                .show()
            return
        }

        prefs.edit().putString("last_quick_signal", text).apply()
        saveRecentSignal(text)
        setupPresets()

        val fontStyleInt = when (currentFontStyle) {
            GlyphFontEngine.FontStyle.PIXEL_TINY -> 0
            GlyphFontEngine.FontStyle.SMOOTH -> 1
            GlyphFontEngine.FontStyle.BLOCK_BOLD -> 2
        }

        val intent = Intent(this, QuickSignalService::class.java).apply {
            action = QuickSignalService.ACTION_START_SIGNAL
            putExtra(QuickSignalService.EXTRA_TEXT, text)
            putExtra(QuickSignalService.EXTRA_DURATION_SEC, selectedDurationSec)
            putExtra(QuickSignalService.EXTRA_FONT_STYLE, fontStyleInt)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        btnToggleSignal.vibrateTick()
    }

    private fun stopQuickSignal() {
        val intent = Intent(this, QuickSignalService::class.java).apply {
            action = QuickSignalService.ACTION_STOP_SIGNAL
        }
        startService(intent)
        btnToggleSignal.vibrateTick()
    }

    override fun onStart() {
        super.onStart()
        frameBusSubscription?.let {
            GlyphFrameBus.unsubscribe(it)
            GlyphFrameBus.subscribe(it)
        }
    }

    override fun onStop() {
        super.onStop()
        frameBusSubscription?.let { GlyphFrameBus.unsubscribe(it) }
    }

    override fun onResume() {
        super.onResume()
        QuickSignalService.addListener(this)
        matrixMirror.post {
            if (!QuickSignalService.isRunning) {
                updateLocalPreview()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        QuickSignalService.removeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        previewAnimator?.cancel()
        frameBusSubscription?.let { GlyphFrameBus.unsubscribe(it) }
    }

    override fun onStateChanged(active: Boolean, text: String, remaining: Int) {
        runOnUiThread {
            val previewCard = findViewById<View>(R.id.previewCard)
            if (active) {
                previewAnimator?.cancel()
                previewCard?.setBackgroundResource(R.drawable.bg_glass_card_active)
                btnToggleSignal.text = "CLEAR SIGNAL"
                btnToggleSignal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#140707"))
                btnToggleSignal.strokeColor = ColorStateList.valueOf(Color.parseColor("#EA3323"))
                btnToggleSignal.strokeWidth = dpToPx(1)
                btnToggleSignal.setTextColor(Color.parseColor("#EA3323"))

                signalStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA3323"))
                tvSignalStatus.text = "SHOWING ON REAR MATRIX"
                tvSignalStatus.setTextColor(Color.parseColor("#EA3323"))

                if (remaining > 0) {
                    tvSignalCountdown.visibility = View.VISIBLE
                    tvSignalCountdown.text = "${remaining}s remaining"
                } else {
                    tvSignalCountdown.visibility = View.VISIBLE
                    tvSignalCountdown.text = "Until cleared"
                }
            } else {
                previewCard?.setBackgroundResource(R.drawable.bg_glass_card)
                btnToggleSignal.text = "SHOW SIGNAL"
                btnToggleSignal.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA3323"))
                btnToggleSignal.strokeColor = ColorStateList.valueOf(Color.parseColor("#FF6655"))
                btnToggleSignal.strokeWidth = dpToPx(1)
                btnToggleSignal.setTextColor(Color.WHITE)

                signalStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#888888"))
                tvSignalStatus.text = "LIVE 25×25 PREVIEW"
                tvSignalStatus.setTextColor(Color.parseColor("#CCCCCC"))
                tvSignalCountdown.visibility = View.GONE

                updateLocalPreview()
            }
        }
    }

    private fun saveRecentSignal(text: String) {
        val current = loadRecentSignals().toMutableList()
        current.remove(text)
        current.add(0, text)
        val trimmed = current.take(6)
        prefs.edit().putString("recent_signals", trimmed.joinToString(";;;")).apply()
    }

    private fun loadRecentSignals(): List<String> {
        val raw = prefs.getString("recent_signals", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(";;;").filter { it.isNotBlank() }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
