package org.isro.itantra.audio

import java.util.concurrent.ArrayBlockingQueue

/**
 * High-performance, zero-allocation buffer pool for 16 kHz PCM audio chunks.
 * Eliminates GC pressure in real-time streaming audio pipelines.
 */
class AudioBufferPool(
    private val bufferSize: Int = AudioConfig.FRAME_SIZE_BYTES,
    capacity: Int = 16
) {
    private val pool = ArrayBlockingQueue<ByteArray>(capacity)

    init {
        for (i in 0 until capacity) {
            pool.offer(ByteArray(bufferSize))
        }
    }

    /**
     * Obtains a recycled buffer from the pool, or allocates a new one if exhausted.
     */
    fun acquire(): ByteArray {
        return pool.poll() ?: ByteArray(bufferSize)
    }

    /**
     * Recycles a buffer back into the pool.
     */
    fun release(buffer: ByteArray) {
        if (buffer.size == bufferSize) {
            pool.offer(buffer)
        }
    }
}
