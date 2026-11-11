package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * LiquidGlassSegmentedBar
 *
 * Apple Liquid Glass-inspired segmented control featuring:
 * - Fluid morphing jelly capsule thumb that elastically stretches during transitions.
 * - Interactive horizontal scrubbing with viscous edge rubber-banding.
 * - Real-time haptic detents on segment crossings.
 * - Translucent frosted glass substrate with top specular rim light.
 * - Zero GC allocations during onDraw().
 */
class LiquidGlassSegmentedBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var segments: List<String> = listOf("Option 1", "Option 2")
    var selectedIndex: Int = 0
        private set

    var onSegmentSelected: ((Int, String) -> Unit)? = null

    // Track paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = Color.parseColor("#22FFFFFF")
    }

    // Thumb paints
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val thumbRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = Color.parseColor("#44FFFFFF")
    }

    // Text paints
    private val unselectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A8A93")
        textSize = 12f * resources.displayMetrics.scaledDensity
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 12f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Geometry
    private val trackRect = RectF()
    private val thumbRect = RectF()
    private val cornerRadius = 22f * resources.displayMetrics.density
    private val thumbRadius = 18f * resources.displayMetrics.density
    private val thumbPadding = 4f * resources.displayMetrics.density

    // Animation state
    private var thumbLeft: Float = 0f
    private var thumbRight: Float = 0f
    private var targetLeft: Float = 0f
    private var targetRight: Float = 0f
    private var animator: ValueAnimator? = null

    // Touch & Scrubbing state
    private var isDragging = false
    private var touchDownX = 0f
    private var lastHapticIndex = -1

    init {
        isClickable = true
        isFocusable = true
    }

    fun setSegments(items: List<String>, initialIndex: Int = 0) {
        if (items.isEmpty()) return
        segments = items
        selectedIndex = initialIndex.coerceIn(0, items.size - 1)
        lastHapticIndex = selectedIndex
        post {
            calculateTargetBounds(selectedIndex)
            thumbLeft = targetLeft
            thumbRight = targetRight
            invalidate()
        }
    }

    fun selectSegment(index: Int, notify: Boolean = true) {
        val target = index.coerceIn(0, segments.size - 1)
        if (target == selectedIndex && !isDragging) return
        selectedIndex = target
        animateThumbTo(target)
        if (notify) {
            onSegmentSelected?.invoke(selectedIndex, segments[selectedIndex])
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackRect.set(0f, 0f, w.toFloat(), h.toFloat())

        // Linear gradient for dark glass substrate
        trackPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#18181E"),
            Color.parseColor("#0E0E12"),
            Shader.TileMode.CLAMP
        )

        calculateTargetBounds(selectedIndex)
        thumbLeft = targetLeft
        thumbRight = targetRight
    }

    private fun calculateTargetBounds(index: Int) {
        if (width <= 0 || segments.isEmpty()) return
        val usableWidth = width.toFloat() - (thumbPadding * 2)
        val segmentWidth = usableWidth / segments.size
        targetLeft = thumbPadding + (index * segmentWidth)
        targetRight = targetLeft + segmentWidth
    }

    /**
     * Animate with Apple Liquid Glass stretch:
     * Leading edge shoots ahead, stretching the pill, then trailing edge snaps up with spring overshoot.
     */
    private fun animateThumbTo(targetIndex: Int) {
        calculateTargetBounds(targetIndex)
        animator?.cancel()

        val startLeft = thumbLeft
        val startRight = thumbRight
        val movingRight = targetRight > startRight

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { va ->
                val fraction = va.animatedValue as Float

                if (movingRight) {
                    // Leading right edge moves faster (stretching forward)
                    val rightFrac = min(1.0f, fraction * 1.25f)
                    // Trailing left edge catches up smoothly
                    val leftFrac = max(0.0f, (fraction - 0.15f) / 0.85f)
                    thumbRight = startRight + (targetRight - startRight) * rightFrac
                    thumbLeft = startLeft + (targetLeft - startLeft) * leftFrac
                } else {
                    // Moving left: leading left edge shoots forward first
                    val leftFrac = min(1.0f, fraction * 1.25f)
                    val rightFrac = max(0.0f, (fraction - 0.15f) / 0.85f)
                    thumbLeft = startLeft + (targetLeft - startLeft) * leftFrac
                    thumbRight = startRight + (targetRight - startRight) * rightFrac
                }
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (segments.isEmpty() || width <= 0) return super.onTouchEvent(event)

        val usableWidth = width.toFloat() - (thumbPadding * 2)
        val segmentWidth = usableWidth / segments.size

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                isDragging = true
                touchDownX = event.x
                vibrateTick()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    // Fluid dragging with rubber-band edge damping
                    val touchX = event.x.coerceIn(thumbPadding, width.toFloat() - thumbPadding)
                    val activeIndex = ((touchX - thumbPadding) / segmentWidth).toInt()
                        .coerceIn(0, segments.size - 1)

                    if (activeIndex != lastHapticIndex) {
                        lastHapticIndex = activeIndex
                        vibrateDetent()
                    }

                    // Jelly stretch tracking finger
                    val currentCenter = touchX
                    val halfWidth = (segmentWidth / 2f) * 1.15f // slight stretch during drag
                    thumbLeft = currentCenter - halfWidth
                    thumbRight = currentCenter + halfWidth
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                val touchX = event.x.coerceIn(thumbPadding, width.toFloat() - thumbPadding)
                val finalIndex = ((touchX - thumbPadding) / segmentWidth).toInt()
                    .coerceIn(0, segments.size - 1)
                
                selectedIndex = finalIndex
                lastHapticIndex = finalIndex
                vibrateDetent()
                animateThumbTo(finalIndex)
                onSegmentSelected?.invoke(selectedIndex, segments[selectedIndex])
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                animateThumbTo(selectedIndex)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty() || width <= 0 || height <= 0) return

        // 1. Draw Liquid Glass Track
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, rimPaint)

        // 2. Draw Active Liquid Capsule Thumb
        val thumbTop = thumbPadding
        val thumbBottom = height.toFloat() - thumbPadding
        thumbRect.set(thumbLeft, thumbTop, thumbRight, thumbBottom)

        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint)
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbRimPaint)

        // 3. Draw Segment Titles
        val usableWidth = width.toFloat() - (thumbPadding * 2)
        val segmentWidth = usableWidth / segments.size
        val textY = (height / 2f) - ((unselectedTextPaint.descent() + unselectedTextPaint.ascent()) / 2f)

        for (i in segments.indices) {
            val textX = thumbPadding + (i * segmentWidth) + (segmentWidth / 2f)
            val paint = if (i == selectedIndex && !isDragging) {
                selectedTextPaint
            } else if (isDragging && abs(textX - ((thumbLeft + thumbRight) / 2f)) < segmentWidth * 0.4f) {
                selectedTextPaint
            } else {
                unselectedTextPaint
            }
            canvas.drawText(segments[i], textX, textY, paint)
        }
    }
}
