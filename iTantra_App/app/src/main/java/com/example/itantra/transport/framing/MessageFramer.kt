package com.example.itantra.transport.framing

import com.example.itantra.transport.TransportMessage
import com.example.itantra.transport.TransportMessageType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * MessageFramer provides binary framing and deframing over raw TCP streams.
 *
 * Frame Format:
 * - 2 bytes: MAGIC (0x4954 -> "IT")
 * - 1 byte:  VERSION (0x01)
 * - 4 bytes: BODY_LENGTH (Int, big-endian)
 * - N bytes: Serialized TransportMessage body
 *
 * Total header overhead: 7 bytes.
 */
object MessageFramer {
    const val MAGIC: Short = 0x4954.toShort()
    const val VERSION: Byte = 0x01.toByte()
    const val MAX_FRAME_BODY_SIZE: Int = 65536
    const val HEADER_SIZE: Int = 7

    private const val MAGIC_BYTE_1: Byte = 0x49.toByte() // 'I'
    private const val MAGIC_BYTE_2: Byte = 0x54.toByte() // 'T'

    /**
     * Serializes and writes a [TransportMessage] frame to [out].
     *
     * @param message The message to encode.
     * @param out The target output stream.
     * @return Total number of bytes written to the stream (header + body).
     * @throws IllegalArgumentException if the serialized body exceeds [MAX_FRAME_BODY_SIZE].
     */
    fun encodeFrame(message: TransportMessage, out: DataOutputStream): Int {
        val body = serializeMessageBody(message)
        if (body.size > MAX_FRAME_BODY_SIZE) {
            throw IllegalArgumentException(
                "Payload size ${body.size} bytes exceeds maximum allowed frame body ($MAX_FRAME_BODY_SIZE bytes)"
            )
        }

        out.writeShort(MAGIC.toInt())
        out.writeByte(VERSION.toInt())
        out.writeInt(body.size)
        out.write(body)
        out.flush()

        return HEADER_SIZE + body.size
    }

    /**
     * Reads and decodes the next [TransportMessage] from [input].
     *
     * Handles stream synchronization: if stream corruption occurs, scans byte-by-byte
     * for the next MAGIC sequence (0x49, 0x54) to re-synchronize framing.
     *
     * @param input The input stream to read from.
     * @return The decoded [TransportMessage], or `null` if the end of stream (EOF) is reached.
     */
    fun decodeFrame(input: DataInputStream): TransportMessage? {
        try {
            while (true) {
                // 1. Scan byte-by-byte for MAGIC: 0x49, 0x54
                var b1 = input.readByte()
                while (true) {
                    if (b1 == MAGIC_BYTE_1) {
                        val b2 = input.readByte()
                        if (b2 == MAGIC_BYTE_2) {
                            break
                        } else {
                            // b2 is not 0x54, but b2 might itself be 0x49
                            b1 = b2
                            continue
                        }
                    }
                    b1 = input.readByte()
                }

                // 2. Validate version
                val version = input.readByte()
                if (version != VERSION) {
                    // Protocol version mismatch or false magic sync, resume scanning
                    continue
                }

                // 3. Read and validate body length
                val bodyLength = input.readInt()
                if (bodyLength < 0 || bodyLength > MAX_FRAME_BODY_SIZE) {
                    // Body length bounds violated, resume scanning
                    continue
                }

                // 4. Read body fully
                val body = ByteArray(bodyLength)
                input.readFully(body)

                // 5. Deserialize message body
                return try {
                    deserializeMessageBody(body)
                } catch (e: Exception) {
                    // Deserialization failure due to corrupted body, resume scanning
                    continue
                }
            }
        } catch (e: EOFException) {
            return null
        } catch (e: IOException) {
            return null
        }
    }

    private fun serializeMessageBody(message: TransportMessage): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { dos ->
            dos.writeUTF(message.messageId)
            dos.writeLong(message.timestamp)
            dos.writeByte(message.messageType.typeCode.toInt())
            dos.writeUTF(message.language)
            dos.writeInt(message.priority)
            dos.writeInt(message.compressedPayload.size)
            dos.write(message.compressedPayload)
            dos.flush()
        }
        return baos.toByteArray()
    }

    private fun deserializeMessageBody(body: ByteArray): TransportMessage {
        DataInputStream(ByteArrayInputStream(body)).use { dis ->
            val messageId = dis.readUTF()
            val timestamp = dis.readLong()
            val typeCode = dis.readByte()
            val messageType = TransportMessageType.fromCode(typeCode)
            val language = dis.readUTF()
            val priority = dis.readInt()
            val payloadLength = dis.readInt()

            if (payloadLength < 0 || payloadLength > dis.available()) {
                throw IllegalArgumentException("Invalid payload length $payloadLength in body of size ${body.size}")
            }

            val payload = ByteArray(payloadLength)
            dis.readFully(payload)

            return TransportMessage(
                messageId = messageId,
                timestamp = timestamp,
                messageType = messageType,
                language = language,
                priority = priority,
                compressedPayload = payload
            )
        }
    }
}
