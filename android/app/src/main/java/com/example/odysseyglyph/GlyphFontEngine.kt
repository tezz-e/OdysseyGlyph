package com.example.odysseyglyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object GlyphFontEngine {
    
    enum class FontStyle {
        PIXEL_TINY,   // ~3x5 logical size using a tiny font size
        BLOCK_BOLD,   // ~5x5 bold
        SMOOTH        // Anti-aliased smooth font
    }
    
    private fun getConfiguredPaint(text: String, style: FontStyle, autoScale: Boolean): Paint {
        val paint = Paint()
        paint.color = Color.WHITE
        when (style) {
            FontStyle.PIXEL_TINY -> {
                paint.typeface = Typeface.MONOSPACE
                paint.textSize = 9f
                paint.isAntiAlias = false
            }
            FontStyle.BLOCK_BOLD -> {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = 12f
                paint.isAntiAlias = false
            }
            FontStyle.SMOOTH -> {
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.textSize = 12f
                paint.isAntiAlias = true
            }
        }
        
        if (autoScale) {
            val lines = text.split("\n")
            val isMultiline = lines.size > 1
            
            if (isMultiline && paint.textSize > 7.5f) {
                paint.textSize = 7.5f
            }
            
            val maxWidth = if (isMultiline) 17f else 22f
            
            while (lines.any { paint.measureText(it) > maxWidth } && paint.textSize > 5f) {
                paint.textSize -= 0.5f
            }
        }
        return paint
    }

    /**
     * Renders a single frame of text onto the 25x25 matrix.
     * @param text The string to render.
     * @param style The typography style.
     * @param scrollOffsetX Horizontal pixel offset (for scrolling text).
     * @param autoScale Whether to shrink text to fit the 25px width.
     * @return 625-byte array of brightness values (0-255).
     */
    fun renderTextFrame(text: String, style: FontStyle, scrollOffsetX: Float, autoScale: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        
        val paint = getConfiguredPaint(text, style, autoScale)
        
        val lines = text.split("\n")
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.bottom - fontMetrics.top
        
        val bounds = android.graphics.Rect()
        
        if (lines.size == 1) {
            val line = lines[0]
            paint.getTextBounds(line, 0, line.length, bounds)
            val leftBearingOffset = if (bounds.left < 0) -bounds.left.toFloat() else 0f
            
            val textOffset = textHeight / 2 - fontMetrics.bottom
            val y = 25f / 2f + textOffset
            canvas.drawText(line, scrollOffsetX + leftBearingOffset, y, paint)
        } else {
            // Compress line spacing so words don't hit the narrow top/bottom edges of the circular matrix
            val lineHeight = textHeight * 0.8f
            val totalHeight = lineHeight * lines.size
            var startY = (25f - totalHeight) / 2f - fontMetrics.top
            for (line in lines) {
                paint.getTextBounds(line, 0, line.length, bounds)
                val leftBearingOffset = if (bounds.left < 0) -bounds.left.toFloat() else 0f
                
                // Ignore scrollOffsetX for multiline since it's only used in Flash mode, which calculates its own line X
                val lineWidth = paint.measureText(line)
                val lineX = (25f - lineWidth) / 2f
                canvas.drawText(line, lineX + leftBearingOffset, startY, paint)
                startY += lineHeight
            }
        }
        
        // Extract to ByteArray
        val pixels = IntArray(625)
        bitmap.getPixels(pixels, 0, 25, 0, 0, 25, 25)
        
        val output = ByteArray(625)
        val center = 12f
        val radiusSq = 12f * 12f
        
        for (i in 0 until 625) {
            val px = i % 25
            val py = i / 25
            
            // Circular mask
            val distSq = (px - center) * (px - center) + (py - center) * (py - center)
            if (distSq <= radiusSq) {
                val color = pixels[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                output[i] = gray.toByte()
            } else {
                output[i] = 0
            }
        }
        
        bitmap.recycle()
        return output
    }
    
    /**
     * Helper to measure text width for scrolling calculations.
     */
    fun measureTextWidth(text: String, style: FontStyle, autoScale: Boolean = false): Float {
        val paint = getConfiguredPaint(text, style, autoScale)
        val lines = text.split("\n")
        return lines.maxOfOrNull { paint.measureText(it) } ?: 0f
    }
    
    /**
     * Adapts a single word into 1, 2, or multiple chunks based on raw pixel width.
     * Ensures chunks do not exceed the 25px matrix width (using 24f for safety).
     */
    fun formatWordForDisplay(word: String, style: FontStyle, progress: Float = 0f): String {
        // Measure the unscaled text width.
        val singleLineWidth = measureTextWidth(word, style, autoScale = false)
        if (singleLineWidth <= 24f) {
            return word
        }

        // Try splitting into 2 lines. Find the index that minimizes the difference between width1 and width2.
        var bestSplitIdx = -1
        var minDiff = Float.MAX_VALUE
        var bestW1 = 0f
        var bestW2 = 0f
        
        for (i in 1 until word.length) {
            val p1 = word.substring(0, i) + "-"
            val p2 = word.substring(i)
            val w1 = measureTextWidth(p1, style, autoScale = false)
            val w2 = measureTextWidth(p2, style, autoScale = false)
            val diff = kotlin.math.abs(w1 - w2)
            if (diff < minDiff) {
                minDiff = diff
                bestSplitIdx = i
                bestW1 = w1
                bestW2 = w2
            }
        }
        
        // If both parts fit within 24f when stacked
        if (bestSplitIdx != -1 && bestW1 <= 24f && bestW2 <= 24f) {
            return word.substring(0, bestSplitIdx) + "-\n" + word.substring(bestSplitIdx)
        }
        
        // Time-chunking: break word into sequential chunks that fit within ~24f
        val chunks = mutableListOf<String>()
        var currentChunk = ""
        for (i in word.indices) {
            val char = word[i]
            val suffix = if (i == word.lastIndex) "" else "-"
            if (measureTextWidth(currentChunk + char + suffix, style, autoScale = false) <= 24f) {
                currentChunk += char
            } else {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk + "-")
                }
                currentChunk = char.toString()
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }
        
        if (chunks.isEmpty()) return word
        
        val chunkIndex = (progress * chunks.size).toInt().coerceIn(0, chunks.size - 1)
        return chunks[chunkIndex]
    }

    /**
     * Renders a fallback visualizer when no lyrics are present.
     * @param audioLevels The current audio levels from the FFT (0.0 to 1.0, length 5). 
     * @param style 0: Static Note, 1: Math EQ, 2: Mic EQ, 3: Mic Ring
     * @param timeMs Used for Math EQ animation.
     */
    fun renderFallbackFrame(audioLevels: FloatArray, style: Int, timeMs: Long): ByteArray {
        val bitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = false // MUST be false for pixel-perfect clarity on 25x25
        }
        
        val center = 12.5f
        
        when (style) {
            0 -> {
                // Static Note (Pixel Art)
                paint.style = Paint.Style.FILL
                
                // Draw a crisp double eighth note (♫) manually to avoid font rasterization issues.
                // Dimensions: ~11x11 pixels. Center it at 12.5
                val offsetX = 12.5f - 5.5f
                val offsetY = 12.5f - 5.5f

                // Top Beam (Width 10, Height 2)
                canvas.drawRect(offsetX + 1f, offsetY, offsetX + 11f, offsetY + 2f, paint)
                
                // Left Stem (Width 2, Height 6)
                canvas.drawRect(offsetX + 1f, offsetY + 2f, offsetX + 3f, offsetY + 8f, paint)
                
                // Right Stem (Width 2, Height 6)
                canvas.drawRect(offsetX + 9f, offsetY + 2f, offsetX + 11f, offsetY + 8f, paint)
                
                // Left Note Head (Width 4, Height 3, slightly offset to the left)
                canvas.drawRect(offsetX - 1f, offsetY + 7f, offsetX + 3f, offsetY + 10f, paint)
                
                // Right Note Head (Width 4, Height 3, slightly offset to the left)
                canvas.drawRect(offsetX + 7f, offsetY + 7f, offsetX + 11f, offsetY + 10f, paint)
            }
            1 -> {
                // Math EQ (Fake - Mirrored Waveform)
                paint.style = Paint.Style.FILL
                val barCount = 13
                val barWidth = 1f
                val spacing = 1f
                val startX = 0f
                val centerY = 12f
                
                for (i in 0 until barCount) {
                    val phase = i * 0.8f
                    val wave1 = kotlin.math.sin(timeMs / 150.0 + phase)
                    val wave2 = kotlin.math.sin(timeMs / 250.0 - phase)
                    val combined = (wave1 + wave2) / 2.0
                    
                    val heightRatio = ((combined + 1.0) / 2.0).toFloat()
                    val blocks = (1 + heightRatio * 11).toInt() // Max 12 pixels up/down
                    
                    val x = startX + i * (barWidth + spacing)
                    // Draw mirrored from center (pixel-perfect)
                    canvas.drawRect(x, centerY - blocks, x + barWidth, centerY + blocks + 1f, paint)
                }
            }
            2 -> {
                // Mic EQ (Real - Mirrored Waveform)
                paint.style = Paint.Style.FILL
                val barCount = 13
                val barWidth = 1f
                val spacing = 1f
                val startX = 0f
                val centerY = 12f
                
                for (i in 0 until barCount) {
                    val level = if (i < audioLevels.size) audioLevels[i] else 0f
                    // Smooth visual blocks based on level (max 12 pixels up/down)
                    val blocks = (level * 12).toInt().coerceIn(1, 12)
                    
                    val x = startX + i * (barWidth + spacing)
                    // Draw mirrored from center (pixel-perfect)
                    canvas.drawRect(x, centerY - blocks, x + barWidth, centerY + blocks + 1f, paint)
                }
            }
            3 -> {
                // Mic Ring (Speaker Cone Style)
                // Extract distinct frequency bands
                val bass = audioLevels[0] * 0.6f + audioLevels[1] * 0.4f
                val mids = audioLevels[4] * 0.5f + audioLevels[5] * 0.5f
                val treble = audioLevels[9] * 0.5f + audioLevels[10] * 0.5f

                // Inner Bass Core (Solid)
                paint.style = Paint.Style.FILL
                val coreRadius = (1f + bass * 4f).toInt().toFloat()
                if (coreRadius > 0f) {
                    canvas.drawCircle(center, center, coreRadius, paint)
                }

                // Outer Mid Ring (Stroke)
                paint.style = Paint.Style.STROKE
                val outerRadius = (7f + mids * 3f).toInt().toFloat()
                paint.strokeWidth = (1f + treble * 2f).toInt().toFloat()
                if (outerRadius > coreRadius + 1f) {
                    canvas.drawCircle(center, center, outerRadius, paint)
                }
                
                // Treble Sparks (Outer Corners)
                paint.style = Paint.Style.FILL
                if (treble > 0.3f) {
                    val dotDist = 11f
                    val dotSize = (treble * 2f).toInt().toFloat().coerceAtLeast(1f)
                    
                    // Draw 4 corner/edge sparks
                    canvas.drawRect(center - dotSize, center - dotDist, center + dotSize, center - dotDist + dotSize, paint) // Top
                    canvas.drawRect(center - dotSize, center + dotDist - dotSize, center + dotSize, center + dotDist, paint) // Bottom
                    canvas.drawRect(center - dotDist, center - dotSize, center - dotDist + dotSize, center + dotSize, paint) // Left
                    canvas.drawRect(center + dotDist - dotSize, center - dotSize, center + dotDist, center + dotSize, paint) // Right
                }
            }
            4 -> {
                // Spinning Vinyl (Static audio, relies on timeMs)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                
                // Record grooves
                canvas.drawCircle(center, center, 11f, paint)
                canvas.drawCircle(center, center, 7f, paint)
                
                // Label rim
                paint.strokeWidth = 2f
                canvas.drawCircle(center, center, 3f, paint)
                
                // Center hole
                paint.style = Paint.Style.FILL
                canvas.drawCircle(center, center, 1f, paint)
                
                // Spinning marker
                paint.strokeWidth = 1.5f
                val angle = (timeMs % 2000L) / 2000.0 * 2.0 * Math.PI
                val startX = center + (4f * Math.cos(angle)).toFloat()
                val startY = center + (4f * Math.sin(angle)).toFloat()
                val stopX = center + (6f * Math.cos(angle)).toFloat()
                val stopY = center + (6f * Math.sin(angle)).toFloat()
                canvas.drawLine(startX, startY, stopX, stopY, paint)
            }
            5 -> {
                // Pulse (Mic)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                
                val bass = audioLevels[0]
                val mids = audioLevels[4]
                val treble = audioLevels[9]
                
                val r1 = ((timeMs / 40.0) % 15.0).toFloat()
                val r2 = ((timeMs / 40.0 + 5.0) % 15.0).toFloat()
                val r3 = ((timeMs / 40.0 + 10.0) % 15.0).toFloat()
                
                paint.alpha = (bass * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(center, center, r1, paint)
                
                paint.alpha = (mids * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(center, center, r2, paint)
                
                paint.alpha = (treble * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(center, center, r3, paint)
                
                paint.alpha = 255 // Reset
            }
            6 -> {
                // Matrix Rain (Math)
                paint.style = Paint.Style.FILL
                for (x in 0 until 25) {
                    // Pseudo-random deterministic parameters for each column
                    val seed = x * 1337
                    val speed = 30.0 + (seed % 20)
                    val offset = seed % 2000
                    
                    val y = ((timeMs + offset) / speed) % 35 - 5
                    
                    val px = x.toFloat()
                    val py = y.toFloat()
                    
                    paint.alpha = 255
                    canvas.drawRect(px, py, px + 1f, py + 1f, paint)
                    
                    // Draw fading tail
                    for (t in 1..4) {
                        paint.alpha = 255 - (t * 50)
                        canvas.drawRect(px, py - t, px + 1f, py - t + 1f, paint)
                    }
                }
                paint.alpha = 255 // Reset
            }
            7 -> {
                // Radar (Math)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                
                // Outer ring
                paint.alpha = 100
                canvas.drawCircle(center, center, 12f, paint)
                
                // Sweeping hand
                val angle = (timeMs % 3000L) / 3000.0 * 2.0 * Math.PI
                
                // Draw fading trail
                for (i in 0..20) {
                    val trailAngle = angle - (i * 0.05)
                    val stopX = center + (12f * Math.cos(trailAngle)).toFloat()
                    val stopY = center + (12f * Math.sin(trailAngle)).toFloat()
                    paint.alpha = 255 - (i * 12)
                    canvas.drawLine(center, center, stopX, stopY, paint)
                }
                paint.alpha = 255 // Reset
            }
            8 -> {
                // Beat Wave (Mic)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                
                val bass = audioLevels[0] * 0.6f + audioLevels[1] * 0.4f
                val amplitude = 1f + (bass * 11f)
                val phase = timeMs / 150.0
                
                var prevX = 0f
                var prevY = center + (amplitude * Math.sin(phase)).toFloat()
                
                for (x in 1..25) {
                    val px = x.toFloat()
                    val py = center + (amplitude * Math.sin(px / 3.0 + phase)).toFloat()
                    canvas.drawLine(prevX, prevY, px, py, paint)
                    prevX = px
                    prevY = py
                }
            }
        }
        
        val pixels = IntArray(625)
        bitmap.getPixels(pixels, 0, 25, 0, 0, 25, 25)
        val output = ByteArray(625)
        val radiusSq = 12.5f * 12.5f
        
        for (i in 0 until 625) {
            val px = i % 25
            val py = i / 25
            
            // Circular mask
            val distSq = (px - center) * (px - center) + (py - center) * (py - center)
            if (distSq <= radiusSq) {
                val color = pixels[i]
                val a = Color.alpha(color)
                output[i] = a.toByte()
            } else {
                output[i] = 0
            }
        }
        
        bitmap.recycle()
        return output
    }

    /**
     * Generates a static high-res preview bitmap for UI display.
     * Renders authentic circular diode LEDs with transparent background
     * and circular chassis to eliminate square clipping boxes in the UI.
     */
    fun generatePreviewBitmap(patternStyle: Int): Bitmap {
        // Dummy audio data to simulate activity
        val dummyAudio = floatArrayOf(0.7f, 0.6f, 0.4f, 0.3f, 0.8f, 0.7f, 0.5f, 0.4f, 0.3f, 0.9f, 0.6f, 0.2f, 0.8f)
        val rawBytes = renderFallbackFrame(dummyAudio, patternStyle, 500L) // Fixed time 500ms

        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = size / 2f
        val matrixRadius = size * 0.44f
        val diodeRadius = (size / 25f) * 0.38f

        // Smoked circular glass chassis
        val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#08080B")
            this.style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, matrixRadius + diodeRadius * 1.5f, chassisPaint)

        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.FILL
        }
        val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1C22")
            this.style = Paint.Style.FILL
        }

        val step = (matrixRadius * 2f) / 24f
        val startX = center - matrixRadius
        val startY = center - matrixRadius

        for (row in 0 until 25) {
            for (col in 0 until 25) {
                val idx = row * 25 + col
                val cx = startX + col * step
                val cy = startY + row * step

                val distSq = (cx - center) * (cx - center) + (cy - center) * (cy - center)
                if (distSq <= matrixRadius * matrixRadius) {
                    val brightness = rawBytes[idx].toInt() and 0xFF
                    if (brightness > 20) {
                        activePaint.alpha = brightness
                        canvas.drawCircle(cx, cy, diodeRadius, activePaint)
                    } else {
                        canvas.drawCircle(cx, cy, diodeRadius * 0.7f, inactivePaint)
                    }
                }
            }
        }
        return bitmap
    }
}

