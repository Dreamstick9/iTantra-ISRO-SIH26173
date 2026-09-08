package com.itantra.voice.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the ElevenLabs REST API.
 *
 * Only the fields iTantra uses are declared; the client parses with
 * `ignoreUnknownKeys` so new fields upstream cannot break transcription.
 */

/** Response of `POST /v1/speech-to-text` (Scribe). */
@Serializable
data class ScribeResponse(
    @SerialName("text") val text: String = "",
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("language_probability") val languageProbability: Double? = null
)

/** Request body of `POST /v1/text-to-speech/{voice_id}`. */
@Serializable
data class ElevenTtsRequest(
    @SerialName("text") val text: String,
    @SerialName("model_id") val modelId: String,
    /** ISO 639-1 code used for text normalisation, e.g. "hi". */
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("voice_settings") val voiceSettings: ElevenVoiceSettings? = null
)

@Serializable
data class ElevenVoiceSettings(
    @SerialName("stability") val stability: Double = 0.5,
    @SerialName("similarity_boost") val similarityBoost: Double = 0.75,
    @SerialName("speed") val speed: Double = 1.0
)

/** Response of `GET /v2/voices`. */
@Serializable
data class ElevenVoicesResponse(
    @SerialName("voices") val voices: List<ElevenVoice> = emptyList()
)

@Serializable
data class ElevenVoice(
    @SerialName("voice_id") val voiceId: String,
    @SerialName("name") val name: String = ""
)

@Serializable
data class ElevenErrorResponse(
    @SerialName("detail") val detail: ElevenErrorDetail? = null
)

@Serializable
data class ElevenErrorDetail(
    @SerialName("status") val status: String? = null,
    @SerialName("message") val message: String? = null
)
