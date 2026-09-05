package org.isro.itantra.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-performance audio conversion and utility routines.
 * Zero external dependencies; optimized for low heap allocation and neural model compatibility.
 */
object AudioUtils {

    const val WAV_HEADER_SIZE = 44
    private const val FLOAT_NORM_FACTOR = 32768.0f

    /**
     * Generates a canonical 44-byte RIFF/WAVE header for 16 kHz Mono 16-bit PCM.
     */
    fun generateWavHeader(
        pcmByteLength: Int,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channelCount: Int = AudioConfig.CHANNEL_COUNT,
        bitsPerSample: Int = AudioConfig.BYTES_PER_SAMPLE * 8
    ): ByteArray {
        val header = ByteArray(WAV_HEADER_SIZE)
        val totalDataLen = pcmByteLength + 36
        val byteRate = sampleRate * channelCount * (bitsPerSample / 8)
        val blockAlign = channelCount * (bitsPerSample / 8)

        // "RIFF" chunk descriptor
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // ChunkSize: 36 + SubChunk2Size
        writeIntLe(header, 4, totalDataLen)

        // "WAVE" format
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // "fmt " subchunk marker
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1Size: 16 for PCM
        writeIntLe(header, 16, 16)

        // AudioFormat: 1 = Linear PCM
        writeShortLe(header, 20, 1.toShort())

        // NumChannels
        writeShortLe(header, 22, channelCount.toShort())

        // SampleRate
        writeIntLe(header, 24, sampleRate)

        // ByteRate
        writeIntLe(header, 28, byteRate)

        // BlockAlign
        writeShortLe(header, 32, blockAlign.toShort())

        // BitsPerSample
        writeShortLe(header, 34, bitsPerSample.toShort())

        // "data" subchunk marker
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Subchunk2Size
        writeIntLe(header, 40, pcmByteLength)

        return header
    }

    /**
     * Converts raw 16-bit PCM bytes into a valid standalone WAV byte array.
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = AudioConfig.SAMPLE_RATE
    ): ByteArray {
        val header = generateWavHeader(pcmData.size, sampleRate)
        val wav = ByteArray(WAV_HEADER_SIZE + pcmData.size)
        System.arraycopy(header, 0, wav, 0, WAV_HEADER_SIZE)
        System.arraycopy(pcmData, 0, wav, WAV_HEADER_SIZE, pcmData.size)
        return wav
    }

    /**
     * Validates if a byte array contains a valid 44-byte RIFF/WAVE header.
     */
    fun isValidWavHeader(headerBytes: ByteArray): Boolean {
        if (headerBytes.size < WAV_HEADER_SIZE) return false
        val isRiff = headerBytes[0] == 'R'.code.toByte() &&
                headerBytes[1] == 'I'.code.toByte() &&
                headerBytes[2] == 'F'.code.toByte() &&
                headerBytes[3] == 'F'.code.toByte()
        val isWave = headerBytes[8] == 'W'.code.toByte() &&
                headerBytes[9] == 'A'.code.toByte() &&
                headerBytes[10] == 'V'.code.toByte() &&
                headerBytes[11] == 'E'.code.toByte()
        val isFmt = headerBytes[12] == 'f'.code.toByte() &&
                headerBytes[13] == 'm'.code.toByte() &&
                headerBytes[14] == 't'.code.toByte() &&
                headerBytes[15] == ' '.code.toByte()
        val isData = headerBytes[36] == 'd'.code.toByte() &&
                headerBytes[37] == 'a'.code.toByte() &&
                headerBytes[38] == 't'.code.toByte() &&
                headerBytes[39] == 'a'.code.toByte()
        return isRiff && isWave && isFmt && isData
    }

