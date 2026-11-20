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
     * Apple-style Liquid Glass subtle specular touch glow drawable.
     * Follows sdegenaar/liquid_glass_widgets architecture:
     * - ZERO glow at rest (resting intensity = 0.0f; absolutely no gradient bands across the card).
     * - On touch, dynamic optical glow appears directly where the finger touches and tracks the contact point.
     * - Continuous-curvature squircle refractive rim highlight.
     * - On release, smoothly dissolves back to 0.0f at the release location.
     */
    class LiquidGlassGlossDrawable(private val targetView: View) : android.graphics.drawable.Drawable() {
        private val rimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            isDither = true
        }
        private val clipPath = android.graphics.Path()
        private val rectF = android.graphics.RectF()

        // 0.0f at rest — zero gradient or artifact when idle
        private var glossIntensity = 0.0f
        private var touchX = 0f
        private var touchY = 0f
        private var fadeAnimator: android.animation.ValueAnimator? = null

        fun onTouchDown(x: Float, y: Float) {
            fadeAnimator?.cancel()
            touchX = x
            touchY = y
            fadeAnimator = android.animation.ValueAnimator.ofFloat(glossIntensity, 1.0f).apply {
                duration = 120
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
            val dx = x - touchX
            val dy = y - touchY
            if (dx * dx + dy * dy >= 9f) { // 3px deadband filter
                touchX = x
                touchY = y
                if (glossIntensity < 0.95f) {
                    glossIntensity = 1.0f
                }
                targetView.invalidate()
                invalidateSelf()
            }
        }

        fun onTouchUp() {
            fadeAnimator?.cancel()
            fadeAnimator = android.animation.ValueAnimator.ofFloat(glossIntensity, 0.0f).apply {
                duration = 220
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
            if (glossIntensity <= 0.001f) return
            val w = targetView.width.toFloat()
            val h = targetView.height.toFloat()
            if (w <= 0 || h <= 0) return

            val density = targetView.resources.displayMetrics.density
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
                android.graphics.Color.argb(peakRimAlpha, 255, 255, 255),
                android.graphics.Color.argb(midRimAlpha, 255, 255, 255),
                android.graphics.Color.argb(0, 255, 255, 255)
            )
            val rimStops = floatArrayOf(0.0f, 0.45f, 1.0f)

            rimPaint.strokeWidth = strokeW
            rimPaint.shader = android.graphics.RadialGradient(
                touchX, touchY, rimGlowRadius, rimColors, rimStops, android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, rimPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /**
     * Signed smoothstep from sdegenaar/liquid_glass_widgets (stretch.dart):
     * Maps [-edge..+edge] -> [-1..+1] with smooth cubic easing.
     * Glides pivot through center to prevent sudden edge jumps.
     */
    private fun signedSmoothStep(x: Float, edge: Float): Float {
        if (edge <= 0.001f) return 0f
        val t = (x / edge).coerceIn(-1.0f, 1.0f)
        val a = Math.abs(t)
        val smooth = a * a * (3.0f - 2.0f * a)
        return if (t >= 0f) smooth else -smooth
    }

    /**
     * Attaches Apple-style anchored gooey rubber stretch physics and subtle liquid glass gloss.
     * Follows sdegenaar/liquid_glass_widgets architecture:
     * - Zero glow at rest (clean smoked glass, no static gradients).
     * - Touch-responsive optical glow directly under the finger at the contact point.
     * - Aspect-ratio normalized jelly stretch & squash (volume conservation).
     * - Dynamic screen bounds detection preventing any border clipping.
     * - Rubbery harmonic spring settling on release.
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
            stiffness = 260f        // Gooey rubber elasticity (pliable elastomer)
            dampingRatio = 0.55f    // Playful underdamped harmonic bounce
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
        var downX = 0f
        var downY = 0f
        val touchSlop = 20f * density

        view.setOnTouchListener { v, event ->
            val w = v.width.toFloat()
            val h = v.height.toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = event.x
                    downY = event.y
                    glossDrawable.onTouchDown(event.x, event.y)

                    v.pivotX = w / 2f
                    v.pivotY = h / 2f
                    holder.springX.animateToFinalPosition(holder.pressedScale)
                    holder.springY.animateToFinalPosition(holder.pressedScale)
                    v.vibrateTick()
                }
                MotionEvent.ACTION_MOVE -> {
                    glossDrawable.onTouchMove(event.x, event.y)
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    val absDx = Math.abs(deltaX)
                    val absDy = Math.abs(deltaY)

                    if (w > 0 && h > 0 && (absDx > 0.001f || absDy > 0.001f)) {
                        // Normalise both axes against the SAME reference dimension (max(w, h))
                        val refDim = kotlin.math.max(w, h)
                        val relativeX = if (refDim > 0f) absDx / refDim else 0f
                        val relativeY = if (refDim > 0f) absDy / refDim else 0f

                        // Aspect-ratio correction: dampens stretch along the already-long axis
                        val aspectRatio = w / h
                        val xDamping = if (aspectRatio > 1.0f) (1.0f / aspectRatio).coerceIn(0.20f, 1.0f) else 1.0f
                        val yDamping = if (aspectRatio < 1.0f) aspectRatio.coerceIn(0.20f, 1.0f) else 1.0f

                        // Elongate along drag direction, compress perpendicular dimension (jelly volume conservation)
                        val stretchIntensity = 0.50f
                        val squashFactor = 0.30f
                        val stretchX = 1.0f + (relativeX * stretchIntensity * xDamping).coerceIn(0.0f, 0.22f)
                        val stretchY = 1.0f + (relativeY * stretchIntensity * yDamping).coerceIn(0.0f, 0.22f)
                        val squashX = 1.0f - (relativeY * squashFactor * xDamping).coerceIn(0.0f, 0.12f)
                        val squashY = 1.0f - (relativeX * squashFactor * yDamping).coerceIn(0.0f, 0.12f)

                        // Glide pivot through center using signed smoothstep (prevents sudden edge jumping)
                        val pivotX = w * (0.5f - signedSmoothStep(deltaX, 24f * density) * 0.5f)
                        val pivotY = h * (0.5f - signedSmoothStep(deltaY, 24f * density) * 0.5f)
                        v.pivotX = pivotX
                        v.pivotY = pivotY

                        // Screen bounds detection: dynamically compute maximum safe stretch
                        val loc = IntArray(2)
                        v.getLocationOnScreen(loc)
                        val screenW = v.resources.displayMetrics.widthPixels
                        val screenH = v.resources.displayMetrics.heightPixels
                        val distLeft = loc[0].coerceAtLeast(0)
                        val distRight = (screenW - (loc[0] + v.width)).coerceAtLeast(0)
                        val distTop = loc[1].coerceAtLeast(0)
                        val distBottom = (screenH - (loc[1] + v.height)).coerceAtLeast(0)

                        val maxStretchX = if (w - pivotX > 1f) (distRight * 0.70f) / (w - pivotX) else 0.08f
                        val maxSafeScaleX = 1.0f + minOf(maxStretchX, if (pivotX > 1f) (distLeft * 0.70f) / pivotX else 0.08f).coerceIn(0.0f, 0.08f)

                        val maxStretchY = if (h - pivotY > 1f) (distBottom * 0.70f) / (h - pivotY) else 0.08f
                        val maxSafeScaleY = 1.0f + minOf(maxStretchY, if (pivotY > 1f) (distTop * 0.70f) / pivotY else 0.08f).coerceIn(0.0f, 0.08f)

                        val targetScaleX = (holder.pressedScale * (stretchX * squashX)).coerceIn(0.85f, maxSafeScaleX)
                        val targetScaleY = (holder.pressedScale * (stretchY * squashY)).coerceIn(0.85f, maxSafeScaleY)

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

            val listenerHandled = onTouchListener?.onTouch(v, event) ?: false
            listenerHandled || true
        }

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                glossDrawable.onTouchUp()
                holder.springX.animateToFinalPosition(holder.restingScale)
                holder.springY.animateToFinalPosition(holder.restingScale)
            }
        })
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
