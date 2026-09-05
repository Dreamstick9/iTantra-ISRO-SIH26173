package org.isro.itantra.tts

/**
 * Standard output contract from Milestone 2 Neural TTS engines.
 * Contains normalized float PCM samples in range [-1.0f, 1.0f] and the model's native sample rate.
 */
data class GeneratedAudio(
    val samples: FloatArray,
    val sampleRate: Int = 22050
) {
    val durationMs: Long
        get() = if (sampleRate > 0) (samples.size * 1000L) / sampleRate else 0L

    val totalBytes: Long
        get() = (samples.size * 2).toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GeneratedAudio
        return samples.contentEquals(other.samples) && sampleRate == other.sampleRate
    }

    override fun hashCode(): Int = 31 * samples.contentHashCode() + sampleRate
}
