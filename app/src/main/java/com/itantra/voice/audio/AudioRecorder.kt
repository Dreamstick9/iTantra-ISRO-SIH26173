package com.itantra.voice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Abstraction layer for native Android AudioRecord to enable both real hardware
 * audio streaming and deterministic unit testing.
 */
interface NativeAudioRecord {
    val state: Int
    val recordingState: Int
    fun startRecording()
    fun stop()
    fun release()
    fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int
}

/**
 * Production implementation of NativeAudioRecord wrapping Android's android.media.AudioRecord.
 */
class DefaultNativeAudioRecord(
    audioSource: Int,
    sampleRate: Int,
    channelConfig: Int,
    audioFormat: Int,
    bufferSize: Int
) : NativeAudioRecord {

    @SuppressLint("MissingPermission")
    private val record: AudioRecord = AudioRecord(
        audioSource,
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize
    )

    override val state: Int get() = record.state
    override val recordingState: Int get() = record.recordingState
    override fun startRecording() = record.startRecording()
    override fun stop() = record.stop()
    override fun release() = record.release()
    override fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int =
        record.read(audioData, offsetInBytes, sizeInBytes)
}

/**
 * Native audio recording engine capturing 16 kHz Mono 16-bit linear PCM audio.
 * Executes non-blocking streaming on [Dispatchers.IO] and emits real-time normalized
 * audio amplitude for UI visualization.
 */
