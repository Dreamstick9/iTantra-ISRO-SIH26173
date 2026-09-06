package com.example.itantra.transport

import com.example.itantra.data.Language
import com.example.itantra.data.MessageType
import com.example.itantra.data.ReceivedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportMessageTest {

    @Test
    fun testTextFactory() {
        val msg = TransportMessage.text("Test message", language = "hi", priority = 0)
        assertEquals(TransportMessageType.TEXT, msg.messageType)
        assertEquals("Test message", msg.text)
        assertEquals("hi", msg.language)
        assertEquals(0, msg.priority)
        assertEquals(msg.messageId, msg.id)
    }

    @Test
    fun testAlertFactory() {
        val alert = TransportMessage.alert("Emergency alert", language = "ta", priority = 2)
        assertEquals(TransportMessageType.ALERT, alert.messageType)
        assertEquals("Emergency alert", alert.text)
        assertEquals("ta", alert.language)
        assertEquals(2, alert.priority)
    }

    @Test
    fun testFromReceivedMessageAndToReceivedMessage() {
        val original = ReceivedMessage(
            id = "msg-xyz",
            senderId = "operator-1",
            senderName = "Operator 1",
            text = "Coast guard emergency",
            originalLanguage = Language.ENGLISH,
            messageType = MessageType.EMERGENCY_ALERT,
            isEmergency = true
        )

        val transportMsg = TransportMessage.fromReceivedMessage(original)
        assertEquals("msg-xyz", transportMsg.messageId)
        assertEquals("Coast guard emergency", transportMsg.text)
        assertEquals(TransportMessageType.ALERT, transportMsg.messageType)
        assertEquals(1, transportMsg.priority)
        assertEquals("en", transportMsg.language)

        val convertedBack = transportMsg.toReceivedMessage()
        assertEquals(original.id, convertedBack.id)
        assertEquals(original.text, convertedBack.text)
        assertEquals(MessageType.EMERGENCY_ALERT, convertedBack.messageType)
        assertTrue(convertedBack.isEmergency)
    }

    @Test
    fun testEqualityAndHashCode() {
        val msg1 = TransportMessage.text("Hello", language = "en", messageId = "123", timestamp = 5000L)
        val msg2 = TransportMessage.text("Hello", language = "en", messageId = "123", timestamp = 5000L)
        val msg3 = TransportMessage.text("World", language = "en", messageId = "124", timestamp = 5000L)

        assertEquals(msg1, msg2)
        assertEquals(msg1.hashCode(), msg2.hashCode())
        assertFalse(msg1 == msg3)
    }

    @Test
    fun testPeerStatusFromCode() {
        assertEquals(PeerStatus.CONNECTED, PeerStatus.fromCode(0))
        assertEquals(PeerStatus.INVITED, PeerStatus.fromCode(1))
        assertEquals(PeerStatus.FAILED, PeerStatus.fromCode(2))
        assertEquals(PeerStatus.AVAILABLE, PeerStatus.fromCode(3))
        assertEquals(PeerStatus.UNAVAILABLE, PeerStatus.fromCode(4))
        assertEquals(PeerStatus.UNAVAILABLE, PeerStatus.fromCode(99))
    }

    @Test
    fun testDiagnosticsDefaults() {
        val diag = TransportDiagnostics()
        assertEquals(P2pStatus.OFFLINE, diag.p2pStatus)
        assertEquals(TcpStatus.DISCONNECTED, diag.tcpStatus)
        assertEquals(0L, diag.bytesSent)
        assertEquals(0L, diag.bytesReceived)
        assertFalse(diag.isGroupOwner)
    }

    @Test
    fun testMessagePayloadJsonRoundTrip() {
        val original = MessagePayload(
            text = "Cyclone warning in sector 4",
            sttLatencyMs = 185L,
            audioDurationMs = 2100L,
            t0 = 1000L,
            t1 = 1185L,
            t2 = 1210L
        )
        assertTrue(original.hasMetadata)

        val json = original.toJson()
        assertTrue(json.contains("\"text\":\"Cyclone warning in sector 4\""))
        assertTrue(json.contains("\"sttLatencyMs\":185"))
        assertTrue(json.contains("\"audioDurationMs\":2100"))
        assertTrue(json.contains("\"t0\":1000"))
        assertTrue(json.contains("\"t1\":1185"))
        assertTrue(json.contains("\"t2\":1210"))

        val parsed = MessagePayload.parse(json)
        assertEquals(original.text, parsed.text)
        assertEquals(original.sttLatencyMs, parsed.sttLatencyMs)
        assertEquals(original.audioDurationMs, parsed.audioDurationMs)
        assertEquals(original.t0, parsed.t0)
        assertEquals(original.t1, parsed.t1)
        assertEquals(original.t2, parsed.t2)
        assertTrue(parsed.hasMetadata)
    }

    @Test
    fun testMessagePayloadPlaintextBackwardsCompatibility() {
        val rawPlain = "PLAIN TEXT MESSAGE NO JSON"
        val payload = MessagePayload.parse(rawPlain)
        assertEquals("PLAIN TEXT MESSAGE NO JSON", payload.text)
        assertEquals(0L, payload.sttLatencyMs)
        assertEquals(0L, payload.audioDurationMs)
        assertEquals(0L, payload.t0)
        assertEquals(0L, payload.t1)
        assertEquals(0L, payload.t2)
        assertFalse(payload.hasMetadata)

        val bytes = rawPlain.toByteArray(Charsets.UTF_8)
        val fromBytes = MessagePayload.parse(bytes)
        assertEquals("PLAIN TEXT MESSAGE NO JSON", fromBytes.text)
        assertFalse(fromBytes.hasMetadata)
    }

    @Test
    fun testMessagePayloadSpecialCharactersAndMultilingual() {
        val complexText = "नाविक \"warning\" \\ alerts:\nLine 2 & tab\tDone!"
        val payload = MessagePayload(
            text = complexText,
            sttLatencyMs = 250L,
            audioDurationMs = 1800L,
            t0 = 5000L,
            t1 = 5250L,
            t2 = 5290L
        )

        val jsonBytes = payload.toByteArray()
        val parsed = MessagePayload.parse(jsonBytes)
        assertEquals(complexText, parsed.text)
        assertEquals(250L, parsed.sttLatencyMs)
        assertEquals(1800L, parsed.audioDurationMs)
        assertEquals(5000L, parsed.t0)
        assertEquals(5250L, parsed.t1)
        assertEquals(5290L, parsed.t2)
    }

    @Test
    fun testTransportMessageWithMetadataAndTimestamps() {
        val msg = TransportMessage.text(
            text = "Emergency evacuation now",
            language = "hi",
            priority = 1,
            t0 = 1000L,
            t1 = 1185L,
            t2 = 1205L,
            sttLatencyMs = 185L,
            audioDurationMs = 2200L
        )

        assertEquals("Emergency evacuation now", msg.text)
        assertEquals(1000L, msg.t0)
        assertEquals(1185L, msg.t1)
        assertEquals(1205L, msg.t2)
        assertEquals(185L, msg.sttLatencyMs)
        assertEquals(2200L, msg.audioDurationMs)
        assertTrue(msg.hasMetadata)

        // Test withMetadata update
        val updatedMsg = msg.withMetadata(t2 = 1250L)
        assertEquals(1250L, updatedMsg.t2)
        assertEquals(1000L, updatedMsg.t0)
        assertEquals(1185L, updatedMsg.t1)

        // Test conversion to VerticalSliceTimestamps
        val vsTimestamps = updatedMsg.toVerticalSliceTimestamps(
            t3Received = 1290L,
            t4TtsComplete = 1400L,
            t5PlaybackStarted = 1410L
        )
        assertEquals(1000L, vsTimestamps.t0PttReleased)
        assertEquals(1185L, vsTimestamps.t1SttComplete)
        assertEquals(1250L, vsTimestamps.t2Transmitted)
        assertEquals(1290L, vsTimestamps.t3Received)
        assertEquals(1400L, vsTimestamps.t4TtsComplete)
        assertEquals(1410L, vsTimestamps.t5PlaybackStarted)
        assertEquals(185L, vsTimestamps.sttLatencyMs)
        assertEquals(40L, vsTimestamps.networkLatencyMs)
        assertEquals(110L, vsTimestamps.ttsLatencyMs)
        assertEquals(410L, vsTimestamps.endToEndLatencyMs)

        // Test conversion to ReceivedMessage
        val received = updatedMsg.toReceivedMessage(t3Received = 1290L)
        assertNotNull(received.latencyMetrics)
        val metrics = received.latencyMetrics!!
        assertEquals(185L, metrics.sttLatencyMs)
        assertEquals(40L, metrics.transmissionLatencyMs)
        assertEquals(2200L, metrics.audioDurationMs)
        assertEquals(1000L, metrics.t0PttReleased)
        assertEquals(1185L, metrics.t1SttComplete)
        assertEquals(1250L, metrics.t2Transmitted)
        assertEquals(1290L, metrics.t3Received)
    }

    @Test
    fun testCorruptedJsonFallbackToPlainText() {
        val corruptedJson = "{\"text\":\"Incomplete string...}"
        val parsed = MessagePayload.parse(corruptedJson)
        assertEquals(corruptedJson, parsed.text)
        assertFalse(parsed.hasMetadata)
    }
}
