package com.example.odysseyglyph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * GlyphMatrixMirrorView
 *
 * Real-time 25×25 circular LED matrix mirror rendered in-app so users can see
 * their rear Glyph Matrix output without turning the phone over.
 *
 * Performance Architecture:
 * - Zero GC allocations during onDraw() (pre-allocated float arrays for diode centers and paints).
 * - Circular aperture masking to match Nothing Phone (3) rear circular matrix.
 * - Auto-subscribes to GlyphFrameBus on attach / unsubscribes on detach.
 */
class GlyphMatrixMirrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), GlyphFrameBus.FrameListener {

    companion object {
        const val MATRIX_DIM = 25
        const val TOTAL_LEDS = MATRIX_DIM * MATRIX_DIM // 625
        const val COLOR_CHASSIS = 0xFF0D0D0D.toInt()
        const val COLOR_BEZEL = 0xFF262626.toInt()
        const val COLOR_INACTIVE_DIODE = 0xFF1C1C1C.toInt()
        const val COLOR_ACTIVE_DIODE = 0xFFFFFFFF.toInt()
        const val MIN_TOUCH_DP = 48f
    }

    // Pre-allocated paints
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CHASSIS
        style = Paint.Style.FILL
    }

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_BEZEL
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val inactiveDiodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_INACTIVE_DIODE
        style = Paint.Style.FILL
    }

    private val activeDiodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_ACTIVE_DIODE
        style = Paint.Style.FILL
    }

    // Pre-allocated diode coordinates (X, Y) for 625 diodes = 1250 floats
    private val diodeCoords = FloatArray(TOTAL_LEDS * 2)
    // Pre-calculated mask: true if inside circular aperture
    private val isInsideAperture = BooleanArray(TOTAL_LEDS)
    // Current frame buffer (0..255 brightness per diode)
    private val currentFrame = ByteArray(TOTAL_LEDS)

    private var diodeRadius: Float = 0f
    private var matrixRadius: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f

    init {
        val density = resources.displayMetrics.density
        val minPx = (MIN_TOUCH_DP * density + 0.5f).toInt()
        minimumWidth = minPx
        minimumHeight = minPx
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val minPx = (MIN_TOUCH_DP * density + 0.5f).toInt()

        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)

        val desiredSize = maxOf(minPx, (140f * density + 0.5f).toInt())

        val resolvedW = when (wMode) {
            MeasureSpec.EXACTLY -> wSize
            MeasureSpec.AT_MOST -> minOf(desiredSize, wSize)
            else -> desiredSize
        }
        val resolvedH = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> minOf(desiredSize, hSize)
            else -> desiredSize
        }

        // Keep 1:1 aspect ratio
        val finalSize = maxOf(minPx, minOf(resolvedW, resolvedH))
        setMeasuredDimension(finalSize, finalSize)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        centerX = w / 2f
        centerY = h / 2f
        matrixRadius = minOf(centerX, centerY) * 0.92f

        val step = (matrixRadius * 2f) / MATRIX_DIM
        diodeRadius = step * 0.38f // Circular diode with spacing gap

        val startX = centerX - matrixRadius + step * 0.5f
        val startY = centerY - matrixRadius + step * 0.5f

        var idx = 0
        val maxDistSq = matrixRadius * matrixRadius

        for (row in 0 until MATRIX_DIM) {
            val y = startY + row * step
            for (col in 0 until MATRIX_DIM) {
                val x = startX + col * step
                diodeCoords[idx * 2] = x
                diodeCoords[idx * 2 + 1] = y

                // Check if diode falls inside the circular aperture
                val dx = x - centerX
                val dy = y - centerY
                isInsideAperture[idx] = (dx * dx + dy * dy) <= maxDistSq
                idx++
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (matrixRadius <= 0f) return

        // 1. Draw circular chassis background & bezel ring
        canvas.drawCircle(centerX, centerY, matrixRadius, chassisPaint)
        canvas.drawCircle(centerX, centerY, matrixRadius, bezelPaint)

        // 2. Draw 625 diodes with zero object allocations
        for (i in 0 until TOTAL_LEDS) {
            if (!isInsideAperture[i]) continue

            val x = diodeCoords[i * 2]
            val y = diodeCoords[i * 2 + 1]
            val rawByte = currentFrame[i].toInt() and 0xFF

            if (rawByte > 10) {
                // Diode is active: scale brightness
                activeDiodePaint.alpha = rawByte
                canvas.drawCircle(x, y, diodeRadius, activeDiodePaint)
            } else {
                // Inactive diode dot
                canvas.drawCircle(x, y, diodeRadius * 0.7f, inactiveDiodePaint)
            }
        }
    }

    override fun onGlyphFrame(frame: ByteArray) {
        if (frame.size != TOTAL_LEDS) return
        System.arraycopy(frame, 0, currentFrame, 0, TOTAL_LEDS)
        postInvalidateOnAnimation()
    }

    fun updateFrame(frame: ByteArray) {
        onGlyphFrame(frame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlyphFrameBus.subscribe(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        GlyphFrameBus.unsubscribe(this)
    }
}
