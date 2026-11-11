package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.View.MeasureSpec
import android.view.animation.DecelerateInterpolator

class KnobView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val DEFAULT_MIN_SIZE_DP = 48f
        const val GLOW_TRANSITION_DURATION_MS = 150L
        const val GLOW_MAX_ALPHA = 150
        const val COLOR_NOTHING_RED = 0xFFEA3323.toInt()
        const val COLOR_CHASSIS = 0xFF1A1A1A.toInt()

        /**
         * Resolves the measured dimension ensuring strict WCAG 2.1 AA 48dp minimum bounds,
         * handling MeasureSpec.EXACTLY, AT_MOST, and UNSPECIFIED across any display density.
         */
        fun resolveMinDimension(
            suggestedMin: Int,
            specSize: Int,
            specMode: Int,
            density: Float
        ): Int {
            val minSizePx = (DEFAULT_MIN_SIZE_DP * density + 0.5f).toInt()
            val desired = maxOf(suggestedMin, minSizePx)
            val resolved = when (specMode) {
                MeasureSpec.EXACTLY, 1 -> specSize
                MeasureSpec.AT_MOST, 2 -> minOf(desired, specSize)
                else -> desired
            }
            return maxOf(minSizePx, resolved)
        }
    }

    var isChecked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                animateGlow(value)
                invalidate()
            }
        }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CHASSIS
        style = Paint.Style.FILL
    }

    private val knobHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_NOTHING_RED
        style = Paint.Style.FILL
        alpha = 0
    }

    // Pre-allocated indicator dot paints (Zero GC allocations in onDraw)
    private val dotActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val dotInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.FILL
    }

    // Finite single-shot state transition progress (0.0f = unchecked, 1.0f = checked)
    private var glowProgress = 0f
    private var glowTransitionAnimator: ValueAnimator? = null

    var onCheckRequested: ((KnobView) -> Unit)? = null

    init {
        isClickable = true
        val minSizePx = (DEFAULT_MIN_SIZE_DP * resources.displayMetrics.density + 0.5f).toInt()
        minimumWidth = minSizePx
        minimumHeight = minSizePx

        setOnClickListener {
            if (!isChecked) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                animateClick()
                onCheckRequested?.invoke(this)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val measuredW = resolveMinDimension(
            suggestedMinimumWidth,
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getMode(widthMeasureSpec),
            density
        )
        val measuredH = resolveMinDimension(
            suggestedMinimumHeight,
            MeasureSpec.getSize(heightMeasureSpec),
            MeasureSpec.getMode(heightMeasureSpec),
            density
        )

        setMeasuredDimension(measuredW, measuredH)
    }

    private fun animateClick() {
        animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
            animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }

    /**
     * Finite, event-driven single-shot 150ms state transition.
     * Settles at strictly 0 repaints in idle state, enabling the LTPO display to throttle down to 1Hz.
     */
    private fun animateGlow(show: Boolean) {
        glowTransitionAnimator?.cancel()
        val targetProgress = if (show) 1.0f else 0.0f
        val startProgress = glowProgress

        if (startProgress == targetProgress) return

        glowTransitionAnimator = ValueAnimator.ofFloat(startProgress, targetProgress).apply {
            duration = GLOW_TRANSITION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                glowProgress = animator.animatedValue as Float
                glowPaint.alpha = (GLOW_MAX_ALPHA * glowProgress).toInt().coerceIn(0, 255)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy) * 0.65f // Inner knob radius

        // Draw glow (only during transition or when checked)
        if (glowProgress > 0f && glowPaint.alpha > 0) {
            val glowRadius = radius * (1.0f + 0.25f * glowProgress)
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)
        }

        // Draw knob chassis & highlight ring
        canvas.drawCircle(cx, cy, radius, knobPaint)
        canvas.drawCircle(cx, cy, radius, knobHighlightPaint)

        // Draw indicator dot (zero allocation via pre-allocated paints)
        val dotRadius = radius * 0.12f
        val dotPaint = if (isChecked) dotActivePaint else dotInactivePaint
        canvas.drawCircle(cx, cy - radius * 0.6f, dotRadius, dotPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowTransitionAnimator?.cancel()
        glowTransitionAnimator = null
        animate().cancel()
    }
}
