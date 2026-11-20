package com.example.odysseyglyph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * TactileWaveformScrubberView
 * Industrial audio waveform visualizer and scrubber inspired by boutique synthesizers (OP-1 / EP-133).
 * Renders discrete vertical amplitude bars with 0 GC allocations in onDraw().
 */
class TactileWaveformScrubberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EA3323") // Nothing Red
        style = Paint.Style.FILL
    }

    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222228") // Frosted track tone
        style = Paint.Style.FILL
    }

    private val playheadGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44EA3323")
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0C0C10")
        style = Paint.Style.FILL
    }

    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#18FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val barRect = RectF()
    private var progress: Float = 0f // 0.0f to 1.0f
    private var isDragging = false

    // Default synthetic waveform amplitudes (normalized 0.1f to 1.0f)
    private var amplitudes = FloatArray(48) { idx ->
        val sinVal = kotlin.math.sin(idx * 0.35f)
        val cosVal = kotlin.math.cos(idx * 0.18f)
        ((kotlin.math.abs(sinVal * cosVal) * 0.8f + 0.2f).coerceIn(0.15f, 1.0f))
    }

    private var onScrubListener: ((Float) -> Unit)? = null
    private var lastHapticBucket = -1

    fun setProgress(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(progress - clamped) > 0.001f) {
            progress = clamped
            postInvalidateOnAnimation()
        }
    }

    fun getProgress(): Float = progress

    fun setOnScrubListener(listener: (Float) -> Unit) {
        this.onScrubListener = listener
    }

    fun setAmplitudes(newAmplitudes: FloatArray) {
        if (newAmplitudes.isNotEmpty()) {
            amplitudes = newAmplitudes.copyOf()
            postInvalidateOnAnimation()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minWidth = (48 * resources.displayMetrics.density).toInt()
        val minHeight = (48 * resources.displayMetrics.density).toInt()
        val width = resolveSize(minWidth, widthMeasureSpec)
        val height = resolveSize(minHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Draw liquid glass track background
        barRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(barRect, 14f * resources.displayMetrics.density, 14f * resources.displayMetrics.density, trackBgPaint)
        canvas.drawRoundRect(barRect, 14f * resources.displayMetrics.density, 14f * resources.displayMetrics.density, trackBorderPaint)

        val count = amplitudes.size
        if (count == 0) return

        val barWidth = 3.5f * resources.displayMetrics.density
        val barGap = 2f * resources.displayMetrics.density
        val totalBarSpan = barWidth + barGap
        val contentWidth = count * totalBarSpan - barGap
        val startX = (w - contentWidth).coerceAtLeast(0f) / 2f
        val centerY = h / 2f

        val playheadX = startX + (contentWidth * progress)

        for (i in 0 until count) {
            val left = startX + (i * totalBarSpan)
            val right = left + barWidth
            val barAmp = amplitudes[i]
            val barHalfHeight = (h * 0.36f * barAmp).coerceAtLeast(2f * resources.displayMetrics.density)
            val top = centerY - barHalfHeight
            val bottom = centerY + barHalfHeight

            barRect.set(left, top, right, bottom)
            val paint = if (right <= playheadX) playedPaint else unplayedPaint
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, paint)
        }

        // Frosted playhead indicator with red ambient halo
        canvas.drawLine(playheadX, 8f, playheadX, h - 8f, playheadGlowPaint)
        canvas.drawLine(playheadX, 8f, playheadX, h - 8f, playheadPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateProgressFromTouch(event.x)
                vibrateDetent()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateProgressFromTouch(event.x)
                    checkHapticDetent()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    updateProgressFromTouch(event.x)
                    vibrateClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromTouch(touchX: Float) {
        val w = width.toFloat()
        if (w <= 0f) return
        val count = amplitudes.size
        val barWidth = 4f * resources.displayMetrics.density
        val barGap = 2f * resources.displayMetrics.density
        val totalBarSpan = barWidth + barGap
        val contentWidth = count * totalBarSpan - barGap
        val startX = (w - contentWidth).coerceAtLeast(0f) / 2f

        val fraction = ((touchX - startX) / contentWidth).coerceIn(0f, 1f)
        progress = fraction
        onScrubListener?.invoke(progress)
        postInvalidateOnAnimation()
    }

    private fun checkHapticDetent() {
        val bucket = (progress * 20).toInt() // Detent tick every 5%
        if (bucket != lastHapticBucket) {
            lastHapticBucket = bucket
            vibrateDetent()
        }
    }
}
