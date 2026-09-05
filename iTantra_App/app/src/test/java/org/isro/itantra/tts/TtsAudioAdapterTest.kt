package org.isro.itantra.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TtsAudioAdapterTest {

    @Test
    fun testEmptyConversions() {
        val emptyBytes = TtsAudioAdapter.floatsToPcm16(FloatArray(0))
        assertEquals(0, emptyBytes.size)

        val emptyFloats = TtsAudioAdapter.pcm16ToFloats(ByteArray(0))
        assertEquals(0, emptyFloats.size)
    }

    @Test
    fun testFloatToPcm16Boundaries() {
        val input = floatArrayOf(-1.0f, 0.0f, 1.0f)
        val pcm = TtsAudioAdapter.floatsToPcm16(input)
        assertEquals(6, pcm.size)

        val floatsBack = TtsAudioAdapter.pcm16ToFloats(pcm)
        assertEquals(3, floatsBack.size)

        assertEquals(-1.0f, floatsBack[0], 0.001f)
        assertEquals(0.0f, floatsBack[1], 0.001f)
        assertEquals(1.0f, floatsBack[2], 0.001f)
    }

    @Test
    fun testRoundTripSineWave() {
        val sampleRate = 22050
        val freq = 440.0
        val count = 2205 // 100ms
        val input = FloatArray(count) { i ->
            kotlin.math.sin(2.0 * Math.PI * freq * i / sampleRate).toFloat() * 0.8f
        }

        val pcm = TtsAudioAdapter.floatsToPcm16(input)
        assertEquals(count * 2, pcm.size)

        val output = TtsAudioAdapter.pcm16ToFloats(pcm)
        assertEquals(count, output.size)

        for (i in 0 until count) {
            assertEquals("Sample $i mismatch", input[i], output[i], 0.001f)
        }
    }

    @Test
    fun testEmergencyAlertPeakNormalization() {
        val input = floatArrayOf(0.1f, -0.4f, 0.5f, -0.2f)
        val boosted = TtsAudioAdapter.applyEmergencyAlertVolume(input)

        var maxPeak = 0f
        for (v in boosted) {
            val mag = abs(v)
            if (mag > maxPeak) maxPeak = mag
        }

        // The peak (0.5f) should be scaled to 1.0f
        assertEquals(1.0f, maxPeak, 0.001f)
    }

    @Test
    fun testDurationMath() {
        assertEquals(1000L, TtsAudioAdapter.samplesToDurationMs(22050L, 22050))
        assertEquals(22050L, TtsAudioAdapter.durationMsToSamples(1000L, 22050))
        assertEquals(1000L, TtsAudioAdapter.pcmBytesToDurationMs(44100L, 22050, 1, 2))
        assertEquals(44100L, TtsAudioAdapter.durationMsToPcmBytes(1000L, 22050, 1, 2))
    }
}
