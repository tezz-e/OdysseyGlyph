package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * TiltSpecularCardView
 * 3D tactile card with dynamic tilt response and specular spotlight sheen.
 * Inspired by ReactBits TiltedCard and Apple spatial materials.
 */
class TiltSpecularCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var touchX: Float = -1f
    private var touchY: Float = -1f
    private var isTouching = false

    private val maxTiltDeg = 7.0f
    private var returnAnimator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    init {
        setWillNotDraw(false)
        cameraDistance = 8000 * resources.displayMetrics.density
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                returnAnimator?.cancel()
                isTouching = true
                downX = event.x
                downY = event.y
                touchX = event.x
                touchY = event.y
                updateTilt(event.x, event.y)
                super.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                updateTilt(event.x, event.y)
                super.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_UP -> {
                isTouching = false
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
                isTouching = false
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

    private fun updateTilt(x: Float, y: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        if (centerX > 0 && centerY > 0) {
            val normX = ((x - centerX) / centerX).coerceIn(-1.1f, 1.1f)
            val normY = ((y - centerY) / centerY).coerceIn(-1.1f, 1.1f)

            // Anchored base: pivot stays pinned on the opposite side of input within safe margin
            pivotX = (centerX - (normX * centerX * 0.60f)).coerceIn(width * 0.10f, width * 0.90f)
            pivotY = (centerY - (normY * centerY * 0.60f)).coerceIn(height * 0.10f, height * 0.90f)

            // Pure rubber stretch: compresses under touch within 1.0f envelope to prevent screen clipping
            val stretchX = Math.abs(normX) * 0.05f
            val stretchY = Math.abs(normY) * 0.05f
            scaleX = (0.95f * (1.0f + stretchX - stretchY * 0.25f)).coerceIn(0.92f, 1.00f)
            scaleY = (0.95f * (1.0f + stretchY - stretchX * 0.25f)).coerceIn(0.92f, 1.00f)

            // Zero 3D tilt/rotation
            rotationY = 0f
            rotationX = 0f
            invalidate()
        }
    }

    private fun animateBackToRest() {
        val startScaleX = scaleX
        val startScaleY = scaleY

        returnAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.20f)
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
        if (isTouching && touchX >= 0 && touchY >= 0 && width > 0 && height > 0) {
            val density = resources.displayMetrics.density
            val cornerRadius = kotlin.math.min(22f * density, kotlin.math.min(width, height) / 2f)
            val path = android.graphics.Path()
            path.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(path)

            val radius = kotlin.math.hypot(width.toDouble(), height.toDouble()).toFloat() * 0.45f
            specularPaint.shader = RadialGradient(
                touchX, touchY, radius.coerceAtLeast(10f),
                Color.argb(45, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), specularPaint)
            canvas.restoreToCount(save)
        }
    }
}
