package com.itantra.voice.audio

import com.itantra.voice.fixtures.SarvamMockFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAudioOracleTest {

    // Tier 1 Tests: Feature Coverage for F2 (PCM-to-WAV Encoder)

    @Test
    fun testWavEncoderPrependsCanonical44ByteRiffHeader() {
        val pcmPayload = ByteArray(32000) // 1.0 second of 16 kHz Mono 16-bit PCM
        val wavBytes = SarvamMockFixtures.createCanonicalWav(pcmPayload)
        assertEquals("WAV byte length must equal PCM size + 44 header bytes", 32044, wavBytes.size)
    }

    @Test
    fun testWavHeaderMagicIdentifiersRiffWaveFmtData() {
        val pcmPayload = ByteArray(1024)
        val wavBytes = SarvamMockFixtures.createCanonicalWav(pcmPayload)

        val chunkId = String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII)
        val format = String(wavBytes.copyOfRange(8, 12), Charsets.US_ASCII)
        val subchunk1Id = String(wavBytes.copyOfRange(12, 16), Charsets.US_ASCII)
        val subchunk2Id = String(wavBytes.copyOfRange(36, 40), Charsets.US_ASCII)

        assertEquals("Bytes 0-3 must be 'RIFF'", "RIFF", chunkId)
        assertEquals("Bytes 8-11 must be 'WAVE'", "WAVE", format)
        assertEquals("Bytes 12-15 must be 'fmt '", "fmt ", subchunk1Id)
        assertEquals("Bytes 36-39 must be 'data'", "data", subchunk2Id)
    }

    @Test
    fun testWavHeaderChunkSizeAndSubchunkSizesLittleEndian() {
        val pcmSize = 6400 // 200 ms of audio
        val wavBytes = SarvamMockFixtures.createCanonicalWav(ByteArray(pcmSize))

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        val chunkSize = buffer.getInt(4)
        val subchunk1Size = buffer.getInt(16)
        val subchunk2Size = buffer.getInt(40)

        assertEquals("ChunkSize must equal 36 + PCM length", pcmSize + 36, chunkSize)
        assertEquals("Subchunk1Size must equal 16 for uncompressed PCM", 16, subchunk1Size)
        assertEquals("Subchunk2Size must equal exact PCM length", pcmSize, subchunk2Size)
    }

    @Test
    fun testWavHeaderAudioFormatParametersPcm16khzMono16bit() {
        val wavBytes = SarvamMockFixtures.createCanonicalWav(ByteArray(100), sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        val audioFormat = buffer.getShort(20).toInt()
        val numChannels = buffer.getShort(22).toInt()
        val sampleRate = buffer.getInt(24)
        val byteRate = buffer.getInt(28)
        val blockAlign = buffer.getShort(32).toInt()
        val bitsPerSample = buffer.getShort(34).toInt()

        assertEquals("AudioFormat must be 1 (uncompressed PCM)", 1, audioFormat)
        assertEquals("NumChannels must be 1 (Mono)", 1, numChannels)
        assertEquals("SampleRate must be 16000 Hz", 16000, sampleRate)
        assertEquals("ByteRate must be 32000 (16000 * 1 * 2)", 32000, byteRate)
        assertEquals("BlockAlign must be 2 bytes (1 * 16 / 8)", 2, blockAlign)
        assertEquals("BitsPerSample must be 16", 16, bitsPerSample)
    }

    @Test
    fun testWavHeaderPreservesPcmPayloadIntegrity() {
        val testPcm = byteArrayOf(0x12, 0x34, 0x56, 0x78, -0x10, -0x20)
        val wavBytes = SarvamMockFixtures.createCanonicalWav(testPcm)

        val extractedPcm = wavBytes.copyOfRange(44, wavBytes.size)
        assertEquals("Extracted PCM length must match input", testPcm.size, extractedPcm.size)
        for (i in testPcm.indices) {
            assertEquals("PCM byte at offset $i must match exactly", testPcm[i], extractedPcm[i])
        }
    }

    // Tier 2 Tests: Boundary & Corner Cases for F2 (PCM-to-WAV Encoder)

    @Test
    fun testZeroLengthPcmProducesValid44ByteHeader() {
        val wavBytes = SarvamMockFixtures.createCanonicalWav(ByteArray(0))
        assertEquals("Zero length PCM must produce exactly 44 bytes header", 44, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("ChunkSize for 0 bytes must be 36", 36, buffer.getInt(4))
        assertEquals("Subchunk2Size for 0 bytes must be 0", 0, buffer.getInt(40))
    }

    @Test
    fun testOddByteLengthPcmEncodesCleanly() {
        val oddPcm = ByteArray(301)
        val wavBytes = SarvamMockFixtures.createCanonicalWav(oddPcm)
        assertEquals("WAV size must equal 44 + 301 = 345", 345, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("Subchunk2Size must match exact 301 bytes", 301, buffer.getInt(40))
    }

    @Test
    fun testLargePcmBuffer30SecondsCalculatesCorrectChunkSizes() {
        val maxSpeechBytes = 30 * 32000 // 960,000 bytes (30 seconds)
        val wavBytes = SarvamMockFixtures.createCanonicalWav(ByteArray(maxSpeechBytes))
        assertEquals("WAV length must be 960,044 bytes", maxSpeechBytes + 44, wavBytes.size)

        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("ChunkSize for 30s audio must be 960,036", maxSpeechBytes + 36, buffer.getInt(4))
        assertEquals("Subchunk2Size must be 960,000", maxSpeechBytes, buffer.getInt(40))
    }

    @Test
    fun testCustomSampleRates8khz22khz44khz() {
        val sampleRates = listOf(8000, 22050, 44100, 48000)
        for (rate in sampleRates) {
            val wavBytes = SarvamMockFixtures.createCanonicalWav(ByteArray(100), sampleRate = rate)
            val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

            val expectedByteRate = rate * 1 * 2
            assertEquals("SampleRate must equal configured rate $rate", rate, buffer.getInt(24))
            assertEquals("ByteRate for rate $rate must be $expectedByteRate", expectedByteRate, buffer.getInt(28))
        }
    }

    @Test
    fun testHeaderByteOrderBigEndianVsLittleEndianIntegrity() {
        val wavBytes = SarvamMockFixtures.createCanonicalWav(ByteArray(256))

        // Big Endian bytes: 'R' 'I' 'F' 'F' -> 0x52, 0x49, 0x46, 0x46
        assertEquals(0x52.toByte(), wavBytes[0])
        assertEquals(0x49.toByte(), wavBytes[1])
        assertEquals(0x46.toByte(), wavBytes[2])
        assertEquals(0x46.toByte(), wavBytes[3])

        // Little Endian Subchunk1Size = 16 -> 0x10, 0x00, 0x00, 0x00
        assertEquals(0x10.toByte(), wavBytes[16])
        assertEquals(0x00.toByte(), wavBytes[17])
        assertEquals(0x00.toByte(), wavBytes[18])
        assertEquals(0x00.toByte(), wavBytes[19])
    }
}
