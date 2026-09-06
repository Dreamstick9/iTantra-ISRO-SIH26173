package com.example.itantra.transport

import com.example.itantra.transport.framing.MessageFramer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Random

class MessageFramerTest {

    @Test
    fun testEncodeAndDecodeTextMessage() {
        val original = TransportMessage.text(
            text = "HELLO FROM PHONE A",
            language = "en",
            priority = 0
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        val bytesWritten = MessageFramer.encodeFrame(original, dos)

        assertEquals(bos.size(), bytesWritten)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val decoded = MessageFramer.decodeFrame(dis)

        assertNotNull(decoded)
        assertEquals(original.messageId, decoded!!.messageId)
        assertEquals(original.messageType, decoded.messageType)
        assertEquals(TransportMessageType.TEXT, decoded.messageType)
        assertEquals(original.language, decoded.language)
        assertEquals(original.priority, decoded.priority)
        assertEquals(original.text, decoded.text)
        assertEquals("HELLO FROM PHONE A", decoded.text)
        assertArrayEquals(original.compressedPayload, decoded.compressedPayload)
    }

    @Test
    fun testEncodeAndDecodeAlertMessage() {
        val original = TransportMessage.alert(
            text = "CYCLONE ADVISORY",
            language = "en",
            priority = 2
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        val bytesWritten = MessageFramer.encodeFrame(original, dos)

        assertEquals(bos.size(), bytesWritten)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val decoded = MessageFramer.decodeFrame(dis)

        assertNotNull(decoded)
        assertEquals(original.messageId, decoded!!.messageId)
        assertEquals(original.messageType, decoded.messageType)
        assertEquals(TransportMessageType.ALERT, decoded.messageType)
        assertEquals(original.language, decoded.language)
        assertEquals(original.priority, decoded.priority)
        assertEquals(original.text, decoded.text)
        assertEquals("CYCLONE ADVISORY", decoded.text)
        assertArrayEquals(original.compressedPayload, decoded.compressedPayload)
    }

    @Test
    fun testStreamSynchronizationWithCorruptedPrefix() {
        val validMessage = TransportMessage.text(
            text = "RECOVERED AFTER GARBAGE",
            language = "en",
            priority = 1
        )

        val frameBos = ByteArrayOutputStream()
        MessageFramer.encodeFrame(validMessage, DataOutputStream(frameBos))
        val validFrameBytes = frameBos.toByteArray()

        // 15 random garbage bytes before a valid frame
        val random = Random(42)
        val garbageBytes = ByteArray(15)
        random.nextBytes(garbageBytes)
        // Ensure no accidental MAGIC sequence (0x49, 0x54) in the garbage prefix
        for (i in 0 until 14) {
            if (garbageBytes[i] == 0x49.toByte() && garbageBytes[i + 1] == 0x54.toByte()) {
                garbageBytes[i + 1] = 0x00
            }
        }

        val combinedStream = ByteArrayOutputStream()
        combinedStream.write(garbageBytes)
        combinedStream.write(validFrameBytes)

        val dis = DataInputStream(ByteArrayInputStream(combinedStream.toByteArray()))
        val decoded = MessageFramer.decodeFrame(dis)

        assertNotNull(decoded)
        assertEquals(validMessage.messageId, decoded!!.messageId)
        assertEquals(validMessage.messageType, decoded.messageType)
        assertEquals(validMessage.language, decoded.language)
        assertEquals(validMessage.priority, decoded.priority)
        assertEquals(validMessage.text, decoded.text)
    }

    @Test
    fun testMultipleConsecutiveFramesInStream() {
        val msg1 = TransportMessage.text("First message in stream", language = "en", priority = 0)
        val msg2 = TransportMessage.alert("Second alert message", language = "hi", priority = 2)
        val msg3 = TransportMessage(
            messageId = "ping-frame-3",
            timestamp = System.currentTimeMillis(),
            messageType = TransportMessageType.PING,
            language = "en",
            priority = 0,
            compressedPayload = "Third message in stream".toByteArray(Charsets.UTF_8)
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        MessageFramer.encodeFrame(msg1, dos)
        MessageFramer.encodeFrame(msg2, dos)
        MessageFramer.encodeFrame(msg3, dos)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))

        val decoded1 = MessageFramer.decodeFrame(dis)
        val decoded2 = MessageFramer.decodeFrame(dis)
        val decoded3 = MessageFramer.decodeFrame(dis)
        val decoded4 = MessageFramer.decodeFrame(dis)

        assertNotNull(decoded1)
        assertEquals(msg1.messageId, decoded1!!.messageId)
        assertEquals(msg1.messageType, decoded1.messageType)
        assertEquals(msg1.language, decoded1.language)
        assertEquals(msg1.priority, decoded1.priority)
        assertEquals(msg1.text, decoded1.text)

        assertNotNull(decoded2)
        assertEquals(msg2.messageId, decoded2!!.messageId)
        assertEquals(msg2.messageType, decoded2.messageType)
        assertEquals(msg2.language, decoded2.language)
        assertEquals(msg2.priority, decoded2.priority)
        assertEquals(msg2.text, decoded2.text)

        assertNotNull(decoded3)
        assertEquals(msg3.messageId, decoded3!!.messageId)
        assertEquals(msg3.messageType, decoded3.messageType)
        assertEquals(msg3.language, decoded3.language)
        assertEquals(msg3.priority, decoded3.priority)
        assertEquals(msg3.text, decoded3.text)

        assertNull(decoded4)
    }

    @Test
    fun testEmptyStreamReturnsNull() {
        val dis = DataInputStream(ByteArrayInputStream(ByteArray(0)))
        val decoded = MessageFramer.decodeFrame(dis)
        assertNull(decoded)
    }

    @Test
    fun testOversizedPayloadRejection() {
        val hugePayload = ByteArray(MessageFramer.MAX_FRAME_BODY_SIZE + 1)
        val oversizedMsg = TransportMessage(
            messageId = "huge-01",
            timestamp = System.currentTimeMillis(),
            messageType = TransportMessageType.TEXT,
            language = "en",
            priority = 0,
            compressedPayload = hugePayload
        )

        val dos = DataOutputStream(ByteArrayOutputStream())
        assertThrows(IllegalArgumentException::class.java) {
            MessageFramer.encodeFrame(oversizedMsg, dos)
        }
    }

    @Test
    fun testInvalidVersionCausesResync() {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // Write bad frame: valid magic but invalid version 0x99
        dos.writeShort(MessageFramer.MAGIC.toInt())
        dos.writeByte(0x99) // Invalid version
        dos.writeInt(10)
        dos.write(ByteArray(10))

        // Followed by valid frame
        val validMsg = TransportMessage.text("Valid frame after bad version")
        MessageFramer.encodeFrame(validMsg, dos)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val decoded = MessageFramer.decodeFrame(dis)

        assertNotNull(decoded)
        assertEquals(validMsg.messageId, decoded!!.messageId)
        assertEquals("Valid frame after bad version", decoded.text)
    }

    @Test
    fun testMultilingualPayloadPreservation() {
        val testPhrases = listOf(
            "hi" to "नाविक संकट चेतावनी",
            "ta" to "புயல் எச்சரிக்கை: கடலோர பகுதியை உடனடியாக காலி செய்யவும்",
            "te" to "తుఫాను హెచ్చరిక: తీరప్రాంతాన్ని ఖాళీ చేయండి",
            "bn" to "ঘূর্ণিঝড় সতর্কতা: অবিলম্বে উপকূলীয় এলাকা ত্যাগ করুন",
            "gu" to "વાવાઝોડાની ચેતવણી: દરિયાકાંઠો તાત્કાલિક ખાલી કરો",
            "or" to "ବାତ୍ୟା ସତର୍କତା: ତୁରନ୍ତ ଉପକୂଳ ଅଞ୍ଚଳ ଖାଲି କରନ୍ତୁ"
        )

        for ((lang, phrase) in testPhrases) {
            val msg = TransportMessage.text(text = phrase, language = lang)
            val bos = ByteArrayOutputStream()
            MessageFramer.encodeFrame(msg, DataOutputStream(bos))

            val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
            val decoded = MessageFramer.decodeFrame(dis)

            assertNotNull(decoded)
            assertEquals(phrase, decoded!!.text)
            assertEquals(lang, decoded.language)
        }
    }

    @Test
    fun testEncodeAndDecodeMessageWithVerticalSliceMetadata() {
        val original = TransportMessage.text(
            text = "EVACUATION ORDER IN EFFECT",
            language = "hi",
            priority = 1,
            t0 = 1725625000000L,
            t1 = 1725625000185L,
            t2 = 1725625000210L,
            sttLatencyMs = 185L,
            audioDurationMs = 2400L
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        val bytesWritten = MessageFramer.encodeFrame(original, dos)

        assertEquals(bos.size(), bytesWritten)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))
        val decoded = MessageFramer.decodeFrame(dis)

        assertNotNull(decoded)
        assertEquals(original.messageId, decoded!!.messageId)
        assertEquals("EVACUATION ORDER IN EFFECT", decoded.text)
        assertEquals(1725625000000L, decoded.t0)
        assertEquals(1725625000185L, decoded.t1)
        assertEquals(1725625000210L, decoded.t2)
        assertEquals(185L, decoded.sttLatencyMs)
        assertEquals(2400L, decoded.audioDurationMs)
        assertTrue(decoded.hasMetadata)

        val vsTimestamps = decoded.toVerticalSliceTimestamps(
            t3Received = 1725625000250L,
            t4TtsComplete = 1725625000370L,
            t5PlaybackStarted = 1725625000375L
        )
        assertEquals(185L, vsTimestamps.sttLatencyMs)
        assertEquals(40L, vsTimestamps.networkLatencyMs)
        assertEquals(120L, vsTimestamps.ttsLatencyMs)
        assertEquals(375L, vsTimestamps.endToEndLatencyMs)
    }

