package com.example.itantra.audio

import com.example.itantra.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class MockAudioEngine : AudioEngine {
    private val TAG = "MockAudio"

    private val _recordingState = MutableStateFlow(AudioRecordingState.IDLE)
    override val recordingState: StateFlow<AudioRecordingState> = _recordingState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isRecording = AtomicBoolean(false)
    val isRecording: Boolean get() = _isRecording.get()

    private val _isPlaying = AtomicBoolean(false)
    val isPlaying: Boolean get() = _isPlaying.get()

    var lastPlayedAudio: ByteArray? = null
        private set
    var lastPlayedWasEmergency: Boolean = false
        private set
    var recordingStartTimestamp: Long = 0L
        private set

    fun initialize(): Result<Unit> {
        Logger.i(TAG, "Initialized MockAudioEngine (16kHz Mono 16-bit PCM).")
        return Result.success(Unit)
    }

    override suspend fun startRecording() {
        if (_isRecording.compareAndSet(false, true)) {
            recordingStartTimestamp = System.currentTimeMillis()
            _recordingState.value = AudioRecordingState.RECORDING
            _audioLevel.value = 0.72f
            Logger.d(TAG, "Audio capture started.")
        }
    }

    override suspend fun stopRecording(): ByteArray {
        return if (_isRecording.compareAndSet(true, false)) {
            _recordingState.value = AudioRecordingState.PROCESSING
            _audioLevel.value = 0f
            val durationMs = (System.currentTimeMillis() - recordingStartTimestamp).coerceAtLeast(100L)
            val sampleCount = ((durationMs * AudioConfig.SAMPLE_RATE_HZ) / 1000L).toInt()
            val pcmBytes = ByteArray(sampleCount * AudioConfig.BYTES_PER_SAMPLE) { (it % 127).toByte() }
            Logger.d(TAG, "Audio capture stopped. Captured ${pcmBytes.size} bytes ($durationMs ms).")
            _recordingState.value = AudioRecordingState.IDLE
            pcmBytes
        } else {
            _recordingState.value = AudioRecordingState.IDLE
            ByteArray(0)
        }
    }

    fun playAudio(pcmData: ByteArray, sampleRate: Int = AudioConfig.SAMPLE_RATE_HZ, isEmergency: Boolean = false): Result<Unit> {
        lastPlayedAudio = pcmData
        lastPlayedWasEmergency = isEmergency
        _isPlaying.set(true)
        if (isEmergency) {
            Logger.w(TAG, "EMERGENCY AUDIO PLAYBACK TRIGGERED at max volume (${pcmData.size} bytes).")
        } else {
            Logger.d(TAG, "Standard audio playback started (${pcmData.size} bytes).")
        }
        return Result.success(Unit)
    }

    fun stopPlayback() {
        if (_isPlaying.compareAndSet(true, false)) {
            Logger.d(TAG, "Audio playback stopped.")
        }
    }

    override fun release() {
        _isRecording.set(false)
        stopPlayback()
        _recordingState.value = AudioRecordingState.IDLE
        _audioLevel.value = 0f
        Logger.i(TAG, "MockAudioEngine released.")
    }

    fun setSimulatedAudioLevel(level: Float) {
        _audioLevel.value = level.coerceIn(0f, 1f)
    }

    private val _streamFlow = kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override fun startAudioStream(): kotlinx.coroutines.flow.Flow<ByteArray> = _streamFlow
    override fun stopAudioStream() {}
    fun emitStreamChunk(chunk: ByteArray) {
        _streamFlow.tryEmit(chunk)
    }
}
