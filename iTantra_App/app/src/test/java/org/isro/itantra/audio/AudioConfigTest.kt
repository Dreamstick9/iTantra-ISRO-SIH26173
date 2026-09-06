package org.isro.itantra.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioConfigTest {

    @Test
    fun testAudioConfigConstants() {
        assertEquals("Sample rate must be 16kHz for speech models", 16000, AudioConfig.SAMPLE_RATE)
        assertEquals("Bytes per sample must be 2 for 16-bit PCM", 2, AudioConfig.BYTES_PER_SAMPLE)
        assertEquals("Channels must be 1 for Mono", 1, AudioConfig.CHANNEL_COUNT)
        assertEquals("Frame duration must be 20ms", 20, AudioConfig.FRAME_DURATION_MS)
        assertEquals("20ms frame must have 320 samples", 320, AudioConfig.FRAME_SIZE_SAMPLES)
        assertEquals("20ms frame must have 640 bytes", 640, AudioConfig.FRAME_SIZE_BYTES)
        assertEquals("Byte rate must be 32,000 bytes/sec", 32000, AudioConfig.BYTE_RATE)
    }

    @Test
    fun testDurationAndByteCalculations() {
        // 20 ms frame @ 16 kHz Mono 16-bit
        assertEquals(640L, AudioConfig.durationMsToBytes(20L))
        assertEquals(20L, AudioConfig.bytesToDurationMs(640L))
        assertEquals(0.02, AudioConfig.bytesToDurationSeconds(640L), 1e-6)

        // 320 samples @ 16 kHz
        assertEquals(20L, AudioConfig.samplesToDurationMs(320L))
        assertEquals(320L, AudioConfig.durationMsToSamples(20L))
        assertEquals(640L, AudioConfig.samplesToBytes(320L))
        assertEquals(320L, AudioConfig.bytesToSamples(640L))

        // 1.0 second (32,000 bytes)
        assertEquals(32000L, AudioConfig.durationMsToBytes(1000L))
        assertEquals(1000L, AudioConfig.bytesToDurationMs(32000L))
        assertEquals(1.0, AudioConfig.bytesToDurationSeconds(32000L), 1e-6)

        // 2.5 seconds (80,000 bytes)
        assertEquals(80000L, AudioConfig.durationSecondsToBytes(2.5))
        assertEquals(2.5, AudioConfig.bytesToDurationSeconds(80000L), 1e-6)
    }

    @Test
    fun testEdgeCasesAndZeroProtection() {
        assertEquals(0L, AudioConfig.bytesToDurationMs(0L))
        assertEquals(0L, AudioConfig.bytesToDurationMs(-100L))
        assertEquals(0.0, AudioConfig.bytesToDurationSeconds(0L), 1e-6)
        assertEquals(0.0, AudioConfig.bytesToDurationSeconds(-100L), 1e-6)
        assertEquals(0L, AudioConfig.durationMsToBytes(0L))
        assertEquals(0L, AudioConfig.durationMsToBytes(-50L))
        assertEquals(0L, AudioConfig.samplesToDurationMs(0L))
        assertEquals(0L, AudioConfig.samplesToDurationMs(-10L))
    }
}
