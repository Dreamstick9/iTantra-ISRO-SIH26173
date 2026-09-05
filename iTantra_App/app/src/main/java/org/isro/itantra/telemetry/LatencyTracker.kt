package org.isro.itantra.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.isro.itantra.audio.AudioConfig

/**
 * Real-time telemetry tracker instrumented for ISRO SIH26173 Evaluation Metrics:
 * - Latency (20%): Record start lag and Stop-to-playback turnaround
 * - Efficiency (20%): Memory footprint & Bitrate verification
 */
data class TelemetryStats(
    val pressToFirstBufferMs: Long = 0L,
    val releaseToPlaybackStartMs: Long = 0L,
    val recordedDurationMs: Long = 0L,
    val recordedBytes: Long = 0L,
    val sampleRate: Int = AudioConfig.SAMPLE_RATE,
    val channels: Int = AudioConfig.CHANNEL_COUNT,
    val bitrateKbps: Int = (AudioConfig.BYTE_RATE * 8) / 1000
)

class LatencyTracker(
    private val timeProvider: () -> Long = { System.nanoTime() }
) {

    private val _stats = MutableStateFlow(TelemetryStats())
    val stats = _stats.asStateFlow()

    private var pressTimestampNs: Long = 0L
    private var releaseTimestampNs: Long = 0L

    fun onPttPressed() {
        pressTimestampNs = timeProvider()
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

    fun reset() {
        pressTimestampNs = 0L
        releaseTimestampNs = 0L
        _stats.value = TelemetryStats()
    }
}
