package com.itantra.voice.network

import com.itantra.voice.fixtures.SarvamMockFixtures
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SarvamMockContractTest {

    private fun createTestClient(interceptor: SarvamMockFixtures.MockSarvamInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // Tier 1 Tests for F4 (Saaras v3 STT Client Contract)

    @Test
    fun testSttMultipartRequestDispatchesToSpeechToTextEndpoint() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor()
        val client = createTestClient(interceptor)

        val dummyWav = SarvamMockFixtures.createCanonicalWav(ByteArray(3200))
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "saaras:v3")
            .addFormDataPart("language_code", "hi-IN")
            .addFormDataPart("mode", "transcribe")
            .addFormDataPart("file", "audio.wav", dummyWav.toRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://api.sarvam.ai/speech-to-text")
            .header("api-subscription-key", "test-key-12345")
            .post(multipartBody)
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        val responseJson = response.body?.string()
        assertNotNull(responseJson)
        assertTrue("Response must contain transcript", responseJson!!.contains("transcript"))
        assertTrue("Response must contain language_code", responseJson.contains("hi-IN"))

        val captured = interceptor.capturedRequests.first()
        assertEquals("test-key-12345", captured.header("api-subscription-key"))
        assertEquals("POST", captured.method)
    }

    // Tier 1 Tests for F5 (Mayura v1 Translation Contract)

    @Test
    fun testTranslateRequestDispatchesJsonToTranslateEndpoint() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor()
        val client = createTestClient(interceptor)

        val jsonPayload = """
        {
            "input": "नमस्ते दुनिया",
            "source_language_code": "hi-IN",
            "target_language_code": "en-IN",
            "model": "mayura:v1",
            "mode": "formal"
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.sarvam.ai/translate")
            .header("api-subscription-key", "test-key-12345")
            .header("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        val responseJson = response.body?.string()
        assertNotNull(responseJson)
        assertTrue("Response must contain translated_text", responseJson!!.contains("translated_text"))
    }

    // Tier 1 Tests for F6 (Bulbul v3 TTS Contract)

    @Test
    fun testTtsRequestDispatchesJsonToTextToSpeechEndpoint() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor()
        val client = createTestClient(interceptor)

        val jsonPayload = """
        {
            "inputs": ["Hello world"],
            "target_language_code": "en-IN",
            "speaker": "priya",
            "model": "bulbul:v3",
            "speech_sample_rate": 16000
        }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.sarvam.ai/text-to-speech")
            .header("api-subscription-key", "test-key-12345")
            .header("Content-Type", "application/json")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)

        val responseJson = response.body?.string()
        assertNotNull(responseJson)
        assertTrue("Response must contain audios array", responseJson!!.contains("audios"))
        assertTrue("Response must contain Base64 audio", responseJson.contains("UklGR"))
    }

    // Tier 2 Tests: Boundary & Error Handling Contracts

    @Test
    fun testHttp401UnauthorizedReturnsAuthenticationError() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttStatusCode = 401,
            sttResponseJson = SarvamMockFixtures.ERROR_401_UNAUTHORIZED
        )
        val client = createTestClient(interceptor)

        val request = Request.Builder()
            .url("https://api.sarvam.ai/speech-to-text")
            .header("api-subscription-key", "invalid-key")
            .post("dummy".toRequestBody())
            .build()

        val response = client.newCall(request).execute()
        assertEquals(401, response.code)
        val body = response.body?.string()
        assertTrue(body!!.contains("unauthorized"))
    }

    @Test
    fun testHttp429TooManyRequestsReturnsRateLimitError() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            translateStatusCode = 429,
            translateResponseJson = SarvamMockFixtures.ERROR_429_RATE_LIMIT
        )
        val client = createTestClient(interceptor)

        val request = Request.Builder()
            .url("https://api.sarvam.ai/translate")
            .header("api-subscription-key", "test-key")
            .post("dummy".toRequestBody())
            .build()

        val response = client.newCall(request).execute()
        assertEquals(429, response.code)
        val body = response.body?.string()
        assertTrue(body!!.contains("rate_limit_exceeded"))
    }

    @Test
    fun testHttp500ServerErrorReturnsInternalError() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            ttsStatusCode = 500,
            ttsResponseJson = SarvamMockFixtures.ERROR_500_SERVER_ERROR
        )
        val client = createTestClient(interceptor)

        val request = Request.Builder()
            .url("https://api.sarvam.ai/text-to-speech")
            .header("api-subscription-key", "test-key")
            .post("dummy".toRequestBody())
            .build()

        val response = client.newCall(request).execute()
        assertEquals(500, response.code)
        val body = response.body?.string()
        assertTrue(body!!.contains("internal_error"))
    }

    @Test
    fun testEmptyTranscriptReturns200OkWithEmptyString() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_EMPTY
        )
        val client = createTestClient(interceptor)

        val request = Request.Builder()
            .url("https://api.sarvam.ai/speech-to-text")
            .header("api-subscription-key", "test-key")
            .post("dummy".toRequestBody())
            .build()

        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
        val body = response.body?.string()
        assertTrue(body!!.contains("\"transcript\": \"\""))
    }
}
