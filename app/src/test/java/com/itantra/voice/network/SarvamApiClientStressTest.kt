package com.itantra.voice.network

import com.itantra.voice.data.Language
import com.itantra.voice.fixtures.SarvamMockFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Adversarial Empirical Stress Test Suite for SarvamApiClient & SarvamApiModels.
 *
 * Authored by: challenger_m1_2
 * Validates:
 * 1. STT multipart uploads across WAV boundaries and all 10 BCP-47 language codes
 * 2. Translation with special characters, Unicode emojis, numbers, and 10k+ character text
 * 3. TTS with various Indic scripts, speakers, and empty audio payloads
 * 4. Error mapping for HTTP 400, 401, 403, 404, 429, 500, 502, 503, 504
 * 5. Network fault simulation: SocketTimeoutException, UnknownHostException, ConnectException, IOException
 * 6. Corrupted JSON, HTML payloads, and empty bodies on HTTP 200
 * 7. API key placeholder validation and URL normalization
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SarvamApiClientStressTest {

    private fun createTestClient(interceptor: Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // =========================================================================
    // 1. STT Multipart Upload Stress & Boundary Tests
    // =========================================================================

    @Test
    fun testSttEmptyAudioFailsFastWithoutNetworkCall() = runTest {
        var callDispatched = false
        val interceptor = Interceptor { chain ->
            callDispatched = true
            chain.proceed(chain.request())
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.transcribe(ByteArray(0), "hi-IN")
        assertTrue("Empty audio must fail fast", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Must throw InvalidRequestException: $exception", exception is SarvamApiException.InvalidRequestException)
        assertEquals("WAV audio payload cannot be empty.", exception?.message)
        assertFalse("Network call must NOT be dispatched on empty input", callDispatched)
    }

    @Test
    fun testSttMinimal1SampleWavSucceeds() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_HINDI
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        // 1-sample mono 16-bit PCM = 2 bytes
        val minimalWav = SarvamMockFixtures.createCanonicalWav(ByteArray(2))
        assertEquals(46, minimalWav.size)

        val result = client.transcribe(minimalWav, "hi-IN")
        assertTrue("Minimal WAV should succeed", result.isSuccess)
        assertEquals(1, interceptor.capturedRequests.size)

        val request = interceptor.capturedRequests.first()
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("speech-to-text"))
    }

    @Test
    fun testSttLargeWavPayloadHandling() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_HINDI
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        // ~1 MB PCM (~32.7 seconds of 16 kHz 16-bit mono audio)
        val largePcm = ByteArray(1024 * 1024)
        val largeWav = SarvamMockFixtures.createCanonicalWav(largePcm)
        assertEquals(1024 * 1024 + 44, largeWav.size)

        val result = client.transcribe(largeWav, "hi-IN")
        assertTrue("Large WAV payload must succeed without crash", result.isSuccess)
        assertEquals(1, interceptor.capturedRequests.size)
    }

    @Test
    fun testSttOddByteLengthPcmWav() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_HINDI
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        // Odd length PCM buffer: 3333 bytes
        val oddWav = SarvamMockFixtures.createCanonicalWav(ByteArray(3333))
        assertEquals(3377, oddWav.size)

        val result = client.transcribe(oddWav, "hi-IN")
        assertTrue("Odd-byte PCM WAV must succeed", result.isSuccess)
    }

    @Test
    fun testSttAll10LanguageCodesDispatchedAccurately() = runTest {
        for (language in Language.entries) {
            val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
                sttResponseJson = """{"transcript": "Test speech for ${language.name}", "language_code": "${language.bcp47Code}"}"""
            )
            val client = SarvamApiClient(
                apiKeyProvider = { "test-api-key" },
                customClient = createTestClient(interceptor),
                ioDispatcher = StandardTestDispatcher(testScheduler)
            )

            val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(320))
            val result = client.transcribe(dummyWav, language.bcp47Code)

            assertTrue("Language ${language.name} (${language.bcp47Code}) must succeed", result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("Test speech for ${language.name}", response.transcript)
            assertEquals(language.bcp47Code, response.languageCode)
            assertEquals(1, interceptor.capturedRequests.size)
        }
    }

    @Test
    fun testSttEmptyTranscriptResponseParsedAsSuccess() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_EMPTY
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(320))
        val result = client.transcribe(dummyWav, "hi-IN")

        assertTrue("Empty transcript response must succeed with blank string", result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("", response.transcript)
        assertEquals("hi-IN", response.languageCode)
    }

    @Test
    fun testSttMissingLanguageCodeInResponseHandledSafely() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = """{"transcript": "Transcript without language code"}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(320))
        val result = client.transcribe(dummyWav, "hi-IN")

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("Transcript without language code", response.transcript)
        assertNull(response.languageCode)
    }

    // =========================================================================
    // 2. Translation Stress & Language Matrix Tests
    // =========================================================================

    @Test
    fun testTranslationSpecialCharactersAndEscaping() = runTest {
        val complexInput = "Special: \"quotes\", 'single', <brackets>, &amp;, /slash/, \\backslash\\, \nnewline, \ttab, %$#@!*^"
        val expectedOutput = "Translated: special content intact"

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateResponseJson = """{"translated_text": "$expectedOutput"}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate(complexInput, "en-IN", "hi-IN")
        assertTrue("Complex characters must be encoded and transmitted successfully", result.isSuccess)
        assertEquals(expectedOutput, result.getOrThrow().translatedText)
    }

    @Test
    fun testTranslationUnicodeEmojisAndMixedIndicScripts() = runTest {
        val emojiInput = "🚨 अलर्ट! Cyclone warning 🌪️🌊 safely evacuate! तुरंत सुरक्षित स्थान पर जाएं! বাংলা தமிழ்"
        val expectedOutput = "🚨 Alert! Cyclone warning 🌪️🌊 safely evacuate! तुरंत सुरक्षित स्थान पर जाएं!"

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateResponseJson = """{"translated_text": "$expectedOutput"}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate(emojiInput, "hi-IN", "en-IN")
        assertTrue("Multibyte emojis and scripts must succeed", result.isSuccess)
        assertEquals(expectedOutput, result.getOrThrow().translatedText)
    }

    @Test
    fun testTranslationNumbersAndCurrencies() = runTest {
        val input = "Account balance: ₹1,50,000.75 for ID #9988-ABC on 2026-09-07 15:30:00"
        val expected = "खाता शेष: ₹1,50,000.75"

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateResponseJson = """{"translated_text": "$expected"}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate(input, "en-IN", "hi-IN")
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrThrow().translatedText)
    }

    @Test
    fun testTranslationExtremelyLongText() = runTest {
        // Generate a 10,000+ character text payload
        val sentence = "Emergency communication network test across Indian coastal zones and regional stations. "
        val builder = StringBuilder()
        while (builder.length < 10000) {
            builder.append(sentence)
        }
        val longText = builder.toString()
        assertTrue(longText.length >= 10000)

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateResponseJson = """{"translated_text": "Translated long text successfully."}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate(longText, "en-IN", "hi-IN")
        assertTrue("10,000+ character text must translate without memory overflow", result.isSuccess)
        assertEquals("Translated long text successfully.", result.getOrThrow().translatedText)
    }

    @Test
    fun testTranslationBlankAndWhitespaceInputsFailFast() = runTest {
        var callDispatched = false
        val interceptor = Interceptor { chain ->
            callDispatched = true
            chain.proceed(chain.request())
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        // Test empty string
        val res1 = client.translate("", "en-IN", "hi-IN")
        assertTrue(res1.isFailure)
        assertTrue(res1.exceptionOrNull() is SarvamApiException.InvalidRequestException)

        // Test spaces only
        val res2 = client.translate("     ", "en-IN", "hi-IN")
        assertTrue(res2.isFailure)
        assertTrue(res2.exceptionOrNull() is SarvamApiException.InvalidRequestException)

        // Test mixed newlines and tabs
        val res3 = client.translate("\t\n\r  ", "en-IN", "hi-IN")
        assertTrue(res3.isFailure)
        assertTrue(res3.exceptionOrNull() is SarvamApiException.InvalidRequestException)

        assertFalse("No network requests should be triggered for blank text", callDispatched)
    }

    @Test
    fun testTranslationMilestoneLanguagePairs() = runTest {
        val testPairs = listOf(
            Triple(Language.HINDI, Language.ENGLISH, "Milestone 1: hi->en"),
            Triple(Language.ENGLISH, Language.MARATHI, "Milestone 2: en->mr"),
            Triple(Language.TAMIL, Language.HINDI, "Milestone 3: ta->hi"),
            Triple(Language.BENGALI, Language.ODIA, "Matrix: bn->od"),
            Triple(Language.GUJARATI, Language.TELUGU, "Matrix: gu->te"),
            Triple(Language.KANNADA, Language.MALAYALAM, "Matrix: kn->ml")
        )

        for ((src, tgt, description) in testPairs) {
            val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
                translateResponseJson = """{"translated_text": "Translation result for $description"}"""
            )
            val client = SarvamApiClient(
                apiKeyProvider = { "test-api-key" },
                customClient = createTestClient(interceptor),
                ioDispatcher = StandardTestDispatcher(testScheduler)
            )

            val result = client.translate("Input text", src.bcp47Code, tgt.bcp47Code)
            assertTrue("Translation $description must succeed", result.isSuccess)

            val request = interceptor.capturedRequests.first()
            assertEquals("POST", request.method)
            assertTrue(request.url.encodedPath.endsWith("translate"))
        }
    }

    // =========================================================================
    // 3. TTS (Text-to-Speech) Stress & Audio Payload Tests
    // =========================================================================

    @Test
    fun testTtsVariousSpeakersAndIndicScripts() = runTest {
        val testCases = listOf(
            Triple("तटीय क्षेत्र में भीषण चक्रवात", "hi-IN", "priya"),
            Triple("Evacuate sector four immediately", "en-IN", "aditya"),
            Triple("எனக்கு கடுமையான காய்ச்சல் உள்ளது", "ta-IN", "ratan"),
            Triple("પીવાના પાણીની તાત્કાલિક જરૂર છે", "gu-IN", "kavya")
        )

        for ((text, lang, speaker) in testCases) {
            val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
                ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS
            )
            val client = SarvamApiClient(
                apiKeyProvider = { "test-api-key" },
                customClient = createTestClient(interceptor),
                ioDispatcher = StandardTestDispatcher(testScheduler)
            )

            val result = client.synthesize(text, lang, speaker)
            assertTrue("TTS for $lang with speaker $speaker must succeed", result.isSuccess)
            val ttsResponse = result.getOrThrow()
            assertEquals(1, ttsResponse.audios.size)
            assertEquals(SarvamMockFixtures.MOCK_BASE64_WAV, ttsResponse.audios.first())
        }
    }

    @Test
    fun testTtsBlankAndWhitespaceInputsFailFast() = runTest {
        var callDispatched = false
        val interceptor = Interceptor { chain ->
            callDispatched = true
            chain.proceed(chain.request())
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val res1 = client.synthesize("", "en-IN")
        assertTrue(res1.isFailure)
        assertTrue(res1.exceptionOrNull() is SarvamApiException.InvalidRequestException)

        val res2 = client.synthesize("    \t\n", "en-IN")
        assertTrue(res2.isFailure)
        assertTrue(res2.exceptionOrNull() is SarvamApiException.InvalidRequestException)

        assertFalse("Network must not be called on blank TTS input", callDispatched)
    }

    @Test
    fun testTtsEmptyAudioArrayFailsWithEmptyResponseException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsResponseJson = """{"audios": []}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.synthesize("Hello", "en-IN")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Must be EmptyResponseException: $exception", exception is SarvamApiException.EmptyResponseException)
        assertEquals("TTS response contained empty audio array.", exception?.message)
    }

    @Test
    fun testTtsBlankAudioStringInAudiosArrayFailsWithEmptyResponseException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsResponseJson = """{"audios": ["   "]}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-api-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.synthesize("Hello", "en-IN")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Must be EmptyResponseException: $exception", exception is SarvamApiException.EmptyResponseException)
        assertEquals("TTS response contained empty audio array.", exception?.message)
    }

    // =========================================================================
    // 4. HTTP Status Codes & Error Mapping Tests (401, 403, 404, 429, 500, 502, 503)
    // =========================================================================

    @Test
    fun testHttp401WithServerErrorMessageMapsToAuthenticationException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttStatusCode = 401,
            sttResponseJson = """{"error": {"message": "Invalid API key provided", "code": "unauthorized"}}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(320))
        val result = client.transcribe(dummyWav, "hi-IN")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be AuthenticationException", ex is SarvamApiException.AuthenticationException)
        assertEquals("Invalid API key provided", ex?.message)
    }

    @Test
    fun testHttp401WithPlainTextBodyFallsBackToDefaultMessage() = runTest {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body("Plain text unauthorized".toResponseBody("text/plain".toMediaType()))
                .build()
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(320))
        val result = client.transcribe(dummyWav, "hi-IN")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be AuthenticationException", ex is SarvamApiException.AuthenticationException)
        assertEquals("Invalid API key. Please verify your Sarvam AI subscription key.", ex?.message)
    }

    @Test
    fun testHttp403ForbiddenMapsToForbiddenException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateStatusCode = 403,
            translateResponseJson = """{"error": {"message": "Account subscription expired", "code": "forbidden"}}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate("Hello", "en-IN", "hi-IN")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be ForbiddenException: $ex", ex is SarvamApiException.ForbiddenException)
        assertEquals("Account subscription expired", ex?.message)
    }

    @Test
    fun testHttp404NotFoundMapsToServerExceptionWithCode404() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsStatusCode = 404,
            ttsResponseJson = """{"error": {"message": "Endpoint not found", "code": "not_found"}}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.synthesize("Hello", "en-IN")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be ServerException", ex is SarvamApiException.ServerException)
        val serverEx = ex as SarvamApiException.ServerException
        assertEquals(404, serverEx.code)
        assertEquals("Endpoint not found", serverEx.message)
    }

    @Test
    fun testHttp429RateLimitMapsToRateLimitException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateStatusCode = 429,
            translateResponseJson = """{"error": {"message": "Quota limit reached. Try in 5 seconds.", "code": "rate_limit_exceeded"}}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate("Hello", "en-IN", "hi-IN")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be RateLimitException", ex is SarvamApiException.RateLimitException)
        assertEquals("Quota limit reached. Try in 5 seconds.", ex?.message)
    }

    @Test
    fun testHttp500ServerErrorMapsToServerException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsStatusCode = 500,
            ttsResponseJson = """{"error": {"message": "GPU compute cluster failure", "code": "internal_error"}}"""
        )
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.synthesize("Hello", "en-IN")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be ServerException", ex is SarvamApiException.ServerException)
        val serverEx = ex as SarvamApiException.ServerException
        assertEquals(500, serverEx.code)
        assertEquals("GPU compute cluster failure", serverEx.message)
    }

    @Test
    fun testHttp502And503And504GatewaysMapToServerException() = runTest {
        val codes = listOf(502, 503, 504)
        for (code in codes) {
            val interceptor = Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Gateway Error")
                    .body("""{"error": {"message": "Service unavailable with code $code"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val client = SarvamApiClient(
                apiKeyProvider = { "test-key" },
                customClient = createTestClient(interceptor),
                ioDispatcher = StandardTestDispatcher(testScheduler)
            )

            val result = client.translate("Test text", "en-IN", "hi-IN")
            assertTrue("Code $code must map to failure", result.isFailure)
            val ex = result.exceptionOrNull()
            assertTrue("Must be ServerException: $ex", ex is SarvamApiException.ServerException)
            assertEquals(code, (ex as SarvamApiException.ServerException).code)
            assertEquals("Service unavailable with code $code", ex.message)
        }
    }

    @Test
    fun testHttp400WithRootMessageFormat() = runTest {
        // FastAPI / Starlette sometimes returns {"message": "..."} at the root
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .body("""{"message": "Unsupported source language"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate("Hello", "xyz-IN", "hi-IN")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SarvamApiException.ServerException)
        assertEquals(400, (ex as SarvamApiException.ServerException).code)
        assertEquals("Unsupported source language", ex.message)
    }

    // =========================================================================
    // 5. Network Fault Injections (Timeouts, DNS, Connection Reset)
    // =========================================================================

    @Test
    fun testSocketTimeoutExceptionMapsToNetworkException() = runTest {
        val interceptor = Interceptor {
            throw SocketTimeoutException("Read timed out after 30000ms")
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(320))
        val result = client.transcribe(dummyWav, "hi-IN")

        assertTrue("Timeout must result in failure", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be NetworkException: $ex", ex is SarvamApiException.NetworkException)
        assertTrue("Message must mention timeout: ${ex?.message}", ex?.message?.contains("timed out", ignoreCase = true) == true)
        assertTrue("Cause must be SocketTimeoutException", ex?.cause is SocketTimeoutException)
    }

    @Test
    fun testUnknownHostExceptionMapsToNetworkException() = runTest {
        val interceptor = Interceptor {
            throw UnknownHostException("Unable to resolve host \"api.sarvam.ai\": No address associated with hostname")
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate("Hello", "en-IN", "hi-IN")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be NetworkException", ex is SarvamApiException.NetworkException)
        assertTrue("Message must mention no internet connection", ex?.message?.contains("No internet connection") == true)
        assertTrue(ex?.cause is UnknownHostException)
    }

    @Test
    fun testConnectExceptionMapsToNetworkException() = runTest {
        val interceptor = Interceptor {
            throw ConnectException("Failed to connect to api.sarvam.ai/127.0.0.1:443")
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.synthesize("Hello", "en-IN")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be NetworkException", ex is SarvamApiException.NetworkException)
        assertTrue("Message must mention no internet connection", ex?.message?.contains("No internet connection") == true)
        assertTrue(ex?.cause is ConnectException)
    }

    @Test
    fun testGeneralIOExceptionMapsToNetworkException() = runTest {
        val interceptor = Interceptor {
            throw IOException("Connection reset by peer")
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate("Hello", "en-IN", "hi-IN")

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be NetworkException", ex is SarvamApiException.NetworkException)
        assertTrue("Must include error message", ex?.message?.contains("Connection reset by peer") == true)
        assertTrue(ex?.cause is IOException)
    }

    // =========================================================================
    // 6. Corrupted Payloads, Empty 200 OK, HTML responses
    // =========================================================================

    @Test
    fun testCorruptedJsonOnHttp200ReturnsFailureSafely() = runTest {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{ "broken_json": [ incomplete""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate("Hello", "en-IN", "hi-IN")
        assertTrue("Corrupted JSON must be caught and returned as failure", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun testEmptyBodyOnHttp200ReturnsEmptyResponseException() = runTest {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.translate("Hello", "en-IN", "hi-IN")
        assertTrue("Empty body on 200 must fail", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must be EmptyResponseException: $ex", ex is SarvamApiException.EmptyResponseException)
    }

    @Test
    fun testHtmlBodyOnHttp200ReturnsFailureSafely() = runTest {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("<!DOCTYPE html><html><body>Attention Required | Cloudflare</body></html>".toResponseBody("text/html".toMediaType()))
                .build()
        }
        val client = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = client.synthesize("Hello", "en-IN")
        assertTrue("HTML payload must fail deserialization safely", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    // =========================================================================
    // 7. API Key Placeholder Validation & URL Normalization
    // =========================================================================

    @Test
    fun testAuthInterceptorThrowsAuthenticationExceptionOnPlaceholders() {
        val placeholders = listOf(
            "",
            "   ",
            "YOUR_API_KEY_HERE",
            "your_sarvam_api_key_here",
            "<SARVAM_API_KEY>",
            "placeholder",
            "  placeholder  ",
            "YOUR_api_key_HERE"
        )

        for (placeholder in placeholders) {
            val interceptor = SarvamApiClient.createAuthInterceptor { placeholder }
            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

            val request = Request.Builder().url("https://api.sarvam.ai/translate").build()

            try {
                client.newCall(request).execute()
                org.junit.Assert.fail("Expected AuthenticationException for placeholder '$placeholder'")
            } catch (e: Exception) {
                assertTrue("Expected AuthenticationException for '$placeholder', got $e", e is SarvamApiException.AuthenticationException)
                assertTrue(e.message!!.contains("SARVAM_API_KEY"))
            }
        }
    }

    @Test
    fun testBaseUrlNormalizationWithAndWithoutTrailingSlash() = runTest {
        // Test with trailing slash
        val interceptor1 = SarvamMockFixtures.MockSarvamInterceptor()
        val client1 = SarvamApiClient(
            apiKeyProvider = { "valid-key" },
            baseUrl = "https://custom.api.sarvam.ai/",
            customClient = createTestClient(interceptor1),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        client1.translate("Test", "en-IN", "hi-IN")
        assertEquals("https://custom.api.sarvam.ai/translate", interceptor1.capturedRequests.first().url.toString())

        // Test without trailing slash
        val interceptor2 = SarvamMockFixtures.MockSarvamInterceptor()
        val client2 = SarvamApiClient(
            apiKeyProvider = { "valid-key" },
            baseUrl = "https://custom.api.sarvam.ai",
            customClient = createTestClient(interceptor2),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        client2.translate("Test", "en-IN", "hi-IN")
        assertEquals("https://custom.api.sarvam.ai/translate", interceptor2.capturedRequests.first().url.toString())
    }
}
