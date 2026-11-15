package com.example.odysseyglyph

import android.content.Context
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object HardwareVideoDecoder {
    private const val TAG = "HardwareVideoDecoder"
    private const val TIMEOUT_US = 10000L // 10ms
    const val MATRIX_SIZE = 25

    class CropData(
        val luma: ByteArray,
        val width: Int,
        val height: Int
    )

    /**
     * Decodes video frames directly on the Qualcomm hardware VPU via MediaCodec + MediaExtractor.
     * Decouples VPU decoding from parallel CPU downsampling across all 8 cores with worker-local
     * scratch buffers to eliminate garbage collection overhead and maximize throughput.
     *
     * @return true if decoding succeeded and populated the needed frames; false if caller should fallback.
     */
    fun decodeVideoFrames(
        context: Context,
        mediaUri: Uri,
        startTimeMs: Long,
        endTimeMs: Long,
        targetFps: Int,
        neededIndices: List<Int>,
        framesByIndex: MutableMap<Int, ByteArray>,
        videoCx: Float,
        videoCy: Float,
        videoRadius: Float,
        enhancementMode: Int,
        isCancelled: AtomicBoolean,
        onProgress: (Int) -> Unit
    ): Boolean {
        val startTimeWallClock = System.currentTimeMillis()
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val executor = Executors.newFixedThreadPool(numCores) { runnable ->
            Thread {
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY)
                } catch (ignored: Throwable) {}
                runnable.run()
            }
        }
        val threadLocalScratch = ThreadLocal.withInitial { LedTone.ScratchBuffer() }
        val inFlightPermits = Semaphore(numCores * 2) // Cap in-flight memory to ~16 frames (< 20MB)
        val futures = ArrayList<Future<*>>()
        val processedCount = AtomicInteger(0)
        val timeDownsampleCpuNs = AtomicLong(0L)

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, mediaUri, null)

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                Log.w(TAG, "No video track found in media")
                return false
            }

            extractor.selectTrack(videoTrackIndex)
            val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false

            // Query rotation metadata safely
            val rotation = try {
                if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    videoFormat.getInteger(MediaFormat.KEY_ROTATION)
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, mediaUri)
                    val r = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    retriever.release()
                    r
                }
            } catch (e: Exception) {
                0
            }

            // Create Qualcomm hardware decoder and configure for flexible YUV_420_888 buffer mode
            decoder = MediaCodec.createDecoderByType(mime)
            videoFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            decoder.configure(videoFormat, null, null, 0)
            decoder.start()

            val intervalMs = 1000L / targetFps
            val startUs = startTimeMs * 1000L
            val endUs = endTimeMs * 1000L

            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            val missingIndices = neededIndices.filter { !framesByIndex.containsKey(it) }.toSet()
            if (missingIndices.isEmpty()) {
                onProgress(85)
                return true
            }

            val alreadyCached = neededIndices.size - missingIndices.size
            var timeCodecNs = 0L
            var loopCount = 0

            while (!isCancelled.get()) {
                loopCount++
                if (!isEOS) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val tCodec0 = System.nanoTime()
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                timeCodecNs += (System.nanoTime() - tCodec0)
                if (outIndex >= 0) {
                    loopCount = 0 // reset idle loop counter
                    val ptsUs = bufferInfo.presentationTimeUs
                    val ptsMs = ptsUs / 1000L

                    if (ptsUs >= startUs && ptsUs <= endUs + (intervalMs * 1000L)) {
                        val frameIdx = (ptsMs / intervalMs).toInt()

                        if (missingIndices.contains(frameIdx) && !framesByIndex.containsKey(frameIdx)) {
                            val image = decoder.getOutputImage(outIndex)
                            if (image != null) {
                                val cropData = extractCropPixels(
                                    image = image,
                                    videoCx = videoCx,
                                    videoCy = videoCy,
                                    videoRadius = videoRadius,
                                    rotation = rotation
                                )
                                image.close()

                                if (cropData != null) {
                                    inFlightPermits.acquire()
                                    val future = executor.submit {
                                        try {
                                            if (!isCancelled.get()) {
                                                val t0 = System.nanoTime()
                                                val scratch = threadLocalScratch.get()
                                                val frame = downsampleCrop(cropData, enhancementMode, scratch)
                                                timeDownsampleCpuNs.addAndGet(System.nanoTime() - t0)
                                                framesByIndex[frameIdx] = frame
                                                val count = processedCount.incrementAndGet()
                                                val prog = ((alreadyCached + count).toFloat() / neededIndices.size * 85).toInt().coerceIn(0, 85)
                                                onProgress(prog)
                                            }
                                        } finally {
                                            inFlightPermits.release()
                                        }
                                    }
                                    futures.add(future)
                                }
                            }
                        }
                    }

                    decoder.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || ptsUs > endUs + (intervalMs * 1000L)) {
                        break
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "MediaCodec output format changed: ${decoder.outputFormat}")
                } else if (isEOS && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (loopCount > 30) break
                }
            }

            // Await all parallel workers to complete downsampling
            for (f in futures) {
                if (isCancelled.get()) break
                try {
                    f.get()
                } catch (ignored: Exception) {}
            }
            executor.shutdown()
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS)
            } catch (ignored: InterruptedException) {}

            // Fill any minor framerate decimation gaps by repeating the nearest decoded frame
            if (neededIndices.isNotEmpty()) {
                var lastValidFrame: ByteArray? = null
                for (idx in neededIndices) {
                    val frame = framesByIndex[idx]
                    if (frame != null) {
                        lastValidFrame = frame
                    } else if (lastValidFrame != null) {
                        framesByIndex[idx] = lastValidFrame
                    }
                }
                // Backward fill for initial gap if needed
                for (idx in neededIndices.indices.reversed()) {
                    val k = neededIndices[idx]
                    if (!framesByIndex.containsKey(k) && idx + 1 < neededIndices.size) {
                        val next = framesByIndex[neededIndices[idx + 1]]
                        if (next != null) framesByIndex[k] = next
                    }
                }
            }

            val allFilled = neededIndices.all { framesByIndex.containsKey(it) }
            val elapsedMs = System.currentTimeMillis() - startTimeWallClock
            val decodedTotal = processedCount.get()
            val effectiveFps = if (elapsedMs > 0) (decodedTotal * 1000f / elapsedMs) else 0f
            val codecMs = timeCodecNs / 1000000L
            val totalCpuWorkMs = timeDownsampleCpuNs.get() / 1000000L
            Log.i(TAG, "[BENCHMARK] Hardware decoding completed: $decodedTotal frames in ${elapsedMs}ms ($effectiveFps FPS) [VPU Codec: ${codecMs}ms, CPU Work: ${totalCpuWorkMs}ms across $numCores cores]")
            onProgress(85)
            return allFilled && !isCancelled.get()

        } catch (e: Throwable) {
            Log.w(TAG, "Hardware video decoding failed, falling back to MediaMetadataRetriever", e)
            return false
        } finally {
            try { executor.shutdownNow() } catch (ignored: Throwable) {}
            try { decoder?.stop() } catch (ignored: Throwable) {}
            try { decoder?.release() } catch (ignored: Throwable) {}
            try { extractor?.release() } catch (ignored: Throwable) {}
        }
    }

    fun extractCropPixels(
        image: Image,
        videoCx: Float,
        videoCy: Float,
        videoRadius: Float,
        rotation: Int = 0
    ): CropData? {
        val plane = image.planes[0] // Luminance Y plane
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bufW = image.width
        val bufH = image.height

        val dispW = if (rotation == 90 || rotation == 270) bufH else bufW
        val dispH = if (rotation == 90 || rotation == 270) bufW else bufH

        val cropSide = (videoRadius * 2).toInt()
        val left = (videoCx - videoRadius).toInt().coerceIn(0, dispW - 1)
        val top = (videoCy - videoRadius).toInt().coerceIn(0, dispH - 1)
        val width = cropSide.coerceAtMost(dispW - left)
        val height = cropSide.coerceAtMost(dispH - top)

        if (width <= 0 || height <= 0) return null

        val cropLuma = ByteArray(width * height)
        for (y in 0 until height) {
            val dispY = top + y
            val dstOffset = y * width
            for (x in 0 until width) {
                val dispX = left + x
                val bx: Int
                val by: Int
                when (rotation) {
                    90 -> {
                        bx = dispY.coerceIn(0, bufW - 1)
                        by = (bufH - 1 - dispX).coerceIn(0, bufH - 1)
                    }
                    180 -> {
                        bx = (bufW - 1 - dispX).coerceIn(0, bufW - 1)
                        by = (bufH - 1 - dispY).coerceIn(0, bufH - 1)
                    }
                    270 -> {
                        bx = (bufW - 1 - dispY).coerceIn(0, bufW - 1)
                        by = dispX.coerceIn(0, bufH - 1)
                    }
                    else -> {
                        bx = dispX.coerceIn(0, bufW - 1)
                        by = dispY.coerceIn(0, bufH - 1)
                    }
                }
                cropLuma[dstOffset + x] = buffer.get(by * rowStride + bx * pixelStride)
            }
        }
        return CropData(cropLuma, width, height)
    }

    fun downsampleCrop(
        cropData: CropData,
        enhancementMode: Int,
        scratch: LedTone.ScratchBuffer? = null
    ): ByteArray {
        val width = cropData.width
        val height = cropData.height
        val cropLuma = cropData.luma

        return if (enhancementMode == 2) {
            // --- MODE 2: STRICT 100% LEGACY BOX FILTER ---
            val out = ByteArray(MATRIX_SIZE * MATRIX_SIZE)
            val scaleX = width.toFloat() / MATRIX_SIZE
            val scaleY = height.toFloat() / MATRIX_SIZE
            for (gy in 0 until MATRIX_SIZE) {
                val y0 = (gy * scaleY).toInt()
                val y1 = ((gy + 1) * scaleY).toInt().coerceAtMost(height)
                for (gx in 0 until MATRIX_SIZE) {
                    val x0 = (gx * scaleX).toInt()
                    val x1 = ((gx + 1) * scaleX).toInt().coerceAtMost(width)
                    var sumLuma = 0L
                    var count = 0
                    for (y in y0 until y1) {
                        val rowOff = y * width
                        for (x in x0 until x1) {
                            val lum = cropLuma[rowOff + x].toInt() and 0xFF
                            sumLuma += lum
                            count++
                        }
                    }
                    val avg = if (count > 0) (sumLuma / count).toInt() else 0
                    out[gy * MATRIX_SIZE + gx] = avg.toByte()
                }
            }
            out
        } else {
            LedTone.downsampleLuma(cropLuma, width, height, scratch)
        }
    }

    fun downsampleYuvImage(
        image: Image,
        videoCx: Float,
        videoCy: Float,
        videoRadius: Float,
        enhancementMode: Int,
        rotation: Int = 0
    ): ByteArray? {
        val cropData = extractCropPixels(image, videoCx, videoCy, videoRadius, rotation) ?: return null
        return downsampleCrop(cropData, enhancementMode)
    }
}
