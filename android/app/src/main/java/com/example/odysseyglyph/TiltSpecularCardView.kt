package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * TiltSpecularCardView
 * Tactile glass card following sdegenaar/liquid_glass_widgets architecture:
 * - ZERO glow at rest (completely clean smoked glass; no static gradient bands).
 * - Touch-responsive optical specular glow centered directly at the contact point.
 * - Aspect-ratio normalized jelly stretch & squash with signed smoothstep pivot gliding.
 * - Dynamic screen bounds detection preventing border clipping.
 */
class TiltSpecularCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        isFilterBitmap = true
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.0f
        isDither = true
    }
    private val clipPath = Path()
    private val rectF = RectF()

    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private var glossIntensity: Float = 0.0f // 0.0f at rest — zero gradient or artifact when idle
    private var glossFadeAnimator: ValueAnimator? = null
    private var returnAnimator: ValueAnimator? = null

    private var downX = 0f
    private var downY = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    init {
        setWillNotDraw(false)
    }

    private fun signedSmoothStep(x: Float, edge: Float): Float {
        if (edge <= 0.001f) return 0f
        val t = (x / edge).coerceIn(-1.0f, 1.0f)
        val a = Math.abs(t)
        val smooth = a * a * (3.0f - 2.0f * a)
        return if (t >= 0f) smooth else -smooth
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                returnAnimator?.cancel()
                glossFadeAnimator?.cancel()
                downX = event.x
                downY = event.y
                touchX = event.x
                touchY = event.y

                glossFadeAnimator = ValueAnimator.ofFloat(glossIntensity, 1.0f).apply {
                    duration = 120
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { va ->
                        glossIntensity = va.animatedValue as Float
                        invalidate()
                    }
                    start()
                }

                updateRubber(event.x, event.y)
                super.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchX
                val dy = event.y - touchY
                if (dx * dx + dy * dy >= 9f) { // 3px deadband filter
                    touchX = event.x
                    touchY = event.y
                    if (glossIntensity < 0.95f) {
                        glossIntensity = 1.0f
                    }
                }
                updateRubber(event.x, event.y)
                super.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animateBackToRest()
                val deltaX = kotlin.math.abs(event.x - downX)
                val deltaY = kotlin.math.abs(event.y - downY)
                if (deltaX < touchSlop && deltaY < touchSlop) {
                    performClick()
                }
                super.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animateBackToRest()
                super.onTouchEvent(event)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateRubber(x: Float, y: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val deltaX = x - downX
        val deltaY = y - downY
        val absDx = Math.abs(deltaX)
        val absDy = Math.abs(deltaY)

        if (w > 0 && h > 0 && (absDx > 0.001f || absDy > 0.001f)) {
            val density = resources.displayMetrics.density

            // Aspect-ratio normalization from sdegenaar/liquid_glass_widgets
            val refDim = kotlin.math.max(w, h)
            val relativeX = if (refDim > 0f) absDx / refDim else 0f
            val relativeY = if (refDim > 0f) absDy / refDim else 0f

            val aspectRatio = w / h
            val xDamping = if (aspectRatio > 1.0f) (1.0f / aspectRatio).coerceIn(0.20f, 1.0f) else 1.0f
            val yDamping = if (aspectRatio < 1.0f) aspectRatio.coerceIn(0.20f, 1.0f) else 1.0f

            val stretchIntensity = 0.50f
            val squashFactor = 0.30f
            val stretchX = 1.0f + (relativeX * stretchIntensity * xDamping).coerceIn(0.0f, 0.22f)
            val stretchY = 1.0f + (relativeY * stretchIntensity * yDamping).coerceIn(0.0f, 0.22f)
            val squashX = 1.0f - (relativeY * squashFactor * xDamping).coerceIn(0.0f, 0.12f)
            val squashY = 1.0f - (relativeX * squashFactor * yDamping).coerceIn(0.0f, 0.12f)

            // Smooth pivot glide through center using signed smoothstep
            pivotX = w * (0.5f - signedSmoothStep(deltaX, 24f * density) * 0.5f)
            pivotY = h * (0.5f - signedSmoothStep(deltaY, 24f * density) * 0.5f)

            // Screen bounds detection: dynamically compute maximum safe stretch
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            val distLeft = loc[0].coerceAtLeast(0)
            val distRight = (screenW - (loc[0] + width)).coerceAtLeast(0)
            val distTop = loc[1].coerceAtLeast(0)
            val distBottom = (screenH - (loc[1] + height)).coerceAtLeast(0)

            val maxStretchX = if (w - pivotX > 1f) (distRight * 0.70f) / (w - pivotX) else 0.08f
            val maxSafeScaleX = 1.0f + minOf(maxStretchX, if (pivotX > 1f) (distLeft * 0.70f) / pivotX else 0.08f).coerceIn(0.0f, 0.08f)

            val maxStretchY = if (h - pivotY > 1f) (distBottom * 0.70f) / (h - pivotY) else 0.08f
            val maxSafeScaleY = 1.0f + minOf(maxStretchY, if (pivotY > 1f) (distTop * 0.70f) / pivotY else 0.08f).coerceIn(0.0f, 0.08f)

            scaleX = (0.94f * (stretchX * squashX)).coerceIn(0.85f, maxSafeScaleX)
            scaleY = (0.94f * (stretchY * squashY)).coerceIn(0.85f, maxSafeScaleY)

            rotationY = 0f
            rotationX = 0f
            invalidate()
        }
    }

    private fun animateBackToRest() {
        val startScaleX = scaleX
        val startScaleY = scaleY

        glossFadeAnimator?.cancel()
        glossFadeAnimator = ValueAnimator.ofFloat(glossIntensity, 0.0f).apply {
            duration = 220
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                glossIntensity = va.animatedValue as Float
                invalidate()
            }
            start()
        }

        returnAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.35f) // Rubbery harmonic bounce
            addUpdateListener { va ->
                val frac = va.animatedValue as Float
                rotationX = 0f
                rotationY = 0f
                scaleX = 1.0f + (startScaleX - 1.0f) * frac
                scaleY = 1.0f + (startScaleY - 1.0f) * frac
                if (frac == 0f) {
                    pivotX = width / 2f
                    pivotY = height / 2f
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (glossIntensity <= 0.001f) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cornerRadius = kotlin.math.min(22f * density, kotlin.math.min(w, h) / 2f)

        // Strictly NO interior pointer disc, circle, or face fill.
        // Matching Apple Liquid Glass & sdegenaar/liquid_glass_widgets:
        // "Blend toward highlightColor, gated by edgeFactor so it stays on the curved rim"
        // The touch causes specular reflection along the outer squircle RIM nearest the finger contact point.
        val strokeW = 1.5f * density
        val halfStroke = strokeW / 2f
        rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)

        val maxDim = kotlin.math.max(w, h)
        val rimGlowRadius = kotlin.math.max(140f * density, maxDim * 0.50f)
        val peakRimAlpha = (120 * glossIntensity).toInt().coerceIn(0, 255)
        val midRimAlpha = (40 * glossIntensity).toInt().coerceIn(0, 255)
        val rimColors = intArrayOf(
            Color.argb(peakRimAlpha, 255, 255, 255),
            Color.argb(midRimAlpha, 255, 255, 255),
            Color.argb(0, 255, 255, 255)
        )
        val rimStops = floatArrayOf(0.0f, 0.45f, 1.0f)

        rimPaint.strokeWidth = strokeW
        rimPaint.shader = RadialGradient(
            touchX, touchY, rimGlowRadius, rimColors, rimStops, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, rimPaint)
    }
}
