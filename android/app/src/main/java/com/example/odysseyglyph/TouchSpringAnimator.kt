package com.example.odysseyglyph

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.WeakHashMap

/**
 * TouchSpringAnimator
 *
 * Implements second-order physics-based dynamic spring animations on interactive Views
 * following Nothing OS / Apple fluid interface principles.
 *
 * Kinematics:
 * - Stiffness k ≈ 380f (responsive, non-sluggish mechanical response)
 * - Damping ratio ζ ≈ 0.78f (slightly underdamped; fast settling in ~200ms with zero lingering oscillation)
 * - Pressed scale: 0.96f
 * - Resting scale: 1.0f
 */
object TouchSpringAnimator {

    const val DEFAULT_STIFFNESS = 380f
    const val DEFAULT_DAMPING_RATIO = 0.78f
    const val DEFAULT_PRESSED_SCALE = 0.96f
    const val DEFAULT_RESTING_SCALE = 1.0f

    private class SpringHolder(
        val springX: SpringAnimation,
        val springY: SpringAnimation,
        var pressedScale: Float,
        var restingScale: Float,
        var glossDrawable: LiquidGlassGlossDrawable? = null
    )

    private val springRegistry = WeakHashMap<View, SpringHolder>()

    /**
     * Apple-style Liquid Glass subtle specular gloss drawable.
     * Renders a refined top-bezel specular crescent gloss and squircle continuous-curvature rim highlight.
     * Subtle, tactile, and optical — strictly no flashlight or pointer discs.
     */
    class LiquidGlassGlossDrawable(private val targetView: View) : android.graphics.drawable.Drawable() {
        private val glossPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val rimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = targetView.resources.displayMetrics.density * 1.0f
        }
        private val clipPath = android.graphics.Path()
        private val rectF = android.graphics.RectF()

        // 0.35f ambient gloss at rest, smoothly glints to 1.0f on interaction
        private var glossIntensity = 0.35f
        private var touchXNorm = 0.5f
        private var fadeAnimator: android.animation.ValueAnimator? = null

        fun onTouchDown(x: Float, y: Float) {
            fadeAnimator?.cancel()
            val w = targetView.width.toFloat()
            if (w > 0) touchXNorm = (x / w).coerceIn(0.15f, 0.85f)
            fadeAnimator = android.animation.ValueAnimator.ofFloat(glossIntensity, 1.0f).apply {
                duration = 140
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { va ->
                    glossIntensity = va.animatedValue as Float
                    targetView.invalidate()
                    invalidateSelf()
                }
                start()
            }
        }

        fun onTouchMove(x: Float, y: Float) {
            val w = targetView.width.toFloat()
            if (w > 0) touchXNorm = (x / w).coerceIn(0.15f, 0.85f)
            if (glossIntensity < 0.95f) {
                glossIntensity = 1.0f
            }
            targetView.invalidate()
            invalidateSelf()
        }

