package com.itantra.voice.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Strictly typed data models for Sarvam AI cloud REST APIs:
 * - Saaras v3 Speech-to-Text (POST /speech-to-text)
 * - Mayura v1 / Sarvam Translate (POST /translate)
 * - Bulbul v3 Text-to-Speech (POST /text-to-speech)
 */

@Serializable
data class SpeechResponse(
    @SerialName("transcript") val transcript: String,
    @SerialName("language_code") val language_code: String? = null
) {
    val languageCode: String? get() = language_code
}

@Serializable
data class TranslationRequest(
    @SerialName("input") val input: String,
    @SerialName("source_language_code") val source_language_code: String,
    @SerialName("target_language_code") val target_language_code: String,
    @SerialName("model") val model: String = "mayura:v1",
    @SerialName("mode") val mode: String = "formal"
) {
    val sourceLanguageCode: String get() = source_language_code
    val targetLanguageCode: String get() = target_language_code
}

@Serializable
data class TranslationResponse(
    @SerialName("translated_text") val translated_text: String
) {
    val translatedText: String get() = translated_text
}

@Serializable
data class TtsRequest(
    @SerialName("inputs") val inputs: List<String>,
    @SerialName("target_language_code") val target_language_code: String,
    @SerialName("speaker") val speaker: String = "priya",
    @SerialName("model") val model: String = "bulbul:v3",
    @SerialName("speech_sample_rate") val speech_sample_rate: Int = 16000
) {
    val targetLanguageCode: String get() = target_language_code
    val speechSampleRate: Int get() = speech_sample_rate
}

@Serializable
data class TtsResponse(
    @SerialName("audios") val audios: List<String>
)

@Serializable
data class SarvamErrorResponse(
    @SerialName("error") val error: SarvamErrorDetail? = null,
    @SerialName("message") val message: String? = null
)

@Serializable
data class SarvamErrorDetail(
    @SerialName("message") val message: String? = null,
    @SerialName("code") val code: String? = null
)
