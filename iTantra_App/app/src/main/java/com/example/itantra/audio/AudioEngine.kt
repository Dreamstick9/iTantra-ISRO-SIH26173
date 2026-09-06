package com.example.itantra.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioEngine {
    val audioLevel: StateFlow<Float>
    val isRecording: Boolean
    val isPlaying: Boolean

    fun initialize(): Result<Unit>
    fun startRecording(): Result<Unit>
    fun stopRecording(): ByteArray
    fun playAudio(pcmData: ByteArray, sampleRate: Int = AudioConfig.SAMPLE_RATE_HZ, isEmergency: Boolean = false): Result<Unit>
    fun stopPlayback()
    fun release()
}
