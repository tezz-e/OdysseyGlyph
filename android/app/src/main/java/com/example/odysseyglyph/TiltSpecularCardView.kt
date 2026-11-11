package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
 * Tactile glass card with Apple-style subtle specular crescent gloss and
 * anchored gooey rubber deformation that preserves screen bounds.
 */
class TiltSpecularCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.0f
    }
    private val clipPath = Path()
    private val rectF = RectF()

    private var touchXNorm: Float = 0.5f
    private var glossIntensity: Float = 0.35f
    private var glossFadeAnimator: ValueAnimator? = null
    private var returnAnimator: ValueAnimator? = null

    private var downX = 0f
    private var downY = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    init {
        setWillNotDraw(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                returnAnimator?.cancel()
                glossFadeAnimator?.cancel()
                downX = event.x
                downY = event.y
                if (width > 0) touchXNorm = (event.x / width).coerceIn(0.15f, 0.85f)

                glossFadeAnimator = ValueAnimator.ofFloat(glossIntensity, 1.0f).apply {
                    duration = 140
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
                if (width > 0) touchXNorm = (event.x / width).coerceIn(0.15f, 0.85f)
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
        val centerX = width / 2f
        val centerY = height / 2f
        if (centerX > 0 && centerY > 0) {
            val normX = ((x - centerX) / centerX).coerceIn(-1.25f, 1.25f)
            val normY = ((y - centerY) / centerY).coerceIn(-1.25f, 1.25f)

            // Dynamic anchored base: pivot stays pinned on the opposite side of input
            val targetPivotX = (centerX - (normX * centerX * 0.70f)).coerceIn(width * 0.08f, width * 0.92f)
            val targetPivotY = (centerY - (normY * centerY * 0.70f)).coerceIn(height * 0.08f, height * 0.92f)
            pivotX = targetPivotX
            pivotY = targetPivotY

            // Screen bounds detection: dynamically compute maximum safe stretch
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val screenW = resources.displayMetrics.widthPixels
            val screenH = resources.displayMetrics.heightPixels
            val distLeft = loc[0].coerceAtLeast(0)
            val distRight = (screenW - (loc[0] + width)).coerceAtLeast(0)
            val distTop = loc[1].coerceAtLeast(0)
            val distBottom = (screenH - (loc[1] + height)).coerceAtLeast(0)

            val maxStretchX = if (width - targetPivotX > 1f) (distRight * 0.70f) / (width - targetPivotX) else 0.08f
            val maxSafeScaleX = 1.0f + minOf(maxStretchX, if (targetPivotX > 1f) (distLeft * 0.70f) / targetPivotX else 0.08f).coerceIn(0.0f, 0.08f)

            val maxStretchY = if (height - targetPivotY > 1f) (distBottom * 0.70f) / (height - targetPivotY) else 0.08f
            val maxSafeScaleY = 1.0f + minOf(maxStretchY, if (targetPivotY > 1f) (distTop * 0.70f) / targetPivotY else 0.08f).coerceIn(0.0f, 0.08f)

            // Asymmetric Poisson rubber stretch
            val stretchX = Math.abs(normX) * 0.12f
            val stretchY = Math.abs(normY) * 0.12f
            scaleX = (0.94f * (1.0f + stretchX - stretchY * 0.48f)).coerceIn(0.86f, maxSafeScaleX)
            scaleY = (0.94f * (1.0f + stretchY - stretchX * 0.48f)).coerceIn(0.86f, maxSafeScaleY)

            // Strictly flat plane: zero 3D tilt/rotation
            rotationY = 0f
            rotationX = 0f
            invalidate()
        }
    }

    private fun animateBackToRest() {
        val startScaleX = scaleX
        val startScaleY = scaleY

        glossFadeAnimator?.cancel()
        glossFadeAnimator = ValueAnimator.ofFloat(glossIntensity, 0.35f).apply {
            duration = 280
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
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cornerRadius = kotlin.math.min(22f * density, kotlin.math.min(w, h) / 2f)

        rectF.set(0f, 0f, w, h)
        clipPath.reset()
        clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)

        val save = canvas.save()
        canvas.clipPath(clipPath)

        // 1. Apple-style Liquid Glass Top Bezel Specular Crescent (subtle gloss, NOT a pointer spotlight)
        val glossHeight = (h * 0.40f).coerceAtLeast(16f * density)
        val topAlpha = (26 * glossIntensity).toInt().coerceIn(0, 255)
        val midAlpha = (10 * glossIntensity).toInt().coerceIn(0, 255)
        specularPaint.shader = LinearGradient(
            w * touchXNorm, 0f,
            w * touchXNorm, glossHeight,
            intArrayOf(
                Color.argb(topAlpha, 255, 255, 255),
                Color.argb(midAlpha, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0.0f, 0.45f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, glossHeight, specularPaint)

        // 2. Refractive Specular Rim (Apple continuous curvature highlight)
        val rimAlpha = (22 * glossIntensity).toInt().coerceIn(0, 255)
        rimPaint.color = Color.argb(rimAlpha, 255, 255, 255)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, rimPaint)

        canvas.restoreToCount(save)
    }
}
