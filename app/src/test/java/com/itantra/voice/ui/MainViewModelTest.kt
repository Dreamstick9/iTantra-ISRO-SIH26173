package com.itantra.voice.ui

import android.media.AudioFormat
import android.media.AudioRecord
import com.itantra.voice.audio.AudioPlayer
import com.itantra.voice.audio.AudioRecorder
import com.itantra.voice.audio.NativeAudioRecord
import com.itantra.voice.audio.NativeMediaPlayer
import com.itantra.voice.data.Language
import com.itantra.voice.pipeline.PipelineMode
import com.itantra.voice.pipeline.SpeechPipeline
import com.itantra.voice.pipeline.SynthesisResult
import com.itantra.voice.pipeline.TranscriptionResult
import com.itantra.voice.pipeline.TranslationResult
import com.itantra.voice.transport.DiscoveredPeer
import com.itantra.voice.transport.TransportConnectionState
import com.itantra.voice.transport.TransportEngine
import com.itantra.voice.transport.TransportMessage
import com.itantra.voice.transport.TransportMessageType
import com.itantra.voice.transport.TransportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private class FakeNativeAudioRecord(
        override var state: Int = AudioRecord.STATE_INITIALIZED,
        override var recordingState: Int = AudioRecord.RECORDSTATE_STOPPED,
        val audioBytesToSupply: ByteArray = ByteArray(6400) { (it % 100).toByte() }
    ) : NativeAudioRecord {
        var startRecordingCalled = false
        var stopCalled = false
        var releaseCalled = false
        private var offset = 0

        override fun startRecording() {
            startRecordingCalled = true
            recordingState = AudioRecord.RECORDSTATE_RECORDING
            offset = 0
        }

        override fun stop() {
            stopCalled = true
            recordingState = AudioRecord.RECORDSTATE_STOPPED
            offset = 0
        }

        override fun release() {
            releaseCalled = true
            offset = 0
        }

        override fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) return -1
            if (offset >= audioBytesToSupply.size) return 0
            val count = minOf(audioBytesToSupply.size - offset, sizeInBytes)
            System.arraycopy(audioBytesToSupply, offset, audioData, offsetInBytes, count)
            offset += count
            return count
        }
    }

    private class FakeNativeMediaPlayer : NativeMediaPlayer {
        override var onCompletionListener: (() -> Unit)? = null
        override var onErrorListener: ((what: Int, extra: Int) -> Boolean)? = null

        var startCalled = false
        var stopCalled = false
        var releaseCalled = false
        private var playing = false

        override fun setAudioAttributes(contentType: Int, usage: Int) {}
        override fun setDataSource(path: String) {}
        override fun prepare() {}

        override fun start() {
            startCalled = true
            playing = true
        }

        override fun stop() {
            stopCalled = true
            playing = false
        }

        override fun reset() {
            playing = false
        }

        override fun release() {
            releaseCalled = true
            playing = false
        }

        override val isPlaying: Boolean get() = playing

        fun triggerCompletion() {
            playing = false
            onCompletionListener?.invoke()
        }

        fun triggerError(what: Int = 1, extra: Int = -1) {
            playing = false
            onErrorListener?.invoke(what, extra)
        }
    }

    /**
     * Deterministic stand-in for a real speech engine. Each stage returns a canned
     * value, or a canned failure, so the FSM can be driven through every branch without
     * a network, a microphone, or an Android TTS service.
     */
    private class FakeSpeechPipeline(
        var transcript: String = "नमस्ते",
        var translation: String = "Hello",
        var audio: ByteArray = com.itantra.voice.audio.WavEncoder.encode(ByteArray(3200) { 7 }),
        var transcribeError: Throwable? = null,
        var translateError: Throwable? = null,
        var synthesizeError: Throwable? = null,
        var available: Boolean = true
    ) : SpeechPipeline {
        override val mode = PipelineMode.ON_DEVICE
        override val displayName = "Fake"

        var lastSynthesisWasEmergency: Boolean? = null
        var synthesisCallCount = 0
        var releaseCount = 0

        /** Stages executed, in order, so tests can assert the pipeline short-circuits. */
        val stageCalls = mutableListOf<String>()

        override suspend fun isAvailable() = available

        override suspend fun transcribe(wavData: ByteArray, source: Language): Result<TranscriptionResult> {
            stageCalls.add("stt")
            return transcribeError?.let { Result.failure(it) }
                ?: Result.success(TranscriptionResult(transcript))
        }

        override suspend fun translate(text: String, source: Language, target: Language): Result<TranslationResult> {
            stageCalls.add("translate")
            return translateError?.let { Result.failure(it) }
                ?: Result.success(TranslationResult(translation))
        }

        override suspend fun synthesize(text: String, target: Language, isEmergency: Boolean):
            Result<SynthesisResult> {
            stageCalls.add("tts")
            lastSynthesisWasEmergency = isEmergency
            synthesisCallCount++
            return synthesizeError?.let { Result.failure(it) }
                ?: Result.success(SynthesisResult(wavBytes = audio))
        }

        override fun release() {
            releaseCount++
        }
    }

    private lateinit var fakeRecord: FakeNativeAudioRecord
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var fakePipeline: FakeSpeechPipeline
    private lateinit var fakePlayer: FakeNativeMediaPlayer
    private lateinit var audioPlayer: AudioPlayer
    private var mockCurrentTime = 1000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeRecord = FakeNativeAudioRecord()
        audioRecorder = AudioRecorder(
            ioDispatcher = testDispatcher,
            recordProvider = { _, _, _, _, _ -> fakeRecord }
        )

        fakePipeline = FakeSpeechPipeline()

        val cacheDir = tempFolder.newFolder("test_cache")
        fakePlayer = FakeNativeMediaPlayer()
        audioPlayer = AudioPlayer(cacheDir, playerFactory = { fakePlayer })
        mockCurrentTime = 1000L
    }

    @After
    fun tearDown() {
        try {
            runBlocking { audioRecorder.stopRecording() }
            audioRecorder.release()
        } catch (ignored: Exception) {}
        try {
            audioPlayer.stopAndRelease()
            audioPlayer.release()
        } catch (ignored: Exception) {}
        Dispatchers.resetMain()
    }

    private fun createViewModel(transportEngine: TransportEngine? = null): MainViewModel {
        return MainViewModel(
            audioRecorder = audioRecorder,
            speechPipeline = fakePipeline,
            audioPlayer = audioPlayer,
            transportEngine = transportEngine,
            ioDispatcher = testDispatcher,
            timeProvider = { mockCurrentTime }
        ).also {
            // Push-to-talk is permission-gated; every PTT test assumes it was granted.
            it.onPermissionResult(true)
        }
    }


    private fun executePttHold(viewModel: MainViewModel, durationMs: Long = 600L) {
        val start = mockCurrentTime
        viewModel.onPttPress()
        testDispatcher.scheduler.runCurrent()
        mockCurrentTime = start + durationMs
        viewModel.onPttRelease()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // 1. Initial State Verification
    @Test
    fun testInitialStateIsIdle() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(PttState.IDLE, state.state)
        assertEquals(Language.DEFAULT_SOURCE, state.sourceLanguage)
        assertEquals(Language.DEFAULT_TARGET, state.targetLanguage)
        assertEquals("", state.sourceTranscript)
        assertEquals("", state.translatedText)
        assertFalse(state.hasAudioToReplay)
        assertNull(state.errorMessage)
        assertFalse(state.isHolding)
        assertFalse(state.isBusy)
        assertEquals(0L, state.latencies.totalMs)
    }

    // 2. PTT Press Transitions to RECORDING
    @Test
    fun testPttPressTransitionsToRecording() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onPttPress()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(PttState.RECORDING, state.state)
        assertTrue(state.isHolding)
        assertTrue(audioRecorder.isRecording)
        assertTrue(fakeRecord.startRecordingCalled)

        // Stop recording cleanly to terminate background loop
        audioRecorder.stopRecording()
        advanceUntilIdle()
    }

    // 3. Short Tap (<300ms) Cancels to IDLE Without Network Requests
    @Test
    fun testShortTapLessThan300msCancelsToIdleWithoutNetworkRequests() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        mockCurrentTime = 1000L
        viewModel.onPttPress()
        testScheduler.runCurrent()

        // Released after only 150ms (< 300ms)
        mockCurrentTime = 1150L
        viewModel.onPttRelease()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PttState.IDLE, state.state)
        assertFalse(state.isHolding)
        assertEquals("Hold the button while speaking.", state.errorMessage)
        assertEquals("No pipeline work for a short tap", 0, fakePipeline.stageCalls.size)
    }

    // 4. Full Pipeline Success: IDLE -> RECORDING -> TRANSCRIBING -> TRANSLATING -> SYNTHESIZING -> PLAYING -> IDLE
    @Test
    fun testFullPipelineSuccess() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // Execute PTT hold and release
        executePttHold(viewModel, durationMs = 600L)

        // Pipeline completed through STT, Translate, TTS to PLAYING
        val stateWhilePlaying = viewModel.uiState.value
        assertEquals(PttState.PLAYING, stateWhilePlaying.state)
        assertTrue(stateWhilePlaying.hasAudioToReplay)
        assertTrue("Transcript populated", stateWhilePlaying.sourceTranscript.isNotBlank())
        assertTrue("Translated text populated", stateWhilePlaying.translatedText.isNotBlank())
        assertTrue("MediaPlayer started", fakePlayer.startCalled)
        assertEquals(listOf("stt", "translate", "tts"), fakePipeline.stageCalls)

        // Verify Telemetry Latencies
        assertTrue(stateWhilePlaying.latencies.sttMs >= 0)
        assertTrue(stateWhilePlaying.latencies.translateMs >= 0)
        assertTrue(stateWhilePlaying.latencies.ttsMs >= 0)

        // Audio playback completes
        fakePlayer.triggerCompletion()
        testScheduler.runCurrent()

        val finalState = viewModel.uiState.value
        assertEquals(PttState.IDLE, finalState.state)
        assertTrue(finalState.hasAudioToReplay)
    }

    // 5. STT Network Error Handling Transitions to ERROR
    @Test
    fun testSttNetworkErrorTransitionsToError() = runTest(testDispatcher) {
        fakePipeline.transcribeError = IllegalStateException("Speech service unavailable")

        val viewModel = createViewModel()

        executePttHold(viewModel, durationMs = 500L)

        val state = viewModel.uiState.value
        assertEquals(PttState.ERROR, state.state)
        assertNotNull(state.errorMessage)
        assertEquals(listOf("stt"), fakePipeline.stageCalls)
    }

    // 6. Translation Network Error Transitions to ERROR
    @Test
    fun testTranslationErrorTransitionsToError() = runTest(testDispatcher) {
        fakePipeline.translateError = IllegalStateException("Rate limit reached")

        val viewModel = createViewModel()

        executePttHold(viewModel, durationMs = 500L)

        val state = viewModel.uiState.value
        assertEquals(PttState.ERROR, state.state)
        assertNotNull(state.errorMessage)
        assertEquals(listOf("stt", "translate"), fakePipeline.stageCalls)
    }

    // 7. TTS Synthesis Error Transitions to ERROR
    @Test
    fun testSynthesisErrorTransitionsToError() = runTest(testDispatcher) {
        fakePipeline.synthesizeError = IllegalStateException("Synthesis service unavailable")

        val viewModel = createViewModel()

        executePttHold(viewModel, durationMs = 500L)

        val state = viewModel.uiState.value
        assertEquals(PttState.ERROR, state.state)
        assertEquals(listOf("stt", "translate", "tts"), fakePipeline.stageCalls)
    }

    // 8. Silence STT Aborts Downstream Pipeline and Reverts to IDLE
    @Test
    fun testSilenceSttAbortsPipelineAndReturnsToIdle() = runTest(testDispatcher) {
        fakePipeline.transcript = ""

        val viewModel = createViewModel()

        executePttHold(viewModel, durationMs = 500L)

        val state = viewModel.uiState.value
        assertEquals(PttState.IDLE, state.state)
        assertEquals("No speech detected.", state.errorMessage)
        assertEquals("Downstream translation/TTS must be aborted", listOf("stt"), fakePipeline.stageCalls)
    }

    // 9. Replay Plays Cached Audio Without Network Calls
    @Test
    fun testReplayAudioPlaysCachedWavWithoutNetworkCalls() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // First successful turn
        executePttHold(viewModel, durationMs = 500L)
        fakePlayer.triggerCompletion()

        assertEquals(listOf("stt", "translate", "tts"), fakePipeline.stageCalls)
        assertTrue(viewModel.uiState.value.hasAudioToReplay)

        // Trigger Replay Audio
        viewModel.onReplayAudio()
        advanceUntilIdle()

        assertEquals(PttState.PLAYING, viewModel.uiState.value.state)
        assertEquals("Replay must not re-run the pipeline", listOf("stt", "translate", "tts"), fakePipeline.stageCalls)

        // Complete replay playback
        fakePlayer.triggerCompletion()
        testScheduler.runCurrent()

        assertEquals(PttState.IDLE, viewModel.uiState.value.state)
        assertTrue(viewModel.uiState.value.hasAudioToReplay)
    }

    // 10. Language Switching Updates State
    @Test
    fun testLanguageSwitching() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onSourceLanguageChange(Language.TAMIL)
        assertEquals(Language.TAMIL, viewModel.uiState.value.sourceLanguage)

        viewModel.onTargetLanguageChange(Language.MARATHI)
        assertEquals(Language.MARATHI, viewModel.uiState.value.targetLanguage)
    }

    // 11. Language Swap Button Exchanges Source and Target
    @Test
    fun testLanguageSwapExchangesSourceAndTarget() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onSourceLanguageChange(Language.BENGALI)
        viewModel.onTargetLanguageChange(Language.ODIA)
        assertEquals(Language.BENGALI, viewModel.uiState.value.sourceLanguage)
        assertEquals(Language.ODIA, viewModel.uiState.value.targetLanguage)

        viewModel.onSwapLanguages()
        assertEquals(Language.ODIA, viewModel.uiState.value.sourceLanguage)
        assertEquals(Language.BENGALI, viewModel.uiState.value.targetLanguage)
    }

    // 12. Dismiss Error Reverts State to IDLE
    @Test
    fun testDismissErrorRevertsStateToIdle() = runTest(testDispatcher) {
        fakePipeline.transcribeError = IllegalStateException("Speech service unavailable")
        val viewModel = createViewModel()

        executePttHold(viewModel, durationMs = 500L)

        assertEquals(PttState.ERROR, viewModel.uiState.value.state)
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onDismissError()
        assertEquals(PttState.IDLE, viewModel.uiState.value.state)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // 13. PTT Press While Error Clears Error and Starts Recording
    @Test
    fun testPttPressWhileErrorClearsErrorAndStartsRecording() = runTest(testDispatcher) {
        fakePipeline.transcribeError = IllegalStateException("Speech service unavailable")
        val viewModel = createViewModel()

        executePttHold(viewModel, durationMs = 500L)
        assertEquals(PttState.ERROR, viewModel.uiState.value.state)

        // Press again
        mockCurrentTime = 3000L
        viewModel.onPttPress()
        testScheduler.runCurrent()

        assertEquals(PttState.RECORDING, viewModel.uiState.value.state)
        assertNull(viewModel.uiState.value.errorMessage)

        // Clean up recorder
        audioRecorder.stopRecording()
        advanceUntilIdle()
    }

    // 14. PTT Press While Playing Stops Playback Immediately
    @Test
    fun testPttPressWhilePlayingStopsPlaybackImmediately() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // Turn 1
        executePttHold(viewModel, durationMs = 500L)
        fakePlayer.triggerCompletion()

        // Replay -> State is PLAYING
        viewModel.onReplayAudio()
        assertEquals(PttState.PLAYING, viewModel.uiState.value.state)

        // PTT pressed while playing
        mockCurrentTime = 3000L
        viewModel.onPttPress()
        testScheduler.runCurrent()

        assertTrue("MediaPlayer stop must be invoked", fakePlayer.stopCalled)
        assertEquals(PttState.RECORDING, viewModel.uiState.value.state)

        // Clean up recorder
        audioRecorder.stopRecording()
        advanceUntilIdle()
    }

    // 15. Feedback Submission
    @Test
    fun testFeedbackSubmission() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // Feedback is ignored when there is no translated text yet
        viewModel.onFeedback(true)
        assertNull("Feedback should be ignored with no translation", viewModel.uiState.value.feedbackSubmitted)

        // Seed a translation result directly via state reflection
        val stateField = viewModel.javaClass.getDeclaredField("_uiState")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = stateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<com.itantra.voice.ui.MainUiState>
        flow.value = flow.value.copy(translatedText = "I want to go to the station.")

        // First feedback press is accepted
        viewModel.onFeedback(true)
        assertEquals(true, viewModel.uiState.value.feedbackSubmitted)

        // Second feedback press is ignored (one per interaction)
        viewModel.onFeedback(false)
        assertEquals(true, viewModel.uiState.value.feedbackSubmitted)

        // After a new PTT press the feedback resets
        viewModel.onPttPress()
        testScheduler.runCurrent()
        assertNull("Feedback should reset on new PTT press", viewModel.uiState.value.feedbackSubmitted)
        viewModel.uiState.value.let { /* stop recorder immediately */ }
        audioRecorder.stopRecording()
        advanceUntilIdle()
    }


    // 16. Permission Result Handling
    @Test
    fun testPermissionResultHandling() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onPermissionResult(false)
        assertFalse(viewModel.uiState.value.micPermissionGranted)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Microphone permission"))

        viewModel.onPermissionResult(true)
        assertTrue(viewModel.uiState.value.micPermissionGranted)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // 17. Telemetry Resets on Fresh Recording Press
    @Test
    fun testTelemetryResetsOnFreshRecordingPress() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        // Turn 1: has latencies
        executePttHold(viewModel, durationMs = 500L)

        assertTrue(viewModel.uiState.value.latencies.totalMs >= 0)

        // Turn 2: Press PTT resets latencies
        mockCurrentTime = 4000L
        viewModel.onPttPress()
        testScheduler.runCurrent()

        assertEquals(0L, viewModel.uiState.value.latencies.sttMs)
        assertEquals(0L, viewModel.uiState.value.latencies.translateMs)
        assertEquals(0L, viewModel.uiState.value.latencies.ttsMs)
        assertEquals(0L, viewModel.uiState.value.latencies.totalMs)

        // Clean up recorder
        audioRecorder.stopRecording()
        advanceUntilIdle()
    }

    private class FakeTransportEngine(
        initialState: TransportConnectionState = TransportConnectionState.DISCONNECTED
    ) : TransportEngine {
        override val transportType: TransportType = TransportType.WIFI_DIRECT
        private val _state = MutableStateFlow(initialState)
        override val connectionState: StateFlow<TransportConnectionState> = _state.asStateFlow()

        private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
        override val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

        private val _peer = MutableStateFlow<DiscoveredPeer?>(null)
        override val connectedPeer: StateFlow<DiscoveredPeer?> = _peer.asStateFlow()

        // replay = 0 mirrors production: a replay buffer would re-deliver the previous
        // message to every new collector, which is the duplicate-playback bug itself.
        val incomingChannel = MutableSharedFlow<TransportMessage>(replay = 0, extraBufferCapacity = 16)
        override val incomingMessages: Flow<TransportMessage> = incomingChannel.asSharedFlow()

        /** Delivers a message to whatever collectors are currently subscribed. */
        suspend fun emit(message: TransportMessage) = incomingChannel.emit(message)

        val sentMessages = mutableListOf<TransportMessage>()

        fun setState(state: TransportConnectionState) { _state.value = state }
        fun setPeers(peers: List<DiscoveredPeer>) { _peers.value = peers }

        override fun startDiscovery() { _state.value = TransportConnectionState.DISCOVERING }
        override fun stopDiscovery() { _state.value = TransportConnectionState.DISCONNECTED }
        override fun connect(peer: DiscoveredPeer) {
            _state.value = TransportConnectionState.CONNECTED
            _peer.value = peer
        }
        override fun disconnect() {
            _state.value = TransportConnectionState.DISCONNECTED
            _peer.value = null
        }
        override suspend fun send(message: TransportMessage): Result<Unit> {
            sentMessages.add(message)
            return Result.success(Unit)
        }
        override fun release() { disconnect() }
    }

    @Test
    fun testTransportEngineLifecycleAndDiscovery() = runTest {
        val transport = FakeTransportEngine()
        val viewModel = createViewModel(transportEngine = transport)

        assertEquals(TransportConnectionState.DISCONNECTED, viewModel.uiState.value.transportState)

        viewModel.onStartDiscovery()
        testScheduler.runCurrent()
        assertEquals(TransportConnectionState.DISCOVERING, viewModel.uiState.value.transportState)

        val peer = DiscoveredPeer("id1", "Phone-B", "02:00:00:00:00:00")
        transport.setPeers(listOf(peer))
        testScheduler.runCurrent()
        assertEquals(1, viewModel.uiState.value.discoveredPeers.size)
        assertEquals("Phone-B", viewModel.uiState.value.discoveredPeers.first().name)

        viewModel.onConnectPeer(peer)
        testScheduler.runCurrent()
        assertEquals(TransportConnectionState.CONNECTED, viewModel.uiState.value.transportState)
        assertEquals(peer, viewModel.uiState.value.connectedPeer)

        viewModel.onDisconnectTransport()
        testScheduler.runCurrent()
        assertEquals(TransportConnectionState.DISCONNECTED, viewModel.uiState.value.transportState)
        assertNull(viewModel.uiState.value.connectedPeer)
    }

    @Test
    fun testEmergencyModeFlagsOutgoingMessageAsAlert() = runTest {
        val transport = FakeTransportEngine(initialState = TransportConnectionState.CONNECTED)
        val viewModel = createViewModel(transportEngine = transport)

        viewModel.onToggleEmergencyMode()
        assertTrue(viewModel.uiState.value.isEmergencyMode)

        executePttHold(viewModel)
        advanceUntilIdle()

        assertEquals(1, transport.sentMessages.size)
        val sent = transport.sentMessages.first()
        assertEquals(TransportMessageType.ALERT, sent.type)
        assertEquals(1, sent.priority)
        // The local synthesis of an armed transmission must also use the alarm route.
        assertEquals(true, fakePipeline.lastSynthesisWasEmergency)
    }

    @Test
    fun testNonEmergencyTransmissionIsPlainTranslation() = runTest {
        val transport = FakeTransportEngine(initialState = TransportConnectionState.CONNECTED)
        val viewModel = createViewModel(transportEngine = transport)

        executePttHold(viewModel)
        advanceUntilIdle()

        assertEquals(1, transport.sentMessages.size)
        assertEquals(TransportMessageType.TRANSLATION, transport.sentMessages.first().type)
        assertEquals(0, transport.sentMessages.first().priority)
        assertEquals(false, fakePipeline.lastSynthesisWasEmergency)
    }

    @Test
    fun testIncomingAlertIsAnnouncedAsEmergency() = runTest {
        val transport = FakeTransportEngine(initialState = TransportConnectionState.CONNECTED)
        val viewModel = createViewModel(transportEngine = transport)

        advanceUntilIdle()
        transport.emit(
            TransportMessage(
                type = TransportMessageType.ALERT,
                sourceLanguage = "hi-IN",
                targetLanguage = "en-IN",
                text = "CYCLONE WARNING",
                priority = 1
            )
        )
        advanceUntilIdle()

        assertEquals("CYCLONE WARNING", viewModel.uiState.value.translatedText)
        assertEquals(true, fakePipeline.lastSynthesisWasEmergency)
    }

    @Test
    fun testPttPressIsRejectedWithoutMicrophonePermission() = runTest {
        val viewModel = createViewModel()
        viewModel.onPermissionResult(false)

        viewModel.onPttPress()
        advanceUntilIdle()

        // Must not enter RECORDING: constructing AudioRecord without the permission can
        // only fail, so the press is refused up front with an actionable message.
        assertEquals(PttState.IDLE, viewModel.uiState.value.state)
        assertFalse(viewModel.uiState.value.isHolding)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSetTransportEngineTwiceDoesNotDuplicateIncomingHandling() = runTest {
        val transport = FakeTransportEngine(initialState = TransportConnectionState.CONNECTED)
        val viewModel = createViewModel(transportEngine = transport)

        // Re-installing the same engine must not add a second set of collectors, which
        // previously made every received message be spoken twice.
        viewModel.setTransportEngine(transport)
        advanceUntilIdle()

        transport.emit(
            TransportMessage(
                sourceLanguage = "hi-IN",
                targetLanguage = "en-IN",
                text = "ONCE"
            )
        )
        advanceUntilIdle()

        assertEquals("ONCE", viewModel.uiState.value.translatedText)
        assertEquals(1, fakePipeline.synthesisCallCount)
    }

    @Test
    fun testIncomingRemoteMessageUpdatesUiAndTriggersTts() = runTest {
        val transport = FakeTransportEngine(initialState = TransportConnectionState.CONNECTED)
        val viewModel = createViewModel(transportEngine = transport)

        val incoming = TransportMessage(
            sourceLanguage = "hi-IN",
            targetLanguage = "en-IN",
            text = "Emergency alert: evacuate coastal sector",
            type = TransportMessageType.ALERT
        )

        advanceUntilIdle()
        transport.incomingChannel.emit(incoming)
        testScheduler.runCurrent()
        advanceUntilIdle()

        assertEquals("Emergency alert: evacuate coastal sector", viewModel.uiState.value.translatedText)
        assertTrue(viewModel.uiState.value.isRemoteMessage)
        assertEquals(Language.ENGLISH, viewModel.uiState.value.targetLanguage)
    }
}

