package com.example.itantra.speech

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Little-Endian 16-bit PCM to Float32 conversion and audio duration/sample count calculations.
 */
class PcmAudioConversionTest {

    private val floatDelta = 0.0001f

    @Test
    fun testSilenceConversion() {
        // 16-bit PCM silence: 0x0000 -> 0.0f
        val silenceBytes = byteArrayOf(0x00.toByte(), 0x00.toByte())
        val floats = PcmAudioConverter.pcm16LeToFloatArray(silenceBytes)

        assertEquals(1, floats.size)
        assertEquals(0.0f, floats[0], floatDelta)
    }

    @Test
    fun testMaxPositiveConversion() {
        // 16-bit signed max positive: 0x7FFF = 32767
        // In little-endian: low byte = 0xFF, high byte = 0x7F
        // 32767 / 32768.0f = ~0.999969f
        val maxPositiveBytes = byteArrayOf(0xFF.toByte(), 0x7F.toByte())
        val floats = PcmAudioConverter.pcm16LeToFloatArray(maxPositiveBytes)

        assertEquals(1, floats.size)
        assertEquals(0.99996948f, floats[0], floatDelta)
        assertTrue("Max positive float must be <= 1.0f", floats[0] <= 1.0f)
        assertTrue("Max positive float must be > 0.999f", floats[0] > 0.999f)
    }

    @Test
    fun testMaxNegativeConversion() {
        // 16-bit signed max negative: 0x8000 = -32768
        // In little-endian: low byte = 0x00, high byte = 0x80
        // -32768 / 32768.0f = -1.0f
        val maxNegativeBytes = byteArrayOf(0x00.toByte(), 0x80.toByte())
        val floats = PcmAudioConverter.pcm16LeToFloatArray(maxNegativeBytes)

        assertEquals(1, floats.size)
        assertEquals(-1.0f, floats[0], floatDelta)
        assertTrue("Max negative float must be >= -1.0f", floats[0] >= -1.0f)
    }

    @Test
    fun testIntermediateValues() {
        // Test +0.5: 16384 (0x4000) -> low = 0x00, high = 0x40
        val halfPositive = byteArrayOf(0x00.toByte(), 0x40.toByte())
        val floatsHalfPos = PcmAudioConverter.pcm16LeToFloatArray(halfPositive)
        assertEquals(1, floatsHalfPos.size)
        assertEquals(0.5f, floatsHalfPos[0], floatDelta)

        // Test -0.5: -16384 (0xC000) -> low = 0x00, high = 0xC0
        val halfNegative = byteArrayOf(0x00.toByte(), 0xC0.toByte())
        val floatsHalfNeg = PcmAudioConverter.pcm16LeToFloatArray(halfNegative)
        assertEquals(1, floatsHalfNeg.size)
        assertEquals(-0.5f, floatsHalfNeg[0], floatDelta)

        // Test +0.25: 8192 (0x2000) -> low = 0x00, high = 0x20
        val quarterPositive = byteArrayOf(0x00.toByte(), 0x20.toByte())
        val floatsQuarter = PcmAudioConverter.pcm16LeToFloatArray(quarterPositive)
        assertEquals(1, floatsQuarter.size)
        assertEquals(0.25f, floatsQuarter[0], floatDelta)
    }

