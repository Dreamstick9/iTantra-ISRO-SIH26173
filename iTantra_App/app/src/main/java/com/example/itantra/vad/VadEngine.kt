package com.example.itantra.vad

import kotlinx.coroutines.flow.StateFlow

/**
 * Common abstraction for offline Voice Activity Detection.
 */
interface VadEngine {
    val isModelLoaded: StateFlow<Boolean>
    val loadErrorMessage: StateFlow<String?>

    /**
     * Initialize or ensure the underlying VAD model is loaded.
     */
    fun initialize(): Result<Unit>

    /**
     * Feeds incoming 16kHz mono audio samples into the VAD processor.
     * Typically fed in chunks of 512 samples (32 ms).
     */
    fun acceptWaveform(samples: FloatArray)

    /**
     * Returns true if speech is currently detected by the VAD model.
     */
    fun isSpeechDetected(): Boolean

    /**
     * Returns true if the VAD has detected an end of utterance and has a segmented speech segment ready.
     */
    fun hasSegment(): Boolean

    /**
     * Retrieves and pops the completed speech segment if available, or null.
     */
    fun popSegment(): FloatArray?

    /**
     * Resets the internal state of the VAD detector for the next utterance.
     */
    fun reset()

    /**
     * Releases any native model memory and resources.
     */
    fun release()
}
