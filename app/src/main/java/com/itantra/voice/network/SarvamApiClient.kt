package com.itantra.voice.network

import com.itantra.voice.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Domain-specific exception hierarchy mapping Sarvam AI cloud API failure modes
 * to actionable, user-friendly states.
 */
sealed class SarvamApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationException(message: String = "Invalid API key. Please verify your Sarvam AI subscription key.") : SarvamApiException(message)
    class ForbiddenException(message: String = "Access forbidden. Please check your account subscription.") : SarvamApiException(message)
    class RateLimitException(message: String = "Server rate limit reached. Please wait a few seconds before trying again.") : SarvamApiException(message)
    class ServerException(val code: Int, message: String = "Sarvam AI service is temporarily unavailable. Please try again shortly.") : SarvamApiException(message)
    class NetworkException(message: String = "No internet connection. Please check your Wi-Fi or mobile data.", cause: Throwable? = null) : SarvamApiException(message, cause)
    class EmptyResponseException(message: String = "Sarvam AI returned empty response.") : SarvamApiException(message)
    class InvalidRequestException(message: String) : SarvamApiException(message)
}

/**
 * High-reliability HTTP client for official Sarvam AI cloud APIs:
 * - Saaras v3 STT: `POST /speech-to-text`
 * - Mayura v1 Translate: `POST /translate`
 * - Bulbul v3 TTS: `POST /text-to-speech`
 */
