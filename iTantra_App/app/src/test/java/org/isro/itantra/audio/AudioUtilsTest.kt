package org.isro.itantra.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioUtilsTest {

    @Test
    fun testWavHeaderGeneration() {
        val pcmLength = 32000 // 1 second of audio
        val header = AudioUtils.generateWavHeader(pcmLength)

        assertEquals("WAV header must be exactly 44 bytes", 44, header.size)
        assertTrue("WAV header must validate correctly", AudioUtils.isValidWavHeader(header))

        // Check RIFF marker
        assertEquals('R'.code.toByte(), header[0])
        assertEquals('I'.code.toByte(), header[1])
        assertEquals('F'.code.toByte(), header[2])
        assertEquals('F'.code.toByte(), header[3])

        // Check WAVE marker
        assertEquals('W'.code.toByte(), header[8])
        assertEquals('A'.code.toByte(), header[9])
        assertEquals('V'.code.toByte(), header[10])
        assertEquals('E'.code.toByte(), header[11])

        // Check Sample Rate at offset 24 (16000 = 0x00003E80 -> LE: 0x80, 0x3E, 0x00, 0x00)
        assertEquals(0x80.toByte(), header[24])
        assertEquals(0x3E.toByte(), header[25])
        assertEquals(0x00.toByte(), header[26])
        assertEquals(0x00.toByte(), header[27])

        // Check Byte Rate at offset 28 (32000 = 0x00007D00 -> LE: 0x00, 0x7D, 0x00, 0x00)
        assertEquals(0x00.toByte(), header[28])
        assertEquals(0x7D.toByte(), header[29])
        assertEquals(0x00.toByte(), header[30])
        assertEquals(0x00.toByte(), header[31])
    }

    @Test
    fun testPcmToWavPackaging() {
        val pcmData = ByteArray(640) { 0 }
        val wav = AudioUtils.pcmToWav(pcmData)

        assertEquals(44 + 640, wav.size)
        assertTrue(AudioUtils.isValidWavHeader(wav))
    }

    @Test
    fun testInvalidWavHeaderRejection() {
        val corruptedHeader = ByteArray(44) { 0 }
        assertFalse(AudioUtils.isValidWavHeader(corruptedHeader))
    }

    @Test
    fun testPcmToFloatNormalization() {
        // Test +32767, -32768, and 0
        val pcmBytes = byteArrayOf(
            0xFF.toByte(), 0x7F.toByte(), // +32767
            0x00.toByte(), 0x80.toByte(), // -32768
            0x00.toByte(), 0x00.toByte()  // 0
        )

        val floats = AudioUtils.pcm16ToFloats(pcmBytes)
        assertEquals(3, floats.size)
        assertTrue("Max positive sample should normalize near +1.0f", abs(floats[0] - 0.9999695f) < 1e-4)
        assertEquals("Max negative sample should normalize to -1.0f", -1.0f, floats[1], 1e-6f)
        assertEquals("Zero sample should normalize to 0.0f", 0.0f, floats[2], 1e-6f)
    }

    @Test
    fun testPcmFloatRoundtrip() {
        val originalFloats = floatArrayOf(0.5f, -0.5f, 0.0f, 0.95f, -0.95f)
        val pcmBytes = AudioUtils.floatsToPcm16(originalFloats)
        val convertedBack = AudioUtils.pcm16ToFloats(pcmBytes)

        assertEquals(originalFloats.size, convertedBack.size)
        for (i in originalFloats.indices) {
            assertTrue("Sample $i roundtrip drift should be under 0.001", abs(originalFloats[i] - convertedBack[i]) < 1e-3)
        }
    }

    @Test
    fun testRmsAndVisualizerCalculation() {
        val silencePcm = ByteArray(640) { 0 }
        val rmsSilence = AudioUtils.calculateRmsFromPcm16(silencePcm)
        assertEquals(0.0f, rmsSilence, 1e-6f)
        assertEquals(0.0f, AudioUtils.calculateVisualizerLevel(rmsSilence), 1e-6f)

        // Peak audio: alternating max values
        val loudPcm = ByteArray(640)
        for (i in 0 until 320) {
            loudPcm[i * 2] = 0xFF.toByte()
            loudPcm[i * 2 + 1] = 0x7F.toByte()
        }
        val rmsLoud = AudioUtils.calculateRmsFromPcm16(loudPcm)
        assertTrue("RMS of loud audio should be near 1.0f", rmsLoud > 0.9f)
        val visLevel = AudioUtils.calculateVisualizerLevel(rmsLoud)
        assertTrue("Visualizer level of loud audio should be near 1.0f", visLevel > 0.9f)
    }
}
