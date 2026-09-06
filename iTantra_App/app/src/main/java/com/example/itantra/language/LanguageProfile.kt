package com.example.itantra.language

/**
 * Text normalizer contract for script-specific linguistic normalization.
 */
fun interface TextNormalizer {
    fun normalize(text: String): String
}

/**
 * Configuration for an offline Speech-To-Text (STT) model.
 */
data class SttModelConfig(
    val encoderPath: String,
    val decoderPath: String,
    val joinerPath: String,
    val tokensPath: String,
    val modelType: String = "zipformer",
    val sampleRate: Int = 16000,
    val isBundled: Boolean = false
)

/**
 * Configuration for an offline Text-To-Speech (TTS) model.
 */
data class TtsModelConfig(
    val modelPath: String,
    val configPath: String,
    val espeakDataPath: String,
    val tokensPath: String,
    val sampleRate: Int = 16000,
    val isBundled: Boolean = false
)

/**
 * Complete language profile associating a SupportedLanguage with its
 * STT model, TTS model, text normalizer, and sample advisory text.
 */
data class LanguageProfile(
    val language: SupportedLanguage,
    val sttConfig: SttModelConfig,
    val ttsConfig: TtsModelConfig,
    val normalizer: TextNormalizer,
    val sampleTranscript: String
)
