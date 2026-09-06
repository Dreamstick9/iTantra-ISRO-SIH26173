package com.example.itantra.speech

import com.example.itantra.audio.AudioConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility object for PCM audio operations and format conversions for offline STT engines.
 * Converts 16-bit signed Little-Endian PCM audio bytes into normalized 32-bit Float arrays [-1.0f, 1.0f].
 */
object PcmAudioConverter {

    /**
     * Converts a 16-bit mono Little-Endian PCM ByteArray into a normalized FloatArray [-1.0f, 1.0f].
     * Divides each signed 16-bit integer by 32768.0f.
     *
     * Safely handles:
     * - null audio: returns empty FloatArray
     * - empty audio (0 bytes): returns empty FloatArray without IndexOutOfBoundsException
     * - odd-length audio: discards trailing incomplete byte safely
     */
    fun pcm16LeToFloatArray(audio: ByteArray?): FloatArray {
        if (audio == null || audio.isEmpty()) {
            return FloatArray(0)
        }
        val sampleCount = audio.size / 2
        if (sampleCount == 0) {
            return FloatArray(0)
        }

        val samples = FloatArray(sampleCount)
        val buffer = ByteBuffer.wrap(audio, 0, sampleCount * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        for (i in 0 until sampleCount) {
            samples[i] = buffer.get(i) / 32768.0f
        }
        return samples
    }

    /**
     * Alias for pcm16LeToFloatArray.
     */
    fun toFloat32Array(audio: ByteArray?): FloatArray = pcm16LeToFloatArray(audio)

    /**
     * Calculate sample count from byte count for 16-bit mono audio (2 bytes per sample).
     */
    fun calculateSampleCount(byteCount: Int): Int {
        if (byteCount <= 0) return 0
        return byteCount / 2
    }

    /**
     * Calculate duration in milliseconds from byte count and sample rate (default 16kHz mono 16-bit).
     */
    fun calculateDurationMs(
        byteCount: Int,
        sampleRate: Int = AudioConfig.SAMPLE_RATE_HZ,
        channelCount: Int = AudioConfig.CHANNEL_COUNT
    ): Long {
        if (byteCount <= 0 || sampleRate <= 0 || channelCount <= 0) return 0L
        val bytesPerSample = AudioConfig.BYTES_PER_SAMPLE // 2
        val bytesPerSecond = sampleRate.toLong() * channelCount * bytesPerSample
        return (byteCount.toLong() * 1000L) / bytesPerSecond
    }

    /**
     * Calculate duration in seconds from byte count and sample rate.
     */
    fun calculateDurationSeconds(
        byteCount: Int,
        sampleRate: Int = AudioConfig.SAMPLE_RATE_HZ,
        channelCount: Int = AudioConfig.CHANNEL_COUNT
    ): Float {
        if (byteCount <= 0 || sampleRate <= 0 || channelCount <= 0) return 0f
        val bytesPerSample = AudioConfig.BYTES_PER_SAMPLE // 2
        val bytesPerSecond = sampleRate.toFloat() * channelCount * bytesPerSample
        return byteCount.toFloat() / bytesPerSecond
    }
}
