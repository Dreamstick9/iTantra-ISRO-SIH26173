package com.example.itantra

import com.example.itantra.audio.AudioConfig
import com.example.itantra.audio.AudioRecordingState
import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.data.PttState
import com.example.itantra.service.MockServiceController
import com.example.itantra.speech.MockSpeechEngine
import com.example.itantra.transport.MockTransportEngine
import com.example.itantra.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Stage1AudioCaptureTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var audioEngine: MockAudioEngine
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        audioEngine = MockAudioEngine()
        viewModel = MainViewModel(
            audioEngine = audioEngine,
            speechEngine = MockSpeechEngine(),
            transportEngine = MockTransportEngine(),
            compressionEngine = MockCompressionEngine(),
            serviceController = MockServiceController(),
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        viewModel.onCleared()
        Dispatchers.resetMain()
    }

    @Test
    fun testAudioConfigSpecifications() {
        assertEquals(16000, AudioConfig.SAMPLE_RATE_HZ)
        assertEquals(1, AudioConfig.CHANNEL_COUNT)
        assertEquals(16, AudioConfig.BITS_PER_SAMPLE)
        assertEquals(2, AudioConfig.BYTES_PER_SAMPLE)
        assertEquals(32000, AudioConfig.BYTES_PER_SECOND)

        // 1 second of audio at 16kHz mono 16-bit must equal 32,000 bytes
        val oneSecondBytes = 32000
        val durationMs = (oneSecondBytes * 1000L) / AudioConfig.BYTES_PER_SECOND
        assertEquals(1000L, durationMs)
    }

    @Test
    fun testAudioRecordingStateTransitions() = runTest(testDispatcher) {
        assertEquals(AudioRecordingState.IDLE, audioEngine.recordingState.value)

        audioEngine.startRecording()
        assertEquals(AudioRecordingState.RECORDING, audioEngine.recordingState.value)
        assertTrue(audioEngine.audioLevel.value > 0f)

        val pcm = audioEngine.stopRecording()
        assertEquals(AudioRecordingState.IDLE, audioEngine.recordingState.value)
        assertTrue(pcm.isNotEmpty())
        assertEquals(0f, audioEngine.audioLevel.value, 0.001f)
    }

    @Test
    fun testPttCaptureReturnsNonEmptyBufferAndDisplaysDebugInfo() = runTest(testDispatcher) {
        // Initial state
        assertEquals(PttState.IDLE, viewModel.appState.value.pttState)
        assertNull(viewModel.appState.value.lastAudioDebugInfo)

        // PTT pressed
        viewModel.onPttPressed()
        advanceUntilIdle()
        assertEquals(PttState.RECORDING, viewModel.appState.value.pttState)
        assertTrue(viewModel.appState.value.isPttPressed)

        // PTT released
        viewModel.onPttReleased()
        advanceUntilIdle()

        // Verify final state
        val finalState = viewModel.appState.value
        assertEquals(PttState.IDLE, finalState.pttState)
        assertFalse(finalState.isPttPressed)

        // Verify debug info
        val debugInfo = finalState.lastAudioDebugInfo
        assertNotNull("AudioDebugInfo must not be null after capture", debugInfo)
        assertEquals("Audio captured successfully", debugInfo?.message)
        assertTrue("Byte count must be greater than zero", (debugInfo?.byteCount ?: 0) > 0)
        assertTrue("Duration must be greater than zero", (debugInfo?.durationMs ?: 0L) > 0L)
        assertEquals(16000, debugInfo?.sampleRate)
        assertEquals(1, debugInfo?.channels)
        assertEquals(16, debugInfo?.bitDepth)

        // Verify status message
        assertTrue(finalState.statusMessage?.contains("Audio captured successfully") == true)
        assertNull(finalState.errorMessage)
    }

    @Test
    fun testPermissionDenialHandling() {
        viewModel.onPermissionResult(false)
        assertFalse(viewModel.appState.value.hasAudioPermission)
        assertNotNull(viewModel.appState.value.errorMessage)
        assertTrue(viewModel.appState.value.errorMessage?.contains("RECORD_AUDIO") == true)

        viewModel.onPermissionResult(true)
        assertTrue(viewModel.appState.value.hasAudioPermission)
        assertNull(viewModel.appState.value.errorMessage)
    }
}
