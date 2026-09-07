package com.itantra.voice.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Message types supported by the iTantra peer-to-peer transport protocol.
 */
@Serializable
enum class TransportMessageType {
    @SerialName("TRANSLATION")
    TRANSLATION,

    @SerialName("ALERT")
    ALERT,

    @SerialName("HANDSHAKE")
    HANDSHAKE,

    @SerialName("PING")
    PING
}

/**
 * Canonical transport packet exchanged over P2P Wi-Fi Direct (and future Bluetooth RFCOMM).
 * Carries full language metadata to guarantee the receiving peer synthesizes
 * speech in the correct target language without guessing.
 */
@Serializable
data class TransportMessage(
    @SerialName("messageId")
    val messageId: String = UUID.randomUUID().toString(),

    @SerialName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerialName("type")
    val type: TransportMessageType = TransportMessageType.TRANSLATION,

    @SerialName("sourceLanguage")
    val sourceLanguage: String,

    @SerialName("targetLanguage")
    val targetLanguage: String,

    @SerialName("text")
    val text: String,

    @SerialName("priority")
    val priority: Int = 0
) {
    fun toJson(): String = jsonInstance.encodeToString(this)

    companion object {
        val jsonInstance = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun fromJson(jsonString: String): Result<TransportMessage> = runCatching {
            jsonInstance.decodeFromString<TransportMessage>(jsonString)
        }

        fun createTestMessage(sourceLang: String = "hi-IN", targetLang: String = "en-IN"): TransportMessage =
            TransportMessage(
                type = TransportMessageType.TRANSLATION,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                text = "HELLO FROM ITANTRA"
            )
    }
}
