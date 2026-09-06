package com.example.itantra.data

import java.util.UUID

// ============================================================================
// 1. CONNECTION & TRANSPORT MODELS
// ============================================================================

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    CONNECTED;

    val isConnected: Boolean get() = this == CONNECTED
    val isSearching: Boolean get() = this == SEARCHING
    val isDisconnected: Boolean get() = this == DISCONNECTED
}

enum class TransportType(val displayName: String) {
    NONE("None"),
    WIFI_DIRECT("Wi-Fi Direct"),
    WIFI_AWARE("Wi-Fi Aware (NAN)"),
    BLUETOOTH_RFCOMM("Bluetooth Classic (RFCOMM)"),
    BLE_GATT("Bluetooth Low Energy (GATT)"),
    LOOPBACK_SIMULATION("Embedded Loopback / NavIC Simulator")
}

// ============================================================================
// 2. PUSH-TO-TALK & TRANSMISSION MODES
// ============================================================================

enum class PttState {
    IDLE,
    RECORDING,
    PROCESSING;

    val isIdle: Boolean get() = this == IDLE
    val isRecording: Boolean get() = this == RECORDING
    val isProcessing: Boolean get() = this == PROCESSING
    val isActive: Boolean get() = this != IDLE

    fun toTransceiverState(): TransceiverState = when (this) {
        IDLE -> TransceiverState.IDLE
        RECORDING -> TransceiverState.RECORDING
        PROCESSING -> TransceiverState.TRANSCRIBING
    }
}

enum class TransceiverState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    TRANSMITTING,
    RECEIVING,
    SPEAKING;

    val isIdle: Boolean get() = this == IDLE
    val isRecording: Boolean get() = this == RECORDING
    val isTranscribing: Boolean get() = this == TRANSCRIBING
    val isTransmitting: Boolean get() = this == TRANSMITTING
    val isReceiving: Boolean get() = this == RECEIVING
    val isSpeaking: Boolean get() = this == SPEAKING
    val isActive: Boolean get() = this != IDLE

    /**
     * Backward-compatible mapping to legacy 3-state PttState.
     */
    fun toPttState(): PttState = when (this) {
        IDLE -> PttState.IDLE
        RECORDING -> PttState.RECORDING
        TRANSCRIBING,
        TRANSMITTING,
        RECEIVING,
        SPEAKING -> PttState.PROCESSING
    }
}

enum class TransmissionMode(val displayName: String, val description: String) {
    PUSH_TO_TALK("Push-to-Talk (Walkie-Talkie)", "Walkie-talkie half-duplex. Hold or tap button to speak."),
    CONTINUOUS("Continuous Voice (Phone Mode)", "Phone mode. Hands-free VAD pause-detected streaming.")
}

enum class ContinuousModeState {
    IDLE,
    LISTENING,
    SPEECH_DETECTED,
    RECORDING,
    POSSIBLE_END,
    FINALIZING,
    TRANSCRIBING,
    TRANSMITTING;

    val isIdle: Boolean get() = this == IDLE
    val isListening: Boolean get() = this == LISTENING
    val isSpeechDetected: Boolean get() = this == SPEECH_DETECTED
    val isRecording: Boolean get() = this == RECORDING
    val isPossibleEnd: Boolean get() = this == POSSIBLE_END
    val isFinalizing: Boolean get() = this == FINALIZING
    val isTranscribing: Boolean get() = this == TRANSCRIBING
    val isTransmitting: Boolean get() = this == TRANSMITTING
    val isActive: Boolean get() = this != IDLE
}

// ============================================================================
// 3. MULTILINGUAL SUPPORT (10 SIH TARGET LANGUAGES)
// ============================================================================

typealias SupportedLanguage = com.example.itantra.language.SupportedLanguage
typealias Language = com.example.itantra.language.SupportedLanguage

// ============================================================================
// 4. HARDWARE & ON-DEVICE AI SUBSYSTEM STATUS
// ============================================================================

enum class SubsystemState {
    OFFLINE,
    INITIALIZING,
    READY,
    ACTIVE,
    ERROR;

    val isOperational: Boolean get() = this == READY || this == ACTIVE
    val isReady: Boolean get() = this == READY
    val isActive: Boolean get() = this == ACTIVE
    val isError: Boolean get() = this == ERROR
}

