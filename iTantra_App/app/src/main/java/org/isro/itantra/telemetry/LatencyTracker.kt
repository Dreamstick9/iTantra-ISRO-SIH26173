package org.isro.itantra.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.isro.itantra.audio.AudioConfig

enum class TransceiverMode {
    TRANSMITTER, // STT / Microphone Recording Mode (Phone 1)
    RECEIVER     // TTS / Speech Synthesis Mode (Phone 2)
}

/**
 * Real-time telemetry data structure instrumented for ISRO SIH26173 Evaluation Metrics:
 * - Milestone 1: Physical Mic & AudioTrack latency (20% score)
 * - Milestone 2: Neural TTS Synthesis, RTF, and Tap-to-Ear Latency (20% score)
 */
data class TelemetryStats(
    // === MILESTONE 1: Audio I/O Metrics ===
    val pressToFirstBufferMs: Long = 0L,
    val releaseToPlaybackStartMs: Long = 0L,
    val recordedDurationMs: Long = 0L,
    val recordedBytes: Long = 0L,
    val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    val channels: Int = AudioConfig.CHANNEL_COUNT,
    val bitrateKbps: Int = (AudioConfig.BYTE_RATE * 8) / 1000,

    // === MILESTONE 2: Neural TTS Telemetry ===
    val ttsTapToFirstAudioMs: Long = 0L,      // T_E2E (Tap to DAC playback start)
    val ttsSynthesisLatencyMs: Long = 0L,     // T_synth (Neural compute time)
    val ttsAudioDurationMs: Long = 0L,        // T_audio (Synthesized speech length)
    val ttsRealTimeFactor: Float = 0.0f,      // RTF = T_synth / T_audio
    val ttsTextLengthChars: Int = 0,          // Input character count
    val ttsTextLengthWords: Int = 0,          // Input word count
    val ttsCharsPerSecond: Float = 0.0f,      // Text throughput (chars / T_synth)
    val ttsLanguage: String = "hi",           // e.g., "hi", "mr", "en", "ta"
    val ttsModelName: String = "Indic-TTS VITS", // Engine descriptor
    val isAlertPriority: Boolean = false,     // Emergency non-interruptible alert
    val activeMode: TransceiverMode = TransceiverMode.RECEIVER
) {
    /** Speed factor: how many times faster than real-time (1 / RTF) */
    val realTimeSpeedMultiplier: Float
        get() = if (ttsRealTimeFactor > 0.0001f) 1.0f / ttsRealTimeFactor else 0.0f

    /** True if synthesis runs faster than real-time speech */
    val isRealTimeCapable: Boolean
        get() = ttsRealTimeFactor in 0.001f..1.0f

    /** True if end-to-end latency meets the ITU-T G.114 < 400ms target */
    val isE2eCompliant: Boolean
        get() = ttsTapToFirstAudioMs in 1..400
}

/**
 * High-precision Telemetry Tracker for iTantra Neural Transceiver.
 * Instruments nanosecond timestamps for both Mic (M1) and Neural TTS (M2).
 */
class LatencyTracker(
    private val timeProvider: () -> Long = { System.nanoTime() }
) {

    private val _stats = MutableStateFlow(TelemetryStats())
    val stats = _stats.asStateFlow()

    // === Milestone 1 Timestamps ===
    private var pressTimestampNs: Long = 0L
    private var releaseTimestampNs: Long = 0L

    // === Milestone 2 TTS Timestamps ===
    private var ttsTapTimestampNs: Long = 0L
    private var ttsSynthStartNs: Long = 0L
    private var ttsSynthCompleteNs: Long = 0L

    // -------------------------------------------------------------
    // MILESTONE 1: Audio I/O Handlers
    // -------------------------------------------------------------

    fun onPttPressed() {
        pressTimestampNs = timeProvider()
        _stats.value = _stats.value.copy(activeMode = TransceiverMode.TRANSMITTER)
    }

    fun onFirstPcmBufferEmitted() {
        if (pressTimestampNs > 0L) {
            val deltaMs = (timeProvider() - pressTimestampNs) / 1_000_000L
            _stats.value = _stats.value.copy(pressToFirstBufferMs = deltaMs)
        }
    }

    fun onPttReleased(totalBytes: Long, durationMs: Long) {
        releaseTimestampNs = timeProvider()
        _stats.value = _stats.value.copy(
            recordedBytes = totalBytes,
            recordedDurationMs = durationMs
        )
    }

    fun onPlaybackStarted() {
        if (releaseTimestampNs > 0L) {
            val deltaMs = (timeProvider() - releaseTimestampNs) / 1_000_000L
            _stats.value = _stats.value.copy(releaseToPlaybackStartMs = deltaMs)
        }
    }

    // -------------------------------------------------------------
    // MILESTONE 2: Neural TTS Handlers
    // -------------------------------------------------------------

    fun onTtsRequested(
        text: String,
        language: String = "hi",
        isAlert: Boolean = false,
        modelName: String = "Indic-TTS VITS"
    ) {
        val now = timeProvider()
        ttsTapTimestampNs = now
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size

        _stats.value = _stats.value.copy(
            ttsTextLengthChars = text.length,
            ttsTextLengthWords = words,
            ttsLanguage = language,
            isAlertPriority = isAlert,
            ttsModelName = modelName,
            activeMode = TransceiverMode.RECEIVER
        )
    }

    fun onTtsSynthesisStarted() {
        ttsSynthStartNs = timeProvider()
    }

    fun onTtsSynthesisCompleted(
        pcmByteCount: Long,
        sampleRate: Int = 22050
    ) {
        val now = timeProvider()
        ttsSynthCompleteNs = now

        val tSynthMs = if (ttsSynthStartNs > 0L) {
            (now - ttsSynthStartNs) / 1_000_000L
        } else 0L

        val bytesPerSample = AudioConfig.BYTES_PER_SAMPLE
        val channels = AudioConfig.CHANNEL_COUNT
        val byteRate = sampleRate * channels * bytesPerSample
        val tAudioMs = if (byteRate > 0) (pcmByteCount * 1000L) / byteRate else 0L

        val rtf = if (tAudioMs > 0L) {
            tSynthMs.toFloat() / tAudioMs.toFloat()
        } else {
            0.0f
        }

        val chars = _stats.value.ttsTextLengthChars
        val charsPerSec = if (tSynthMs > 0L) {
            (chars.toFloat() * 1000f) / tSynthMs.toFloat()
        } else {
            0.0f
        }

        _stats.value = _stats.value.copy(
            ttsSynthesisLatencyMs = tSynthMs,
            ttsAudioDurationMs = tAudioMs,
            ttsRealTimeFactor = rtf,
            ttsCharsPerSecond = charsPerSec,
            sampleRate = sampleRate
        )
    }

    fun onTtsFirstAudioFramePlayed() {
        if (ttsTapTimestampNs > 0L) {
            val deltaMs = (timeProvider() - ttsTapTimestampNs) / 1_000_000L
            _stats.value = _stats.value.copy(ttsTapToFirstAudioMs = deltaMs)
        }
    }

    fun setMode(mode: TransceiverMode) {
        _stats.value = _stats.value.copy(activeMode = mode)
    }

    fun reset() {
        pressTimestampNs = 0L
        releaseTimestampNs = 0L
        ttsTapTimestampNs = 0L
        ttsSynthStartNs = 0L
        ttsSynthCompleteNs = 0L
        _stats.value = TelemetryStats()
    }
}