class SarvamApiClient(
    private val apiKeyProvider: () -> String = { BuildConfig.SARVAM_API_KEY },
    private val baseUrl: String = "https://api.sarvam.ai/",
    customClient: OkHttpClient? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val AUTH_HEADER = "api-subscription-key"
        const val DEFAULT_SPEAKER = "priya"
        val SUPPORTED_SPEAKERS = listOf(
            "aditya", "ritu", "ashutosh", "priya", "neha", "rahul", "pooja", "rohan",
            "simran", "kavya", "amit", "dev", "ishita", "shreya", "ratan", "varun",
            "manan", "sumit", "roopa", "kabir", "aayan", "shubh", "advait", "anand",
            "tanya", "tarun", "sunny", "mani", "gokul", "vijay", "shruti", "suhani",
            "mohit", "kavitha", "rehan", "soham", "rupali"
        )
        private val PLACEHOLDERS = listOf(
            "YOUR_API_KEY_HERE",
            "your_sarvam_api_key_here",
            "<SARVAM_API_KEY>",
            "placeholder"
        )

        fun isPlaceholderKey(key: String): Boolean {
            val trimmed = key.trim()
            return trimmed.isBlank() || PLACEHOLDERS.any { it.equals(trimmed, ignoreCase = true) }
        }

        fun createAuthInterceptor(apiKeyProvider: () -> String): Interceptor {
            return Interceptor { chain ->
                val apiKey = apiKeyProvider().trim()
                if (isPlaceholderKey(apiKey)) {
                    throw SarvamApiException.AuthenticationException(
                        "Sarvam AI API key is missing. Please add SARVAM_API_KEY to local.properties."
                    )
                }
                val request = chain.request().newBuilder()
                    .header(AUTH_HEADER, apiKey)
                    .build()
                chain.proceed(request)
            }
        }

        /**
         * Body-level logging only in debug builds: TTS responses carry hundreds of KB
         * of base64 audio and STT requests carry the user's recorded speech, neither of
         * which belongs in a release logcat.
         */
        fun createLoggingInterceptor(): HttpLoggingInterceptor {
            return HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.HEADERS
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
                redactHeader(AUTH_HEADER)
            }
        }

        fun createDefaultOkHttpClient(apiKeyProvider: () -> String): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(createAuthInterceptor(apiKeyProvider))
                .addInterceptor(createLoggingInterceptor())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val okHttpClient: OkHttpClient = customClient ?: createDefaultOkHttpClient(apiKeyProvider)

    private fun newRequestBuilder(endpointPath: String): Request.Builder {
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = endpointPath.removePrefix("/")
        val fullUrl = "$cleanBase$cleanPath"

        // Set the header on the request as well as in createAuthInterceptor. The
        // interceptor only exists on the default client, so a caller-supplied
        // OkHttpClient would otherwise dispatch unauthenticated requests. Both paths
        // agree on what counts as a usable key via isPlaceholderKey.
        val builder = Request.Builder().url(fullUrl)
        val apiKey = apiKeyProvider().trim()
        if (!isPlaceholderKey(apiKey)) {
            builder.header(AUTH_HEADER, apiKey)
        }
        return builder
    }

    /**
     * Transcribes 16 kHz Mono WAV audio to text using Sarvam Saaras v3.
     *
     * @param wavData Canonical 44-byte RIFF WAV byte array
     * @param languageCode BCP-47 language tag (e.g. "hi-IN", "en-IN")
     */
    suspend fun transcribe(
        wavData: ByteArray,
        languageCode: String
    ): Result<SpeechResponse> = withContext(ioDispatcher) {
        if (wavData.isEmpty()) {
            return@withContext Result.failure(
                SarvamApiException.InvalidRequestException("WAV audio payload cannot be empty.")
            )
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "saaras:v3")
            .addFormDataPart("language_code", languageCode)
            .addFormDataPart("mode", "transcribe")
            .addFormDataPart(
                "file",
                "recording.wav",
                wavData.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = newRequestBuilder("speech-to-text")
            .post(requestBody)
            .build()

        executeCall(request) { bodyString ->
            json.decodeFromString<SpeechResponse>(bodyString)
        }
    }

    /**
     * Translates text between Indic languages using Sarvam Mayura v1.
     *
     * @param text Source text to translate
     * @param sourceLang BCP-47 source language code
     * @param targetLang BCP-47 target language code
     */
    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<TranslationResponse> = withContext(ioDispatcher) {
        if (text.isBlank()) {
            return@withContext Result.failure(
                SarvamApiException.InvalidRequestException("Input text to translate cannot be empty.")
            )
        }

        val requestPayload = TranslationRequest(
            input = text,
            source_language_code = sourceLang,
            target_language_code = targetLang,
            model = "mayura:v1",
            mode = "formal"
        )
        val jsonString = json.encodeToString(TranslationRequest.serializer(), requestPayload)

        val request = newRequestBuilder("translate")
            .header("Content-Type", "application/json")
            .post(jsonString.toRequestBody("application/json".toMediaType()))
            .build()

        executeCall(request) { bodyString ->
            json.decodeFromString<TranslationResponse>(bodyString)
        }
    }

    /**
     * Synthesizes text to natural speech WAV using Sarvam Bulbul v3.
     *
     * @param text Input text to speak
     * @param targetLang BCP-47 target language code
     * @param speaker Speaker voice name (default: "priya")
     */
    suspend fun synthesize(
        text: String,
        targetLang: String,
        speaker: String = DEFAULT_SPEAKER
    ): Result<TtsResponse> = withContext(ioDispatcher) {
        if (text.isBlank()) {
            return@withContext Result.failure(
                SarvamApiException.InvalidRequestException("Input text for TTS cannot be empty.")
            )
        }

        val requestPayload = TtsRequest(
            inputs = listOf(text),
            target_language_code = targetLang,
            speaker = speaker,
            model = "bulbul:v3",
            speech_sample_rate = 16000
        )
        val jsonString = json.encodeToString(TtsRequest.serializer(), requestPayload)

        val request = newRequestBuilder("text-to-speech")
            .header("Content-Type", "application/json")
            .post(jsonString.toRequestBody("application/json".toMediaType()))
            .build()

        executeCall(request) { bodyString ->
            val response = json.decodeFromString<TtsResponse>(bodyString)
            if (response.audios.isEmpty() || response.audios.first().isBlank()) {
                throw SarvamApiException.EmptyResponseException("TTS response contained empty audio array.")
            }
            response
        }
    }

    private fun <T> executeCall(
        request: Request,
        parser: (String) -> T
    ): Result<T> {
        return try {
            // `use` guarantees the connection returns to the pool even when the body is
            // null or the parser throws; without it every failed call leaked a socket.
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(mapHttpError(response))
                } else {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isBlank()) {
                        Result.failure(SarvamApiException.EmptyResponseException("Response body was empty."))
                    } else {
                        Result.success(parser(bodyString))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    private fun mapHttpError(response: Response): Throwable {
        val code = response.code
        val bodyString = try {
            response.body?.string() ?: ""
        } catch (ignored: Exception) {
            ""
        }

        val serverMessage = try {
            if (bodyString.isNotBlank()) {
                val parsed = json.decodeFromString<SarvamErrorResponse>(bodyString)
                parsed.error?.message ?: parsed.message
            } else null
        } catch (ignored: Exception) {
            null
        }

        return when (code) {
            401 -> SarvamApiException.AuthenticationException(
                serverMessage ?: "Invalid API key. Please verify your Sarvam AI subscription key."
            )
            403 -> SarvamApiException.ForbiddenException(
                serverMessage ?: "Access forbidden. Please check your account subscription."
            )
            429 -> SarvamApiException.RateLimitException(
                serverMessage ?: "Server rate limit reached. Please wait a few seconds before trying again."
            )
            in 500..599 -> SarvamApiException.ServerException(
                code,
                serverMessage ?: "Sarvam AI service is temporarily unavailable. Please try again shortly."
            )
            else -> SarvamApiException.ServerException(
                code,
                serverMessage ?: "Sarvam AI request failed with HTTP $code"
            )
        }
    }

    private fun mapException(e: Exception): Throwable {
        return when (e) {
            is SarvamApiException -> e
            is SocketTimeoutException -> SarvamApiException.NetworkException(
                "Network timed out while processing speech. Please try again.",
                e
            )
            is UnknownHostException, is ConnectException -> SarvamApiException.NetworkException(
                "No internet connection. Please check your Wi-Fi or mobile data.",
                e
            )
            is IOException -> SarvamApiException.NetworkException(
                "Network communication error: ${e.message}",
                e
            )
            else -> e
        }
    }
}
