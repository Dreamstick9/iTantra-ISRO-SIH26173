package com.example.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.itantra.audio.AudioConfig
import com.example.itantra.audio.AudioEngine
import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.CompressionEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.data.*
import com.example.itantra.service.MockServiceController
import com.example.itantra.service.ServiceController
import com.example.itantra.speech.MockSpeechEngine
import com.example.itantra.speech.SpeechEngine
import com.example.itantra.transport.MockTransportEngine
import com.example.itantra.transport.TransportEngine
import com.example.itantra.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(
    val audioEngine: AudioEngine = MockAudioEngine(),
    val speechEngine: SpeechEngine = MockSpeechEngine(),
    val transportEngine: TransportEngine = MockTransportEngine(),
    val compressionEngine: CompressionEngine = MockCompressionEngine(),
    val serviceController: ServiceController = MockServiceController(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val TAG = "MainViewModel"

    private val _appState = MutableStateFlow(AppState.INITIAL)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    init {
        Logger.i(TAG, "MainViewModel initialized with Stage 0 decoupled architecture.")

        audioEngine.initialize()
        speechEngine.initialize()
        transportEngine.initialize()

        // Observe audio level stream for real-time visual feedback
        viewModelScope.launch(ioDispatcher) {
            audioEngine.audioLevel.collect { level ->
                _appState.update { it.copy(audioLevel = level) }
            }
        }

        // Observe link-layer transport connection status
        viewModelScope.launch(ioDispatcher) {
            transportEngine.connectionStatus.collect { status ->
                _appState.update {
                    it.copy(
                        connectionStatus = status,
                        connectedDeviceName = if (status == ConnectionStatus.CONNECTED) "ISRO-Field-Unit-07" else null,
                        connectedDeviceAddress = if (status == ConnectionStatus.CONNECTED) "192.168.49.1:8988" else null,
                        transportType = if (status == ConnectionStatus.CONNECTED) TransportType.WIFI_DIRECT else TransportType.NONE
                    )
                }
            }
        }

        // Observe incoming messages received over transport
        viewModelScope.launch(ioDispatcher) {
            transportEngine.incomingMessages.collect { message ->
                handleIncomingMessage(message)
            }
        }
    }

    // ========================================================================
    // USER ACTIONS
    // ========================================================================

    fun onPttPressed() {
        if (_appState.value.pttState != PttState.IDLE) return

        if (!_appState.value.emergencyAlert.isActive && audioEngine.isPlaying) {
            audioEngine.stopPlayback()
            _appState.update { it.copy(isAudioPlaying = false) }
        }

        val result = audioEngine.startRecording()
        if (result.isSuccess) {
            _appState.update {
                it.copy(
                    pttState = PttState.RECORDING,
                    isPttPressed = true,
                    statusMessage = "Recording audio...",
                    errorMessage = null
                )
            }
        } else {
            _appState.update {
                it.copy(errorMessage = "Failed to start recording")
            }
        }
    }

    fun onPttReleased() {
        if (_appState.value.pttState != PttState.RECORDING) return

        _appState.update {
            it.copy(
                pttState = PttState.TRANSMITTING,
                isPttPressed = false,
                statusMessage = "Transcribing & Transmitting..."
            )
        }

        viewModelScope.launch(ioDispatcher) {
            val startTime = System.currentTimeMillis()
            val pcmAudio = audioEngine.stopRecording()

            if (pcmAudio.isEmpty()) {
                _appState.update {
                    it.copy(
                        pttState = PttState.IDLE,
                        statusMessage = "Tap too short (no audio)",
                        errorMessage = null
                    )
                }
                return@launch
            }

            val lang = _appState.value.inputLanguage
            val sttResult = speechEngine.transcribe(pcmAudio, lang)

            if (sttResult.isFailure) {
                _appState.update {
                    it.copy(
                        pttState = PttState.IDLE,
                        errorMessage = sttResult.exceptionOrNull()?.message ?: "STT Failed",
                        statusMessage = "STT Error"
                    )
                }
                return@launch
            }

            val transcription = sttResult.getOrThrow()
            val rawText = transcription.text.trim()
            if (rawText.isBlank()) {
                _appState.update {
                    it.copy(
                        pttState = PttState.IDLE,
                        statusMessage = "Empty transcription",
                        errorMessage = null
                    )
                }
                return@launch
            }

            val isEmergency = _appState.value.emergencyAlert.isActive
            val compressedBytes = compressionEngine.compressText(rawText)
            val sttLatency = System.currentTimeMillis() - startTime

            val outgoing = ReceivedMessage(
                id = UUID.randomUUID().toString(),
                senderId = "Local-Node",
                senderName = "You",
                text = rawText,
                originalLanguage = lang,
                targetLanguage = _appState.value.outputLanguage,
                timestamp = System.currentTimeMillis(),
                messageType = if (isEmergency) MessageType.EMERGENCY_ALERT else MessageType.VOICE_NOTE,
                isEmergency = isEmergency,
                rawUtf8ByteSize = rawText.toByteArray(Charsets.UTF_8).size,
                compressedByteSize = compressedBytes.size,
                isOutgoing = true,
                latencyMetrics = LatencyMetrics(
                    sttLatencyMs = sttLatency,
                    transmissionLatencyMs = 38L,
                    ttsLatencyMs = 0L,
                    endToEndLatencyMs = sttLatency + 38L,
                    characterCount = rawText.length,
                    compressedByteSize = compressedBytes.size
                )
            )

            val sendResult = transportEngine.sendMessage(outgoing)

            _appState.update { state ->
                val newMessages = listOf(outgoing) + state.messages
                state.copy(
                    pttState = PttState.IDLE,
                    messages = newMessages,
                    currentLiveTranscription = rawText,
                    statusMessage = if (sendResult.isSuccess) "Transmitted (${compressedBytes.size} B)" else "Transmission error",
                    errorMessage = sendResult.exceptionOrNull()?.message,
                    lastLatencyMetrics = outgoing.latencyMetrics
                )
            }
        }
    }

    fun onPttLockToggled() {
        val currentLocked = _appState.value.isPttLocked
        if (!currentLocked) {
            _appState.update { it.copy(isPttLocked = true) }
            onPttPressed()
        } else {
            _appState.update { it.copy(isPttLocked = false) }
            onPttReleased()
        }
    }

    fun onTransmissionModeChanged(mode: TransmissionMode) {
        transportEngine.setTransmissionMode(mode)
        _appState.update {
            it.copy(
                transmissionMode = mode,
                statusMessage = "Mode: ${mode.displayName}"
            )
        }
    }

    fun onInputLanguageSelected(lang: Language) {
        _appState.update {
            it.copy(
                inputLanguage = lang,
                statusMessage = "Input language: ${lang.englishName}"
            )
        }
    }

    fun onOutputLanguageSelected(lang: Language) {
        _appState.update {
            it.copy(
                outputLanguage = lang,
                statusMessage = "Output voice: ${lang.englishName}"
            )
        }
    }

    fun onSwapLanguages() {
        _appState.update {
            it.copy(
                inputLanguage = it.outputLanguage,
                outputLanguage = it.inputLanguage,
                statusMessage = "Languages swapped"
            )
        }
    }

    fun onEmergencyAlertToggled(active: Boolean? = null) {
        _appState.update { state ->
            val newState = active ?: !state.emergencyAlert.isActive
            val alertState = if (newState) {
                EmergencyAlertState(
                    isActive = true,
                    title = "CYCLONE ADVISORY",
                    message = "High alert warning: Evacuate coastal lowlands.",
                    severity = AlertSeverity.CRITICAL,
                    category = AlertCategory.CYCLONE,
                    source = "NavIC-L5 / INCOIS"
                )
            } else {
                EmergencyAlertState()
            }
            state.copy(
                emergencyAlert = alertState,
                statusMessage = if (newState) "EMERGENCY BROADCAST ARMED" else "Normal mode"
            )
        }
    }

    fun onConnectOrScanClick() {
        viewModelScope.launch(ioDispatcher) {
            transportEngine.connect()
        }
    }

    fun onDisconnectClick() {
        viewModelScope.launch(ioDispatcher) {
            transportEngine.disconnect()
        }
    }

    fun onPlayMessageAudio(message: ReceivedMessage) {
        viewModelScope.launch(ioDispatcher) {
            val synthResult = speechEngine.synthesize(
                text = message.text,
                language = message.targetLanguage,
                isEmergency = message.isEmergency
            )
            synthResult.onSuccess { synth ->
                audioEngine.playAudio(
                    pcmData = synth.audioPcm,
                    sampleRate = synth.sampleRate,
                    isEmergency = message.isEmergency
                )
            }
        }
    }

    fun simulateReceiveMessage(customText: String? = null) {
        val text = customText ?: "चक्रवात चेतावनी: तटीय क्षेत्र तुरंत खाली करें"
        val simMsg = ReceivedMessage(
            id = UUID.randomUUID().toString(),
            senderId = "INCOIS-NavIC-Sat",
            senderName = "NavIC Beacon #3",
            text = text,
            originalLanguage = _appState.value.inputLanguage,
            targetLanguage = _appState.value.outputLanguage,
            timestamp = System.currentTimeMillis(),
            messageType = if (_appState.value.emergencyAlert.isActive) MessageType.EMERGENCY_ALERT else MessageType.VOICE_NOTE,
            isEmergency = _appState.value.emergencyAlert.isActive,
            rawUtf8ByteSize = text.toByteArray(Charsets.UTF_8).size,
            compressedByteSize = 25,
            isOutgoing = false
        )
        viewModelScope.launch(ioDispatcher) {
            transportEngine.sendMessage(simMsg)
            handleIncomingMessage(simMsg)
        }
    }

    private suspend fun handleIncomingMessage(message: ReceivedMessage) {
        val synthStart = System.currentTimeMillis()
        val normalized = speechEngine.normalize(message.text, message.targetLanguage)
        val synthResult = speechEngine.synthesize(
            text = normalized,
            language = message.targetLanguage,
            isEmergency = message.isEmergency
        )

        val ttsDuration = System.currentTimeMillis() - synthStart

        if (synthResult.isSuccess) {
            val synth = synthResult.getOrThrow()
            _appState.update { state ->
                val newMessages = listOf(message.copy(playbackStatus = MessagePlaybackStatus.PLAYING)) + state.messages
                val latency = LatencyMetrics(
                    sttLatencyMs = 180L,
                    transmissionLatencyMs = 45L,
                    ttsLatencyMs = ttsDuration,
                    realTimeFactor = synth.rtf,
                    endToEndLatencyMs = 180L + 45L + ttsDuration,
                    audioDurationMs = synth.audioDurationMs,
                    characterCount = message.text.length,
                    compressedByteSize = message.compressedByteSize
                )
                state.copy(
                    messages = newMessages,
                    isAudioPlaying = true,
                    statusMessage = "Playing voice from ${message.senderName}",
                    lastLatencyMetrics = latency
                )
            }

            audioEngine.playAudio(
                pcmData = synth.audioPcm,
                sampleRate = synth.sampleRate,
                isEmergency = message.isEmergency
            )

            _appState.update {
                it.copy(
                    isAudioPlaying = false,
                    statusMessage = "Playback completed"
                )
            }
        } else {
            _appState.update { state ->
                val newMessages = listOf(message.copy(playbackStatus = MessagePlaybackStatus.FAILED)) + state.messages
                state.copy(
                    messages = newMessages,
                    errorMessage = "TTS synthesis failed"
                )
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        speechEngine.release()
        transportEngine.release()
        Logger.i(TAG, "MainViewModel cleared.")
    }
}
