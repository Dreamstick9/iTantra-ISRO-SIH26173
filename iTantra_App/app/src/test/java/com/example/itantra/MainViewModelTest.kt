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
        assertNotNull(finalState.lastAudioDebugInfo)
        assertEquals("Audio captured successfully", finalState.lastAudioDebugInfo?.message)
        assertTrue((finalState.lastAudioDebugInfo?.durationMs ?: 0L) > 0L)
        assertTrue((finalState.lastAudioDebugInfo?.byteCount ?: 0) > 0)
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
        assertEquals(com.example.itantra.data.MessagePlaybackStatus.PLAYED, state.messages[0].playbackStatus)
        assertNotNull(state.lastLatencyMetrics)
    }

    @Test
    fun testSttDiagnosticsFlowOnPttRelease() = runTest(testDispatcher) {
        advanceUntilIdle() // let init coroutines collect speechEngine state

        val initialDiag = viewModel.appState.value.sttDiagnostics
        assertTrue(initialDiag.isModelLoaded)
        assertTrue(initialDiag.isOffline)
        assertEquals("English", initialDiag.language)
        assertEquals("", initialDiag.recognizedText)
        assertNull(initialDiag.errorMessage)

        viewModel.onInputLanguageSelected(Language.ENGLISH)
        viewModel.onPttPressed()
        advanceUntilIdle()

        viewModel.onPttReleased()
        advanceUntilIdle()

        val finalState = viewModel.appState.value
        val finalDiag = finalState.sttDiagnostics

        assertTrue(finalDiag.isModelLoaded)
        assertTrue(finalDiag.isOffline)
        assertEquals("English", finalDiag.language)
        assertTrue(finalDiag.recognizedText.isNotBlank())
        assertNull(finalDiag.errorMessage)

        // Verify that the recognized text was added to conversation messages
        assertEquals(1, finalState.messages.size)
        val outgoingMsg = finalState.messages[0]
        assertTrue(outgoingMsg.isOutgoing)
        assertEquals(finalDiag.recognizedText, outgoingMsg.text)
        assertEquals(Language.ENGLISH, outgoingMsg.originalLanguage)
    }

    @Test
    fun testMultilingualInputLanguageSelectionAndPttFlow() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Select Tamil
        viewModel.onInputLanguageSelected(Language.TAMIL)
        advanceUntilIdle()
        assertEquals(Language.TAMIL, viewModel.appState.value.inputLanguage)

        // Capture voice on Tamil
        viewModel.onPttPressed()
        advanceUntilIdle()
        viewModel.onPttReleased()
        advanceUntilIdle()

        val finalState = viewModel.appState.value
        assertEquals(1, finalState.messages.size)
        val outgoingMsg = finalState.messages[0]
        assertEquals(Language.TAMIL, outgoingMsg.originalLanguage)
        assertEquals("Tamil", finalState.sttDiagnostics.language)
        assertEquals(com.example.itantra.language.ModelRegistry.getSampleTranscript(Language.TAMIL), outgoingMsg.text)

        // Swap languages
        viewModel.onSwapLanguages()
        advanceUntilIdle()
        assertEquals(Language.TAMIL, viewModel.appState.value.outputLanguage)
    }

    @Test
    fun testWifiDirectDiscoveryAndPeerObservation() = runTest(testDispatcher) {
        viewModel.onStartPeerDiscovery()
        advanceUntilIdle()

        // Verify peers flow is accessible from ViewModel
        assertNotNull(viewModel.discoveredPeers.value)
        assertNotNull(viewModel.transportDiagnostics.value)
    }

    @Test
    fun testWifiDirectSendTextMessageReflectedInState() = runTest(testDispatcher) {
        viewModel.onSendTextMessage("HELLO FROM PHONE A")
        advanceUntilIdle()

        val messages = viewModel.appState.value.messages
        assertEquals(1, messages.size)
        val sent = messages[0]
        assertTrue(sent.isOutgoing)
        assertEquals("HELLO FROM PHONE A", sent.text)
        assertEquals("Local Operator (You)", sent.senderName)
    }

    @Test
    fun testWifiDirectSendAlertMessageReflectedInState() = runTest(testDispatcher) {
        viewModel.onSendAlertMessage("CYCLONE ADVISORY")
        advanceUntilIdle()

        val messages = viewModel.appState.value.messages
        assertEquals(1, messages.size)
        val sent = messages[0]
        assertTrue(sent.isOutgoing)
        assertEquals("CYCLONE ADVISORY", sent.text)
        assertEquals(com.example.itantra.data.MessageType.EMERGENCY_ALERT, sent.messageType)
        assertTrue(sent.isEmergency)
    }

    @Test
    fun testWifiDirectDisconnectAction() = runTest(testDispatcher) {
        viewModel.onDisconnectTransport()
        advanceUntilIdle()

        assertEquals(com.example.itantra.data.ConnectionStatus.DISCONNECTED, viewModel.appState.value.connectionStatus)
    }
}
