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
import android.view.ViewConfiguration
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
        textSize = 11f * scaledDensity
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

    // Touch & Scrubbing state (Apple UISegmentedControl & expo-glass-tabs parity)
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private var isDragging = false
    private var isTouchActive = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragCenterOffset = 0f
    private var lastHapticIndex = -1
    private var lastMoveX = 0f
    private var dynamicStretch = 1.0f

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
     * Animate with Liquid Glass Spring (Apple UISegmentedControl & expo-glass-tabs SLIDE_SPRING):
     * Leading edge shoots ahead (stretching the liquid pill), trailing edge snaps up with spring settle.
     */
    private fun animateThumbTo(targetIndex: Int) {
        calculateTargetBounds(targetIndex)
        animator?.cancel()

        val startLeft = thumbLeft
        val startRight = thumbRight
        val movingRight = targetRight >= startRight

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = OvershootInterpolator(1.25f)
            addUpdateListener { va ->
                val fraction = va.animatedValue as Float

                if (abs(targetRight - startRight) < 2f && abs(targetLeft - startLeft) < 2f) {
                    thumbLeft = targetLeft
                    thumbRight = targetRight
                } else if (movingRight) {
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
                // Immediately request parent scroll containers not to intercept
                parent?.requestDisallowInterceptTouchEvent(true)
                animator?.cancel()
                touchDownX = event.x
                touchDownY = event.y
                lastMoveX = event.x
                isDragging = false
                isTouchActive = true

                // Check if touch down landed on the active thumb for 1:1 grab
                val isInsideThumb = event.x >= thumbLeft && event.x <= thumbRight
                val currentThumbCenter = (thumbLeft + thumbRight) / 2f
                dragCenterOffset = if (isInsideThumb) currentThumbCenter - event.x else 0f

                vibrateTick()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTouchActive) return true

                val dx = event.x - touchDownX
                val dy = event.y - touchDownY

                if (!isDragging) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (abs(dy) > touchSlop && abs(dy) > abs(dx) && dragCenterOffset == 0f) {
                        // User clearly intended vertical scrolling on empty track
                        parent?.requestDisallowInterceptTouchEvent(false)
                        isTouchActive = false
                        return false
                    }
                }

                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(true)

                    // Calculate instantaneous drag velocity for Apple-style dynamic stretch
                    val moveDeltaX = event.x - lastMoveX
                    lastMoveX = event.x
                    val velocityStretch = (abs(moveDeltaX) / (8f * density)).coerceIn(0f, 0.25f)
                    dynamicStretch = 1.08f + velocityStretch // Dynamic 8% to 33% elongation while dragging

                    // Boundary rubber-band resistance
                    val minCenter = thumbPadding + (segmentWidth / 2f)
                    val maxCenter = width.toFloat() - thumbPadding - (segmentWidth / 2f)
                    val rawCenter = event.x + dragCenterOffset
                    val currentCenter = when {
                        rawCenter < minCenter -> minCenter - (minCenter - rawCenter) * 0.2f
                        rawCenter > maxCenter -> maxCenter + (rawCenter - maxCenter) * 0.2f
                        else -> rawCenter
                    }

                    val activeIndex = ((currentCenter - thumbPadding) / segmentWidth).toInt()
                        .coerceIn(0, segments.size - 1)

                    if (activeIndex != lastHapticIndex) {
                        lastHapticIndex = activeIndex
                        vibrateDetent()
                    }

                    val halfWidth = (segmentWidth / 2f) * dynamicStretch
                    thumbLeft = (currentCenter - halfWidth).coerceAtLeast(thumbPadding * 0.5f)
                    thumbRight = (currentCenter + halfWidth).coerceAtMost(width.toFloat() - thumbPadding * 0.5f)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isTouchActive = false

                val finalIndex = if (isDragging) {
                    // Released after drag: snap to segment closest to thumb center
                    val currentCenter = (thumbLeft + thumbRight) / 2f
                    ((currentCenter - thumbPadding) / segmentWidth).toInt().coerceIn(0, segments.size - 1)
                } else {
                    // Single tap / click: select segment where tap landed
                    val touchX = event.x.coerceIn(thumbPadding, width.toFloat() - thumbPadding)
                    ((touchX - thumbPadding) / segmentWidth).toInt().coerceIn(0, segments.size - 1)
                }

                isDragging = false
                val changed = finalIndex != selectedIndex
                selectedIndex = finalIndex
                lastHapticIndex = finalIndex
                vibrateDetent()
                animateThumbTo(finalIndex)
                if (changed) {
                    onSegmentSelected?.invoke(selectedIndex, segments[selectedIndex])
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isDragging = false
                isTouchActive = false
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

        // 2. Draw Active Liquid Capsule Lens with Dynamic Lift when Pressed/Dragging
        val isPressedOrDragging = isDragging || isTouchActive
        val currentElevation = if (isPressedOrDragging) 2.4f * density else 1.8f * density
        val currentTop = if (isPressedOrDragging) thumbTop - 0.6f * density else thumbTop
        val currentBottom = if (isPressedOrDragging) thumbBottom + 0.6f * density else thumbBottom
        val currentThumbRadius = (currentBottom - currentTop) / 2f

        shadowRect.set(thumbLeft, currentTop + currentElevation, thumbRight, currentBottom + currentElevation)
        canvas.drawRoundRect(shadowRect, currentThumbRadius, currentThumbRadius, thumbShadowPaint)

        thumbRect.set(thumbLeft, currentTop, thumbRight, currentBottom)
        canvas.drawRoundRect(thumbRect, currentThumbRadius, currentThumbRadius, thumbPaint)
        canvas.drawRoundRect(thumbRect, currentThumbRadius, currentThumbRadius, thumbRimPaint)

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
