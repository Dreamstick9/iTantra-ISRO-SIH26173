package com.itantra.voice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.voice.audio.AudioPlayer
import com.itantra.voice.audio.AudioRecorder
import com.itantra.voice.audio.WavEncoder
import com.itantra.voice.data.Language
import com.itantra.voice.feedback.FeedbackRepository
import com.itantra.voice.network.SarvamApiClient
import com.itantra.voice.transport.DiscoveredPeer
import com.itantra.voice.transport.TransportConnectionState
import com.itantra.voice.transport.TransportEngine
import com.itantra.voice.transport.TransportMessage
import com.itantra.voice.transport.TransportMessageType
import kotlinx.coroutines.CoroutineDispatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File



/**
 * 7-State Finite State Machine representingPush-to-Talk voice translation pipeline.
 */
enum class PttState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    TRANSLATING,
    SYNTHESIZING,
    PLAYING,
    ERROR
}

/**
 * Real-time latency telemetry stats in milliseconds.
 */
data class LatencyStats(
    val sttMs: Long = 0L,
    val translateMs: Long = 0L,
    val ttsMs: Long = 0L,
    val netMs: Long? = null,
    val totalMs: Long = 0L
)

/**
 * Immutable UI State observed by Jetpack Compose [MainScreen].
 */
data class MainUiState(
    val state: PttState = PttState.IDLE,
    val sourceLanguage: Language = Language.DEFAULT_SOURCE,
    val targetLanguage: Language = Language.DEFAULT_TARGET,
    val sourceTranscript: String = "",
    val translatedText: String = "",
    val hasAudioToReplay: Boolean = false,
    val errorMessage: String? = null,
    val latencies: LatencyStats = LatencyStats(),
    val amplitude: Float = 0f,
    val isHolding: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val feedbackSubmitted: Boolean? = null,
    val transportState: TransportConnectionState = TransportConnectionState.DISCONNECTED,
    val discoveredPeers: List<DiscoveredPeer> = emptyList(),
    val connectedPeer: DiscoveredPeer? = null,
    val isRemoteMessage: Boolean = false
) {
    val isBusy: Boolean
        get() = state in listOf(PttState.TRANSCRIBING, PttState.TRANSLATING, PttState.SYNTHESIZING)
}

/**
 * MainViewModel manages unidirectional data flow, 7-state FSM, and orchestrates
 * AudioRecord -> WavEncoder -> Saaras v3 STT -> Mayura v1 Translate -> Bulbul v3 TTS -> AudioPlayer.
 */
