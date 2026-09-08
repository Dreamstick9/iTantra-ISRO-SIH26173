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
 * Engine selection from configuration.
 *
 * Guards the case that stranded operators in the field: a phone with a recognition
 * service but no offline language pack for the chosen language. The offline engine
 * reports itself available there, so selection must not consult that claim.
 */
class ConfigurableSpeechPipelineTest {

    private class Stub(
        override val displayName: String,
        override val mode: PipelineMode,
        override val capturesOwnAudio: Boolean,
        private val available: Boolean = true
    ) : SpeechPipeline {
        private val _amp = MutableStateFlow(0f)
        override val amplitude: StateFlow<Float> = _amp
        var prepareCount = 0
        var releaseCount = 0
        var beginCaptureCount = 0

        override suspend fun prepare() { prepareCount++ }
        override suspend fun isAvailable() = available
        override fun beginCapture(source: Language) { beginCaptureCount++ }
        override suspend fun transcribe(wavData: ByteArray, source: Language) =
            Result.success(TranscriptionResult(displayName))
        override suspend fun translate(text: String, source: Language, target: Language) =
            Result.success(TranslationResult(displayName))
        override suspend fun synthesize(text: String, target: Language, isEmergency: Boolean) =
            Result.success(SynthesisResult(ByteArray(0)))
        override fun release() { releaseCount++ }
    }

    // The offline engine claims availability even with no language pack installed.
    private fun offline() = Stub("On-device", PipelineMode.ON_DEVICE, capturesOwnAudio = true)
    private fun cloud() = Stub("Sarvam Cloud", PipelineMode.CLOUD, capturesOwnAudio = false)

    private fun pipeline(key: String, forceOffline: Boolean = false, od: Stub = offline(), cl: Stub = cloud()) =
        ConfigurableSpeechPipeline(
            onDevice = od,
            cloudEngines = listOf(
                ConfigurableSpeechPipeline.CloudEngine(
                    pipeline = cl,
                    hasUsableKey = { !com.itantra.voice.network.SarvamApiClient.isPlaceholderKey(key) }
                )
            ),
            preferOffline = { forceOffline }
        )

    @Test
    fun `a usable key selects the cloud engine`() = runTest {
        val p = pipeline(key = "sk_live_real_key")
        p.prepare()

        assertEquals("Sarvam Cloud", p.displayName)
        assertEquals(PipelineMode.CLOUD, p.mode)
        // The caller must run AudioRecorder: the cloud engine needs a recorded WAV.
        assertFalse(p.capturesOwnAudio)
    }

    @Test
    fun `no key selects the offline engine`() = runTest {
        val p = pipeline(key = "")
        p.prepare()

        assertEquals("On-device", p.displayName)
        assertTrue(p.capturesOwnAudio)
    }

    @Test
    fun `a placeholder key is not treated as configured`() = runTest {
        val p = pipeline(key = "YOUR_API_KEY_HERE")
        p.prepare()
        assertEquals("On-device", p.displayName)
    }

    @Test
    fun `force offline beats a configured key`() = runTest {
        val p = pipeline(key = "sk_live_real_key", forceOffline = true)
        p.prepare()

        assertEquals("On-device", p.displayName)
        assertEquals(PipelineMode.ON_DEVICE, p.mode)
    }

    @Test
    fun `configuration is re-read on every prepare so a key entered at runtime takes effect`() = runTest {
        var key = ""
        val p = ConfigurableSpeechPipeline(
            onDevice = offline(),
            cloudEngines = listOf(
                ConfigurableSpeechPipeline.CloudEngine(
                    pipeline = cloud(),
                    hasUsableKey = { !com.itantra.voice.network.SarvamApiClient.isPlaceholderKey(key) }
                )
            ),
            preferOffline = { false }
        )

        p.prepare()
        assertEquals("On-device", p.displayName)

        // Operator pastes a key into the in-app settings sheet.
        key = "sk_live_pasted_at_runtime"
        p.prepare()

        assertEquals("Sarvam Cloud", p.displayName)
        assertFalse("capture must switch to AudioRecorder too", p.capturesOwnAudio)
    }

    @Test
    fun `capture is forwarded only to the resolved engine`() = runTest {
        val od = offline()
        val cl = cloud()
        val p = pipeline(key = "sk_live_real_key", od = od, cl = cl)
        p.prepare()

        p.beginCapture(Language.HINDI)

        assertEquals(0, od.beginCaptureCount)
        assertEquals(1, cl.beginCaptureCount)
    }

    @Test
    fun `the first configured cloud engine wins`() = runTest {
        val eleven = Stub("ElevenLabs", PipelineMode.CLOUD, capturesOwnAudio = false)
        val sarvam = Stub("Sarvam Cloud", PipelineMode.CLOUD, capturesOwnAudio = false)

        val p = ConfigurableSpeechPipeline(
            onDevice = offline(),
            cloudEngines = listOf(
                ConfigurableSpeechPipeline.CloudEngine(eleven) { true },
                ConfigurableSpeechPipeline.CloudEngine(sarvam) { true }
            ),
            preferOffline = { false }
        )
        p.prepare()
        assertEquals("ElevenLabs", p.displayName)
    }

    @Test
    fun `an unconfigured engine is skipped for the next one`() = runTest {
        val eleven = Stub("ElevenLabs", PipelineMode.CLOUD, capturesOwnAudio = false)
        val sarvam = Stub("Sarvam Cloud", PipelineMode.CLOUD, capturesOwnAudio = false)

        val p = ConfigurableSpeechPipeline(
            onDevice = offline(),
            cloudEngines = listOf(
                ConfigurableSpeechPipeline.CloudEngine(eleven) { false },
                ConfigurableSpeechPipeline.CloudEngine(sarvam) { true }
            ),
            preferOffline = { false }
        )
        p.prepare()
        assertEquals("Sarvam Cloud", p.displayName)
    }

    @Test
    fun `no configured cloud engine falls back to offline`() = runTest {
        val p = ConfigurableSpeechPipeline(
            onDevice = offline(),
            cloudEngines = listOf(
                ConfigurableSpeechPipeline.CloudEngine(cloud()) { false }
            ),
            preferOffline = { false }
        )
        p.prepare()
        assertEquals("On-device", p.displayName)
    }

    @Test
    fun `release tears down both engines regardless of which is active`() = runTest {
        val od = offline()
        val cl = cloud()
        pipeline(key = "", od = od, cl = cl).release()

        assertEquals(1, od.releaseCount)
        assertEquals(1, cl.releaseCount)
    }
}
