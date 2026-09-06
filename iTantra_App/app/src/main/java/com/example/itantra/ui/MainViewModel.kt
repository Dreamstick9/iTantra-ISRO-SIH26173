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
import kotlinx.coroutines.flow.combine
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
        Logger.i(TAG, "MainViewModel initialized with Stage 1 decoupled architecture.")

        (audioEngine as? MockAudioEngine)?.initialize()
        speechEngine.initialize()
        transportEngine.initialize()

        // Observe audio level stream for real-time visual feedback
        viewModelScope.launch(ioDispatcher) {
            audioEngine.audioLevel.collect { level ->
                _appState.update { it.copy(audioLevel = level) }
            }
        }

        // Observe audio engine recording state
        viewModelScope.launch(ioDispatcher) {
            audioEngine.recordingState.collect { recState ->
                when (recState) {
                    com.example.itantra.audio.AudioRecordingState.RECORDING -> {
                        _appState.update { it.copy(pttState = PttState.RECORDING) }
                    }
                    com.example.itantra.audio.AudioRecordingState.PROCESSING -> {
                        _appState.update { it.copy(pttState = PttState.PROCESSING) }
                    }
                    com.example.itantra.audio.AudioRecordingState.IDLE -> {
                        if (_appState.value.pttState != PttState.IDLE) {
                            _appState.update { it.copy(pttState = PttState.IDLE) }
                        }
                    }
                    com.example.itantra.audio.AudioRecordingState.ERROR -> {
                        _appState.update {
                            it.copy(
                                pttState = PttState.IDLE,
                                isPttPressed = false,
                                isPttLocked = false
                            )
                        }
                    }
                }
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

        // Observe speech engine model load state and errors for STT diagnostics
        viewModelScope.launch(ioDispatcher) {
            combine(speechEngine.isModelLoaded, speechEngine.loadErrorMessage) { loaded, errorMsg ->
                Pair(loaded, errorMsg)
            }.collect { (loaded, errorMsg) ->
                _appState.update { state ->
                    val sttState = when {
                        loaded -> SubsystemState.READY
                        errorMsg != null -> SubsystemState.ERROR
                        else -> SubsystemState.INITIALIZING
                    }
                    state.copy(
                        subsystems = state.subsystems.copy(
                            stt = sttState,
                            sttErrorMessage = errorMsg
                        ),
                        sttDiagnostics = state.sttDiagnostics.copy(
                            isModelLoaded = loaded,
                            isOffline = true,
                            language = "English",
                            errorMessage = errorMsg
                        )
                    )
                }
            }
        }
    }

    // ========================================================================
    // USER ACTIONS
    // ========================================================================

    fun onPermissionResult(isGranted: Boolean) {
        _appState.update {
            it.copy(
                hasAudioPermission = isGranted,
                errorMessage = if (isGranted) null else "Microphone permission (RECORD_AUDIO) was denied. Please grant permission to record audio."
            )
        }
    }

    fun onPttPressed() {
        if (_appState.value.pttState != PttState.IDLE) return

        _appState.update {
            it.copy(
                pttState = PttState.RECORDING,
                isPttPressed = true,
                statusMessage = "Recording PCM audio...",
                errorMessage = null
            )
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                audioEngine.startRecording()
            } catch (e: SecurityException) {
                Logger.e(TAG, "Microphone permission missing: ${e.message}")
                _appState.update {
                    it.copy(
                        pttState = PttState.IDLE,
                        isPttPressed = false,
                        isPttLocked = false,
                        hasAudioPermission = false,
                        errorMessage = "RECORD_AUDIO permission denied. Please grant microphone access.",
                        statusMessage = "Permission required"
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "AudioRecord initialization error: ${e.message}", e)
                _appState.update {
                    it.copy(
                        pttState = PttState.IDLE,
                        isPttPressed = false,
                        isPttLocked = false,
                        errorMessage = "AudioRecord initialization error: ${e.message}",
                        statusMessage = "AudioRecord failed"
                    )
                }
            }
        }
    }

    fun onPttReleased() {
        if (_appState.value.pttState != PttState.RECORDING) return

        _appState.update {
            it.copy(
                pttState = PttState.PROCESSING,
                isPttPressed = false,
                isPttLocked = false,
                statusMessage = "Processing captured audio..."
            )
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val pcmAudio = audioEngine.stopRecording()

                if (pcmAudio.isEmpty()) {
                    _appState.update {
                        it.copy(
                            pttState = PttState.IDLE,
                            statusMessage = "Audio capture too short (0 bytes)",
                            errorMessage = null
                        )
                    }
                    return@launch
                }

                val durationMs = (pcmAudio.size * 1000L) / AudioConfig.BYTES_PER_SECOND
                val debugInfo = AudioDebugInfo(
                    durationMs = durationMs,
                    byteCount = pcmAudio.size,
                    message = "Audio captured successfully",
                    sampleRate = AudioConfig.SAMPLE_RATE_HZ,
                    channels = AudioConfig.CHANNEL_COUNT,
                    bitDepth = AudioConfig.BITS_PER_SAMPLE,
                    timestamp = System.currentTimeMillis()
                )

                Logger.i(TAG, "Audio captured successfully: ${pcmAudio.size} bytes ($durationMs ms). Transcribing via speech engine...")

                // Measure latency and transcribe PCM audio via speechEngine
                val startTime = System.currentTimeMillis()
                var recognizedText = ""
                var sttError: String? = null
                try {
                    recognizedText = speechEngine.transcribe(pcmAudio)
                } catch (e: Exception) {
                    Logger.e(TAG, "STT transcription error: ${e.message}", e)
                    sttError = e.message ?: "STT transcription error"
                }
                val processingTimeMs = System.currentTimeMillis() - startTime

                Logger.i(TAG, "Transcribed in ${processingTimeMs}ms: '$recognizedText'")

                _appState.update { state ->
                    val newDiagnostics = state.sttDiagnostics.copy(
                        isModelLoaded = speechEngine.isModelLoaded.value,
                        isOffline = true,
                        language = "English",
                        processingTimeMs = processingTimeMs,
                        recognizedText = recognizedText,
                        errorMessage = sttError
                    )

                    val updatedMessages = if (recognizedText.isNotBlank()) {
                        val outgoingMessage = ReceivedMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = "LOCAL_USER",
                            senderName = "Local Operator (You)",
                            text = recognizedText,
                            originalLanguage = Language.ENGLISH,
                            targetLanguage = state.outputLanguage,
                            timestamp = System.currentTimeMillis(),
                            messageType = if (state.emergencyAlert.isActive) MessageType.EMERGENCY_ALERT else MessageType.VOICE_NOTE,
                            isEmergency = state.emergencyAlert.isActive,
                            rawUtf8ByteSize = recognizedText.toByteArray(Charsets.UTF_8).size,
                            compressedByteSize = (recognizedText.length * 0.75).toInt().coerceAtLeast(1),
                            isOutgoing = true
                        )
                        listOf(outgoingMessage) + state.messages
                    } else {
                        state.messages
                    }

                    val updatedStatus = if (sttError != null) {
                        "Audio captured successfully (${pcmAudio.size} bytes). STT Error: $sttError"
                    } else if (recognizedText.isNotBlank()) {
                        "Audio captured successfully (${pcmAudio.size} bytes, ${durationMs}ms) | STT: \"$recognizedText\" (${processingTimeMs}ms)"
                    } else {
                        "Audio captured successfully (${pcmAudio.size} bytes, ${durationMs}ms)"
                    }

                    state.copy(
                        pttState = PttState.IDLE,
                        lastAudioDebugInfo = debugInfo,
                        sttDiagnostics = newDiagnostics,
                        messages = updatedMessages,
                        statusMessage = updatedStatus,
                        errorMessage = sttError
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error stopping audio recording: ${e.message}", e)
                _appState.update {
                    it.copy(
                        pttState = PttState.IDLE,
                        errorMessage = "AudioRecord stop error: ${e.message}",
                        statusMessage = "Error stopping capture"
                    )
                }
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
                (audioEngine as? MockAudioEngine)?.playAudio(
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

            (audioEngine as? MockAudioEngine)?.playAudio(
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
