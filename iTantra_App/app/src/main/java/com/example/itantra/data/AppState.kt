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
}

enum class TransmissionMode(val displayName: String, val description: String) {
    PUSH_TO_TALK("Push-to-Talk (Walkie-Talkie)", "Walkie-talkie half-duplex. Hold or tap button to speak."),
    CONTINUOUS("Continuous Voice (Phone Mode)", "Phone mode. Hands-free VAD pause-detected streaming.")
}

// ============================================================================
// 3. MULTILINGUAL SUPPORT (10 SIH TARGET LANGUAGES)
// ============================================================================

enum class Language(
    val isoCode: String,
    val bcp47Tag: String,
    val englishName: String,
    val nativeName: String,
    val scriptName: String
) {
    HINDI("hi", "hi-IN", "Hindi", "हिन्दी", "Devanagari"),
    GUJARATI("gu", "gu-IN", "Gujarati", "ગુજરાતી", "Gujarati"),
    MARATHI("mr", "mr-IN", "Marathi", "मराठी", "Devanagari"),
    KANNADA("kn", "kn-IN", "Kannada", "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", "ml-IN", "Malayalam", "മലയാളം", "Malayalam"),
    TAMIL("ta", "ta-IN", "Tamil", "தமிழ்", "Tamil"),
    TELUGU("te", "te-IN", "Telugu", "తెలుగు", "Telugu"),
    ODIA("or", "or-IN", "Odia", "ଓଡ଼ିଆ", "Odia"),
    BENGALI("bn", "bn-IN", "Bengali", "বাংলা", "Bengali"),
    ENGLISH("en", "en-IN", "English", "English", "Latin");

    companion object {
        val DEFAULT: Language = HINDI

        fun fromCode(code: String?, fallback: Language = HINDI): Language {
            if (code.isNullOrBlank()) return fallback
            val cleaned = code.trim().lowercase()
            return entries.firstOrNull {
                it.isoCode.equals(cleaned, ignoreCase = true) ||
                it.bcp47Tag.equals(cleaned, ignoreCase = true) ||
                cleaned.startsWith(it.isoCode)
            } ?: fallback
        }
    }
}

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
// 5. EVALUATION LATENCY METRICS
// ============================================================================

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
    val timestamp: Long = System.currentTimeMillis()
) {
    fun formattedSummary(): String =
        "E2E: ${endToEndLatencyMs}ms | STT: ${sttLatencyMs}ms | Trans: ${transmissionLatencyMs}ms | TTS: ${ttsLatencyMs}ms (RTF: ${"%.2f".format(realTimeFactor)})"
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

data class AppState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val transportType: TransportType = TransportType.NONE,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val pttState: PttState = PttState.IDLE,
    val isPttPressed: Boolean = false,
    val isPttLocked: Boolean = false,
    val transmissionMode: TransmissionMode = TransmissionMode.PUSH_TO_TALK,
    val inputLanguage: Language = Language.DEFAULT,
    val outputLanguage: Language = Language.DEFAULT,
    val subsystems: SubsystemStatus = SubsystemStatus(),
    val sttDiagnostics: SttDiagnosticState = SttDiagnosticState(),
    val emergencyAlert: EmergencyAlertState = EmergencyAlertState(),
    val messages: List<ReceivedMessage> = emptyList(),
    val currentLiveTranscription: String = "",
    val isAudioPlaying: Boolean = false,
    val audioLevel: Float = 0f,
    val lastLatencyMetrics: LatencyMetrics? = null,
    val lastAudioDebugInfo: AudioDebugInfo? = null,
    val hasAudioPermission: Boolean = false,
    val statusMessage: String? = "Ready",
    val errorMessage: String? = null
) {
    companion object {
        val INITIAL = AppState()
    }
}
