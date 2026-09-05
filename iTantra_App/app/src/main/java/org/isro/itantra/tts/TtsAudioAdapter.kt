package org.isro.itantra.tts

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Audio format adapter bridging Neural TTS FloatArray outputs with 16-bit PCM AudioTrack.
 */
object TtsAudioAdapter {

    fun floatsToPcm16(
        floats: FloatArray,
        offset: Int = 0,
        length: Int = floats.size - offset
    ): ByteArray {
        if (length <= 0 || offset >= floats.size) return ByteArray(0)
        val actualLength = minOf(length, floats.size - offset)
        val outBytes = ByteArray(actualLength * 2)
        var byteIdx = 0
        for (i in offset until offset + actualLength) {
            val clamped = floats[i].coerceIn(-1.0f, 1.0f)
            val sample = if (clamped < 0f) {
                (clamped * 32768.0f).roundToInt().coerceIn(-32768, 32767)
            } else {
                (clamped * 32767.0f).roundToInt().coerceIn(-32768, 32767)
            }
            outBytes[byteIdx] = (sample and 0xFF).toByte()
            outBytes[byteIdx + 1] = ((sample ushr 8) and 0xFF).toByte()
            byteIdx += 2
        }
        return outBytes
    }

    fun pcm16ToFloats(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset
    ): FloatArray {
        if (length <= 0 || offset >= pcmBytes.size) return FloatArray(0)
        val actualLength = minOf(length, pcmBytes.size - offset)
        val sampleCount = actualLength / 2
        val outFloats = FloatArray(sampleCount)
        var byteIdx = offset
        for (i in 0 until sampleCount) {
            val lsb = pcmBytes[byteIdx].toInt() and 0xFF
            val msb = pcmBytes[byteIdx + 1].toInt() and 0xFF
            val sample = ((msb shl 8) or lsb).toShort()
            outFloats[i] = if (sample < 0) {
                sample / 32768.0f
            } else {
                sample / 32767.0f
            }
            byteIdx += 2
        }
        return outFloats
    }

    fun applyVolume(
        floats: FloatArray,
        volumeFactor: Float,
        clipProtection: Boolean = true
    ): FloatArray {
        require(volumeFactor >= 0.0f) { "volumeFactor must be >= 0" }
        val out = FloatArray(floats.size)
        for (i in floats.indices) {
            var scaled = floats[i] * volumeFactor
            if (clipProtection) {
                scaled = scaled.coerceIn(-1.0f, 1.0f)
            }
            out[i] = scaled
        }
        return out
    }

    fun applyEmergencyAlertVolume(floats: FloatArray): FloatArray {
        if (floats.isEmpty()) return floats
        var peak = 0.0f
        for (sample in floats) {
            val mag = abs(sample)
            if (mag > peak) peak = mag
        }
        if (peak <= 1e-6f) return floats.copyOf()
        val gain = 1.0f / peak
        return applyVolume(floats, gain, clipProtection = true)
    }

    fun samplesToDurationMs(sampleCount: Long, sampleRate: Int): Long {
        if (sampleRate <= 0) return 0L
        return (sampleCount * 1000L) / sampleRate
    }

    fun durationMsToSamples(durationMs: Long, sampleRate: Int): Long {
        if (sampleRate <= 0) return 0L
        return (durationMs * sampleRate) / 1000L
    }

    fun pcmBytesToDurationMs(
        byteCount: Long,
        sampleRate: Int,
        channelCount: Int = 1,
        bytesPerSample: Int = 2
    ): Long {
        val byteRate = sampleRate.toLong() * channelCount * bytesPerSample
        if (byteRate <= 0L) return 0L
        return (byteCount * 1000L) / byteRate
    }

    fun durationMsToPcmBytes(
        durationMs: Long,
        sampleRate: Int,
        channelCount: Int = 1,
        bytesPerSample: Int = 2
    ): Long {
        val byteRate = sampleRate.toLong() * channelCount * bytesPerSample
        return (durationMs * byteRate) / 1000L
    }
}
