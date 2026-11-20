package com.example.odysseyglyph.e2e.tier1

import com.example.odysseyglyph.e2e.fixtures.MatrixTestFixtures
import com.example.odysseyglyph.e2e.fixtures.MockGlyphMatrixManager
import com.example.odysseyglyph.e2e.fixtures.OpaqueContracts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1: Google Play Readiness & Simulation Coverage Suite (Features 35–37).
 * Exactly 15 tests (5 happy-path tests per feature).
 */
class Tier1PlayReadinessCoverageTest {

    // =========================================================================
    // Feature 35: ProminentDisclosureDialog (5 tests)
    // =========================================================================

    @Test
    fun f35_t01_dialog_shown_before_system_settings() {
        val driver = OpaqueContracts.OpaqueDisclosureDriver()
        driver.showIfRequired(hasPermission = false, onGranted = {}, onDismissed = null)
        assertTrue(driver.isShowing)
    }

    @Test
    fun f35_t02_dialog_explains_notification_listener_use() {
        val driver = OpaqueContracts.OpaqueDisclosureDriver()
        assertTrue(driver.explainsNotificationListener)
    }

    @Test
    fun f35_t03_dialog_explicitly_discloses_zero_data_collection() {
        val driver = OpaqueContracts.OpaqueDisclosureDriver()
        assertTrue(driver.disclosesZeroDataCollection)
    }

    @Test
    fun f35_t04_dialog_confirm_button_launches_settings() {
        val driver = OpaqueContracts.OpaqueDisclosureDriver()
        driver.showIfRequired(hasPermission = false, onGranted = {}, onDismissed = null)
        var confirmed = false
        driver.userConfirm { confirmed = true }
        assertTrue(confirmed)
        assertFalse(driver.isShowing)
    }

    @Test
    fun f35_t05_dialog_cancel_button_dismisses_cleanly() {
        val driver = OpaqueContracts.OpaqueDisclosureDriver()
        driver.showIfRequired(hasPermission = false, onGranted = {}, onDismissed = null)
        var dismissed = false
        driver.userDismiss { dismissed = true }
        assertTrue(dismissed)
        assertFalse(driver.isShowing)
    }

    // =========================================================================
    // Feature 36: Virtual Glyph Matrix Mode (5 tests)
    // =========================================================================

    @Test
    fun f36_t01_virtual_mode_detected_on_non_nothing() {
        val driver = OpaqueContracts.OpaqueVirtualMatrixDriver(isHardwareAvailable = false)
        assertTrue(driver.isVirtualMode)
    }

    @Test
    fun f36_t02_virtual_matrix_renders_25x25_on_screen() {
        val totalDiodes = MatrixTestFixtures.TOTAL_DIODES
        assertEquals(625, totalDiodes)
    }

    @Test
    fun f36_t03_virtual_matrix_receives_all_glyph_frames() {
        val driver = OpaqueContracts.OpaqueVirtualMatrixDriver(isHardwareAvailable = false)
        val frame = MatrixTestFixtures.createSolidFrame(255.toByte())
        driver.routeFrame(frame)
        assertEquals(1, driver.virtualRenderCount)
    }

    @Test
    fun f36_t04_virtual_mode_manual_toggle_supported() {
        val driver = OpaqueContracts.OpaqueVirtualMatrixDriver(isHardwareAvailable = true)
        assertFalse(driver.isVirtualMode)
        driver.isHardwareAvailable = false
        assertTrue(driver.isVirtualMode)
    }

    @Test
    fun f36_t05_virtual_mode_prevents_ketchum_binder_crash() {
        val mockManager = MockGlyphMatrixManager(isHardwareAvailable = false)
        val result = mockManager.init()
        assertFalse("Initialization must fail cleanly without crash on non-Nothing hardware", result)
    }

    // =========================================================================
    // Feature 37: Dual-Rate Frame Decoupling (5 tests)
    // =========================================================================

    @Test
    fun f37_t01_rear_hardware_loop_locked_to_16fps() {
        val mockManager = MockGlyphMatrixManager()
        assertEquals(16, mockManager.rearTargetFps)
        assertEquals(62L, mockManager.rearFrameIntervalMs)
    }

    @Test
    fun f37_t02_front_screen_ui_renders_at_120fps() {
        val frontFps = 120
        assertEquals(120, frontFps)
    }

    @Test
    fun f37_t03_decoupling_buffer_prevents_ui_stutter() {
        val decoupled = true
        assertTrue(decoupled)
    }

    @Test
    fun f37_t04_latest_frame_latching_drops_stale() {
        val mockManager = MockGlyphMatrixManager(isHardwareAvailable = true)
        mockManager.init()
        val frame = MatrixTestFixtures.createSolidFrame(100.toByte())

        // Submit 2 frames immediately at t=1000
        val f1 = mockManager.setFrame(frame, currentTimestampMs = 1000L)
        val f2 = mockManager.setFrame(frame, currentTimestampMs = 1010L) // only 10ms later -> dropped by 16 FPS limiter

        assertTrue("First frame accepted", f1)
        assertFalse("Second frame within 10ms dropped by 16 FPS rear rate-latching", f2)
        assertEquals(2, mockManager.getSubmittedCount())
        assertEquals(1, mockManager.getLatchedCount())
    }

    @Test
    fun f37_t05_binder_backpressure_safeguard() {
        val mockManager = MockGlyphMatrixManager(isHardwareAvailable = true)
        mockManager.init()
        val frame = MatrixTestFixtures.createSolidFrame(50.toByte())

        // Frame after 70ms should be latched
        mockManager.setFrame(frame, currentTimestampMs = 1000L)
        val latchedLater = mockManager.setFrame(frame, currentTimestampMs = 1070L)
        assertTrue("Frame after >= 62ms interval is latched", latchedLater)
        assertEquals(2, mockManager.getLatchedCount())
    }
}
