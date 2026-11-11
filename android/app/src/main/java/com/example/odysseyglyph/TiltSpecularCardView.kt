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

    init {
        setWillNotDraw(false)
        cameraDistance = 8000 * resources.displayMetrics.density
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                returnAnimator?.cancel()
                isTouching = true
                touchX = event.x
                touchY = event.y

                val centerX = width / 2f
                val centerY = height / 2f
                if (centerX > 0 && centerY > 0) {
                    val normX = ((touchX - centerX) / centerX).coerceIn(-1f, 1f)
                    val normY = ((touchY - centerY) / centerY).coerceIn(-1f, 1f)

                    rotationY = normX * maxTiltDeg
                    rotationX = -normY * maxTiltDeg
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                animateBackToRest()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateBackToRest() {
        val startRotX = rotationX
        val startRotY = rotationY

        returnAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { va ->
                val frac = va.animatedValue as Float
                rotationX = startRotX * frac
                rotationY = startRotY * frac
                invalidate()
            }
            start()
        }
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (isTouching && touchX >= 0 && touchY >= 0) {
            val radius = width.coerceAtLeast(height) * 0.75f
            val gradient = RadialGradient(
                touchX, touchY, radius,
                Color.argb(32, 255, 255, 255), // Subtle 12% specular white
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            specularPaint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), specularPaint)
        }
    }
}
