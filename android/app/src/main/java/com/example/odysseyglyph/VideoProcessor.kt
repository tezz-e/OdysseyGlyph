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
        val adaptiveGain: Float,
        val gammaLift: Float = 1.0f
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
        spreadThreshold: Float = 150f,
        mode: Int = 0 // 0 = Auto, 1 = On, 2 = Off
    ): AdaptiveRange {
        if (mode == 2 || activeLuma.isEmpty() || frameMax <= frameMin) {
            val safeRange = (frameMax - frameMin).coerceAtLeast(1f)
            return AdaptiveRange(frameMin, frameMax, safeRange, 0f, 1.0f)
        }

        val sorted = activeLuma.sorted()
        // Robust 3rd and 97th percentiles
        val p3 = sorted[(sorted.size * 0.03f).toInt().coerceIn(0, sorted.size - 1)]
        val p97 = sorted[(sorted.size * 0.97f).toInt().coerceIn(0, sorted.size - 1)]

        if (mode == 1) {
            // Force ON: maximize dynamic range stretch and perceptual shadow lift
            val effectiveMin = frameMin * 0.25f + p3 * 0.75f
            val effectiveMax = frameMax * 0.25f + p97 * 0.75f
            val effectiveRange = (effectiveMax - effectiveMin).coerceAtLeast(1f)
            return AdaptiveRange(effectiveMin, effectiveMax, effectiveRange, 1.0f, 0.82f)
        }

        // Mode 0: AUTO
        val dynamicSpread = p97 - p3
        val adaptiveGain = if (dynamicSpread < spreadThreshold) {
            ((spreadThreshold - dynamicSpread) / spreadThreshold).coerceIn(0f, 1f)
        } else {
            0f
        }

        val effectiveMin = frameMin * (1f - adaptiveGain * 0.70f) + p3 * (adaptiveGain * 0.70f)
        val effectiveMax = frameMax * (1f - adaptiveGain * 0.70f) + p97 * (adaptiveGain * 0.70f)
        val effectiveRange = (effectiveMax - effectiveMin).coerceAtLeast(1f)
        val gamma = 1.0f - (adaptiveGain * 0.18f) // Smooth lift from 1.0 down to 0.82

        return AdaptiveRange(effectiveMin, effectiveMax, effectiveRange, adaptiveGain, gamma)
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
        enhancementMode: Int = 0,
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
                                val frame = extractAndDownsampleFrame(bitmap, videoCx, videoCy, videoRadius, enhancementMode)
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
                        
                        val frame = extractAndDownsampleFrame(bitmap, videoCx, videoCy, videoRadius, enhancementMode)
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
                    
                    val frame = extractAndDownsampleFrame(fullBitmap, scaledCx, scaledCy, scaledRadius, enhancementMode)
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
                    val adaptiveRange = calculateAdaptiveRange(frameMin, frameMax, activeLuma, mode = enhancementMode)
                    val (centerWeight, neighborWeight) = calculateSharpeningWeights(adaptiveRange.adaptiveGain)

                    // Apply 3x3 Sharpening kernel
                    val sharpenedGrid = Array(MATRIX_SIZE) { FloatArray(MATRIX_SIZE) }
                    for (y in 0 until MATRIX_SIZE) {
                        for (x in 0 until MATRIX_SIZE) {
                            if (y == 0 || y == MATRIX_SIZE - 1 || x == 0 || x == MATRIX_SIZE - 1 || !sharpen || enhancementMode != 2) {
                                sharpenedGrid[y][x] = grid[y][x]
                            } else {
                                val sum = centerWeight * grid[y][x] - 
                                          neighborWeight * (grid[y-1][x] + grid[y+1][x] + grid[y][x-1] + grid[y][x+1])
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
                                
                                // Perceptual LED curve: lift shadow midtones so facial features & contours don't crush into black
                                val lifted = if (adaptiveRange.gammaLift < 1.0f) {
                                    Math.pow(stretched.toDouble(), adaptiveRange.gammaLift.toDouble()).toFloat()
                                } else {
                                    stretched
                                }
                                
                                // Apply contrast multiplier
                                val adjustedStretched = ((lifted - 0.5f) * contrastMulti + 0.5f).coerceIn(0f, 1f)
                                
                                val contrasted = adjustedStretched * adjustedStretched * (3f - 2f * adjustedStretched)
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

    private fun extractAndDownsampleFrame(bitmap: Bitmap, videoCx: Float, videoCy: Float, videoRadius: Float, enhancementMode: Int = 0): ByteArray? {
        val cropSide = (videoRadius * 2).toInt()
        val left = (videoCx - videoRadius).toInt().coerceIn(0, bitmap.width - 1)
        val top = (videoCy - videoRadius).toInt().coerceIn(0, bitmap.height - 1)
        val width = cropSide.coerceAtMost(bitmap.width - left)
        val height = cropSide.coerceAtMost(bitmap.height - top)
        
        if (width <= 0 || height <= 0) return null

        val square = Bitmap.createBitmap(bitmap, left, top, width, height)
        val result = downsampleSquareFrame(square, mode = enhancementMode)
        if (square != bitmap) square.recycle()
        return result
    }

    fun downsampleSquareFrame(square: Bitmap, mode: Int = 0): ByteArray? {
        val w = square.width
        val h = square.height
        val pixels = IntArray(w * h)
        square.getPixels(pixels, 0, w, 0, 0, w, h)
        
        if (mode == 2) {
            // --- MODE 2: STRICT 100% LEGACY BOX FILTER ---
            val small = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val scaleX = w.toFloat() / MATRIX_SIZE
            val scaleY = h.toFloat() / MATRIX_SIZE
            val outPixels = IntArray(MATRIX_SIZE * MATRIX_SIZE)
            for (y in 0 until MATRIX_SIZE) {
                for (x in 0 until MATRIX_SIZE) {
                    var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
                    val startX = (x * scaleX).toInt()
                    val endX = Math.min(w, ((x + 1) * scaleX).toInt())
                    val startY = (y * scaleY).toInt()
                    val endY = Math.min(h, ((y + 1) * scaleY).toInt())
                    for (sy in startY until endY) {
                        val offset = sy * w
                        for (sx in startX until endX) {
                            val px = pixels[offset + sx]
                            rSum += (px shr 16) and 0xFF
                            gSum += (px shr 8) and 0xFF
                            bSum += px and 0xFF
                            count++
                        }
                    }
                    if (count > 0) {
                        outPixels[y * MATRIX_SIZE + x] = android.graphics.Color.rgb(
                            (rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt()
                        )
                    }
                }
            }
            small.setPixels(outPixels, 0, MATRIX_SIZE, 0, 0, MATRIX_SIZE, MATRIX_SIZE)
            val grayFrame = convertToGrayscaleByteArray(small)
            small.recycle()
            return grayFrame
        }
        
        // --- MODE 0 (AUTO) & MODE 1 (ON): HIGH-DPI SUPER-RESOLUTION & SALIENT FEATURE DOWNSAMPLER ---
        return downsampleSuperRes(pixels, w, h)
    }
    
    fun downsampleSuperRes(pixels: IntArray, w: Int, h: Int): ByteArray {
        // 1. Convert to luma
        val luma = FloatArray(w * h)
        for (i in 0 until (w * h)) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // 2. High-DPI Super-Resolution Upscaling (target ~600-800px)
        val upScale = Math.max(2, 600 / Math.max(1, Math.min(w, h)))
        val upW = w * upScale
        val upH = h * upScale
        val upLuma = upscaleSuperRes(luma, w, h, upW, upH, 2.0f)

        // 3. Multi-scale local detail enhancement (boosts shadow details like eyes/wrinkles without lifting DC black)
        val boostedLuma = applyLocalFeatureBoost(upLuma, upW, upH, 1.8f)

        // 4. Salient Extrema Feature Downsampling to 25x25
        val scaleX = upW.toFloat() / MATRIX_SIZE
        val scaleY = upH.toFloat() / MATRIX_SIZE
        val out = ByteArray(MATRIX_SIZE * MATRIX_SIZE)

        for (gy in 0 until MATRIX_SIZE) {
            val cellCY = (gy + 0.5f) * scaleY
            val sy0 = Math.max(0, (cellCY - scaleY * 0.5f).toInt())
            val sy1 = Math.min(upH - 1, (cellCY + scaleY * 0.5f).toInt())

            for (gx in 0 until MATRIX_SIZE) {
                val cellCX = (gx + 0.5f) * scaleX
                val sx0 = Math.max(0, (cellCX - scaleX * 0.5f).toInt())
                val sx1 = Math.min(upW - 1, (cellCX + scaleX * 0.5f).toInt())

                var minV = 255f
                var maxV = 0f
                var sumV = 0f
                var count = 0

                for (sy in sy0..sy1) {
                    val row = sy * upW
                    for (sx in sx0..sx1) {
                        val v = boostedLuma[row + sx]
                        sumV += v
                        count++
                        if (v < minV) minV = v
                        if (v > maxV) maxV = v
                    }
                }

                val avg = if (count > 0) (sumV / count) else 128f
                val localContrast = maxV - minV

                val valToUse = if (localContrast > 40f) {
                    val distToMax = maxV - avg
                    val distToMin = avg - minV
                    if (distToMax > distToMin * 1.2f) {
                        // Sharp bright spike (e.g. canine fang, incisor tooth, whisker, catchlight)
                        avg * 0.3f + maxV * 0.7f
                    } else if (distToMin > distToMax * 1.2f) {
                        // Sharp dark crevice (e.g. pupil, nostril, deep oral cavity)
                        avg * 0.3f + minV * 0.7f
                    } else {
                        if (avg > 128f) avg * 0.65f + maxV * 0.35f else avg * 0.65f + minV * 0.35f
                    }
                } else {
                    avg
                }

                out[gy * MATRIX_SIZE + gx] = valToUse.toInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }

    private fun upscaleSuperRes(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int, edgeGain: Float): FloatArray {
        val dst = FloatArray(dw * dh)
        val scaleX = sw.toFloat() / dw
        val scaleY = sh.toFloat() / dh

        for (dy in 0 until dh) {
            val sy = (dy + 0.5f) * scaleY - 0.5f
            val iy = Math.floor(sy.toDouble()).toInt()
            val fy = sy - iy
            val row = dy * dw
            for (dx in 0 until dw) {
                val sx = (dx + 0.5f) * scaleX - 0.5f
                val ix = Math.floor(sx.toDouble()).toInt()
                val fx = sx - ix

                var value = 0f
                for (m in -1..2) {
                    val py = (iy + m).coerceIn(0, sh - 1)
                    val wy = cubicWeight(m - fy)
                    val srcRow = py * sw
                    for (n in -1..2) {
                        val px = (ix + n).coerceIn(0, sw - 1)
                        val wx = cubicWeight(n - fx)
                        value += src[srcRow + px] * wx * wy
                    }
                }
                dst[row + dx] = value.coerceIn(0f, 255f)
            }
        }

        val enhanced = FloatArray(dw * dh)
        val rad = Math.max(1, (dw / sw) / 2)
        for (y in 0 until dh) {
            val row = y * dw
            val y0 = Math.max(0, y - rad)
            val y1 = Math.min(dh - 1, y + rad)
            for (x in 0 until dw) {
                val x0 = Math.max(0, x - rad)
                val x1 = Math.min(dw - 1, x + rad)
                var sum = 0f
                var count = 0
                for (ny in y0..y1) {
                    val nRow = ny * dw
                    for (nx in x0..x1) {
                        sum += dst[nRow + nx]
                        count++
                    }
                }
                val blurred = sum / count
                val diff = dst[row + x] - blurred
                enhanced[row + x] = (dst[row + x] + edgeGain * diff).coerceIn(0f, 255f)
            }
        }
        return enhanced
    }

    private fun cubicWeight(x: Float): Float {
        val ax = Math.abs(x)
        return if (ax <= 1f) {
            (1.5f * ax - 2.5f) * ax * ax + 1f
        } else if (ax < 2f) {
            ((-0.5f * ax + 2.5f) * ax - 4f) * ax + 2f
        } else {
            0f
        }
    }

    private fun applyLocalFeatureBoost(src: FloatArray, w: Int, h: Int, shadowBoost: Float): FloatArray {
        val bg = FloatArray(w * h)
        val bgRad = Math.max(8, (Math.min(w, h) * 0.10f).toInt())
        val temp = FloatArray(w * h)

        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            var count = 0
            val maxInit = Math.min(w - 1, bgRad)
            for (x in 0..maxInit) {
                sum += src[row + x]
                count++
            }
            for (x in 0 until w) {
                temp[row + x] = sum / count
                val addX = x + bgRad + 1
                if (addX < w) { sum += src[row + addX]; count++ }
                val remX = x - bgRad
                if (remX >= 0) { sum -= src[row + remX]; count-- }
            }
        }

        for (x in 0 until w) {
            var sum = 0f
            var count = 0
            val maxInit = Math.min(h - 1, bgRad)
            for (y in 0..maxInit) {
                sum += temp[y * w + x]
                count++
            }
            for (y in 0 until h) {
                bg[y * w + x] = sum / count
                val addY = y + bgRad + 1
                if (addY < h) { sum += temp[addY * w + x]; count++ }
                val remY = y - bgRad
                if (remY >= 0) { sum -= temp[remY * w + x]; count-- }
            }
        }

        val result = FloatArray(w * h)
        for (i in 0 until (w * h)) {
            val value = src[i]
            val localBg = bg[i]
            val detail = value - localBg
            val shadowWeight = Math.max(0f, (140f - localBg) / 140f)
            val gain = 1.0f + shadowWeight * shadowBoost
            result[i] = (localBg + detail * gain).coerceIn(0f, 255f)
        }
        return result
    }

    fun downsampleBilateral(pixels: IntArray, w: Int, h: Int): ByteArray {
        return downsampleSuperRes(pixels, w, h)
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
