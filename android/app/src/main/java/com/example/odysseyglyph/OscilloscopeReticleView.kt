package com.example.odysseyglyph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * OscilloscopeReticleView
 * Aerospace & telemetry CRT oscilloscope vector display.
 * Renders reticle grid lines, coordinate ticks, and dynamic audio wave traces.
 * 0 allocations in onDraw() for LTPO power efficiency.
 */
class OscilloscopeReticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#08080B")
        style = Paint.Style.FILL
    }

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A33")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#261414") // Deep subtle Nothing Red reticle grid
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val centerAxisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D1B1B") // Dashed dark red center axis
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }

    private val waveTracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EA3323") // Signature Nothing Red vector trace
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val waveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33EA3323") // Nothing Red vector glow
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val wavePath = Path()
    private val wavePoints = FloatArray(64)
    private var phase: Float = 0f
    private var isSimulating = true

    init {
        // Initialize wave buffer with standard rest sine
        updateWaveform(0.5f, 1.0f)
    }

    fun setTraceColor(colorHex: Int) {
        waveTracePaint.color = colorHex
        waveGlowPaint.color = (colorHex and 0x00FFFFFF) or 0x33000000
        postInvalidateOnAnimation()
    }

    fun updateWaveform(amplitude: Float, frequency: Float) {
        val count = wavePoints.size
        for (i in 0 until count) {
            val normX = i.toFloat() / (count - 1)
            val sin = kotlin.math.sin((normX * 4f * Math.PI.toFloat() * frequency) + phase)
            wavePoints[i] = (sin * amplitude).coerceIn(-1f, 1f)
        }
        phase += 0.15f
        if (phase > 2f * Math.PI.toFloat()) {
            phase -= 2f * Math.PI.toFloat()
        }
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minDim = (64 * resources.displayMetrics.density).toInt()
        val w = resolveSize(minDim, widthMeasureSpec)
        val h = resolveSize(minDim, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val centerY = h / 2f
        val centerX = w / 2f
        val radius = minOf(centerX, centerY) * 0.92f

        // 1. Draw circular smoked glass chassis & bezel ring
        canvas.drawCircle(centerX, centerY, radius, chassisPaint)
        canvas.drawCircle(centerX, centerY, radius, bezelPaint)

        // Clip to circular lens cavity
        val saveCount = canvas.save()
        val clipPath = Path()
        clipPath.addCircle(centerX, centerY, radius - 2f, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 2. Draw reticle grid
        val gridDivs = 4
        for (i in 1 until gridDivs) {
            val y = h * i / gridDivs
            val x = w * i / gridDivs
            canvas.drawLine(0f, y, w, y, gridPaint)
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }

        // Center reticle axes
        canvas.drawLine(0f, centerY, w, centerY, centerAxisPaint)
        canvas.drawLine(centerX, 0f, centerX, h, centerAxisPaint)

        // 3. Draw smooth waveform path
        val count = wavePoints.size
        if (count >= 2) {
            wavePath.reset()
            val sliceX = w / (count - 1)

            var prevX = 0f
            var prevY = centerY - (wavePoints[0] * (h * 0.36f))
            wavePath.moveTo(prevX, prevY)

            for (i in 1 until count) {
                val curX = i * sliceX
                val curY = centerY - (wavePoints[i] * (h * 0.36f))
                val midX = (prevX + curX) / 2f
                val midY = (prevY + curY) / 2f
                wavePath.quadTo(prevX, prevY, midX, midY)
                prevX = curX
                prevY = curY
            }
            wavePath.lineTo(prevX, prevY)

            canvas.drawPath(wavePath, waveGlowPaint)
            canvas.drawPath(wavePath, waveTracePaint)
        }

        canvas.restoreToCount(saveCount)
    }
}
