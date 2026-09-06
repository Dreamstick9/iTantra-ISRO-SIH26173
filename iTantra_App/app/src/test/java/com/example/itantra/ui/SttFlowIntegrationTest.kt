package com.example.itantra.ui

import com.example.itantra.audio.AudioConfig
import com.example.itantra.audio.AudioEngine
import com.example.itantra.audio.AudioRecordingState
import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.data.Language
import com.example.itantra.data.MessageType
import com.example.itantra.data.PttState
import com.example.itantra.data.SubsystemState
import com.example.itantra.service.MockServiceController
import com.example.itantra.speech.MockSpeechEngine
import com.example.itantra.speech.SpeechEngine
import com.example.itantra.transport.MockTransportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * End-to-end integration tests for the Offline STT Flow in MainViewModel.
 * Verifies PTT audio recording, offline transcription via SpeechEngine,
 * SttDiagnosticState updates, outgoing message generation, and empty audio edge cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SttFlowIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockSpeechEngine: MockSpeechEngine
    private lateinit var mockAudioEngine: MockAudioEngine
    private lateinit var mockTransportEngine: MockTransportEngine
    private lateinit var mockCompressionEngine: MockCompressionEngine
    private lateinit var mockServiceController: MockServiceController
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAudioEngine = MockAudioEngine()
        mockSpeechEngine = MockSpeechEngine()
        mockTransportEngine = MockTransportEngine()
        mockCompressionEngine = MockCompressionEngine()
        mockServiceController = MockServiceController()

        viewModel = MainViewModel(
            audioEngine = mockAudioEngine,
            speechEngine = mockSpeechEngine,
            transportEngine = mockTransportEngine,
            compressionEngine = mockCompressionEngine,
            serviceController = mockServiceController,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        viewModel.onCleared()
        Dispatchers.resetMain()
    }

    @Test
    fun testAudioRecordAndPttRelease_FullSttFlow() = runTest(testDispatcher) {
        advanceUntilIdle() // Wait for initial state flow collection

        // Configure realistic offline transcription result
        val expectedTranscript = "Cyclone warning: Evacuate coastal lowlands immediately"
        mockSpeechEngine.simulatedTranscriptionText = expectedTranscript
        mockSpeechEngine.simulatedSttLatencyMs = 120L

        // Initial diagnostic verification
        val initialDiag = viewModel.appState.value.sttDiagnostics
        assertTrue("Speech engine model must report loaded", initialDiag.isModelLoaded)
        assertTrue("STT must operate strictly offline", initialDiag.isOffline)
        assertEquals("English", initialDiag.language)
        assertEquals("", initialDiag.recognizedText)
        assertNull(initialDiag.errorMessage)
        assertEquals(0, viewModel.appState.value.messages.size)

        // 1. Press PTT
        viewModel.onPttPressed()
        advanceUntilIdle()

        assertEquals(PttState.RECORDING, viewModel.appState.value.pttState)
        assertTrue(mockAudioEngine.isRecording)
        assertEquals("Recording PCM audio...", viewModel.appState.value.statusMessage)

        // 2. Release PTT
        viewModel.onPttReleased()
        advanceUntilIdle()

        val finalState = viewModel.appState.value
        val finalDiag = finalState.sttDiagnostics

        // Verify PTT returned to IDLE
        assertEquals(PttState.IDLE, finalState.pttState)
        assertFalse(mockAudioEngine.isRecording)

        // Verify SpeechEngine.transcribe(pcmAudio) was called
        assertEquals(1, mockSpeechEngine.transcribeCallCount)
        assertNotNull("PCM audio must have been passed to transcribe()", mockSpeechEngine.lastTranscribedAudio)
        assertTrue(
            "Transcribed PCM buffer must not be empty",
            mockSpeechEngine.lastTranscribedAudio!!.isNotEmpty()
        )

        // Verify SttDiagnosticState is updated
        assertTrue("isModelLoaded must be true", finalDiag.isModelLoaded)
        assertTrue("processingTimeMs must be >= 0", finalDiag.processingTimeMs >= 0L)
        assertEquals(expectedTranscript, finalDiag.recognizedText)
        assertTrue("isOffline must remain true", finalDiag.isOffline)
        assertEquals("English", finalDiag.language)
        assertNull("errorMessage must be null on success", finalDiag.errorMessage)

        // Verify Outgoing ReceivedMessage is created and added to state.messages
        assertEquals(1, finalState.messages.size)
        val outgoingMsg = finalState.messages[0]
        assertTrue("Message must be flagged as outgoing", outgoingMsg.isOutgoing)
        assertEquals("LOCAL_USER", outgoingMsg.senderId)
        assertEquals("Local Operator (You)", outgoingMsg.senderName)
        assertEquals(expectedTranscript, outgoingMsg.text)
        assertEquals(Language.ENGLISH, outgoingMsg.originalLanguage)
        assertEquals(MessageType.VOICE_NOTE, outgoingMsg.messageType)
        assertFalse("Standard voice note should not be emergency", outgoingMsg.isEmergency)
        assertTrue("rawUtf8ByteSize must be calculated", outgoingMsg.rawUtf8ByteSize > 0)
        assertTrue("compressedByteSize must be calculated", outgoingMsg.compressedByteSize > 0)

        // Verify status message is updated with STT output
        val status = checkNotNull(finalState.statusMessage)
        assertTrue(status.contains("Audio captured successfully"))
        assertTrue(status.contains(expectedTranscript))
    }

    @Test
    fun testEmptyAudioHandling_GracefulWithoutCrash() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Create a custom AudioEngine that returns 0 bytes (e.g. empty mic capture or instant tap)
        val emptyAudioEngine = object : AudioEngine {
            val _state = MutableStateFlow(AudioRecordingState.IDLE)
            override val recordingState: StateFlow<AudioRecordingState> = _state.asStateFlow()
            override val audioLevel: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()

            override suspend fun startRecording() {
                _state.value = AudioRecordingState.RECORDING
            }

            override suspend fun stopRecording(): ByteArray {
                _state.value = AudioRecordingState.IDLE
                return ByteArray(0)
            }

            override fun release() {
                _state.value = AudioRecordingState.IDLE
            }
        }

        val emptyVm = MainViewModel(
            audioEngine = emptyAudioEngine,
            speechEngine = mockSpeechEngine,
            transportEngine = mockTransportEngine,
            compressionEngine = mockCompressionEngine,
            serviceController = mockServiceController,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        // Press PTT
        emptyVm.onPttPressed()
        advanceUntilIdle()
        assertEquals(PttState.RECORDING, emptyVm.appState.value.pttState)

        // Release PTT immediately returning 0 bytes
        emptyVm.onPttReleased()
        advanceUntilIdle()

        val state = emptyVm.appState.value

        // Verify graceful return to IDLE without crash
        assertEquals(PttState.IDLE, state.pttState)
        assertEquals("Audio capture too short (0 bytes)", state.statusMessage)
        assertNull(state.errorMessage)

        // Verify SpeechEngine was NOT invoked for empty audio
        assertEquals(0, mockSpeechEngine.transcribeCallCount)

        // Verify NO outgoing message was added
        assertTrue(state.messages.isEmpty())

        emptyVm.onCleared()
    }

    @Test
    fun testEmergencyAlertTaggedVoiceNote() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Arm emergency broadcast mode
        viewModel.onEmergencyAlertToggled(true)
        advanceUntilIdle()
        assertTrue(viewModel.appState.value.emergencyAlert.isActive)

        val emergencyTranscript = "MAYDAY MAYDAY: Coastal surge breaching seawall"
        mockSpeechEngine.simulatedTranscriptionText = emergencyTranscript

        viewModel.onPttPressed()
        advanceUntilIdle()

        viewModel.onPttReleased()
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals(1, state.messages.size)
        val msg = state.messages[0]

        // Verify emergency attributes
        assertTrue("Message must be flagged as emergency", msg.isEmergency)
        assertEquals(MessageType.EMERGENCY_ALERT, msg.messageType)
        assertEquals(emergencyTranscript, msg.text)
        assertTrue(msg.isOutgoing)
    }

    @Test
    fun testSttException_GracefulDegradation() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Create a SpeechEngine that simulates an inference failure
        val failingSpeechEngine = object : SpeechEngine {
            override val isModelLoaded: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
            override val loadErrorMessage: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
            override val lastLatencyMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

            override suspend fun transcribe(audio: ByteArray): String {
                throw RuntimeException("ONNX Runtime inference error: memory limit reached")
            }

            override fun release() {}
        }

        val errorVm = MainViewModel(
            audioEngine = mockAudioEngine,
            speechEngine = failingSpeechEngine,
            transportEngine = mockTransportEngine,
            compressionEngine = mockCompressionEngine,
            serviceController = mockServiceController,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        errorVm.onPttPressed()
        advanceUntilIdle()

        errorVm.onPttReleased()
        advanceUntilIdle()

        val state = errorVm.appState.value

        // Verify graceful error handling
        assertEquals(PttState.IDLE, state.pttState)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("ONNX Runtime inference error"))
        assertNotNull(state.sttDiagnostics.errorMessage)
        assertTrue(state.sttDiagnostics.errorMessage!!.contains("ONNX Runtime inference error"))
        assertTrue(state.statusMessage?.contains("STT Error") == true)
        // No corrupt message should be appended
        assertTrue(state.messages.isEmpty())

        errorVm.onCleared()
    }

    @Test
    fun testBlankTranscription_DoesNotCreateEmptyMessage() = runTest(testDispatcher) {
        advanceUntilIdle()

        // SpeechEngine returns empty transcript (e.g. ambient silence without words)
        mockSpeechEngine.simulatedTranscriptionText = ""

        viewModel.onPttPressed()
        advanceUntilIdle()

        viewModel.onPttReleased()
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals(PttState.IDLE, state.pttState)
        assertEquals("", state.sttDiagnostics.recognizedText)
        assertTrue("No outgoing message should be added when transcription is blank", state.messages.isEmpty())
        assertTrue(state.statusMessage?.contains("Audio captured successfully") == true)
    }

    @Test
    fun testModelLoadStateObservation() = runTest(testDispatcher) {
        advanceUntilIdle()

        val modelStateFlow = MutableStateFlow(false)
        val errorStateFlow = MutableStateFlow<String?>(null)

        val dynamicSpeechEngine = object : SpeechEngine {
            override val isModelLoaded: StateFlow<Boolean> = modelStateFlow.asStateFlow()
            override val loadErrorMessage: StateFlow<String?> = errorStateFlow.asStateFlow()
            override val lastLatencyMs: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

            override suspend fun transcribe(audio: ByteArray): String = ""
            override fun release() {}
        }

        val dynamicVm = MainViewModel(
            audioEngine = mockAudioEngine,
            speechEngine = dynamicSpeechEngine,
            transportEngine = mockTransportEngine,
            compressionEngine = mockCompressionEngine,
            serviceController = mockServiceController,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        // Initially not loaded and no error -> INITIALIZING
        assertEquals(SubsystemState.INITIALIZING, dynamicVm.appState.value.subsystems.stt)
        assertFalse(dynamicVm.appState.value.sttDiagnostics.isModelLoaded)

        // Model finishes loading -> READY
        modelStateFlow.value = true
        advanceUntilIdle()
        assertEquals(SubsystemState.READY, dynamicVm.appState.value.subsystems.stt)
        assertTrue(dynamicVm.appState.value.sttDiagnostics.isModelLoaded)

        // Model error occurs -> ERROR
        modelStateFlow.value = false
        errorStateFlow.value = "Failed to map assets/sherpa-onnx model file"
        advanceUntilIdle()
        assertEquals(SubsystemState.ERROR, dynamicVm.appState.value.subsystems.stt)
        assertFalse(dynamicVm.appState.value.sttDiagnostics.isModelLoaded)
        assertEquals("Failed to map assets/sherpa-onnx model file", dynamicVm.appState.value.sttDiagnostics.errorMessage)

        dynamicVm.onCleared()
    }
}
