package com.example.itantra.tts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Mock TTS Engine for unit testing and offline simulation without native Sherpa-ONNX JNI binaries.
 * Synthesizes pure harmonic acoustic sine waves with deterministic latency and telemetry.
 */
class MockTtsEngine(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    var shouldFail: Boolean = false,
    var simulatedLatencyMs: Long = 150L
) : TtsEngine {

    private val _isModelLoaded = MutableStateFlow(true)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _playbackState = MutableStateFlow(TtsPlaybackState.IDLE)
    override val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    override val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _lastRtf = MutableStateFlow(0.0)
    override val lastRtf: StateFlow<Double> = _lastRtf.asStateFlow()

    private val _lastDurationMs = MutableStateFlow(0L)
    override val lastDurationMs: StateFlow<Long> = _lastDurationMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var isStopped = false

    override suspend fun synthesize(text: String): AudioData = withContext(ioDispatcher) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext AudioData.EMPTY
        }

        if (shouldFail) {
            _playbackState.value = TtsPlaybackState.ERROR
            _errorMessage.value = "Mock TTS synthesis forced error"
            return@withContext AudioData.EMPTY
        }

        _playbackState.value = TtsPlaybackState.SYNTHESIZING
        val startTime = System.currentTimeMillis()

        if (simulatedLatencyMs > 0) {
            delay(simulatedLatencyMs)
        }

        val sampleRate = 16000
        val wordCount = trimmed.split("\\s+".toRegex()).size
        val durationMs = (wordCount * 300L).coerceIn(600L, 5000L)
        val totalSamples = ((durationMs * sampleRate) / 1000L).toInt()

        val samples = FloatArray(totalSamples)
        val frequency = 440.0 // A4 note
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            samples[i] = (sin(2.0 * PI * frequency * t) * 0.3).toFloat()
        }

        val pcm = AudioData.floatArrayToPcm16Le(samples)
        val latency = System.currentTimeMillis() - startTime
        val rtf = if (durationMs > 0) latency.toDouble() / durationMs.toDouble() else 0.0

        _lastLatencyMs.value = latency
        _lastRtf.value = rtf
        _lastDurationMs.value = durationMs
        _playbackState.value = TtsPlaybackState.IDLE
        _errorMessage.value = null

        AudioData(
            samples = samples,
            sampleRate = sampleRate,
            rawPcm = pcm,
            durationMs = durationMs
        )
    }

    override suspend fun speak(text: String) = withContext(ioDispatcher) {
        isStopped = false
        val audio = synthesize(text)
        if (audio.isEmpty) return@withContext

        _playbackState.value = TtsPlaybackState.PLAYING
        // Simulate playback duration
        val steps = (audio.durationMs / 50L).toInt().coerceAtLeast(1)
        for (i in 0 until steps) {
            if (isStopped) {
                _playbackState.value = TtsPlaybackState.STOPPED
                return@withContext
            }
            delay(50L)
        }
        if (!isStopped) {
            _playbackState.value = TtsPlaybackState.IDLE
        }
    }

    override suspend fun stop() {
        isStopped = true
        _playbackState.value = TtsPlaybackState.STOPPED
    }

    override fun release() {
        _isModelLoaded.value = false
        _playbackState.value = TtsPlaybackState.IDLE
    }
}
