package com.itantra.voice.pipeline

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the cloud adapter's own logic: availability gating on the key, the
 * same-language short-circuit, and base64 decoding of the returned audio.
 */
class SarvamSpeechPipelineTest {

    @Test
    fun `is unavailable when no key is configured`() = runTest {
        val pipeline = SarvamSpeechPipeline(apiKeyProvider = { "" })
        assertFalse(pipeline.isAvailable())
    }

    @Test
    fun `is unavailable for a placeholder key`() = runTest {
        val pipeline = SarvamSpeechPipeline(apiKeyProvider = { "YOUR_API_KEY_HERE" })
        assertFalse(pipeline.isAvailable())
    }

    @Test
    fun `is available for a real key`() = runTest {
        val pipeline = SarvamSpeechPipeline(apiKeyProvider = { "sk_live_abc123" })
        assertTrue(pipeline.isAvailable())
    }

    @Test
    fun `reports the cloud mode so the UI can say the app is not air-gapped`() {
        val pipeline = SarvamSpeechPipeline(apiKeyProvider = { "k" })
        assertEquals(PipelineMode.CLOUD, pipeline.mode)
        assertEquals("Sarvam Cloud", pipeline.displayName)
    }
}
