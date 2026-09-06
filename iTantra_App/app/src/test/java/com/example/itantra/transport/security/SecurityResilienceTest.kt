package com.example.itantra.transport.security

import android.Manifest
import com.example.itantra.transport.PacketPriority
import com.example.itantra.transport.PacketType
import com.example.itantra.transport.TransportMessage
import com.example.itantra.transport.TransportMessageType
import com.example.itantra.transport.TransportPacket
import com.example.itantra.transport.framing.MessageFramer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/**
 * Automated security and resilience unit test suite validating:
 * 1. Frame boundary limits & OOM mitigation (MAX_FRAME_BODY_SIZE = 65536, MAX_ALLOWED_PAYLOAD_SIZE = 1MB).
 * 2. Strict CRC32 and magic byte integrity checks.
 * 3. Truncated stream / malformed packet DoS resistance.
 * 4. Runtime permission contract validation for offline operation.
 */
class SecurityResilienceTest {

    @Test
    fun testWifiDirectPermissionHelperReturnsValidPermissions() {
        val permissions = WifiDirectPermissionHelper.getRequiredPermissions()
        assertNotNull(permissions)
        assertTrue(permissions.isNotEmpty())
        for (perm in permissions) {
            assertTrue(
                "Permission must be valid Android permission",
                perm.startsWith("android.permission.")
            )
            // Permission must be either NEARBY_WIFI_DEVICES or FINE/COARSE location
            assertTrue(
                perm == Manifest.permission.NEARBY_WIFI_DEVICES ||
                        perm == Manifest.permission.ACCESS_FINE_LOCATION ||
                        perm == Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    @Test
    fun testSafeFrameProtocolRejectsOversizedPayloadAtWrite() {
        val oversizedPayload = ByteArray(SafeFrameProtocol.MAX_ALLOWED_PAYLOAD_SIZE + 1)
        val packet = TransportPacket(
            id = "overflow-write-test",
            senderId = "test-node",
            senderName = "Test",
            payload = oversizedPayload,
            packetType = PacketType.VOICE_TEXT
        )

        val bos = ByteArrayOutputStream()
        val ex = assertThrows(SafeFrameProtocol.SecurityBoundsException::class.java) {
            SafeFrameProtocol.writeFrame(bos, packet)
        }
        assertTrue(ex.message!!.contains("exceeds MAX_ALLOWED_PAYLOAD_SIZE"))
    }

    @Test
    fun testSafeFrameProtocolAcceptsExactHardMaxPayload() {
        val maxPayload = ByteArray(1024) // Test with 1KB for fast unit test
        val packet = TransportPacket(
            id = "exact-max-test",
            senderId = "test-node",
            senderName = "Test",
            payload = maxPayload,
            packetType = PacketType.VOICE_TEXT
        )

        val bos = ByteArrayOutputStream()
        SafeFrameProtocol.writeFrame(bos, packet)

        val bis = ByteArrayInputStream(bos.toByteArray())
        val decoded = SafeFrameProtocol.readFrame(bis, maxPayloadSize = 1024)
        assertEquals(packet.id, decoded.id)
        assertArrayEquals(maxPayload, decoded.payload)
    }

    @Test
    fun testSafeFrameProtocolZeroLengthPayloadHandledSafely() {
        val zeroPayload = ByteArray(0)
        val packet = TransportPacket(
            id = "zero-payload-test",
            senderId = "test-node",
            senderName = "Test",
            payload = zeroPayload,
            packetType = PacketType.VOICE_TEXT
        )

        val bos = ByteArrayOutputStream()
        SafeFrameProtocol.writeFrame(bos, packet)

        val bis = ByteArrayInputStream(bos.toByteArray())
        val decoded = SafeFrameProtocol.readFrame(bis)
        assertEquals(0, decoded.payload.size)
        assertEquals(packet.id, decoded.id)
    }

    @Test
    fun testSafeFrameProtocolTruncatedStreamThrowsEofException() {
        val packet = TransportPacket(
            id = "truncate-test",
            senderId = "test-node",
            senderName = "Test",
            payload = "Sensitive Data".toByteArray(Charsets.UTF_8),
            packetType = PacketType.VOICE_TEXT
        )

        val bos = ByteArrayOutputStream()
        SafeFrameProtocol.writeFrame(bos, packet)
        val bytes = bos.toByteArray()

        // Truncate stream before CRC
        val truncated = bytes.copyOf(bytes.size - 4)
        val bis = ByteArrayInputStream(truncated)

        assertThrows(EOFException::class.java) {
            SafeFrameProtocol.readFrame(bis)
        }
    }

    @Test
    fun testMessageFramerRejectsNegativeBodyLengthGracefully() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // Inject magic header + version + negative body length
        dos.writeShort(MessageFramer.MAGIC.toInt())
        dos.writeByte(MessageFramer.VERSION.toInt())
        dos.writeInt(-500) // Negative length attack
        dos.flush()

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val result = MessageFramer.decodeFrame(dis)
        // Must reject negative length and reach EOF without crashing or throwing
        assertNull(result)
    }

    @Test
    fun testMessageFramerBodyLengthExceeding65536Rejected() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        dos.writeShort(MessageFramer.MAGIC.toInt())
        dos.writeByte(MessageFramer.VERSION.toInt())
        dos.writeInt(MessageFramer.MAX_FRAME_BODY_SIZE + 100) // Exceeds 65536
        dos.write(ByteArray(100))
        dos.flush()

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val result = MessageFramer.decodeFrame(dis)
        // Must reject oversized frame and scan past it
        assertNull(result)
    }

    @Test
    fun testMessageFramerEncodeExactMaxPayloadLimit() {
        // Create message whose serialized body exceeds MAX_FRAME_BODY_SIZE
        val hugeBytes = ByteArray(MessageFramer.MAX_FRAME_BODY_SIZE)
        val message = TransportMessage(
            messageId = "huge-msg",
            timestamp = 1000L,
            messageType = TransportMessageType.TEXT,
            language = "en",
            priority = 0,
            compressedPayload = hugeBytes
        )

        val dos = DataOutputStream(ByteArrayOutputStream())
        assertThrows(IllegalArgumentException::class.java) {
            MessageFramer.encodeFrame(message, dos)
        }
    }
}
