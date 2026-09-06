package com.example.itantra.compression

interface CompressionEngine {
    fun compress(input: ByteArray): ByteArray
    fun decompress(compressed: ByteArray): ByteArray
    fun compressText(text: String): ByteArray
    fun decompressText(compressed: ByteArray): String
}
