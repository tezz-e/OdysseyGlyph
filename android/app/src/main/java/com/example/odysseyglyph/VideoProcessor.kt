package com.example.odysseyglyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object VideoProcessor {
    const val MATRIX_SIZE = 25

    data class AdaptiveRange(
        val effectiveMin: Float,
        val effectiveMax: Float,
        val effectiveRange: Float,
        val adaptiveGain: Float
    )

    /**
     * Automatically analyzes the luminance distribution within the active circular aperture.
     * For high-contrast media (e.g. camera close-ups), adaptiveGain is 0.0 and min/max are preserved untouched.
     * For low-contrast or low-res media (e.g. faces or compressed images with narrow dynamic range),
     * adaptiveGain smoothly scales up to expand the subject's midtones across the full LED range.
     */
    fun calculateAdaptiveRange(
        frameMin: Float,
        frameMax: Float,
        activeLuma: List<Float>,
        spreadThreshold: Float = 150f
    ): AdaptiveRange {
        if (activeLuma.isEmpty() || frameMax <= frameMin) {
            val safeRange = (frameMax - frameMin).coerceAtLeast(1f)
            return AdaptiveRange(frameMin, frameMax, safeRange, 0f)
        }

        val sorted = activeLuma.sorted()
        val p5 = sorted[(sorted.size * 0.05f).toInt().coerceIn(0, sorted.size - 1)]
        val p95 = sorted[(sorted.size * 0.95f).toInt().coerceIn(0, sorted.size - 1)]
        val dynamicSpread = p95 - p5

        val adaptiveGain = if (dynamicSpread < spreadThreshold) {
            ((spreadThreshold - dynamicSpread) / spreadThreshold).coerceIn(0f, 1f)
        } else {
            0f
        }

        val effectiveMin = frameMin * (1f - adaptiveGain * 0.65f) + p5 * (adaptiveGain * 0.65f)
        val effectiveMax = frameMax * (1f - adaptiveGain * 0.65f) + p95 * (adaptiveGain * 0.65f)
        val effectiveRange = (effectiveMax - effectiveMin).coerceAtLeast(1f)

        return AdaptiveRange(effectiveMin, effectiveMax, effectiveRange, adaptiveGain)
    }

    /**
     * Computes energy-preserving 3x3 Laplacian sharpening weights.
     * When adaptiveGain is 0.0 (standard camera shots), returns standard weights (center=5.0, neighbor=1.0).
     * For low-contrast subjects (adaptiveGain > 0.0), edge contrast is smoothly enhanced.
     * Always satisfies center - 4 * neighbor == 1.0 (mean-preserving).
     */
    fun calculateSharpeningWeights(adaptiveGain: Float): Pair<Float, Float> {
        val center = 5f + (adaptiveGain.coerceIn(0f, 1f) * 1.5f)
        val neighbor = (center - 1f) / 4f
        return Pair(center, neighbor)
    }

    fun processMedia(
        context: Context, 
        mediaUri: Uri, 
        audioUri: Uri?,
        mediaType: Int, // 0=video, 1=gif, 2=static
        startTimeMs: Long,
        endTimeMs: Long,
        targetFps: Int,
        playbackMode: Int,
        invertColors: Boolean,
        contrastMulti: Float = 1.0f,
        brightnessMulti: Float = 1.0f,
        imageDurationSec: Int = 5,
        sharpen: Boolean = true,
        cropCx: Float,
        cropCy: Float,
        cropRadius: Float,
        inverseTransform: Matrix,
        slotIndex: Int = 1,
        isCancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Int) -> Unit, 
        onComplete: (Boolean, String?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        
        thread {
            try {
                // Map screen coordinates to raw video coordinates using the inverse transform matrix
                val pts = floatArrayOf(cropCx, cropCy, cropCx + cropRadius, cropCy)
                inverseTransform.mapPoints(pts)
                val videoCx = pts[0]
                val videoCy = pts[1]
                val videoRadius = Math.hypot((pts[2] - pts[0]).toDouble(), (pts[3] - pts[1]).toDouble()).toFloat()

                val rawFrames = mutableListOf<ByteArray>()

                if (mediaType == 0) {
                    // --- VIDEO ---
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, mediaUri)
                        
                        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toLongOrNull() ?: run {
                            mainHandler.post { onComplete(false, "Could not determine video duration.") }
                            return@thread
                        }
                        
                        val validEndMs = if (endTimeMs <= 0 || endTimeMs > durationMs) durationMs else endTimeMs
                        val validStartMs = if (startTimeMs < 0 || startTimeMs >= validEndMs) 0L else startTimeMs
                        
                        val processDurationMs = validEndMs - validStartMs
                        if (processDurationMs <= 0) {
                            mainHandler.post { onComplete(false, "Trimmed duration is zero.") }
                            return@thread
                        }
                        
                        val intervalMs = 1000L / targetFps
                        val totalFrames = (processDurationMs / intervalMs).toInt()
                        if (totalFrames == 0) {
                            mainHandler.post { onComplete(false, "Video is too short for the selected FPS.") }
                            return@thread
                        }

                        for (i in 0 until totalFrames) {
                            if (isCancelled.get()) {
                                mainHandler.post { onComplete(false, "Cancelled by user.") }
                                return@thread
                            }
                            val timeUs = (validStartMs + i * intervalMs) * 1000
                            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                            if (bitmap != null) {
                                val frame = extractAndDownsampleFrame(bitmap, videoCx, videoCy, videoRadius)
                                if (frame != null) rawFrames.add(frame)
                                bitmap.recycle()
                            }
                            val prog = (i.toFloat() / totalFrames * 50).toInt()
                            mainHandler.post { onProgress(prog) }
                        }
                    } finally {
                        retriever.release()
                    }
                } else if (mediaType == 1) {
                    // --- GIF ---
                    val stream = context.contentResolver.openInputStream(mediaUri)
                    val movie = android.graphics.Movie.decodeStream(stream)
                    stream?.close()
                    
                    if (movie == null) {
                        mainHandler.post { onComplete(false, "Could not decode GIF.") }
                        return@thread
                    }
                    
                    val durationMs = movie.duration().toLong().coerceAtLeast(1L)
                    val validEndMs = if (endTimeMs <= 0 || endTimeMs > durationMs) durationMs else endTimeMs
                    val validStartMs = if (startTimeMs < 0 || startTimeMs >= validEndMs) 0L else startTimeMs
                    val processDurationMs = validEndMs - validStartMs
                    
                    val intervalMs = 1000L / targetFps
                    val totalFrames = (processDurationMs / intervalMs).toInt().coerceAtLeast(1)
                    
                    val bitmap = Bitmap.createBitmap(movie.width().coerceAtLeast(1), movie.height().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    
                    for (i in 0 until totalFrames) {
                        if (isCancelled.get()) {
                            mainHandler.post { onComplete(false, "Cancelled by user.") }
                            return@thread
                        }
                        val timeMs = validStartMs + i * intervalMs
                        movie.setTime(timeMs.toInt())
                        
                        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                        movie.draw(canvas, 0f, 0f)
                        
                        val frame = extractAndDownsampleFrame(bitmap, videoCx, videoCy, videoRadius)
                        if (frame != null) rawFrames.add(frame)
                        
                        val prog = (i.toFloat() / totalFrames * 50).toInt()
                        mainHandler.post { onProgress(prog) }
                    }
                    bitmap.recycle()
                } else {
                    // --- STATIC IMAGE ---
                    // Safely get bounds first without loading the image to prevent OOM
                    var stream = context.contentResolver.openInputStream(mediaUri)
                    val boundsOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                    stream?.close()
                    
                    val fullWidth = boundsOptions.outWidth
                    val fullHeight = boundsOptions.outHeight
                    
                    if (fullWidth <= 0 || fullHeight <= 0) {
                        mainHandler.post { onComplete(false, "Could not determine image dimensions.") }
                        return@thread
                    }
                    
                    // Calculate a safe sample size so the bitmap is around 1000px max (uses <4MB RAM)
                    var sampleSize = 1
                    while (fullWidth / sampleSize > 1500 || fullHeight / sampleSize > 1500) {
                        sampleSize *= 2
                    }
                    
                    stream = context.contentResolver.openInputStream(mediaUri)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = true
                    }
                    val fullBitmap = BitmapFactory.decodeStream(stream, null, options)
                    stream?.close()
                    
                    if (fullBitmap == null) {
                        mainHandler.post { onComplete(false, "Could not decode image.") }
                        return@thread
                    }
                    
                    // The coordinate mapping (inverseTransform) assumes the FULL ORIGINAL width/height.
                    // Because we downsampled the bitmap to save RAM, we must scale our crop circle coordinates to match the new size!
                    val scaleX = fullBitmap.width.toFloat() / fullWidth.toFloat()
                    val scaleY = fullBitmap.height.toFloat() / fullHeight.toFloat()
                    
                    val scaledCx = videoCx * scaleX
                    val scaledCy = videoCy * scaleY
                    val scaledRadius = videoRadius * Math.max(scaleX, scaleY)
                    
                    val frame = extractAndDownsampleFrame(fullBitmap, scaledCx, scaledCy, scaledRadius)
                    if (frame != null) {
                        val totalFrames = imageDurationSec * targetFps
                        for (i in 0 until totalFrames) {
                            if (isCancelled.get()) {
                                mainHandler.post { onComplete(false, "Cancelled by user.") }
                                return@thread
                            }
                            rawFrames.add(frame)
                        }
                    }
                    fullBitmap.recycle()
                    
                    mainHandler.post { onProgress(50) }
                }

                if (rawFrames.isEmpty()) {
                    mainHandler.post { onComplete(false, "No valid frames could be extracted.") }
                    return@thread
                }

                // Process contrast and sharpening for all frames
                val finalFrames = mutableListOf<ByteArray>()
                val center = (MATRIX_SIZE - 1) / 2f
                val radiusSq = (MATRIX_SIZE / 2f) * (MATRIX_SIZE / 2f)

                for ((index, frame) in rawFrames.withIndex()) {
                    if (isCancelled.get()) {
                        mainHandler.post { onComplete(false, "Cancelled by user.") }
                        return@thread
                    }
                    val processedFrame = ByteArray(MATRIX_SIZE * MATRIX_SIZE)
                    
                    // Extract to 2D grid for spatial filtering
                    val grid = Array(MATRIX_SIZE) { FloatArray(MATRIX_SIZE) }
                    var frameMin = 255f
                    var frameMax = 0f
                    val activeLuma = ArrayList<Float>(500)
                    for (y in 0 until MATRIX_SIZE) {
                        for (x in 0 until MATRIX_SIZE) {
                            val distSq = (x - center) * (x - center) + (y - center) * (y - center)
                            val v = (frame[y * MATRIX_SIZE + x].toInt() and 0xFF).toFloat()
                            grid[y][x] = v
                            if (distSq <= radiusSq) {
                                if (v < frameMin) frameMin = v
                                if (v > frameMax) frameMax = v
                                activeLuma.add(v)
                            }
                        }
                    }
                    
                    if (frameMax <= frameMin) frameMax = frameMin + 1
                    
                    // Auto-detect dynamic range and compute adaptive contrast stretch & sharpening weights
                    val adaptiveRange = calculateAdaptiveRange(frameMin, frameMax, activeLuma)
                    val (centerWeight, neighborWeight) = calculateSharpeningWeights(adaptiveRange.adaptiveGain)

                    // Apply 3x3 Sharpening kernel
                    val sharpenedGrid = Array(MATRIX_SIZE) { FloatArray(MATRIX_SIZE) }
                    for (y in 0 until MATRIX_SIZE) {
                        for (x in 0 until MATRIX_SIZE) {
                            if (y == 0 || y == MATRIX_SIZE - 1 || x == 0 || x == MATRIX_SIZE - 1 || !sharpen) {
                                sharpenedGrid[y][x] = grid[y][x]
                            } else {
                                val sum = centerWeight * grid[y][x] - 
                                          neighborWeight * (grid[y-1][x] + grid[y+1][x] + grid[y][x-1] + grid[y+1][x])
                                sharpenedGrid[y][x] = sum.coerceIn(0f, 255f)
                            }
                        }
                    }

                    for (y in 0 until MATRIX_SIZE) {
                        for (x in 0 until MATRIX_SIZE) {
                            val distSq = (x - center) * (x - center) + (y - center) * (y - center)
                            val idx = y * MATRIX_SIZE + x
                            if (distSq <= radiusSq) {
                                val original = sharpenedGrid[y][x]
                                
                                val stretched = ((original - adaptiveRange.effectiveMin) / adaptiveRange.effectiveRange).coerceIn(0f, 1f)
                                
                                // Apply contrast multiplier
                                val adjustedStretched = ((stretched - 0.5f) * contrastMulti + 0.5f).coerceIn(0f, 1f)
                                
                                var contrasted = adjustedStretched * adjustedStretched * (3f - 2f * adjustedStretched)
                                var finalValue = (contrasted * 255f * brightnessMulti).toInt()
                                
                                if (invertColors) {
                                    finalValue = 255 - finalValue
                                }
                                processedFrame[idx] = finalValue.coerceIn(0, 255).toByte()
                            } else {
                                processedFrame[idx] = 0 // Mask out corners
                            }
                        }
                    }
                    finalFrames.add(processedFrame)
                    GlyphFrameBus.postFrame(processedFrame)
                    
                    val prog = 50 + (index.toFloat() / rawFrames.size * 40).toInt()
                    mainHandler.post { onProgress(prog) }
                }

                // Handle Ping-Pong mode (PlaybackMode == 2) for multi-frame media
                if (playbackMode == 2 && finalFrames.size > 2) {
                    val reverseFrames = finalFrames.subList(1, finalFrames.size - 1).reversed()
                    finalFrames.addAll(reverseFrames)
                }

                // Write to Preset Gallery
                val presetName = "Media Matrix ${System.currentTimeMillis() % 10000}"
                val presetId = PresetManager.createPreset(context, presetName, "MEDIA")
                val dir = PresetManager.getPresetsDir(context)
                
                val outFile = File(dir, "${presetId}.bin")
                FileOutputStream(outFile).use { fos ->
                    val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    header.putInt(finalFrames.size)
                    header.putInt(targetFps)
                    header.putInt(playbackMode)
                    fos.write(header.array())

                    for (frame in finalFrames) {
                        fos.write(frame)
                    }
                }
                
                // Handle Audio Attachment
                val audioFile = File(dir, "${presetId}.mp3")
                if (audioUri != null) {
                    try {
                        context.contentResolver.openInputStream(audioUri)?.use { input ->
                            FileOutputStream(audioFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Assign to requested slot
                if (slotIndex in 1..3) {
                    PresetManager.assignPresetToSlot(context, presetId, slotIndex)
                }

                mainHandler.post { onProgress(100) }
                mainHandler.post { onComplete(true, null) }
            } catch (e: Throwable) {
                e.printStackTrace()
                mainHandler.post { onComplete(false, e.message ?: "Unknown error occurred.") }
            }
        }
    }

    private fun extractAndDownsampleFrame(bitmap: Bitmap, videoCx: Float, videoCy: Float, videoRadius: Float): ByteArray? {
        val cropSide = (videoRadius * 2).toInt()
        val left = (videoCx - videoRadius).toInt().coerceIn(0, bitmap.width - 1)
        val top = (videoCy - videoRadius).toInt().coerceIn(0, bitmap.height - 1)
        val width = cropSide.coerceAtMost(bitmap.width - left)
        val height = cropSide.coerceAtMost(bitmap.height - top)
        
        if (width <= 0 || height <= 0) return null

        val square = Bitmap.createBitmap(bitmap, left, top, width, height)
        val result = downsampleSquareFrame(square)
        if (square != bitmap) square.recycle()
        return result
    }

    fun downsampleSquareFrame(square: Bitmap): ByteArray? {
        val small = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
        val scaleX = square.width.toFloat() / MATRIX_SIZE
        val scaleY = square.height.toFloat() / MATRIX_SIZE
        
        val pixels = IntArray(square.width * square.height)
        square.getPixels(pixels, 0, square.width, 0, 0, square.width, square.height)
        
        // For camera photos (scaleX > 20), featureWeight is 0.0 (pure arithmetic mean).
        // For low-res crops (scaleX < 20), featureWeight gently prevents fine dark features (pupils, whiskers)
        // from being averaged into oblivion by surrounding skin/fur.
        val featureWeight = if (scaleX < 20f) {
            (0.22f * (1f - (scaleX / 20f))).coerceIn(0f, 0.22f)
        } else {
            0f
        }
        
        val outPixels = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                var count = 0
                
                var minLuma = 255
                var minPx = 0
                
                val startX = (x * scaleX).toInt()
                val endX = ((x + 1) * scaleX).toInt().coerceIn(startX + 1, square.width)
                val startY = (y * scaleY).toInt()
                val endY = ((y + 1) * scaleY).toInt().coerceIn(startY + 1, square.height)
                
                for (sy in startY until endY) {
                    val offset = sy * square.width
                    for (sx in startX until endX) {
                        val px = pixels[offset + sx]
                        val r = android.graphics.Color.red(px)
                        val g = android.graphics.Color.green(px)
                        val b = android.graphics.Color.blue(px)
                        rSum += r
                        gSum += g
                        bSum += b
                        count++
                        
                        if (featureWeight > 0f) {
                            val luma = (r * 299 + g * 587 + b * 114) / 1000
                            if (luma < minLuma) {
                                minLuma = luma
                                minPx = px
                            }
                        }
                    }
                }
                
                if (count > 0) {
                    val avgR = (rSum / count).toInt()
                    val avgG = (gSum / count).toInt()
                    val avgB = (bSum / count).toInt()
                    
                    if (featureWeight > 0f) {
                        val minR = android.graphics.Color.red(minPx)
                        val minG = android.graphics.Color.green(minPx)
                        val minB = android.graphics.Color.blue(minPx)
                        val blendedR = (avgR * (1f - featureWeight) + minR * featureWeight).toInt().coerceIn(0, 255)
                        val blendedG = (avgG * (1f - featureWeight) + minG * featureWeight).toInt().coerceIn(0, 255)
                        val blendedB = (avgB * (1f - featureWeight) + minB * featureWeight).toInt().coerceIn(0, 255)
                        outPixels[y * MATRIX_SIZE + x] = android.graphics.Color.rgb(blendedR, blendedG, blendedB)
                    } else {
                        outPixels[y * MATRIX_SIZE + x] = android.graphics.Color.rgb(avgR, avgG, avgB)
                    }
                }
            }
        }
        small.setPixels(outPixels, 0, MATRIX_SIZE, 0, 0, MATRIX_SIZE, MATRIX_SIZE)
        
        val grayFrame = convertToGrayscaleByteArray(small)
        small.recycle()
        return grayFrame
    }

    private fun convertToGrayscaleByteArray(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val grayBytes = ByteArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = android.graphics.Color.red(color)
            val g = android.graphics.Color.green(color)
            val b = android.graphics.Color.blue(color)
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            grayBytes[i] = gray.toByte()
        }
        return grayBytes
    }

    fun processLyrics(
        context: Context,
        syncedLyrics: String,
        audioUri: Uri?,
        fontStyle: GlyphFontEngine.FontStyle,
        animationStyle: Int, // 0 = Flash, 1 = Scroll Left
        startTimeMs: Long = 0,
        endTimeMs: Long = Long.MAX_VALUE,
        targetFps: Int = 12,
        slotIndex: Int = 1,
        isCancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        
        thread {
            try {
                // 1. Parse LRC
                // Format: [mm:ss.xx] text
                val parsedLines = LrcUtils.parseLrc(syncedLyrics)
                
                if (parsedLines.isEmpty()) {
                    mainHandler.post { onComplete(false, "No valid synced lyrics found.") }
                    return@thread
                }
                
                android.util.Log.d("OdysseyLyrics", "parsed ${parsedLines.size} lines, first=${parsedLines.firstOrNull()}, last=${parsedLines.lastOrNull()}")
                
                // Calculate total duration
                val maxDurationMs = parsedLines.last().first + 5000L
                val validEndMs = if (endTimeMs <= 0 || endTimeMs > maxDurationMs) maxDurationMs else endTimeMs
                val validStartMs = if (startTimeMs < 0 || startTimeMs >= validEndMs) 0L else startTimeMs
                val processDurationMs = validEndMs - validStartMs
                
                if (processDurationMs <= 0) {
                    mainHandler.post { onComplete(false, "Trimmed duration is zero.") }
                    return@thread
                }
                
                val totalFrames = (processDurationMs * targetFps / 1000L).toInt()
                android.util.Log.d("OdysseyLyrics", "startTimeMs=$startTimeMs endTimeMs=$endTimeMs validStartMs=$validStartMs validEndMs=$validEndMs processDurationMs=$processDurationMs totalFrames=$totalFrames")
                
                if (totalFrames == 0) {
                    mainHandler.post { onComplete(false, "Selected range is too short.") }
                    return@thread
                }
                
                val finalFrames = mutableListOf<ByteArray>()
                
                for (i in 0 until totalFrames) {
                    if (isCancelled.get()) {
                        mainHandler.post { onComplete(false, "Cancelled by user.") }
                        return@thread
                    }
                    
                    val currentMs = validStartMs + i * 1000L / targetFps
                    
                    val frameData = LrcUtils.getFrameTextAtTime(parsedLines, currentMs, fontStyle, animationStyle)
                    if (frameData == null) {
                        finalFrames.add(ByteArray(625)) // Blank frame
                    } else {
                        val frameText = frameData.first
                        val offsetX = frameData.second
                        val frame = GlyphFontEngine.renderTextFrame(frameText, fontStyle, offsetX, autoScale = false)
                        finalFrames.add(frame)
                    }
                    
                    if (i % 100 == 0) {
                        val prog = (i.toFloat() / totalFrames * 90).toInt()
                        mainHandler.post { onProgress(prog) }
                    }
                }
                
                val blankCount = finalFrames.count { frame -> frame.all { it == 0.toByte() } }
                android.util.Log.d("OdysseyLyrics", "totalFrames=${finalFrames.size} blankFrames=$blankCount")
                
                // Write to Preset Gallery
                val presetName = "Lyric Matrix ${System.currentTimeMillis() % 10000}"
                val presetId = PresetManager.createPreset(context, presetName, "LYRIC")
                val dir = PresetManager.getPresetsDir(context)
                
                val outFile = File(dir, "${presetId}.bin")
                FileOutputStream(outFile).use { fos ->
                    val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    header.putInt(finalFrames.size)
                    header.putInt(targetFps)
                    header.putInt(0) // PlaybackMode: 0 = ONCE (since it's a full song)
                    header.putInt(startTimeMs.toInt()) // audioOffsetMs
                    fos.write(header.array())

                    for (frame in finalFrames) {
                        fos.write(frame)
                    }
                }
                
                // Handle Audio Attachment
                val audioFile = File(dir, "${presetId}.mp3")
                if (audioUri != null) {
                    try {
                        context.contentResolver.openInputStream(audioUri)?.use { input ->
                            FileOutputStream(audioFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Assign to requested slot
                if (slotIndex in 1..3) {
                    PresetManager.assignPresetToSlot(context, presetId, slotIndex)
                }

                mainHandler.post { onProgress(100) }
                mainHandler.post { onComplete(true, null) }
                
            } catch (e: Throwable) {
                e.printStackTrace()
                mainHandler.post { onComplete(false, e.message ?: "Unknown error.") }
            }
        }
    }
}
