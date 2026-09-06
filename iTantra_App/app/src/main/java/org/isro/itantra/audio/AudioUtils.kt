package org.isro.itantra.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-performance, zero-dependency audio conversion, WAV header packaging, and DSP routines.
 *
 * Design Guarantees:
 * 1. Zero External Dependencies: Pure Kotlin standard library (no android.*, no javax.*).
 * 2. Strict Mathematical Normalization: Symmetrical 32768.0f scaling conforming to
 *    sherpa-onnx (k2-fsa), PyTorch torchaudio, and Silero VAD specifications.
 * 3. Exact Round-Trip Identity: 100% loss-free roundtrip across all 65,536 Short values.
 * 4. Zero-Allocation Streaming Overloads: In-place buffer destinations for 50 Hz frame loops.
 * 5. Robust Bounds & Sanity: NaN protection and 32-bit signed RIFF size overflow prevention.
 */
object AudioUtils {

    const val WAV_HEADER_SIZE = 44
    const val FLOAT_NORM_FACTOR = 32768.0f
    private const val MAX_PCM_HEADER_PAYLOAD = Int.MAX_VALUE - 36

    // =========================================================================
    // 1. CANONICAL 44-BYTE RIFF/WAVE HEADER GENERATOR & PARSER
    // =========================================================================

    /**
     * Writes a canonical 44-byte RIFF/WAVE header directly into [buffer] starting at [offset].
     * Avoids heap allocation; ideal for high-throughput streaming and file sinks.
     *
     * @return Number of header bytes written (always 44).
     */
    fun writeWavHeader(
        buffer: ByteArray,
        offset: Int = 0,
        pcmByteLength: Int,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channelCount: Int = AudioConfig.CHANNEL_COUNT,
        bitsPerSample: Int = AudioConfig.BITS_PER_SAMPLE
    ): Int {
        require(pcmByteLength >= 0) { "pcmByteLength cannot be negative: $pcmByteLength" }
        require(pcmByteLength <= MAX_PCM_HEADER_PAYLOAD) { "pcmByteLength exceeds WAV 32-bit chunk limit: $pcmByteLength" }
        require(offset >= 0 && offset + WAV_HEADER_SIZE <= buffer.size) {
            "Buffer too small: capacity=${buffer.size}, required=${offset + WAV_HEADER_SIZE}"
        }
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(channelCount > 0) { "channelCount must be positive: $channelCount" }
        require(bitsPerSample == 8 || bitsPerSample == 16 || bitsPerSample == 24 || bitsPerSample == 32) {
            "Unsupported bitsPerSample: $bitsPerSample (expected 8, 16, 24, 32)"
        }

        val bytesPerSample = bitsPerSample / 8
        val byteRate = sampleRate * channelCount * bytesPerSample
        val blockAlign = channelCount * bytesPerSample
        val totalDataLen = pcmByteLength + 36

        // 0..3: "RIFF" chunk descriptor
        buffer[offset] = 'R'.code.toByte()
        buffer[offset + 1] = 'I'.code.toByte()
        buffer[offset + 2] = 'F'.code.toByte()
        buffer[offset + 3] = 'F'.code.toByte()

        // 4..7: ChunkSize = 36 + SubChunk2Size (Little-Endian)
        writeIntLe(buffer, offset + 4, totalDataLen)

        // 8..11: "WAVE" format identifier
        buffer[offset + 8] = 'W'.code.toByte()
        buffer[offset + 9] = 'A'.code.toByte()
        buffer[offset + 10] = 'V'.code.toByte()
        buffer[offset + 11] = 'E'.code.toByte()

        // 12..15: "fmt " subchunk marker
        buffer[offset + 12] = 'f'.code.toByte()
        buffer[offset + 13] = 'm'.code.toByte()
        buffer[offset + 14] = 't'.code.toByte()
        buffer[offset + 15] = ' '.code.toByte()

        // 16..19: Subchunk1Size = 16 for Linear PCM
        writeIntLe(buffer, offset + 16, 16)

        // 20..21: AudioFormat: 1 = Uncompressed Linear PCM
        writeShortLe(buffer, offset + 20, 1.toShort())

        // 22..23: NumChannels
        writeShortLe(buffer, offset + 22, channelCount.toShort())

        // 24..27: SampleRate (e.g., 16000)
        writeIntLe(buffer, offset + 24, sampleRate)

        // 28..31: ByteRate = SampleRate * NumChannels * BitsPerSample / 8 (e.g., 32000)
        writeIntLe(buffer, offset + 28, byteRate)

        // 32..33: BlockAlign = NumChannels * BitsPerSample / 8 (e.g., 2)
        writeShortLe(buffer, offset + 32, blockAlign.toShort())

        // 34..35: BitsPerSample (e.g., 16)
        writeShortLe(buffer, offset + 34, bitsPerSample.toShort())

        // 36..39: "data" subchunk marker
        buffer[offset + 36] = 'd'.code.toByte()
        buffer[offset + 37] = 'a'.code.toByte()
        buffer[offset + 38] = 't'.code.toByte()
        buffer[offset + 39] = 'a'.code.toByte()

        // 40..43: Subchunk2Size (raw PCM byte count)
        writeIntLe(buffer, offset + 40, pcmByteLength)

        return WAV_HEADER_SIZE
    }

