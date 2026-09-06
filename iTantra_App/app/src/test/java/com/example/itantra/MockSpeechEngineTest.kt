package com.example.itantra

import com.example.itantra.data.Language
import com.example.itantra.speech.MockSpeechEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockSpeechEngineTest {

    private lateinit var engine: MockSpeechEngine

    @Before
    fun setUp() {
        engine = MockSpeechEngine()
        engine.initialize()
    }

    @Test
    fun testTranscriptionMultilingual() = runBlocking {
        val pcm = ByteArray(3200) { 1 }
        val resultHindi = engine.transcribe(pcm, Language.HINDI).getOrThrow()
        assertTrue(resultHindi.text.contains("चक्रवात चेतावनी"))
        assertTrue(resultHindi.confidence > 0.9f)

        val resultTamil = engine.transcribe(pcm, Language.TAMIL).getOrThrow()
        assertTrue(resultTamil.text.contains("புயல் எச்சரிக்கை"))

        val resultEnglish = engine.transcribe(pcm, Language.ENGLISH).getOrThrow()
        assertTrue(resultEnglish.text.contains("Cyclone alert"))
    }

    @Test
    fun testSynthesisRtfCalculation() = runBlocking {
        val result = engine.synthesize("Cyclone Warning", Language.ENGLISH).getOrThrow()
        assertTrue(result.audioPcm.isNotEmpty())
        assertTrue("RTF must be less than 1.0 (faster than real-time)", result.rtf < 1.0)
    }
}
