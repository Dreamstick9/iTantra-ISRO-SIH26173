package com.example.itantra.tts

import org.junit.Assert.*
import org.junit.Test

class AudioDataTest {

    @Test
    fun testEmptyAudioData() {
        val empty = AudioData.EMPTY
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.sampleCount)
        assertEquals(0, empty.rawPcm.size)
        assertEquals(0L, empty.durationMs)
    }

    @Test
    fun testFloatArrayToPcm16LeConversion() {
        // Zero sample
        val zero = floatArrayOf(0.0f)
        val pcmZero = AudioData.floatArrayToPcm16Le(zero)
        assertEquals(2, pcmZero.size)
        assertEquals(0.toByte(), pcmZero[0])
        assertEquals(0.toByte(), pcmZero[1])

        // Maximum positive amplitude (+1.0f -> 32767 -> 0x7FFF)
        val maxPos = floatArrayOf(1.0f)
        val pcmMaxPos = AudioData.floatArrayToPcm16Le(maxPos)
        assertEquals(2, pcmMaxPos.size)
        assertEquals(0xFF.toByte(), pcmMaxPos[0])
        assertEquals(0x7F.toByte(), pcmMaxPos[1])

        // Maximum negative amplitude (-1.0f -> -32768 -> 0x8000)
        val maxNeg = floatArrayOf(-1.0f)
        val pcmMaxNeg = AudioData.floatArrayToPcm16Le(maxNeg)
        assertEquals(2, pcmMaxNeg.size)
        assertEquals(0x00.toByte(), pcmMaxNeg[0])
        assertEquals(0x80.toByte(), pcmMaxNeg[1])

        // Clipping: values beyond [-1.0, 1.0] must be clamped
        val clipped = floatArrayOf(2.5f, -3.0f)
        val pcmClipped = AudioData.floatArrayToPcm16Le(clipped)
        assertEquals(4, pcmClipped.size)
        assertEquals(0xFF.toByte(), pcmClipped[0])
        assertEquals(0x7F.toByte(), pcmClipped[1])
        assertEquals(0x00.toByte(), pcmClipped[2])
        assertEquals(0x80.toByte(), pcmClipped[3])
    }

    @Test
    fun testRoundTripConversion() {
        val originalFloats = floatArrayOf(0.0f, 0.5f, -0.5f, 0.9f, -0.9f)
        val pcm = AudioData.floatArrayToPcm16Le(originalFloats)
        val recoveredFloats = AudioData.pcm16LeToFloatArray(pcm)

        assertEquals(originalFloats.size, recoveredFloats.size)
        for (i in originalFloats.indices) {
            assertEquals(originalFloats[i], recoveredFloats[i], 0.001f)
        }
    }

    @Test
    fun testAudioDataEqualityAndHashCode() {
        val samples1 = floatArrayOf(0.1f, 0.2f)
        val pcm1 = AudioData.floatArrayToPcm16Le(samples1)
        val audio1 = AudioData(samples1, 16000, pcm1, 100L)

        val samples2 = floatArrayOf(0.1f, 0.2f)
        val pcm2 = AudioData.floatArrayToPcm16Le(samples2)
        val audio2 = AudioData(samples2, 16000, pcm2, 100L)

        assertEquals(audio1, audio2)
        assertEquals(audio1.hashCode(), audio2.hashCode())
    }
}
