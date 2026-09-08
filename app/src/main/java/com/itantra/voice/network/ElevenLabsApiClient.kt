package com.itantra.voice.network

import com.itantra.voice.data.Language
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Failure modes of the ElevenLabs API, mapped to messages an operator can act on.
 */
sealed class ElevenLabsException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationException(message: String = "Invalid ElevenLabs API key.") : ElevenLabsException(message)
    class QuotaException(message: String = "ElevenLabs credits exhausted. Check your plan usage.") : ElevenLabsException(message)
    class RateLimitException(message: String = "ElevenLabs rate limit reached. Wait a moment and try again.") : ElevenLabsException(message)
    class ServerException(val code: Int, message: String) : ElevenLabsException(message)
    class NetworkException(message: String, cause: Throwable? = null) : ElevenLabsException(message, cause)
    class EmptyResponseException(message: String) : ElevenLabsException(message)
    class InvalidRequestException(message: String) : ElevenLabsException(message)
}

/**
 * Client for the ElevenLabs REST API.
 *
 * Covers the two stages ElevenLabs actually serves for this app:
 *  - Scribe speech-to-text (`POST /v1/speech-to-text`)
 *  - Text-to-speech (`POST /v1/text-to-speech/{voice_id}`)
 *
 * There is deliberately no translate call: ElevenLabs exposes translation only inside its
 * Dubbing *project* API, which is asynchronous and job-based and cannot serve a
 * push-to-talk loop. Translation is handled by a separate
 * [com.itantra.voice.pipeline.Translator].
 */
