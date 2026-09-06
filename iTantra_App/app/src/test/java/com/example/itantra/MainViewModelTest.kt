package com.example.itantra

import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.data.Language
import com.example.itantra.data.PttState
import com.example.itantra.data.TransmissionMode
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
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var audioEngine: MockAudioEngine
    private lateinit var speechEngine: MockSpeechEngine
    private lateinit var transportEngine: MockTransportEngine
    private lateinit var compressionEngine: MockCompressionEngine
    private lateinit var serviceController: MockServiceController
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        audioEngine = MockAudioEngine()
        speechEngine = MockSpeechEngine()
        transportEngine = MockTransportEngine()
        compressionEngine = MockCompressionEngine()
        serviceController = MockServiceController()

        viewModel = MainViewModel(
            audioEngine = audioEngine,
            speechEngine = speechEngine,
            transportEngine = transportEngine,
            compressionEngine = compressionEngine,
            serviceController = serviceController,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        viewModel.onCleared()
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        val state = viewModel.appState.value
        assertEquals(PttState.IDLE, state.pttState)
        assertEquals(Language.HINDI, state.inputLanguage)
        assertEquals(Language.HINDI, state.outputLanguage)
        assertEquals(TransmissionMode.PUSH_TO_TALK, state.transmissionMode)
        assertFalse(state.emergencyAlert.isActive)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun testPttPressAndRelease() = runTest(testDispatcher) {
        viewModel.onPttPressed()
        advanceUntilIdle()

        assertEquals(PttState.RECORDING, viewModel.appState.value.pttState)
        assertTrue(audioEngine.isRecording)

        viewModel.onPttReleased()
        advanceUntilIdle()

        val finalState = viewModel.appState.value
        assertEquals(PttState.IDLE, finalState.pttState)
        assertFalse(audioEngine.isRecording)
        assertEquals(1, finalState.messages.size)
        assertTrue(finalState.messages[0].text.isNotEmpty())
        assertNotNull(finalState.lastLatencyMetrics)
    }

    @Test
    fun testLanguageSelectionAndSwap() {
        viewModel.onInputLanguageSelected(Language.BENGALI)
        viewModel.onOutputLanguageSelected(Language.TELUGU)
        assertEquals(Language.BENGALI, viewModel.appState.value.inputLanguage)
        assertEquals(Language.TELUGU, viewModel.appState.value.outputLanguage)

        viewModel.onSwapLanguages()
        assertEquals(Language.TELUGU, viewModel.appState.value.inputLanguage)
        assertEquals(Language.BENGALI, viewModel.appState.value.outputLanguage)
    }

    @Test
    fun testEmergencyAlertToggle() {
        assertFalse(viewModel.appState.value.emergencyAlert.isActive)
        viewModel.onEmergencyAlertToggled(true)
        assertTrue(viewModel.appState.value.emergencyAlert.isActive)
        assertEquals("CYCLONE ADVISORY", viewModel.appState.value.emergencyAlert.title)

        viewModel.onEmergencyAlertToggled(false)
        assertFalse(viewModel.appState.value.emergencyAlert.isActive)
    }

    @Test
    fun testTransmissionModeChange() {
        viewModel.onTransmissionModeChanged(TransmissionMode.CONTINUOUS)
        assertEquals(TransmissionMode.CONTINUOUS, viewModel.appState.value.transmissionMode)
    }

    @Test
    fun testSimulateReceiveMessage() = runTest(testDispatcher) {
        viewModel.simulateReceiveMessage("Test satellite advisory")
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals(1, state.messages.size)
        assertEquals("Test satellite advisory", state.messages[0].text)
        assertNotNull(audioEngine.lastPlayedAudio)
    }
}
