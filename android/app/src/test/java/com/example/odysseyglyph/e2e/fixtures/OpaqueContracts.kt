package com.example.odysseyglyph.e2e.fixtures

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Authoritative Public Interface Contracts and Opaque-Box Test Drivers.
 * Enables hardware-decoupled JVM E2E testing across all UI/UX overhaul features.
 */
object OpaqueContracts {

    // =========================================================================
    // 1. GlyphFrameBus Contract
    // =========================================================================

    interface GlyphFrameBusContract {
        fun postFrame(frame: ByteArray)
        fun subscribe(listener: (ByteArray) -> Unit)
        fun unsubscribe(listener: (ByteArray) -> Unit)
        val currentFrame: ByteArray?
    }

    class OpaqueGlyphFrameBus : GlyphFrameBusContract {
        private val listeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()
        private val cachedFrame = AtomicReference<ByteArray?>(null)
        val dispatchCount = AtomicInteger(0)

        override fun postFrame(frame: ByteArray) {
            require(frame.size == MatrixTestFixtures.TOTAL_DIODES) {
                "Frame must be exactly ${MatrixTestFixtures.TOTAL_DIODES} bytes, was ${frame.size}"
            }
            cachedFrame.set(frame.clone())
            dispatchCount.incrementAndGet()
            for (listener in listeners) {
                listener(frame)
            }
        }

        override fun subscribe(listener: (ByteArray) -> Unit) {
            listeners.add(listener)
        }

        override fun unsubscribe(listener: (ByteArray) -> Unit) {
            listeners.remove(listener)
        }

        override val currentFrame: ByteArray?
            get() = cachedFrame.get()

        fun subscriberCount(): Int = listeners.size

        fun reset() {
            listeners.clear()
            cachedFrame.set(null)
            dispatchCount.set(0)
        }
    }

    // =========================================================================
    // 2. TouchSpringAnimator Contract
    // =========================================================================

    interface TouchSpringAnimatorContract {
        fun onTouchDown(viewId: String): Float
        fun onTouchUp(viewId: String): Float
        fun onTouchCancel(viewId: String): Float
        fun getScale(viewId: String): Float
    }

    class OpaqueTouchSpringDriver(
        val pressedScale: Float = PhysicsTestAssertions.PRESSED_SCALE,
        val restingScale: Float = PhysicsTestAssertions.RESTING_SCALE,
        val stiffness: Float = PhysicsTestAssertions.SPRING_STIFFNESS_K,
        val dampingRatio: Float = PhysicsTestAssertions.SPRING_DAMPING_ZETA
    ) : TouchSpringAnimatorContract {

        private val viewScales = mutableMapOf<String, Float>()
        private val touchEvents = mutableListOf<String>()

        override fun onTouchDown(viewId: String): Float {
            touchEvents.add("$viewId:DOWN")
            viewScales[viewId] = pressedScale
            return pressedScale
        }

        override fun onTouchUp(viewId: String): Float {
            touchEvents.add("$viewId:UP")
            viewScales[viewId] = restingScale
            return restingScale
        }

        override fun onTouchCancel(viewId: String): Float {
            touchEvents.add("$viewId:CANCEL")
            viewScales[viewId] = restingScale
            return restingScale
        }

        override fun getScale(viewId: String): Float = viewScales[viewId] ?: restingScale

        fun getEventLog(): List<String> = touchEvents.toList()

        fun reset() {
            viewScales.clear()
            touchEvents.clear()
        }
    }

    // =========================================================================
    // 3. HapticExt Contract
    // =========================================================================

    interface HapticExtContract {
        fun vibrateTick(): Boolean
        fun vibrateClick(): Boolean
        fun vibrateHeavyClick(): Boolean
        fun vibrateDetent(): Boolean
    }

    class OpaqueHapticDriver(
        var isHapticEnabled: Boolean = true,
        var isSilentMode: Boolean = false
    ) : HapticExtContract {

        val tickCount = AtomicInteger(0)
        val clickCount = AtomicInteger(0)
        val heavyClickCount = AtomicInteger(0)
        val detentCount = AtomicInteger(0)
        private var lastDetentTimestamp = 0L

        override fun vibrateTick(): Boolean {
            if (!isHapticEnabled || isSilentMode) return false
            tickCount.incrementAndGet()
            return true
        }

        override fun vibrateClick(): Boolean {
            if (!isHapticEnabled || isSilentMode) return false
            clickCount.incrementAndGet()
            return true
        }

        override fun vibrateHeavyClick(): Boolean {
            if (!isHapticEnabled || isSilentMode) return false
            heavyClickCount.incrementAndGet()
            return true
        }

        override fun vibrateDetent(): Boolean {
            if (!isHapticEnabled || isSilentMode) return false
            val now = System.currentTimeMillis()
            // 50Hz throttling (20ms interval)
            if (now - lastDetentTimestamp >= 20L || lastDetentTimestamp == 0L) {
                lastDetentTimestamp = now
                detentCount.incrementAndGet()
                return true
            }
            return false
        }

        fun totalHapticCount(): Int =
            tickCount.get() + clickCount.get() + heavyClickCount.get() + detentCount.get()

        fun reset() {
            tickCount.set(0)
            clickCount.set(0)
            heavyClickCount.set(0)
            detentCount.set(0)
            lastDetentTimestamp = 0L
        }
    }

    // =========================================================================
    // 4. SegmentedPillSlider Contract
    // =========================================================================

    interface SegmentedPillSliderContract {
        var progress: Float // 0.0f .. 1.0f
        val numSegments: Int
        fun snapToNearestSegment(): Int
        fun scrubTo(normalizedValue: Float): Boolean
    }

