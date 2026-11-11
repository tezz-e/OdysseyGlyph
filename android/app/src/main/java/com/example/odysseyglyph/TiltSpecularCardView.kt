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
            val normX = ((x - centerX) / centerX).coerceIn(-1.2f, 1.2f)
            val normY = ((y - centerY) / centerY).coerceIn(-1.2f, 1.2f)

            // Anchored base: pivot stays pinned on the opposite side of input
            pivotX = centerX - (normX * centerX * 0.70f)
            pivotY = centerY - (normY * centerY * 0.70f)

            // Pure rubber stretch: stretches toward finger, orthogonal axis thins slightly
            val stretchX = Math.abs(normX) * 0.08f
            val stretchY = Math.abs(normY) * 0.08f
            scaleX = 0.95f * (1.0f + stretchX - stretchY * 0.30f)
            scaleY = 0.95f * (1.0f + stretchY - stretchX * 0.30f)

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
            duration = 280
            interpolator = OvershootInterpolator(1.35f)
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
        // No competing specular glare
    }
}
