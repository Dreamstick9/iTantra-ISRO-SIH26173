package com.example.itantra.tts

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the offline on-device neural Text-To-Speech engine.
 * AudioData must remain local.
 */
interface TtsEngine {
    val isModelLoaded: StateFlow<Boolean>
    val playbackState: StateFlow<TtsPlaybackState>
    val lastLatencyMs: StateFlow<Long>
    val lastRtf: StateFlow<Double>
    val lastDurationMs: StateFlow<Long>
    val errorMessage: StateFlow<String?>

    suspend fun synthesize(text: String): AudioData
    suspend fun speak(text: String)
    suspend fun stop()
    fun release() {}
}
