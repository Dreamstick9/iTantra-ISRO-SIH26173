package org.isro.itantra.audio

import org.junit.Assert.assertEquals
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
        assertEquals("Byte rate must be 32,000 B/s (256 kbps)", 32000, AudioConfig.BYTE_RATE)
    }

    @Test
    fun testDurationAndByteConversions() {
        val durationMs = 2500L // 2.5 seconds
        val bytes = AudioConfig.durationMsToBytes(durationMs)
        assertEquals(80000L, bytes) // 2.5s * 32,000 = 80,000 bytes

        val convertedBackMs = AudioConfig.bytesToDurationMs(bytes)
        assertEquals(durationMs, convertedBackMs)
    }
}
