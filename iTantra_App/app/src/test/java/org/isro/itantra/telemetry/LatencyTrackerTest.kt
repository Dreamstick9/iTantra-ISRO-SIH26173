package org.isro.itantra.telemetry

import org.isro.itantra.audio.AudioConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyTrackerTest {

    @Test
    fun testInitialStats_defaultValues() {
        val tracker = LatencyTracker()
        val stats = tracker.stats.value

        assertEquals(0L, stats.micStartLagNs)
        assertEquals(0L, stats.pressToFirstBufferMs)
        assertEquals(0.0, stats.micStartLagMs, 0.0001)

        assertEquals(0L, stats.turnaroundLagNs)
        assertEquals(0L, stats.releaseToPlaybackStartMs)
        assertEquals(0.0, stats.turnaroundLagMs, 0.0001)

        assertEquals(0L, stats.recordedDurationNs)
        assertEquals(0L, stats.recordedDurationMs)
        assertEquals(0.0, stats.recordedDurationSec, 0.0001)

        assertEquals(0L, stats.recordedBytes)
        assertEquals(0L, stats.memoryBufferSizeBytes)
        assertEquals(0.0, stats.memoryBufferSizeKb, 0.0001)
        assertEquals(0.0, stats.memoryBufferSizeMb, 0.0001)

        assertEquals(16000, stats.sampleRate)
        assertEquals(1, stats.channels)
        assertEquals(256, stats.bitrateKbps)
        assertEquals(256000L, stats.bitrateBps)
        assertEquals(TransceiverMode.RECEIVER, stats.activeMode)
    }

    @Test
    fun testMicStartLag_nanosecondMonotonicTracking() {
        var mockClockNs = 1_000_000_000L // 1.0s
        val tracker = LatencyTracker(timeProvider = { mockClockNs })

        tracker.onPttPressed()
        assertEquals(TransceiverMode.TRANSMITTER, tracker.stats.value.activeMode)

        // Advance mock clock by 18,450,000 ns (18.45 ms)
        mockClockNs += 18_450_000L
        tracker.onFirstPcmBufferEmitted()

        val stats = tracker.stats.value
        assertEquals(18_450_000L, stats.micStartLagNs)
        assertEquals(18L, stats.pressToFirstBufferMs)
        assertEquals(18.45, stats.micStartLagMs, 0.0001)
    }

    @Test
    fun testMicStartLag_subMillisecondPrecisionPreserved() {
        var mockClockNs = 5_000_000_000L
        val tracker = LatencyTracker(timeProvider = { mockClockNs })

        tracker.onPttPressed()
        // Fast HAL startup: 450 µs = 450,000 ns (would truncate to 0ms with integer division)
        mockClockNs += 450_000L
        tracker.onFirstPcmBufferEmitted()

        val stats = tracker.stats.value
        assertEquals(450_000L, stats.micStartLagNs)
        assertEquals(0L, stats.pressToFirstBufferMs) // Integer floor is 0
        assertEquals(0.45, stats.micStartLagMs, 0.0001) // Nanosecond tracking preserves 0.45 ms
    }

    @Test
    fun testMicStartLag_idempotentPerSession() {
        var mockClockNs = 10_000_000_000L
        val tracker = LatencyTracker(timeProvider = { mockClockNs })

        tracker.onPttPressed()
        mockClockNs += 12_000_000L // First buffer at 12ms
        tracker.onFirstPcmBufferEmitted()

        // Subsequent buffer arrives at 32ms (should be ignored)
        mockClockNs += 20_000_000L
        tracker.onFirstPcmBufferEmitted()

        val stats = tracker.stats.value
        assertEquals(12_000_000L, stats.micStartLagNs)
        assertEquals(12L, stats.pressToFirstBufferMs)
    }

    @Test
    fun testMicStartLag_withoutPttPressed_safeNoOp() {
        val tracker = LatencyTracker()
        tracker.onFirstPcmBufferEmitted()

        val stats = tracker.stats.value
        assertEquals(0L, stats.micStartLagNs)
        assertEquals(0L, stats.pressToFirstBufferMs)
    }

    @Test
    fun testTurnaroundLag_nanosecondMonotonicTracking() {
        var mockClockNs = 20_000_000_000L
        val tracker = LatencyTracker(timeProvider = { mockClockNs })

        tracker.onPttPressed()
        mockClockNs += 2_000_000_000L // 2.0s recording
        tracker.onPttReleased(totalBytes = 64000L, durationMs = 2000L)

        // Advance mock clock by 35,750,000 ns (35.75 ms) before AudioTrack play
        mockClockNs += 35_750_000L
        tracker.onPlaybackStarted()

        val stats = tracker.stats.value
        assertEquals(35_750_000L, stats.turnaroundLagNs)
        assertEquals(35L, stats.releaseToPlaybackStartMs)
        assertEquals(35.75, stats.turnaroundLagMs, 0.0001)
    }

    @Test
    fun testTurnaroundLag_withoutRelease_safeNoOp() {
        val tracker = LatencyTracker()
        tracker.onPlaybackStarted()

        val stats = tracker.stats.value
        assertEquals(0L, stats.turnaroundLagNs)
        assertEquals(0L, stats.releaseToPlaybackStartMs)
    }

    @Test
    fun testTurnaroundLag_idempotentUntilNextRelease() {
        var mockClockNs = 30_000_000_000L
        val tracker = LatencyTracker(timeProvider = { mockClockNs })

        tracker.onPttReleased(totalBytes = 32000L, durationMs = 1000L)
        mockClockNs += 25_000_000L // Playback starts at 25ms
        tracker.onPlaybackStarted()

        // Subsequent play callback should be ignored
        mockClockNs += 50_000_000L
        tracker.onPlaybackStarted()

        assertEquals(25_000_000L, tracker.stats.value.turnaroundLagNs)
        assertEquals(25L, tracker.stats.value.releaseToPlaybackStartMs)
    }

    @Test
    fun testPttRelease_durationAndMemoryBufferTracking() {
        val tracker = LatencyTracker()
        val totalBytes = 64000L // 2.0s of 16kHz mono 16-bit PCM
        val durationMs = 2000L
        val bufferCapacityBytes = 131072L // 128 KiB allocated buffer

        tracker.onPttReleased(totalBytes, durationMs, bufferCapacityBytes)

        val stats = tracker.stats.value
        assertEquals(totalBytes, stats.recordedBytes)
        assertEquals(durationMs, stats.recordedDurationMs)
        assertEquals(2_000_000_000L, stats.recordedDurationNs)
        assertEquals(2.0, stats.recordedDurationSec, 0.0001)
        assertEquals(bufferCapacityBytes, stats.memoryBufferSizeBytes)
        assertEquals(128.0, stats.memoryBufferSizeKb, 0.0001)
        assertEquals(0.125, stats.memoryBufferSizeMb, 0.0001)
    }

    @Test
    fun testPttRelease_defaultMemoryBufferEqualsTotalBytes() {
        val tracker = LatencyTracker()
        tracker.onPttReleased(totalBytes = 32000L, durationMs = 1000L)

        val stats = tracker.stats.value
        assertEquals(32000L, stats.memoryBufferSizeBytes)
        assertEquals(32000L / 1024.0, stats.memoryBufferSizeKb, 0.0001)
    }

    @Test
    fun testUpdateMemoryBufferSize() {
        val tracker = LatencyTracker()
        tracker.updateMemoryBufferSize(262144L) // 256 KiB

        val stats = tracker.stats.value
        assertEquals(262144L, stats.memoryBufferSizeBytes)
        assertEquals(256.0, stats.memoryBufferSizeKb, 0.0001)
        assertEquals(0.25, stats.memoryBufferSizeMb, 0.0001)
    }

    @Test
    fun testBitrateBaseline_uncompressed256kbps() {
        assertEquals(256, LatencyTracker.UNCOMPRESSED_BASELINE_BITRATE_KBPS)
        assertEquals(256000L, LatencyTracker.UNCOMPRESSED_BASELINE_BITRATE_BPS)

        val calcKbps = LatencyTracker.calculateBitrateKbps(
            sampleRate = 16000,
            channels = 1,
            bytesPerSample = 2
        )
        assertEquals(256, calcKbps)

        val calcBps = LatencyTracker.calculateBitrateBps(
            sampleRate = 16000,
            channels = 1,
            bytesPerSample = 2
        )
        assertEquals(256000L, calcBps)
    }

    @Test
    fun testReset_clearsAllNanosecondMonotonicMetrics() {
        var mockClockNs = 40_000_000_000L
        val tracker = LatencyTracker(timeProvider = { mockClockNs })

        tracker.onPttPressed()
        mockClockNs += 15_000_000L
        tracker.onFirstPcmBufferEmitted()
        mockClockNs += 1_000_000_000L
        tracker.onPttReleased(totalBytes = 32000L, durationMs = 1000L, bufferSizeBytes = 65536L)
        mockClockNs += 20_000_000L
        tracker.onPlaybackStarted()

        // Verify non-zero before reset
        assertTrue(tracker.stats.value.micStartLagNs > 0L)
        assertTrue(tracker.stats.value.turnaroundLagNs > 0L)
        assertTrue(tracker.stats.value.memoryBufferSizeBytes > 0L)

        // Reset
        tracker.reset()

        val resetStats = tracker.stats.value
        assertEquals(0L, resetStats.micStartLagNs)
        assertEquals(0L, resetStats.pressToFirstBufferMs)
        assertEquals(0L, resetStats.turnaroundLagNs)
        assertEquals(0L, resetStats.releaseToPlaybackStartMs)
        assertEquals(0L, resetStats.recordedBytes)
        assertEquals(0L, resetStats.recordedDurationMs)
        assertEquals(0L, resetStats.recordedDurationNs)
        assertEquals(0L, resetStats.memoryBufferSizeBytes)
    }

    @Test
    fun testMonotonicSafety_zeroDeltaOnInstantaneousCallbacks() {
        val staticClockNs = 50_000_000_000L
        val tracker = LatencyTracker(timeProvider = { staticClockNs })

        tracker.onPttPressed()
        tracker.onFirstPcmBufferEmitted()
        tracker.onPttReleased(0L, 0L)
        tracker.onPlaybackStarted()

        val stats = tracker.stats.value
        assertEquals(0L, stats.micStartLagNs)
        assertEquals(0L, stats.turnaroundLagNs)
    }

    @Test
    fun testMilestone2TtsCompatibilityPreserved() {
        var mockClockNs = 60_000_000_000L
        val tracker = LatencyTracker(timeProvider = { mockClockNs })

        tracker.onTtsRequested(text = "चक्रवात चेतावनी! सुरक्षित स्थान पर जाएं।", language = "hi")
        assertEquals(TransceiverMode.RECEIVER, tracker.stats.value.activeMode)
        assertEquals("hi", tracker.stats.value.ttsLanguage)

        tracker.onTtsSynthesisStarted()
        mockClockNs += 320_000_000L // 320 ms synthesis time
        // 1.5 seconds of 22050 Hz Mono 16-bit PCM = 22050 * 1 * 2 * 1.5 = 66150 bytes
        tracker.onTtsSynthesisCompleted(pcmByteCount = 66150L, sampleRate = 22050)

        mockClockNs += 160_000_000L // 160 ms audio track buffer delay
        tracker.onTtsFirstAudioFramePlayed()

        val stats = tracker.stats.value
        assertEquals(320L, stats.ttsSynthesisLatencyMs)
        assertEquals(1500L, stats.ttsAudioDurationMs)
        assertEquals(480L, stats.ttsTapToFirstAudioMs) // 320 + 160 = 480 ms
        assertTrue(stats.ttsRealTimeFactor in 0.20f..0.22f)
        assertTrue(stats.realTimeSpeedMultiplier > 4.5f)
        assertTrue(stats.isRealTimeCapable)
    }
}
