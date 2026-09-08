package com.itantra.voice.pipeline

import android.util.Base64
import com.itantra.voice.data.Language
import com.itantra.voice.network.SarvamApiClient
import java.util.logging.Logger

/**
 * [SpeechPipeline] backed by the Sarvam AI cloud APIs.
 *
 * Opt-in: available only when a non-placeholder `sarvam.api.key` is configured in
 * `local.properties`. The SIH26173 brief mandates an offline pipeline, so this exists
 * as a higher-accuracy alternative for demos rather than as the default.
 */
class SarvamSpeechPipeline(
    private val client: SarvamApiClient = SarvamApiClient(),
    private val apiKeyProvider: () -> String = { com.itantra.voice.BuildConfig.SARVAM_API_KEY },
    private val base64Decoder: (String) -> ByteArray = { Base64.decode(it, Base64.DEFAULT) }
) : SpeechPipeline {

    private val log = Logger.getLogger("SarvamSpeechPipeline")

    override val mode: PipelineMode = PipelineMode.CLOUD
    override val displayName: String = "Sarvam Cloud"

    override suspend fun isAvailable(): Boolean =
        !SarvamApiClient.isPlaceholderKey(apiKeyProvider())

    override suspend fun transcribe(
        wavData: ByteArray,
        source: Language
    ): Result<TranscriptionResult> =
        client.transcribe(wavData, source.bcp47Code)
            .map { TranscriptionResult(it.transcript.trim()) }

    override suspend fun translate(
        text: String,
        source: Language,
        target: Language
    ): Result<TranslationResult> {
        // Translating a language into itself is a no-op the API should not be billed for.
        if (source == target) return Result.success(TranslationResult(text))
        return client.translate(text, source.bcp47Code, target.bcp47Code)
            .map { TranslationResult(it.translatedText.trim()) }
    }

    override suspend fun synthesize(
        text: String,
        target: Language,
        isEmergency: Boolean
    ): Result<SynthesisResult> =
        client.synthesize(text, target.bcp47Code).mapCatching { response ->
            val encoded = response.audios.firstOrNull()
            if (encoded.isNullOrBlank()) {
                throw IllegalStateException("Speech synthesis returned no audio.")
            }
            SynthesisResult(wavBytes = base64Decoder(encoded), spokenDirectly = false)
        }

    override fun release() {
        runCatching {
            client.okHttpClient.dispatcher.executorService.shutdown()
            client.okHttpClient.connectionPool.evictAll()
        }.onFailure { log.warning("Error releasing Sarvam HTTP client: ${it.message}") }
    }
}
