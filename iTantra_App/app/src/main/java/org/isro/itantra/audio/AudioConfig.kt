package org.isro.itantra.audio

import android.media.AudioFormat

/**
 * Standard Audio Configuration for iTantra Neural Transceiver.
 *
 * All neural models (AI4Bharat IndicConformer, sherpa-onnx, Vosk, Silero VAD)
 * require 16 kHz Mono 16-bit signed Linear PCM.
 */
object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2
    const val CHANNEL_COUNT = 1

    /** 20 ms frame duration: standard window for VAD & streaming neural ASR */
    const val FRAME_DURATION_MS = 20

    /** 320 samples per 20 ms frame @ 16 kHz */
    const val FRAME_SIZE_SAMPLES = (SAMPLE_RATE * FRAME_DURATION_MS) / 1000

    /** 640 bytes per 20 ms frame @ 16 kHz 16-bit mono */
    const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * BYTES_PER_SAMPLE

    /** 32,000 bytes/second (256 kbps uncompressed baseline) */
    const val BYTE_RATE = SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE

    /** Convert PCM byte count to duration in milliseconds */
    fun bytesToDurationMs(byteCount: Long): Long {
        return (byteCount * 1000L) / BYTE_RATE
    }

    /** Convert duration in milliseconds to expected raw PCM byte count */
    fun durationMsToBytes(durationMs: Long): Long {
        return (durationMs * BYTE_RATE) / 1000L
    }
}
