package com.example.itantra.audio

object AudioConfig {
    const val SAMPLE_RATE_HZ = 16000
    const val CHANNEL_COUNT = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = 2
    const val BYTES_PER_SECOND = SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE
    const val FRAME_SIZE_MS = 20
    const val BYTES_PER_FRAME = (SAMPLE_RATE_HZ * FRAME_SIZE_MS / 1000) * BYTES_PER_SAMPLE
}
