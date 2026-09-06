package com.example.itantra.speech

import com.example.itantra.data.Language
import kotlinx.coroutines.flow.StateFlow

data class TranscriptionResult(
    val text: String,
    val confidence: Float = 0.95f,
    val durationMs: Long = 180L,
    val werEstimate: Float = 0.05f
)

data class SynthesisResult(
    val audioPcm: ByteArray,
    val sampleRate: Int = 16000,
    val audioDurationMs: Long = 1900L,
    val computeTimeMs: Long = 320L,
    val rtf: Double = 0.17
)

interface SpeechEngine {
    val isModelLoaded: StateFlow<Boolean>
    val loadErrorMessage: StateFlow<String?>
    val lastLatencyMs: StateFlow<Long>

    fun initialize(): Result<Unit> = Result.success(Unit)

    suspend fun transcribe(
        audio: ByteArray
    ): String

    suspend fun transcribe(pcmAudio: ByteArray, language: Language): Result<TranscriptionResult> {
        val text = transcribe(pcmAudio)
        return Result.success(TranscriptionResult(text = text))
    }

    suspend fun synthesize(text: String, language: Language, isEmergency: Boolean = false): Result<SynthesisResult> {
        return Result.failure(UnsupportedOperationException("Synthesis not implemented in this engine"))
    }

    fun normalize(text: String, language: Language): String = text.trim()

    fun release()
}