        fun onTouchUp() {
            fadeAnimator?.cancel()
            fadeAnimator = android.animation.ValueAnimator.ofFloat(glossIntensity, 0.35f).apply {
                duration = 280
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    glossIntensity = va.animatedValue as Float
                    targetView.invalidate()
                    invalidateSelf()
                }
                start()
            }
        }

        override fun draw(canvas: android.graphics.Canvas) {
            val w = targetView.width.toFloat()
            val h = targetView.height.toFloat()
            if (w <= 0 || h <= 0) return

            val density = targetView.resources.displayMetrics.density
            val cornerRadius = kotlin.math.min(22f * density, kotlin.math.min(w, h) / 2f)

            rectF.set(0f, 0f, w, h)
            clipPath.reset()
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)

            val saveCount = canvas.save()
            canvas.clipPath(clipPath)

            // 1. Apple-style Liquid Glass Top Bezel Specular Crescent (subtle gloss, NOT a pointer spotlight)
            val glossHeight = (h * 0.40f).coerceAtLeast(16f * density)
            val topAlpha = (26 * glossIntensity).toInt().coerceIn(0, 255)
            val midAlpha = (10 * glossIntensity).toInt().coerceIn(0, 255)
            glossPaint.shader = android.graphics.LinearGradient(
                w * touchXNorm, 0f,
                w * touchXNorm, glossHeight,
                intArrayOf(
                    android.graphics.Color.argb(topAlpha, 255, 255, 255),
                    android.graphics.Color.argb(midAlpha, 255, 255, 255),
                    android.graphics.Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0.0f, 0.45f, 1.0f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, glossHeight, glossPaint)

            // 2. Refractive Specular Rim (Apple continuous curvature highlight)
            val rimAlpha = (22 * glossIntensity).toInt().coerceIn(0, 255)
            rimPaint.color = android.graphics.Color.argb(rimAlpha, 255, 255, 255)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, rimPaint)

            canvas.restoreToCount(saveCount)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /**
     * Attaches Apple-style anchored gooey rubber stretch physics and subtle liquid glass gloss.
     * The card remains firmly anchored to its surface position (strictly zero translation).
     * Strictly no 3D tilt/rotation to prevent competing visual effects.
     * Dynamic screen bounds detection allows generous gooey deformation without screen clipping.
     * On release, it snaps back with playful rubbery harmonic oscillation and haptic detents.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachLiquidGlass(
        view: View,
        pressedScale: Float = 0.94f,
        enableStretch: Boolean = true,
        onTouchListener: View.OnTouchListener? = null
    ) {
        val density = view.resources.displayMetrics.density

        // Unclip ancestors up to 3 levels to ensure harmonic bounce is never clipped
        var p = view.parent
        var depth = 0
        while (p is android.view.ViewGroup && depth < 3) {
            p.clipChildren = false
            p.clipToPadding = false
            p = p.parent
            depth++
        }

        // Gooey rubber harmonic spring: pliable, organic response with playful harmonic wobble on release
        val scaleForce = SpringForce().apply {
            stiffness = 240f        // Gooey rubber elasticity (pliable elastomer)
            dampingRatio = 0.52f    // Playful underdamped harmonic bounce
            finalPosition = DEFAULT_RESTING_SCALE
        }

        val springX = SpringAnimation(view, DynamicAnimation.SCALE_X).setSpring(SpringForce().apply {
            stiffness = scaleForce.stiffness
            dampingRatio = scaleForce.dampingRatio
            finalPosition = DEFAULT_RESTING_SCALE
        })
        val springY = SpringAnimation(view, DynamicAnimation.SCALE_Y).setSpring(SpringForce().apply {
            stiffness = scaleForce.stiffness
            dampingRatio = scaleForce.dampingRatio
            finalPosition = DEFAULT_RESTING_SCALE
        })

        // Strictly anchored base - the card NEVER moves/translates across the screen
        // Strictly flat plane - zero 3D tilt/rotation
        view.translationX = 0f
        view.translationY = 0f
        view.rotationX = 0f
        view.rotationY = 0f

        val glossDrawable = LiquidGlassGlossDrawable(view)
        view.overlay.add(glossDrawable)

        val holder = SpringHolder(
            springX = springX,
            springY = springY,
            pressedScale = pressedScale,
            restingScale = DEFAULT_RESTING_SCALE,
            glossDrawable = glossDrawable
        )
        springRegistry[view] = holder

        if (!view.isClickable && !view.isFocusable) {
            view.isClickable = true
        }

        var downRawX = 0f
        var downRawY = 0f
        val touchSlop = 20f * density

        view.setOnTouchListener { v, event ->
            val w = v.width.toFloat()
            val h = v.height.toFloat()
            val cx = w / 2f
            val cy = h / 2f

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    glossDrawable.onTouchDown(event.x, event.y)

                    val nx = if (cx > 0) ((event.x - cx) / cx).coerceIn(-1f, 1f) else 0f
                    val ny = if (cy > 0) ((event.y - cy) / cy).coerceIn(-1f, 1f) else 0f

                    // Anchored base: pivot stays pinned on the opposite side of input
                    val targetPivotX = (cx - (nx * cx * 0.70f)).coerceIn(w * 0.08f, w * 0.92f)
                    val targetPivotY = (cy - (ny * cy * 0.70f)).coerceIn(h * 0.08f, h * 0.92f)
                    v.pivotX = targetPivotX
                    v.pivotY = targetPivotY

                    // Screen bounds detection: calculate maximum safe stretch without clipping screen borders
                    val loc = IntArray(2)
                    v.getLocationOnScreen(loc)
                    val screenW = v.resources.displayMetrics.widthPixels
                    val screenH = v.resources.displayMetrics.heightPixels
                    val distLeft = loc[0].coerceAtLeast(0)
                    val distRight = (screenW - (loc[0] + v.width)).coerceAtLeast(0)
                    val distTop = loc[1].coerceAtLeast(0)
                    val distBottom = (screenH - (loc[1] + v.height)).coerceAtLeast(0)

                    val maxStretchX = if (w - targetPivotX > 1f) (distRight * 0.70f) / (w - targetPivotX) else 0.08f
                    val maxSafeScaleX = 1.0f + minOf(maxStretchX, if (targetPivotX > 1f) (distLeft * 0.70f) / targetPivotX else 0.08f).coerceIn(0.0f, 0.08f)

                    val maxStretchY = if (h - targetPivotY > 1f) (distBottom * 0.70f) / (h - targetPivotY) else 0.08f
                    val maxSafeScaleY = 1.0f + minOf(maxStretchY, if (targetPivotY > 1f) (distTop * 0.70f) / targetPivotY else 0.08f).coerceIn(0.0f, 0.08f)

                    // Gooey rubber indentation: pressed point depresses, lateral axes stretch with Poisson volume conservation
                    val stretchX = Math.abs(nx) * 0.08f
                    val stretchY = Math.abs(ny) * 0.08f
                    val targetScaleX = (holder.pressedScale * (1.0f + stretchX - stretchY * 0.45f)).coerceIn(0.86f, maxSafeScaleX)
                    val targetScaleY = (holder.pressedScale * (1.0f + stretchY - stretchX * 0.45f)).coerceIn(0.86f, maxSafeScaleY)

                    holder.springX.animateToFinalPosition(targetScaleX)
                    holder.springY.animateToFinalPosition(targetScaleY)
                    v.vibrateTick()
                }
                MotionEvent.ACTION_MOVE -> {
                    glossDrawable.onTouchMove(event.x, event.y)
                    if (cx > 0 && cy > 0) {
                        val nx = ((event.x - cx) / cx).coerceIn(-1.25f, 1.25f)
                        val ny = ((event.y - cy) / cy).coerceIn(-1.25f, 1.25f)

                        // Dynamic gooey pivot: deeply anchors opposite to drag vector
                        val targetPivotX = (cx - (nx * cx * 0.75f)).coerceIn(w * 0.08f, w * 0.92f)
                        val targetPivotY = (cy - (ny * cy * 0.75f)).coerceIn(h * 0.08f, h * 0.92f)
                        v.pivotX = targetPivotX
                        v.pivotY = targetPivotY

                        val loc = IntArray(2)
                        v.getLocationOnScreen(loc)
                        val screenW = v.resources.displayMetrics.widthPixels
                        val screenH = v.resources.displayMetrics.heightPixels
                        val distLeft = loc[0].coerceAtLeast(0)
                        val distRight = (screenW - (loc[0] + v.width)).coerceAtLeast(0)
                        val distTop = loc[1].coerceAtLeast(0)
                        val distBottom = (screenH - (loc[1] + v.height)).coerceAtLeast(0)

                        val maxStretchX = if (w - targetPivotX > 1f) (distRight * 0.70f) / (w - targetPivotX) else 0.08f
                        val maxSafeScaleX = 1.0f + minOf(maxStretchX, if (targetPivotX > 1f) (distLeft * 0.70f) / targetPivotX else 0.08f).coerceIn(0.0f, 0.08f)

                        val maxStretchY = if (h - targetPivotY > 1f) (distBottom * 0.70f) / (h - targetPivotY) else 0.08f
                        val maxSafeScaleY = 1.0f + minOf(maxStretchY, if (targetPivotY > 1f) (distTop * 0.70f) / targetPivotY else 0.08f).coerceIn(0.0f, 0.08f)

                        // Asymmetric Poisson rubber stretch (real gooey liquid elasticity):
                        // Pulling along one axis thins the orthogonal axis!
                        val stretchX = Math.abs(nx) * 0.13f
                        val stretchY = Math.abs(ny) * 0.13f
                        val targetScaleX = (holder.pressedScale * (1.0f + stretchX - stretchY * 0.50f)).coerceIn(0.86f, maxSafeScaleX)
                        val targetScaleY = (holder.pressedScale * (1.0f + stretchY - stretchX * 0.50f)).coerceIn(0.86f, maxSafeScaleY)

                        holder.springX.animateToFinalPosition(targetScaleX)
                        holder.springY.animateToFinalPosition(targetScaleY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    glossDrawable.onTouchUp()
                    // Rubbery snapback with harmonic jiggle (underdamped spring settling)
                    holder.springX.animateToFinalPosition(holder.restingScale)
                    holder.springY.animateToFinalPosition(holder.restingScale)

                    // Smoothly reset pivot to center as the spring settles
                    v.postDelayed({
                        if (holder.springX.spring.finalPosition == holder.restingScale) {
                            v.pivotX = v.width / 2f
                            v.pivotY = v.height / 2f
                        }
                    }, 260)

                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val dx = Math.abs(event.rawX - downRawX)
                        val dy = Math.abs(event.rawY - downRawY)
                        if (dx < touchSlop && dy < touchSlop) {
                            v.vibrateDetent()
                            v.performClick()
                        }
                    }
                }
            }

            onTouchListener?.onTouch(v, event) ?: false
        }
    }

    /**
     * Attaches spring touch animation to a View.
     * Preserves existing click handling by returning false in OnTouchListener.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        view: View,
        pressedScale: Float = DEFAULT_PRESSED_SCALE,
        stiffness: Float = DEFAULT_STIFFNESS,
        dampingRatio: Float = DEFAULT_DAMPING_RATIO,
        onTouchListener: View.OnTouchListener? = null
    ) {
        val forceX = SpringForce().apply {
            this.stiffness = stiffness
            this.dampingRatio = dampingRatio
            this.finalPosition = DEFAULT_RESTING_SCALE
        }
        val forceY = SpringForce().apply {
            this.stiffness = stiffness
            this.dampingRatio = dampingRatio
            this.finalPosition = DEFAULT_RESTING_SCALE
        }

        val springX = SpringAnimation(view, DynamicAnimation.SCALE_X).setSpring(forceX)
        val springY = SpringAnimation(view, DynamicAnimation.SCALE_Y).setSpring(forceY)

        val holder = SpringHolder(
            springX = springX,
            springY = springY,
            pressedScale = pressedScale,
            restingScale = DEFAULT_RESTING_SCALE
        )
        springRegistry[view] = holder

        // Ensure interactive views are clickable so onTouchEvent receives full gesture stream
        if (!view.isClickable && !view.isFocusable) {
            view.isClickable = true
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.pivotX = v.width / 2f
                    v.pivotY = v.height / 2f
                    holder.springX.animateToFinalPosition(holder.pressedScale)
                    holder.springY.animateToFinalPosition(holder.pressedScale)
                }
                MotionEvent.ACTION_MOVE -> {
                    val isInside = event.x in 0f..v.width.toFloat() &&
                            event.y in 0f..v.height.toFloat()
                    val targetScale = if (isInside) holder.pressedScale else holder.restingScale
                    if (holder.springX.spring.finalPosition != targetScale) {
                        holder.springX.animateToFinalPosition(targetScale)
                        holder.springY.animateToFinalPosition(targetScale)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holder.springX.animateToFinalPosition(holder.restingScale)
                    holder.springY.animateToFinalPosition(holder.restingScale)
                }
            }

            // Chain caller's onTouchListener if provided; otherwise return false so onTouchEvent handles performClick
            onTouchListener?.onTouch(v, event) ?: false
        }
    }

    /**
     * Cancels active springs and resets view scale and rotation to resting state.
     */
    fun cancel(view: View) {
        springRegistry[view]?.let { holder ->
            if (holder.springX.isRunning) holder.springX.cancel()
            if (holder.springY.isRunning) holder.springY.cancel()
        }
        view.scaleX = DEFAULT_RESTING_SCALE
        view.scaleY = DEFAULT_RESTING_SCALE
        view.rotationX = 0f
        view.rotationY = 0f
        view.translationX = 0f
        view.translationY = 0f
    }

    /**
     * Detaches spring animation from the view and removes touch listener.
     */
    fun detach(view: View) {
        springRegistry[view]?.glossDrawable?.let { view.overlay.remove(it) }
        cancel(view)
        springRegistry.remove(view)
        view.setOnTouchListener(null)
    }

    /**
     * Pure mathematical and physical model of the second-order harmonic oscillator.
     * Decoupled from Android UI hierarchy for pure JVM testing and verification.
     */
    object Physics {
        const val STIFFNESS = DEFAULT_STIFFNESS
        const val DAMPING_RATIO = DEFAULT_DAMPING_RATIO
        const val PRESSED_SCALE = DEFAULT_PRESSED_SCALE
        const val RESTING_SCALE = DEFAULT_RESTING_SCALE

        /**
         * Undamped natural angular frequency: omega_0 = sqrt(k / m)
         */
        fun angularFrequency(stiffness: Float = STIFFNESS, mass: Float = 1.0f): Float {
            require(stiffness > 0f) { "Stiffness must be positive" }
            require(mass > 0f) { "Mass must be positive" }
            return Math.sqrt((stiffness / mass).toDouble()).toFloat()
        }

        /**
         * Damped natural angular frequency: omega_d = omega_0 * sqrt(1 - zeta^2)
         */
        fun dampedFrequency(
            stiffness: Float = STIFFNESS,
            dampingRatio: Float = DAMPING_RATIO,
            mass: Float = 1.0f
        ): Float {
            require(dampingRatio in 0f..<1f) { "Damping ratio must be in [0, 1) for underdamped oscillation" }
            val w0 = angularFrequency(stiffness, mass)
            return (w0 * Math.sqrt((1.0 - dampingRatio * dampingRatio).toDouble())).toFloat()
        }

        /**
         * Calculates displacement x(t) for underdamped harmonic oscillator.
         * x(t) = e^(-zeta * w0 * t) * (x0 * cos(wd * t) + ((v0 + zeta * w0 * x0) / wd) * sin(wd * t))
         */
        fun calculateDisplacement(
            t: Float,
            x0: Float,
            v0: Float = 0f,
            stiffness: Float = STIFFNESS,
            dampingRatio: Float = DAMPING_RATIO,
            mass: Float = 1.0f
        ): Float {
            if (t <= 0f) return x0
            val w0 = angularFrequency(stiffness, mass)
            val wd = dampedFrequency(stiffness, dampingRatio, mass)
            val decay = Math.exp((-dampingRatio * w0 * t).toDouble()).toFloat()
            val c1 = x0
            val c2 = (v0 + dampingRatio * w0 * x0) / wd
            return decay * (c1 * Math.cos((wd * t).toDouble()).toFloat() + c2 * Math.sin((wd * t).toDouble()).toFloat())
        }

        /**
         * Calculates decay envelope fraction: e^(-zeta * w0 * t)
         */
        fun calculateDecayEnvelope(
            t: Float,
            stiffness: Float = STIFFNESS,
            dampingRatio: Float = DAMPING_RATIO,
            mass: Float = 1.0f
        ): Float {
            if (t <= 0f) return 1.0f
            val w0 = angularFrequency(stiffness, mass)
            return Math.exp((-dampingRatio * w0 * t).toDouble()).toFloat()
        }

        /**
         * Calculates theoretical settling time to reach within a specified tolerance fraction (e.g. 0.05 for 5%).
         * t_settle = -ln(tolerance) / (zeta * omega_0)
         */
        fun settlingTime(
            tolerance: Float = 0.05f,
            stiffness: Float = STIFFNESS,
            dampingRatio: Float = DAMPING_RATIO,
            mass: Float = 1.0f
        ): Float {
            require(tolerance > 0f && tolerance < 1f) { "Tolerance must be in (0, 1)" }
            val w0 = angularFrequency(stiffness, mass)
            val decayRate = dampingRatio * w0
            return (-Math.log(tolerance.toDouble()) / decayRate).toFloat()
        }

        /**
         * Volume-preserving aspect ratio squish: Sx * Sy = 1.0 -> Sx = 1.0 / Sy
         */
        fun computeConservedVolumeScaleX(scaleY: Float): Float {
            require(scaleY > 0f) { "Scale must be positive" }
            return 1.0f / scaleY
        }

        /**
         * Validates whether damping ratio is underdamped (playful, snappy).
         */
        fun isUnderdamped(dampingRatio: Float = DAMPING_RATIO): Boolean {
            return dampingRatio in 0.0f..<1.0f
        }
    }
}

/**
 * Idiomatic extension function to attach touch spring animation directly to any View.
 */
fun View.attachTouchSpring(
    pressedScale: Float = TouchSpringAnimator.DEFAULT_PRESSED_SCALE,
    stiffness: Float = TouchSpringAnimator.DEFAULT_STIFFNESS,
    dampingRatio: Float = TouchSpringAnimator.DEFAULT_DAMPING_RATIO
): View {
    TouchSpringAnimator.attach(this, pressedScale, stiffness, dampingRatio)
    return this
}

/**
 * Idiomatic extension function to attach Apple Liquid Glass anchored gooey spring physics.
 */
fun View.applyLiquidGlass(
    enableStretch: Boolean = true,
    pressedScale: Float = 0.94f
): View {
    TouchSpringAnimator.attachLiquidGlass(this, pressedScale = pressedScale, enableStretch = enableStretch)
    return this
}
