package com.example.itantra.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.itantra.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Concrete Android implementation of AudioEngine using android.media.AudioRecord.
 * Captures 16-bit Mono PCM audio at 16,000 Hz on Dispatchers.IO.
 */
class AndroidAudioEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AudioEngine {

    private val TAG = "AndroidAudioEngine"

    private val _recordingState = MutableStateFlow(AudioRecordingState.IDLE)
    override val recordingState: StateFlow<AudioRecordingState> = _recordingState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val isCapturing = AtomicBoolean(false)
    private val audioOutputStream = ByteArrayOutputStream()

    // 16kHz Mono 16-bit PCM standard for offline speech
    private val sampleRate = AudioConfig.SAMPLE_RATE_HZ
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @SuppressLint("MissingPermission")
    override suspend fun startRecording() = withContext(ioDispatcher) {
        if (_recordingState.value == AudioRecordingState.RECORDING) {
            Logger.w(TAG, "Recording is already active, ignoring startRecording.")
            return@withContext
        }

        // 1. Runtime permission verification
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.e(TAG, "RECORD_AUDIO permission not granted.")
            _recordingState.value = AudioRecordingState.ERROR
            throw SecurityException("Microphone permission (RECORD_AUDIO) is not granted.")
        }

        // 2. AudioRecord buffer calculation
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            Logger.e(TAG, "Failed to get minBufferSize for 16kHz mono 16-bit: $minBufferSize")
            _recordingState.value = AudioRecordingState.ERROR
            throw IllegalStateException("Device does not support 16kHz Mono 16-bit PCM.")
        }

        val bufferSize = maxOf(minBufferSize * 2, 4096)

        // 3. AudioRecord instantiation & validation with fallback (VOICE_RECOGNITION -> MIC)
        val record = createAndStartAudioRecord(bufferSize)
        if (record == null) {
            Logger.e(TAG, "AudioRecord failed to initialize with all audio sources.")
            safeReleaseRecord()
            _recordingState.value = AudioRecordingState.ERROR
            throw IllegalStateException("AudioRecord failed to initialize with available audio sources.")
        }

        audioRecord = record

        synchronized(audioOutputStream) {
            audioOutputStream.reset()
        }
        smoothedMeterLevel = 0f
        _audioLevel.value = 0f
        isCapturing.set(true)
        _recordingState.value = AudioRecordingState.RECORDING
        Logger.i(TAG, "AudioRecord started: 16000Hz, Mono, 16-bit PCM (bufferSize: $bufferSize)")

        // 4. Background capture loop on Dispatchers.IO
        captureJob = CoroutineScope(ioDispatcher).launch {
            val chunkBuffer = ByteArray(AudioConfig.BYTES_PER_FRAME.coerceAtLeast(1024))
            try {
                while (isCapturing.get() && isActive) {
                    val bytesRead = record.read(chunkBuffer, 0, chunkBuffer.size)
                    if (bytesRead > 0) {
                        synchronized(audioOutputStream) {
                            audioOutputStream.write(chunkBuffer, 0, bytesRead)
                        }
                        computeAndEmitAudioLevel(chunkBuffer, bytesRead)
                    } else if (bytesRead < 0) {
                        Logger.w(TAG, "AudioRecord read returned error: $bytesRead")
                        break
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Exception during audio capture loop: ${e.message}", e)
            } finally {
                _audioLevel.value = 0f
            }
        }
    }

    override suspend fun stopRecording(): ByteArray = withContext(ioDispatcher) {
        if (!isCapturing.getAndSet(false)) {
            Logger.w(TAG, "stopRecording called while not actively capturing.")
            _recordingState.value = AudioRecordingState.IDLE
            return@withContext ByteArray(0)
        }

        _recordingState.value = AudioRecordingState.PROCESSING
        _audioLevel.value = 0f

        // Immediately stop AudioRecord to unblock any pending blocking native read
        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Benign exception stopping AudioRecord: ${e.message}")
        }

        // Wait for background capture job to complete current read
        captureJob?.join()
        captureJob = null

        val pcmBytes = synchronized(audioOutputStream) {
            val bytes = audioOutputStream.toByteArray()
            audioOutputStream.reset()
            bytes
        }

        safeReleaseRecord()

        _recordingState.value = AudioRecordingState.IDLE
        val durationSeconds = pcmBytes.size / AudioConfig.BYTES_PER_SECOND.toFloat()
        Logger.i(TAG, "AudioRecord stopped: Captured ${pcmBytes.size} PCM bytes (%.2f seconds)".format(durationSeconds))
        return@withContext pcmBytes
    }

    @SuppressLint("MissingPermission")
    override fun startAudioStream(): Flow<ByteArray> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            close(SecurityException("Microphone permission (RECORD_AUDIO) is not granted."))
            return@callbackFlow
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize * 2, 4096)
        val record = createAndStartAudioRecord(bufferSize)
        if (record == null) {
            close(IllegalStateException("Failed to initialize and start AudioRecord."))
            return@callbackFlow
        }

        audioRecord = record
        isCapturing.set(true)
        _recordingState.value = AudioRecordingState.RECORDING

        // 512 samples @ 16kHz 16-bit mono = 1024 bytes per frame (32ms chunk)
        val frameSizeBytes = 1024
        val chunkBuffer = ByteArray(frameSizeBytes)

        val streamJob = launch(ioDispatcher) {
            try {
                while (isCapturing.get() && isActive) {
                    var bytesReadTotal = 0
                    while (bytesReadTotal < frameSizeBytes && isCapturing.get() && isActive) {
                        val read = record.read(chunkBuffer, bytesReadTotal, frameSizeBytes - bytesReadTotal)
                        if (read > 0) {
                            bytesReadTotal += read
                        } else if (read < 0) {
                            Logger.w(TAG, "AudioRecord read returned error: $read")
                            break
                        }
                    }

                    if (bytesReadTotal == frameSizeBytes) {
                        val chunkCopy = chunkBuffer.copyOf()
                        computeAndEmitAudioLevel(chunkCopy, chunkCopy.size)
                        trySend(chunkCopy)
                    } else if (bytesReadTotal > 0) {
                        val partialCopy = chunkBuffer.copyOf(bytesReadTotal)
                        computeAndEmitAudioLevel(partialCopy, partialCopy.size)
                        trySend(partialCopy)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Exception during audio streaming: ${e.message}", e)
            } finally {
                _audioLevel.value = 0f
            }
        }

        awaitClose {
            isCapturing.set(false)
            streamJob.cancel()
            safeReleaseRecord()
            _recordingState.value = AudioRecordingState.IDLE
            _audioLevel.value = 0f
        }
    }

    override fun stopAudioStream() {
        isCapturing.set(false)
        captureJob?.cancel()
        captureJob = null
        safeReleaseRecord()
        _recordingState.value = AudioRecordingState.IDLE
        _audioLevel.value = 0f
    }

    @SuppressLint("MissingPermission")
    private fun createAndStartAudioRecord(bufferSize: Int): AudioRecord? {
        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        var record: AudioRecord? = null
        for (source in audioSources) {
            try {
                val candidate = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    Logger.d(TAG, "AudioRecord initialized successfully with audio source: $source")
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                Logger.w(TAG, "AudioRecord failed with source $source: ${e.message}")
            }
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            return null
        }

        try {
            record.startRecording()
        } catch (e: Exception) {
            Logger.e(TAG, "AudioRecord.startRecording failed: ${e.message}", e)
            safeReleaseRecord()
            return null
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Logger.e(TAG, "AudioRecord failed to enter RECORDSTATE_RECORDING: ${record.recordingState}")
            safeReleaseRecord()
            return null
        }

        return record
    }

    override fun release() {
        isCapturing.set(false)
        captureJob?.cancel()
        captureJob = null
        safeReleaseRecord()
        _recordingState.value = AudioRecordingState.IDLE
        _audioLevel.value = 0f
        Logger.i(TAG, "AndroidAudioEngine released.")
    }

    private fun safeReleaseRecord() {
        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Error while safely releasing AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    private var smoothedMeterLevel = 0f

    private fun computeAndEmitAudioLevel(buffer: ByteArray, bytesRead: Int) {
        val sampleCount = bytesRead / 2
        if (sampleCount <= 0) return

        var sumSquares = 0.0
        for (i in 0 until bytesRead - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sample16 = sample.toShort().toDouble()
            sumSquares += sample16 * sample16
        }

        val rms = kotlin.math.sqrt(sumSquares / sampleCount)
        if (rms <= 1.0) {
            _audioLevel.value = 0f
            return
        }

        // Logarithmic dBFS formula with -50 dBFS noise floor threshold
        val dbfs = 20.0 * kotlin.math.log10(rms / 32767.0)
        val normalized = ((dbfs + 50.0) / 50.0).coerceIn(0.0, 1.0).toFloat()
        // Exponential Moving Average (EMA) smoothing for responsive yet jitter-free meter
        smoothedMeterLevel = 0.35f * normalized + 0.65f * smoothedMeterLevel
        _audioLevel.value = smoothedMeterLevel.coerceIn(0f, 1f)
    }
}
