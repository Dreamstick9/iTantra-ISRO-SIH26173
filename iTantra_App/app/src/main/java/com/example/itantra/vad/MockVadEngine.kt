package com.example.itantra.vad

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Deterministic Mock implementation of VadEngine for unit testing.
 */
class MockVadEngine : VadEngine {

    private val _isModelLoaded = MutableStateFlow(true)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _loadErrorMessage = MutableStateFlow<String?>(null)
    override val loadErrorMessage: StateFlow<String?> = _loadErrorMessage.asStateFlow()

    private var speechDetected: Boolean = false
    private val segmentsQueue = ArrayDeque<FloatArray>()

    var acceptedWaveformsCount: Int = 0
        private set
    var resetCount: Int = 0
        private set
    var released: Boolean = false
        private set

    override fun initialize(): Result<Unit> {
        _isModelLoaded.value = true
        return Result.success(Unit)
    }

    override fun acceptWaveform(samples: FloatArray) {
        acceptedWaveformsCount++
    }

    override fun isSpeechDetected(): Boolean = speechDetected

    fun setSpeechDetected(detected: Boolean) {
        speechDetected = detected
    }

    override fun hasSegment(): Boolean = segmentsQueue.isNotEmpty()

    fun pushSegment(segment: FloatArray) {
        segmentsQueue.add(segment)
    }

    override fun popSegment(): FloatArray? {
        return if (segmentsQueue.isNotEmpty()) segmentsQueue.poll() else null
    }

    override fun reset() {
        resetCount++
        speechDetected = false
        segmentsQueue.clear()
    }

    override fun release() {
        released = true
        _isModelLoaded.value = false
    }
}
