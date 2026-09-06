package com.example.itantra.transport.security

import com.example.itantra.transport.PacketPriority
import com.example.itantra.transport.PacketType
import com.example.itantra.transport.TransportPacket
import com.example.itantra.util.Logger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.CRC32

/**
 * Robust binary framing protocol for TCP sockets over Wi-Fi Direct.
 *
 * Wire Format:
 * +------------------------+-------------------+--------------------+
 * | Magic Bytes (4 bytes)  | Protocol Ver (1B) | Packet Type ID(1B) |
 * | "ITAN" [0x49,0x54,0x41,0x4E]| 0x01         | 0x01..0x04         |
 * +------------------------+-------------------+--------------------+
 * | Priority (1B)          | Header Len (2B)   | Sender ID (UTF)    |
 * +------------------------+-------------------+--------------------+
 * | Sender Name (UTF)      | Packet ID (UTF)   | Payload Length (4B)|
 * +------------------------+-------------------+--------------------+
 * | Payload Data (N bytes) | CRC32 (8 bytes / Long)                 |
 * +------------------------+----------------------------------------+
 *
 * Security Protections:
 * 1. OOM & DoS Defense: Enforces strict [MAX_ALLOWED_PAYLOAD_SIZE] (default 64 KB, absolute ceiling 1 MB).
 *    Refuses to allocate ByteArray if length header is negative or exceeds bounds.
 * 2. Desynchronization Defense: Magic byte validation aborts immediately if stream is misaligned.
 * 3. Data Integrity: CRC32 checksum validates payload integrity across local wireless link.
 * 4. Read Loop Bounds: Uses bounded buffer reads to prevent partial frame corruption.
 */
object SafeFrameProtocol {

    private const val TAG = "SafeFrameProtocol"

    val MAGIC_HEADER = byteArrayOf(0x49.toByte(), 0x54.toByte(), 0x41.toByte(), 0x4E.toByte()) // "ITAN"
    const val PROTOCOL_VERSION: Byte = 0x01

    /**
     * Absolute hard ceiling for frame payload size across the socket.
     * Prevents malicious or corrupted length headers (e.g. 0x7FFFFFFF) from triggering OutOfMemoryError.
     */
    const val MAX_ALLOWED_PAYLOAD_SIZE: Int = 1 * 1024 * 1024 // 1 MB hard max

    /**
     * Standard recommended max payload size for tactical voice/text packets.
     */
    const val DEFAULT_MAX_PAYLOAD_SIZE: Int = 64 * 1024 // 64 KB

    class SecurityBoundsException(message: String) : SecurityException(message)
    class ProtocolMismatchException(message: String) : IllegalArgumentException(message)
    class ChecksumMismatchException(message: String) : IllegalStateException(message)

    /**
     * Serializes and writes a [TransportPacket] safely to the output stream.
     */
    fun writeFrame(outputStream: OutputStream, packet: TransportPacket) {
        val dos = DataOutputStream(outputStream)

        // 1. Magic Header (4 bytes)
        dos.write(MAGIC_HEADER)

        // 2. Protocol Version (1 byte)
        dos.writeByte(PROTOCOL_VERSION.toInt())

        // 3. Packet Type (1 byte)
        dos.writeByte(packet.packetType.id.toInt())

        // 4. Priority (1 byte: 0 = STANDARD, 1 = EMERGENCY)
        dos.writeByte(if (packet.priority == PacketPriority.EMERGENCY) 1 else 0)

        // 5. Metadata Strings
        dos.writeUTF(packet.id)
        dos.writeUTF(packet.senderId)
        dos.writeUTF(packet.senderName)
        dos.writeLong(packet.timestamp)

        // 6. Payload Length check before writing
        val payload = packet.payload
        if (payload.size > MAX_ALLOWED_PAYLOAD_SIZE) {
            throw SecurityBoundsException("Payload exceeds MAX_ALLOWED_PAYLOAD_SIZE (${payload.size} > $MAX_ALLOWED_PAYLOAD_SIZE)")
        }
        dos.writeInt(payload.size)

        // 7. Payload Data
        dos.write(payload)

        // 8. CRC32 Checksum (8 bytes / Long)
        val crc = CRC32()
        crc.update(payload)
        dos.writeLong(crc.value)

        dos.flush()
    }

    /**
     * Safely reads and parses a [TransportPacket] from the input stream with strict bounds verification.
     *
     * @param inputStream The socket input stream.
     * @param maxPayloadSize The maximum acceptable payload size for this session (capped at [MAX_ALLOWED_PAYLOAD_SIZE]).
     * @return Fully parsed and validated [TransportPacket].
     * @throws SecurityBoundsException If the declared payload length exceeds permitted bounds.
     * @throws ProtocolMismatchException If magic bytes or version header are invalid.
     * @throws ChecksumMismatchException If CRC32 payload verification fails.
     * @throws EOFException If socket stream closes prematurely.
     */
    fun readFrame(
        inputStream: InputStream,
        maxPayloadSize: Int = DEFAULT_MAX_PAYLOAD_SIZE
    ): TransportPacket {
        val dis = DataInputStream(inputStream)

        // 1. Validate Magic Header (4 bytes)
        val magic = ByteArray(4)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            val hex = magic.joinToString("") { "%02X".format(it) }
            Logger.e(TAG, "Protocol desync or invalid magic header: 0x$hex")
            throw ProtocolMismatchException("Invalid protocol magic header: 0x$hex")
        }

        // 2. Validate Protocol Version (1 byte)
        val version = dis.readByte()
        if (version != PROTOCOL_VERSION) {
            throw ProtocolMismatchException("Unsupported protocol version: $version (expected $PROTOCOL_VERSION)")
        }

        // 3. Packet Type (1 byte)
        val typeId = dis.readByte()
        val packetType = PacketType.values().firstOrNull { it.id == typeId } ?: PacketType.VOICE_TEXT

        // 4. Priority (1 byte)
        val priorityByte = dis.readByte()
        val priority = if (priorityByte.toInt() == 1) PacketPriority.EMERGENCY else PacketPriority.STANDARD

        // 5. Metadata Strings
        val packetId = dis.readUTF()
        val senderId = dis.readUTF()
        val senderName = dis.readUTF()
        val timestamp = dis.readLong()

        // 6. Security Bounds Verification on Payload Length
        val payloadLength = dis.readInt()
        val effectiveMax = maxPayloadSize.coerceAtMost(MAX_ALLOWED_PAYLOAD_SIZE)

        if (payloadLength < 0) {
            throw SecurityBoundsException("Malformed packet: Negative payload length ($payloadLength)")
        }
        if (payloadLength > effectiveMax) {
            throw SecurityBoundsException(
                "Potential DoS / OOM attack detected! Declared payload size $payloadLength bytes " +
                        "exceeds max allowable limit of $effectiveMax bytes. Aborting frame read."
            )
        }

        // 7. Bounded allocation and read
        val payload = ByteArray(payloadLength)
        dis.readFully(payload)

        // 8. CRC32 Checksum Verification
        val expectedCrc = dis.readLong()
        val crc = CRC32()
        crc.update(payload)
        val actualCrc = crc.value

        if (actualCrc != expectedCrc) {
            throw ChecksumMismatchException("CRC32 checksum mismatch: expected $expectedCrc, got $actualCrc")
        }

        return TransportPacket(
            id = packetId,
            senderId = senderId,
            senderName = senderName,
            payload = payload,
            packetType = packetType,
            priority = priority,
            timestamp = timestamp
        )
    }
}
