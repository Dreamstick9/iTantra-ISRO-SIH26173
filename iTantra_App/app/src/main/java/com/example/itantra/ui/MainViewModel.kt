package com.example.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.itantra.audio.AudioConfig
import com.example.itantra.audio.AudioEngine
import com.example.itantra.audio.AudioTrackPlayer
import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.CompressionEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.data.*
import com.example.itantra.service.MockServiceController
import com.example.itantra.service.ServiceController
import com.example.itantra.speech.MockSpeechEngine
import com.example.itantra.speech.SpeechEngine
import com.example.itantra.transport.MockTransportEngine
import com.example.itantra.transport.Peer
import com.example.itantra.transport.TcpStatus
import com.example.itantra.transport.TransportDiagnostics
import com.example.itantra.transport.TransportEngine
import com.example.itantra.transport.TransportMessage
import com.example.itantra.tts.AudioData
import com.example.itantra.tts.MockTtsEngine
import com.example.itantra.tts.TtsEngine
import com.example.itantra.tts.TtsPlaybackState
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val ttsEngine: TtsEngine = MockTtsEngine(ioDispatcher = ioDispatcher),
    val transportEngine: TransportEngine = MockTransportEngine(),
    val compressionEngine: CompressionEngine = MockCompressionEngine(),
    val serviceController: ServiceController = MockServiceController(),
    val audioTrackPlayer: AudioTrackPlayer = AudioTrackPlayer(ioDispatcher)
) : ViewModel() {

    private val TAG = "MainViewModel"

    private val _appState = MutableStateFlow(AppState.INITIAL)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    val transportDiagnostics: StateFlow<TransportDiagnostics> = transportEngine.diagnostics
    val discoveredPeers: StateFlow<List<Peer>> = transportEngine.peers

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
                        _appState.update {
                            it.copy(
                                pttState = PttState.RECORDING,
                                transceiverState = TransceiverState.RECORDING
                            )
                        }
                    }
                    com.example.itantra.audio.AudioRecordingState.PROCESSING -> {
                        // Maintain active transceiver state if transcribing
                    }
                    com.example.itantra.audio.AudioRecordingState.IDLE -> {
                        if (_appState.value.transceiverState == TransceiverState.RECORDING) {
                            _appState.update {
                                it.copy(
                                    pttState = PttState.IDLE,
                                    transceiverState = TransceiverState.IDLE
                                )
                            }
                        }
                    }
                    com.example.itantra.audio.AudioRecordingState.ERROR -> {
                        _appState.update {
                            it.copy(
                                pttState = PttState.IDLE,
                                transceiverState = TransceiverState.IDLE,
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

        // Observe link diagnostics to enrich device info in AppState
        viewModelScope.launch(ioDispatcher) {
            transportEngine.diagnostics.collect { diag ->
                if (diag.tcpStatus == TcpStatus.CONNECTED) {
                    _appState.update {
                        it.copy(
                            connectedDeviceName = diag.connectedPeerName ?: if (diag.isGroupOwner) "Group Client" else "Group Owner (GO)",
                            connectedDeviceAddress = "${diag.remoteIpAddress ?: "192.168.49.1"}:8988",
                            transportType = TransportType.WIFI_DIRECT
                        )
                    }
                }
            }
        }

        // Observe incoming messages received over transport
        viewModelScope.launch(ioDispatcher) {
            transportEngine.incomingMessages().collect { message ->
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

        // Observe TTS engine state and metrics for UI HUD
        viewModelScope.launch(ioDispatcher) {
            combine(
                ttsEngine.isModelLoaded,
                ttsEngine.playbackState,
                combine(ttsEngine.lastLatencyMs, ttsEngine.lastRtf, ttsEngine.lastDurationMs) { lat, rtf, dur -> Triple(lat, rtf, dur) },
                ttsEngine.errorMessage
            ) { isLoaded, pState, (latency, rtf, duration), err ->
                TtsStateTuple(isLoaded, pState, latency, rtf, duration, err)
            }.collect { info ->
                _appState.update { state ->
                    val ttsSubState = when {
                        info.isLoaded && info.pState == TtsPlaybackState.PLAYING -> SubsystemState.ACTIVE
                        info.isLoaded -> SubsystemState.READY
                        info.err != null -> SubsystemState.ERROR
                        else -> SubsystemState.INITIALIZING
                    }
                    state.copy(
                        subsystems = state.subsystems.copy(
                            tts = ttsSubState,
                            ttsErrorMessage = info.err
                        ),
                        ttsState = state.ttsState.copy(
                            isModelLoaded = info.isLoaded,
                            playbackState = info.pState,
                            synthesisLatencyMs = info.latency,
                            realTimeFactor = info.rtf,
                            audioDurationMs = info.duration,
                            errorMessage = info.err
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
        if (_appState.value.transceiverState != TransceiverState.IDLE) return

        _appState.update {
            it.copy(
                transceiverState = TransceiverState.RECORDING,
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
                        transceiverState = TransceiverState.IDLE,
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
                        transceiverState = TransceiverState.IDLE,
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
        if (_appState.value.transceiverState != TransceiverState.RECORDING) return

        val t0 = System.currentTimeMillis()

        _appState.update {
            it.copy(
                transceiverState = TransceiverState.TRANSCRIBING,
                pttState = PttState.PROCESSING,
                isPttPressed = false,
                isPttLocked = false,
                statusMessage = "Processing captured audio..."
            )
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val pcmAudio = audioEngine.stopRecording()

                if (pcmAudio.isEmpty() || pcmAudio.size < 3200) {
                    val tooShortStatus = if (pcmAudio.isEmpty()) {
                        "Audio capture too short (0 bytes)"
                    } else {
                        "Audio too short (${pcmAudio.size} bytes < 100ms)"
                    }
                    _appState.update {
                        it.copy(
                            transceiverState = TransceiverState.IDLE,
                            pttState = PttState.IDLE,
                            statusMessage = tooShortStatus,
                            errorMessage = null
                        )
                    }
                    return@launch
                }

                val audioDurationMs = (pcmAudio.size * 1000L) / AudioConfig.BYTES_PER_SECOND
                val debugInfo = AudioDebugInfo(
                    durationMs = audioDurationMs,
                    byteCount = pcmAudio.size,
                    message = "Audio captured successfully",
                    sampleRate = AudioConfig.SAMPLE_RATE_HZ,
                    channels = AudioConfig.CHANNEL_COUNT,
                    bitDepth = AudioConfig.BITS_PER_SAMPLE,
                    timestamp = System.currentTimeMillis()
                )

                Logger.i(TAG, "Audio captured successfully: ${pcmAudio.size} bytes ($audioDurationMs ms). Transcribing via speech engine...")

                var recognizedText = ""
                var sttError: String? = null
                try {
                    recognizedText = speechEngine.transcribe(pcmAudio)
                } catch (e: Exception) {
                    Logger.e(TAG, "STT transcription error: ${e.message}", e)
                    sttError = e.message ?: "STT transcription error"
                }

                val t1 = System.currentTimeMillis()
                val sttLatency = t1 - t0

                Logger.i(TAG, "Transcribed in ${sttLatency}ms: '$recognizedText'")

                if (recognizedText.isBlank()) {
                    val status = if (sttError != null) {
                        "Audio captured successfully (${pcmAudio.size} bytes). STT Error: $sttError"
                    } else {
                        "Audio captured successfully (${pcmAudio.size} bytes, ${audioDurationMs}ms) | No speech recognized"
                    }
                    _appState.update { state ->
                        state.copy(
                            transceiverState = TransceiverState.IDLE,
                            pttState = PttState.IDLE,
                            lastAudioDebugInfo = debugInfo,
                            sttDiagnostics = state.sttDiagnostics.copy(
                                isModelLoaded = speechEngine.isModelLoaded.value,
                                isOffline = true,
                                language = "English",
                                processingTimeMs = sttLatency,
                                recognizedText = "",
                                errorMessage = sttError
                            ),
                            statusMessage = status,
                            errorMessage = sttError
                        )
                    }
                    return@launch
                }

                // recognizedText isNotBlank:
                _appState.update {
                    it.copy(
                        transceiverState = TransceiverState.TRANSMITTING,
                        pttState = PttState.PROCESSING,
                        statusMessage = "Transmitting message..."
                    )
                }

                val isEmergency = _appState.value.emergencyAlert.isActive
                val t2BeforeSend = System.currentTimeMillis()
                val message = if (isEmergency) {
                    TransportMessage.alert(
                        text = recognizedText,
                        language = _appState.value.inputLanguage.isoCode,
                        priority = 2,
                        t0 = t0,
                        t1 = t1,
                        t2 = t2BeforeSend,
                        sttLatencyMs = sttLatency,
                        audioDurationMs = audioDurationMs
                    )
                } else {
                    TransportMessage.text(
                        text = recognizedText,
                        language = _appState.value.inputLanguage.isoCode,
                        priority = 0,
                        t0 = t0,
                        t1 = t1,
                        t2 = t2BeforeSend,
                        sttLatencyMs = sttLatency,
                        audioDurationMs = audioDurationMs
                    )
                }

                try {
                    transportEngine.send(message)
                } catch (e: Exception) {
                    Logger.e(TAG, "Transport send error: ${e.message}", e)
                }

                val t2 = System.currentTimeMillis()
                val vsTimestamps = VerticalSliceTimestamps(
                    t0PttReleased = t0,
                    t1SttComplete = t1,
                    t2Transmitted = t2,
                    sttLatencyMs = sttLatency,
                    endToEndLatencyMs = t2 - t0
                )

                val latencyMetrics = vsTimestamps.toLatencyMetrics(
                    audioDurationMs = audioDurationMs,
                    characterCount = recognizedText.length,
                    compressedByteSize = message.compressedPayload.size
                )

                val outgoingMessage = ReceivedMessage(
                    id = message.messageId,
                    senderId = "LOCAL_USER",
                    senderName = "Local Operator (You)",
                    text = recognizedText,
                    originalLanguage = Language.ENGLISH,
                    targetLanguage = _appState.value.outputLanguage,
                    timestamp = System.currentTimeMillis(),
                    messageType = if (isEmergency) MessageType.EMERGENCY_ALERT else MessageType.VOICE_NOTE,
                    isEmergency = isEmergency,
                    latencyMetrics = latencyMetrics,
                    rawUtf8ByteSize = recognizedText.toByteArray(Charsets.UTF_8).size,
                    compressedByteSize = (recognizedText.length * 0.75).toInt().coerceAtLeast(1),
                    isOutgoing = true
                )

                _appState.update { state ->
                    val newDiagnostics = state.sttDiagnostics.copy(
                        isModelLoaded = speechEngine.isModelLoaded.value,
                        isOffline = true,
                        language = "English",
                        processingTimeMs = sttLatency,
                        recognizedText = recognizedText,
                        errorMessage = null
                    )

                    val updatedMessages = listOf(outgoingMessage) + state.messages
                    val updatedStatus = "Audio captured successfully (${pcmAudio.size} bytes, ${audioDurationMs}ms) | STT: \"$recognizedText\" (${sttLatency}ms) | Sent (${t2 - t0}ms)"

                    state.copy(
                        transceiverState = TransceiverState.IDLE,
                        pttState = PttState.IDLE,
                        lastAudioDebugInfo = debugInfo,
                        sttDiagnostics = newDiagnostics,
                        messages = updatedMessages,
                        verticalSliceTimestamps = vsTimestamps,
                        lastLatencyMetrics = latencyMetrics,
                        statusMessage = updatedStatus,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error stopping audio recording: ${e.message}", e)
                _appState.update {
                    it.copy(
                        transceiverState = TransceiverState.IDLE,
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

    fun onStartPeerDiscovery() {
        viewModelScope.launch(ioDispatcher) {
            try {
                transportEngine.startDiscovery()
            } catch (e: Exception) {
                Logger.e(TAG, "Error starting peer discovery: ${e.message}", e)
                _appState.update { it.copy(errorMessage = "Discovery error: ${e.message}") }
            }
        }
    }

    fun onConnectToPeer(peer: Peer) {
        viewModelScope.launch(ioDispatcher) {
            try {
                transportEngine.connect(peer)
            } catch (e: Exception) {
                Logger.e(TAG, "Error connecting to peer ${peer.deviceName}: ${e.message}", e)
                _appState.update { it.copy(errorMessage = "Connect error: ${e.message}") }
            }
        }
    }

    fun onDisconnectTransport() {
        viewModelScope.launch(ioDispatcher) {
            try {
                transportEngine.disconnect()
            } catch (e: Exception) {
                Logger.e(TAG, "Error disconnecting transport: ${e.message}", e)
            }
        }
    }

    fun onSendTextMessage(text: String) {
        if (text.isBlank()) return
        val message = TransportMessage.text(
            text = text.trim(),
            language = _appState.value.inputLanguage.isoCode,
            priority = 0
        )
        viewModelScope.launch(ioDispatcher) {
            try {
                transportEngine.send(message)
                val uiMsg = message.toReceivedMessage().copy(
                    isOutgoing = true,
                    senderName = "Local Operator (You)"
                )
                _appState.update {
                    it.copy(
                        messages = listOf(uiMsg) + it.messages,
                        statusMessage = "Sent: \"${text.take(30)}\""
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to send text message over Wi-Fi Direct: ${e.message}", e)
                _appState.update { it.copy(errorMessage = "Send failed: ${e.message}") }
            }
        }
    }

    fun onSendAlertMessage(text: String) {
        if (text.isBlank()) return
        val alert = TransportMessage.alert(
            text = text.trim(),
            language = _appState.value.inputLanguage.isoCode,
            priority = 2
        )
        viewModelScope.launch(ioDispatcher) {
            try {
                transportEngine.send(alert)
                val uiMsg = alert.toReceivedMessage().copy(
                    isOutgoing = true,
                    senderName = "Local Operator (You)"
                )
                _appState.update {
                    it.copy(
                        messages = listOf(uiMsg) + it.messages,
                        statusMessage = "BROADCAST ALERT SENT"
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to send alert over Wi-Fi Direct: ${e.message}", e)
                _appState.update { it.copy(errorMessage = "Alert send failed: ${e.message}") }
            }
        }
    }

    fun onPlayMessageAudio(message: ReceivedMessage) {
        viewModelScope.launch(ioDispatcher) {
            _appState.update {
                it.copy(
                    transceiverState = TransceiverState.SPEAKING,
                    pttState = PttState.PROCESSING,
                    isAudioPlaying = true,
                    statusMessage = "Playing voice from ${message.senderName}..."
                )
            }
            try {
                val audioData = ttsEngine.synthesize(message.text)
                if (!audioData.isEmpty) {
                    try {
                        audioTrackPlayer.play(audioData.rawPcm, audioData.sampleRate)
                    } catch (e: Throwable) {
                        Logger.w(TAG, "AudioTrackPlayer playback error: ${e.message}, falling back to ttsEngine.speak", e)
                        ttsEngine.speak(message.text)
                    }
                } else {
                    ttsEngine.speak(message.text)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Audio playback error: ${e.message}", e)
                _appState.update { it.copy(errorMessage = "Playback failed: ${e.message}") }
            } finally {
                _appState.update {
                    it.copy(
                        transceiverState = TransceiverState.IDLE,
                        pttState = PttState.IDLE,
                        isAudioPlaying = false,
                        statusMessage = "Playback completed"
                    )
                }
            }
        }
    }

    fun simulateReceiveMessage(customText: String? = null) {
        val text = customText ?: "चक्रवात चेतावनी: तटीय क्षेत्र तुरंत खाली करें"
        val now = System.currentTimeMillis()
        val simT0 = now - 380L
        val simT1 = now - 190L
        val simT2 = now - 28L
        val simSttLat = simT1 - simT0

        val simMsg = ReceivedMessage(
            id = UUID.randomUUID().toString(),
            senderId = "INCOIS-NavIC-Sat",
            senderName = "NavIC Beacon #3",
            text = text,
            originalLanguage = _appState.value.inputLanguage,
            targetLanguage = _appState.value.outputLanguage,
            timestamp = now,
            messageType = if (_appState.value.emergencyAlert.isActive) MessageType.EMERGENCY_ALERT else MessageType.VOICE_NOTE,
            isEmergency = _appState.value.emergencyAlert.isActive,
            rawUtf8ByteSize = text.toByteArray(Charsets.UTF_8).size,
            compressedByteSize = 25,
            isOutgoing = false
        )
        viewModelScope.launch(ioDispatcher) {
            val tMsg = TransportMessage.text(
                text = text,
                language = _appState.value.inputLanguage.isoCode,
                priority = if (_appState.value.emergencyAlert.isActive) 1 else 0,
                messageId = simMsg.id,
                timestamp = now,
                t0 = simT0,
                t1 = simT1,
                t2 = simT2,
                sttLatencyMs = simSttLat
            )
            try {
                transportEngine.sendMessage(simMsg)
            } catch (e: Exception) {
                Logger.d(TAG, "Simulate receive transport send skipped: ${e.message}")
            }
            handleIncomingMessage(tMsg)
        }
    }

    suspend fun handleIncomingMessage(message: TransportMessage) {
        val t3 = System.currentTimeMillis()

        _appState.update {
            it.copy(
                transceiverState = TransceiverState.RECEIVING,
                pttState = PttState.PROCESSING,
                statusMessage = "Receiving message from peer..."
            )
        }

        val text = message.text
        val netLat = if (message.t2 > 0 && t3 >= message.t2 && (t3 - message.t2) < 30000L) (t3 - message.t2) else 15L

        _appState.update {
            it.copy(
                transceiverState = TransceiverState.SPEAKING,
                pttState = PttState.PROCESSING,
                isAudioPlaying = true,
                statusMessage = "Synthesizing voice from peer..."
            )
        }

        val audioData = try {
            ttsEngine.synthesize(text)
        } catch (e: Exception) {
            Logger.e(TAG, "TTS synthesis error: ${e.message}", e)
            AudioData.EMPTY
        }

        val t4 = System.currentTimeMillis()
        val ttsLatency = t4 - t3

        if (!audioData.isEmpty) {
            val t5 = System.currentTimeMillis()
            val e2eLatency = message.sttLatencyMs + netLat + ttsLatency + (t5 - t4)

            val vsTimestamps = VerticalSliceTimestamps(
                t0PttReleased = message.t0,
                t1SttComplete = message.t1,
                t2Transmitted = message.t2,
                t3Received = t3,
                t4TtsComplete = t4,
                t5PlaybackStarted = t5,
                sttLatencyMs = message.sttLatencyMs,
                networkLatencyMs = netLat,
                ttsLatencyMs = ttsLatency,
                endToEndLatencyMs = e2eLatency
            )

            val latencyMetrics = vsTimestamps.toLatencyMetrics(
                audioDurationMs = audioData.durationMs,
                characterCount = text.length,
                compressedByteSize = message.compressedPayload.size
            ).copy(
                realTimeFactor = if (audioData.durationMs > 0) ttsLatency.toDouble() / audioData.durationMs.toDouble() else 0.0
            )

            val receivedMsg = message.toReceivedMessage(t3Received = t3).copy(
                playbackStatus = MessagePlaybackStatus.PLAYING,
                latencyMetrics = latencyMetrics
            )

            _appState.update { state ->
                val newMessages = listOf(receivedMsg) + state.messages
                state.copy(
                    messages = newMessages,
                    isAudioPlaying = true,
                    statusMessage = "Playing voice from ${receivedMsg.senderName}",
                    verticalSliceTimestamps = vsTimestamps,
                    lastLatencyMetrics = latencyMetrics
                )
            }

            try {
                audioTrackPlayer.play(audioData.rawPcm, audioData.sampleRate)
            } catch (e: Throwable) {
                Logger.w(TAG, "AudioTrackPlayer playback error: ${e.message}, falling back to ttsEngine.speak", e)
                ttsEngine.speak(text)
            }

            _appState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.id == receivedMsg.id) msg.copy(playbackStatus = MessagePlaybackStatus.PLAYED) else msg
                }
                state.copy(
                    messages = updatedMessages,
                    isAudioPlaying = false,
                    statusMessage = "Playback completed"
                )
            }
        } else {
            val receivedMsg = message.toReceivedMessage(t3Received = t3).copy(
                playbackStatus = MessagePlaybackStatus.FAILED
            )
            _appState.update { state ->
                val newMessages = listOf(receivedMsg) + state.messages
                state.copy(
                    messages = newMessages,
                    isAudioPlaying = false,
                    statusMessage = "TTS synthesis failed",
                    errorMessage = "TTS synthesis failed"
                )
            }
        }

        _appState.update {
            it.copy(
                transceiverState = TransceiverState.IDLE,
                pttState = PttState.IDLE,
                isAudioPlaying = false
            )
        }
    }

    suspend fun handleIncomingMessage(message: ReceivedMessage) {
        val transportMsg = TransportMessage.fromReceivedMessage(message)
        handleIncomingMessage(transportMsg)
    }

    // ========================================================================
    // TTS OFFLINE SPEECH ACTIONS
    // ========================================================================

    fun onTtsInputChanged(newText: String) {
        _appState.update { it.copy(ttsState = it.ttsState.copy(inputText = newText)) }
    }

    fun onSpeakTts(customText: String? = null) {
        val textToSpeak = (customText ?: _appState.value.ttsState.inputText).trim()
        if (textToSpeak.isBlank()) {
            _appState.update {
                it.copy(
                    ttsState = it.ttsState.copy(errorMessage = "Text cannot be empty"),
                    statusMessage = "Please enter text to synthesize"
                )
            }
            return
        }

        _appState.update {
            it.copy(
                statusMessage = "Synthesizing offline speech: \"$textToSpeak\"...",
                ttsState = it.ttsState.copy(errorMessage = null)
            )
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                ttsEngine.speak(textToSpeak)
                _appState.update {
                    it.copy(statusMessage = "Offline speech playback completed.")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "TTS speak error: ${e.message}", e)
                _appState.update {
                    it.copy(
                        statusMessage = "TTS playback failed: ${e.message}",
                        ttsState = it.ttsState.copy(errorMessage = e.message)
                    )
                }
            }
        }
    }

    fun onStopTts() {
        viewModelScope.launch(ioDispatcher) {
            ttsEngine.stop()
            audioTrackPlayer.stop()
            _appState.update {
                it.copy(
                    isAudioPlaying = false,
                    transceiverState = TransceiverState.IDLE,
                    pttState = PttState.IDLE,
                    statusMessage = "TTS playback stopped."
                )
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        speechEngine.release()
        ttsEngine.release()
        transportEngine.release()
        audioTrackPlayer.release()
        Logger.i(TAG, "MainViewModel cleared.")
    }
}

private data class TtsStateTuple(
    val isLoaded: Boolean,
    val pState: TtsPlaybackState,
    val latency: Long,
    val rtf: Double,
    val duration: Long,
    val err: String?
)

