package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import com.itantra.voice.network.ElevenLabsApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the ElevenLabs-only setup.
 *
 * An operator with only an ElevenLabs key used to get "Sarvam API key is missing" and lose
 * the whole utterance — an error naming a provider they never chose, for a stage
 * ElevenLabs cannot serve at all. Relaying the words untranslated is the correct
 * behaviour: in a distress call, the wrong language beats silence.
 */
class TranslationFallbackTest {

    private class NeverCalledTranslator : Translator {
        override val displayName = "Should not be used"
        var translateCalls = 0
        override suspend fun isAvailable() = false
        override suspend fun translate(text: String, source: Language, target: Language): Result<String> {
            translateCalls++
            return Result.failure(IllegalStateException("Sarvam API key is missing."))
        }
    }

    private class WorkingTranslator : Translator {
        override val displayName = "Sarvam"
        override suspend fun isAvailable() = true
        override suspend fun translate(text: String, source: Language, target: Language) =
            Result.success("TRANSLATED:$text")
    }

    private fun pipeline(translator: Translator) = ElevenLabsSpeechPipeline(
        client = ElevenLabsApiClient(
            apiKeyProvider = { "sk_test" },
            customClient = OkHttpClient.Builder().addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            ).build()
        ),
        apiKeyProvider = { "sk_test" },
        translator = translator
    )

    @Test
    fun `an unconfigured translator is never called`() = runTest {
        val translator = NeverCalledTranslator()
        val result = pipeline(translator).translate("पानी चाहिए", Language.HINDI, Language.ENGLISH)

        assertTrue("must not fail the utterance", result.isSuccess)
        assertEquals(
            "no translator should ever be invoked when it reports unavailable",
            0,
            translator.translateCalls
        )
    }

    @Test
    fun `text is relayed unchanged when no translator is configured`() = runTest {
        val result = pipeline(NeverCalledTranslator())
            .translate("पानी चाहिए", Language.HINDI, Language.ENGLISH)
            .getOrThrow()

        assertEquals("पानी चाहिए", result.text)
        // Flagged so the UI can say "relayed, not translated" instead of claiming English.
        assertFalse(result.translated)
    }

    @Test
    fun `a configured translator is used and the result is marked translated`() = runTest {
        val result = pipeline(WorkingTranslator())
            .translate("पानी चाहिए", Language.HINDI, Language.ENGLISH)
            .getOrThrow()

        assertEquals("TRANSLATED:पानी चाहिए", result.text)
        assertTrue(result.translated)
    }

    @Test
    fun `same-language transmission skips translation entirely`() = runTest {
        val translator = NeverCalledTranslator()
        val result = pipeline(translator)
            .translate("hello", Language.ENGLISH, Language.ENGLISH)
            .getOrThrow()

        assertEquals("hello", result.text)
        assertTrue("same-language output is not an untranslated relay", result.translated)
        assertEquals(0, translator.translateCalls)
    }

    @Test
    fun `the default translator reports itself unconfigured`() = runTest {
        // PassThroughTranslator must not claim availability, or callers would treat a
        // verbatim relay as a real translation and mislabel the output language.
        assertFalse(PassThroughTranslator.isAvailable())
        assertEquals("x", PassThroughTranslator.translate("x", Language.HINDI, Language.ENGLISH).getOrThrow())
    }
}
