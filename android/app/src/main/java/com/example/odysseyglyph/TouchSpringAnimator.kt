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
        val springTransX: SpringAnimation? = null,
        val springTransY: SpringAnimation? = null,
        val springRotX: SpringAnimation? = null,
        val springRotY: SpringAnimation? = null
    )

    private val springRegistry = WeakHashMap<View, SpringHolder>()

    /**
     * Attaches Apple Liquid Glass anchored gooey spring physics.
     * The view remains anchored to its layout socket (strictly leashed within 18dp displacement),
     * fluidly stretching along the user's drag vector with viscous rubber-band resistance and 3D tilt.
     * On release, it snaps back to (0, 0) with a bouncy harmonic spring settling and haptic detents.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachLiquidGlass(
        view: View,
        pressedScale: Float = 0.94f,
        enableStretch: Boolean = true,
        onTouchListener: View.OnTouchListener? = null
    ) {
        val density = view.resources.displayMetrics.density

        val scaleForce = SpringForce().apply {
            stiffness = 340f
            dampingRatio = 0.62f // Bouncy, juicy harmonic spring with overshoot
            finalPosition = DEFAULT_RESTING_SCALE
        }
        val transForce = SpringForce().apply {
            stiffness = 380f
            dampingRatio = 0.60f // Elastic snapback to anchor point
            finalPosition = 0f
        }
        val rotForce = SpringForce().apply {
            stiffness = 380f
            dampingRatio = 0.68f
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
        val springTransX = SpringAnimation(view, DynamicAnimation.TRANSLATION_X).setSpring(SpringForce().apply {
            stiffness = transForce.stiffness
            dampingRatio = transForce.dampingRatio
            finalPosition = 0f
        })
        val springTransY = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y).setSpring(SpringForce().apply {
            stiffness = transForce.stiffness
            dampingRatio = transForce.dampingRatio
            finalPosition = 0f
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

        view.cameraDistance = 8000f * density

        val holder = SpringHolder(
            springX = springX,
            springY = springY,
            pressedScale = pressedScale,
            restingScale = DEFAULT_RESTING_SCALE,
            springTransX = springTransX,
            springTransY = springTransY,
            springRotX = springRotX,
            springRotY = springRotY
        )
        springRegistry[view] = holder

        if (!view.isClickable && !view.isFocusable) {
            view.isClickable = true
        }

        var downRawX = 0f
        var downRawY = 0f
        val touchSlop = 20f * density

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    v.pivotX = v.width / 2f
                    v.pivotY = v.height / 2f
                    holder.springX.animateToFinalPosition(holder.pressedScale)
                    holder.springY.animateToFinalPosition(holder.pressedScale)
                    v.vibrateTick()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val rawDist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                    // Strict Apple-style anchored leash (max 18dp displacement from anchor position)
                    val maxLeash = 18f * density
                    val pullSens = 0.32f
                    // Asymptotic hyperbolic rubber-band resistance curve: dampedDist strictly bounded in [0, maxLeash)
                    val dampedDist = maxLeash * (1.0f - (1.0f / (1.0f + (rawDist * pullSens / maxLeash))))

                    val angle = Math.atan2(dy.toDouble(), dx.toDouble())
                    val tx = (dampedDist * Math.cos(angle)).toFloat()
                    val ty = (dampedDist * Math.sin(angle)).toFloat()

                    // Viscous liquid fluid stretch: elongates along pull axis, subtly compresses orthogonal axis
                    val stretchFactor = (dampedDist / maxLeash) * 0.08f
                    val cos2 = (Math.cos(angle) * Math.cos(angle)).toFloat()
                    val sin2 = (Math.sin(angle) * Math.sin(angle)).toFloat()

                    val targetScaleX = holder.pressedScale + (stretchFactor * cos2) - (stretchFactor * 0.35f * sin2)
                    val targetScaleY = holder.pressedScale + (stretchFactor * sin2) - (stretchFactor * 0.35f * cos2)

                    // Directional 3D perspective tilt proportional to pull
                    val targetRotY = (tx / maxLeash * 6.0f).coerceIn(-6.5f, 6.5f)
                    val targetRotX = (-ty / maxLeash * 6.0f).coerceIn(-6.5f, 6.5f)

                    holder.springX.animateToFinalPosition(targetScaleX)
                    holder.springY.animateToFinalPosition(targetScaleY)
                    holder.springTransX?.animateToFinalPosition(tx)
                    holder.springTransY?.animateToFinalPosition(ty)
                    holder.springRotX?.animateToFinalPosition(targetRotX)
                    holder.springRotY?.animateToFinalPosition(targetRotY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holder.springX.animateToFinalPosition(holder.restingScale)
                    holder.springY.animateToFinalPosition(holder.restingScale)
                    holder.springTransX?.animateToFinalPosition(0f)
                    holder.springTransY?.animateToFinalPosition(0f)
                    holder.springRotX?.animateToFinalPosition(0f)
                    holder.springRotY?.animateToFinalPosition(0f)

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
 * Idiomatic extension function to attach Apple Liquid Glass anchored gooey spring physics.
 */
fun View.applyLiquidGlass(
    enableStretch: Boolean = true,
    pressedScale: Float = 0.94f
): View {
    TouchSpringAnimator.attachLiquidGlass(this, pressedScale = pressedScale, enableStretch = enableStretch)
    return this
}
