package com.itantra.voice.fixtures

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * SarvamMockFixtures provides canonical reference oracles, mock payloads,
 * and an OkHttp Mock Interceptor for hermetic offline testing across all 4 tiers.
 */
object SarvamMockFixtures {

    const val MOCK_BASE64_WAV = "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA="

    // Authoritative Mock JSON Responses

    const val STT_RESPONSE_HINDI = """
    {
        "transcript": "तटीय क्षेत्र में भीषण चक्रवात की चेतावनी है, तुरंत सुरक्षित स्थान पर जाएं",
        "language_code": "hi-IN"
    }
    """

    const val STT_RESPONSE_TAMIL = """
    {
        "transcript": "எனக்கு கடுமையான காய்ச்சல் மற்றும் தலைவலி உள்ளது",
        "language_code": "ta-IN"
    }
    """

    const val STT_RESPONSE_ENGLISH = """
    {
        "transcript": "Evacuate sector four and report to base camp immediately",
        "language_code": "en-IN"
    }
    """

    const val STT_RESPONSE_BENGALI = """
    {
        "transcript": "আগামী চব্বিশ ঘণ্টায় সমুদ্রে যাবেন না",
        "language_code": "bn-IN"
    }
    """

    const val STT_RESPONSE_GUJARATI = """
    {
        "transcript": "પીવાના પાણીની તાત્કાલિક જરૂર છે",
        "language_code": "gu-IN"
    }
    """

    const val STT_RESPONSE_EMPTY = """
    {
        "transcript": "",
        "language_code": "hi-IN"
    }
    """

    const val TRANSLATE_RESPONSE_HI_TO_EN = """
    {
        "translated_text": "There is a warning of severe cyclone in coastal area, move to safe place immediately"
    }
    """

    const val TRANSLATE_RESPONSE_TA_TO_HI = """
    {
        "translated_text": "मुझे तेज बुखार और सिरदर्द है"
    }
    """

    const val TRANSLATE_RESPONSE_EN_TO_MR = """
    {
        "translated_text": "सेक्टर चार रिकामे करा आणि त्वरित बेस कॅम्पवर रिपोर्ट करा"
    }
    """

    const val TRANSLATE_RESPONSE_BN_TO_OD = """
    {
        "translated_text": "ଆଗାମୀ ଚବିଶ ଘଣ୍ଟାରେ ସମୁଦ୍ରକୁ ଯାଆନ୍ତୁ ନାହିଁ"
    }
    """

    const val TRANSLATE_RESPONSE_GU_TO_TE = """
    {
        "translated_text": "తాగునీటి తక్షణ అవసరం ఉంది"
    }
    """

    const val TTS_RESPONSE_SUCCESS = """
    {
        "audios": [
            "$MOCK_BASE64_WAV"
        ]
    }
    """

    const val ERROR_401_UNAUTHORIZED = """
    {
        "error": {
            "message": "Invalid or missing subscription key",
            "code": "unauthorized"
        }
    }
    """

    const val ERROR_429_RATE_LIMIT = """
    {
        "error": {
            "message": "Rate limit exceeded. Please wait a few seconds before retrying.",
            "code": "rate_limit_exceeded"
        }
    }
    """

    const val ERROR_500_SERVER_ERROR = """
    {
        "error": {
            "message": "Internal server error in acoustic speech model",
            "code": "internal_error"
        }
    }
    """

    /**
     * Authoritative Pure-Kotlin Canonical 44-byte RIFF WAV Header Encoder & Validator.
     */
    fun createCanonicalWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val audioLength = pcmData.size
        val totalDataLen = audioLength + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteArray(44)
        // 00-03: 'RIFF'
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        // 04-07: total file size - 8 bytes
        header[4] = (totalDataLen and 0xFF).toByte()
        header[5] = ((totalDataLen shr 8) and 0xFF).toByte()
        header[6] = ((totalDataLen shr 16) and 0xFF).toByte()
        header[7] = ((totalDataLen shr 24) and 0xFF).toByte()
        // 08-11: 'WAVE'
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 12-15: 'fmt '
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        // 16-19: Subchunk1Size = 16
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        // 20-21: AudioFormat = 1 (PCM)
        header[20] = 1
        header[21] = 0
        // 22-23: NumChannels
        header[22] = (channels.toInt() and 0xFF).toByte()
        header[23] = ((channels.toInt() shr 8) and 0xFF).toByte()
        // 24-27: SampleRate
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()
        // 28-31: ByteRate
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        // 32-33: BlockAlign
        header[32] = (blockAlign.toInt() and 0xFF).toByte()
        header[33] = ((blockAlign.toInt() shr 8) and 0xFF).toByte()
        // 34-35: BitsPerSample
        header[34] = (bitsPerSample.toInt() and 0xFF).toByte()
        header[35] = ((bitsPerSample.toInt() shr 8) and 0xFF).toByte()
        // 36-39: 'data'
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        // 40-43: Subchunk2Size
        header[40] = (audioLength and 0xFF).toByte()
        header[41] = ((audioLength shr 8) and 0xFF).toByte()
        header[42] = ((audioLength shr 16) and 0xFF).toByte()
        header[43] = ((audioLength shr 24) and 0xFF).toByte()

        val wav = ByteArray(44 + audioLength)
        System.arraycopy(header, 0, wav, 0, 44)
        System.arraycopy(pcmData, 0, wav, 44, audioLength)
        return wav
    }

    /**
     * OkHttp Interceptor for hermetic offline testing of Sarvam AI REST contracts.
     */
    class MockSarvamInterceptor(
        var sttResponseJson: String = STT_RESPONSE_HINDI,
        var translateResponseJson: String = TRANSLATE_RESPONSE_HI_TO_EN,
        var ttsResponseJson: String = TTS_RESPONSE_SUCCESS,
        var sttStatusCode: Int = 200,
        var translateStatusCode: Int = 200,
        var ttsStatusCode: Int = 200,
        var recordRequests: Boolean = true
    ) : Interceptor {

        val capturedRequests = mutableListOf<okhttp3.Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (recordRequests) {
                capturedRequests.add(request)
            }

            val path = request.url.encodedPath
            val (statusCode, bodyJson) = when {
                path.endsWith("speech-to-text") -> sttStatusCode to sttResponseJson
                path.endsWith("translate") -> translateStatusCode to translateResponseJson
                path.endsWith("text-to-speech") -> ttsStatusCode to ttsResponseJson
                else -> 404 to """{"error": "Endpoint not found"}"""
            }

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode in 200..299) "OK" else "Error")
                .body(bodyJson.trimIndent().toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
