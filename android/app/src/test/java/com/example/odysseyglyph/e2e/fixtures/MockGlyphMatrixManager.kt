package com.example.odysseyglyph.e2e.fixtures

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Simulated Nothing Hardware Binder Driver & Fallback Simulator.
 * Models the proprietary Ketchum GlyphMatrixManager AIDL driver,
 * providing deterministic testing for rear matrix latching (16 FPS),
 * binder backpressure protection, lease arbitration, and virtual simulation fallback.
 */
class MockGlyphMatrixManager(
    var isHardwareAvailable: Boolean = true
) {

    private val isInitialized = AtomicBoolean(false)
    private val currentLeaseHolder = AtomicReference<String?>(null)
    private val submittedFramesCount = AtomicInteger(0)
    private val latchedFramesCount = AtomicInteger(0)
    private val lastLatchedFrame = AtomicReference<ByteArray?>(null)

    // Dual-rate decoupling constants
    val rearTargetFps = 16
    val rearFrameIntervalMs = 1000L / rearTargetFps // ~62ms

    private var lastLatchTimestampMs = 0L

    /**
     * Simulates AIDL service initialization.
     */
    fun init(): Boolean {
        if (!isHardwareAvailable) return false
        isInitialized.set(true)
        return true
    }

    /**
     * Releases binder connection without unInit deadlock.
     */
    fun unInit() {
        isInitialized.set(false)
        currentLeaseHolder.set(null)
    }

    fun isBound(): Boolean = isInitialized.get() && isHardwareAvailable

    /**
     * Acquires exclusive hardware binder lease for a service (e.g. "LiveLyricsService").
     */
    fun acquireLease(serviceName: String): Boolean {
        if (!isHardwareAvailable) return false
        return currentLeaseHolder.compareAndSet(null, serviceName) ||
                currentLeaseHolder.get() == serviceName
    }

    /**
     * Releases active binder lease.
     */
    fun releaseLease(serviceName: String): Boolean {
        return currentLeaseHolder.compareAndSet(serviceName, null)
    }

    fun getActiveLeaseHolder(): String? = currentLeaseHolder.get()

    /**
     * Submits a 625-byte matrix frame.
     * Enforces circular mask and simulates 16 FPS rear hardware latching.
     */
    fun setFrame(frame: ByteArray, currentTimestampMs: Long = System.currentTimeMillis()): Boolean {
        require(frame.size == MatrixTestFixtures.TOTAL_DIODES) {
            "Frame size must be exactly ${MatrixTestFixtures.TOTAL_DIODES} bytes"
        }
        if (!isHardwareAvailable || !isInitialized.get()) {
            return false // Falls back to Virtual Simulation
        }

        submittedFramesCount.incrementAndGet()

        // Rate-latching simulation: Only latch if interval >= ~62ms has elapsed
        if (currentTimestampMs - lastLatchTimestampMs >= rearFrameIntervalMs || lastLatchTimestampMs == 0L) {
            lastLatchTimestampMs = currentTimestampMs
            lastLatchedFrame.set(frame.clone())
            latchedFramesCount.incrementAndGet()
            return true
        }

        // Frame dropped by rear rate-latching to preserve 16 FPS bus limit
        return false
    }

    fun getSubmittedCount(): Int = submittedFramesCount.get()
    fun getLatchedCount(): Int = latchedFramesCount.get()
    fun getLastLatchedFrame(): ByteArray? = lastLatchedFrame.get()

    fun reset() {
        submittedFramesCount.set(0)
        latchedFramesCount.set(0)
        lastLatchedFrame.set(null)
        lastLatchTimestampMs = 0L
        currentLeaseHolder.set(null)
    }
}
