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
import org.isro.itantra.telemetry.TransceiverMode
import org.isro.itantra.tts.EmergencyPreset
import org.isro.itantra.tts.SupportedLanguage
import org.isro.itantra.tts.TtsEngine

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

    // --- Milestone 2: Neural TTS State ---
    private val ttsEngine = TtsEngine()

    private val _selectedLanguage = MutableStateFlow(SupportedLanguage.HINDI)
    val selectedLanguage = _selectedLanguage.asStateFlow()

    private val _ttsInputText = MutableStateFlow("चक्रवात चेतावनी! तुरंत सुरक्षित स्थान पर जाएं।")
    val ttsInputText = _ttsInputText.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing = _isSynthesizing.asStateFlow()

    private val _isPlayingTts = MutableStateFlow(false)
    val isPlayingTts = _isPlayingTts.asStateFlow()

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
            if (_isPlayingTts.value) {
                latencyTracker.onTtsFirstAudioFramePlayed()
            } else {
                latencyTracker.onPlaybackStarted()
            }
        },
        onPlaybackFinished = {
            _pttState.value = PttState.IDLE
            _isPlayingTts.value = false
        }
    )

    // -------------------------------------------------------------
    // MILESTONE 1: Push-To-Talk Audio Actions
    // -------------------------------------------------------------

    fun onPttDown() {
        if (_pttState.value != PttState.IDLE) return

        latencyTracker.setMode(TransceiverMode.TRANSMITTER)
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

            // Playback what was recorded
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
            latencyTracker.setMode(TransceiverMode.TRANSMITTER)
            playAudio(pcm)
        }
    }

    private fun playAudio(pcmData: ByteArray) {
        _isPlayingTts.value = false
        audioPlayer.playPcm(viewModelScope, pcmData)
    }

    // -------------------------------------------------------------
    // MILESTONE 2: Offline Indic TTS Actions
    // -------------------------------------------------------------

    fun selectLanguage(language: SupportedLanguage) {
        _selectedLanguage.value = language
    }

    fun updateInputText(text: String) {
        _ttsInputText.value = text
    }

    fun selectEmergencyPreset(preset: EmergencyPreset) {
        val langText = preset.getTextFor(_selectedLanguage.value)
        _ttsInputText.value = langText
    }

    fun synthesizeAndSpeak(isEmergency: Boolean = false) {
        val text = _ttsInputText.value.trim()
        if (text.isEmpty() || _isSynthesizing.value || _pttState.value != PttState.IDLE) return

        viewModelScope.launch {
            try {
                _isSynthesizing.value = true
                val currentLang = _selectedLanguage.value

                latencyTracker.setMode(TransceiverMode.RECEIVER)
                latencyTracker.onTtsRequested(
                    text = text,
                    language = currentLang.code,
                    isAlert = isEmergency,
                    modelName = "Indic-TTS VITS"
                )

                latencyTracker.onTtsSynthesisStarted()
                val generated = ttsEngine.synthesize(
                    rawText = text,
                    language = currentLang,
                    speed = 1.0f,
                    isEmergencyAlert = isEmergency
                )

                val pcmByteCount = (generated.samples.size * 2).toLong()
                latencyTracker.onTtsSynthesisCompleted(
                    pcmByteCount = pcmByteCount,
                    sampleRate = generated.sampleRate
                )

                _isPlayingTts.value = true
                val played = audioPlayer.playGeneratedAudio(
                    scope = viewModelScope,
                    audio = generated,
                    isAlarmPriority = isEmergency
                )

                if (!played) {
                    _isPlayingTts.value = false
                }
            } catch (e: Exception) {
                _isPlayingTts.value = false
            } finally {
                _isSynthesizing.value = false
            }
        }
    }

    fun stopTtsPlayback() {
        if (_isPlayingTts.value || audioPlayer.isPlaying) {
            audioPlayer.stop()
            _isPlayingTts.value = false
            _pttState.value = PttState.IDLE
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
    }
}
