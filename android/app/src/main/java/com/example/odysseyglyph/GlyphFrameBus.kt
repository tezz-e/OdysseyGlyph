package com.example.odysseyglyph

import java.util.concurrent.CopyOnWriteArrayList

/**
 * GlyphFrameBus
 *
 * Thread-safe reactive event bus distributing 25×25 (625-byte) monochrome Glyph matrix frames
 * to UI views (GlyphMatrixMirrorView, Dynamic Pod, Virtual Glyph Simulator).
 *
 * Decouples the 16 FPS rear hardware binder loop from the 120 FPS front-panel UI rendering.
 */
object GlyphFrameBus {

    const val MATRIX_SIZE = 25
    const val FRAME_BYTES = MATRIX_SIZE * MATRIX_SIZE // 625

    fun interface FrameListener {
        fun onGlyphFrame(frame: ByteArray)
    }

    private val listeners = CopyOnWriteArrayList<FrameListener>()
    
    // Cached latest frame for newly attached views
    private val latestFrame = ByteArray(FRAME_BYTES)
    private val frameLock = Any()

    fun subscribe(listener: FrameListener) {
        listeners.add(listener)
        synchronized(frameLock) {
            listener.onGlyphFrame(latestFrame.copyOf())
        }
    }

    fun unsubscribe(listener: FrameListener) {
        listeners.remove(listener)
    }

    /**
     * Post a 625-byte matrix frame from VideoProcessor, LiveLyricsService, or VisualizerService.
     */
    fun postFrame(frame: ByteArray) {
        if (frame.size != FRAME_BYTES) return

        synchronized(frameLock) {
            System.arraycopy(frame, 0, latestFrame, 0, FRAME_BYTES)
        }

        // Broadcast to all active UI mirrors
        for (listener in listeners) {
            listener.onGlyphFrame(frame)
        }
    }

    /**
     * Clear active matrix (all diodes off).
     */
    fun clear() {
        val blank = ByteArray(FRAME_BYTES)
        postFrame(blank)
    }
}
