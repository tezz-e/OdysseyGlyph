package com.example.odysseyglyph

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * SegmentedPillSlider
 *
 * Chunky tactile pill slider with discrete step notches and haptic detent ticks.
 * Designed for Nothing Phone (3) hardware console ergonomics.
 *
 * Capabilities:
 * - 48dp minimum touch target enforcement.
 * - Haptic detent feedback on segment crossings.
 * - Physics spring bounce on touch press/release.
 * - Zero GC allocations during onDraw().
 */
class SegmentedPillSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MIN_HEIGHT_DP = 48f
        const val DEFAULT_STEPS = 5 // 0%, 25%, 50%, 75%, 100%
        const val COLOR_TRACK = 0xFF141414.toInt()
        const val COLOR_PROGRESS = 0xFFFFFFFF.toInt()
        const val COLOR_THUMB = 0xFFFFFFFF.toInt()
        const val COLOR_BORDER = 0xFF2A2A2A.toInt()
    }

    var steps: Int = DEFAULT_STEPS
        set(value) {
            field = maxOf(2, value)
            invalidate()
        }

    var progress: Float = 0.5f // 0.0f .. 1.0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                onProgressChanged?.invoke(field)
                invalidate()
            }
        }

    var onProgressChanged: ((Float) -> Unit)? = null

    // Pre-allocated paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TRACK
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_PROGRESS
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_BORDER
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }

    private val trackRect = RectF()
    private val progressRect = RectF()
    private val thumbRect = RectF()

    private var lastHapticStep: Int = -1
    private var isDragging: Boolean = false

    // Spring animation for touch scale feedback
    private val scaleSpringX: SpringAnimation
    private val scaleSpringY: SpringAnimation

    init {
        isClickable = true
        isFocusable = true

        val density = resources.displayMetrics.density
        minimumHeight = (MIN_HEIGHT_DP * density + 0.5f).toInt()

        val force = SpringForce().apply {
            stiffness = 380f
            dampingRatio = 0.78f
            finalPosition = 1.0f
        }
        scaleSpringX = SpringAnimation(this, DynamicAnimation.SCALE_X).setSpring(force)
        scaleSpringY = SpringAnimation(this, DynamicAnimation.SCALE_Y).setSpring(force)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val minH = (MIN_HEIGHT_DP * density + 0.5f).toInt()
        val minW = (120f * density + 0.5f).toInt()

        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)

        val resolvedH = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> minOf(minH, hSize)
            else -> minH
        }

        setMeasuredDimension(maxOf(minW, wSize), maxOf(minH, resolvedH))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = 4f
        trackRect.set(pad, pad, w - pad, h - pad)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val radius = trackRect.height() / 2f

        // 1. Background pill track
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        canvas.drawRoundRect(trackRect, radius, radius, borderPaint)

        // 2. Filled progress pill
        val activeWidth = trackRect.width() * progress
        if (activeWidth > 0f) {
            progressRect.set(
                trackRect.left,
                trackRect.top,
                trackRect.left + activeWidth,
                trackRect.bottom
            )
            canvas.drawRoundRect(progressRect, radius, radius, progressPaint)
        }

        // 3. Step notches
        val stepInterval = trackRect.width() / (steps - 1)
        val notchRadius = 2.5f
        for (i in 1 until steps - 1) {
            val nx = trackRect.left + i * stepInterval
            canvas.drawCircle(nx, trackRect.centerY(), notchRadius, notchPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                scaleSpringX.animateToFinalPosition(0.97f)
                scaleSpringY.animateToFinalPosition(0.97f)
                updateFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateFromTouch(event.x)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                scaleSpringX.animateToFinalPosition(1.0f)
                scaleSpringY.animateToFinalPosition(1.0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateFromTouch(touchX: Float) {
        val usableWidth = trackRect.width()
        if (usableWidth <= 0f) return

        val fraction = ((touchX - trackRect.left) / usableWidth).coerceIn(0f, 1f)
        progress = fraction

        // Check if crossed a discrete step notch
        val currentStep = (fraction * (steps - 1) + 0.5f).toInt()
        if (currentStep != lastHapticStep) {
            lastHapticStep = currentStep
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
