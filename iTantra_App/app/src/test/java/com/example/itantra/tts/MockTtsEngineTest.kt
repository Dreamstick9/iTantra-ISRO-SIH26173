package com.example.itantra.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockTtsEngineTest {

    private lateinit var engine: MockTtsEngine

    @Before
    fun setUp() {
        engine = MockTtsEngine(simulatedLatencyMs = 10L)
    }

    @Test
    fun testSynthesizeValidText() = runBlocking {
        val text = "Hello, this is iTantra."
        val audioData = engine.synthesize(text)

        assertFalse(audioData.isEmpty)
        assertTrue(audioData.sampleCount > 0)
        assertEquals(16000, audioData.sampleRate)
        assertTrue(audioData.rawPcm.isNotEmpty())
        assertEquals(audioData.sampleCount * 2, audioData.rawPcm.size)
        assertTrue(audioData.durationMs > 0)
        assertTrue(engine.lastLatencyMs.value > 0)
        assertEquals(audioData.durationMs, engine.lastDurationMs.value)
        assertEquals(TtsPlaybackState.IDLE, engine.playbackState.value)
    }

    @Test
    fun testSynthesizeEmptyTextHandling() = runBlocking {
        val emptyAudio = engine.synthesize("")
        assertTrue(emptyAudio.isEmpty)

        val blankAudio = engine.synthesize("   ")
        assertTrue(blankAudio.isEmpty)
    }

    @Test
    fun testSynthesizeFailureHandling() = runBlocking {
        engine.shouldFail = true
        val failedAudio = engine.synthesize("Test failure")
        assertTrue(failedAudio.isEmpty)
        assertEquals(TtsPlaybackState.ERROR, engine.playbackState.value)
        assertNotNull(engine.errorMessage.value)
    }

    @Test
    fun testSpeakAndStopLifecycle() = runBlocking {
        assertEquals(TtsPlaybackState.IDLE, engine.playbackState.value)
        engine.stop()
        assertEquals(TtsPlaybackState.STOPPED, engine.playbackState.value)
    }

    @Test
    fun testRelease() {
        assertTrue(engine.isModelLoaded.value)
        engine.release()
        assertFalse(engine.isModelLoaded.value)
    }
}
