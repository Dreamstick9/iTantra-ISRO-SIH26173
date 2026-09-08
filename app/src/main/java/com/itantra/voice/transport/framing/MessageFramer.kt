package com.itantra.voice.transport.framing

import com.itantra.voice.transport.TransportMessage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Encodes and decodes length-prefixed binary frames for persistent TCP/RFCOMM streaming.
 * Frame structure:
 * [4-byte big-endian Int: payload length N] + [N bytes: UTF-8 JSON TransportMessage]
 *
 * Enforces a strict 64 KB maximum payload limit to prevent unbounded memory allocation.
 */
object MessageFramer {

    const val MAX_PAYLOAD_SIZE = 65536 // 64 KB

    /**
     * Serializes a TransportMessage into a length-prefixed frame and writes it to the output stream.
     */
    fun writeFrame(message: TransportMessage, outputStream: OutputStream) {
        val jsonBytes = message.toJson().toByteArray(Charsets.UTF_8)
        if (jsonBytes.size > MAX_PAYLOAD_SIZE) {
            throw IllegalArgumentException("Message payload exceeds maximum allowed size of $MAX_PAYLOAD_SIZE bytes (actual: ${jsonBytes.size})")
        }

        val dataOut = DataOutputStream(outputStream)
        dataOut.writeInt(jsonBytes.size)
        dataOut.write(jsonBytes)
        dataOut.flush()
    }

    /**
     * Reads a single length-prefixed frame from the input stream and deserializes it into a TransportMessage.
     * Throws [java.io.EOFException] if the stream terminates cleanly between frames.
     */
    fun readFrame(inputStream: InputStream): TransportMessage {
        val dataIn = DataInputStream(inputStream)
        val length = dataIn.readInt()

        // Reject zero as well as negative: an empty payload is never a valid message and
        // previously fell through to a misleading JSON parse failure.
        if (length <= 0 || length > MAX_PAYLOAD_SIZE) {
            throw IllegalStateException("Invalid frame length: $length (allowed: 1..$MAX_PAYLOAD_SIZE)")
        }

        val payload = ByteArray(length)
        dataIn.readFully(payload)

        val jsonString = String(payload, Charsets.UTF_8)
        return TransportMessage.fromJson(jsonString).getOrThrow()
    }
}
