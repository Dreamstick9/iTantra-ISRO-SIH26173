package com.example.itantra.tts

/**
 * Local generated audio data containing neural acoustic waveform samples,
 * sample rate, raw 16-bit linear PCM byte buffer, and duration.
 *
 * AudioData must remain strictly local to the device and never leak over external networks.
 */
data class AudioData(
    val samples: FloatArray,
    val sampleRate: Int,
    val rawPcm: ByteArray,
    val durationMs: Long
) {
    val sampleCount: Int get() = samples.size
    val isEmpty: Boolean get() = samples.isEmpty() || rawPcm.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioData
        return samples.contentEquals(other.samples) &&
                sampleRate == other.sampleRate &&
                rawPcm.contentEquals(other.rawPcm) &&
                durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + rawPcm.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }

    companion object {
        val EMPTY = AudioData(
            samples = FloatArray(0),
            sampleRate = 16000,
            rawPcm = ByteArray(0),
            durationMs = 0L
        )

        /**
         * Converts normalized FloatArray [-1.0f, 1.0f] to 16-bit linear PCM little-endian ByteArray.
         */
        fun floatArrayToPcm16Le(samples: FloatArray): ByteArray {
            if (samples.isEmpty()) return ByteArray(0)
            val pcm = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val clamped = samples[i].coerceIn(-1.0f, 1.0f)
                val s = if (clamped >= 0f) {
                    (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                } else {
                    (clamped * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
                }
                pcm[i * 2] = (s.toInt() and 0xFF).toByte()
                pcm[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
            }
            return pcm
        }

        /**
         * Converts 16-bit linear PCM little-endian ByteArray to normalized FloatArray [-1.0f, 1.0f].
         */
        fun pcm16LeToFloatArray(pcm: ByteArray): FloatArray {
            if (pcm.isEmpty()) return FloatArray(0)
            val sampleCount = pcm.size / 2
            val floats = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                val bLo = pcm[i * 2].toInt() and 0xFF
                val bHi = pcm[i * 2 + 1].toInt()
                val s = ((bHi shl 8) or bLo).toShort()
                floats[i] = s / 32768.0f
            }
            return floats
        }
    }
}
