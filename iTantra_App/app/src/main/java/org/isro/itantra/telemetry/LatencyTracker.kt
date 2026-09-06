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
 * - Milestone 1: Physical Mic & AudioTrack latency, 256 kbps baseline, buffer memory (20% score)
 * - Milestone 2: Neural TTS Synthesis, RTF, and Tap-to-Ear Latency (20% score)
 * - Milestone 6: Live Metrics HUD Dashboard
 */
data class TelemetryStats(
    // === MILESTONE 1: Audio I/O Metrics (Nanosecond Monotonic Precision) ===
    val micStartLagNs: Long = 0L,                  // Press to first PCM buffer (nanoseconds)
    val pressToFirstBufferMs: Long = 0L,           // Floor integer ms (backward-compatible)
    val turnaroundLagNs: Long = 0L,                // Release to AudioTrack DAC playback start (nanoseconds)
    val releaseToPlaybackStartMs: Long = 0L,       // Floor integer ms (backward-compatible)
    val recordedDurationNs: Long = 0L,             // Recorded audio duration in nanoseconds
    val recordedDurationMs: Long = 0L,             // Recorded audio duration in milliseconds
    val recordedBytes: Long = 0L,                  // Total raw PCM bytes captured
    val memoryBufferSizeBytes: Long = 0L,          // Allocated/accumulated memory buffer size in bytes
    val sampleRate: Int = AudioConfig.SAMPLE_RATE, // 16000 Hz
    val channels: Int = AudioConfig.CHANNEL_COUNT, // 1 (Mono)
    val bitrateKbps: Int = (AudioConfig.BYTE_RATE * 8) / 1000,   // 256 kbps uncompressed baseline
    val bitrateBps: Long = (AudioConfig.BYTE_RATE * 8).toLong(), // 256,000 bps uncompressed baseline

    // === MILESTONE 2: Neural TTS Telemetry ===
    val ttsTapToFirstAudioMs: Long = 0L,           // T_E2E (Tap to DAC playback start)
    val ttsSynthesisLatencyMs: Long = 0L,          // T_synth (Neural compute time)
    val ttsAudioDurationMs: Long = 0L,             // T_audio (Synthesized speech length)
    val ttsRealTimeFactor: Float = 0.0f,           // RTF = T_synth / T_audio
    val ttsTextLengthChars: Int = 0,               // Input character count
    val ttsTextLengthWords: Int = 0,               // Input word count
    val ttsCharsPerSecond: Float = 0.0f,           // Text throughput (chars / T_synth)
    val ttsLanguage: String = "hi",                // e.g., "hi", "mr", "en", "ta"
    val ttsModelName: String = "Indic-TTS VITS",    // Engine descriptor
    val isAlertPriority: Boolean = false,          // Emergency non-interruptible alert
    val activeMode: TransceiverMode = TransceiverMode.RECEIVER
) {
    /** High-resolution mic start lag in fractional milliseconds */
    val micStartLagMs: Double
        get() = micStartLagNs / 1_000_000.0

    /** High-resolution turnaround lag in fractional milliseconds */
    val turnaroundLagMs: Double
        get() = turnaroundLagNs / 1_000_000.0

    /** Memory buffer size in Kilobytes (KiB) */
    val memoryBufferSizeKb: Double
        get() = memoryBufferSizeBytes / 1024.0

    /** Memory buffer size in Megabytes (MiB) */
    val memoryBufferSizeMb: Double
        get() = memoryBufferSizeBytes / (1024.0 * 1024.0)

    /** High-resolution recorded duration in fractional seconds */
    val recordedDurationSec: Double
        get() = recordedDurationMs / 1000.0

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
 * Instruments nanosecond monotonic timestamps for both Physical Mic (M1) and Neural TTS (M2).
 */
class LatencyTracker(
    private val timeProvider: () -> Long = { System.nanoTime() }
) {
    companion object {
        const val UNCOMPRESSED_BASELINE_BITRATE_KBPS = 256
        const val UNCOMPRESSED_BASELINE_BITRATE_BPS = 256_000L
        const val NS_PER_MS = 1_000_000L
        const val NS_PER_SEC = 1_000_000_000L

        /**
         * Calculates uncompressed PCM bitrate in kilobits per second (kbps).
         */
        fun calculateBitrateKbps(
            sampleRate: Int = AudioConfig.SAMPLE_RATE,
            channels: Int = AudioConfig.CHANNEL_COUNT,
            bytesPerSample: Int = AudioConfig.BYTES_PER_SAMPLE
        ): Int {
            return (sampleRate * channels * bytesPerSample * 8) / 1000
        }

        /**
         * Calculates uncompressed PCM bitrate in bits per second (bps).
         */
        fun calculateBitrateBps(
            sampleRate: Int = AudioConfig.SAMPLE_RATE,
            channels: Int = AudioConfig.CHANNEL_COUNT,
            bytesPerSample: Int = AudioConfig.BYTES_PER_SAMPLE
        ): Long {
            return sampleRate.toLong() * channels.toLong() * bytesPerSample.toLong() * 8L
        }
    }

    private val _stats = MutableStateFlow(TelemetryStats())
    val stats = _stats.asStateFlow()

    // === Milestone 1 Monotonic Timestamps (nanoseconds) ===
    private var pressTimestampNs: Long = 0L
    private var firstBufferTimestampNs: Long = 0L
    private var firstBufferCaptured: Boolean = false
    private var releaseTimestampNs: Long = 0L
    private var playbackStartTimestampNs: Long = 0L
    private var playbackStarted: Boolean = false

    // === Milestone 2 TTS Monotonic Timestamps (nanoseconds) ===
    private var ttsTapTimestampNs: Long = 0L
    private var ttsSynthStartNs: Long = 0L
    private var ttsSynthCompleteNs: Long = 0L

    // -------------------------------------------------------------
    // MILESTONE 1: Audio I/O Handlers
    // -------------------------------------------------------------

    /**
     * Triggered on user PTT button down.
     * Starts the monotonic timer for Mic Start Lag.
     */
    fun onPttPressed(timestampNs: Long = timeProvider()) {
        pressTimestampNs = timestampNs
        firstBufferTimestampNs = 0L
        firstBufferCaptured = false
        releaseTimestampNs = 0L
        playbackStartTimestampNs = 0L
        playbackStarted = false

        _stats.value = _stats.value.copy(
            activeMode = TransceiverMode.TRANSMITTER,
            micStartLagNs = 0L,
            pressToFirstBufferMs = 0L,
            turnaroundLagNs = 0L,
            releaseToPlaybackStartMs = 0L
        )
    }

    /**
     * Triggered when AudioRecord captures and emits the first PCM chunk.
     * Computes Mic Start Lag: T_first_pcm - T_ptt_press.
     * Idempotent per PTT press cycle.
     */
    fun onFirstPcmBufferEmitted(timestampNs: Long = timeProvider()) {
        if (pressTimestampNs > 0L && !firstBufferCaptured) {
            firstBufferCaptured = true
            firstBufferTimestampNs = timestampNs
            val deltaNs = maxOf(0L, timestampNs - pressTimestampNs)
            val deltaMs = deltaNs / NS_PER_MS

            _stats.value = _stats.value.copy(
                micStartLagNs = deltaNs,
                pressToFirstBufferMs = deltaMs
            )
        }
    }

    /**
     * Triggered on user PTT button release.
     * Records audio duration, payload size, memory buffer size, and sets the turnaround baseline.
     */
    fun onPttReleased(
        totalBytes: Long,
        durationMs: Long,
        bufferSizeBytes: Long = totalBytes,
        timestampNs: Long = timeProvider()
    ) {
        releaseTimestampNs = timestampNs
        playbackStartTimestampNs = 0L
        playbackStarted = false

        val durationNs = if (durationMs > 0L) {
            durationMs * NS_PER_MS
        } else if (pressTimestampNs > 0L) {
            maxOf(0L, timestampNs - pressTimestampNs)
        } else 0L

        _stats.value = _stats.value.copy(
            recordedBytes = totalBytes,
            recordedDurationMs = durationMs,
            recordedDurationNs = durationNs,
            memoryBufferSizeBytes = bufferSizeBytes,
            bitrateKbps = UNCOMPRESSED_BASELINE_BITRATE_KBPS,
            bitrateBps = UNCOMPRESSED_BASELINE_BITRATE_BPS
        )
    }

    /**
     * Triggered when AudioTrack initiates physical DAC playback of the recorded PCM.
     * Computes Turnaround Lag: T_track_play - T_ptt_release.
     * Idempotent per playback cycle.
     */
    fun onPlaybackStarted(timestampNs: Long = timeProvider()) {
        if (releaseTimestampNs > 0L && !playbackStarted) {
            playbackStarted = true
            playbackStartTimestampNs = timestampNs
            val deltaNs = maxOf(0L, timestampNs - releaseTimestampNs)
            val deltaMs = deltaNs / NS_PER_MS

            _stats.value = _stats.value.copy(
                turnaroundLagNs = deltaNs,
                releaseToPlaybackStartMs = deltaMs
            )
        }
    }

    /**
     * Explicitly updates the allocated memory buffer size (e.g. DMA ring buffer or PCM stream heap).
     */
    fun updateMemoryBufferSize(bufferSizeBytes: Long) {
        _stats.value = _stats.value.copy(memoryBufferSizeBytes = bufferSizeBytes)
    }

    // -------------------------------------------------------------
    // MILESTONE 2: Neural TTS Handlers (Monotonic Instrumentation)
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
            maxOf(0L, now - ttsSynthStartNs) / NS_PER_MS
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

        val dynamicBitrateKbps = (byteRate * 8) / 1000
        val dynamicBitrateBps = (byteRate * 8).toLong()

        _stats.value = _stats.value.copy(
            ttsSynthesisLatencyMs = tSynthMs,
            ttsAudioDurationMs = tAudioMs,
            ttsRealTimeFactor = rtf,
            ttsCharsPerSecond = charsPerSec,
            sampleRate = sampleRate,
            bitrateKbps = dynamicBitrateKbps,
            bitrateBps = dynamicBitrateBps,
            memoryBufferSizeBytes = pcmByteCount
        )
    }

    fun onTtsFirstAudioFramePlayed() {
        if (ttsTapTimestampNs > 0L) {
            val deltaMs = maxOf(0L, timeProvider() - ttsTapTimestampNs) / NS_PER_MS
            _stats.value = _stats.value.copy(ttsTapToFirstAudioMs = deltaMs)
        }
    }

    fun setMode(mode: TransceiverMode) {
        _stats.value = _stats.value.copy(activeMode = mode)
    }

    fun reset() {
        pressTimestampNs = 0L
        firstBufferTimestampNs = 0L
        firstBufferCaptured = false
        releaseTimestampNs = 0L
        playbackStartTimestampNs = 0L
        playbackStarted = false

        ttsTapTimestampNs = 0L
        ttsSynthStartNs = 0L
        ttsSynthCompleteNs = 0L

        _stats.value = TelemetryStats()
    }
}
