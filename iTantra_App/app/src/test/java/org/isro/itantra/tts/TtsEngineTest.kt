package org.isro.itantra.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsEngineTest {

    private val engine = TtsEngine()

    @Test
    fun testSynthesizeHindiText() = runBlocking {
        val text = "चक्रवात चेतावनी! तुरंत सुरक्षित स्थान पर जाएं।"
        val audio = engine.synthesize(text, SupportedLanguage.HINDI)

        assertEquals(22050, audio.sampleRate)
        assertTrue("Audio samples should not be empty", audio.samples.isNotEmpty())
        assertTrue("Audio duration should be at least 400ms", audio.durationMs >= 400L)
        assertEquals(audio.samples.size * 2L, audio.totalBytes)
    }

    @Test
    fun testSynthesizeEnglishEmergency() = runBlocking {
        val text = "Tsunami alert! Evacuate coastal area immediately."
        val audio = engine.synthesize(text, SupportedLanguage.ENGLISH, isEmergencyAlert = true)

        assertEquals(22050, audio.sampleRate)
        assertTrue(audio.samples.isNotEmpty())

        // Emergency alerts are normalized to maximum dynamic range
        var maxPeak = 0.0f
        for (s in audio.samples) {
            val mag = kotlin.math.abs(s)
            if (mag > maxPeak) maxPeak = mag
        }
        assertTrue("Emergency alert peak should reach 1.0f", maxPeak >= 0.99f)
    }

    @Test
    fun testSynthesizeTamilSampleRate() = runBlocking {
        val text = "புயல் எச்சரிக்கை"
        val audio = engine.synthesize(text, SupportedLanguage.TAMIL)

        assertEquals(16000, audio.sampleRate)
        assertTrue(audio.samples.isNotEmpty())
    }

    @Test
    fun testSynthesisSpeedBenchmark() = runBlocking {
        val text = "इसरो आईटंत्रा ऑफ़लाइन वाकी-टाकी सिस्टम परीक्षण सफल रहा।"
        val startNs = System.nanoTime()
        val audio = engine.synthesize(text, SupportedLanguage.HINDI)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        val durationMs = audio.durationMs
        val rtf = elapsedMs.toFloat() / durationMs.toFloat()

        println("TTS Benchmark: Compute=${elapsedMs}ms, Audio=${durationMs}ms, RTF=$rtf")
        assertTrue("RTF should be well under real-time (< 1.0)", rtf < 1.0f)
    }
}
