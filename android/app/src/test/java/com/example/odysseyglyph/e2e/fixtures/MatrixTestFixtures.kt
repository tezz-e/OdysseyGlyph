package com.example.odysseyglyph.e2e.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Reusable Matrix Test Fixtures for 25x25 (625 diodes) Glyph circular displays.
 * Implements authoritative mathematical constants and validation formulas.
 */
object MatrixTestFixtures {

    const val MATRIX_WIDTH = 25
    const val MATRIX_HEIGHT = 25
    const val TOTAL_DIODES = 625 // 25 * 25
    const val CENTER_X = 12.0f
    const val CENTER_Y = 12.0f
    const val MASK_RADIUS = 12.5f
    const val MASK_RADIUS_SQ = 156.25f // 12.5 * 12.5

    const val EXPECTED_ACTIVE_DIODES = 489
    const val EXPECTED_MASKED_DIODES = 136

    /**
     * Determines whether diode coordinate (x, y) resides within the circular mask:
     * (x - 12.0)^2 + (y - 12.0)^2 <= 12.5^2 = 156.25
     */
    fun isWithinCircularMask(x: Int, y: Int): Boolean {
        val dx = x - CENTER_X
        val dy = y - CENTER_Y
        return (dx * dx + dy * dy) <= MASK_RADIUS_SQ
    }

    /**
     * Counts active unmasked diodes within the 25x25 grid.
     * Authoritative ground truth: exactly 489 diodes.
     */
    fun countUnmaskedDiodes(): Int {
        var count = 0
        for (y in 0 until MATRIX_HEIGHT) {
            for (x in 0 until MATRIX_WIDTH) {
                if (isWithinCircularMask(x, y)) {
                    count++
                }
            }
        }
        return count
    }

    /**
     * Counts diodes outside the circular mask.
     * Authoritative ground truth: exactly 136 diodes.
     */
    fun countMaskedDiodes(): Int = TOTAL_DIODES - countUnmaskedDiodes()

    /**
     * Creates a 625-byte frame with uniform brightness.
     */
    fun createSolidFrame(brightness: Byte): ByteArray {
        return ByteArray(TOTAL_DIODES) { brightness }
    }

    /**
     * Creates an alternating checkerboard pattern across 25x25 grid.
     */
    fun createCheckerboardFrame(): ByteArray {
        val frame = ByteArray(TOTAL_DIODES)
        for (y in 0 until MATRIX_HEIGHT) {
            for (x in 0 until MATRIX_WIDTH) {
                val index = y * MATRIX_WIDTH + x
                frame[index] = if ((x + y) % 2 == 0) 255.toByte() else 0.toByte()
            }
        }
        return frame
    }

    /**
     * Creates a circular impulse/dot frame centered at (cx, cy) with given radius.
     */
    fun createImpulseFrame(
        cx: Int = 12,
        cy: Int = 12,
        radius: Float = 3f,
        brightness: Byte = 255.toByte()
    ): ByteArray {
        val frame = ByteArray(TOTAL_DIODES)
        val rSq = radius * radius
        for (y in 0 until MATRIX_HEIGHT) {
            for (x in 0 until MATRIX_WIDTH) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= rSq && isWithinCircularMask(x, y)) {
                    frame[y * MATRIX_WIDTH + x] = brightness
                }
            }
        }
        return frame
    }

    /**
     * Applies the circular mask, zeroing out all diodes where (x-12)^2 + (y-12)^2 > 156.25.
     */
    fun applyCircularMask(frame: ByteArray): ByteArray {
        require(frame.size == TOTAL_DIODES) { "Frame size must be 625 bytes, was ${frame.size}" }
        val masked = frame.clone()
        for (y in 0 until MATRIX_HEIGHT) {
            for (x in 0 until MATRIX_WIDTH) {
                if (!isWithinCircularMask(x, y)) {
                    masked[y * MATRIX_WIDTH + x] = 0
                }
            }
        }
        return masked
    }

    /**
     * Asserts that all diodes outside the circular mask are strictly zero (masked).
     */
    fun assertCircularMaskEnforced(frame: ByteArray) {
        assertEquals("Frame must be 625 bytes", TOTAL_DIODES, frame.size)
        var maskedViolationCount = 0
        for (y in 0 until MATRIX_HEIGHT) {
            for (x in 0 until MATRIX_WIDTH) {
                if (!isWithinCircularMask(x, y)) {
                    val value = frame[y * MATRIX_WIDTH + x].toInt() and 0xFF
                    if (value != 0) {
                        maskedViolationCount++
                    }
                }
            }
        }
        assertTrue("Circular mask violation: $maskedViolationCount diodes outside radius were lit", maskedViolationCount == 0)
    }

    /**
     * Computes total brightness across all unmasked active diodes.
     */
    fun calculateLuminance(frame: ByteArray): Double {
        var sum = 0L
        var count = 0
        for (y in 0 until MATRIX_HEIGHT) {
            for (x in 0 until MATRIX_WIDTH) {
                if (isWithinCircularMask(x, y)) {
                    sum += (frame[y * MATRIX_WIDTH + x].toInt() and 0xFF)
                    count++
                }
            }
        }
        return if (count > 0) sum.toDouble() / count.toDouble() else 0.0
    }
}