class AudioRecorder(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val recordProvider: ((audioSource: Int, sampleRate: Int, channelConfig: Int, audioFormat: Int, bufferSize: Int) -> NativeAudioRecord)? = null
) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2 // 16-bit PCM = 2 bytes per sample
        const val CHANNELS = 1
        const val BYTE_RATE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE // 32,000 bytes/sec
        const val MAX_RECORDING_SECONDS = 30
        const val MAX_RECORDING_BYTES = MAX_RECORDING_SECONDS * BYTE_RATE // 960,000 bytes (~0.96 MB)
        const val MIN_BUFFER_FLOOR = 4096
        const val READ_CHUNK_BYTES = 2048

        /** Upper bound on waiting for the capture coroutine to finish draining. */
        const val JOB_JOIN_TIMEOUT_MS = 1_000L

        /**
         * Computes scaled buffer size: 2x minBufferSize with 4096 bytes floor.
         */
        fun calculateBufferSize(): Int {
            return try {
                val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                if (min > 0) maxOf(min * 2, MIN_BUFFER_FLOOR) else MIN_BUFFER_FLOOR
            } catch (e: Throwable) {
                MIN_BUFFER_FLOOR
            }
        }
    }

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val isRecordingInternal = AtomicBoolean(false)
    val isRecording: Boolean get() = isRecordingInternal.get()

    private var activeRecord: NativeAudioRecord? = null
    private var recordingJob: Job? = null
    private val currentBuffer = ByteArrayOutputStream()
    private val bufferLock = Any()

    /**
     * Starts capturing 16 kHz Mono 16-bit PCM audio on a background coroutine.
     *
     * @param scope CoroutineScope to launch the streaming loop
     * @return StateFlow emitting normalized peak amplitude (0.0f to 1.0f)
     */
    fun startRecording(scope: CoroutineScope): StateFlow<Float> {
        synchronized(bufferLock) {
            if (isRecordingInternal.getAndSet(true)) {
                return amplitude
            }

            currentBuffer.reset()
            _amplitude.value = 0f

            val bufferSize = calculateBufferSize()
            val record: NativeAudioRecord = try {
                recordProvider?.invoke(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                ) ?: DefaultNativeAudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (e: Exception) {
                isRecordingInternal.set(false)
                throw IllegalStateException("Failed to instantiate AudioRecord: ${e.message}", e)
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                isRecordingInternal.set(false)
                try {
                    record.release()
                } catch (ignored: Exception) {}
                throw IllegalStateException("AudioRecord parameter configuration not supported by hardware (uninitialized).")
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                isRecordingInternal.set(false)
                try {
                    record.release()
                } catch (ignored: Exception) {}
                throw IllegalStateException("Failed to start AudioRecord: ${e.message}", e)
            }

            activeRecord = record

            recordingJob = scope.launch(ioDispatcher) {
                val chunkBuffer = ByteArray(READ_CHUNK_BYTES)
                try {
                    while (isRecordingInternal.get() && isActive) {
                        val bytesRead = record.read(chunkBuffer, 0, chunkBuffer.size)
                        if (bytesRead > 0) {
                            var maxSample = 0
                            synchronized(bufferLock) {
                                currentBuffer.write(chunkBuffer, 0, bytesRead)

                                for (i in 0 until bytesRead - 1 step 2) {
                                    val sample = (chunkBuffer[i].toInt() and 0xFF) or (chunkBuffer[i + 1].toInt() shl 8)
                                    val abs = kotlin.math.abs(sample.toShort().toInt())
                                    if (abs > maxSample) maxSample = abs
                                }
                            }

                            val normalized = (maxSample.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
                            _amplitude.value = normalized

                            // Auto-stop if exceeding maximum threshold (30 seconds)
                            val currentSize = synchronized(bufferLock) { currentBuffer.size() }
                            if (currentSize >= MAX_RECORDING_BYTES) {
                                isRecordingInternal.set(false)
                                break
                            }
                        } else if (bytesRead < 0) {
                            // AudioRecord error condition
                            isRecordingInternal.set(false)
                            break
                        } else {
                            // 0 bytes read, yield to prevent tight loop
                            kotlinx.coroutines.delay(10)
                        }
                    }
                } finally {
                    // Drain here rather than from stopRecording(): this coroutine is the
                    // sole reader of `record`, so the tail of the utterance is collected
                    // without a second thread racing it for the same hardware buffer.
                    drainInto(record)
                    safeReleaseRecord(record)
                }
            }
        }
        return amplitude
    }

    /**
     * Stops audio recording and returns the raw captured PCM bytes.
     *
     * Suspends until the capture coroutine has drained the hardware buffer, so the
     * returned array always includes the tail of the utterance. Draining is delegated
     * to that coroutine because it is the only thread permitted to touch the
     * AudioRecord; reading from two threads split utterances and produced the
     * intermittent "No speech detected" failures.
     */
    suspend fun stopRecording(): ByteArray {
        val recordToStop: NativeAudioRecord?
        val jobToStop: Job?

        synchronized(bufferLock) {
            isRecordingInternal.set(false)
            recordToStop = activeRecord
            jobToStop = recordingJob
            recordingJob = null
        }

        // Ask AudioRecord to stop capturing new frames. Already-buffered audio survives,
        // and the pending read() in the capture loop returns so the coroutine can finish.
        try {
            if (recordToStop?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recordToStop.stop()
            }
        } catch (ignored: Exception) {}

        // Let the capture coroutine exit its loop and run its drain/release in finally.
        // It is not cancelled: cancellation would skip the drain and truncate the tail.
        if (jobToStop != null) {
            withTimeoutOrNull(JOB_JOIN_TIMEOUT_MS) { jobToStop.join() }
            if (jobToStop.isActive) {
                // Hung in a blocking read; cancel and release so the mic is never leaked.
                jobToStop.cancel()
                safeReleaseRecord(recordToStop)
            }
        } else {
            safeReleaseRecord(recordToStop)
        }

        synchronized(bufferLock) {
            _amplitude.value = 0f
            val capturedBytes = currentBuffer.toByteArray()
            currentBuffer.reset()
            return capturedBytes
        }
    }

    /**
     * Reads whatever the hardware still holds into the capture buffer.
     * Called only from the capture coroutine, which is the sole reader.
     */
    private fun drainInto(record: NativeAudioRecord) {
        val drainBuffer = ByteArray(READ_CHUNK_BYTES)
        try {
            while (true) {
                // Honour the capture cap here too. When the 30 s limit is what ended the
                // loop, the hardware is still in RECORDING state and keeps returning
                // data, so an unbounded drain would blow straight past the cap.
                val alreadyCaptured = synchronized(bufferLock) { currentBuffer.size() }
                if (alreadyCaptured >= MAX_RECORDING_BYTES) break

                val drained = record.read(drainBuffer, 0, drainBuffer.size)
                if (drained <= 0) break

                val room = MAX_RECORDING_BYTES - alreadyCaptured
                synchronized(bufferLock) {
                    currentBuffer.write(drainBuffer, 0, minOf(drained, room))
                }
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Stops capture and discards the audio. Safe to call from a non-suspending context
     * such as ViewModel teardown.
     */
    fun release() {
        val recordToStop: NativeAudioRecord?
        val jobToStop: Job?
        synchronized(bufferLock) {
            isRecordingInternal.set(false)
            recordToStop = activeRecord
            jobToStop = recordingJob
            recordingJob = null
            currentBuffer.reset()
        }
        jobToStop?.cancel()
        safeReleaseRecord(recordToStop)
    }

    private fun safeReleaseRecord(record: NativeAudioRecord?) {
        try {
            record?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (ignored: Exception) {
        } finally {
            synchronized(bufferLock) {
                if (activeRecord === record) {
                    activeRecord = null
                    isRecordingInternal.set(false)
                    _amplitude.value = 0f
                }
            }
        }
    }
}