data class SubsystemStatus(
    val audio: SubsystemState = SubsystemState.READY,
    val stt: SubsystemState = SubsystemState.READY,
    val tts: SubsystemState = SubsystemState.READY,
    val audioErrorMessage: String? = null,
    val sttErrorMessage: String? = null,
    val ttsErrorMessage: String? = null
) {
    val isFullyReady: Boolean
        get() = audio.isOperational && stt.isOperational && tts.isOperational

    val hasError: Boolean
        get() = audio.isError || stt.isError || tts.isError
}

// ============================================================================
// 5. EVALUATION LATENCY METRICS & VERTICAL SLICE TIMESTAMPS
// ============================================================================

data class VerticalSliceTimestamps(
    val t0PttReleased: Long = 0L,
    val t1SttComplete: Long = 0L,
    val t2Transmitted: Long = 0L,
    val t3Received: Long = 0L,
    val t4TtsComplete: Long = 0L,
    val t5PlaybackStarted: Long = 0L,
    val sttLatencyMs: Long = 0L,
    val networkLatencyMs: Long = 0L,
    val ttsLatencyMs: Long = 0L,
    val endToEndLatencyMs: Long = 0L
) {
    /**
     * Computes any missing latency deltas from the timestamps if not explicitly set.
     */
    fun withComputedLatencies(): VerticalSliceTimestamps {
        val stt = if (sttLatencyMs > 0L) sttLatencyMs else if (t1SttComplete >= t0PttReleased && t0PttReleased > 0L) (t1SttComplete - t0PttReleased) else 0L
        val net = if (networkLatencyMs > 0L) networkLatencyMs else if (t3Received >= t2Transmitted && t2Transmitted > 0L) (t3Received - t2Transmitted) else 0L
        val tts = if (ttsLatencyMs > 0L) ttsLatencyMs else if (t4TtsComplete >= t3Received && t3Received > 0L) (t4TtsComplete - t3Received) else 0L
        val e2e = if (endToEndLatencyMs > 0L) endToEndLatencyMs else if (t5PlaybackStarted >= t0PttReleased && t0PttReleased > 0L) (t5PlaybackStarted - t0PttReleased) else (stt + net + tts)
        return copy(
            sttLatencyMs = stt,
            networkLatencyMs = net,
            ttsLatencyMs = tts,
            endToEndLatencyMs = e2e
        )
    }

    fun toLatencyMetrics(
        audioDurationMs: Long = 0L,
        characterCount: Int = 0,
        compressedByteSize: Int = 0
    ): LatencyMetrics {
        val computed = withComputedLatencies()
        return LatencyMetrics(
            sttLatencyMs = computed.sttLatencyMs,
            transmissionLatencyMs = computed.networkLatencyMs,
            ttsLatencyMs = computed.ttsLatencyMs,
            endToEndLatencyMs = computed.endToEndLatencyMs,
            audioDurationMs = audioDurationMs,
            characterCount = characterCount,
            compressedByteSize = compressedByteSize,
            timestamp = if (t5PlaybackStarted > 0L) t5PlaybackStarted else System.currentTimeMillis(),
            t0PttReleased = computed.t0PttReleased,
            t1SttComplete = computed.t1SttComplete,
            t2Transmitted = computed.t2Transmitted,
            t3Received = computed.t3Received,
            t4TtsComplete = computed.t4TtsComplete,
            t5PlaybackStarted = computed.t5PlaybackStarted
        )
    }

    fun formattedSummary(): String =
        "E2E: ${endToEndLatencyMs}ms [STT: ${sttLatencyMs}ms | Net: ${networkLatencyMs}ms | TTS: ${ttsLatencyMs}ms]"
}

