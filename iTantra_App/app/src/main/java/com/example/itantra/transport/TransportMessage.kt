package com.example.itantra.transport

import com.example.itantra.compression.Unishox2
import com.example.itantra.data.Language
import com.example.itantra.data.LatencyMetrics
import com.example.itantra.data.MessageType
import com.example.itantra.data.ReceivedMessage
import com.example.itantra.data.VerticalSliceTimestamps
import java.util.UUID

enum class TransportMessageType(val typeCode: Byte) {
    TEXT(0x01),
    ALERT(0x02),
    ACK(0x03),
    PING(0x04),
    PONG(0x05);

    companion object {
        fun fromCode(code: Byte): TransportMessageType =
            entries.firstOrNull { it.typeCode == code } ?: TEXT
    }
}

/**
 * MessagePayload encapsulates the message text along with evaluation latency and
 * timestamp metadata for the iTantra vertical slice.
 *
 * Supports serialization as a lightweight JSON envelope or plain text, guaranteeing
 * 100% backwards compatibility with legacy plain text payloads.
 */
data class MessagePayload(
    val text: String,
    val sttLatencyMs: Long = 0L,
    val audioDurationMs: Long = 0L,
    val t0: Long = 0L,
    val t1: Long = 0L,
    val t2: Long = 0L
) {
    val hasMetadata: Boolean
        get() = t0 != 0L || t1 != 0L || t2 != 0L || sttLatencyMs != 0L || audioDurationMs != 0L

    /**
     * Serializes this payload to a JSON envelope.
     */
    fun toJson(): String = buildString {
        append("{")
        append("\"text\":\"").append(escapeJsonString(text)).append("\",")
        append("\"sttLatencyMs\":").append(sttLatencyMs).append(",")
        append("\"audioDurationMs\":").append(audioDurationMs).append(",")
        append("\"t0\":").append(t0).append(",")
        append("\"t1\":").append(t1).append(",")
        append("\"t2\":").append(t2)
        append("}")
    }

    /**
     * Serializes to bytes. If metadata is present or [forceEnvelope] is true, encodes
     * as JSON envelope. Otherwise writes raw UTF-8 text for wire-size minimalism.
     */
    fun toByteArray(forceEnvelope: Boolean = false): ByteArray {
        return if (forceEnvelope || hasMetadata) {
            toJson().toByteArray(Charsets.UTF_8)
        } else {
            text.toByteArray(Charsets.UTF_8)
        }
    }

    fun toEnvelopeByteArray(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    fun toCompressedByteArray(forceEnvelope: Boolean = false): ByteArray {
        val raw = toByteArray(forceEnvelope)
        return try {
            val compressed = Unishox2.compress(raw)
            if (compressed.isNotEmpty() && compressed.size < raw.size) {
                ByteArray(compressed.size + 1).apply {
                    this[0] = 0x55.toByte()
                    System.arraycopy(compressed, 0, this, 1, compressed.size)
                }
            } else {
                raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    companion object {
        fun create(
            text: String,
            sttLatencyMs: Long = 0L,
            audioDurationMs: Long = 0L,
            t0: Long = 0L,
            t1: Long = 0L,
            t2: Long = 0L
        ): MessagePayload = MessagePayload(
            text = text,
            sttLatencyMs = sttLatencyMs,
            audioDurationMs = audioDurationMs,
            t0 = t0,
            t1 = t1,
            t2 = t2
        )

        fun parse(payloadBytes: ByteArray): MessagePayload {
            if (payloadBytes.isEmpty()) return MessagePayload(text = "")
            val decompressedBytes = if (payloadBytes.isNotEmpty() && payloadBytes[0] == 0x55.toByte()) {
                try {
                    Unishox2.decompress(payloadBytes.copyOfRange(1, payloadBytes.size))
                } catch (_: Exception) {
                    payloadBytes
                }
            } else {
                payloadBytes
            }
            val raw = String(decompressedBytes, Charsets.UTF_8)
            return parse(raw)
        }

        fun parse(raw: String): MessagePayload {
            if (raw.isBlank()) return MessagePayload(text = raw)
            return parseJsonEnvelope(raw) ?: MessagePayload(text = raw)
        }

        private fun escapeJsonString(str: String): String {
            val sb = StringBuilder()
            for (ch in str) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            val hex = ch.code.toString(16).padStart(4, '0')
                            sb.append("\\u").append(hex)
                        } else {
                            sb.append(ch)
                        }
                    }
                }
            }
            return sb.toString()
        }

        private fun skipWhitespace(json: String, startIndex: Int): Int {
            var idx = startIndex
            while (idx < json.length && json[idx].isWhitespace()) {
                idx++
            }
            return idx
        }

        private fun parseString(json: String, startIndex: Int): Pair<String, Int> {
            var idx = startIndex
            if (idx >= json.length || json[idx] != '"') {
                throw IllegalArgumentException("Expected '\"' at $idx")
            }
            idx++ // skip opening quote
            val sb = StringBuilder()
            while (idx < json.length) {
                val ch = json[idx++]
                if (ch == '"') {
                    return Pair(sb.toString(), idx)
                }
                if (ch == '\\') {
                    if (idx >= json.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = json[idx++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (idx + 4 > json.length) throw IllegalArgumentException("Incomplete unicode escape")
                            val hex = json.substring(idx, idx + 4)
                            sb.append(hex.toInt(16).toChar())
                            idx += 4
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(json: String, startIndex: Int): Pair<Long, Int> {
            var idx = startIndex
            val start = idx
            while (idx < json.length && (json[idx].isDigit() || json[idx] == '-' || json[idx] == '+')) {
                idx++
            }
            val numStr = json.substring(start, idx)
            val value = numStr.toLongOrNull() ?: 0L
            return Pair(value, idx)
        }

        fun parseJsonEnvelope(raw: String): MessagePayload? {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

            return try {
                var idx = 1 // skip '{'
                var hasTextKey = false
                var textVal = ""
                var sttLatencyVal = 0L
                var audioDurationVal = 0L
                var t0Val = 0L
                var t1Val = 0L
                var t2Val = 0L

                idx = skipWhitespace(trimmed, idx)
                while (idx < trimmed.length) {
                    if (trimmed[idx] == '}') {
                        idx++
                        break
                    }
                    if (trimmed[idx] == ',') {
                        idx++
                        idx = skipWhitespace(trimmed, idx)
                    }
                    if (idx >= trimmed.length || trimmed[idx] == '}') break

                    val (key, afterKeyIdx) = parseString(trimmed, idx)
                    idx = skipWhitespace(trimmed, afterKeyIdx)
                    if (idx >= trimmed.length || trimmed[idx] != ':') return null
                    idx++ // skip ':'
                    idx = skipWhitespace(trimmed, idx)
                    if (idx >= trimmed.length) return null

                    when (key) {
                        "text" -> {
                            if (trimmed[idx] == '"') {
                                val (parsedText, afterTextIdx) = parseString(trimmed, idx)
                                textVal = parsedText
                                hasTextKey = true
                                idx = afterTextIdx
                            } else {
                                return null
                            }
                        }
                        "sttLatencyMs" -> {
                            val (parsedNum, afterNumIdx) = parseNumber(trimmed, idx)
                            sttLatencyVal = parsedNum
                            idx = afterNumIdx
                        }
                        "audioDurationMs" -> {
                            val (parsedNum, afterNumIdx) = parseNumber(trimmed, idx)
                            audioDurationVal = parsedNum
                            idx = afterNumIdx
                        }
                        "t0" -> {
                            val (parsedNum, afterNumIdx) = parseNumber(trimmed, idx)
                            t0Val = parsedNum
                            idx = afterNumIdx
                        }
                        "t1" -> {
                            val (parsedNum, afterNumIdx) = parseNumber(trimmed, idx)
                            t1Val = parsedNum
                            idx = afterNumIdx
                        }
                        "t2" -> {
                            val (parsedNum, afterNumIdx) = parseNumber(trimmed, idx)
                            t2Val = parsedNum
                            idx = afterNumIdx
                        }
                        else -> {
                            // Skip unknown value
                            if (trimmed[idx] == '"') {
                                val (_, afterStrIdx) = parseString(trimmed, idx)
                                idx = afterStrIdx
                            } else if (trimmed[idx] == '{' || trimmed[idx] == '[') {
                                var depth = 1
                                val open = trimmed[idx++]
                                val close = if (open == '{') '}' else ']'
                                while (idx < trimmed.length && depth > 0) {
                                    val c = trimmed[idx++]
                                    if (c == open) depth++
                                    else if (c == close) depth--
                                    else if (c == '"') {
                                        val (_, afterStrIdx) = parseString(trimmed, idx - 1)
                                        idx = afterStrIdx
                                    }
                                }
                            } else {
                                while (idx < trimmed.length && trimmed[idx] != ',' && trimmed[idx] != '}') {
                                    idx++
                                }
                            }
                        }
                    }
                    idx = skipWhitespace(trimmed, idx)
                }

                if (!hasTextKey) return null

                MessagePayload(
                    text = textVal,
                    sttLatencyMs = sttLatencyVal,
                    audioDurationMs = audioDurationVal,
                    t0 = t0Val,
                    t1 = t1Val,
                    t2 = t2Val
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class TransportMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: TransportMessageType = TransportMessageType.TEXT,
    val language: String = "en",
    val priority: Int = 0,
    val compressedPayload: ByteArray = ByteArray(0)
) {
    val id: String get() = messageId

    val payload: MessagePayload by lazy(LazyThreadSafetyMode.NONE) {
        MessagePayload.parse(compressedPayload)
    }

    val text: String get() = payload.text
    val t0: Long get() = payload.t0
    val t1: Long get() = payload.t1
    val t2: Long get() = payload.t2
    val sttLatencyMs: Long get() = payload.sttLatencyMs
    val audioDurationMs: Long get() = payload.audioDurationMs
    val hasMetadata: Boolean get() = payload.hasMetadata

    val rawPayloadText: String get() = String(compressedPayload, Charsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransportMessage

        if (messageId != other.messageId) return false
        if (timestamp != other.timestamp) return false
        if (messageType != other.messageType) return false
        if (language != other.language) return false
        if (priority != other.priority) return false
        if (!compressedPayload.contentEquals(other.compressedPayload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + priority
        result = 31 * result + compressedPayload.contentHashCode()
        return result
    }

    /**
     * Returns a copy of this message with updated vertical slice metadata.
     */
    fun withMetadata(
        t0: Long = this.t0,
        t1: Long = this.t1,
        t2: Long = this.t2,
        sttLatencyMs: Long = this.sttLatencyMs,
        audioDurationMs: Long = this.audioDurationMs
    ): TransportMessage {
        val newPayload = MessagePayload(
            text = this.text,
            sttLatencyMs = sttLatencyMs,
            audioDurationMs = audioDurationMs,
            t0 = t0,
            t1 = t1,
            t2 = t2
        )
        return copy(compressedPayload = newPayload.toCompressedByteArray())
    }

    /**
     * Converts the timestamps and latencies into a [VerticalSliceTimestamps] model.
     */
    fun toVerticalSliceTimestamps(
        t3Received: Long = System.currentTimeMillis(),
        t4TtsComplete: Long = 0L,
        t5PlaybackStarted: Long = 0L
    ): VerticalSliceTimestamps {
        val netLatency = if (t2 > 0L && t3Received >= t2) (t3Received - t2) else 0L
        val sttLat = if (sttLatencyMs > 0L) sttLatencyMs else if (t1 >= t0 && t0 > 0L) (t1 - t0) else 0L
        val ttsLat = if (t4TtsComplete >= t3Received && t3Received > 0L) (t4TtsComplete - t3Received) else 0L
        val e2eLat = if (t5PlaybackStarted >= t0 && t0 > 0L) (t5PlaybackStarted - t0) else (sttLat + netLatency + ttsLat)

        return VerticalSliceTimestamps(
            t0PttReleased = t0,
            t1SttComplete = t1,
            t2Transmitted = t2,
            t3Received = t3Received,
            t4TtsComplete = t4TtsComplete,
            t5PlaybackStarted = t5PlaybackStarted,
            sttLatencyMs = sttLat,
            networkLatencyMs = netLatency,
            ttsLatencyMs = ttsLat,
            endToEndLatencyMs = e2eLat
        )
    }

    fun toReceivedMessage(
        isEmergencyOverride: Boolean? = null,
        t3Received: Long = System.currentTimeMillis()
    ): ReceivedMessage {
        val isEmergency = isEmergencyOverride ?: (messageType == TransportMessageType.ALERT)
        val lang = Language.fromCode(language, Language.ENGLISH)
        val netLatency = if (t2 > 0L && t3Received >= t2) (t3Received - t2) else 0L
        val metrics = if (hasMetadata) {
            LatencyMetrics(
                sttLatencyMs = sttLatencyMs,
                transmissionLatencyMs = netLatency,
                audioDurationMs = audioDurationMs,
                characterCount = text.length,
                compressedByteSize = compressedPayload.size,
                timestamp = t3Received,
                t0PttReleased = t0,
                t1SttComplete = t1,
                t2Transmitted = t2,
                t3Received = t3Received
            )
        } else null
        return ReceivedMessage(
            id = messageId,
            senderId = "peer",
            senderName = "Peer Device",
            text = text,
            originalLanguage = lang,
            targetLanguage = lang,
            timestamp = timestamp,
            messageType = if (isEmergency) MessageType.EMERGENCY_ALERT else MessageType.VOICE_NOTE,
            isEmergency = isEmergency,
            latencyMetrics = metrics,
            rawUtf8ByteSize = text.toByteArray(Charsets.UTF_8).size,
            compressedByteSize = compressedPayload.size,
            isOutgoing = false
        )
    }

    companion object {
        fun text(
            text: String,
            language: String = "en",
            priority: Int = 0,
            messageId: String = UUID.randomUUID().toString(),
            timestamp: Long = System.currentTimeMillis(),
            t0: Long = 0L,
            t1: Long = 0L,
            t2: Long = 0L,
            sttLatencyMs: Long = 0L,
            audioDurationMs: Long = 0L
        ): TransportMessage {
            val payload = MessagePayload(
                text = text,
                sttLatencyMs = sttLatencyMs,
                audioDurationMs = audioDurationMs,
                t0 = t0,
                t1 = t1,
                t2 = t2
            )
            return TransportMessage(
                messageId = messageId,
                timestamp = timestamp,
                messageType = TransportMessageType.TEXT,
                language = language,
                priority = priority,
                compressedPayload = payload.toCompressedByteArray()
            )
        }

        fun alert(
            text: String,
            language: String = "en",
            priority: Int = 1,
            messageId: String = UUID.randomUUID().toString(),
            timestamp: Long = System.currentTimeMillis(),
            t0: Long = 0L,
            t1: Long = 0L,
            t2: Long = 0L,
            sttLatencyMs: Long = 0L,
            audioDurationMs: Long = 0L
        ): TransportMessage {
            val payload = MessagePayload(
                text = text,
                sttLatencyMs = sttLatencyMs,
                audioDurationMs = audioDurationMs,
                t0 = t0,
                t1 = t1,
                t2 = t2
            )
            return TransportMessage(
                messageId = messageId,
                timestamp = timestamp,
                messageType = TransportMessageType.ALERT,
                language = language,
                priority = priority,
                compressedPayload = payload.toCompressedByteArray()
            )
        }

        fun withPayload(
            payload: MessagePayload,
            language: String = "en",
            priority: Int = 0,
            messageType: TransportMessageType = TransportMessageType.TEXT,
            messageId: String = UUID.randomUUID().toString(),
            timestamp: Long = System.currentTimeMillis()
        ): TransportMessage {
            return TransportMessage(
                messageId = messageId,
                timestamp = timestamp,
                messageType = messageType,
                language = language,
                priority = priority,
                compressedPayload = payload.toCompressedByteArray()
            )
        }

        fun fromReceivedMessage(
            received: ReceivedMessage,
            t0: Long = received.latencyMetrics?.t0PttReleased ?: 0L,
            t1: Long = received.latencyMetrics?.t1SttComplete ?: 0L,
            t2: Long = received.latencyMetrics?.t2Transmitted ?: 0L,
            sttLatencyMs: Long = received.latencyMetrics?.sttLatencyMs ?: 0L,
            audioDurationMs: Long = received.latencyMetrics?.audioDurationMs ?: 0L
        ): TransportMessage {
            val type = if (received.isEmergency) TransportMessageType.ALERT else TransportMessageType.TEXT
            val payload = MessagePayload(
                text = received.text,
                sttLatencyMs = sttLatencyMs,
                audioDurationMs = audioDurationMs,
                t0 = t0,
                t1 = t1,
                t2 = t2
            )
            return TransportMessage(
                messageId = received.id,
                timestamp = received.timestamp,
                messageType = type,
                language = received.originalLanguage.isoCode,
                priority = if (received.isEmergency) 1 else 0,
                compressedPayload = payload.toCompressedByteArray()
            )
        }
    }
}