class ElevenLabsApiClient(
    private val apiKeyProvider: () -> String,
    private val baseUrl: String = "https://api.elevenlabs.io/",
    customClient: OkHttpClient? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val AUTH_HEADER = "xi-api-key"

        /** Scribe transcription model. 90+ languages, covering all ten iTantra supports. */
        const val STT_MODEL = "scribe_v2"

        /**
         * Lowest-latency multilingual voice model (~75 ms), but only 32 languages.
         * SIH26173 scores latency at 20%, so it is preferred wherever it is supported.
         */
        const val TTS_MODEL_FAST = "eleven_flash_v2_5"

        /** Broadest coverage (70+ languages), used for languages Flash does not carry. */
        const val TTS_MODEL_BROAD = "eleven_v3"

        /**
         * Languages confirmed on the Flash v2.5 model. Anything outside this set is
         * synthesised with [TTS_MODEL_BROAD] instead, trading latency for coverage rather
         * than failing the utterance.
         */
        private val FAST_MODEL_LANGUAGES = setOf(
            Language.ENGLISH,
            Language.HINDI,
            Language.BENGALI,
            Language.TAMIL,
            Language.TELUGU,
            Language.KANNADA,
            Language.MALAYALAM
        )

        /**
         * 16 kHz mono WAV: matches the app's capture rate and arrives with a RIFF header,
         * so it flows straight into [com.itantra.voice.audio.AudioPlayer] with no
         * transcoding and no base64 hop.
         */
        const val TTS_OUTPUT_FORMAT = "wav_16000"

        private val PLACEHOLDERS = listOf(
            "YOUR_API_KEY_HERE", "your_elevenlabs_api_key_here", "<ELEVENLABS_API_KEY>", "placeholder"
        )

        fun isPlaceholderKey(key: String): Boolean {
            val trimmed = key.trim()
            return trimmed.isBlank() || PLACEHOLDERS.any { it.equals(trimmed, ignoreCase = true) }
        }

        /** Picks the fastest model that supports [language]. */
        fun modelFor(language: Language): String =
            if (language in FAST_MODEL_LANGUAGES) TTS_MODEL_FAST else TTS_MODEL_BROAD

        /**
         * ElevenLabs expects a bare ISO 639-1 code ("hi"), not a BCP-47 tag ("hi-IN").
         * Odia is carried as "od-IN" to match Sarvam's tag but its ISO code is "or".
         */
        fun isoCode(language: Language): String = when (language) {
            Language.ODIA -> "or"
            else -> language.bcp47Code.substringBefore('-')
        }

        fun createClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    val okHttpClient: OkHttpClient = customClient ?: createClient()

    private fun request(path: String): Request.Builder {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val builder = Request.Builder().url("$base${path.removePrefix("/")}")
        val key = apiKeyProvider().trim()
        if (!isPlaceholderKey(key)) builder.header(AUTH_HEADER, key)
        return builder
    }

    /**
     * Transcribes 16 kHz mono WAV audio with Scribe.
     *
     * @param languageCode ISO 639-1 code, or null to let Scribe detect the language.
     */
    suspend fun transcribe(
        wavData: ByteArray,
        languageCode: String?
    ): Result<ScribeResponse> = withContext(ioDispatcher) {
        if (wavData.isEmpty()) {
            return@withContext Result.failure(
                ElevenLabsException.InvalidRequestException("Audio payload is empty.")
            )
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model_id", STT_MODEL)
            .apply { languageCode?.let { addFormDataPart("language_code", it) } }
            .addFormDataPart(
                "file",
                "utterance.wav",
                wavData.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        execute(request("v1/speech-to-text").post(body).build()) { text ->
            json.decodeFromString<ScribeResponse>(text)
        }
    }

    /**
     * Synthesises [text] as 16 kHz WAV.
     *
     * Returns raw audio bytes rather than a parsed body: this endpoint answers with the
     * audio file itself, not JSON.
     */
    suspend fun synthesize(
        text: String,
        voiceId: String,
        language: Language
    ): Result<ByteArray> = withContext(ioDispatcher) {
        if (text.isBlank()) {
            return@withContext Result.failure(
                ElevenLabsException.InvalidRequestException("Nothing to speak.")
            )
        }
        if (voiceId.isBlank()) {
            return@withContext Result.failure(
                ElevenLabsException.InvalidRequestException("No ElevenLabs voice selected.")
            )
        }

        val payload = ElevenTtsRequest(
            text = text,
            modelId = modelFor(language),
            languageCode = isoCode(language),
            voiceSettings = ElevenVoiceSettings()
        )

        val request = request("v1/text-to-speech/$voiceId?output_format=$TTS_OUTPUT_FORMAT")
            .header("Content-Type", "application/json")
            .header("Accept", "audio/wav")
            .post(
                json.encodeToString(ElevenTtsRequest.serializer(), payload)
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use Result.failure(mapHttpError(response))
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    Result.failure(ElevenLabsException.EmptyResponseException("Synthesis returned no audio."))
                } else {
                    Result.success(bytes)
                }
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    /**
     * Lists the voices on this account.
     *
     * Used to pick a default rather than hardcoding a voice id, which would break for any
     * account that does not have that particular voice.
     */
    suspend fun listVoices(): Result<List<ElevenVoice>> = withContext(ioDispatcher) {
        execute(request("v2/voices").get().build()) { text ->
            json.decodeFromString<ElevenVoicesResponse>(text).voices
        }
    }

    private fun <T> execute(request: Request, parse: (String) -> T): Result<T> = try {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Result.failure(mapHttpError(response))
            } else {
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    Result.failure(ElevenLabsException.EmptyResponseException("Empty response body."))
                } else {
                    Result.success(parse(body))
                }
            }
        }
    } catch (e: Exception) {
        Result.failure(mapException(e))
    }

    private fun mapHttpError(response: Response): Throwable {
        val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val serverMessage = runCatching {
            json.decodeFromString<ElevenErrorResponse>(body).detail?.message
        }.getOrNull()

        return when (response.code) {
            401, 403 -> ElevenLabsException.AuthenticationException(
                serverMessage ?: "Invalid ElevenLabs API key."
            )
            // ElevenLabs reports an exhausted character quota as 402.
            402 -> ElevenLabsException.QuotaException(
                serverMessage ?: "ElevenLabs credits exhausted. Check your plan usage."
            )
            422 -> ElevenLabsException.InvalidRequestException(
                serverMessage ?: "ElevenLabs rejected the request. The language or voice may be unsupported."
            )
            429 -> ElevenLabsException.RateLimitException(
                serverMessage ?: "ElevenLabs rate limit reached. Wait a moment and try again."
            )
            else -> ElevenLabsException.ServerException(
                response.code,
                serverMessage ?: "ElevenLabs request failed with HTTP ${response.code}."
            )
        }
    }

    private fun mapException(e: Exception): Throwable = when (e) {
        is ElevenLabsException -> e
        is SocketTimeoutException -> ElevenLabsException.NetworkException(
            "ElevenLabs timed out. Please try again.", e
        )
        is UnknownHostException, is ConnectException -> ElevenLabsException.NetworkException(
            "No internet connection. ElevenLabs needs network access.", e
        )
        is IOException -> ElevenLabsException.NetworkException("Network error: ${e.message}", e)
        else -> e
    }
}
