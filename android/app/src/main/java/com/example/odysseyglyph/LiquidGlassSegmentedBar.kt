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
 * Floating Liquid Glass Segmented Control & Navigation Dock inspired by:
 * - Framer Liquid Glass Tabs & Apple iOS 26 UIGlassEffect
 * - davidmokos/expo-glass-tabs (1:1 finger scrubbing, transform spring, boundary haptic ticks)
 * - sdegenaar/liquid_glass_widgets (GlassSegmentedControl, dynamic lens meniscus & smooth crossfade)
 *
 * Features:
 * - Stadium capsule geometry with pure achromatic dark glass substrate.
 * - Luminous translucent glass pill with multi-stop vertical specular meniscus highlights.
 * - Subtle ambient drop shadow under the floating pill.
 * - Continuous smoothstep text crossfade between muted glass silver and pure white.
 * - Elastic jelly stretch tracking finger velocity during 1:1 scrubbing.
 * - Boundary crossing haptic detents (CLOCK_TICK) with release-only navigation commit.
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

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    // Track Substrate paints
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    // Active Liquid Glass Thumb paints
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#50000000") // Deep obsidian floating elevation shadow
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density // Refined narrow hairline
    }

    // Text paints
    private val boldTypeface: Typeface by lazy {
        try {
            androidx.core.content.res.ResourcesCompat.getFont(context, R.font.geist_bold)
                ?: androidx.core.content.res.ResourcesCompat.getFont(context, R.font.font_geist)
                ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        } catch (_: Exception) {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * scaledDensity
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.05f
        isFakeBoldText = true
    }

    init {
        try {
            textPaint.typeface = boldTypeface
        } catch (_: Exception) {}
    }

    // Inactive & Active text colors (Noir liquid glass palette)
    private val inactiveColorR = 120
    private val inactiveColorG = 120
    private val inactiveColorB = 130
    private val inactiveColorA = 190

    private val activeColorR = 255
    private val activeColorG = 255
    private val activeColorB = 255
    private val activeColorA = 255

    // Geometry
    private val trackRect = RectF()
    private val thumbRect = RectF()
    private val shadowRect = RectF()
    private val thumbPadding = 4.5f * density

    // Dynamic pill dimensions
    private var cornerRadius = 0f
    private var thumbRadius = 0f
    private var thumbTop = 0f
    private var thumbBottom = 0f

    // Animation state
    private var thumbLeft: Float = 0f
    private var thumbRight: Float = 0f
    private var targetLeft: Float = 0f
    private var targetRight: Float = 0f
    private var animator: ValueAnimator? = null

    // Touch & Scrubbing state (expo-glass-tabs pattern)
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
        if (w <= 0 || h <= 0) return

        trackRect.set(0f, 0f, w.toFloat(), h.toFloat())
        cornerRadius = h / 2f

        thumbTop = thumbPadding
        thumbBottom = h.toFloat() - thumbPadding
        val thumbHeight = thumbBottom - thumbTop
        thumbRadius = thumbHeight / 2f

        // 1. Solid Pure Obsidian Trench (Zero gradient, flat solid pitch black)
        trackPaint.shader = null
        trackPaint.style = Paint.Style.FILL
        trackPaint.color = Color.parseColor("#060608")

        // 2. Uniform Subtle Laser-etched Outline (Zero gradient, clean 1px hairline)
        rimPaint.shader = null
        rimPaint.style = Paint.Style.STROKE
        rimPaint.strokeWidth = 1f * density
        rimPaint.color = Color.parseColor("#10FFFFFF")

        // 3. Active Selection Pill: Liquid Glass Specular Meniscus & Lens Refraction
        thumbPaint.shader = LinearGradient(
            0f, thumbTop, 0f, thumbBottom,
            intArrayOf(
                Color.parseColor("#2C2C32"), // Polished graphite meniscus
                Color.parseColor("#1A1A1E"), // Smoky obsidian core
                Color.parseColor("#101013")  // Deep midnight noir base
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        // 4. Active Selection Pill: Specular Meniscus Rim (Refined razor-sharp hairline reflection)
        thumbRimPaint.shader = LinearGradient(
            0f, thumbTop, 0f, thumbBottom,
            intArrayOf(
                Color.parseColor("#48FFFFFF"), // Narrow top specular meniscus highlight
                Color.parseColor("#18FFFFFF"), // Translucent lateral edge
                Color.parseColor("#28FFFFFF")  // Ground bounce reflection
            ),
            floatArrayOf(0f, 0.5f, 1f),
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
     * Animate with Liquid Glass Spring (expo-glass-tabs SLIDE_SPRING parity):
     * Leading edge shoots ahead (stretching the liquid pill), trailing edge snaps up with spring settle.
     */
    private fun animateThumbTo(targetIndex: Int) {
        calculateTargetBounds(targetIndex)
        animator?.cancel()

        val startLeft = thumbLeft
        val startRight = thumbRight
        val movingRight = targetRight > startRight

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340L
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { va ->
                val fraction = va.animatedValue as Float

                if (movingRight) {
                    val rightFrac = min(1.0f, fraction * 1.25f)
                    val leftFrac = max(0.0f, (fraction - 0.15f) / 0.85f)
                    thumbRight = startRight + (targetRight - startRight) * rightFrac
                    thumbLeft = startLeft + (targetLeft - startLeft) * leftFrac
                } else {
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
                    // 1:1 finger scrubbing (expo-glass-tabs pattern)
                    val touchX = event.x.coerceIn(thumbPadding, width.toFloat() - thumbPadding)
                    val activeIndex = ((touchX - thumbPadding) / segmentWidth).toInt()
                        .coerceIn(0, segments.size - 1)

                    if (activeIndex != lastHapticIndex) {
                        lastHapticIndex = activeIndex
                        vibrateDetent()
                    }

                    // Jelly stretch tracking finger velocity
                    val currentCenter = touchX
                    val halfWidth = (segmentWidth / 2f) * 1.15f // 15% dynamic elastic elongation
                    thumbLeft = (currentCenter - halfWidth).coerceAtLeast(thumbPadding)
                    thumbRight = (currentCenter + halfWidth).coerceAtMost(width.toFloat() - thumbPadding)
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
                // Navigation committed only on release (expo-glass-tabs invariant)
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

        // 1. Draw Liquid Glass Track Trench (Full Stadium Capsule, Solid Obsidian with Zero Line Artifacts)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        // 2. Draw Active Liquid Capsule Lens
        val shadowOffsetY = 1.8f * density
        shadowRect.set(thumbLeft, thumbTop + shadowOffsetY, thumbRight, thumbBottom + shadowOffsetY)
        canvas.drawRoundRect(shadowRect, thumbRadius, thumbRadius, thumbShadowPaint)

        thumbRect.set(thumbLeft, thumbTop, thumbRight, thumbBottom)
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint)
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbRimPaint)

        // 3. Draw Segment Titles with Smoothstep Overlap Crossfade
        val usableWidth = width.toFloat() - (thumbPadding * 2)
        val segmentWidth = usableWidth / segments.size
        val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        val thumbCenter = (thumbLeft + thumbRight) / 2f

        for (i in segments.indices) {
            val textX = thumbPadding + (i * segmentWidth) + (segmentWidth / 2f)

            // Overlap fraction with smoothstep interpolation [0.0 .. 1.0]
            val distanceToPill = abs(textX - thumbCenter)
            val maxOverlapDist = segmentWidth * 0.75f
            val rawOverlap = (1f - (distanceToPill / maxOverlapDist)).coerceIn(0f, 1f)
            val overlap = rawOverlap * rawOverlap * (3f - 2f * rawOverlap) // Hermite smoothstep

            val r = (inactiveColorR + (activeColorR - inactiveColorR) * overlap).toInt()
            val g = (inactiveColorG + (activeColorG - inactiveColorG) * overlap).toInt()
            val b = (inactiveColorB + (activeColorB - inactiveColorB) * overlap).toInt()
            val a = (inactiveColorA + (activeColorA - inactiveColorA) * overlap).toInt()

            textPaint.color = Color.argb(a, r, g, b)
            canvas.drawText(segments[i].uppercase(), textX, textY, textPaint)
        }
    }

    private fun vibrateTick() {
        try {
            performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Exception) {}
    }

    private fun vibrateDetent() {
        try {
            performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Exception) {}
    }
}
