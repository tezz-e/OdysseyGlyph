package com.example.odysseyglyph

import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * GlyphDynamicPodView
 *
 * Persistent hardware pod anchored to the front camera punch-hole cutout.
 * Follows Nothing OS 3.0 dynamic island / physical pod mechanics.
 *
 * States:
 * 1. RESTING: Compact pill (130×36dp) with pulsating Nothing Red indicator dot and 3 micro EQ bars.
 * 2. EXPANDED: Springs open into full dashboard card housing 25×25 matrix mirror, track title, and controls.
 */
class GlyphDynamicPodView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class PodState { RESTING, EXPANDED }

    var podState: PodState = PodState.RESTING
        private set

    var trackTitle: String = "LIVE LYRICS ACTIVE"
        set(value) {
            field = value
            triggerCipherScramble(value)
        }

    private var displayTitle: String = trackTitle

    // Pre-allocated paints
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D0D0D")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#262626")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val redDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EA3323")
        style = Paint.Style.FILL
    }

    private val eqBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        isFakeBoldText = true
    }

    private val podRect = RectF()
    private var morphProgress = 0.0f // 0f = resting, 1f = expanded

    // Spring animation for state morphing
    private val springAnim: SpringAnimation

    init {
        isClickable = true
        isFocusable = true

        val density = resources.displayMetrics.density
        minimumHeight = (36f * density + 0.5f).toInt()
        minimumWidth = (130f * density + 0.5f).toInt()

        val force = SpringForce().apply {
            stiffness = 380f
            dampingRatio = 0.78f
            finalPosition = 0f
        }

        springAnim = SpringAnimation(this, object : androidx.dynamicanimation.animation.FloatPropertyCompat<GlyphDynamicPodView>("morph") {
            override fun getValue(view: GlyphDynamicPodView): Float = view.morphProgress
            override fun setValue(view: GlyphDynamicPodView, value: Float) {
                view.morphProgress = value
                view.requestLayout()
                view.invalidate()
            }
        }).setSpring(force)
    }

    fun toggleState() {
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        podState = if (podState == PodState.RESTING) PodState.EXPANDED else PodState.RESTING
        val target = if (podState == PodState.EXPANDED) 1.0f else 0.0f
        springAnim.animateToFinalPosition(target)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            toggleState()
            return true
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val restingW = 140f * density
        val restingH = 40f * density
        val expandedW = 320f * density
        val expandedH = 120f * density

        val curW = restingW + (expandedW - restingW) * morphProgress
        val curH = restingH + (expandedH - restingH) * morphProgress

        setMeasuredDimension(curW.toInt(), curH.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        podRect.set(2f, 2f, w - 2f, h - 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val radius = minOf(podRect.width(), podRect.height()) * 0.45f
        canvas.drawRoundRect(podRect, radius, radius, chassisPaint)
        canvas.drawRoundRect(podRect, radius, radius, borderPaint)

        val cy = podRect.centerY()

        if (morphProgress < 0.5f) {
            // Resting state: [ ●  LIVE LYRICS  ı ı ı ]
            val dotX = podRect.left + 24f
            canvas.drawCircle(dotX, cy, 6f, redDotPaint)

            canvas.drawText(displayTitle.take(16), dotX + 16f, cy + 9f, textPaint)

            // Micro EQ bars
            val eqX = podRect.right - 40f
            canvas.drawRect(eqX, cy - 8f, eqX + 4f, cy + 8f, eqBarPaint)
            canvas.drawRect(eqX + 8f, cy - 14f, eqX + 12f, cy + 8f, eqBarPaint)
            canvas.drawRect(eqX + 16f, cy - 5f, eqX + 20f, cy + 8f, eqBarPaint)
        } else {
            // Expanded state: Title and matrix indication
            val left = podRect.left + 24f
            canvas.drawCircle(left, podRect.top + 28f, 7f, redDotPaint)
            canvas.drawText(displayTitle, left + 20f, podRect.top + 36f, textPaint)

            // Expanded status text
            textPaint.textSize = 22f
            canvas.drawText("[MATRIX SYNC ACTIVE — 25x25 circular]", left, podRect.bottom - 24f, textPaint)
            textPaint.textSize = 28f
        }
    }

    private fun triggerCipherScramble(finalText: String) {
        val cipherChars = "■▲▼◆0123456789ABCDEF"
        val animator = ValueAnimator.ofInt(0, 8).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val step = anim.animatedValue as Int
                if (step >= 7) {
                    displayTitle = finalText
                } else {
                    displayTitle = finalText.map { ch ->
                        if (ch.isWhitespace()) ' ' else cipherChars.random()
                    }.joinToString("")
                }
                invalidate()
            }
        }
        animator.start()
    }
}