    @Test
    fun testMultipleSequentialSamples() {
        // Concatenate silence, max positive, max negative, half positive
        val multiSampleBytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), // 0.0f
            0xFF.toByte(), 0x7F.toByte(), // ~0.999969f
            0x00.toByte(), 0x80.toByte(), // -1.0f
            0x00.toByte(), 0x40.toByte()  // 0.5f
        )
        val floats = PcmAudioConverter.pcm16LeToFloatArray(multiSampleBytes)

        assertEquals(4, floats.size)
        assertEquals(0.0f, floats[0], floatDelta)
        assertEquals(0.99996948f, floats[1], floatDelta)
        assertEquals(-1.0f, floats[2], floatDelta)
        assertEquals(0.5f, floats[3], floatDelta)

        // Verify bounds [-1.0f, 1.0f] for all samples
        for (sample in floats) {
            assertTrue("Sample $sample must be >= -1.0f", sample >= -1.0f)
            assertTrue("Sample $sample must be <= 1.0f", sample <= 1.0f)
        }
    }

    @Test
    fun testEmptyByteArrayHandling() {
        // Empty byte array (size 0) must return safely without IndexOutOfBoundsException
        val emptyBytes = byteArrayOf()
        val floats = PcmAudioConverter.pcm16LeToFloatArray(emptyBytes)

        assertNotNull(floats)
        assertEquals(0, floats.size)
    }

    @Test
    fun testNullByteArrayHandling() {
        // Null byte array must return empty FloatArray safely
        val floats = PcmAudioConverter.pcm16LeToFloatArray(null)

        assertNotNull(floats)
        assertEquals(0, floats.size)
    }

    @Test
    fun testOddLengthByteArrayHandling() {
        // Single byte (incomplete sample): sampleCount = 1 / 2 = 0
        val singleByte = byteArrayOf(0x7F.toByte())
        val floatsSingle = PcmAudioConverter.pcm16LeToFloatArray(singleByte)
        assertNotNull(floatsSingle)
        assertEquals(0, floatsSingle.size)

        // 3 bytes: 1 valid sample (0x00, 0x00) + 1 trailing byte (0x7F)
        val threeBytes = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x7F.toByte())
        val floatsThree = PcmAudioConverter.pcm16LeToFloatArray(threeBytes)
        assertEquals(1, floatsThree.size)
        assertEquals(0.0f, floatsThree[0], floatDelta)

        // 5 bytes: 2 valid samples (silence, max pos) + 1 trailing odd byte
        val fiveBytes = byteArrayOf(
            0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0x7F.toByte(),
            0x42.toByte()
        )
        val floatsFive = PcmAudioConverter.pcm16LeToFloatArray(fiveBytes)
        assertEquals(2, floatsFive.size)
        assertEquals(0.0f, floatsFive[0], floatDelta)
        assertEquals(0.99996948f, floatsFive[1], floatDelta)
    }

    @Test
    fun testDurationAndSampleCountCalculations() {
        // Sample count calculation for 16-bit PCM (2 bytes per sample)
        assertEquals(0, PcmAudioConverter.calculateSampleCount(0))
        assertEquals(0, PcmAudioConverter.calculateSampleCount(1))
        assertEquals(1, PcmAudioConverter.calculateSampleCount(2))
        assertEquals(1, PcmAudioConverter.calculateSampleCount(3))
        assertEquals(2, PcmAudioConverter.calculateSampleCount(4))
        assertEquals(16000, PcmAudioConverter.calculateSampleCount(32000))
        assertEquals(0, PcmAudioConverter.calculateSampleCount(-10))

        // Duration in ms: 16000 Hz, 1 channel, 16-bit (32000 bytes/sec)
        // 32000 bytes = 1000 ms
        assertEquals(1000L, PcmAudioConverter.calculateDurationMs(32000, sampleRate = 16000))
        // 64000 bytes = 2000 ms
        assertEquals(2000L, PcmAudioConverter.calculateDurationMs(64000, sampleRate = 16000))
        // 640 bytes = 20 ms (standard 20ms audio frame)
        assertEquals(20L, PcmAudioConverter.calculateDurationMs(640, sampleRate = 16000))
        // 0 bytes = 0 ms
        assertEquals(0L, PcmAudioConverter.calculateDurationMs(0, sampleRate = 16000))
        // Negative bytes = 0 ms
        assertEquals(0L, PcmAudioConverter.calculateDurationMs(-500, sampleRate = 16000))

        // Duration in seconds
        assertEquals(1.0f, PcmAudioConverter.calculateDurationSeconds(32000, sampleRate = 16000), floatDelta)
        assertEquals(0.5f, PcmAudioConverter.calculateDurationSeconds(16000, sampleRate = 16000), floatDelta)
        assertEquals(0.0f, PcmAudioConverter.calculateDurationSeconds(0, sampleRate = 16000), floatDelta)
    }

    @Test
    fun testSherpaOnnxSpeechEngineCompanionEquivalence() {
        // Verify SherpaOnnxSpeechEngine companion helper delegates to PcmAudioConverter accurately
        val pcm = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0x7F.toByte())
        val fromCompanion = SherpaOnnxSpeechEngine.pcm16LeToFloatArray(pcm)
        val fromConverter = PcmAudioConverter.pcm16LeToFloatArray(pcm)

        assertArrayEquals(fromConverter, fromCompanion, floatDelta)
        assertEquals(
            PcmAudioConverter.calculateSampleCount(pcm.size),
            SherpaOnnxSpeechEngine.calculateSampleCount(pcm.size)
        )
        assertEquals(
            PcmAudioConverter.calculateDurationMs(pcm.size),
            SherpaOnnxSpeechEngine.calculateDurationMs(pcm.size)
        )
    }
}
