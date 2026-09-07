package com.itantra.voice.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {

    // Tier 1 Tests: Nominal Feature Coverage (F2 PCM-to-WAV Encoder)

    @Test
    fun testWavEncoderPrependsCanonical44ByteRiffHeader() {
        val pcmPayload = ByteArray(32000) // 1 second of 16 kHz Mono 16-bit audio
        val wavBytes = WavEncoder.encode(pcmPayload)
        assertEquals(32044, wavBytes.size)
    }

    @Test
    fun testWavHeaderMagicIdentifiersRiffWaveFmtData() {
        val pcmPayload = ByteArray(1024)
        val wavBytes = WavEncoder.encode(pcmPayload)

        val chunkId = String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII)
        val format = String(wavBytes.copyOfRange(8, 12), Charsets.US_ASCII)
        val subchunk1Id = String(wavBytes.copyOfRange(12, 16), Charsets.US_ASCII)
        val subchunk2Id = String(wavBytes.copyOfRange(36, 40), Charsets.US_ASCII)

        assertEquals("RIFF", chunkId)
        assertEquals("WAVE", format)
        assertEquals("fmt ", subchunk1Id)
        assertEquals("data", subchunk2Id)
    }

    @Test
    fun testWavHeaderChunkSizeAndSubchunkSizesLittleEndian() {
        val pcmSize = 6400 // 200ms
        val wavBytes = WavEncoder.encode(ByteArray(pcmSize))

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkSize = buffer.getInt(4)
        val subchunk1Size = buffer.getInt(16)
        val subchunk2Size = buffer.getInt(40)

        assertEquals(pcmSize + 36, chunkSize)
        assertEquals(16, subchunk1Size)
        assertEquals(pcmSize, subchunk2Size)
    }

    @Test
    fun testWavHeaderAudioFormatParametersPcm16khzMono16bit() {
        val wavBytes = WavEncoder.encode(ByteArray(100), sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        val audioFormat = buffer.getShort(20).toInt()
        val numChannels = buffer.getShort(22).toInt()
        val sampleRate = buffer.getInt(24)
        val byteRate = buffer.getInt(28)
        val blockAlign = buffer.getShort(32).toInt()
        val bitsPerSample = buffer.getShort(34).toInt()

        assertEquals(1, audioFormat) // PCM
        assertEquals(1, numChannels) // Mono
        assertEquals(16000, sampleRate)
        assertEquals(32000, byteRate) // 16000 * 1 * 2
        assertEquals(2, blockAlign) // 1 * 16 / 8
        assertEquals(16, bitsPerSample)
    }

    @Test
    fun testWavHeaderPreservesPcmPayloadIntegrity() {
        val testPcm = byteArrayOf(0x10, 0x20, 0x30, 0x40, -0x50, -0x60, 0x7F, -0x80)
        val wavBytes = WavEncoder.encode(testPcm)

        assertEquals(44 + testPcm.size, wavBytes.size)
        val extractedPcm = wavBytes.copyOfRange(44, wavBytes.size)
        for (i in testPcm.indices) {
            assertEquals("Byte $i must match", testPcm[i], extractedPcm[i])
        }
    }

    // Tier 2 Tests: Boundary & Edge Cases (F2 PCM-to-WAV Encoder)

    @Test
    fun testZeroLengthPcmProducesValid44ByteHeader() {
        val wavBytes = WavEncoder.encode(ByteArray(0))
        assertEquals(44, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36, buffer.getInt(4)) // 0 + 36
        assertEquals(16, buffer.getInt(16))
        assertEquals(0, buffer.getInt(40)) // Subchunk2Size = 0
    }

    @Test
    fun testOddByteLengthPcmEncodesCleanly() {
        val oddPcm = ByteArray(301)
        for (i in oddPcm.indices) {
            oddPcm[i] = (i % 128).toByte()
        }
        val wavBytes = WavEncoder.encode(oddPcm)
        assertEquals(345, wavBytes.size) // 44 + 301

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(301 + 36, buffer.getInt(4))
        assertEquals(301, buffer.getInt(40))

        val extracted = wavBytes.copyOfRange(44, wavBytes.size)
        for (i in oddPcm.indices) {
            assertEquals(oddPcm[i], extracted[i])
        }
    }

    @Test
    fun testLargePcmBuffer30SecondsCalculatesCorrectChunkSizes() {
        val maxSpeechBytes = 30 * 32000 // 960,000 bytes
        val wavBytes = WavEncoder.encode(ByteArray(maxSpeechBytes))
        assertEquals(maxSpeechBytes + 44, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(maxSpeechBytes + 36, buffer.getInt(4))
        assertEquals(maxSpeechBytes, buffer.getInt(40))
    }

    @Test
    fun testCustomSampleRates8khz22khz44khz() {
        val rates = listOf(8000, 22050, 44100, 48000)
        for (rate in rates) {
            val wavBytes = WavEncoder.encode(ByteArray(160), sampleRate = rate, channels = 1, bitsPerSample = 16)
            val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

            val expectedByteRate = rate * 1 * 2
            assertEquals(rate, buffer.getInt(24))
            assertEquals(expectedByteRate, buffer.getInt(28))
        }
    }

    @Test
    fun testStereoAndMultiChannelEncoding() {
        val channels = 2
        val wavBytes = WavEncoder.encode(ByteArray(400), sampleRate = 16000, channels = channels, bitsPerSample = 16)
        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(2, buffer.getShort(22).toInt())
        assertEquals(16000 * 2 * 2, buffer.getInt(28)) // ByteRate = 64000
        assertEquals(4, buffer.getShort(32).toInt()) // BlockAlign = 4
    }

    @Test
    fun testNegativeOrZeroSampleRateThrowsException() {
        try {
            WavEncoder.encode(ByteArray(10), sampleRate = 0)
            fail("Expected IllegalArgumentException for sampleRate <= 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("sampleRate"))
        }

        try {
            WavEncoder.encode(ByteArray(10), sampleRate = -16000)
            fail("Expected IllegalArgumentException for negative sampleRate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("sampleRate"))
        }
    }

    @Test
    fun testInvalidChannelsOrBitsPerSampleThrowsException() {
        try {
            WavEncoder.encode(ByteArray(10), channels = 0)
            fail("Expected IllegalArgumentException for channels <= 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("channels"))
        }

        try {
            WavEncoder.encode(ByteArray(10), bitsPerSample = 12)
            fail("Expected IllegalArgumentException for bitsPerSample not multiple of 8")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("bitsPerSample"))
        }
    }
}
