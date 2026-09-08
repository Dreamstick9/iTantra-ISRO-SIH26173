package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers engine selection, which is the part of the pipeline layer that can strand an
 * utterance: the on-device engine captures through its own recogniser session while the
 * cloud engine expects a recorded WAV, so the choice has to be settled before capture.
 */
class FallbackSpeechPipelineTest {

    private class StubPipeline(
        override val displayName: String,
        override val mode: PipelineMode,
        private var available: Boolean,
        override val capturesOwnAudio: Boolean = false
    ) : SpeechPipeline {

        private val _amplitude = MutableStateFlow(0f)
        override val amplitude: StateFlow<Float> = _amplitude

        var beginCaptureCount = 0
        var cancelCaptureCount = 0
        var prepareCount = 0
        var releaseCount = 0

        fun setAvailable(value: Boolean) { available = value }

        override suspend fun prepare() { prepareCount++ }
        override suspend fun isAvailable() = available
        override fun beginCapture(source: Language) { beginCaptureCount++ }
        override fun cancelCapture() { cancelCaptureCount++ }

        override suspend fun transcribe(wavData: ByteArray, source: Language) =
            Result.success(TranscriptionResult("$displayName-transcript"))

        override suspend fun translate(text: String, source: Language, target: Language) =
            Result.success(TranslationResult("$displayName-translation"))

        override suspend fun synthesize(text: String, target: Language, isEmergency: Boolean) =
            Result.success(SynthesisResult(wavBytes = displayName.toByteArray()))

        override fun release() { releaseCount++ }
    }

    private fun onDevice(available: Boolean) =
        StubPipeline("On-device", PipelineMode.ON_DEVICE, available, capturesOwnAudio = true)

    private fun cloud(available: Boolean) =
        StubPipeline("Sarvam Cloud", PipelineMode.CLOUD, available, capturesOwnAudio = false)

    @Test
    fun `prefers the offline engine when it is available`() = runTest {
        val pipeline = FallbackSpeechPipeline(onDevice(true), cloud(true))
        pipeline.prepare()

        assertEquals("On-device", pipeline.displayName)
        assertEquals(PipelineMode.ON_DEVICE, pipeline.mode)
        assertEquals(
            "On-device-transcript",
            pipeline.transcribe(ByteArray(0), Language.HINDI).getOrThrow().transcript
        )
    }

    @Test
    fun `falls back to the cloud engine only when offline is unavailable`() = runTest {
        val pipeline = FallbackSpeechPipeline(onDevice(false), cloud(true))
        pipeline.prepare()

        assertEquals("Sarvam Cloud", pipeline.displayName)
        assertEquals(
            "Sarvam Cloud-transcript",
            pipeline.transcribe(ByteArray(0), Language.HINDI).getOrThrow().transcript
        )
    }

    @Test
    fun `microphone ownership follows the resolved engine, not the preferred one`() = runTest {
        val pipeline = FallbackSpeechPipeline(onDevice(false), cloud(true))

        // Before resolution the composite still reports the preferred engine.
        assertTrue(pipeline.capturesOwnAudio)

        pipeline.prepare()

        // After resolving to the cloud engine the caller must run its own recorder,
        // otherwise the utterance would be captured by nobody.
        assertFalse(pipeline.capturesOwnAudio)
    }

    @Test
    fun `capture is forwarded to the resolved engine only`() = runTest {
        val offline = onDevice(true)
        val remote = cloud(true)
        val pipeline = FallbackSpeechPipeline(offline, remote)
        pipeline.prepare()

        pipeline.beginCapture(Language.TAMIL)
        pipeline.cancelCapture()

        assertEquals(1, offline.beginCaptureCount)
        assertEquals(1, offline.cancelCaptureCount)
        assertEquals(0, remote.beginCaptureCount)
        assertEquals(0, remote.cancelCaptureCount)
    }

    @Test
    fun `re-preparing switches engines when availability changes`() = runTest {
        val offline = onDevice(true)
        val pipeline = FallbackSpeechPipeline(offline, cloud(true))
        pipeline.prepare()
        assertEquals("On-device", pipeline.displayName)

        // e.g. the operator removed the offline voice pack.
        offline.setAvailable(false)
        pipeline.prepare()

        assertEquals("Sarvam Cloud", pipeline.displayName)
    }

    @Test
    fun `composite is available when either engine is`() = runTest {
        assertTrue(FallbackSpeechPipeline(onDevice(false), cloud(true)).isAvailable())
        assertTrue(FallbackSpeechPipeline(onDevice(true), cloud(false)).isAvailable())
        assertFalse(FallbackSpeechPipeline(onDevice(false), cloud(false)).isAvailable())
    }

    @Test
    fun `release tears down both engines regardless of which is active`() = runTest {
        val offline = onDevice(true)
        val remote = cloud(true)
        FallbackSpeechPipeline(offline, remote).release()

        assertEquals(1, offline.releaseCount)
        assertEquals(1, remote.releaseCount)
    }
}