data class LatencyMetrics(
    val sttLatencyMs: Long = 185L,
    val textNormLatencyMs: Long = 12L,
    val transmissionLatencyMs: Long = 42L,
    val ttsLatencyMs: Long = 118L,
    val realTimeFactor: Double = 0.18,
    val endToEndLatencyMs: Long = 345L,
    val audioDurationMs: Long = 1900L,
    val characterCount: Int = 24,
    val compressedByteSize: Int = 24,
    val timestamp: Long = System.currentTimeMillis(),
    val t0PttReleased: Long = 0L,
    val t1SttComplete: Long = 0L,
    val t2Transmitted: Long = 0L,
    val t3Received: Long = 0L,
    val t4TtsComplete: Long = 0L,
    val t5PlaybackStarted: Long = 0L
) {
    fun formattedSummary(): String =
        "E2E: ${endToEndLatencyMs}ms | STT: ${sttLatencyMs}ms | Trans: ${transmissionLatencyMs}ms | TTS: ${ttsLatencyMs}ms (RTF: ${"%.2f".format(realTimeFactor)})"

    fun toVerticalSliceTimestamps(): VerticalSliceTimestamps = VerticalSliceTimestamps(
        t0PttReleased = t0PttReleased,
        t1SttComplete = t1SttComplete,
        t2Transmitted = t2Transmitted,
        t3Received = t3Received,
        t4TtsComplete = t4TtsComplete,
        t5PlaybackStarted = t5PlaybackStarted,
        sttLatencyMs = sttLatencyMs,
        networkLatencyMs = transmissionLatencyMs,
        ttsLatencyMs = ttsLatencyMs,
        endToEndLatencyMs = endToEndLatencyMs
    )
}

