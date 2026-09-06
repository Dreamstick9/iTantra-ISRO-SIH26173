package com.example.itantra.compression

import com.example.itantra.util.Logger

class Unishox2CompressionEngine : CompressionEngine {

    companion object {
        private const val TAG = "Unishox2Engine"
        const val MAGIC_UNISHOX2: Byte = 0x55 // 'U'
        const val MAGIC_RAW_UTF8: Byte = 0x52 // 'R'
    }

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        return try {
            val compressed = Unishox2.compress(input)
            if (compressed.isNotEmpty() && compressed.size < input.size) {
                ByteArray(compressed.size + 1).apply {
                    this[0] = MAGIC_UNISHOX2
                    System.arraycopy(compressed, 0, this, 1, compressed.size)
                }
            } else {
                ByteArray(input.size + 1).apply {
                    this[0] = MAGIC_RAW_UTF8
                    System.arraycopy(input, 0, this, 1, input.size)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Unishox2 compression error, falling back to raw: ${e.message}", e)
            ByteArray(input.size + 1).apply {
                this[0] = MAGIC_RAW_UTF8
                System.arraycopy(input, 0, this, 1, input.size)
            }
        }
    }

    override fun decompress(compressed: ByteArray): ByteArray {
        if (compressed.isEmpty()) return ByteArray(0)
        return try {
            when (compressed[0]) {
                MAGIC_UNISHOX2 -> {
                    val payload = compressed.copyOfRange(1, compressed.size)
                    Unishox2.decompress(payload)
                }
                MAGIC_RAW_UTF8 -> {
                    compressed.copyOfRange(1, compressed.size)
                }
                else -> {
                    // Legacy uncompressed bytes without magic header
                    compressed
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Unishox2 decompression error: ${e.message}", e)
            if (compressed.isNotEmpty() && compressed[0] == MAGIC_RAW_UTF8) {
                compressed.copyOfRange(1, compressed.size)
            } else {
                compressed
            }
        }
    }

    override fun compressText(text: String): ByteArray {
        return compress(text.toByteArray(Charsets.UTF_8))
    }

    override fun decompressText(compressed: ByteArray): String {
        val decompressed = decompress(compressed)
        return String(decompressed, Charsets.UTF_8)
    }
}
