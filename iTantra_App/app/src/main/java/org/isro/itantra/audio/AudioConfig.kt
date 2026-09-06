package org.isro.itantra.audio

/**
 * Standard Audio Configuration for Speech AI Pipelines (sherpa-onnx, Vosk, Silero VAD).
 *
 * 100% Zero-Dependency Pure Kotlin (No Android SDK dependencies).
 * Fully compatible with Kotlin Multiplatform (JVM, Android, Native, Desktop).
 */
object AudioConfig {

    /** Standard sample rate required by speech foundation models (16 kHz) */
    const val SAMPLE_RATE = 16000

    /** Single channel (Mono) */
    const val CHANNEL_COUNT = 1

    /** 16-bit signed Linear PCM */
    const val BITS_PER_SAMPLE = 16

    /** 2 bytes per 16-bit sample */
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8

    /** Standard 20 ms audio frame duration for real-time VAD and streaming neural ASR */
    const val FRAME_DURATION_MS = 20

    /** 320 samples per 20 ms frame @ 16 kHz */
    const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000

    /** 640 bytes per 20 ms frame @ 16 kHz 16-bit mono */
    const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * BYTES_PER_SAMPLE

    /** 32,000 bytes/second uncompressed baseline (256 kbps) */
    const val BYTE_RATE = SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE

    // =========================================================================
    // DURATION & BYTE CALCULATIONS (PURE MATH)
    // =========================================================================

    /**
     * Converts raw PCM byte count to duration in milliseconds.
     */
    fun bytesToDurationMs(
        byteCount: Long,
        sampleRate: Int = SAMPLE_RATE,
        channelCount: Int = CHANNEL_COUNT,
        bytesPerSample: Int = BYTES_PER_SAMPLE
    ): Long {
        val byteRate = sampleRate.toLong() * channelCount * bytesPerSample
        if (byteRate <= 0L || byteCount <= 0L) return 0L
        return (byteCount * 1000L) / byteRate
    }

    /**
     * Converts raw PCM byte count to duration in fractional seconds (high precision).
     */
    fun bytesToDurationSeconds(
        byteCount: Long,
        sampleRate: Int = SAMPLE_RATE,
        channelCount: Int = CHANNEL_COUNT,
        bytesPerSample: Int = BYTES_PER_SAMPLE
    ): Double {
        val byteRate = sampleRate.toDouble() * channelCount * bytesPerSample
        if (byteRate <= 0.0 || byteCount <= 0L) return 0.0
        return byteCount / byteRate
    }

    /**
     * Converts duration in milliseconds to expected raw PCM byte count.
     */
    fun durationMsToBytes(
        durationMs: Long,
        sampleRate: Int = SAMPLE_RATE,
        channelCount: Int = CHANNEL_COUNT,
        bytesPerSample: Int = BYTES_PER_SAMPLE
    ): Long {
        if (durationMs <= 0L) return 0L
        val byteRate = sampleRate.toLong() * channelCount * bytesPerSample
        return (durationMs * byteRate) / 1000L
    }

    /**
     * Converts duration in fractional seconds to expected raw PCM byte count.
     */
    fun durationSecondsToBytes(
        durationSeconds: Double,
        sampleRate: Int = SAMPLE_RATE,
        channelCount: Int = CHANNEL_COUNT,
        bytesPerSample: Int = BYTES_PER_SAMPLE
    ): Long {
        if (durationSeconds <= 0.0) return 0L
        val byteRate = sampleRate.toDouble() * channelCount * bytesPerSample
        return (durationSeconds * byteRate).toLong()
    }

    /**
     * Converts sample count to duration in milliseconds.
     */
    fun samplesToDurationMs(sampleCount: Long, sampleRate: Int = SAMPLE_RATE): Long {
        if (sampleRate <= 0 || sampleCount <= 0L) return 0L
        return (sampleCount * 1000L) / sampleRate
    }

    /**
     * Converts sample count to duration in fractional seconds.
     */
    fun samplesToDurationSeconds(sampleCount: Long, sampleRate: Int = SAMPLE_RATE): Double {
        if (sampleRate <= 0 || sampleCount <= 0L) return 0.0
        return sampleCount.toDouble() / sampleRate
    }

    /**
     * Converts duration in milliseconds to sample count.
     */
    fun durationMsToSamples(durationMs: Long, sampleRate: Int = SAMPLE_RATE): Long {
        if (sampleRate <= 0 || durationMs <= 0L) return 0L
        return (durationMs * sampleRate.toLong()) / 1000L
    }

    /**
     * Converts duration in fractional seconds to sample count.
     */
    fun durationSecondsToSamples(durationSeconds: Double, sampleRate: Int = SAMPLE_RATE): Long {
        if (sampleRate <= 0 || durationSeconds <= 0.0) return 0L
        return (durationSeconds * sampleRate).toLong()
    }

    /**
     * Converts sample count to PCM byte count.
     */
    fun samplesToBytes(
        sampleCount: Long,
        channelCount: Int = CHANNEL_COUNT,
        bytesPerSample: Int = BYTES_PER_SAMPLE
    ): Long {
        if (sampleCount <= 0L) return 0L
        return sampleCount * channelCount * bytesPerSample
    }

    /**
     * Converts PCM byte count to sample count.
     */
    fun bytesToSamples(
        byteCount: Long,
        channelCount: Int = CHANNEL_COUNT,
        bytesPerSample: Int = BYTES_PER_SAMPLE
    ): Long {
        val bytesPerFrame = channelCount * bytesPerSample
        if (bytesPerFrame <= 0 || byteCount <= 0L) return 0L
        return byteCount / bytesPerFrame
    }
}
