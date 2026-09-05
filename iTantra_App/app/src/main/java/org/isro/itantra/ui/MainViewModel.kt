package org.isro.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.isro.itantra.audio.AudioConfig
import org.isro.itantra.audio.AudioPlayer
import org.isro.itantra.audio.AudioRecorder
import org.isro.itantra.telemetry.LatencyTracker
import org.isro.itantra.telemetry.TelemetryStats

enum class PttState {
    IDLE,
    RECORDING,
    PLAYING
}

class MainViewModel : ViewModel() {

    private val latencyTracker = LatencyTracker()
    val telemetryStats: StateFlow<TelemetryStats> = latencyTracker.stats

    private val _pttState = MutableStateFlow(PttState.IDLE)
    val pttState = _pttState.asStateFlow()

    private val _amplitude = MutableStateFlow(0.0f)
    val amplitude = _amplitude.asStateFlow()

    private val _lastRecordedPcm = MutableStateFlow<ByteArray?>(null)
    val lastRecordedPcm = _lastRecordedPcm.asStateFlow()

    private val _hasRecordedAudio = MutableStateFlow(false)
    val hasRecordedAudio = _hasRecordedAudio.asStateFlow()

    private val audioRecorder = AudioRecorder(
        onAmplitudeChanged = { level ->
            _amplitude.value = level
        },
        onFirstChunkCaptured = {
            latencyTracker.onFirstPcmBufferEmitted()
        }
    )

    private val audioPlayer = AudioPlayer(
        onPlaybackStarted = {
            _pttState.value = PttState.PLAYING
            latencyTracker.onPlaybackStarted()
        },
        onPlaybackFinished = {
            _pttState.value = PttState.IDLE
        }
    )

    fun onPttDown() {
        if (_pttState.value != PttState.IDLE) return

        latencyTracker.onPttPressed()
        val started = audioRecorder.startRecording(viewModelScope)
        if (started) {
            _pttState.value = PttState.RECORDING
        }
    }

    fun onPttUp() {
        if (_pttState.value != PttState.RECORDING) return

        val pcmData = audioRecorder.stopRecording()
        _amplitude.value = 0.0f

        if (pcmData.isNotEmpty()) {
            _lastRecordedPcm.value = pcmData
            _hasRecordedAudio.value = true
            val durationMs = AudioConfig.bytesToDurationMs(pcmData.size.toLong())
            latencyTracker.onPttReleased(pcmData.size.toLong(), durationMs)

            // Milestone 1 Verification Loop: immediately play back what was recorded
            playAudio(pcmData)
        } else {
            _pttState.value = PttState.IDLE
        }
    }

    fun onPttCancel() {
        if (_pttState.value == PttState.RECORDING) {
            audioRecorder.stopRecording()
            _amplitude.value = 0.0f
            _pttState.value = PttState.IDLE
        }
    }

    fun replayLastAudio() {
        val pcm = _lastRecordedPcm.value ?: return
        if (_pttState.value == PttState.IDLE) {
            playAudio(pcm)
        }
    }

    private fun playAudio(pcmData: ByteArray) {
        audioPlayer.playPcm(viewModelScope, pcmData)
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
    }
}
