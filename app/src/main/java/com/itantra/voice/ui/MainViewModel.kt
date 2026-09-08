package com.itantra.voice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.voice.audio.AudioPlayer
import com.itantra.voice.audio.AudioRecorder
import com.itantra.voice.audio.WavEncoder
import com.itantra.voice.data.Language
import com.itantra.voice.feedback.FeedbackRepository
import com.itantra.voice.pipeline.SpeechPipeline
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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * States of the push-to-talk pipeline.
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
 * Per-stage latency telemetry in milliseconds. The SIH26173 brief scores latency at
 * 20%, specifically the delta between a sentence being spoken and the same sentence
 * beginning playback on the receiving phone, so each stage is tracked separately.
 */
data class LatencyStats(
    val sttMs: Long = 0L,
    val translateMs: Long = 0L,
    val ttsMs: Long = 0L,
    val netMs: Long? = null,
    val totalMs: Long = 0L
)

/**
 * Immutable UI state observed by [MainScreen].
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
    val isRemoteMessage: Boolean = false,
    /** When armed, outgoing traffic is flagged ALERT and played at alarm volume. */
    val isEmergencyMode: Boolean = false,
    /** Name of the engine serving requests, e.g. "On-device" or "Sarvam Cloud". */
    val engineName: String = "",
    /** True while an incoming ALERT is being announced. */
    val isAlertPlaying: Boolean = false
) {
    /** True while a pipeline stage is in flight and input must be rejected. */
    val isBusy: Boolean
        get() = state == PttState.TRANSCRIBING ||
            state == PttState.TRANSLATING ||
            state == PttState.SYNTHESIZING

    /** True when the operator may start a new press. */
    val canRecord: Boolean
        get() = !isBusy && state != PttState.RECORDING
}

/**
 * Orchestrates the transceiver: microphone capture -> [SpeechPipeline] STT ->
 * translate -> TTS -> playback, with the recognised text mirrored to the paired
 * phone over [TransportEngine].
 *
 * The pipeline is injected rather than constructed so the same FSM drives both the
 * offline on-device engine and the Sarvam cloud engine.
 */