    /**
     * Allocates and returns a standalone canonical 44-byte RIFF/WAVE header ByteArray.
     */
    fun generateWavHeader(
        pcmByteLength: Int,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channelCount: Int = AudioConfig.CHANNEL_COUNT,
        bitsPerSample: Int = AudioConfig.BITS_PER_SAMPLE
    ): ByteArray {
        val header = ByteArray(WAV_HEADER_SIZE)
        writeWavHeader(header, 0, pcmByteLength, sampleRate, channelCount, bitsPerSample)
        return header
    }

    /**
     * Converts a raw PCM byte array into a complete, standalone WAV byte array.
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channelCount: Int = AudioConfig.CHANNEL_COUNT,
        bitsPerSample: Int = AudioConfig.BITS_PER_SAMPLE
    ): ByteArray {
        val wav = ByteArray(WAV_HEADER_SIZE + pcmData.size)
        writeWavHeader(wav, 0, pcmData.size, sampleRate, channelCount, bitsPerSample)
        pcmData.copyInto(destination = wav, destinationOffset = WAV_HEADER_SIZE, startIndex = 0, endIndex = pcmData.size)
        return wav
    }

    /**
     * Validates whether [headerBytes] contains a standard 44-byte canonical PCM RIFF/WAVE header.
     */
    fun isValidWavHeader(headerBytes: ByteArray, offset: Int = 0): Boolean {
        if (headerBytes.size - offset < WAV_HEADER_SIZE) return false

        // Check "RIFF"
        val isRiff = headerBytes[offset] == 'R'.code.toByte() &&
                headerBytes[offset + 1] == 'I'.code.toByte() &&
                headerBytes[offset + 2] == 'F'.code.toByte() &&
                headerBytes[offset + 3] == 'F'.code.toByte()

        // Check "WAVE"
        val isWave = headerBytes[offset + 8] == 'W'.code.toByte() &&
                headerBytes[offset + 9] == 'A'.code.toByte() &&
                headerBytes[offset + 10] == 'V'.code.toByte() &&
                headerBytes[offset + 11] == 'E'.code.toByte()

        // Check "fmt "
        val isFmt = headerBytes[offset + 12] == 'f'.code.toByte() &&
                headerBytes[offset + 13] == 'm'.code.toByte() &&
                headerBytes[offset + 14] == 't'.code.toByte() &&
                headerBytes[offset + 15] == ' '.code.toByte()

        // AudioFormat at offset + 20 must be 1 (PCM)
        val formatCode = readShortLe(headerBytes, offset + 20)

        // Check "data"
        val isData = headerBytes[offset + 36] == 'd'.code.toByte() &&
                headerBytes[offset + 37] == 'a'.code.toByte() &&
                headerBytes[offset + 38] == 't'.code.toByte() &&
                headerBytes[offset + 39] == 'a'.code.toByte()

        return isRiff && isWave && isFmt && isData && (formatCode == 1.toShort())
    }

