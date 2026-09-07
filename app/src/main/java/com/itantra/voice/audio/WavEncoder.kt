package com.itantra.voice.audio

/**
 * In-memory pure Kotlin PCM-to-WAV converter.
 * Prepends a canonical 44-byte RIFF header formatted for 16 kHz Mono 16-bit PCM.
 */
object WavEncoder {

    const val HEADER_SIZE = 44
    const val DEFAULT_SAMPLE_RATE = 16000
    const val DEFAULT_CHANNELS = 1
    const val DEFAULT_BITS_PER_SAMPLE = 16

    /**
     * Encodes raw linear PCM audio bytes into a canonical 44-byte RIFF WAV byte array.
     *
     * @param pcmData Raw PCM audio samples (16-bit linear PCM)
     * @param sampleRate Sampling rate in Hz (default: 16000 Hz)
     * @param channels Number of audio channels (default: 1 for Mono)
     * @param bitsPerSample Bit depth per sample (default: 16 bits)
     * @return Canonical 44-byte RIFF WAV formatted byte array
     */
    fun encode(
        pcmData: ByteArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channels: Int = DEFAULT_CHANNELS,
        bitsPerSample: Int = DEFAULT_BITS_PER_SAMPLE
    ): ByteArray {
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(channels > 0) { "channels must be positive: $channels" }
        require(bitsPerSample > 0 && bitsPerSample % 8 == 0) {
            "bitsPerSample must be a positive multiple of 8: $bitsPerSample"
        }

        val audioLength = pcmData.size
        val totalDataLen = audioLength + 36
        val bytesPerSample = bitsPerSample / 8
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample

        val header = ByteArray(HEADER_SIZE)

        // 00-03: 'RIFF' chunk descriptor (Big Endian ASCII)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // 04-07: Total file size - 8 bytes (32-bit Little Endian)
        header[4] = (totalDataLen and 0xFF).toByte()
        header[5] = ((totalDataLen shr 8) and 0xFF).toByte()
        header[6] = ((totalDataLen shr 16) and 0xFF).toByte()
        header[7] = ((totalDataLen shr 24) and 0xFF).toByte()

        // 08-11: 'WAVE' format (Big Endian ASCII)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // 12-15: 'fmt ' subchunk header (Big Endian ASCII)
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // 16-19: Subchunk1Size = 16 for PCM (32-bit Little Endian)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // 20-21: AudioFormat = 1 for uncompressed PCM (16-bit Little Endian)
        header[20] = 1
        header[21] = 0

        // 22-23: NumChannels (16-bit Little Endian)
        header[22] = (channels and 0xFF).toByte()
        header[23] = ((channels shr 8) and 0xFF).toByte()

        // 24-27: SampleRate (32-bit Little Endian)
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()

        // 28-31: ByteRate = SampleRate * NumChannels * BitsPerSample / 8 (32-bit Little Endian)
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()

        // 32-33: BlockAlign = NumChannels * BitsPerSample / 8 (16-bit Little Endian)
        header[32] = (blockAlign and 0xFF).toByte()
        header[33] = ((blockAlign shr 8) and 0xFF).toByte()

        // 34-35: BitsPerSample (16-bit Little Endian)
        header[34] = (bitsPerSample and 0xFF).toByte()
        header[35] = ((bitsPerSample shr 8) and 0xFF).toByte()

        // 36-39: 'data' subchunk header (Big Endian ASCII)
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // 40-43: Subchunk2Size = raw audio length (32-bit Little Endian)
        header[40] = (audioLength and 0xFF).toByte()
        header[41] = ((audioLength shr 8) and 0xFF).toByte()
        header[42] = ((audioLength shr 16) and 0xFF).toByte()
        header[43] = ((audioLength shr 24) and 0xFF).toByte()

        val wavBytes = ByteArray(HEADER_SIZE + audioLength)
        System.arraycopy(header, 0, wavBytes, 0, HEADER_SIZE)
        if (audioLength > 0) {
            System.arraycopy(pcmData, 0, wavBytes, HEADER_SIZE, audioLength)
        }
        return wavBytes
    }
}