    @Test
    fun testLegacyAndEnvelopeMessageFramingCoexistence() {
        // Frame 1: Legacy plain text without metadata
        val legacyMsg = TransportMessage.text("Legacy plain text message")
        // Frame 2: Modern message with vertical slice metadata
        val modernMsg = TransportMessage.text(
            text = "Modern envelope message",
            sttLatencyMs = 150L,
            t0 = 5000L,
            t1 = 5150L,
            t2 = 5175L
        )

        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        MessageFramer.encodeFrame(legacyMsg, dos)
        MessageFramer.encodeFrame(modernMsg, dos)

        val dis = DataInputStream(ByteArrayInputStream(bos.toByteArray()))

        val decoded1 = MessageFramer.decodeFrame(dis)
        val decoded2 = MessageFramer.decodeFrame(dis)

        assertNotNull(decoded1)
        assertEquals("Legacy plain text message", decoded1!!.text)
        assertFalse(decoded1.hasMetadata)
        assertEquals(0L, decoded1.t0)

        assertNotNull(decoded2)
        assertEquals("Modern envelope message", decoded2!!.text)
        assertTrue(decoded2.hasMetadata)
        assertEquals(5000L, decoded2.t0)
        assertEquals(150L, decoded2.sttLatencyMs)
    }
}
