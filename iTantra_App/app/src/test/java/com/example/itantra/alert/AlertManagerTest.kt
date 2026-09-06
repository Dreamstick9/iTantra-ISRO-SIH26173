package com.example.itantra.alert

import com.example.itantra.audio.AudioTrackPlayer
import com.example.itantra.transport.TransportMessage
import com.example.itantra.transport.TransportMessageType
import com.example.itantra.tts.AudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AlertManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var audioTrackPlayer: AudioTrackPlayer
    private lateinit var alertManager: MockAlertManager

    @Before
    fun setUp() {
        audioTrackPlayer = AudioTrackPlayer(testDispatcher)
        alertManager = MockAlertManager(audioTrackPlayer)
    }

    @Test
    fun testAlertPriorityDetection() {
        // Priority 1 or above -> ALERT
        val alertMsg1 = TransportMessage.text("Cyclone Warning", priority = 1)
        assertTrue("Priority 1 must be treated as alert", alertManager.isAlert(alertMsg1))

        // TransportMessageType.ALERT -> ALERT
        val alertMsg2 = TransportMessage.alert("Tsunami Advisory", priority = 2)
        assertTrue("TransportMessageType.ALERT must be treated as alert", alertManager.isAlert(alertMsg2))

        // Regular text priority 0 -> NOT alert
        val normalMsg = TransportMessage.text("Standard voice note", priority = 0)
        assertFalse("Priority 0 text message must not be treated as alert", alertManager.isAlert(normalMsg))
    }

    @Test
    fun testEmergencyAlertPlaybackLifecycle() = runTest(testDispatcher) {
        val samplePcm = ByteArray(1600) { (it % 128).toByte() }
        val audioData = AudioData(
            samples = FloatArray(800),
            sampleRate = 16000,
            rawPcm = samplePcm,
            durationMs = 100L
        )

        var playbackStarted = false
        val result = alertManager.playEmergencyAlert("TEST ALARM", audioData) {
            playbackStarted = true
        }

        assertTrue("Playback callback must be invoked", playbackStarted)
        assertTrue("Alert playback must succeed", result.success)
        assertTrue("Audio focus must be reported accurately", result.focusGranted)
        assertEquals(15, result.appliedVolume)
        assertEquals(15, result.maxPermittedVolume)
        assertFalse("Alert in progress must be false after playback completion", alertManager.isAlertInProgress())
        assertEquals("TEST ALARM", alertManager.lastPlayedText)
    }

    @Test
    fun testEmptyAudioDataHandling() = runTest(testDispatcher) {
        val emptyData = AudioData.EMPTY
        val androidAlertManager = AndroidAlertManager(null, audioTrackPlayer, testDispatcher)

        val result = androidAlertManager.playEmergencyAlert("NO AUDIO", emptyData)
        assertFalse("Empty audio data must result in failure", result.success)
        assertNotNull(result.errorMessage)
        assertFalse("Alert in progress must be cleared", androidAlertManager.isAlertInProgress())
    }

    @Test
    fun testQueueGatingSerializesExecution() = runTest(testDispatcher) {
        val events = mutableListOf<String>()

        launch {
            alertManager.withAlertLock {
                events.add("alert_start")
                delay(100)
                events.add("alert_end")
            }
        }

        launch {
            delay(10) // Wait slightly so alert lock is acquired first
            alertManager.withAlertLock {
                events.add("normal_message_processed")
            }
        }

        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf("alert_start", "alert_end", "normal_message_processed"),
            events
        )
    }

    @Test
    fun testAlertPlaybackResultHonestFormatting() {
        val result = AlertPlaybackResult(
            success = true,
            focusGranted = true,
            appliedVolume = 14,
            maxPermittedVolume = 15,
            durationMs = 850L
        )
        val formatted = result.formattedSummary()
        assertTrue(formatted.contains("Focus: GRANTED (EXCLUSIVE)"))
        assertTrue(formatted.contains("Vol: 14/15"))
        assertTrue(formatted.contains("Dur: 850ms"))
    }
}
