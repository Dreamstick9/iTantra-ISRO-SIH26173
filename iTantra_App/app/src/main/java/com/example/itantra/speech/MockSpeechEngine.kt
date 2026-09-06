package com.example.itantra.speech

import com.example.itantra.data.Language
import com.example.itantra.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockSpeechEngine : SpeechEngine {
    private val TAG = "MockSpeech"

    private val _isModelLoaded = MutableStateFlow(true)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _loadErrorMessage = MutableStateFlow<String?>(null)
    override val loadErrorMessage: StateFlow<String?> = _loadErrorMessage.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(180L)
    override val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    var simulatedTranscriptionText: String? = null
    var simulatedSttLatencyMs: Long = 180L
    var simulatedTtsComputeTimeMs: Long = 320L
    var simulatedTtsAudioDurationMs: Long = 1900L
    var transcribeCallCount: Int = 0
        private set
    var lastTranscribedAudio: ByteArray? = null
        private set
    var synthesizeCallCount: Int = 0
        private set

    override fun initialize(): Result<Unit> {
        _isModelLoaded.value = true
        _loadErrorMessage.value = null
        Logger.i(TAG, "MockSpeechEngine initialized (10 Indian languages mock).")
        return Result.success(Unit)
    }

    override suspend fun transcribe(audio: ByteArray): String {
        return transcribe(audio, Language.ENGLISH).getOrThrow().text
    }

    override suspend fun transcribe(pcmAudio: ByteArray, language: Language): Result<TranscriptionResult> {
        transcribeCallCount++
        lastTranscribedAudio = pcmAudio
        val text = simulatedTranscriptionText ?: com.example.itantra.language.ModelRegistry.getSampleTranscript(language)

        _lastLatencyMs.value = simulatedSttLatencyMs
        Logger.i(TAG, "STT Transcribed [${language.isoCode}]: '$text' in ${simulatedSttLatencyMs}ms.")
        return Result.success(
            TranscriptionResult(
                text = text,
                confidence = 0.96f,
                durationMs = simulatedSttLatencyMs,
                werEstimate = 0.04f
            )
        )
    }

    override suspend fun synthesize(text: String, language: Language, isEmergency: Boolean): Result<SynthesisResult> {
        synthesizeCallCount++
        val rtf = simulatedTtsComputeTimeMs.toDouble() / simulatedTtsAudioDurationMs.toDouble()
        val byteCount = ((simulatedTtsAudioDurationMs * 16000L * 2L) / 1000L).toInt()
        val dummyPcm = ByteArray(byteCount) { (it % 120).toByte() }

        Logger.i(TAG, "TTS Synthesized [${language.isoCode}]: length=${simulatedTtsAudioDurationMs}ms, RTF=%.3f".format(rtf))

        return Result.success(
            SynthesisResult(
                audioPcm = dummyPcm,
                sampleRate = 16000,
                audioDurationMs = simulatedTtsAudioDurationMs,
                computeTimeMs = simulatedTtsComputeTimeMs,
                rtf = rtf
            )
        )
    }

    override fun normalize(text: String, language: Language): String {
        return com.example.itantra.language.ModelRegistry.normalize(text, language)
    }

    override fun release() {
        Logger.i(TAG, "MockSpeechEngine released.")
    }
}