    /**
     * Parsed WAV metadata descriptor.
     */
    data class WavMetadata(
        val sampleRate: Int,
        val channelCount: Int,
        val bitsPerSample: Int,
        val byteRate: Int,
        val blockAlign: Int,
        val pcmDataOffset: Int,
        val pcmByteLength: Int,
        val durationMs: Long,
        val durationSeconds: Double
    )

    /**
     * Parses audio format and metadata from a WAV header byte array.
     */
    fun parseWavHeader(headerBytes: ByteArray, offset: Int = 0): WavMetadata? {
        if (!isValidWavHeader(headerBytes, offset)) return null
        val channelCount = readShortLe(headerBytes, offset + 22).toInt() and 0xFFFF
        val sampleRate = readIntLe(headerBytes, offset + 24)
        val byteRate = readIntLe(headerBytes, offset + 28)
        val blockAlign = readShortLe(headerBytes, offset + 32).toInt() and 0xFFFF
        val bitsPerSample = readShortLe(headerBytes, offset + 34).toInt() and 0xFFFF
        val pcmByteLength = readIntLe(headerBytes, offset + 40)

        val durationMs = AudioConfig.bytesToDurationMs(
            pcmByteLength.toLong(),
            sampleRate = sampleRate,
            channelCount = channelCount,
            bytesPerSample = bitsPerSample / 8
        )
        val durationSeconds = AudioConfig.bytesToDurationSeconds(
            pcmByteLength.toLong(),
            sampleRate = sampleRate,
            channelCount = channelCount,
            bytesPerSample = bitsPerSample / 8
        )

        return WavMetadata(
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitsPerSample = bitsPerSample,
            byteRate = byteRate,
            blockAlign = blockAlign,
            pcmDataOffset = offset + WAV_HEADER_SIZE,
            pcmByteLength = pcmByteLength,
            durationMs = durationMs,
            durationSeconds = durationSeconds
        )
    }

    // =========================================================================
    // 2. PCM 16-BIT BYTE ARRAY <-> SHORT ARRAY
    // =========================================================================

    /**
     * Converts Little-Endian 16-bit PCM bytes to ShortArray directly into [outShorts].
     * @return Number of samples written to [outShorts].
     */
    fun pcm16ToShorts(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset,
        outShorts: ShortArray,
        outOffset: Int = 0
    ): Int {
        if (length <= 0 || offset >= pcmBytes.size) return 0
        val safeLength = minOf(length, pcmBytes.size - offset)
        val sampleCount = safeLength / 2
        require(outOffset + sampleCount <= outShorts.size) {
            "Destination outShorts capacity exceeded: capacity=${outShorts.size}, required=${outOffset + sampleCount}"
        }

        var byteIdx = offset
        for (i in 0 until sampleCount) {
            val lsb = pcmBytes[byteIdx].toInt() and 0xFF
            val msb = pcmBytes[byteIdx + 1].toInt()
            outShorts[outOffset + i] = ((msb shl 8) or lsb).toShort()
            byteIdx += 2
        }
        return sampleCount
    }

    /** Allocating overload: Little-Endian 16-bit PCM bytes to ShortArray */
    fun pcm16ToShorts(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset
    ): ShortArray {
        if (length <= 0 || offset >= pcmBytes.size) return ShortArray(0)
        val safeLength = minOf(length, pcmBytes.size - offset)
        val outShorts = ShortArray(safeLength / 2)
        pcm16ToShorts(pcmBytes, offset, safeLength, outShorts, 0)
        return outShorts
    }