class MainViewModel(
    private val audioRecorder: AudioRecorder = AudioRecorder(),
    private var speechPipeline: SpeechPipeline? = null,
    audioPlayer: AudioPlayer? = null,
    transportEngine: TransportEngine? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private var audioPlayer: AudioPlayer? = audioPlayer
    private var transportEngine: TransportEngine? = null
    private var feedbackRepository: FeedbackRepository? = null
    private var pressStartTimeMs: Long = 0L
    private var pipelineJob: Job? = null

    /** Last synthesised utterance, retained so replay never re-runs the pipeline. */
    var cachedAudio: ByteArray? = null
        private set

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRecorder.amplitude.collect { amp -> publishAmplitude(amp, fromRecorder = true) }
        }
        speechPipeline?.let { setSpeechPipeline(it) }
        transportEngine?.let { setTransportEngine(it) }
    }

    /**
     * Routes an input level to the waveform, ignoring the source that is not currently
     * capturing so the two never fight over the same field.
     */
    private fun publishAmplitude(amp: Float, fromRecorder: Boolean) {
        val pipelineOwnsMic = speechPipeline?.capturesOwnAudio == true
        if (fromRecorder == pipelineOwnsMic) return

        if (_uiState.value.state == PttState.RECORDING) {
            _uiState.update { it.copy(amplitude = amp) }
        } else if (_uiState.value.amplitude != 0f) {
            _uiState.update { it.copy(amplitude = 0f) }
        }
    }

    // ─── Wiring ──────────────────────────────────────────────────────────────

    fun initAudioPlayer(cacheDir: File) {
        if (this.audioPlayer == null) this.audioPlayer = AudioPlayer(cacheDir)
    }

    fun initFeedbackRepository(filesDir: File) {
        if (feedbackRepository == null) feedbackRepository = FeedbackRepository(filesDir)
    }

    fun setAudioPlayer(player: AudioPlayer) {
        this.audioPlayer = player
    }

    /**
     * Installs the speech engine and publishes its name for the status line.
     */
    fun setSpeechPipeline(pipeline: SpeechPipeline) {
        if (speechPipeline === pipeline && _uiState.value.engineName.isNotEmpty()) return
        speechPipeline = pipeline

        if (pipeline.capturesOwnAudio) {
            viewModelScope.launch {
                pipeline.amplitude.collect { amp -> publishAmplitude(amp, fromRecorder = false) }
            }
        }

        viewModelScope.launch {
            // Settle which engine will serve utterances before the first press: the
            // capture path (own recogniser session vs. AudioRecord) depends on it.
            runCatching { pipeline.prepare() }
            val available = runCatching { pipeline.isAvailable() }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    engineName = pipeline.displayName,
                    errorMessage = if (available) it.errorMessage
                    else "No speech engine is ready. Install an offline voice pack, or add a Sarvam API key."
                )
            }
        }
    }

    /**
     * Subscribes to a transport engine.
     *
     * Guarded against re-entry: each call previously launched four new collectors, so
     * calling it twice made every received message be spoken twice.
     */
    fun setTransportEngine(engine: TransportEngine) {
        if (transportEngine === engine) return
        transportEngine = engine

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
            engine.incomingMessages.collect { message -> handleIncomingRemoteMessage(message) }
        }
    }

    // ─── Receiving ───────────────────────────────────────────────────────────

    /**
     * Handles a message from the paired phone and speaks it.
     *
     * Network latency is deliberately not derived from the sender's timestamp: the two
     * handsets have independent clocks, so that subtraction reports skew rather than
     * transit time. Only locally-measured send latency is reported.
     */
    private fun handleIncomingRemoteMessage(message: TransportMessage) {
        val isAlert = message.type == TransportMessageType.ALERT
        val target = Language.fromBcp47(message.targetLanguage)

        _uiState.update {
            it.copy(
                sourceTranscript = message.text,
                translatedText = message.text,
                targetLanguage = target ?: it.targetLanguage,
                isRemoteMessage = true,
                isAlertPlaying = isAlert,
                feedbackSubmitted = null
            )
        }

        speakText(
            text = message.text,
            target = target ?: _uiState.value.targetLanguage,
            isEmergency = isAlert
        )
    }

    /**
     * Synthesises and plays [text]. Emergency traffic is routed to the alarm stream at
     * full volume, per the SIH26173 requirement that alert messages "will be announced
     * at highest volume non-interruptible".
     */
    private fun speakText(text: String, target: Language, isEmergency: Boolean) {
        val pipeline = speechPipeline ?: run {
            setError("No speech engine configured.")
            return
        }

        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(state = PttState.SYNTHESIZING, errorMessage = null) }

            val ttsStart = timeProvider()
            val result = pipeline.synthesize(text, target, isEmergency)
            val ttsMs = (timeProvider() - ttsStart).coerceAtLeast(0)

            val synthesis = result.getOrElse { error ->
                setError(error.message ?: "Speech synthesis failed")
                return@launch
            }

            _uiState.update {
                it.copy(latencies = it.latencies.copy(ttsMs = ttsMs, totalMs = ttsMs))
            }
            playSynthesis(synthesis.wavBytes, isEmergency)
        }
    }

    // ─── Push-to-talk ────────────────────────────────────────────────────────

    /**
     * Begins capture. Rejects the press when the microphone permission is missing so
     * the recorder is never constructed in a state that can only throw.
     */
    fun onPttPress() {
        val current = _uiState.value
        if (!current.canRecord) return

        if (!current.micPermissionGranted) {
            // Refusing the press is not a pipeline failure: stay IDLE so the control is
            // immediately usable once the permission is granted.
            _uiState.update {
                it.copy(errorMessage = "Microphone permission is required to transmit.")
            }
            return
        }

        if (current.state == PttState.PLAYING) audioPlayer?.stopAndRelease()

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
                hasAudioToReplay = false,
                isRemoteMessage = false,
                isAlertPlaying = false
            )
        }

        try {
            // Only one capturer may hold the microphone. A pipeline that runs its own
            // recogniser session opens the mic itself; running AudioRecord alongside it
            // makes one of the two receive silence.
            if (speechPipeline?.capturesOwnAudio == true) {
                speechPipeline?.beginCapture(current.sourceLanguage)
            } else {
                audioRecorder.startRecording(viewModelScope)
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    state = PttState.ERROR,
                    isHolding = false,
                    errorMessage = e.message ?: "Could not start the microphone."
                )
            }
        }
    }

    /**
     * Ends capture and runs the pipeline. Presses shorter than [MIN_PRESS_MS] are
     * treated as accidental taps and discarded.
     */
    fun onPttRelease() {
        if (_uiState.value.state != PttState.RECORDING) return
        val pressDuration = timeProvider() - pressStartTimeMs
        _uiState.update { it.copy(isHolding = false) }

        val pipelineOwnsMic = speechPipeline?.capturesOwnAudio == true

        // A press too short to be speech is discarded before any engine work is done.
        if (pressDuration < MIN_PRESS_MS) {
            if (pipelineOwnsMic) speechPipeline?.cancelCapture()
            viewModelScope.launch {
                if (!pipelineOwnsMic) runCatching { audioRecorder.stopRecording() }
                _uiState.update {
                    it.copy(state = PttState.IDLE, errorMessage = "Hold the button while speaking.")
                }
            }
            return
        }

        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            val wavData: ByteArray = if (pipelineOwnsMic) {
                // The pipeline captured its own audio; nothing to hand over.
                ByteArray(0)
            } else {
                val pcmBytes = try {
                    audioRecorder.stopRecording()
                } catch (e: Exception) {
                    setError("Error stopping the microphone: ${e.message}")
                    return@launch
                }

                if (pcmBytes.isEmpty()) {
                    _uiState.update {
                        it.copy(state = PttState.IDLE, errorMessage = "No speech detected.")
                    }
                    return@launch
                }

                // Encoding a 30 s capture copies ~1 MB; keep it off the main thread.
                withContext(ioDispatcher) { WavEncoder.encode(pcmBytes) }
            }

            runPipeline(wavData)
        }
    }

    /**
     * Runs STT -> translate -> transmit -> TTS -> playback.
     *
     * The paired phone is sent the text as soon as translation completes, before local
     * synthesis, so the receiver starts speaking as early as possible — the delta the
     * brief scores.
     */
    private suspend fun runPipeline(wavData: ByteArray) {
        val pipeline = speechPipeline ?: run {
            setError("No speech engine configured.")
            return
        }

        val sourceLang = _uiState.value.sourceLanguage
        val targetLang = _uiState.value.targetLanguage
        val isEmergency = _uiState.value.isEmergencyMode

        // 1. Speech to text
        _uiState.update { it.copy(state = PttState.TRANSCRIBING, errorMessage = null) }
        val sttStart = timeProvider()
        val sttResult = pipeline.transcribe(wavData, sourceLang)
        val sttMs = (timeProvider() - sttStart).coerceAtLeast(0)

        val transcript = sttResult.getOrElse { error ->
            setError(error.message ?: "Speech recognition failed", LatencyStats(sttMs = sttMs))
            return
        }.transcript.trim()

        if (transcript.isBlank()) {
            _uiState.update {
                it.copy(
                    state = PttState.IDLE,
                    sourceTranscript = "",
                    errorMessage = "No speech detected.",
                    latencies = LatencyStats(sttMs = sttMs)
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                state = PttState.TRANSLATING,
                sourceTranscript = transcript,
                latencies = LatencyStats(sttMs = sttMs)
            )
        }

        // 2. Translation
        val transStart = timeProvider()
        val transResult = pipeline.translate(transcript, sourceLang, targetLang)
        val translateMs = (timeProvider() - transStart).coerceAtLeast(0)

        val translated = transResult.getOrElse { error ->
            setError(
                error.message ?: "Translation failed",
                LatencyStats(sttMs = sttMs, translateMs = translateMs)
            )
            return
        }.text.trim()

        // 3. Transmit to the paired phone before synthesising locally
        var netMs: Long? = null
        val engine = transportEngine
        if (engine != null && engine.connectionState.value == TransportConnectionState.CONNECTED) {
            val outgoing = TransportMessage(
                type = if (isEmergency) TransportMessageType.ALERT else TransportMessageType.TRANSLATION,
                sourceLanguage = sourceLang.bcp47Code,
                targetLanguage = targetLang.bcp47Code,
                text = translated,
                priority = if (isEmergency) 1 else 0
            )
            val netStart = timeProvider()
            val sendResult = engine.send(outgoing)
            netMs = (timeProvider() - netStart).coerceAtLeast(0)
            if (sendResult.isFailure) {
                netMs = null
                _uiState.update {
                    it.copy(errorMessage = "Could not reach the paired device: ${sendResult.exceptionOrNull()?.message}")
                }
            }
        }

        _uiState.update {
            it.copy(
                state = PttState.SYNTHESIZING,
                translatedText = translated,
                isRemoteMessage = false,
                latencies = LatencyStats(sttMs = sttMs, translateMs = translateMs, netMs = netMs)
            )
        }

        // 4. Synthesis
        val ttsStart = timeProvider()
        val ttsResult = pipeline.synthesize(translated, targetLang, isEmergency)
        val ttsMs = (timeProvider() - ttsStart).coerceAtLeast(0)

        val finalLatencies = LatencyStats(
            sttMs = sttMs,
            translateMs = translateMs,
            ttsMs = ttsMs,
            netMs = netMs,
            totalMs = sttMs + translateMs + ttsMs + (netMs ?: 0L)
        )

        val synthesis = ttsResult.getOrElse { error ->
            setError(error.message ?: "Speech synthesis failed", finalLatencies)
            return
        }

        _uiState.update { it.copy(latencies = finalLatencies) }
        playSynthesis(synthesis.wavBytes, isEmergency)
    }

    /**
     * Plays synthesised audio and returns the FSM to IDLE when it completes.
     */
    private fun playSynthesis(wavBytes: ByteArray, isEmergency: Boolean) {
        if (wavBytes.isEmpty()) {
            _uiState.update {
                it.copy(state = PttState.IDLE, hasAudioToReplay = false, isAlertPlaying = false)
            }
            return
        }

        cachedAudio = wavBytes
        _uiState.update { it.copy(state = PttState.PLAYING, hasAudioToReplay = true) }

        val player = audioPlayer
        if (player == null) {
            _uiState.update {
                it.copy(state = PttState.IDLE, hasAudioToReplay = true, isAlertPlaying = false)
            }
            return
        }

        player.playWavBytes(
            wavBytes = wavBytes,
            isEmergency = isEmergency,
            onComplete = {
                _uiState.update {
                    it.copy(state = PttState.IDLE, hasAudioToReplay = true, isAlertPlaying = false)
                }
            },
            onError = { error ->
                _uiState.update {
                    it.copy(
                        state = PttState.ERROR,
                        errorMessage = error.message ?: "Audio playback failed",
                        isAlertPlaying = false
                    )
                }
            }
        )
    }

    /** Replays the last utterance without re-running the pipeline. */
    fun onReplayAudio() {
        val audio = cachedAudio ?: return
        val current = _uiState.value
        if (current.isBusy || current.state == PttState.RECORDING) return

        if (current.state == PttState.PLAYING) audioPlayer?.stopAndRelease()
        playSynthesis(audio, current.isAlertPlaying)
    }

    // ─── Transport controls ──────────────────────────────────────────────────

    fun onStartDiscovery() = transportEngine?.startDiscovery() ?: Unit
    fun onStopDiscovery() = transportEngine?.stopDiscovery() ?: Unit
    fun onConnectPeer(peer: DiscoveredPeer) = transportEngine?.connect(peer) ?: Unit
    fun onDisconnectTransport() = transportEngine?.disconnect() ?: Unit

    // ─── Settings ────────────────────────────────────────────────────────────

    fun onSourceLanguageChange(language: Language) {
        if (!_uiState.value.canRecord) return
        _uiState.update { it.copy(sourceLanguage = language) }
    }

    fun onTargetLanguageChange(language: Language) {
        if (!_uiState.value.canRecord) return
        _uiState.update { it.copy(targetLanguage = language) }
    }

    fun onSwapLanguages() {
        if (!_uiState.value.canRecord) return
        _uiState.update {
            it.copy(sourceLanguage = it.targetLanguage, targetLanguage = it.sourceLanguage)
        }
    }

    /** Arms or disarms emergency mode. */
    fun onToggleEmergencyMode() {
        if (!_uiState.value.canRecord) return
        _uiState.update { it.copy(isEmergencyMode = !it.isEmergencyMode) }
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
        if (current.translatedText.isBlank()) return
        if (current.feedbackSubmitted != null) return

        _uiState.update { it.copy(feedbackSubmitted = isPositive) }

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
                errorMessage = if (isGranted) {
                    // Clear only the permission prompt, never an unrelated pipeline error.
                    it.errorMessage?.takeUnless { msg -> msg.contains("Microphone permission") }
                } else {
                    "Microphone permission is required. Allow audio recording in Settings."
                }
            )
        }
    }

    private fun setError(message: String, latencies: LatencyStats? = null) {
        _uiState.update {
            it.copy(
                state = PttState.ERROR,
                errorMessage = message,
                isAlertPlaying = false,
                latencies = latencies ?: it.latencies
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        audioRecorder.release()
        audioPlayer?.release()
        speechPipeline?.release()
    }

    private companion object {
        /** Presses below this are treated as accidental taps. */
        const val MIN_PRESS_MS = 300L
    }
}