data class CompressionMetrics(
    val originalBytes: Int = 0,
    val compressedBytes: Int = 0,
    val compressionRatio: Double = 1.0,
    val savedPercentage: Double = 0.0,
    val algorithm: String = "Unishox2",
    val sampleText: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun formattedSummary(): String =
        "Original: ${originalBytes}B | Compressed: ${compressedBytes}B | Ratio: ${"%.2f".format(compressionRatio)}x (Saved: ${"%.1f".format(savedPercentage)}%)"

    companion object {
        fun from(text: String, compressionEngine: com.example.itantra.compression.CompressionEngine): CompressionMetrics {
            val raw = text.toByteArray(Charsets.UTF_8).size
            val compBytes = compressionEngine.compressText(text)
            val comp = compBytes.size.coerceAtLeast(1)
            val ratio = if (comp > 0) raw.toDouble() / comp.toDouble() else 1.0
            val saved = if (raw > 0) ((raw - comp).toDouble() / raw.toDouble()) * 100.0 else 0.0
            return CompressionMetrics(
                originalBytes = raw,
                compressedBytes = comp,
                compressionRatio = ratio,
                savedPercentage = saved,
                algorithm = "Unishox2",
                sampleText = text,
                timestamp = System.currentTimeMillis()
            )
        }

        fun from(text: String, compressedBytesCount: Int, algorithm: String = "Unishox2"): CompressionMetrics {
            val raw = text.toByteArray(Charsets.UTF_8).size
            val comp = compressedBytesCount.coerceAtLeast(1)
            val ratio = if (comp > 0) raw.toDouble() / comp.toDouble() else 1.0
            val saved = if (raw > 0) ((raw - comp).toDouble() / raw.toDouble()) * 100.0 else 0.0
            return CompressionMetrics(
                originalBytes = raw,
                compressedBytes = comp,
                compressionRatio = ratio,
                savedPercentage = saved,
                algorithm = algorithm,
                sampleText = text,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

// ============================================================================
// 6. ISRO / NAVIC EMERGENCY ALERTS
// ============================================================================

enum class AlertSeverity {
    NONE,
    INFO,
    WARNING,
    CRITICAL,
    LIFE_SAFETY
}

enum class AlertCategory(val serviceCode: String) {
    GENERAL("0000"),
    CYCLONE("1111"),
    TSUNAMI("0011"),
    HIGH_WAVE("0111"),
    POTENTIAL_FISHING_ZONE("0020"),
    DISTRESS_SOS("9999")
}

data class EmergencyAlertState(
    val isActive: Boolean = false,
    val alertId: String? = null,
    val title: String = "",
    val message: String = "",
    val severity: AlertSeverity = AlertSeverity.NONE,
    val category: AlertCategory = AlertCategory.GENERAL,
    val source: String = "",
    val timestamp: Long = 0L,
    val isAcknowledged: Boolean = false
)

// ============================================================================
// 7. RECEIVED & OUTGOING MESSAGES
// ============================================================================

enum class MessageType {
    VOICE_NOTE,
    EMERGENCY_ALERT,
    HEARTBEAT_PING,
    DELIVERY_ACK
}

enum class MessagePlaybackStatus {
    UNPLAYED,
    PLAYING,
    PLAYED,
    FAILED
}

data class ReceivedMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val text: String,
    val originalLanguage: Language,
    val targetLanguage: Language = originalLanguage,
    val translatedText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.VOICE_NOTE,
    val isEmergency: Boolean = (messageType == MessageType.EMERGENCY_ALERT),
    val playbackStatus: MessagePlaybackStatus = MessagePlaybackStatus.UNPLAYED,
    val latencyMetrics: LatencyMetrics? = null,
    val rawUtf8ByteSize: Int = text.toByteArray(Charsets.UTF_8).size,
    val compressedByteSize: Int = 0,
    val isOutgoing: Boolean = false
) {
    val displayText: String get() = translatedText ?: text
    val fitsNavIcSubframe: Boolean get() = (if (compressedByteSize > 0) compressedByteSize else rawUtf8ByteSize) <= 27
    val fitsNavIcPacket: Boolean get() = (if (compressedByteSize > 0) compressedByteSize else rawUtf8ByteSize) <= 277
}

// ============================================================================
// 8. ROOT APPLICATION DOMAIN STATE (AppState)
// ============================================================================

data class AudioDebugInfo(
    val durationMs: Long,
    val byteCount: Int,
    val message: String = "Audio captured successfully",
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val timestamp: Long = System.currentTimeMillis()
)

data class SttDiagnosticState(
    val isModelLoaded: Boolean = false,
    val isOffline: Boolean = true,
    val language: String = "English",
    val processingTimeMs: Long = 0L,
    val recognizedText: String = "",
    val errorMessage: String? = null
)

data class TtsUiState(
    val inputText: String = "Hello, this is iTantra.",
    val playbackState: com.example.itantra.tts.TtsPlaybackState = com.example.itantra.tts.TtsPlaybackState.IDLE,
    val isModelLoaded: Boolean = false,
    val synthesisLatencyMs: Long = 0L,
    val audioDurationMs: Long = 0L,
    val realTimeFactor: Double = 0.0,
    val sampleRate: Int = 16000,
    val errorMessage: String? = null
)

data class AppState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val transportType: TransportType = TransportType.NONE,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val pttState: PttState = PttState.IDLE,
    val transceiverState: TransceiverState = TransceiverState.IDLE,
    val continuousModeState: ContinuousModeState = ContinuousModeState.IDLE,
    val isPttPressed: Boolean = false,
    val isPttLocked: Boolean = false,
    val transmissionMode: TransmissionMode = TransmissionMode.PUSH_TO_TALK,
    val inputLanguage: Language = Language.DEFAULT,
    val outputLanguage: Language = Language.DEFAULT,
    val subsystems: SubsystemStatus = SubsystemStatus(),
    val sttDiagnostics: SttDiagnosticState = SttDiagnosticState(),
    val ttsState: TtsUiState = TtsUiState(),
    val emergencyAlert: EmergencyAlertState = EmergencyAlertState(),
    val messages: List<ReceivedMessage> = emptyList(),
    val currentLiveTranscription: String = "",
    val isAudioPlaying: Boolean = false,
    val audioLevel: Float = 0f,
    val verticalSliceTimestamps: VerticalSliceTimestamps = VerticalSliceTimestamps(),
    val lastLatencyMetrics: LatencyMetrics? = null,
    val lastCompressionMetrics: CompressionMetrics? = null,
    val lastAudioDebugInfo: AudioDebugInfo? = null,
    val hasAudioPermission: Boolean = false,
    val statusMessage: String? = "Ready",
    val errorMessage: String? = null
) {
    fun withTransceiverState(newState: TransceiverState): AppState = copy(
        transceiverState = newState,
        pttState = newState.toPttState()
    )

    fun withContinuousModeState(newState: ContinuousModeState): AppState = copy(
        continuousModeState = newState
    )

    companion object {
        val INITIAL = AppState()
    }
}

