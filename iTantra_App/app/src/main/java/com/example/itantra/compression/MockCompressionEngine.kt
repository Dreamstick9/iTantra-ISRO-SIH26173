package com.example.itantra.compression

import com.example.itantra.util.Logger

class MockCompressionEngine : CompressionEngine {
    private val TAG = "MockCompression"

    override fun compress(input: ByteArray): ByteArray {
        Logger.d(TAG, "Mock compress: ${input.size} bytes -> ${input.size} bytes")
        return input.copyOf()
    }

    override fun decompress(compressed: ByteArray): ByteArray {
        Logger.d(TAG, "Mock decompress: ${compressed.size} bytes -> ${compressed.size} bytes")
        return compressed.copyOf()
    }

    override fun compressText(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        Logger.d(TAG, "Mock compressText: ${text.length} chars -> ${bytes.size} bytes")
        return bytes
    }

    override fun decompressText(compressed: ByteArray): String {
        val text = String(compressed, Charsets.UTF_8)
        Logger.d(TAG, "Mock decompressText: ${compressed.size} bytes -> ${text.length} chars")
        return text
    }
}