    class OpaqueSegmentedSliderDriver(
        override val numSegments: Int = 4,
        private val hapticDriver: HapticExtContract = OpaqueHapticDriver()
    ) : SegmentedPillSliderContract {

        override var progress: Float = 0.0f
        var currentSegmentIndex: Int = 0
            private set

        val detentHistory = mutableListOf<Int>()

        override fun scrubTo(normalizedValue: Float): Boolean {
            val clamped = normalizedValue.coerceIn(0.0f, 1.0f)
            progress = clamped
            val newSegment = snapToNearestSegment()
            if (newSegment != currentSegmentIndex) {
                currentSegmentIndex = newSegment
                detentHistory.add(newSegment)
                hapticDriver.vibrateDetent()
                return true
            }
            return false
        }

        override fun snapToNearestSegment(): Int {
            val stepSize = 1.0f / numSegments.toFloat()
            val segment = (progress / stepSize + 0.5f).toInt().coerceIn(0, numSegments)
            return segment
        }

        fun getSegmentFraction(segment: Int): Float {
            return (segment.coerceIn(0, numSegments).toFloat() / numSegments.toFloat())
        }
    }

    // =========================================================================
    // 5. GlyphDynamicPod Contract
    // =========================================================================

    enum class DynamicPodState {
        RESTING_PILL,
        EXPANDING,
        EXPANDED_CONSOLE,
        COLLAPSING
    }

    interface GlyphDynamicPodContract {
        val currentState: DynamicPodState
        val widthDp: Float
        val heightDp: Float
        fun tapPill()
        fun swipeUp()
        fun tapOutside()
        fun triggerTrackChange(newTitle: String): String
    }

    class OpaqueDynamicPodDriver : GlyphDynamicPodContract {

        override var currentState: DynamicPodState = DynamicPodState.RESTING_PILL
            private set

        override var widthDp: Float = 130.0f
            private set

        override var heightDp: Float = 36.0f
            private set

        var activeTitle: String = "IDLE"
            private set

        val cipherCharset = "0123456789ABCDEF!@#%&*+=~"

        override fun tapPill() {
            if (currentState == DynamicPodState.RESTING_PILL) {
                currentState = DynamicPodState.EXPANDING
                widthDp = 320.0f
                heightDp = 180.0f
                currentState = DynamicPodState.EXPANDED_CONSOLE
            }
        }

        override fun swipeUp() {
            if (currentState == DynamicPodState.EXPANDED_CONSOLE) {
                currentState = DynamicPodState.COLLAPSING
                widthDp = 130.0f
                heightDp = 36.0f
                currentState = DynamicPodState.RESTING_PILL
            }
        }

        override fun tapOutside() {
            swipeUp()
        }

        /**
         * Simulates 300ms cipher text scramble.
         */
        override fun triggerTrackChange(newTitle: String): String {
            activeTitle = newTitle
            if (newTitle.isEmpty()) return ""
            val scrambled = StringBuilder()
            val rnd = Random(42)
            for (ch in newTitle) {
                if (ch.isWhitespace()) {
                    scrambled.append(' ')
                } else {
                    scrambled.append(cipherCharset[rnd.nextInt(cipherCharset.length)])
                }
            }
            return scrambled.toString()
        }

        fun isResting(): Boolean = currentState == DynamicPodState.RESTING_PILL
        fun isExpanded(): Boolean = currentState == DynamicPodState.EXPANDED_CONSOLE
    }

    // =========================================================================
    // 6. ProminentDisclosureDialog Contract
    // =========================================================================

    interface ProminentDisclosureDialogContract {
        val isShowing: Boolean
        val explainsNotificationListener: Boolean
        val disclosesZeroDataCollection: Boolean
        fun showIfRequired(hasPermission: Boolean, onGranted: () -> Unit, onDismissed: (() -> Unit)?)
    }

    class OpaqueDisclosureDriver : ProminentDisclosureDialogContract {
        override var isShowing: Boolean = false
            private set
        override val explainsNotificationListener: Boolean = true
        override val disclosesZeroDataCollection: Boolean = true

        var confirmInvoked: Boolean = false
        var dismissInvoked: Boolean = false

        override fun showIfRequired(
            hasPermission: Boolean,
            onGranted: () -> Unit,
            onDismissed: (() -> Unit)?
        ) {
            if (hasPermission) {
                isShowing = false
                confirmInvoked = true
                onGranted()
            } else {
                isShowing = true
            }
        }

        fun userConfirm(onGranted: () -> Unit) {
            isShowing = false
            confirmInvoked = true
            onGranted()
        }

        fun userDismiss(onDismissed: (() -> Unit)?) {
            isShowing = false
            dismissInvoked = true
            onDismissed?.invoke()
        }
    }

    // =========================================================================
    // 7. VirtualGlyphMatrixManager Contract
    // =========================================================================

    interface VirtualGlyphMatrixManagerContract {
        var isHardwareAvailable: Boolean
        val isVirtualMode: Boolean
        fun routeFrame(frame: ByteArray): Boolean
    }

    class OpaqueVirtualMatrixDriver(
        override var isHardwareAvailable: Boolean = false
    ) : VirtualGlyphMatrixManagerContract {

        override val isVirtualMode: Boolean
            get() = !isHardwareAvailable

        val virtualFrameHistory = mutableListOf<ByteArray>()
        var virtualRenderCount: Int = 0

        override fun routeFrame(frame: ByteArray): Boolean {
            require(frame.size == MatrixTestFixtures.TOTAL_DIODES)
            if (isVirtualMode) {
                virtualFrameHistory.add(frame.clone())
                virtualRenderCount++
                return true
            }
            return false
        }

        fun reset() {
            virtualFrameHistory.clear()
            virtualRenderCount = 0
        }
    }
}
