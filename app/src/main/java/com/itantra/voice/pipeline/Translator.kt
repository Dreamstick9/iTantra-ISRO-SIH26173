package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import com.itantra.voice.network.SarvamApiClient

/**
 * Text translation, separated from the speech engine.
 *
 * The three pipeline stages do not have to come from one vendor, and for ElevenLabs they
 * cannot: ElevenLabs has no text-translation endpoint. Its only translation lives inside
 * the Dubbing *project* API, which is asynchronous and job-based — you create a project,
 * add target languages, then poll — which cannot serve a push-to-talk loop scored on
 * latency. Splitting translation out lets ElevenLabs do what it is excellent at
 * (recognition and synthesis) while translation comes from wherever one is available.
 */
interface Translator {

    /** Name shown in the UI when it differs from the speech engine. */
    val displayName: String

    /** True when this translator can be used right now. */
    suspend fun isAvailable(): Boolean

    /**
     * Translates [text] from [source] to [target].
     *
     * Implementations that cannot translate return the input unchanged rather than
     * failing, so a same-language transceiver still works.
     */
    suspend fun translate(text: String, source: Language, target: Language): Result<String>
}

/**
 * Relays text unchanged.
 *
 * Used when no translator is configured. The transceiver still works — the far handset
 * speaks what was said — but cross-language translation does not happen. Failing the
 * utterance instead would be worse: in a distress call, delivering the words in the
 * wrong language beats delivering nothing.
 */
object PassThroughTranslator : Translator {
    override val displayName: String = "None"

    // Reports false so callers treat it as "no translator configured" and label the
    // output with the language the text is actually in.
    override suspend fun isAvailable(): Boolean = false
    override suspend fun translate(text: String, source: Language, target: Language): Result<String> =
        Result.success(text)
}

/**
 * Translation via Sarvam's Mayura model.
 *
 * Kept available independently of the speech engine so an ElevenLabs pipeline can still
 * translate between Indic languages when a Sarvam key is also configured.
 */
class SarvamTranslator(
    private val client: SarvamApiClient = SarvamApiClient(),
    private val apiKeyProvider: () -> String = { com.itantra.voice.BuildConfig.SARVAM_API_KEY }
) : Translator {

    override val displayName: String = "Sarvam"

    override suspend fun isAvailable(): Boolean =
        !SarvamApiClient.isPlaceholderKey(apiKeyProvider())

    override suspend fun translate(
        text: String,
        source: Language,
        target: Language
    ): Result<String> {
        if (source == target) return Result.success(text)
        return client.translate(text, source.bcp47Code, target.bcp47Code)
            .map { it.translatedText.trim() }
    }
}
