package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val GRID_SIZE = 25
        const val TOTAL_GRID_LINES = (GRID_SIZE - 1) * 2 // 48
        const val TOTAL_GRID_FLOATS = TOTAL_GRID_LINES * 4 // 192
        const val DEFAULT_RADIUS_FACTOR = 0.4f

        /**
         * Computes the 192 float coordinates (48 lines * 4 coords) representing
         * the 25x25 grid lines inscribed within the circle bounds.
         */
        fun computeGridLineCoordinates(
            cx: Float,
            cy: Float,
            radius: Float,
            outPoints: FloatArray
        ): FloatArray {
            val startX = cx - radius
            val startY = cy - radius
            val diameter = radius * 2f
            val step = diameter / GRID_SIZE

            var index = 0
            // 24 vertical lines
            for (i in 1 until GRID_SIZE) {
                val x = startX + i * step
                outPoints[index++] = x
                outPoints[index++] = startY
                outPoints[index++] = x
                outPoints[index++] = startY + diameter
            }
            // 24 horizontal lines
            for (i in 1 until GRID_SIZE) {
                val y = startY + i * step
                outPoints[index++] = startX
                outPoints[index++] = y
                outPoints[index++] = startX + diameter
                outPoints[index++] = y
            }
            return outPoints
        }

        /**
         * Evaluates whether diode lattice coordinate (x, y) falls inside the circular
         * matrix mask according to the 8-fold symmetric circular equation:
         * (x - center)^2 + (y - center)^2 <= radius^2
         */
        fun isPointInsideMatrixMask(
            x: Int,
            y: Int,
            center: Double = 12.0,
            radius: Double = 12.5
        ): Boolean {
            val dx = x - center
            val dy = y - center
            return (dx * dx + dy * dy) <= (radius * radius)
        }
    }

    private var isBatchUpdating = false

    // Public properties consumed by MainActivity.kt:730-732 (DO NOT RENAME)
    var circleX: Float = -1f
        set(value) {
            if (field != value) {
                field = value
                if (!isBatchUpdating) {
                    updateGeometry()
                    invalidate()
                }
            }
        }

    var circleY: Float = -1f
        set(value) {
            if (field != value) {
                field = value
                if (!isBatchUpdating) {
                    updateGeometry()
                    invalidate()
                }
            }
        }

    var circleRadius: Float = 200f
        set(value) {
            if (field != value) {
                field = value
                if (!isBatchUpdating) {
                    updateGeometry()
                    invalidate()
                }
            }
        }

    /**
     * Batch updates circle bounds, recalculating geometry and invalidating exactly once.
     */
    fun setCircleBounds(cx: Float, cy: Float, radius: Float) {
        isBatchUpdating = true
        try {
            circleX = cx
            circleY = cy
            circleRadius = radius
        } finally {
            isBatchUpdating = false
        }
        updateGeometry()
        invalidate()
    }

    // Pre-allocated paints to eliminate GC allocations in onDraw()
    private val dimPaint = Paint().apply {
        color = Color.parseColor("#BB000000") // 73% black
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFFFFF") // Machined lens outer accent ring
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1f
        alpha = 0 // Initially hidden until touch interaction
    }

    // Pre-allocated clipping path and grid coordinate array (48 lines * 4 coords = 192 floats)
    private val circlePath = Path()
    private val gridLinePoints = FloatArray(TOTAL_GRID_FLOATS)

    // Grid touch-fade state animator
    private var gridAlphaAnimator: ValueAnimator? = null
    var isTouching: Boolean = false
        private set

    init {
        // LAYER_TYPE_HARDWARE REMOVED to eliminate 4.6MB offscreen FBO blits.
        // clipOutPath draws the transparent viewport directly to the window surface.
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            setCircleBounds(w / 2f, h / 2f, minOf(w, h) * DEFAULT_RADIUS_FACTOR)
        }
    }

    /**
     * Recalculates circlePath and grid line endpoints whenever size or position changes.
     * Guaranteed 0 allocations during onDraw().
     */
    private fun updateGeometry() {
        if (circleRadius <= 0f || (circleX == -1f && circleY == -1f)) return

        circlePath.reset()
        circlePath.addCircle(circleX, circleY, circleRadius, Path.Direction.CW)
        updateGridPoints()
    }

    private fun updateGridPoints() {
        computeGridLineCoordinates(circleX, circleY, circleRadius, gridLinePoints)
    }

    /**
     * Touch-activated 25x25 grid fading.
     * Smoothly fades grid to 35% alpha on touch, and fades to 0% on release.
     * Settles at 0 repaints when idle.
     */
    fun setTouching(touching: Boolean) {
        if (isTouching == touching) return
        isTouching = touching

        gridAlphaAnimator?.cancel()
        val targetAlpha = if (touching) (255 * 0.35f).toInt() else 0
        val currentAlpha = gridPaint.alpha

        if (currentAlpha == targetAlpha) return

        val animDuration = if (touching) 150L else 250L
        gridAlphaAnimator = ValueAnimator.ofInt(currentAlpha, targetAlpha).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                gridPaint.alpha = animator.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (circleRadius <= 0f || width == 0 || height == 0 || (circleX == -1f && circleY == -1f)) return

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Direct window dimming with circular cutout (zero offscreen FBO blits)
        canvas.save()
        canvas.clipOutPath(circlePath)
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        canvas.restore()

        // 2. 25x25 grid lines clipped inside the circular aperture (single JNI call)
        if (gridPaint.alpha > 0) {
            canvas.save()
            canvas.clipPath(circlePath)
            canvas.drawLines(gridLinePoints, gridPaint)
            canvas.restore()
        }

        // 3. Machined lens bezel and border ring
        canvas.drawCircle(circleX, circleY, circleRadius, borderPaint)
        canvas.drawCircle(circleX, circleY, circleRadius + 3f, bezelPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gridAlphaAnimator?.cancel()
        gridAlphaAnimator = null
    }
}
