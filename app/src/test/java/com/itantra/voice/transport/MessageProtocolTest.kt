package com.itantra.voice.transport

import com.itantra.voice.transport.framing.MessageFramer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class MessageProtocolTest {

    @Test
    fun testTransportMessageJsonRoundTrip() {
        val original = TransportMessage(
            sourceLanguage = "hi-IN",
            targetLanguage = "en-IN",
            text = "मुझे स्टेशन जाना है।",
            type = TransportMessageType.TRANSLATION
        )

        val json = original.toJson()
        assertTrue(json.contains("hi-IN"))
        assertTrue(json.contains("en-IN"))
        assertTrue(json.contains("मुझे स्टेशन जाना है।"))

        val parsed = TransportMessage.fromJson(json).getOrThrow()
        assertEquals(original.messageId, parsed.messageId)
        assertEquals(original.sourceLanguage, parsed.sourceLanguage)
        assertEquals(original.targetLanguage, parsed.targetLanguage)
        assertEquals(original.text, parsed.text)
        assertEquals(original.type, parsed.type)
        assertEquals(original.priority, parsed.priority)
    }

    @Test
    fun testFramingWriteAndReadSingleFrame() {
        val message = TransportMessage(
            sourceLanguage = "ta-IN",
            targetLanguage = "hi-IN",
            text = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
            type = TransportMessageType.TRANSLATION
        )

        val out = ByteArrayOutputStream()
        MessageFramer.writeFrame(message, out)

        val frameBytes = out.toByteArray()
        assertTrue("Frame must contain at least 4 bytes length prefix", frameBytes.size > 4)

        val inStream = ByteArrayInputStream(frameBytes)
        val decoded = MessageFramer.readFrame(inStream)

        assertEquals(message.messageId, decoded.messageId)
        assertEquals(message.text, decoded.text)
        assertEquals(message.sourceLanguage, decoded.sourceLanguage)
        assertEquals(message.targetLanguage, decoded.targetLanguage)
    }

    @Test
    fun testFramingMultipleSequentialFrames() {
        val messages = (1..5).map { i ->
            TransportMessage(
                sourceLanguage = "en-IN",
                targetLanguage = "mr-IN",
                text = "Message sequence number $i",
                type = TransportMessageType.TRANSLATION
            )
        }

        val out = ByteArrayOutputStream()
        messages.forEach { MessageFramer.writeFrame(it, out) }

        val inStream = ByteArrayInputStream(out.toByteArray())
        val decodedList = mutableListOf<TransportMessage>()
        repeat(5) {
            decodedList.add(MessageFramer.readFrame(inStream))
        }

        assertEquals(5, decodedList.size)
        messages.forEachIndexed { index, expected ->
            assertEquals(expected.text, decodedList[index].text)
        }
    }

    @Test
    fun testFramingEofThrowsException() {
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        try {
            MessageFramer.readFrame(emptyStream)
            fail("Should have thrown EOFException")
        } catch (expected: EOFException) {
            // Success
        }
    }

    @Test
    fun testOversizedPayloadThrowsException() {
        val largeText = "A".repeat(MessageFramer.MAX_PAYLOAD_SIZE + 10)
        val message = TransportMessage(
            sourceLanguage = "en-IN",
            targetLanguage = "hi-IN",
            text = largeText
        )

        val out = ByteArrayOutputStream()
        try {
            MessageFramer.writeFrame(message, out)
            fail("Should have rejected oversized payload")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("exceeds maximum allowed size"))
        }
    }

    @Test
    fun testTestMessageFactory() {
        val testMsg = TransportMessage.createTestMessage()
        assertEquals("HELLO FROM ITANTRA", testMsg.text)
        assertEquals("hi-IN", testMsg.sourceLanguage)
        assertEquals("en-IN", testMsg.targetLanguage)
        assertNotNull(testMsg.messageId)
    }
}
