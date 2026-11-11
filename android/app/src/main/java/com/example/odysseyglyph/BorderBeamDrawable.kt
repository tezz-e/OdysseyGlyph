package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * BorderBeamDrawable
 *
 * Recreates the Magic UI "Border Beam" glowing laser trace along card perimeters
 * using Nothing OS aesthetics (Nothing Red #EA3323 laser fading to transparent).
 *
 * Performance:
 * - Pre-allocated paths, matrices, and paints.
 * - Single ValueAnimator driving 360-degree rotation.
 * - Auto stops when invisible or detached.
 */
class BorderBeamDrawable(
    private val beamColor: Int = 0xFFEA3323.toInt(), // Nothing Red
    private val borderWidth: Float = 3f,
    private val cornerRadius: Float = 16f,
    private val durationMs: Long = 3000L
) : Drawable() {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    private val borderPath = Path()
    private val rectF = RectF()
    private val shaderMatrix = Matrix()

    private var sweepGradient: SweepGradient? = null
    private var currentAngle: Float = 0f
    private var animator: ValueAnimator? = null
    private var isRunning: Boolean = false

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        rectF.set(
            bounds.left + borderWidth / 2f,
            bounds.top + borderWidth / 2f,
            bounds.right - borderWidth / 2f,
            bounds.bottom - borderWidth / 2f
        )

        borderPath.reset()
        borderPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)

        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        // Beam gradient: sharp bright Nothing Red head trailing into transparent
        val colors = intArrayOf(
            Color.TRANSPARENT,
            beamColor,
            Color.TRANSPARENT,
            Color.TRANSPARENT
        )
        val positions = floatArrayOf(0.0f, 0.12f, 0.28f, 1.0f)

        sweepGradient = SweepGradient(cx, cy, colors, positions)
        borderPaint.shader = sweepGradient
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                currentAngle = anim.animatedValue as Float
                val b = bounds
                shaderMatrix.setRotate(currentAngle, b.exactCenterX(), b.exactCenterY())
                sweepGradient?.setLocalMatrix(shaderMatrix)
                invalidateSelf()
            }
            start()
        }
    }

    fun stop() {
        isRunning = false
        animator?.cancel()
        animator = null
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (rectF.isEmpty || !isRunning) return
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun setAlpha(alpha: Int) {
        borderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        borderPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
