package com.itantra.voice.network

import com.itantra.voice.fixtures.SarvamMockFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SarvamApiClientTest {

    private fun createTestClient(interceptor: SarvamMockFixtures.MockSarvamInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Tier 1 Tests: Feature Coverage for F4, F5, F6

    @Test
    fun testTranscribeDispatchesMultipartAndParsesResponse() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_HINDI
        )
        val testClient = createTestClient(interceptor)
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-api-key-999" },
            customClient = testClient,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val testWav = SarvamMockFixtures.createCanonicalWav(ByteArray(3200))
        val result = apiClient.transcribe(testWav, "hi-IN")

        assertTrue("Transcribe must succeed", result.isSuccess)
        val response = result.getOrThrow()
        assertTrue(response.transcript.contains("तटीय क्षेत्र में भीषण चक्रवात"))
        assertEquals("hi-IN", response.languageCode)

        assertEquals(1, interceptor.capturedRequests.size)
        val request = interceptor.capturedRequests.first()
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("speech-to-text"))
        assertEquals("test-api-key-999", request.header(SarvamApiClient.AUTH_HEADER))
    }

    @Test
    fun testTranslateDispatchesJsonAndParsesResponse() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_HI_TO_EN
        )
        val testClient = createTestClient(interceptor)
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-api-key-999" },
            customClient = testClient,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.translate(
            text = "तटीय क्षेत्र में भीषण चक्रवात",
            sourceLang = "hi-IN",
            targetLang = "en-IN"
        )

        assertTrue("Translate must succeed", result.isSuccess)
        val response = result.getOrThrow()
        assertTrue(response.translatedText.contains("warning of severe cyclone"))

        val request = interceptor.capturedRequests.first()
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("translate"))
        assertEquals("test-api-key-999", request.header(SarvamApiClient.AUTH_HEADER))
    }

    @Test
    fun testSynthesizeDispatchesJsonAndParsesResponse() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS
        )
        val testClient = createTestClient(interceptor)
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-api-key-999" },
            customClient = testClient,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.synthesize(
            text = "Coastal area warning",
            targetLang = "en-IN",
            speaker = "meera"
        )

        assertTrue("Synthesize must succeed", result.isSuccess)
        val response = result.getOrThrow()
        assertEquals(1, response.audios.size)
        assertEquals(SarvamMockFixtures.MOCK_BASE64_WAV, response.audios.first())

        val request = interceptor.capturedRequests.first()
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("text-to-speech"))
        assertEquals("test-api-key-999", request.header(SarvamApiClient.AUTH_HEADER))
    }

    // Tier 2 Tests: Boundary & Error Handling (401, 429, 500, Validation)

    @Test
    fun testHttp401UnauthorizedMapsToAuthenticationException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttStatusCode = 401,
            sttResponseJson = SarvamMockFixtures.ERROR_401_UNAUTHORIZED
        )
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "invalid-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(100))
        val result = apiClient.transcribe(dummyWav, "hi-IN")

        assertTrue("Expected failure on 401", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception must be AuthenticationException: $exception", exception is SarvamApiException.AuthenticationException)
        assertTrue(exception!!.message!!.contains("Invalid") || exception.message!!.contains("subscription key"))
    }

    @Test
    fun testHttp429RateLimitMapsToRateLimitException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateStatusCode = 429,
            translateResponseJson = SarvamMockFixtures.ERROR_429_RATE_LIMIT
        )
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.translate("test", "hi-IN", "en-IN")

        assertTrue("Expected failure on 429", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception must be RateLimitException: $exception", exception is SarvamApiException.RateLimitException)
        assertTrue(exception!!.message!!.contains("rate limit", ignoreCase = true))
    }

    @Test
    fun testHttp500ServerErrorMapsToServerException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsStatusCode = 500,
            ttsResponseJson = SarvamMockFixtures.ERROR_500_SERVER_ERROR
        )
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.synthesize("test text", "en-IN")

        assertTrue("Expected failure on 500", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception must be ServerException: $exception", exception is SarvamApiException.ServerException)
        assertEquals(500, (exception as SarvamApiException.ServerException).code)
    }

    @Test
    fun testEmptyAudioInTranscribeFailsFastWithInvalidRequestException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor()
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.transcribe(ByteArray(0), "hi-IN")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SarvamApiException.InvalidRequestException)
        assertEquals(0, interceptor.capturedRequests.size)
    }

    @Test
    fun testEmptyTextInTranslateFailsFastWithInvalidRequestException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor()
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.translate("", "hi-IN", "en-IN")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SarvamApiException.InvalidRequestException)
        assertEquals(0, interceptor.capturedRequests.size)
    }

    @Test
    fun testEmptyTextInSynthesizeFailsFastWithInvalidRequestException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor()
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.synthesize("   ", "en-IN")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SarvamApiException.InvalidRequestException)
        assertEquals(0, interceptor.capturedRequests.size)
    }

    @Test
    fun testTtsEmptyAudiosArrayReturnsEmptyResponseException() = runTest {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsResponseJson = """{"audios": []}"""
        )
        val apiClient = SarvamApiClient(
            apiKeyProvider = { "test-key" },
            customClient = createTestClient(interceptor),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = apiClient.synthesize("Hello", "en-IN")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SarvamApiException.EmptyResponseException)
    }

    @Test
    fun testPlaceholderApiKeyDetection() {
        assertTrue(SarvamApiClient.isPlaceholderKey(""))
        assertTrue(SarvamApiClient.isPlaceholderKey("   "))
        assertTrue(SarvamApiClient.isPlaceholderKey("YOUR_API_KEY_HERE"))
        assertTrue(SarvamApiClient.isPlaceholderKey("your_sarvam_api_key_here"))
        assertTrue(SarvamApiClient.isPlaceholderKey("<SARVAM_API_KEY>"))
        assertTrue(SarvamApiClient.isPlaceholderKey("placeholder"))
        assertFalse(SarvamApiClient.isPlaceholderKey("sk-live-12345"))
    }
}
