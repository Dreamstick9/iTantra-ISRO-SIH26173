package com.example.itantra.ui

import com.example.itantra.audio.MockAudioEngine
import com.example.itantra.compression.MockCompressionEngine
import com.example.itantra.service.MockServiceController
import com.example.itantra.speech.MockSpeechEngine
import com.example.itantra.transport.MockTransportEngine
import com.example.itantra.tts.MockTtsEngine
import com.example.itantra.tts.TtsPlaybackState
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
class MainViewModelTtsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var ttsEngine: MockTtsEngine
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ttsEngine = MockTtsEngine(ioDispatcher = testDispatcher, simulatedLatencyMs = 10L)

        viewModel = MainViewModel(
            audioEngine = MockAudioEngine(),
            speechEngine = MockSpeechEngine(),
            ttsEngine = ttsEngine,
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
    fun testInitialTtsState() = runTest {
        advanceUntilIdle()
        val state = viewModel.appState.value
        assertEquals("Hello, this is iTantra.", state.ttsState.inputText)
        assertTrue(state.ttsState.isModelLoaded)
        assertEquals(TtsPlaybackState.IDLE, state.ttsState.playbackState)
        assertNull(state.ttsState.errorMessage)
    }

    @Test
    fun testOnTtsInputChanged() = runTest {
        advanceUntilIdle()
        val customText = "Cyclone alert: Evacuate coastal region."
        viewModel.onTtsInputChanged(customText)
        assertEquals(customText, viewModel.appState.value.ttsState.inputText)
    }

    @Test
    fun testOnSpeakTtsValidText() = runTest {
        advanceUntilIdle()
        viewModel.onSpeakTts("Hello, this is iTantra.")
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals("Offline speech playback completed.", state.statusMessage)
        assertNull(state.ttsState.errorMessage)
        assertTrue(state.ttsState.synthesisLatencyMs >= 0)
        assertTrue(state.ttsState.audioDurationMs > 0)
    }

    @Test
    fun testOnSpeakTtsEmptyText() = runTest {
        advanceUntilIdle()
        viewModel.onTtsInputChanged("   ")
        viewModel.onSpeakTts()
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals("Please enter text to synthesize", state.statusMessage)
        assertEquals("Text cannot be empty", state.ttsState.errorMessage)
    }

    @Test
    fun testOnStopTts() = runTest {
        advanceUntilIdle()
        viewModel.onStopTts()
        advanceUntilIdle()

        val state = viewModel.appState.value
        assertEquals("TTS playback stopped.", state.statusMessage)
        assertEquals(TtsPlaybackState.STOPPED, state.ttsState.playbackState)
    }
}
