package org.isro.itantra.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        // Check ChunkSize (32000 + 36 = 32036 = 0x00007D24 -> LE: 0x24, 0x7D, 0x00, 0x00)
        assertEquals(0x24.toByte(), header[4])
        assertEquals(0x7D.toByte(), header[5])
        assertEquals(0x00.toByte(), header[6])
        assertEquals(0x00.toByte(), header[7])

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

        // Check Block Align at offset 32 (2 bytes = 0x0002 -> LE: 0x02, 0x00)
        assertEquals(0x02.toByte(), header[32])
        assertEquals(0x00.toByte(), header[33])

        // Check Bits Per Sample at offset 34 (16 bits = 0x0010 -> LE: 0x10, 0x00)
        assertEquals(0x10.toByte(), header[34])
        assertEquals(0x00.toByte(), header[35])
    }

    @Test
    fun testWavHeaderParsing() {
        val pcmLength = 64000 // 2 seconds
        val header = AudioUtils.generateWavHeader(pcmLength)
        val meta = AudioUtils.parseWavHeader(header)

        assertNotNull(meta)
        assertEquals(16000, meta!!.sampleRate)
        assertEquals(1, meta.channelCount)
        assertEquals(16, meta.bitsPerSample)
        assertEquals(32000, meta.byteRate)
        assertEquals(2, meta.blockAlign)
        assertEquals(64000, meta.pcmByteLength)
        assertEquals(2000L, meta.durationMs)
        assertEquals(2.0, meta.durationSeconds, 1e-6)
    }

    @Test
    fun testPcmToWavPackaging() {
        val pcmData = ByteArray(640) { 0x42.toByte() }
        val wav = AudioUtils.pcmToWav(pcmData)

        assertEquals(44 + 640, wav.size)
        assertTrue(AudioUtils.isValidWavHeader(wav))

        // Ensure payload is intact
        for (i in 0 until 640) {
            assertEquals(0x42.toByte(), wav[44 + i])
        }
    }

    @Test
    fun testCorruptedHeaderValidation() {
        val corruptedHeader = ByteArray(44) { 0 }
        assertFalse(AudioUtils.isValidWavHeader(corruptedHeader))

        val shortHeader = ByteArray(40)
        assertFalse(AudioUtils.isValidWavHeader(shortHeader))
    }

    @Test
    fun testExactShortFloatShortRoundTrip() {
        val testShorts = shortArrayOf(
            Short.MIN_VALUE,
            (-16384).toShort(),
            (-1).toShort(),
            0.toShort(),
            1.toShort(),
            16384.toShort(),
            Short.MAX_VALUE
        )

        val floats = AudioUtils.shortsToFloats(testShorts)
        assertEquals(-1.0f, floats[0], 1e-7f)
        assertEquals(-0.5f, floats[1], 1e-7f)
        assertEquals(0.0f, floats[3], 1e-7f)
        assertEquals(0.5f, floats[5], 1e-7f)
        assertTrue("Max short must normalize to 32767/32768", abs(floats[6] - 0.9999695f) < 1e-6f)

        val restoredShorts = AudioUtils.floatsToShorts(floats)
        for (i in testShorts.indices) {
            assertEquals("Round-trip mismatch at index $i", testShorts[i], restoredShorts[i])
        }
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
    fun testFloatClippingAndNaNSanitization() {
        val dirtyFloats = floatArrayOf(2.5f, -3.0f, Float.NaN, 0.0f)
        val pcmBytes = AudioUtils.floatsToPcm16(dirtyFloats)
        val restoredShorts = AudioUtils.pcm16ToShorts(pcmBytes)

        assertEquals("Positive saturation must clamp to +32767", 32767.toShort(), restoredShorts[0])
        assertEquals("Negative saturation must clamp to -32768", (-32768).toShort(), restoredShorts[1])
        assertEquals("NaN must be safely sanitized to 0", 0.toShort(), restoredShorts[2])
        assertEquals("Zero must remain 0", 0.toShort(), restoredShorts[3])
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
