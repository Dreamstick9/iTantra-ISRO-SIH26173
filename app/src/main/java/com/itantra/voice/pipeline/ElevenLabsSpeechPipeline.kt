package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import com.itantra.voice.network.ElevenLabsApiClient
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/**
 * Cloud pipeline backed by ElevenLabs.
 *
 * Recognition uses Scribe (`scribe_v2`, 90+ languages) and synthesis uses the fastest
 * voice model that supports the target language — Flash v2.5 at ~75 ms where available,
 * falling back to v3 for broader coverage. Audio comes back as 16 kHz WAV, matching the
 * app's capture rate, so it flows straight into the player with no transcoding.
 *
 * **Translation does not come from ElevenLabs.** They expose no text-translation endpoint;
 * their only translation lives in the asynchronous, job-based Dubbing project API, which
 * cannot serve a push-to-talk loop. A [Translator] is injected instead — Sarvam's Mayura
 * when a key is configured, otherwise a pass-through that relays the recognised text.
 */
class ElevenLabsSpeechPipeline(
    private val client: ElevenLabsApiClient,
    private val apiKeyProvider: () -> String,
    private val translator: Translator = PassThroughTranslator,
    private val voiceIdProvider: () -> String = { "" }
) : SpeechPipeline {

    private val log = Logger.getLogger("ElevenLabsSpeechPipeline")

    override val mode: PipelineMode = PipelineMode.CLOUD
    override val displayName: String = "ElevenLabs"

    /** Voice resolved from the account, so no voice id has to be hardcoded. */
    private val resolvedVoiceId = AtomicReference<String?>(null)

    override suspend fun isAvailable(): Boolean =
        !ElevenLabsApiClient.isPlaceholderKey(apiKeyProvider())

    /**
     * Resolves a voice before the first utterance so synthesis never pays for a voice
     * lookup mid-transmission.
     */
    override suspend fun prepare() {
        if (!isAvailable()) return

        val configured = voiceIdProvider().trim()
        if (configured.isNotBlank()) {
            resolvedVoiceId.set(configured)
            return
        }
        if (resolvedVoiceId.get() != null) return

        client.listVoices()
            .onSuccess { voices ->
                voices.firstOrNull()?.let {
                    log.info("Using ElevenLabs voice '${it.name}' (${it.voiceId})")
                    resolvedVoiceId.set(it.voiceId)
                }
            }
            .onFailure { log.warning("Could not list ElevenLabs voices: ${it.message}") }
    }

    override suspend fun transcribe(
        wavData: ByteArray,
        source: Language
    ): Result<TranscriptionResult> =
        client.transcribe(wavData, ElevenLabsApiClient.isoCode(source))
            .map { TranscriptionResult(it.text.trim()) }

    override suspend fun translate(
        text: String,
        source: Language,
        target: Language
    ): Result<TranslationResult> {
        if (source == target) return Result.success(TranslationResult(text))
        return translator.translate(text, source, target).map { TranslationResult(it) }
    }

    override suspend fun synthesize(
        text: String,
        target: Language,
        isEmergency: Boolean
    ): Result<SynthesisResult> {
        val voiceId = resolvedVoiceId.get()
            ?: voiceIdProvider().trim().ifBlank { null }
            ?: run {
                // prepare() may not have run, or the voice lookup failed; retry once here
                // rather than failing the utterance outright.
                prepare()
                resolvedVoiceId.get()
            }
            ?: return Result.failure(
                IllegalStateException("No ElevenLabs voice available. Check the API key and that the account has at least one voice.")
            )

        return client.synthesize(text, voiceId, target)
            .map { SynthesisResult(wavBytes = it, spokenDirectly = false) }
    }

    override fun release() {
        runCatching {
            client.okHttpClient.dispatcher.executorService.shutdown()
            client.okHttpClient.connectionPool.evictAll()
        }
    }
}
