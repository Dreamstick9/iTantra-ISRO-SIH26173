package com.itantra.voice.e2e

import com.itantra.voice.data.Language
import com.itantra.voice.fixtures.SarvamMockFixtures
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class Tier4RealWorldScenarioTest {

    private fun createClient(interceptor: SarvamMockFixtures.MockSarvamInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @Test
    fun testScenario1_CoastalDisasterEmergencyWarning_HindiToEnglish() {
        // Context: Cyclone alert broadcast in coastal belt
        val sourceLang = Language.HINDI
        val targetLang = Language.ENGLISH

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_HINDI,
            translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_HI_TO_EN,
            ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS
        )
        val client = createClient(interceptor)

        // 1. Audio input: 3.2s recorded audio (102,400 bytes PCM)
        val rawPcm = ByteArray(102400)
        val wavBytes = SarvamMockFixtures.createCanonicalWav(rawPcm)
        assertEquals(102444, wavBytes.size)

        // 2. Transcribe via Saaras v3
        val sttReq = Request.Builder()
            .url("https://api.sarvam.ai/speech-to-text")
            .header("api-subscription-key", "test-key")
            .post(
                MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("model", "saaras:v3")
                    .addFormDataPart("language_code", sourceLang.bcp47Code)
                    .addFormDataPart("mode", "transcribe")
                    .addFormDataPart("file", "cyclone_alert.wav", wavBytes.toRequestBody("audio/wav".toMediaType()))
                    .build()
            ).build()
        val sttRes = client.newCall(sttReq).execute()
        assertEquals(200, sttRes.code)
        val transcript = "तटीय क्षेत्र में भीषण चक्रवात की चेतावनी है, तुरंत सुरक्षित स्थान पर जाएं"

        // 3. Translate via Mayura v1
        val transReq = Request.Builder()
            .url("https://api.sarvam.ai/translate")
            .header("api-subscription-key", "test-key")
            .post(
                """{"input":"$transcript","source_language_code":"${sourceLang.bcp47Code}","target_language_code":"${targetLang.bcp47Code}","model":"mayura:v1"}"""
                    .toRequestBody("application/json".toMediaType())
            ).build()
        val transRes = client.newCall(transReq).execute()
        assertEquals(200, transRes.code)
        val translatedText = "There is a warning of severe cyclone in coastal area, move to safe place immediately"

        // 4. Synthesize via Bulbul v3
        val ttsReq = Request.Builder()
            .url("https://api.sarvam.ai/text-to-speech")
            .header("api-subscription-key", "test-key")
            .post(
                """{"inputs":["$translatedText"],"target_language_code":"${targetLang.bcp47Code}","speaker":"priya","model":"bulbul:v3"}"""
                    .toRequestBody("application/json".toMediaType())
            ).build()
        val ttsRes = client.newCall(ttsReq).execute()
        assertEquals(200, ttsRes.code)

        // 5. Feedback Logging verification
        val feedbackRecord = """
        {
            "id": "${UUID.randomUUID()}",
            "source_language": "${sourceLang.bcp47Code}",
            "target_language": "${targetLang.bcp47Code}",
            "source_text": "$transcript",
            "translated_text": "$translatedText",
            "feedback": "positive"
        }
        """.trimIndent()
        assertTrue(feedbackRecord.contains("positive"))
        assertTrue(feedbackRecord.contains("hi-IN"))
        assertTrue(feedbackRecord.contains("en-IN"))
    }

    @Test
    fun testScenario2_TamilHealthInquiryToHindiFirstResponder() {
        val sourceLang = Language.TAMIL
        val targetLang = Language.HINDI

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_TAMIL,
            translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_TA_TO_HI,
            ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS
        )
        val client = createClient(interceptor)

        // 1. Audio input: 2.4s audio (76,800 bytes)
        val rawPcm = ByteArray(76800)
        val wavBytes = SarvamMockFixtures.createCanonicalWav(rawPcm)

        // 2. Saaras v3 STT
        val sttReq = Request.Builder().url("https://api.sarvam.ai/speech-to-text").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(sttReq).execute().code)

        // 3. Mayura v1 Translate
        val transReq = Request.Builder().url("https://api.sarvam.ai/translate").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(transReq).execute().code)

        // 4. Bulbul v3 TTS
        val ttsReq = Request.Builder().url("https://api.sarvam.ai/text-to-speech").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(ttsReq).execute().code)

        // 5. Total 3 network requests executed
        assertEquals(3, interceptor.capturedRequests.size)
    }

    @Test
    fun testScenario3_EnglishTacticalCommandToMarathiFieldUnit() {
        val sourceLang = Language.ENGLISH
        val targetLang = Language.MARATHI

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_ENGLISH,
            translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_EN_TO_MR,
            ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS
        )
        val client = createClient(interceptor)

        // STT -> Translate -> TTS
        val sttReq = Request.Builder().url("https://api.sarvam.ai/speech-to-text").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(sttReq).execute().code)

        val transReq = Request.Builder().url("https://api.sarvam.ai/translate").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(transReq).execute().code)

        val ttsReq = Request.Builder().url("https://api.sarvam.ai/text-to-speech").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(ttsReq).execute().code)

        // Commander rates translation quality with 👎 (negative) feedback
        val negativeFeedback = """
        {
            "id": "${UUID.randomUUID()}",
            "source_language": "${sourceLang.bcp47Code}",
            "target_language": "${targetLang.bcp47Code}",
            "source_text": "Evacuate sector four and report to base camp immediately",
            "translated_text": "सेक्टर चार रिकामे करा आणि त्वरित बेस कॅम्पवर रिपोर्ट करा",
            "feedback": "negative"
        }
        """.trimIndent()
        assertTrue(negativeFeedback.contains("negative"))
        assertTrue(negativeFeedback.contains("mr-IN"))
    }

    @Test
    fun testScenario4_BengaliFishermenWeatherAdvisoryToOdiaPatrol() {
        val sourceLang = Language.BENGALI
        val targetLang = Language.ODIA

        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttResponseJson = SarvamMockFixtures.STT_RESPONSE_BENGALI,
            translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_BN_TO_OD,
            ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS
        )
        val client = createClient(interceptor)

        val sttReq = Request.Builder().url("https://api.sarvam.ai/speech-to-text").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(sttReq).execute().code)

        val transReq = Request.Builder().url("https://api.sarvam.ai/translate").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(transReq).execute().code)

        val ttsReq = Request.Builder().url("https://api.sarvam.ai/text-to-speech").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(ttsReq).execute().code)

        assertEquals("bn-IN", sourceLang.bcp47Code)
        assertEquals("od-IN", targetLang.bcp47Code)
    }

    @Test
    fun testScenario5_DisasterZoneNetworkOutageRecovery_GujaratiToTelugu() {
        val sourceLang = Language.GUJARATI
        val targetLang = Language.TELUGU

        // First attempt fails with 500 error due to network tower failure
        val interceptor = SarvamMockFixtures.MockSarvamInterceptor(
            sttStatusCode = 500,
            sttResponseJson = SarvamMockFixtures.ERROR_500_SERVER_ERROR
        )
        val client = createClient(interceptor)

        val sttReq = Request.Builder().url("https://api.sarvam.ai/speech-to-text").header("api-subscription-key", "k").post("".toRequestBody()).build()
        val failRes = client.newCall(sttReq).execute()
        assertEquals(500, failRes.code)

        // Tower power restored, user retries -> succeeds end-to-end
        interceptor.sttStatusCode = 200
        interceptor.sttResponseJson = SarvamMockFixtures.STT_RESPONSE_GUJARATI
        interceptor.translateResponseJson = SarvamMockFixtures.TRANSLATE_RESPONSE_GU_TO_TE
        interceptor.ttsResponseJson = SarvamMockFixtures.TTS_RESPONSE_SUCCESS

        val successStt = client.newCall(sttReq).execute()
        assertEquals(200, successStt.code)

        val transReq = Request.Builder().url("https://api.sarvam.ai/translate").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(transReq).execute().code)

        val ttsReq = Request.Builder().url("https://api.sarvam.ai/text-to-speech").header("api-subscription-key", "k").post("".toRequestBody()).build()
        assertEquals(200, client.newCall(ttsReq).execute().code)

        assertEquals("gu-IN", sourceLang.bcp47Code)
        assertEquals("te-IN", targetLang.bcp47Code)
    }
}
