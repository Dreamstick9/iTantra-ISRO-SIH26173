package com.example.itantra.ui

import com.example.itantra.audio.AudioConfig
import com.example.itantra.audio.AudioEngine
import com.example.itantra.audio.AudioRecordingState
import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.data.Language
import com.example.itantra.data.MessagePlaybackStatus
import com.example.itantra.data.PttState
import com.example.itantra.data.TransceiverState
import com.example.itantra.service.MockServiceController
import com.example.itantra.speech.MockSpeechEngine
import com.example.itantra.transport.MockTransportEngine
import com.example.itantra.transport.TransportMessage
import com.example.itantra.tts.AudioData
import com.example.itantra.tts.MockTtsEngine
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
 * End-to-end integration tests verifying the full Voice Transceiver Pipeline in MainViewModel.
 * Tests Phone A send flow (PTT press -> record -> STT -> transmit -> latency metrics)
 * and Phone B receive & speak flow (receive -> TTS synthesis -> play -> E2E latency metrics).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTransceiverPipelineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockAudioEngine: MockAudioEngine
    private lateinit var mockSpeechEngine: MockSpeechEngine
    private lateinit var mockTtsEngine: MockTtsEngine
    private lateinit var mockTransportEngine: MockTransportEngine
    private lateinit var mockCompressionEngine: MockCompressionEngine
    private lateinit var mockServiceController: MockServiceController
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAudioEngine = MockAudioEngine()
        mockSpeechEngine = MockSpeechEngine()
        mockTtsEngine = MockTtsEngine(ioDispatcher = testDispatcher, simulatedLatencyMs = 5L)
        mockTransportEngine = MockTransportEngine()
        mockCompressionEngine = MockCompressionEngine()
        mockServiceController = MockServiceController()

        viewModel = MainViewModel(
            audioEngine = mockAudioEngine,
            speechEngine = mockSpeechEngine,
            ttsEngine = mockTtsEngine,
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

    // ========================================================================
    // PHONE A SEND FLOW TESTS
    // ========================================================================

    @Test
    fun testPhoneASendFlow_CompletePipeline() = runTest(testDispatcher) {
        advanceUntilIdle()

        // 1. Verify initial transceiver state
        assertEquals(TransceiverState.IDLE, viewModel.appState.value.transceiverState)
        assertEquals(PttState.IDLE, viewModel.appState.value.pttState)

        val recognizedText = "High seas warning: Gale force wind approaching."
        mockSpeechEngine.simulatedTranscriptionText = recognizedText

        // 2. onPttPressed()
        viewModel.onPttPressed()
        advanceUntilIdle()

        assertEquals(TransceiverState.RECORDING, viewModel.appState.value.transceiverState)
        assertEquals(PttState.RECORDING, viewModel.appState.value.pttState)
        assertTrue(mockAudioEngine.isRecording)
        assertTrue(viewModel.appState.value.isPttPressed)

        // 3. onPttReleased()
        viewModel.onPttReleased()
        advanceUntilIdle()

        val state = viewModel.appState.value

        // 4. Verify state returns to IDLE
        assertEquals(TransceiverState.IDLE, state.transceiverState)
        assertEquals(PttState.IDLE, state.pttState)
        assertFalse(mockAudioEngine.isRecording)
        assertFalse(state.isPttPressed)

        // 5. Verify transport sent a message
        assertEquals(1, mockTransportEngine.sentTransportMessages.size)
        val sentMsg = mockTransportEngine.sentTransportMessages[0]
        assertEquals(recognizedText, sentMsg.text)
        assertTrue("t0 timestamp must be recorded", sentMsg.t0 > 0L)
        assertTrue("t1 timestamp must be recorded", sentMsg.t1 >= sentMsg.t0)
        assertTrue("sttLatencyMs must be recorded", sentMsg.sttLatencyMs >= 0L)
        assertTrue("audioDurationMs must be recorded", sentMsg.audioDurationMs > 0L)

        // 6. Verify VerticalSliceTimestamps on Phone A
        val vsTimestamps = state.verticalSliceTimestamps
        assertTrue(vsTimestamps.t0PttReleased > 0L)
        assertTrue(vsTimestamps.t1SttComplete >= vsTimestamps.t0PttReleased)
        assertTrue(vsTimestamps.t2Transmitted >= vsTimestamps.t1SttComplete)
        assertEquals(sentMsg.sttLatencyMs, vsTimestamps.sttLatencyMs)
        assertEquals(vsTimestamps.t2Transmitted - vsTimestamps.t0PttReleased, vsTimestamps.endToEndLatencyMs)

        // 7. Verify outgoing message history
        assertEquals(1, state.messages.size)
        val outMsg = state.messages[0]
        assertTrue(outMsg.isOutgoing)
        assertEquals(recognizedText, outMsg.text)
        assertEquals("Local Operator (You)", outMsg.senderName)
        assertNotNull(outMsg.latencyMetrics)
        assertEquals(vsTimestamps.sttLatencyMs, outMsg.latencyMetrics?.sttLatencyMs)
    }

    @Test
    fun testPhoneASendFlow_PttPressedIgnoredWhenNotIdle() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onPttPressed()
        advanceUntilIdle()
        assertEquals(TransceiverState.RECORDING, viewModel.appState.value.transceiverState)

        // Calling onPttPressed again while already RECORDING must be ignored
        viewModel.onPttPressed()
        advanceUntilIdle()
        assertEquals(TransceiverState.RECORDING, viewModel.appState.value.transceiverState)

        viewModel.onPttReleased()
        advanceUntilIdle()
        assertEquals(TransceiverState.IDLE, viewModel.appState.value.transceiverState)
    }

    @Test
    fun testPhoneASendFlow_PttReleasedIgnoredWhenNotRecording() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Calling onPttReleased when IDLE must do nothing
        viewModel.onPttReleased()
        advanceUntilIdle()

        assertEquals(TransceiverState.IDLE, viewModel.appState.value.transceiverState)
        assertEquals(0, mockTransportEngine.sentTransportMessages.size)
        assertTrue(viewModel.appState.value.messages.isEmpty())
    }

    @Test
    fun testPhoneASendFlow_AudioTooShortResetToIdle() = runTest(testDispatcher) {
        advanceUntilIdle()

        val shortAudioEngine = object : AudioEngine {
            val _recState = MutableStateFlow(AudioRecordingState.IDLE)
            override val recordingState: StateFlow<AudioRecordingState> = _recState.asStateFlow()
            override val audioLevel: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()
            override suspend fun startRecording() { _recState.value = AudioRecordingState.RECORDING }
            override suspend fun stopRecording(): ByteArray {
                _recState.value = AudioRecordingState.IDLE
                return ByteArray(1000) // Less than 3200 bytes (100ms)
            }
            override fun release() {}
        }

        val shortVm = MainViewModel(
            audioEngine = shortAudioEngine,
            speechEngine = mockSpeechEngine,
            ttsEngine = mockTtsEngine,
            transportEngine = mockTransportEngine,
            compressionEngine = mockCompressionEngine,
            serviceController = mockServiceController,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        shortVm.onPttPressed()
        advanceUntilIdle()
        assertEquals(TransceiverState.RECORDING, shortVm.appState.value.transceiverState)

        shortVm.onPttReleased()
        advanceUntilIdle()

        val state = shortVm.appState.value
        assertEquals(TransceiverState.IDLE, state.transceiverState)
        assertEquals(PttState.IDLE, state.pttState)
        assertTrue("Status message should indicate audio too short", state.statusMessage?.contains("Audio too short") == true)
        assertEquals(0, mockSpeechEngine.transcribeCallCount)
        assertTrue(state.messages.isEmpty())

        shortVm.onCleared()
    }

    @Test
    fun testPhoneASendFlow_BlankSpeechRecognitionResetToIdle() = runTest(testDispatcher) {
        advanceUntilIdle()

        mockSpeechEngine.simulatedTranscriptionText = "   " // blank recognized text

        viewModel.onPttPressed()
        advanceUntilIdle()

        viewModel.onPttReleased()
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals(TransceiverState.IDLE, state.transceiverState)
        assertEquals(PttState.IDLE, state.pttState)
        assertTrue("Status should indicate no speech recognized", state.statusMessage?.contains("No speech recognized") == true)
        assertEquals(0, mockTransportEngine.sentTransportMessages.size)
        assertTrue(state.messages.isEmpty())
    }

    // ========================================================================
    // PHONE B RECEIVE & SPEAK FLOW TESTS
    // ========================================================================

    @Test
    fun testPhoneBReceiveAndSpeakFlow_CompletePipeline() = runTest(testDispatcher) {
        advanceUntilIdle()

        val now = System.currentTimeMillis()
        val t0 = now - 500L
        val t1 = now - 320L
        val t2 = now - 300L
        val sttLatency = 180L
        val audioDuration = 1900L
        val incomingText = "Tsunami warning: Vessel safety protocol active."

        val transportMsg = TransportMessage.text(
            text = incomingText,
            t0 = t0,
            t1 = t1,
            t2 = t2,
            sttLatencyMs = sttLatency,
            audioDurationMs = audioDuration
        )

        // Simulate incoming message over transport flow
        mockTransportEngine.simulateIncomingMessage(transportMsg)
        advanceUntilIdle()

        val state = viewModel.appState.value

        // 1. Verify state returns to IDLE after speaking
        assertEquals(TransceiverState.IDLE, state.transceiverState)
        assertEquals(PttState.IDLE, state.pttState)
        assertFalse(state.isAudioPlaying)

        // 2. Verify message history updated
        assertEquals(1, state.messages.size)
        val msg = state.messages[0]
        assertEquals(incomingText, msg.text)
        assertFalse(msg.isOutgoing)
        assertEquals(MessagePlaybackStatus.PLAYED, msg.playbackStatus)

        // 3. Verify VerticalSliceTimestamps on Phone B
        val vsTimestamps = state.verticalSliceTimestamps
        assertEquals(t0, vsTimestamps.t0PttReleased)
        assertEquals(t1, vsTimestamps.t1SttComplete)
        assertEquals(t2, vsTimestamps.t2Transmitted)
        assertTrue("t3Received must be recorded", vsTimestamps.t3Received > 0L)
        assertTrue("t4TtsComplete must be recorded", vsTimestamps.t4TtsComplete >= vsTimestamps.t3Received)
        assertTrue("t5PlaybackStarted must be recorded", vsTimestamps.t5PlaybackStarted >= vsTimestamps.t4TtsComplete)
        assertEquals(sttLatency, vsTimestamps.sttLatencyMs)
        assertTrue("networkLatencyMs must be >= 0", vsTimestamps.networkLatencyMs >= 0L)
        assertTrue("ttsLatencyMs must be >= 0", vsTimestamps.ttsLatencyMs >= 0L)
        assertTrue("endToEndLatencyMs must be positive", vsTimestamps.endToEndLatencyMs > 0L)

        // 4. Verify LatencyMetrics
        val latency = state.lastLatencyMetrics
        assertNotNull(latency)
        assertEquals(sttLatency, latency?.sttLatencyMs)
        assertEquals(vsTimestamps.networkLatencyMs, latency?.transmissionLatencyMs)
        assertEquals(vsTimestamps.ttsLatencyMs, latency?.ttsLatencyMs)
        assertEquals(vsTimestamps.endToEndLatencyMs, latency?.endToEndLatencyMs)
    }

    @Test
    fun testPhoneBReceiveAndSpeakFlow_TtsSynthesisFailureGraceful() = runTest(testDispatcher) {
        advanceUntilIdle()

        val failingTtsEngine = MockTtsEngine(ioDispatcher = testDispatcher, shouldFail = true)

        val failVm = MainViewModel(
            audioEngine = mockAudioEngine,
            speechEngine = mockSpeechEngine,
            ttsEngine = failingTtsEngine,
            transportEngine = mockTransportEngine,
            compressionEngine = mockCompressionEngine,
            serviceController = mockServiceController,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        val incomingMsg = TransportMessage.text(text = "Hello from satellite")
        mockTransportEngine.simulateIncomingMessage(incomingMsg)
        advanceUntilIdle()

        val state = failVm.appState.value
        assertEquals(TransceiverState.IDLE, state.transceiverState)
        assertEquals(1, state.messages.size)
        assertEquals(MessagePlaybackStatus.FAILED, state.messages[0].playbackStatus)
        assertEquals("TTS synthesis failed", state.errorMessage)

        failVm.onCleared()
    }
}
