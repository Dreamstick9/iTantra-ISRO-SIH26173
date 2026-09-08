package com.itantra.voice.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.media.AudioRecord
import com.itantra.voice.audio.AudioPlayer
import com.itantra.voice.audio.AudioRecorder
import com.itantra.voice.audio.NativeAudioRecord
import com.itantra.voice.audio.NativeMediaPlayer
import com.itantra.voice.audio.WavEncoder
import com.itantra.voice.data.Language
import com.itantra.voice.location.GeoPoint
import com.itantra.voice.location.LocationProvider
import com.itantra.voice.ui.components.LOCATION_TOGGLE_TAG
import com.itantra.voice.ui.components.SENDER_LOCATION_TAG
import kotlinx.coroutines.runBlocking
import com.itantra.voice.pipeline.PipelineMode
import com.itantra.voice.pipeline.SpeechPipeline
import com.itantra.voice.pipeline.SynthesisResult
import com.itantra.voice.pipeline.TranscriptionResult
import com.itantra.voice.pipeline.TranslationResult
import com.itantra.voice.transport.DiscoveredPeer
import com.itantra.voice.transport.TransportConnectionState
import com.itantra.voice.transport.TransportEngine
import com.itantra.voice.transport.TransportMessage
import com.itantra.voice.transport.TransportType
import com.itantra.voice.ui.components.EMERGENCY_TOGGLE_TAG
import com.itantra.voice.ui.components.NOTICE_BAR_TAG
import com.itantra.voice.ui.components.TALK_BUTTON_TAG
import com.itantra.voice.ui.theme.ITantraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device UI tests for the redesigned single screen.
 *
 * These drive the real Compose tree on an emulator with fake engines behind it, so they
 * exercise layout, gestures and state rendering without needing a microphone, a speech
 * engine, or a second handset.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakePipeline(
        var transcript: String = "नमस्ते दुनिया",
        var translation: String = "Hello world"
    ) : SpeechPipeline {
        override val mode = PipelineMode.ON_DEVICE
        override val displayName = "On-device"
        var lastEmergency: Boolean? = null

        override suspend fun isAvailable() = true
        override suspend fun transcribe(wavData: ByteArray, source: Language) =
            Result.success(TranscriptionResult(transcript))

        override suspend fun translate(text: String, source: Language, target: Language) =
            Result.success(TranslationResult(translation))

        override suspend fun synthesize(text: String, target: Language, isEmergency: Boolean):
            Result<SynthesisResult> {
            lastEmergency = isEmergency
            return Result.success(SynthesisResult(WavEncoder.encode(ByteArray(1600))))
        }

        override fun release() = Unit
    }

    private class FakeTransport(
        initial: TransportConnectionState = TransportConnectionState.DISCONNECTED
    ) : TransportEngine {
        override val transportType = TransportType.WIFI_DIRECT
        private val _state = MutableStateFlow(initial)
        override val connectionState = _state.asStateFlow()
        private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
        override val discoveredPeers = _peers.asStateFlow()
        private val _peer = MutableStateFlow<DiscoveredPeer?>(null)
        override val connectedPeer = _peer.asStateFlow()
        private val channel = MutableSharedFlow<TransportMessage>(extraBufferCapacity = 8)
        override val incomingMessages: Flow<TransportMessage> = channel.asSharedFlow()

        val sent = mutableListOf<TransportMessage>()

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
            sent.add(message)
            return Result.success(Unit)
        }

        suspend fun deliver(message: TransportMessage) = channel.emit(message)
        override fun release() = disconnect()
    }

    /**
     * AudioRecord stand-in. The test emulator runs headless with `-no-audio`, so a real
     * AudioRecord cannot be constructed; this supplies deterministic PCM instead.
     */
    private class FakeRecord : NativeAudioRecord {
        override val state = AudioRecord.STATE_INITIALIZED
        override var recordingState = AudioRecord.RECORDSTATE_STOPPED
        private val payload = ByteArray(3200) { (it % 97).toByte() }
        private var offset = 0

        override fun startRecording() {
            recordingState = AudioRecord.RECORDSTATE_RECORDING
            offset = 0
        }

        override fun stop() {
            recordingState = AudioRecord.RECORDSTATE_STOPPED
        }

        override fun release() = Unit

        override fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) return -1
            if (offset >= payload.size) return 0
            val n = minOf(sizeInBytes, payload.size - offset)
            System.arraycopy(payload, offset, audioData, offsetInBytes, n)
            offset += n
            return n
        }
    }

    /** MediaPlayer stand-in: the emulator has no audio output under test. */
    private class SilentPlayer : NativeMediaPlayer {
        override var onCompletionListener: (() -> Unit)? = null
        override var onErrorListener: ((Int, Int) -> Boolean)? = null
        override fun setAudioAttributes(contentType: Int, usage: Int) = Unit
        override fun setDataSource(path: String) = Unit
        override fun prepare() = Unit
        override fun start() = Unit
        override fun stop() = Unit
        override fun reset() = Unit
        override fun release() = Unit
        override val isPlaying = false
    }

    private lateinit var pipeline: FakePipeline
    private lateinit var transport: FakeTransport

    private fun launchScreen(
        transportEngine: FakeTransport = FakeTransport(),
        micGranted: Boolean = true
    ): MainViewModel {
        pipeline = FakePipeline()
        transport = transportEngine

        val cacheDir = File(
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().targetContext.cacheDir,
            "ui_test"
        ).apply { mkdirs() }

        val viewModel = MainViewModel(
            audioRecorder = AudioRecorder(recordProvider = { _, _, _, _, _ -> FakeRecord() }),
            speechPipeline = pipeline,
            audioPlayer = AudioPlayer(cacheDir, playerFactory = { SilentPlayer() }),
            transportEngine = transportEngine
        )
        viewModel.onPermissionResult(micGranted)

        composeRule.setContent {
            ITantraTheme { MainScreen(viewModel = viewModel) }
        }
        return viewModel
    }

    @Test
    fun restingScreenShowsTalkButtonAndDefaultLanguages() {
        launchScreen()

        composeRule.onNodeWithTag(TALK_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("HOLD TO TALK").assertIsDisplayed()
        // Defaults are Hindi -> English, rendered in their native scripts.
        composeRule.onNodeWithText(Language.DEFAULT_SOURCE.nativeName).assertIsDisplayed()
        composeRule.onNodeWithText(Language.DEFAULT_TARGET.nativeName).assertIsDisplayed()
        composeRule.onNodeWithText("NO LINK · TAP TO PAIR").assertIsDisplayed()
    }

    @Test
    fun swappingLanguagesExchangesSourceAndTarget() {
        val viewModel = launchScreen()

        composeRule.onNodeWithContentDescription("Swap languages").performClick()
        composeRule.waitForIdle()

        assertEquals(Language.DEFAULT_TARGET, viewModel.uiState.value.sourceLanguage)
        assertEquals(Language.DEFAULT_SOURCE, viewModel.uiState.value.targetLanguage)
    }

    @Test
    fun holdingTalkButtonRunsPipelineAndRendersTranscript() {
        val viewModel = launchScreen()

        // A press must exceed the 300 ms accidental-tap threshold to be accepted.
        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()
        assertEquals(PttState.RECORDING, viewModel.uiState.value.state)

        Thread.sleep(400)
        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { up() }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.translatedText.isNotBlank()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Hello world").assertIsDisplayed()
    }

    @Test
    fun shortTapIsRejectedAsAccidental() {
        val viewModel = launchScreen()

        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { down(center) }
        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { up() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.errorMessage != null
        }

        composeRule.onNodeWithTag(NOTICE_BAR_TAG).assertIsDisplayed()
        assertEquals(PttState.IDLE, viewModel.uiState.value.state)
    }

    @Test
    fun emergencyToggleArmsAlertMode() {
        val viewModel = launchScreen()

        composeRule.onNodeWithTag(EMERGENCY_TOGGLE_TAG).performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.isEmergencyMode)
        composeRule.onNodeWithText("EMERGENCY ARMED").assertIsDisplayed()
    }

    @Test
    fun connectedLinkIsShownInStatusBar() {
        val engine = FakeTransport(TransportConnectionState.CONNECTED)
        launchScreen(transportEngine = engine)
        engine.connect(DiscoveredPeer("id", "Phone-B", "02:00:00:00:00:00"))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("LINKED · Phone-B")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("LINKED · Phone-B").assertIsDisplayed()
    }

    @Test
    fun missingMicrophonePermissionBlocksTransmission() {
        val viewModel = launchScreen(micGranted = false)

        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { down(center) }
        composeRule.waitForIdle()

        // The press must not start a capture without the permission.
        assertEquals(PttState.IDLE, viewModel.uiState.value.state)
        composeRule.onNodeWithTag(NOTICE_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun receivedMessageShowsSenderPositionAndOfflineBearing() {
        val engine = FakeTransport(TransportConnectionState.CONNECTED)
        val viewModel = launchScreen(transportEngine = engine)

        // This handset is in Mumbai; the sender is ~1 km away.
        val here = GeoPoint(19.0759837, 72.8776559)
        val there = GeoPoint(19.0849837, 72.8776559)
        viewModel.setLocationProvider(FixedLocationProvider(here))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.ownLocation != null
        }

        runBlocking {
            engine.deliver(
                TransportMessage.withLocation(
                    TransportMessage(
                        sourceLanguage = "hi-IN",
                        targetLanguage = "en-IN",
                        text = "Need water"
                    ),
                    there
                )
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.senderLocation != null
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SENDER_LOCATION_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(there.format()).assertIsDisplayed()

        // Distance and bearing are computed on-device from the two fixes, with no map
        // data and no network. 0.009 degrees of latitude is very close to 1 km due north.
        val bearing = viewModel.uiState.value.bearingToSender
        assertNotNull(bearing)
        assertTrue("expected a northerly bearing, got $bearing", bearing!!.endsWith("N"))
        assertTrue("expected ~1 km, got $bearing", bearing.startsWith("1.0 km"))
    }

    @Test
    fun transmissionCarriesThisHandsetsPositionWhenSharingIsOn() {
        val engine = FakeTransport(TransportConnectionState.CONNECTED)
        val viewModel = launchScreen(transportEngine = engine)

        val here = GeoPoint(19.0759837, 72.8776559)
        viewModel.setLocationProvider(FixedLocationProvider(here))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.ownLocation != null
        }

        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { down(center) }
        Thread.sleep(400)
        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { up() }

        composeRule.waitUntil(timeoutMillis = 10_000) { engine.sent.isNotEmpty() }

        val sent = engine.sent.first()
        assertTrue("transmission must carry a position", sent.hasLocation)
        assertEquals(here.latitude, sent.senderLocation()!!.latitude, 0.00001)
        assertEquals(here.longitude, sent.senderLocation()!!.longitude, 0.00001)
    }

    @Test
    fun turningLocationSharingOffStripsThePositionFromTransmissions() {
        val engine = FakeTransport(TransportConnectionState.CONNECTED)
        val viewModel = launchScreen(transportEngine = engine)
        viewModel.setLocationProvider(FixedLocationProvider(GeoPoint(19.0759837, 72.8776559)))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.ownLocation != null
        }

        composeRule.onNodeWithTag(LOCATION_TOGGLE_TAG).performClick()
        composeRule.waitForIdle()
        assertFalse(viewModel.uiState.value.locationSharingEnabled)

        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { down(center) }
        Thread.sleep(400)
        composeRule.onNodeWithTag(TALK_BUTTON_TAG).performTouchInput { up() }

        composeRule.waitUntil(timeoutMillis = 10_000) { engine.sent.isNotEmpty() }
        assertFalse(
            "position must not be transmitted once sharing is off",
            engine.sent.first().hasLocation
        )
    }

    /** Location source returning a single fixed position, for deterministic tests. */
    private class FixedLocationProvider(fix: GeoPoint) : LocationProvider {
        override val currentFix = MutableStateFlow<GeoPoint?>(fix)
        override fun isAvailable() = true
        override fun start() = Unit
        override fun stop() = Unit
    }
}
