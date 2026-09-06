package com.example.itantra

import com.example.itantra.data.*
import org.junit.Assert.*
import org.junit.Test

class AppStateTest {

    @Test
    fun testDefaultAppStateInitialization() {
        val state = AppState.INITIAL
        assertEquals(ConnectionStatus.DISCONNECTED, state.connectionStatus)
        assertEquals(Language.HINDI, state.inputLanguage)
        assertEquals(Language.HINDI, state.outputLanguage)
        assertEquals(PttState.IDLE, state.pttState)
        assertEquals(TransmissionMode.PUSH_TO_TALK, state.transmissionMode)
        assertFalse(state.emergencyAlert.isActive)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun testAllTenMandatedLanguagesSupported() {
        val langs = Language.entries
        assertEquals(10, langs.size)

        val codes = langs.map { it.isoCode }.toSet()
        val expected = setOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
        assertEquals(expected, codes)
    }

    @Test
    fun testNavIcSubframeCompliance() {
        val shortMsg = ReceivedMessage(
            senderId = "1",
            senderName = "Peer",
            text = "Cyclone SOS",
            originalLanguage = Language.ENGLISH,
            compressedByteSize = 18
        )
        assertTrue("18 bytes compressed must fit NavIC 27-byte subframe", shortMsg.fitsNavIcSubframe)
        assertTrue(shortMsg.fitsNavIcPacket)

        val longMsg = ReceivedMessage(
            senderId = "2",
            senderName = "Peer",
            text = "A".repeat(80),
            originalLanguage = Language.ENGLISH,
            rawUtf8ByteSize = 80,
            compressedByteSize = 35
        )
        assertFalse("35 bytes compressed exceeds 27-byte subframe", longMsg.fitsNavIcSubframe)
        assertTrue("35 bytes compressed fits 277-byte packet", longMsg.fitsNavIcPacket)
    }
}
