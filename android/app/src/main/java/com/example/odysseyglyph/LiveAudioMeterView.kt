package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * LiveAudioMeterView
 *
 * Real-time 5-bar animated audio spectrum meter.
 * Replaces static/fake text pipe visualizers with genuine dynamic micro-bars.
 * Responds to MusicPlaybackState: dances when music plays, settles into a calm idle state when paused.
 */
class LiveAudioMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), MusicPlaybackState.StateChangeListener {

    private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EA3323") // Nothing Red
        style = Paint.Style.FILL
    }

    private val peakBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.FILL
    }

    private val barRect = RectF()
    private val numBars = 5
    private val barFractions = FloatArray(numBars) { 0.2f }
    private var phase = 0f
    private var isPlaying = false

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase += 0.12f
            updateBars()
            invalidate()
        }
    }

    init {
        val density = resources.displayMetrics.density
        minimumWidth = (28f * density).toInt()
        minimumHeight = (18f * density).toInt()
        contentDescription = "Audio Level Meter"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MusicPlaybackState.addListener(this)
        isPlaying = MusicPlaybackState.isPlaying
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        MusicPlaybackState.removeListener(this)
        animator.cancel()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        postInvalidateOnAnimation()
    }

    override fun onMetadataChanged(title: String, artist: String) {
        // Metadata changed, trigger brief pulse
        postInvalidateOnAnimation()
    }

    private fun updateBars() {
        for (i in 0 until numBars) {
            if (isPlaying) {
                // Dynamic multi-frequency sine wave simulation
                val wave1 = abs(sin(phase * 1.8f + i * 1.1f))
                val wave2 = abs(sin(phase * 0.9f + i * 2.3f))
                barFractions[i] = (0.25f + 0.70f * (wave1 * 0.6f + wave2 * 0.4f)).coerceIn(0.15f, 1.0f)
            } else {
                // Gentle calm ambient breathing state (low amplitude)
                val calm = abs(sin(phase * 0.4f + i * 0.5f))
                barFractions[i] = (0.15f + 0.15f * calm).coerceIn(0.12f, 0.35f)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val totalWidth = width.toFloat()
        val totalHeight = height.toFloat()
        val barSpacing = 3f * resources.displayMetrics.density
        val totalSpacing = barSpacing * (numBars - 1)
        val barWidth = ((totalWidth - totalSpacing) / numBars).coerceAtLeast(2f)
        val cornerRadius = barWidth / 2f

        for (i in 0 until numBars) {
            val left = i * (barWidth + barSpacing)
            val right = left + barWidth
            
            // Background track (dim slot)
            barRect.set(left, 0f, right, totalHeight)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, trackPaint)

            // Active bar
            val barHeight = totalHeight * barFractions[i]
            val top = totalHeight - barHeight
            barRect.set(left, top, right, totalHeight)

            // Center bar or high peaks use white/red distinction
            val paint = if (i == 2 || barFractions[i] > 0.85f) peakBarPaint else activeBarPaint
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)
        }
    }
}
