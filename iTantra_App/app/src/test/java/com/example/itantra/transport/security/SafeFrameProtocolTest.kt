package com.example.itantra.transport.security

import com.example.itantra.transport.PacketPriority
import com.example.itantra.transport.PacketType
import com.example.itantra.transport.TransportPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

class SafeFrameProtocolTest {

    @Test
    fun testValidFrameSerializationAndDeserialization() {
        val payload = "Hello Tactical Mesh Network".toByteArray(Charsets.UTF_8)
        val originalPacket = TransportPacket(
            id = "test-packet-001",
            senderId = "UNIT-ALPHA",
            senderName = "Operator 1",
            payload = payload,
            packetType = PacketType.VOICE_TEXT,
            priority = PacketPriority.EMERGENCY,
            timestamp = 1700000000000L
        )

        val bos = ByteArrayOutputStream()
        SafeFrameProtocol.writeFrame(bos, originalPacket)

        val bis = ByteArrayInputStream(bos.toByteArray())
        val decodedPacket = SafeFrameProtocol.readFrame(bis)

        assertEquals(originalPacket.id, decodedPacket.id)
        assertEquals(originalPacket.senderId, decodedPacket.senderId)
        assertEquals(originalPacket.senderName, decodedPacket.senderName)
        assertEquals(originalPacket.packetType, decodedPacket.packetType)
        assertEquals(originalPacket.priority, decodedPacket.priority)
        assertEquals(originalPacket.timestamp, decodedPacket.timestamp)
        assertArrayEquals(originalPacket.payload, decodedPacket.payload)
    }

    @Test
    fun testOversizedPayloadDetectionPreventsOom() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // Write valid header
        dos.write(SafeFrameProtocol.MAGIC_HEADER)
        dos.writeByte(SafeFrameProtocol.PROTOCOL_VERSION.toInt())
        dos.writeByte(PacketType.VOICE_TEXT.id.toInt())
        dos.writeByte(0) // Standard priority
        dos.writeUTF("packet-malicious")
        dos.writeUTF("attacker")
        dos.writeUTF("rogue-node")
        dos.writeLong(System.currentTimeMillis())

        // Malicious huge length (e.g. 50 MB)
        val hugeLength = 50 * 1024 * 1024
        dos.writeInt(hugeLength)
        dos.flush()

        val bis = ByteArrayInputStream(bos.toByteArray())

        val exception = assertThrows(SafeFrameProtocol.SecurityBoundsException::class.java) {
            SafeFrameProtocol.readFrame(bis, maxPayloadSize = 64 * 1024)
        }

        assertNotNull(exception.message)
        assert(exception.message!!.contains("exceeds max allowable limit"))
    }

    @Test
    fun testNegativePayloadLengthRejected() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        dos.write(SafeFrameProtocol.MAGIC_HEADER)
        dos.writeByte(SafeFrameProtocol.PROTOCOL_VERSION.toInt())
        dos.writeByte(PacketType.VOICE_TEXT.id.toInt())
        dos.writeByte(0)
        dos.writeUTF("packet-negative")
        dos.writeUTF("attacker")
        dos.writeUTF("rogue-node")
        dos.writeLong(System.currentTimeMillis())
        dos.writeInt(-100) // Negative length
        dos.flush()

        val bis = ByteArrayInputStream(bos.toByteArray())

        assertThrows(SafeFrameProtocol.SecurityBoundsException::class.java) {
            SafeFrameProtocol.readFrame(bis)
        }
    }

    @Test
    fun testInvalidMagicHeaderRejectedImmediately() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // Invalid magic header
        dos.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        dos.flush()

        val bis = ByteArrayInputStream(bos.toByteArray())

        val exception = assertThrows(SafeFrameProtocol.ProtocolMismatchException::class.java) {
            SafeFrameProtocol.readFrame(bis)
        }

        assert(exception.message!!.contains("Invalid protocol magic header"))
    }

    @Test
    fun testCorruptedPayloadCrc32MismatchRejected() {
        val payload = "Important Emergency Broadcast".toByteArray(Charsets.UTF_8)
        val packet = TransportPacket(
            id = "packet-crc-test",
            senderId = "HQ",
            senderName = "Command",
            payload = payload,
            packetType = PacketType.EMERGENCY_ALERT,
            priority = PacketPriority.EMERGENCY,
            timestamp = 1700000000000L
        )

        val bos = ByteArrayOutputStream()
        SafeFrameProtocol.writeFrame(bos, packet)
        val rawWireBytes = bos.toByteArray()

        // Tamper with a payload byte in the stream
        val tamperedBytes = rawWireBytes.clone()
        // Payload starts after magic(4) + ver(1) + type(1) + prio(1) + UTF strings + timestamp(8) + length(4)
        // Find the index of byte 'I' (0x49) of payload and flip it
        val targetByteIndex = tamperedBytes.size - 8 - 5 // 5 bytes before CRC
        tamperedBytes[targetByteIndex] = (tamperedBytes[targetByteIndex] + 1).toByte()

        val bis = ByteArrayInputStream(tamperedBytes)

        assertThrows(SafeFrameProtocol.ChecksumMismatchException::class.java) {
            SafeFrameProtocol.readFrame(bis)
        }
    }
}
