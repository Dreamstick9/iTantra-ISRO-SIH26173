package com.example.itantra.audio

import kotlinx.coroutines.flow.StateFlow

enum class AudioRecordingState {
    IDLE,
    RECORDING,
    PROCESSING,
    ERROR
}

interface AudioEngine {
    val recordingState: StateFlow<AudioRecordingState>
    val audioLevel: StateFlow<Float>

    suspend fun startRecording()
    suspend fun stopRecording(): ByteArray
    fun release() {}
}