    /**
     * Converts ShortArray back to Little-Endian 16-bit PCM bytes directly into [outBytes].
     * @return Number of bytes written to [outBytes].
     */
    fun shortsToPcm16(
        shorts: ShortArray,
        offset: Int = 0,
        length: Int = shorts.size - offset,
        outBytes: ByteArray,
        outOffset: Int = 0
    ): Int {
        if (length <= 0 || offset >= shorts.size) return 0
        val safeLength = minOf(length, shorts.size - offset)
        val byteCount = safeLength * 2
        require(outOffset + byteCount <= outBytes.size) {
            "Destination outBytes capacity exceeded: capacity=${outBytes.size}, required=${outOffset + byteCount}"
        }

        var byteIdx = outOffset
        for (i in offset until offset + safeLength) {
            val sample = shorts[i].toInt()
            outBytes[byteIdx] = (sample and 0xFF).toByte()
            outBytes[byteIdx + 1] = ((sample ushr 8) and 0xFF).toByte()
            byteIdx += 2
        }
        return byteCount
    }

    /** Allocating overload: ShortArray to Little-Endian 16-bit PCM bytes */
    fun shortsToPcm16(
        shorts: ShortArray,
        offset: Int = 0,
        length: Int = shorts.size - offset
    ): ByteArray {
        if (length <= 0 || offset >= shorts.size) return ByteArray(0)
        val safeLength = minOf(length, shorts.size - offset)
        val outBytes = ByteArray(safeLength * 2)
        shortsToPcm16(shorts, offset, safeLength, outBytes, 0)
        return outBytes
    }

    // =========================================================================
    // 3. PCM 16-BIT BYTE ARRAY <-> FLOAT ARRAY ([-1.0f, 1.0f] NORMALIZATION)
    // =========================================================================

    /**
     * Converts Little-Endian 16-bit PCM bytes directly to FloatArray [-1.0f, 1.0f] into [outFloats].
     * Mathematical standard for neural models (sherpa-onnx, Vosk, Whisper, Silero VAD).
     *
     * @return Number of float samples written to [outFloats].
     */
    fun pcm16ToFloats(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset,
        outFloats: FloatArray,
        outOffset: Int = 0
    ): Int {
        if (length <= 0 || offset >= pcmBytes.size) return 0
        val safeLength = minOf(length, pcmBytes.size - offset)
        val sampleCount = safeLength / 2
        require(outOffset + sampleCount <= outFloats.size) {
            "Destination outFloats capacity exceeded: capacity=${outFloats.size}, required=${outOffset + sampleCount}"
        }

        var byteIdx = offset
        for (i in 0 until sampleCount) {
            val lsb = pcmBytes[byteIdx].toInt() and 0xFF
            val msb = pcmBytes[byteIdx + 1].toInt()
            val sample = (msb shl 8) or lsb
            outFloats[outOffset + i] = sample / FLOAT_NORM_FACTOR
            byteIdx += 2
        }
        return sampleCount
    }

    /** Allocating overload: Little-Endian 16-bit PCM bytes to FloatArray [-1.0f, 1.0f] */
    fun pcm16ToFloats(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset
    ): FloatArray {
        if (length <= 0 || offset >= pcmBytes.size) return FloatArray(0)
        val safeLength = minOf(length, pcmBytes.size - offset)
        val outFloats = FloatArray(safeLength / 2)
        pcm16ToFloats(pcmBytes, offset, safeLength, outFloats, 0)
        return outFloats
    }

    /**
     * Converts normalized FloatArray [-1.0f, 1.0f] to Little-Endian 16-bit PCM into [outBytes].
     * Features branchless linear scaling, NaN protection, and clipping to [-32768, 32767].
     *
     * @return Number of bytes written to [outBytes].
     */
    fun floatsToPcm16(
        floats: FloatArray,
        offset: Int = 0,
        length: Int = floats.size - offset,
        outBytes: ByteArray,
        outOffset: Int = 0
    ): Int {
        if (length <= 0 || offset >= floats.size) return 0
        val safeLength = minOf(length, floats.size - offset)
        val byteCount = safeLength * 2
        require(outOffset + byteCount <= outBytes.size) {
            "Destination outBytes capacity exceeded: capacity=${outBytes.size}, required=${outOffset + byteCount}"
        }

        var byteIdx = outOffset
        for (i in offset until offset + safeLength) {
            val f = floats[i]
            val clamped = if (f.isNaN()) 0.0f else f.coerceIn(-1.0f, 1.0f)
            val sample = (clamped * FLOAT_NORM_FACTOR).roundToInt().coerceIn(-32768, 32767)

            outBytes[byteIdx] = (sample and 0xFF).toByte()
            outBytes[byteIdx + 1] = ((sample ushr 8) and 0xFF).toByte()
            byteIdx += 2
        }
        return byteCount
    }