    /**
     * Converts Little-Endian 16-bit PCM bytes to ShortArray.
     */
    fun pcm16ToShorts(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset
    ): ShortArray {
        val sampleCount = length / 2
        val outShorts = ShortArray(sampleCount)
        var byteIdx = offset
        for (i in 0 until sampleCount) {
            val lsb = pcmBytes[byteIdx].toInt() and 0xFF
            val msb = pcmBytes[byteIdx + 1].toInt() and 0xFF
            outShorts[i] = ((msb shl 8) or lsb).toShort()
            byteIdx += 2
        }
        return outShorts
    }

    /**
     * Converts ShortArray back to Little-Endian 16-bit PCM ByteArray.
     */
    fun shortsToPcm16(
        shorts: ShortArray,
        offset: Int = 0,
        length: Int = shorts.size - offset
    ): ByteArray {
        val outBytes = ByteArray(length * 2)
        var byteIdx = 0
        for (i in offset until offset + length) {
            val sample = shorts[i].toInt()
            outBytes[byteIdx] = (sample and 0xFF).toByte()
            outBytes[byteIdx + 1] = ((sample ushr 8) and 0xFF).toByte()
            byteIdx += 2
        }
        return outBytes
    }

    /**
     * Converts 16-bit PCM byte array directly to FloatArray normalized to [-1.0f, 1.0f].
     * Standard input format for neural ASR (Whisper, sherpa-onnx, Conformer, Silero VAD).
     */
    fun pcm16ToFloats(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset
    ): FloatArray {
        val sampleCount = length / 2
        val outFloats = FloatArray(sampleCount)
        var byteIdx = offset
        for (i in 0 until sampleCount) {
            val lsb = pcmBytes[byteIdx].toInt() and 0xFF
            val msb = pcmBytes[byteIdx + 1].toInt() and 0xFF
            val sample = ((msb shl 8) or lsb).toShort()
            outFloats[i] = sample / FLOAT_NORM_FACTOR
            byteIdx += 2
        }
        return outFloats
    }

    /**
     * Converts normalized FloatArray [-1.0f, 1.0f] directly to 16-bit Little-Endian PCM byte array.
     */
    fun floatsToPcm16(
        floats: FloatArray,
        offset: Int = 0,
        length: Int = floats.size - offset
    ): ByteArray {
        val outBytes = ByteArray(length * 2)
        var byteIdx = 0
        for (i in offset until offset + length) {
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

    /**
     * Calculates normalized RMS [0.0, 1.0] from raw 16-bit PCM bytes.
     */
    fun calculateRmsFromPcm16(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset
    ): Float {
        val sampleCount = length / 2
        if (sampleCount == 0) return 0.0f

        var sumSquares = 0.0
        var byteIdx = offset
        for (i in 0 until sampleCount) {
            val lsb = pcmBytes[byteIdx].toInt() and 0xFF
            val msb = pcmBytes[byteIdx + 1].toInt() and 0xFF
            val sample = ((msb shl 8) or lsb).toShort().toDouble()
            sumSquares += sample * sample
            byteIdx += 2
        }

        val rmsRaw = sqrt(sumSquares / sampleCount)
        return (rmsRaw / FLOAT_NORM_FACTOR).toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * Converts normalized RMS to perceptual UI visualizer level [0.0, 1.0].
     * Applies logarithmic decibel scale so normal conversational speech (-30 to -15 dB)
     * renders cleanly on UI meters.
     */
    fun calculateVisualizerLevel(
        rms: Float,
        noiseFloorDb: Float = -50.0f,
        maxDb: Float = -6.0f
    ): Float {
        if (rms <= 1e-6f) return 0.0f
        val db = (20.0 * log10(rms.toDouble())).toFloat().coerceIn(noiseFloorDb, 0.0f)
        if (db <= noiseFloorDb) return 0.0f
        return ((db - noiseFloorDb) / (maxDb - noiseFloorDb)).coerceIn(0.0f, 1.0f)
    }

    private fun writeIntLe(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeShortLe(buffer: ByteArray, offset: Int, value: Short) {
        buffer[offset] = (value.toInt() and 0xFF).toByte()
        buffer[offset + 1] = ((value.toInt() ushr 8) and 0xFF).toByte()
    }
}
