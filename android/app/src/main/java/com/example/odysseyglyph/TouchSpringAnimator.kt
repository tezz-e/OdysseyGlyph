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
        val springRotX: SpringAnimation? = null,
        val springRotY: SpringAnimation? = null
    )

    private val springRegistry = WeakHashMap<View, SpringHolder>()

    /**
     * Attaches Apple Liquid Glass press compression, 3D perspective tilt, and snapback physics.
     * The view remains strictly anchored at its layout position (zero translation), responding
     * fluidly with depth compression and subtle directional 3D tilt tracking the user's touch.
     * Moving outside the view bounds gracefully cancels the press; releasing snaps back crisply
     * with high-stiffness harmonic settling and haptic detents.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachLiquidGlass(
        view: View,
        pressedScale: Float = 0.96f,
        enableStretch: Boolean = false,
        onTouchListener: View.OnTouchListener? = null
    ) {
        val density = view.resources.displayMetrics.density

        val scaleForce = SpringForce().apply {
            stiffness = 450f
            dampingRatio = 0.78f // Snappy, firm Apple mechanical snapback
            finalPosition = DEFAULT_RESTING_SCALE
        }
        val rotForce = SpringForce().apply {
            stiffness = 400f
            dampingRatio = 0.75f
            finalPosition = 0f
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
        val springRotX = SpringAnimation(view, DynamicAnimation.ROTATION_X).setSpring(SpringForce().apply {
            stiffness = rotForce.stiffness
            dampingRatio = rotForce.dampingRatio
            finalPosition = 0f
        })
        val springRotY = SpringAnimation(view, DynamicAnimation.ROTATION_Y).setSpring(SpringForce().apply {
            stiffness = rotForce.stiffness
            dampingRatio = rotForce.dampingRatio
            finalPosition = 0f
        })

        // Anchor view firmly in place - zero translation
        view.cameraDistance = 8000f * density
        view.translationX = 0f
        view.translationY = 0f

        val holder = SpringHolder(
            springX = springX,
            springY = springY,
            pressedScale = pressedScale,
            restingScale = DEFAULT_RESTING_SCALE,
            springRotX = springRotX,
            springRotY = springRotY
        )
        springRegistry[view] = holder

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

                    val halfW = v.width / 2f
                    val halfH = v.height / 2f
                    if (halfW > 0 && halfH > 0) {
                        val relX = ((event.x - halfW) / halfW).coerceIn(-1f, 1f)
                        val relY = ((event.y - halfH) / halfH).coerceIn(-1f, 1f)
                        holder.springRotY?.animateToFinalPosition(relX * 3.5f)
                        holder.springRotX?.animateToFinalPosition(-relY * 3.5f)
                    }
                    v.vibrateTick()
                }
                MotionEvent.ACTION_MOVE -> {
                    val halfW = v.width / 2f
                    val halfH = v.height / 2f
                    val isInside = event.x in 0f..v.width.toFloat() &&
                            event.y in 0f..v.height.toFloat()

                    if (isInside && halfW > 0 && halfH > 0) {
                        val relX = ((event.x - halfW) / halfW).coerceIn(-1f, 1f)
                        val relY = ((event.y - halfH) / halfH).coerceIn(-1f, 1f)
                        holder.springRotY?.animateToFinalPosition(relX * 3.5f)
                        holder.springRotX?.animateToFinalPosition(-relY * 3.5f)
                        if (holder.springX.spring.finalPosition != holder.pressedScale) {
                            holder.springX.animateToFinalPosition(holder.pressedScale)
                            holder.springY.animateToFinalPosition(holder.pressedScale)
                        }
                    } else {
                        // Touch dragged outside: smoothly uncompress and level tilt (cancels press)
                        if (holder.springX.spring.finalPosition != holder.restingScale) {
                            holder.springX.animateToFinalPosition(holder.restingScale)
                            holder.springY.animateToFinalPosition(holder.restingScale)
                            holder.springRotX?.animateToFinalPosition(0f)
                            holder.springRotY?.animateToFinalPosition(0f)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    holder.springX.animateToFinalPosition(holder.restingScale)
                    holder.springY.animateToFinalPosition(holder.restingScale)
                    holder.springRotX?.animateToFinalPosition(0f)
                    holder.springRotY?.animateToFinalPosition(0f)
                    val isInside = event.x in 0f..v.width.toFloat() &&
                            event.y in 0f..v.height.toFloat()
                    if (isInside) {
                        v.vibrateDetent()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    holder.springX.animateToFinalPosition(holder.restingScale)
                    holder.springY.animateToFinalPosition(holder.restingScale)
                    holder.springRotX?.animateToFinalPosition(0f)
                    holder.springRotY?.animateToFinalPosition(0f)
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
            if (holder.springRotX?.isRunning == true) holder.springRotX.cancel()
            if (holder.springRotY?.isRunning == true) holder.springRotY.cancel()
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
 * Idiomatic extension function to attach Apple Liquid Glass press compression, tilt, and spring physics.
 */
fun View.applyLiquidGlass(
    enableStretch: Boolean = false,
    pressedScale: Float = 0.96f
): View {
    TouchSpringAnimator.attachLiquidGlass(this, pressedScale = pressedScale, enableStretch = enableStretch)
    return this
}
