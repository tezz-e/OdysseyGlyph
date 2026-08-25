package com.example.odysseyglyph

import android.content.Context

object MatrixConfig {
    const val MATRIX_SIZE_PHONE_3 = 25
    const val MATRIX_SIZE_PHONE_4A_PRO = 13

    // Get the logical matrix size to render at
    fun getMatrixSize(context: Context): Int {
        val prefs = context.getSharedPreferences("OdysseyPrefs", Context.MODE_PRIVATE)
        val simulate4aPro = prefs.getBoolean("simulate_4a_pro", false)
        val hardwareSize = getHardwareMatrixSize()
        // Render at 13x13 if we are on a 4a Pro natively, OR if we are simulating it on a Phone 3
        return if (simulate4aPro || hardwareSize == MATRIX_SIZE_PHONE_4A_PRO) {
            MATRIX_SIZE_PHONE_4A_PRO
        } else {
            MATRIX_SIZE_PHONE_3
        }
    }
    
    // Real hardware matrix size, detecting the device model dynamically
    fun getHardwareMatrixSize(): Int {
        // "A069P" is the Nothing Phone (4a) Pro which has a 13x13 matrix
        if (android.os.Build.MODEL.contains("A069P")) {
            return MATRIX_SIZE_PHONE_4A_PRO
        }
        return MATRIX_SIZE_PHONE_3 // Assume Phone 3 (25x25) for other devices
    }

    // Safely format a byte array for the physical hardware, upscaling if needed
    fun formatForHardware(frame: ByteArray): IntArray {
        val hardwareSize = getHardwareMatrixSize()
        val expectedLength = hardwareSize * hardwareSize
        
        if (frame.size == expectedLength) {
            val result = IntArray(expectedLength)
            for (i in frame.indices) result[i] = (frame[i].toInt() and 0xFF) * 16 // 0-255 -> 0-4095
            return result
        }
        
        // If we are simulating 13x13 (169) on 25x25 hardware (625)
        if (frame.size == 169 && expectedLength == 625) {
            val result = IntArray(625)
            val scale = 25f / 13f
            for (y in 0 until 25) {
                for (x in 0 until 25) {
                    val srcX = (x / scale).toInt().coerceIn(0, 12)
                    val srcY = (y / scale).toInt().coerceIn(0, 12)
                    val value = frame[srcY * 13 + srcX].toInt() and 0xFF
                    result[y * 25 + x] = value * 16
                }
            }
            return result
        }
        
        // Fallback: return empty array to prevent crashes
        return IntArray(expectedLength)
    }
    
    // Total cells in the matrix (e.g. 625 for 25x25, 169 for 13x13)
    fun getCellCount(context: Context): Int {
        val size = getMatrixSize(context)
        return size * size
    }
}
