package com.example.odysseyglyph.e2e.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Reusable Physics and Kinematics Test Assertions.
 * Validates Apple asymptotic rubber-banding, second-order harmonic oscillator spring mechanics,
 * and dynamic volume conservation invariants.
 */
object PhysicsTestAssertions {

    const val RUBBERBAND_COEFFICIENT_C = 0.55f
    const val SPRING_STIFFNESS_K = 380f
    const val SPRING_DAMPING_ZETA = 0.78f
    const val PRESSED_SCALE = 0.96f
    const val RESTING_SCALE = 1.0f

    /**
     * Apple Asymptotic Rubberband formula:
     * x_dampened = (x * D * c) / (D + c * |x|)
     */
    fun calculateRubberbandDisplacement(
        delta: Float,
        dimension: Float,
        c: Float = RUBBERBAND_COEFFICIENT_C
    ): Float {
        if (dimension <= 0f) return 0f
        val absDelta = abs(delta)
        val denominator = dimension + c * absDelta
        return if (denominator == 0f) 0f else (delta * dimension * c) / denominator
    }

    /**
     * Asserts that observed rubberband displacement matches the theoretical asymptotic value
     * within a specified tolerance.
     */
    fun assertAsymptoticDamping(
        delta: Float,
        dimension: Float,
        observed: Float,
        tolerance: Float = 0.05f
    ) {
        val expected = calculateRubberbandDisplacement(delta, dimension)
        assertEquals(
            "Rubberband displacement for delta=$delta, D=$dimension did not match expected formula",
            expected,
            observed,
            tolerance
        )
    }

    /**
     * Asserts spring kinematics configuration matches hardware console specification:
     * k ≈ 380 N/m, zeta ≈ 0.78.
     */
    fun assertSpringKinematics(
        stiffness: Float,
        dampingRatio: Float,
        expectedK: Float = SPRING_STIFFNESS_K,
        expectedZeta: Float = SPRING_DAMPING_ZETA,
        tolerance: Float = 1.0f
    ) {
        assertEquals("Spring stiffness mismatch", expectedK, stiffness, tolerance)
        assertEquals("Spring damping ratio mismatch", expectedZeta, dampingRatio, 0.05f)
        assertTrue("Spring must be underdamped (zeta < 1.0)", dampingRatio < 1.0f)
        assertTrue("Spring must have positive damping", dampingRatio > 0f)
    }

    /**
     * Calculates natural angular frequency omega_0 = sqrt(k / m).
     */
    fun calculateOmega0(stiffness: Float = SPRING_STIFFNESS_K, mass: Float = 1.0f): Float {
        require(stiffness > 0f && mass > 0f) { "Stiffness and mass must be positive" }
        return sqrt(stiffness / mass)
    }

    /**
     * Calculates damped angular frequency omega_d = omega_0 * sqrt(1 - zeta^2).
     */
    fun calculateOmegaD(
        stiffness: Float = SPRING_STIFFNESS_K,
        dampingRatio: Float = SPRING_DAMPING_ZETA,
        mass: Float = 1.0f
    ): Float {
        val omega0 = calculateOmega0(stiffness, mass)
        val factor = sqrt((1.0 - dampingRatio * dampingRatio).toFloat())
        return omega0 * factor
    }

    /**
     * Evaluates harmonic decay envelope fraction: e^(-zeta * omega_0 * t).
     */
    fun calculateDecayEnvelope(
        t: Float,
        stiffness: Float = SPRING_STIFFNESS_K,
        dampingRatio: Float = SPRING_DAMPING_ZETA,
        mass: Float = 1.0f
    ): Float {
        if (t <= 0f) return 1.0f
        val omega0 = calculateOmega0(stiffness, mass)
        return exp(-dampingRatio * omega0 * t)
    }

    /**
     * Theoretical settling time to reach within tolerance fraction (default 5% = 0.05).
     * t_settle = -ln(tolerance) / (zeta * omega_0)
     */
    fun calculateSettlingTime(
        tolerance: Float = 0.05f,
        stiffness: Float = SPRING_STIFFNESS_K,
        dampingRatio: Float = SPRING_DAMPING_ZETA,
        mass: Float = 1.0f
    ): Float {
        require(tolerance in 0.001f..0.999f) { "Tolerance must be in (0, 1)" }
        val omega0 = calculateOmega0(stiffness, mass)
        return (-ln(tolerance) / (dampingRatio * omega0))
    }

    /**
     * Asserts volume conservation during morphing/scaling:
     * Sx * Sy ≈ 1.0 (within tolerance).
     */
    fun assertVolumeConservation(scaleX: Float, scaleY: Float, tolerance: Float = 0.05f) {
        val product = scaleX * scaleY
        assertEquals("Volume conservation violated (Sx * Sy != 1.0)", 1.0f, product, tolerance)
    }
}
