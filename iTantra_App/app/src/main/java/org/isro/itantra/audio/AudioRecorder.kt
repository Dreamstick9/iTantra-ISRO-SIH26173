package org.isro.itantra.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Low-latency 16 kHz Mono 16-bit PCM Audio Recorder.
 * Tuned for downstream Neural ASR models (AI4Bharat IndicConformer, sherpa-onnx, Vosk, Silero VAD).
 *
 * Key Architectural Highlights:
 * - Dedicated single-thread audio dispatcher (Process.THREAD_PRIORITY_URGENT_AUDIO -19)
 * - MediaRecorder.AudioSource.VOICE_RECOGNITION for full 16 kHz spectral fidelity
 * - Anti-overrun circular buffer sizing: maxOf(minBufSize * 2, frameSizeBytes * 4)
 * - Thread-safe teardown preventing native JNI crashes
 */
class AudioRecorder(
    private val onAmplitudeChanged: ((Float) -> Unit)? = null,
    private val onFirstChunkCaptured: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioRecorder"
    }

    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "iTantra-AudioRecord-Urgent").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
        }
    }
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()

    private val isRecordingActive = AtomicBoolean(false)
    val isRecording: Boolean
        get() = isRecordingActive.get()

    private val currentAudioRecord = AtomicReference<AudioRecord?>(null)
    private var recordingJob: Job? = null
    private val recordedDataStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startRecording(
        scope: CoroutineScope,
        onAudioChunk: ((ByteArray, Int) -> Unit)? = null
    ): Boolean {
        if (isRecordingActive.get()) {
            Log.w(TAG, "AudioRecorder is already recording.")
            return false
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufSize <= 0) {
            Log.e(TAG, "AudioRecord parameter error: minBufSize = $minBufSize")
            return false
        }

        // 20 ms frame = 640 bytes. Allocate at least 4 frames or 2x minBufSize for headroom
        val frameSizeBytes = AudioConfig.FRAME_SIZE_BYTES
        val internalBufferSize = maxOf(minBufSize * 2, frameSizeBytes * 4)

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AudioConfig.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(format)
                .setBufferSizeInBytes(internalBufferSize)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate AudioRecord", e)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize (state != STATE_INITIALIZED)")
            record.release()
            return false
        }

        currentAudioRecord.set(record)
        isRecordingActive.set(true)

        synchronized(recordedDataStream) {
            recordedDataStream.reset()
        }

        recordingJob = scope.launch(audioDispatcher + CoroutineName("AudioRecordJob")) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ByteArray(frameSizeBytes)
            var isFirstFrame = true

            try {
                record.startRecording()
                Log.d(TAG, "AudioRecord hardware started successfully [16kHz Mono 16-bit]")

                while (isActive && isRecordingActive.get()) {
                    val bytesRead = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)

                    if (bytesRead > 0) {
                        if (isFirstFrame) {
                            isFirstFrame = false
                            onFirstChunkCaptured?.invoke()
                        }

                        // Accumulate for loopback playback
                        synchronized(recordedDataStream) {
                            recordedDataStream.write(buffer, 0, bytesRead)
                        }

                        // Compute in-place RMS amplitude for UI visualization
                        val rms = AudioUtils.calculateRmsFromPcm16(buffer, 0, bytesRead)
                        val level = AudioUtils.calculateVisualizerLevel(rms)
                        onAmplitudeChanged?.invoke(level)

                        // Emit chunk to optional subscriber
                        onAudioChunk?.invoke(buffer.copyOf(bytesRead), bytesRead)
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord.read error: $bytesRead")
                        break
                    }
                }
            } catch (c: CancellationException) {
                Log.d(TAG, "AudioRecord job cancelled cleanly.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in AudioRecord loop", e)
            } finally {
                teardownRecord(record)
            }
        }
        return true
    }

    fun stopRecording(): ByteArray {
        if (!isRecordingActive.getAndSet(false)) {
            return synchronized(recordedDataStream) { recordedDataStream.toByteArray() }
        }

        currentAudioRecord.get()?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping AudioRecord", e)
            }
        }

        recordingJob?.cancel()
        recordingJob = null

        return synchronized(recordedDataStream) {
            recordedDataStream.toByteArray()
        }
    }

    private fun teardownRecord(record: AudioRecord) {
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
            record.release()
            Log.d(TAG, "AudioRecord hardware released safely.")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        } finally {
            currentAudioRecord.set(null)
            isRecordingActive.set(false)
            onAmplitudeChanged?.invoke(0.0f)
        }
    }

    fun destroy() {
        stopRecording()
        audioExecutor.shutdown()
    }
}