    /** Allocating overload: FloatArray [-1.0f, 1.0f] to Little-Endian 16-bit PCM bytes */
    fun floatsToPcm16(
        floats: FloatArray,
        offset: Int = 0,
        length: Int = floats.size - offset
    ): ByteArray {
        if (length <= 0 || offset >= floats.size) return ByteArray(0)
        val safeLength = minOf(length, floats.size - offset)
        val outBytes = ByteArray(safeLength * 2)
        floatsToPcm16(floats, offset, safeLength, outBytes, 0)
        return outBytes
    }

    // =========================================================================
    // 4. DIRECT SHORT ARRAY <-> FLOAT ARRAY ([-1.0f, 1.0f] BRIDGE)
    // =========================================================================

    /**
     * Directly converts ShortArray (from AudioRecord) to FloatArray (for sherpa-onnx) into [outFloats].
     * Eliminates intermediate ByteArray allocation.
     *
     * @return Number of float samples written to [outFloats].
     */
    fun shortsToFloats(
        shorts: ShortArray,
        offset: Int = 0,
        length: Int = shorts.size - offset,
        outFloats: FloatArray,
        outOffset: Int = 0
    ): Int {
        if (length <= 0 || offset >= shorts.size) return 0
        val safeLength = minOf(length, shorts.size - offset)
        require(outOffset + safeLength <= outFloats.size) {
            "Destination outFloats capacity exceeded: capacity=${outFloats.size}, required=${outOffset + safeLength}"
        }

        for (i in 0 until safeLength) {
            outFloats[outOffset + i] = shorts[offset + i] / FLOAT_NORM_FACTOR
        }
        return safeLength
    }

    /** Allocating overload: ShortArray to FloatArray [-1.0f, 1.0f] */
    fun shortsToFloats(
        shorts: ShortArray,
        offset: Int = 0,
        length: Int = shorts.size - offset
    ): FloatArray {
        if (length <= 0 || offset >= shorts.size) return FloatArray(0)
        val safeLength = minOf(length, shorts.size - offset)
        val outFloats = FloatArray(safeLength)
        shortsToFloats(shorts, offset, safeLength, outFloats, 0)
        return outFloats
    }

    /**
     * Directly converts FloatArray (from sherpa-onnx TTS) to ShortArray (for AudioTrack) into [outShorts].
     * Eliminates intermediate ByteArray allocation.
     *
     * @return Number of short samples written to [outShorts].
     */
    fun floatsToShorts(
        floats: FloatArray,
        offset: Int = 0,
        length: Int = floats.size - offset,
        outShorts: ShortArray,
        outOffset: Int = 0
    ): Int {
        if (length <= 0 || offset >= floats.size) return 0
        val safeLength = minOf(length, floats.size - offset)
        require(outOffset + safeLength <= outShorts.size) {
            "Destination outShorts capacity exceeded: capacity=${outShorts.size}, required=${outOffset + safeLength}"
        }

        for (i in 0 until safeLength) {
            val f = floats[offset + i]
            val clamped = if (f.isNaN()) 0.0f else f.coerceIn(-1.0f, 1.0f)
            val sample = (clamped * FLOAT_NORM_FACTOR).roundToInt().coerceIn(-32768, 32767)
            outShorts[outOffset + i] = sample.toShort()
        }
        return safeLength
    }

