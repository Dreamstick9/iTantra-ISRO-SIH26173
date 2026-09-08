package com.itantra.voice.network

import com.itantra.voice.data.Language
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request shape and error mapping for the ElevenLabs API.
 *
 * A wrong endpoint, header or model id fails identically to a network outage at runtime,
 * so the actual outgoing request is asserted rather than assumed.
 */
class ElevenLabsApiClientTest {

    /** Captures the outgoing request and returns a canned response. */
    private class CapturingInterceptor(
        private val status: Int = 200,
        private val body: String = "{}",
        private val contentType: String = "application/json",
        private val binary: ByteArray? = null
    ) : Interceptor {
        var lastRequest: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            lastRequest = request
            val responseBody = binary?.toResponseBody(contentType.toMediaType())
                ?: body.toResponseBody(contentType.toMediaType())
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(if (status == 200) "OK" else "Error")
                .body(responseBody)
                .build()
        }
    }

    private fun client(interceptor: CapturingInterceptor, key: String = "sk_test_key") =
        ElevenLabsApiClient(
            apiKeyProvider = { key },
            customClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )

    private fun wav(bytes: Int = 3200) = ByteArray(44 + bytes)

    // ─── Speech to text ──────────────────────────────────────────────────────

    @Test
    fun `transcribe posts multipart to the scribe endpoint with the auth header`() = runTest {
        val http = CapturingInterceptor(
            body = """{"text":"नमस्ते","language_code":"hi","language_probability":0.98}"""
        )
        val result = client(http).transcribe(wav(), "hi")

        assertTrue(result.isSuccess)
        assertEquals("नमस्ते", result.getOrThrow().text)

        val request = http.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals(
            "https://api.elevenlabs.io/v1/speech-to-text",
            request.url.toString()
        )
        assertEquals("sk_test_key", request.header("xi-api-key"))
        assertTrue(request.body!!.contentType().toString().startsWith("multipart/form-data"))
    }

    @Test
    fun `transcribe rejects empty audio before making a request`() = runTest {
        val http = CapturingInterceptor()
        val result = client(http).transcribe(ByteArray(0), "hi")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ElevenLabsException.InvalidRequestException)
        assertEquals("no request should be dispatched", null, http.lastRequest)
    }

    // ─── Text to speech ──────────────────────────────────────────────────────

    @Test
    fun `synthesize requests 16 kHz wav from the voice endpoint and returns raw audio`() = runTest {
        val audio = ByteArray(2048) { 3 }
        val http = CapturingInterceptor(contentType = "audio/wav", binary = audio)

        val result = client(http).synthesize("hello", "voice123", Language.HINDI)
        assertTrue(result.isSuccess)
        assertEquals(audio.size, result.getOrThrow().size)

        val url = http.lastRequest!!.url.toString()
        assertTrue("url was $url", url.startsWith("https://api.elevenlabs.io/v1/text-to-speech/voice123"))
        // wav_16000 matches the app's capture rate and arrives with a RIFF header, so it
        // needs no transcoding before playback.
        assertEquals("wav_16000", http.lastRequest!!.url.queryParameter("output_format"))
    }

    @Test
    fun `synthesize refuses without a voice`() = runTest {
        val http = CapturingInterceptor()
        val result = client(http).synthesize("hello", "", Language.HINDI)

        assertTrue(result.isFailure)
        assertEquals(null, http.lastRequest)
    }

    // ─── Model selection ─────────────────────────────────────────────────────

    @Test
    fun `latency-scored languages use the fast voice model`() {
        // SIH26173 scores latency at 20%, so Flash v2.5 (~75 ms) is used wherever it works.
        for (language in listOf(
            Language.ENGLISH, Language.HINDI, Language.BENGALI,
            Language.TAMIL, Language.TELUGU, Language.KANNADA, Language.MALAYALAM
        )) {
            assertEquals(
                "$language should use the fast model",
                ElevenLabsApiClient.TTS_MODEL_FAST,
                ElevenLabsApiClient.modelFor(language)
            )
        }
    }

    @Test
    fun `languages outside the fast model fall back to broad coverage`() {
        // Marathi, Gujarati and Odia are not on Flash v2.5; trading latency for coverage
        // beats failing the utterance.
        for (language in listOf(Language.MARATHI, Language.GUJARATI, Language.ODIA)) {
            assertEquals(
                "$language should fall back to the broad model",
                ElevenLabsApiClient.TTS_MODEL_BROAD,
                ElevenLabsApiClient.modelFor(language)
            )
        }
    }

    @Test
    fun `language codes are sent as ISO 639-1, not BCP-47`() {
        assertEquals("hi", ElevenLabsApiClient.isoCode(Language.HINDI))
        assertEquals("en", ElevenLabsApiClient.isoCode(Language.ENGLISH))
        assertEquals("ta", ElevenLabsApiClient.isoCode(Language.TAMIL))
        // Odia travels as "od-IN" to match Sarvam's tag, but ISO 639-1 assigns it "or".
        assertEquals("or", ElevenLabsApiClient.isoCode(Language.ODIA))
    }

    // ─── Error mapping ───────────────────────────────────────────────────────

    @Test
    fun `an exhausted credit balance is reported as a quota problem`() = runTest {
        val http = CapturingInterceptor(
            status = 402,
            body = """{"detail":{"status":"quota_exceeded","message":"Insufficient credits"}}"""
        )
        val error = client(http).transcribe(wav(), "hi").exceptionOrNull()

        assertTrue("was $error", error is ElevenLabsException.QuotaException)
        assertTrue(error!!.message!!.contains("Insufficient credits"))
    }

    @Test
    fun `a bad key is reported as authentication, not a generic failure`() = runTest {
        val http = CapturingInterceptor(status = 401, body = """{"detail":{"message":"Invalid API key"}}""")
        val error = client(http).transcribe(wav(), "hi").exceptionOrNull()
        assertTrue("was $error", error is ElevenLabsException.AuthenticationException)
    }

    @Test
    fun `an unsupported language is reported as a request problem`() = runTest {
        val http = CapturingInterceptor(
            status = 422,
            body = """{"detail":{"message":"Language not supported by this model"}}"""
        )
        val error = client(http).synthesize("x", "v1", Language.ODIA).exceptionOrNull()
        assertTrue("was $error", error is ElevenLabsException.InvalidRequestException)
    }

    @Test
    fun `rate limiting is distinguished from a server fault`() = runTest {
        val limited = client(CapturingInterceptor(status = 429)).transcribe(wav(), "hi").exceptionOrNull()
        assertTrue(limited is ElevenLabsException.RateLimitException)

        val faulted = client(CapturingInterceptor(status = 503)).transcribe(wav(), "hi").exceptionOrNull()
        assertTrue(faulted is ElevenLabsException.ServerException)
    }

    // ─── Key handling ────────────────────────────────────────────────────────

    @Test
    fun `blank and placeholder keys are not treated as configured`() {
        assertTrue(ElevenLabsApiClient.isPlaceholderKey(""))
        assertTrue(ElevenLabsApiClient.isPlaceholderKey("   "))
        assertTrue(ElevenLabsApiClient.isPlaceholderKey("YOUR_API_KEY_HERE"))
        assertFalse(ElevenLabsApiClient.isPlaceholderKey("sk_abc123"))
    }

    @Test
    fun `voices are listed so no voice id has to be hardcoded`() = runTest {
        val http = CapturingInterceptor(
            body = """{"voices":[{"voice_id":"v1","name":"Aria"},{"voice_id":"v2","name":"Roger"}]}"""
        )
        val voices = client(http).listVoices().getOrThrow()

        assertEquals(2, voices.size)
        assertEquals("v1", voices.first().voiceId)
        assertEquals("Aria", voices.first().name)
        assertEquals("https://api.elevenlabs.io/v2/voices", http.lastRequest!!.url.toString())
        assertNotNull(http.lastRequest!!.header("xi-api-key"))
    }
}
