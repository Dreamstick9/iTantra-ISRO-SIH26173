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
        assertEquals(TransceiverState.IDLE, state.transceiverState)
        assertEquals(TransmissionMode.PUSH_TO_TALK, state.transmissionMode)
        assertFalse(state.emergencyAlert.isActive)
        assertTrue(state.messages.isEmpty())
        assertEquals(0L, state.verticalSliceTimestamps.endToEndLatencyMs)
        assertEquals(0L, state.verticalSliceTimestamps.t0PttReleased)
    }

    @Test
    fun testTransceiverStateEnumAndMappings() {
        val states = TransceiverState.entries
        assertEquals(6, states.size)
        assertTrue(states.contains(TransceiverState.IDLE))
        assertTrue(states.contains(TransceiverState.RECORDING))
        assertTrue(states.contains(TransceiverState.TRANSCRIBING))
        assertTrue(states.contains(TransceiverState.TRANSMITTING))
        assertTrue(states.contains(TransceiverState.RECEIVING))
        assertTrue(states.contains(TransceiverState.SPEAKING))

        // Check property helpers
        assertTrue(TransceiverState.IDLE.isIdle)
        assertFalse(TransceiverState.IDLE.isActive)
        assertTrue(TransceiverState.RECORDING.isRecording)
        assertTrue(TransceiverState.TRANSCRIBING.isTranscribing)
        assertTrue(TransceiverState.TRANSMITTING.isTransmitting)
        assertTrue(TransceiverState.RECEIVING.isReceiving)
        assertTrue(TransceiverState.SPEAKING.isSpeaking)

        // Mapping to PttState
        assertEquals(PttState.IDLE, TransceiverState.IDLE.toPttState())
        assertEquals(PttState.RECORDING, TransceiverState.RECORDING.toPttState())
        assertEquals(PttState.PROCESSING, TransceiverState.TRANSCRIBING.toPttState())
        assertEquals(PttState.PROCESSING, TransceiverState.TRANSMITTING.toPttState())
        assertEquals(PttState.PROCESSING, TransceiverState.RECEIVING.toPttState())
        assertEquals(PttState.PROCESSING, TransceiverState.SPEAKING.toPttState())

        // PttState to TransceiverState mapping
        assertEquals(TransceiverState.IDLE, PttState.IDLE.toTransceiverState())
        assertEquals(TransceiverState.RECORDING, PttState.RECORDING.toTransceiverState())
        assertEquals(TransceiverState.TRANSCRIBING, PttState.PROCESSING.toTransceiverState())

        // AppState helper
        val appState = AppState.INITIAL.withTransceiverState(TransceiverState.TRANSMITTING)
        assertEquals(TransceiverState.TRANSMITTING, appState.transceiverState)
        assertEquals(PttState.PROCESSING, appState.pttState)
    }

    @Test
    fun testVerticalSliceTimestampsComputation() {
        val ts = VerticalSliceTimestamps(
            t0PttReleased = 1000L,
            t1SttComplete = 1185L,
            t2Transmitted = 1200L,
            t3Received = 1242L,
            t4TtsComplete = 1360L,
            t5PlaybackStarted = 1365L
        )

        val computed = ts.withComputedLatencies()
        assertEquals(185L, computed.sttLatencyMs)
        assertEquals(42L, computed.networkLatencyMs)
        assertEquals(118L, computed.ttsLatencyMs)
        assertEquals(365L, computed.endToEndLatencyMs)

        val metrics = computed.toLatencyMetrics(audioDurationMs = 2000L)
        assertEquals(185L, metrics.sttLatencyMs)
        assertEquals(42L, metrics.transmissionLatencyMs)
        assertEquals(118L, metrics.ttsLatencyMs)
        assertEquals(365L, metrics.endToEndLatencyMs)
        assertEquals(2000L, metrics.audioDurationMs)
        assertEquals(1000L, metrics.t0PttReleased)
        assertEquals(1365L, metrics.t5PlaybackStarted)

        val convertedBack = metrics.toVerticalSliceTimestamps()
        assertEquals(1000L, convertedBack.t0PttReleased)
        assertEquals(1185L, convertedBack.t1SttComplete)
        assertEquals(1200L, convertedBack.t2Transmitted)
        assertEquals(1242L, convertedBack.t3Received)
        assertEquals(1360L, convertedBack.t4TtsComplete)
        assertEquals(1365L, convertedBack.t5PlaybackStarted)
        assertEquals(365L, convertedBack.endToEndLatencyMs)
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
