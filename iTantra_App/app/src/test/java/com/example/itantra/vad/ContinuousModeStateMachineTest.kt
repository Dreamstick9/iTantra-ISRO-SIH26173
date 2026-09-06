package com.example.itantra.vad

import com.example.itantra.audio.AudioConfig
import com.example.itantra.audio.AudioTrackPlayer
import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.data.ContinuousModeState
import com.example.itantra.data.MessagePlaybackStatus
import com.example.itantra.data.TransceiverState
import com.example.itantra.data.TransmissionMode
import com.example.itantra.service.MockServiceController
import com.example.itantra.speech.MockSpeechEngine
import com.example.itantra.transport.MockTransportEngine
import com.example.itantra.tts.MockTtsEngine
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

/**
 * Unit test suite verifying the strict 8-state machine for Continuous Mode:
 * IDLE -> LISTENING -> SPEECH_DETECTED -> RECORDING -> POSSIBLE_END -> FINALIZING -> TRANSCRIBING -> TRANSMITTING -> LISTENING
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousModeStateMachineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockAudioEngine: MockAudioEngine
    private lateinit var mockSpeechEngine: MockSpeechEngine
    private lateinit var mockTtsEngine: MockTtsEngine
    private lateinit var mockTransportEngine: MockTransportEngine
    private lateinit var mockVadEngine: MockVadEngine
    private lateinit var viewModel: MainViewModel

    // Standard 512-sample (1024-byte) frame at 16kHz 16-bit mono (32ms)
    private val standardFrame = ByteArray(1024) { (it % 64).toByte() }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAudioEngine = MockAudioEngine()
        mockSpeechEngine = MockSpeechEngine()
        mockTtsEngine = MockTtsEngine(ioDispatcher = testDispatcher)
        mockTransportEngine = MockTransportEngine()
        mockVadEngine = MockVadEngine()

        viewModel = MainViewModel(
            audioEngine = mockAudioEngine,
            speechEngine = mockSpeechEngine,
            ioDispatcher = testDispatcher,
            ttsEngine = mockTtsEngine,
            transportEngine = mockTransportEngine,
            compressionEngine = MockCompressionEngine(),
            serviceController = MockServiceController(),
            audioTrackPlayer = AudioTrackPlayer(testDispatcher),
            vadEngine = mockVadEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialContinuousModeStateIsIdle() {
        val state = viewModel.appState.value
        assertEquals(ContinuousModeState.IDLE, state.continuousModeState)
        assertEquals(TransmissionMode.PUSH_TO_TALK, state.transmissionMode)
        assertTrue(state.continuousModeState.isIdle)
        assertFalse(state.continuousModeState.isActive)
    }

    @Test
    fun testStartContinuousModeTransitionsToListening() = runTest(testDispatcher) {
        viewModel.startContinuousMode()
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals(ContinuousModeState.LISTENING, state.continuousModeState)
        assertEquals(TransmissionMode.CONTINUOUS, state.transmissionMode)
        assertTrue(state.continuousModeState.isListening)
        assertTrue(state.continuousModeState.isActive)
    }

    @Test
    fun testSpeechDetectedTransitionsToRecordingWithPreSpeechPreserved() = runTest(testDispatcher) {
        viewModel.startContinuousMode()
        advanceUntilIdle()
        assertEquals(ContinuousModeState.LISTENING, viewModel.appState.value.continuousModeState)

        // Stream 3 silence frames into pre-speech buffer
        mockVadEngine.setSpeechDetected(false)
        repeat(3) {
            mockAudioEngine.emitStreamChunk(standardFrame)
        }
        advanceUntilIdle()
        assertEquals(ContinuousModeState.LISTENING, viewModel.appState.value.continuousModeState)

        // Now speech is detected by Silero VAD
        mockVadEngine.setSpeechDetected(true)
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()

        // State should transition through SPEECH_DETECTED directly to RECORDING
        val state = viewModel.appState.value
        assertEquals(ContinuousModeState.RECORDING, state.continuousModeState)
        assertEquals(TransceiverState.RECORDING, state.transceiverState)
        assertTrue(state.continuousModeState.isRecording)
    }

    @Test
    fun testInterWordPauseTransitionsToPossibleEndAndResumesRecording() = runTest(testDispatcher) {
        viewModel.startContinuousMode()
        advanceUntilIdle()

        // Enter RECORDING
        mockVadEngine.setSpeechDetected(true)
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()
        assertEquals(ContinuousModeState.RECORDING, viewModel.appState.value.continuousModeState)

        // Temporary pause between words: VAD detects silence
        mockVadEngine.setSpeechDetected(false)
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()

        // State enters POSSIBLE_END
        assertEquals(ContinuousModeState.POSSIBLE_END, viewModel.appState.value.continuousModeState)
        assertTrue(viewModel.appState.value.continuousModeState.isPossibleEnd)

        // Speaker resumes before silence threshold: VAD detects speech again
        mockVadEngine.setSpeechDetected(true)
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()

        // State cleanly returns to RECORDING
        assertEquals(ContinuousModeState.RECORDING, viewModel.appState.value.continuousModeState)
        assertTrue(viewModel.appState.value.continuousModeState.isRecording)
    }

    @Test
    fun testSilenceTimeoutFinalizesTranscribesTransmitsAndLoopsToListening() = runTest(testDispatcher) {
        mockSpeechEngine.simulatedTranscriptionText = "EVACUATE COASTAL SECTOR 4"

        viewModel.startContinuousMode()
        advanceUntilIdle()

        // 1. Speech starts -> enters RECORDING
        mockVadEngine.setSpeechDetected(true)
        repeat(4) {
            mockAudioEngine.emitStreamChunk(standardFrame)
        }
        advanceUntilIdle()
        assertEquals(ContinuousModeState.RECORDING, viewModel.appState.value.continuousModeState)

        // 2. Speech stops -> enters POSSIBLE_END
        mockVadEngine.setSpeechDetected(false)
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()
        assertEquals(ContinuousModeState.POSSIBLE_END, viewModel.appState.value.continuousModeState)

        // 3. Silero VAD confirms silence duration satisfied (segment ready)
        mockVadEngine.pushSegment(FloatArray(512))
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()

        // 4. Verify pipeline finalized, transcribed, transmitted, and looped back to LISTENING
        val state = viewModel.appState.value
        assertEquals(ContinuousModeState.LISTENING, state.continuousModeState)
        assertEquals(TransceiverState.IDLE, state.transceiverState)

        // Verify message was transmitted over transport
        assertEquals(1, mockTransportEngine.sentTransportMessages.size)
        val sentMsg = mockTransportEngine.sentTransportMessages.first()
        assertEquals("EVACUATE COASTAL SECTOR 4", sentMsg.text)

        // Verify outgoing message logged in app state
        assertEquals(1, state.messages.size)
        val logged = state.messages.first()
        assertTrue(logged.isOutgoing)
        assertEquals("EVACUATE COASTAL SECTOR 4", logged.text)
        assertEquals(MessagePlaybackStatus.PLAYED, logged.playbackStatus)

        // Verify latency timestamps were captured
        assertTrue(state.verticalSliceTimestamps.t0PttReleased > 0)
        assertTrue(state.verticalSliceTimestamps.t1SttComplete >= state.verticalSliceTimestamps.t0PttReleased)
        assertTrue(state.verticalSliceTimestamps.t2Transmitted >= state.verticalSliceTimestamps.t1SttComplete)
        assertNotNull(state.lastLatencyMetrics)

        // Verify VAD was reset for next utterance
        assertTrue(mockVadEngine.resetCount > 0)
    }

    @Test
    fun testBlankTranscriptBypassesTransmissionAndReturnsToListening() = runTest(testDispatcher) {
        mockSpeechEngine.simulatedTranscriptionText = "   " // Blank transcription (hallucination/noise)

        viewModel.startContinuousMode()
        advanceUntilIdle()

        // Speech starts
        mockVadEngine.setSpeechDetected(true)
        repeat(4) {
            mockAudioEngine.emitStreamChunk(standardFrame)
        }
        advanceUntilIdle()

        // Silence occurs -> enters POSSIBLE_END
        mockVadEngine.setSpeechDetected(false)
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()
        assertEquals(ContinuousModeState.POSSIBLE_END, viewModel.appState.value.continuousModeState)

        // Silero VAD confirms silence segment -> FINALIZING -> TRANSCRIBING -> LISTENING
        mockVadEngine.pushSegment(FloatArray(512))
        mockAudioEngine.emitStreamChunk(standardFrame)
        advanceUntilIdle()

        // Should return to LISTENING without transmitting blank message
        val state = viewModel.appState.value
        assertEquals(ContinuousModeState.LISTENING, state.continuousModeState)
        assertEquals(0, mockTransportEngine.sentTransportMessages.size)
        assertEquals(0, state.messages.size)
    }

    @Test
    fun testEchoSuppressionDuringIncomingTtsSpeaking() = runTest(testDispatcher) {
        viewModel.startContinuousMode()
        advanceUntilIdle()
        assertEquals(ContinuousModeState.LISTENING, viewModel.appState.value.continuousModeState)

        // Simulate incoming remote message causing local TTS speaking
        val incoming = com.example.itantra.transport.TransportMessage.text("INCOMING ADVISORY")
        mockTransportEngine.simulateIncomingMessage(incoming)
        advanceUntilIdle()

        // Phone B plays audio and state is IDLE / PLAYED
        assertEquals(ContinuousModeState.LISTENING, viewModel.appState.value.continuousModeState)
    }

    @Test
    fun testStopContinuousModeResetsToIdle() = runTest(testDispatcher) {
        viewModel.startContinuousMode()
        advanceUntilIdle()
        assertEquals(ContinuousModeState.LISTENING, viewModel.appState.value.continuousModeState)

        viewModel.stopContinuousMode()
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals(ContinuousModeState.IDLE, state.continuousModeState)
        assertEquals(TransceiverState.IDLE, state.transceiverState)
    }

    @Test
    fun testToggleContinuousModeTurnsOnAndOff() = runTest(testDispatcher) {
        assertEquals(ContinuousModeState.IDLE, viewModel.appState.value.continuousModeState)

        viewModel.onToggleContinuousMode()
        advanceUntilIdle()
        assertEquals(ContinuousModeState.LISTENING, viewModel.appState.value.continuousModeState)

        viewModel.onToggleContinuousMode()
        advanceUntilIdle()
        assertEquals(ContinuousModeState.IDLE, viewModel.appState.value.continuousModeState)
    }
}
