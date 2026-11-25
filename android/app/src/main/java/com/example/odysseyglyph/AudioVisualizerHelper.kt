package com.example.odysseyglyph

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class AudioVisualizerHelper(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private val isRecording = java.util.concurrent.atomic.AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private val lock = Any()

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

    // Current levels (0.0 to 1.0)
    var currentLevels = FloatArray(13) { 0f }
        private set

    fun start() {
        synchronized(lock) {
            if (isRecording.get()) return

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e("AudioVisualizer", "RECORD_AUDIO permission not granted")
                return
            }

            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording()
                    audioRecord = record
                    isRecording.set(true)
                    recordingThread = Thread({ processAudio(record) }, "AudioVisualizerThread").apply {
                        start()
                    }
                } else {
                    try { record.release() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                isRecording.set(false)
                audioRecord = null
                try { record?.release() } catch (_: Exception) {}
                Log.e("AudioVisualizer", "Error initializing AudioRecord", e)
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            val threadToJoin = recordingThread
            val recordToRelease = audioRecord
            if (!isRecording.get() && recordToRelease == null && threadToJoin == null) return

            isRecording.set(false)
            recordingThread = null
            audioRecord = null

            try {
                recordToRelease?.stop()
            } catch (_: Exception) {}
            try {
                threadToJoin?.join(150)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            try {
                recordToRelease?.release()
            } catch (_: Exception) {}
        }
    }

    private fun processAudio(record: AudioRecord) {
        val buffer = ShortArray(1024)
        val doubleBuffer = DoubleArray(1024)
        val imagBuffer = DoubleArray(1024)

        while (isRecording.get()) {
            val readSize = try {
                record.read(buffer, 0, buffer.size)
            } catch (_: Exception) {
                break
            }
            if (readSize > 0) {
                // Apply Hann window and copy to double array
                for (i in 0 until readSize) {
                    val multiplier = 0.5 * (1 - cos(2 * Math.PI * i / (readSize - 1)))
                    doubleBuffer[i] = buffer[i] * multiplier
                    imagBuffer[i] = 0.0
                }
                
                // Zero pad if needed
                for (i in readSize until 1024) {
                    doubleBuffer[i] = 0.0
                    imagBuffer[i] = 0.0
                }

                // Compute FFT (size 1024)
                fft(doubleBuffer, imagBuffer)

                // Calculate magnitudes for 13 bands
                // 1024 samples at 44100Hz -> ~43Hz per bin. Nyquist = 22050Hz (512 bins)
                // We want 13 log-spaced bands between ~40Hz and ~16000Hz.
                val bands = listOf(
                    1..2,      // 43-86Hz
                    2..3,      // 86-129Hz
                    3..5,      // 129-215Hz
                    5..7,      // 215-301Hz
                    7..11,     // 301-473Hz
                    11..16,    // 473-688Hz
                    16..24,    // 688-1032Hz
                    24..35,    // 1032-1505Hz
                    35..52,    // 1505-2236Hz
                    52..77,    // 2236-3311Hz
                    77..115,   // 3311-4945Hz
                    115..170,  // 4945-7310Hz
                    170..300   // 7310-12900Hz
                )

                for (b in 0 until 13) {
                    var sum = 0.0
                    val range = bands[b]
                    for (i in range) {
                        val real = doubleBuffer[i]
                        val imag = imagBuffer[i]
                        val magnitude = sqrt(real * real + imag * imag)
                        sum += magnitude
                    }
                    val avgMagnitude = sum / (range.last - range.first + 1)
                    
                    // Convert to dB. Max possible magnitude is ~16 million (144 dB)
                    val db = 20 * log10(avgMagnitude.coerceAtLeast(1.0))
                    
                    // Boost higher frequencies to account for pink noise roll-off in music
                    val boost = b * 3.0
                    val adjustedDb = db + boost
                    
                    // Map from [85 dB, 130 dB] to [0f, 1f]. Adjust as needed for mic sensitivity.
                    var normalized = ((adjustedDb - 85.0) / 45.0).toFloat().coerceIn(0f, 1f)
                    
                    // Apply a power curve (x^3) to make it punchy: stay low until heavy hits drop
                    normalized = normalized * normalized * normalized
                    
                    // Snappy attack, smooth decay
                    if (normalized > currentLevels[b]) {
                        currentLevels[b] = currentLevels[b] * 0.2f + normalized * 0.8f // Fast Attack
                    } else {
                        currentLevels[b] = currentLevels[b] * 0.90f + normalized * 0.10f // Smooth Decay
                    }
                }
            }
        }
    }

    // Basic Cooley-Tukey FFT implementation
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var m = n / 2
            while (j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        // Cooley-Tukey decimation-in-time
        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val angle = -2 * Math.PI / size
            val wReal = cos(angle)
            val wImag = sin(angle)

            for (i in 0 until n step size) {
                var currentWReal = 1.0
                var currentWImag = 0.0

                for (k in 0 until halfSize) {
                    val index = i + k
                    val indexHalf = index + halfSize

                    val tReal = currentWReal * real[indexHalf] - currentWImag * imag[indexHalf]
                    val tImag = currentWReal * imag[indexHalf] + currentWImag * real[indexHalf]

                    real[indexHalf] = real[index] - tReal
                    imag[indexHalf] = imag[index] - tImag
                    real[index] += tReal
                    imag[index] += tImag

                    val nextWReal = currentWReal * wReal - currentWImag * wImag
                    currentWImag = currentWReal * wImag + currentWImag * wReal
                    currentWReal = nextWReal
                }
            }
            size *= 2
        }
    }
}
