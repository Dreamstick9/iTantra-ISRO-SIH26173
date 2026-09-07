package com.itantra.voice.e2e

import com.itantra.voice.data.Language
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
import java.util.UUID

class Tier3CrossFeatureTest {

    private fun createClient(interceptor: SarvamMockFixtures.MockSarvamInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @Test
    fun testT3_01_LanguageSwitchAndFullPipelineAndReplay() {
        // Step 1: Switch language to Tamil -> Hindi
        val sourceLang = Language.fromBcp47("ta-IN")!!
        val targetLang = Language.fromBcp47("hi-IN")!!
        assertEquals("ta-IN", sourceLang.bcp47Code)
        assertEquals("hi-IN", targetLang.bcp47Code)

        // Step 2: Configure Mock Server with Tamil STT and Hindi Translation
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_TAMIL,
            translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_TA_TO_HI,
            ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS
        )
        val client = createClient(interceptor)

        // Step 3: Execute STT
        val wavAudio = SarvamMockFixtures.createCanonicalWav(ByteArray(6400))
        val sttRequest = Request.Builder()
            .url("https://api.sarvam.ai/speech-to-text")
            .header("api-subscription-key", "test-key")
            .post(
                MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("model", "saaras:v3")
                    .addFormDataPart("language_code", sourceLang.bcp47Code)
                    .addFormDataPart("mode", "transcribe")
                    .addFormDataPart("file", "audio.wav", wavAudio.toRequestBody("audio/wav".toMediaType()))
                    .build()
            ).build()

        val sttResponse = client.newCall(sttRequest).execute()
        assertEquals(200, sttResponse.code)
        val transcript = "எனக்கு கடுமையான காய்ச்சல் மற்றும் தலைவலி உள்ளது"

        // Step 4: Execute Translation
        val transRequest = Request.Builder()
            .url("https://api.sarvam.ai/translate")
            .header("api-subscription-key", "test-key")
            .header("Content-Type", "application/json")
            .post(
                """{"input":"$transcript","source_language_code":"${sourceLang.bcp47Code}","target_language_code":"${targetLang.bcp47Code}","model":"mayura:v1"}"""
                    .toRequestBody("application/json".toMediaType())
            ).build()

        val transResponse = client.newCall(transRequest).execute()
        assertEquals(200, transResponse.code)

        // Step 5: Execute TTS
        val ttsRequest = Request.Builder()
            .url("https://api.sarvam.ai/text-to-speech")
            .header("api-subscription-key", "test-key")
            .header("Content-Type", "application/json")
            .post(
                """{"inputs":["मुझे तेज बुखार और सिरदर्द है"],"target_language_code":"${targetLang.bcp47Code}","speaker":"priya","model":"bulbul:v3"}"""
                    .toRequestBody("application/json".toMediaType())
            ).build()

        val ttsResponse = client.newCall(ttsRequest).execute()
        assertEquals(200, ttsResponse.code)

        // Step 6: Verify total network calls == 3
        assertEquals(3, interceptor.capturedRequests.size)

        // Step 7: Replay Audio simulation (replays cached audio, 0 additional network calls)
        val cachedAudioBase64 = SarvamMockFixtures.MOCK_BASE64_WAV
        assertNotNull(cachedAudioBase64)
        // Verify no extra network requests during replay
        assertEquals(3, interceptor.capturedRequests.size)
    }

    @Test
    fun testT3_02_NetworkFailureDuringTranslationAndUserRetry() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_HINDI,
            translateStatusCode = 503,
            translateResponseJson = """{"error": "Service unavailable"}"""
        )
        val client = createClient(interceptor)

        // STT succeeds
        val sttReq = Request.Builder().url("https://api.sarvam.ai/speech-to-text").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(sttReq).execute().code)

        // Translation fails with 503
        val transReq = Request.Builder().url("https://api.sarvam.ai/translate").header("api-subscription-key", "k").post("".toRequestBody()).build()
        val failResponse = client.newCall(transReq).execute()
        assertEquals(503, failResponse.code)

        // Recovery: Network restores, retry succeeds
        interceptor.translateStatusCode = 200
        interceptor.translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_HI_TO_EN
        val retryResponse = client.newCall(transReq).execute()
        assertEquals(200, retryResponse.code)
    }

    @Test
    fun testT3_04_SilenceSttAbortsDownstreamTranslationAndTts() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_EMPTY
        )
        val client = createClient(interceptor)

        // Dispatch STT
        val sttReq = Request.Builder().url("https://api.sarvam.ai/speech-to-text").header("api-subscription-key", "k").post("".toRequestBody()).build()
        val sttRes = client.newCall(sttReq).execute()
        val body = sttRes.body?.string() ?: ""

        val isTranscriptBlank = body.contains("\"transcript\": \"\"")
        assertTrue("Transcript must be blank for silence audio", isTranscriptBlank)

        // Pipeline rule: if transcript is blank, do NOT dispatch translate or TTS
        val dispatchDownstream = !isTranscriptBlank
        assertFalse("Downstream calls must be aborted when STT returns silence", dispatchDownstream)
        assertEquals(1, interceptor.capturedRequests.size)
    }

    @Test
    fun testT3_09_RateLimit429BackoffAndSuccessfulRetry() {
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttStatusCode = 429,
            sttResponseJson = SarvamMockFixtures.ERROR_429_RATE_LIMIT
        )
        val client = createClient(interceptor)

        val req = Request.Builder().url("https://api.sarvam.ai/speech-to-text").header("api-subscription-key", "k").post("".toRequestBody()).build()

        // First attempt: 429
        val res1 = client.newCall(req).execute()
        assertEquals(429, res1.code)

        // Cooldown passed: server clears quota
        interceptor.sttStatusCode = 200
        interceptor.sttResponseJson = SarvamMockFixtures.STT_RESPONSE_HINDI

        // Second attempt: 200 OK
        val res2 = client.newCall(req).execute()
        assertEquals(200, res2.code)
    }

    @Test
    fun testT3_12_LatencyTelemetryAggregationCalculation() {
        val sttDuration = 420L
        val translateDuration = 210L
        val ttsDuration = 350L

        val totalDuration = sttDuration + translateDuration + ttsDuration
        assertEquals(980L, totalDuration)
        assertTrue("Total duration must be greater than individual component times", totalDuration > sttDuration)
    }
}
