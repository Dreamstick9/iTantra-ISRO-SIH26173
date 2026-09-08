package com.itantra.voice.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.itantra.voice.location.GeoPoint
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
    val priority: Int = 0,

    /**
     * Sender's latitude as a fixed-point integer (degrees x 1e5), or null when no fix
     * was available. Short key and integer encoding because SIH26173 targets links
     * measured in tens of bytes: `"la":1907598` is 13 characters where
     * `"latitude":19.0759837` would be 21, and 1e5 still resolves ~1.1 m.
     */
    @SerialName("la")
    val latitudeE5: Int? = null,

    /** Sender's longitude as a fixed-point integer (degrees x 1e5). See [latitudeE5]. */
    @SerialName("lo")
    val longitudeE5: Int? = null,

    /** Horizontal accuracy of the sender's fix in whole metres, when reported. */
    @SerialName("ac")
    val accuracyMetres: Int? = null
) {
    fun toJson(): String = jsonInstance.encodeToString(this)

    /** The sender's position, or null when the message carried no fix. */
    fun senderLocation(): GeoPoint? = GeoPoint.fromWire(
        scaledLat = latitudeE5,
        scaledLon = longitudeE5,
        accuracyMetres = accuracyMetres?.toFloat(),
        timestampMs = timestamp
    )

    /** True when this message carries a usable position. */
    val hasLocation: Boolean get() = senderLocation() != null

    companion object {
        val jsonInstance = Json {
            ignoreUnknownKeys = true
            // Defaults are omitted rather than serialised: with encodeDefaults = true a
            // message without a fix still carried `"la":null,"lo":null,"ac":null`, 27
            // wasted bytes on a link the brief budgets in tens of bytes.
            encodeDefaults = false
            explicitNulls = false
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

        /** Attaches [point] to [message], rounding to the fixed-point wire precision. */
        fun withLocation(message: TransportMessage, point: GeoPoint?): TransportMessage {
            if (point == null) return message
            return message.copy(
                latitudeE5 = GeoPoint.encode(point.latitude),
                longitudeE5 = GeoPoint.encode(point.longitude),
                accuracyMetres = point.accuracyMetres?.let { kotlin.math.round(it).toInt() }
            )
        }
    }
}
