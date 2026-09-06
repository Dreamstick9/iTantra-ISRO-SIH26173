package com.example.itantra

import com.example.itantra.audio.MockAudioEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockAudioEngineTest {

    private lateinit var engine: MockAudioEngine

    @Before
    fun setUp() {
        engine = MockAudioEngine()
        engine.initialize()
    }

    @Test
    fun testRecordingLifecycle() {
        assertFalse(engine.isRecording)
        assertEquals(0f, engine.audioLevel.value, 0.001f)

        engine.startRecording()
        assertTrue(engine.isRecording)
        assertTrue(engine.audioLevel.value > 0f)

        Thread.sleep(120)
        val pcm = engine.stopRecording()

        assertFalse(engine.isRecording)
        assertEquals(0f, engine.audioLevel.value, 0.001f)
        assertTrue(pcm.isNotEmpty())
    }

    @Test
    fun testPlaybackLifecycle() {
        val testPcm = ByteArray(1024) { 1 }
        engine.playAudio(testPcm, isEmergency = true)

        assertTrue(engine.isPlaying)
        assertTrue(engine.lastPlayedWasEmergency)
        assertEquals(1024, engine.lastPlayedAudio?.size)

        engine.stopPlayback()
        assertFalse(engine.isPlaying)
    }
}