    /** Allocating overload: FloatArray [-1.0f, 1.0f] to ShortArray */
    fun floatsToShorts(
        floats: FloatArray,
        offset: Int = 0,
        length: Int = floats.size - offset
    ): ShortArray {
        if (length <= 0 || offset >= floats.size) return ShortArray(0)
        val safeLength = minOf(length, floats.size - offset)
        val outShorts = ShortArray(safeLength)
        floatsToShorts(floats, offset, safeLength, outShorts, 0)
        return outShorts
    }

    // =========================================================================
    // 5. AUDIO DSP UTILITIES (RMS, GAIN, PEAK NORMALIZATION & DECIBELS)
    // =========================================================================

    /**
     * Calculates normalized RMS energy [0.0, 1.0] from raw 16-bit PCM bytes.
     */
    fun calculateRmsFromPcm16(
        pcmBytes: ByteArray,
        offset: Int = 0,
        length: Int = pcmBytes.size - offset
    ): Float {
        if (length <= 0 || offset >= pcmBytes.size) return 0.0f
        val safeLength = minOf(length, pcmBytes.size - offset)
        val sampleCount = safeLength / 2
        if (sampleCount == 0) return 0.0f

        var sumSquares = 0.0
        var byteIdx = offset
        for (i in 0 until sampleCount) {
            val lsb = pcmBytes[byteIdx].toInt() and 0xFF
            val msb = pcmBytes[byteIdx + 1].toInt()
            val sample = (msb shl 8) or lsb
            sumSquares += sample.toDouble() * sample.toDouble()
            byteIdx += 2
        }

        val rmsRaw = sqrt(sumSquares / sampleCount)
        return (rmsRaw / FLOAT_NORM_FACTOR).toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculates normalized RMS energy [0.0, 1.0] from FloatArray samples.
     */
    fun calculateRmsFromFloats(
        floats: FloatArray,
        offset: Int = 0,
        length: Int = floats.size - offset
    ): Float {
        if (length <= 0 || offset >= floats.size) return 0.0f
        val safeLength = minOf(length, floats.size - offset)
        var sumSquares = 0.0
        for (i in offset until offset + safeLength) {
            val s = floats[i].toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / safeLength).toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * Converts normalized RMS to a perceptual UI visualizer level [0.0, 1.0].
     * Maps log10 dBFS values to a linear [0.0, 1.0] meter scale.
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

    /**
     * Applies linear volume gain with hard saturation clipping protection to FloatArray.
     */
    fun applyGain(
        floats: FloatArray,
        gainFactor: Float,
        clipProtection: Boolean = true
    ): FloatArray {
        require(gainFactor >= 0.0f) { "gainFactor must be non-negative: $gainFactor" }
        val out = FloatArray(floats.size)
        for (i in floats.indices) {
            val scaled = floats[i] * gainFactor
            out[i] = if (clipProtection) scaled.coerceIn(-1.0f, 1.0f) else scaled
        }
        return out
    }

    /**
     * Peak normalizes an audio float array to full scale [-1.0f, 1.0f].
     * Vital for emergency broadcast alerts and distant mic gain boost.
     */
    fun normalizePeak(floats: FloatArray, targetPeak: Float = 1.0f): FloatArray {
        if (floats.isEmpty()) return floats
        require(targetPeak in 0.0f..1.0f) { "targetPeak must be in range [0.0, 1.0]" }

        var maxAmp = 0.0f
        for (sample in floats) {
            val mag = abs(sample)
            if (mag > maxAmp) maxAmp = mag
        }
        if (maxAmp <= 1e-6f || maxAmp >= targetPeak) return floats.copyOf()

        val gain = targetPeak / maxAmp
        return applyGain(floats, gain, clipProtection = true)
    }

    // =========================================================================
    // 6. PRIVATE LITTLE-ENDIAN BIT SHUFFLING ROUTINES
    // =========================================================================

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

    private fun readIntLe(buffer: ByteArray, offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShortLe(buffer: ByteArray, offset: Int): Short {
        val lsb = buffer[offset].toInt() and 0xFF
        val msb = buffer[offset + 1].toInt()
        return ((msb shl 8) or lsb).toShort()
    }
}
