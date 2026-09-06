package com.example.itantra.speech

import com.example.itantra.data.Language

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
    fun initialize(): Result<Unit>
    suspend fun transcribe(pcmAudio: ByteArray, language: Language): Result<TranscriptionResult>
    suspend fun synthesize(text: String, language: Language, isEmergency: Boolean = false): Result<SynthesisResult>
    fun normalize(text: String, language: Language): String
    fun release()
}