class MainViewModel(
    private val audioRecorder: AudioRecorder = AudioRecorder(),
    private val sarvamApiClient: SarvamApiClient = SarvamApiClient(),
    audioPlayer: AudioPlayer? = null,
    transportEngine: TransportEngine? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private var audioPlayer: AudioPlayer? = audioPlayer
    private var transportEngine: TransportEngine? = transportEngine
    private var feedbackRepository: FeedbackRepository? = null
    private var pressStartTimeMs: Long = 0L
    private var pipelineJob: Job? = null

    var cachedAudioBase64: String? = null
        private set

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()


    init {
        viewModelScope.launch {
            audioRecorder.amplitude.collect { amp ->
                if (_uiState.value.state == PttState.RECORDING) {
                    _uiState.update { it.copy(amplitude = amp) }
                } else if (_uiState.value.amplitude != 0f) {
                    _uiState.update { it.copy(amplitude = 0f) }
                }
            }
        }
        transportEngine?.let { setTransportEngine(it) }
    }


    fun initAudioPlayer(cacheDir: File) {
        if (this.audioPlayer == null) {
            this.audioPlayer = AudioPlayer(cacheDir)
        }
    }

    /** Initialises the local feedback persistence store using the app's files directory. */
    fun initFeedbackRepository(filesDir: File) {
        if (feedbackRepository == null) {
            feedbackRepository = FeedbackRepository(filesDir)
        }
    }

    fun setAudioPlayer(player: AudioPlayer) {
        this.audioPlayer = player
    }

    /** Initialises and subscribes to the peer-to-peer transport engine. */
    fun setTransportEngine(engine: TransportEngine) {
        this.transportEngine = engine
        viewModelScope.launch {
            engine.connectionState.collect { state ->
                _uiState.update { it.copy(transportState = state) }
            }
        }
        viewModelScope.launch {
            engine.discoveredPeers.collect { peers ->
                _uiState.update { it.copy(discoveredPeers = peers) }
            }
        }
        viewModelScope.launch {
            engine.connectedPeer.collect { peer ->
                _uiState.update { it.copy(connectedPeer = peer) }
            }
        }
        viewModelScope.launch {
            engine.incomingMessages.collect { message ->
                handleIncomingRemoteMessage(message)
            }
        }
    }

    private fun handleIncomingRemoteMessage(message: TransportMessage) {
        val netDuration = (timeProvider() - message.timestamp).coerceAtLeast(0)
        _uiState.update {
            it.copy(
                sourceTranscript = "[Received] ${message.text}",
                translatedText = message.text,
                targetLanguage = Language.fromBcp47(message.targetLanguage) ?: it.targetLanguage,
                isRemoteMessage = true,
                latencies = it.latencies.copy(netMs = netDuration, totalMs = netDuration)
            )
        }

        // Gate 8: Phone B automatically speaks the received translated text using the EXISTING TTS implementation
        speakReceivedTranslation(
            text = message.text,
            targetLangCode = message.targetLanguage
        )
    }

    private fun speakReceivedTranslation(text: String, targetLangCode: String) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(state = PttState.SYNTHESIZING) }
            val ttsStart = timeProvider()
            val ttsResult = sarvamApiClient.synthesize(
                text = text,
                targetLang = targetLangCode
            )
            val ttsDuration = (timeProvider() - ttsStart).coerceAtLeast(0)

            val ttsResponse = ttsResult.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        state = PttState.ERROR,
                        errorMessage = error.message ?: "Speech synthesis failed"
                    )
                }
                return@launch
            }

            val base64Audio = ttsResponse.audios.firstOrNull()
            if (base64Audio.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        state = PttState.IDLE,
                        errorMessage = "Speech synthesis failed: empty audio"
                    )
                }
                return@launch
            }

            cachedAudioBase64 = base64Audio
            _uiState.update {
                it.copy(
                    state = PttState.PLAYING,
                    hasAudioToReplay = true
                )
            }

            val player = audioPlayer
            if (player != null) {
                player.playBase64Wav(
                    base64Wav = base64Audio,
                    onComplete = {
                        _uiState.update {
                            it.copy(state = PttState.IDLE, hasAudioToReplay = true)
                        }
                    },
                    onError = { playbackError ->
                        _uiState.update {
                            it.copy(
                                state = PttState.ERROR,
                                errorMessage = playbackError.message ?: "Audio playback error"
                            )
                        }
                    }
                )
            } else {
                _uiState.update {
                    it.copy(state = PttState.IDLE, hasAudioToReplay = true)
                }
            }
        }
    }

    fun onStartDiscovery() {
        transportEngine?.startDiscovery()
    }

    fun onStopDiscovery() {
        transportEngine?.stopDiscovery()
    }

    fun onConnectPeer(peer: DiscoveredPeer) {
        transportEngine?.connect(peer)
    }

    fun onDisconnectTransport() {
        transportEngine?.disconnect()
    }

    fun onSendTestMessage() {
        val engine = transportEngine ?: return
        if (engine.connectionState.value != TransportConnectionState.CONNECTED) return

        viewModelScope.launch(ioDispatcher) {
            val current = _uiState.value
            val testMsg = TransportMessage.createTestMessage(
                sourceLang = current.sourceLanguage.bcp47Code,
                targetLang = current.targetLanguage.bcp47Code
            )
            val netStart = timeProvider()
            val result = engine.send(testMsg)
            val netDuration = (timeProvider() - netStart).coerceAtLeast(0)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        latencies = it.latencies.copy(netMs = netDuration),
                        translatedText = testMsg.text,
                        isRemoteMessage = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Failed to send test message: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }


    /**
     * User pressed and holds Push-to-Talk button.
     */
    fun onPttPress() {
        if (_uiState.value.isBusy) return

        // If currently playing, stop playback immediately
        if (_uiState.value.state == PttState.PLAYING) {
            audioPlayer?.stopAndRelease()
        }

        pressStartTimeMs = timeProvider()
        _uiState.update {
            it.copy(
                state = PttState.RECORDING,
                isHolding = true,
                errorMessage = null,
                latencies = LatencyStats(),
                feedbackSubmitted = null,
                sourceTranscript = "",
                translatedText = "",
                hasAudioToReplay = false
            )
        }


        try {
            audioRecorder.startRecording(viewModelScope)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    state = PttState.ERROR,
                    isHolding = false,
                    errorMessage = e.message ?: "Failed to start microphone recording"
                )
            }
        }
    }

    /**
     * User released Push-to-Talk button.
     * Enforces >= 300ms minimum threshold to filter accidental clicks.
     */
    fun onPttRelease() {
        if (_uiState.value.state != PttState.RECORDING) return
        val pressDuration = timeProvider() - pressStartTimeMs
        _uiState.update { it.copy(isHolding = false) }

        if (pressDuration < 300L) {
            // Abort accidental short tap
            audioRecorder.stopRecording()
            _uiState.update {
                it.copy(
                    state = PttState.IDLE,
                    errorMessage = "Hold button while speaking"
                )
            }
            return
        }

        val pcmBytes = try {
            audioRecorder.stopRecording()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    state = PttState.ERROR,
                    errorMessage = "Error stopping audio recording: ${e.message}"
                )
            }
            return
        }

        if (pcmBytes.isEmpty()) {
            _uiState.update {
                it.copy(
                    state = PttState.IDLE,
                    errorMessage = "No speech detected"
                )
            }
            return
        }

        val wavData = WavEncoder.encode(pcmBytes)
        processPipeline(wavData)
    }

    /**
     * Executes the cloud voice pipeline:
     * Saaras v3 STT -> Mayura v1 Translate -> Bulbul v3 TTS -> AudioPlayer Playback
     */
    private fun processPipeline(wavData: ByteArray) {
        val sourceLang = _uiState.value.sourceLanguage
        val targetLang = _uiState.value.targetLanguage

        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch(ioDispatcher) {
            // 1. Saaras v3 Speech-to-Text
            _uiState.update { it.copy(state = PttState.TRANSCRIBING, errorMessage = null) }
            val sttStart = timeProvider()
            val sttResult = sarvamApiClient.transcribe(wavData, sourceLang.bcp47Code)
            val sttDuration = (timeProvider() - sttStart).coerceAtLeast(0)

            val sttResponse = sttResult.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        state = PttState.ERROR,
                        errorMessage = error.message ?: "Speech-to-text failed",
                        latencies = LatencyStats(sttMs = sttDuration)
                    )
                }
                return@launch
            }

            val transcript = sttResponse.transcript.trim()
            if (transcript.isBlank()) {
                _uiState.update {
                    it.copy(
                        state = PttState.IDLE,
                        sourceTranscript = "",
                        errorMessage = "No speech detected",
                        latencies = LatencyStats(sttMs = sttDuration)
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    state = PttState.TRANSLATING,
                    sourceTranscript = transcript,
                    latencies = LatencyStats(sttMs = sttDuration)
                )
            }

            // 2. Mayura v1 Translation
            val transStart = timeProvider()
            val transResult = sarvamApiClient.translate(
                text = transcript,
                sourceLang = sourceLang.bcp47Code,
                targetLang = targetLang.bcp47Code
            )
            val transDuration = (timeProvider() - transStart).coerceAtLeast(0)

            val transResponse = transResult.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        state = PttState.ERROR,
                        errorMessage = error.message ?: "Translation failed",
                        latencies = LatencyStats(sttMs = sttDuration, translateMs = transDuration)
                    )
                }
                return@launch
            }

            val translated = transResponse.translatedText.trim()

            // P2P Transport transmission (Phone A -> Phone B)
            var netDuration: Long? = null
            val engine = transportEngine
            if (engine != null && engine.connectionState.value == TransportConnectionState.CONNECTED) {
                val transportMsg = TransportMessage(
                    type = TransportMessageType.TRANSLATION,
                    sourceLanguage = sourceLang.bcp47Code,
                    targetLanguage = targetLang.bcp47Code,
                    text = translated
                )
                val netStart = timeProvider()
                val sendResult = engine.send(transportMsg)
                if (sendResult.isSuccess) {
                    netDuration = (timeProvider() - netStart).coerceAtLeast(0)
                }
            }

            _uiState.update {
                it.copy(
                    state = PttState.SYNTHESIZING,
                    translatedText = translated,
                    isRemoteMessage = false,
                    latencies = LatencyStats(sttMs = sttDuration, translateMs = transDuration, netMs = netDuration)
                )
            }

            // 3. Bulbul v3 Text-to-Speech
            val ttsStart = timeProvider()
            val ttsResult = sarvamApiClient.synthesize(
                text = translated,
                targetLang = targetLang.bcp47Code
            )
            val ttsDuration = (timeProvider() - ttsStart).coerceAtLeast(0)
            val totalDuration = sttDuration + transDuration + ttsDuration + (netDuration ?: 0L)
            val finalLatencies = LatencyStats(
                sttMs = sttDuration,
                translateMs = transDuration,
                ttsMs = ttsDuration,
                netMs = netDuration,
                totalMs = totalDuration
            )


            val ttsResponse = ttsResult.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        state = PttState.ERROR,
                        errorMessage = error.message ?: "Speech synthesis failed",
                        latencies = finalLatencies
                    )
                }
                return@launch
            }

            val base64Audio = ttsResponse.audios.firstOrNull()
            if (base64Audio.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        state = PttState.ERROR,
                        errorMessage = "Speech synthesis failed: empty audio",
                        latencies = finalLatencies
                    )
                }
                return@launch
            }

            cachedAudioBase64 = base64Audio
            _uiState.update {
                it.copy(
                    state = PttState.PLAYING,
                    hasAudioToReplay = true,
                    latencies = finalLatencies
                )
            }

            // 4. Native Audio Playback
            val player = audioPlayer
            if (player != null) {
                player.playBase64Wav(
                    base64Wav = base64Audio,
                    onComplete = {
                        _uiState.update {
                            it.copy(state = PttState.IDLE, hasAudioToReplay = true)
                        }
                    },
                    onError = { playbackError ->
                        _uiState.update {
                            it.copy(
                                state = PttState.ERROR,
                                errorMessage = playbackError.message ?: "Audio playback error"
                            )
                        }
                    }
                )
            } else {
                _uiState.update {
                    it.copy(state = PttState.IDLE, hasAudioToReplay = true)
                }
            }
        }
    }

    /**
     * Replays cached translated speech audio without querying network APIs.
     */
    fun onReplayAudio() {
        val audio = cachedAudioBase64 ?: return
        if (_uiState.value.isBusy || _uiState.value.state == PttState.RECORDING) return

        if (_uiState.value.state == PttState.PLAYING) {
            audioPlayer?.stopAndRelease()
        }

        _uiState.update { it.copy(state = PttState.PLAYING) }

        val player = audioPlayer
        if (player != null) {
            player.playBase64Wav(
                base64Wav = audio,
                onComplete = {
                    _uiState.update { it.copy(state = PttState.IDLE, hasAudioToReplay = true) }
                },
                onError = { error ->
                    _uiState.update {
                        it.copy(
                            state = PttState.ERROR,
                            errorMessage = error.message ?: "Audio replay failed"
                        )
                    }
                }
            )
        } else {
            _uiState.update { it.copy(state = PttState.IDLE, hasAudioToReplay = true) }
        }
    }

    fun onSourceLanguageChange(language: Language) {
        if (_uiState.value.isBusy || _uiState.value.state == PttState.RECORDING) return
        _uiState.update { it.copy(sourceLanguage = language) }
    }

    fun onTargetLanguageChange(language: Language) {
        if (_uiState.value.isBusy || _uiState.value.state == PttState.RECORDING) return
        _uiState.update { it.copy(targetLanguage = language) }
    }

    fun onSwapLanguages() {
        if (_uiState.value.isBusy || _uiState.value.state == PttState.RECORDING) return
        val currentSource = _uiState.value.sourceLanguage
        val currentTarget = _uiState.value.targetLanguage
        _uiState.update {
            it.copy(sourceLanguage = currentTarget, targetLanguage = currentSource)
        }
    }

    fun onDismissError() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                state = if (it.state == PttState.ERROR) PttState.IDLE else it.state
            )
        }
    }

    fun onFeedback(isPositive: Boolean) {
        val current = _uiState.value
        // Only allow feedback when there is a completed translation
        if (current.translatedText.isBlank()) return
        // Only allow one feedback per interaction
        if (current.feedbackSubmitted != null) return

        _uiState.update { it.copy(feedbackSubmitted = isPositive) }

        // Persist to local JSON Lines file on a background thread
        viewModelScope.launch(ioDispatcher) {
            feedbackRepository?.save(
                sourceLanguage = current.sourceLanguage.bcp47Code,
                targetLanguage = current.targetLanguage.bcp47Code,
                sourceText = current.sourceTranscript,
                translatedText = current.translatedText,
                isPositive = isPositive
            )
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update {
            it.copy(
                micPermissionGranted = isGranted,
                errorMessage = if (!isGranted) "Microphone permission required. Please allow audio recording in device Settings." else null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        audioRecorder.release()
        audioPlayer?.release()
    }
}
