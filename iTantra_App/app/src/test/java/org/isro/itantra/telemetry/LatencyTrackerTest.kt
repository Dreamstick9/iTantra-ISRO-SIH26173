package org.isro.itantra.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyTrackerTest {

    @Test
    fun testInitialStats() {
        val tracker = LatencyTracker()
        val stats = tracker.stats.value

        assertEquals(0L, stats.pressToFirstBufferMs)
        assertEquals(0L, stats.releaseToPlaybackStartMs)
        assertEquals(0L, stats.recordedDurationMs)
        assertEquals(0L, stats.recordedBytes)
        assertEquals(16000, stats.sampleRate)
        assertEquals(1, stats.channels)
        assertEquals(256, stats.bitrateKbps)
    }

    @Test
    fun testPttReleaseUpdatesStats() {
        val tracker = LatencyTracker()
        val totalBytes = 64000L // 2 seconds of audio
        val durationMs = 2000L

        tracker.onPttReleased(totalBytes, durationMs)

        val updated = tracker.stats.value
        assertEquals(totalBytes, updated.recordedBytes)
        assertEquals(durationMs, updated.recordedDurationMs)
    }

    @Test
    fun testResetClearsStats() {
        val tracker = LatencyTracker()
        tracker.onPttReleased(32000L, 1000L)
        tracker.reset()

        val resetStats = tracker.stats.value
        assertEquals(0L, resetStats.recordedBytes)
        assertEquals(0L, resetStats.recordedDurationMs)
    }
}
