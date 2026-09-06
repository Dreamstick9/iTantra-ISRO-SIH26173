package com.example.itantra.language

import com.example.itantra.language.normalizers.*

/**
 * ModelRegistry:
 * Central repository of STT models, TTS models, and linguistic normalization rules
 * across all 10 SupportedLanguages mandated by SIH26173 (ISRO).
 *
 * Eliminates sprawling "if (language == ...)" branches throughout the application.
 */
object ModelRegistry {

    private val profiles: Map<SupportedLanguage, LanguageProfile> = mapOf(
        SupportedLanguage.ENGLISH to LanguageProfile(
            language = SupportedLanguage.ENGLISH,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-small-en-2023-06-26/encoder-epoch-99-avg-1.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-small-en-2023-06-26/decoder-epoch-99-avg-1.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-small-en-2023-06-26/joiner-epoch-99-avg-1.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-small-en-2023-06-26/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = true
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-en_US-amy-low/en_US-amy-low.onnx",
                configPath = "vits-piper-en_US-amy-low/en_US-amy-low.onnx.json",
                espeakDataPath = "vits-piper-en_US-amy-low/espeak-ng-data",
                tokensPath = "vits-piper-en_US-amy-low/tokens.txt",
                sampleRate = 16000,
                isBundled = true
            ),
            normalizer = EnglishTextNormalizer(),
            sampleTranscript = "Cyclone alert evacuate to high ground immediately"
        ),

        SupportedLanguage.HINDI to LanguageProfile(
            language = SupportedLanguage.HINDI,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-hi/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-hi/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-hi/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-hi/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-hi/hi.onnx",
                configPath = "vits-piper-hi/hi.onnx.json",
                espeakDataPath = "vits-piper-hi/espeak-ng-data",
                tokensPath = "vits-piper-hi/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = DevanagariTextNormalizer(isMarathi = false),
            sampleTranscript = "चक्रवात चेतावनी तुरंत सुरक्षित स्थान पर जाएं"
        ),

        SupportedLanguage.BENGALI to LanguageProfile(
            language = SupportedLanguage.BENGALI,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-bn/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-bn/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-bn/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-bn/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-bn/bn.onnx",
                configPath = "vits-piper-bn/bn.onnx.json",
                espeakDataPath = "vits-piper-bn/espeak-ng-data",
                tokensPath = "vits-piper-bn/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = BengaliTextNormalizer(),
            sampleTranscript = "ঘূর্ণিঝড় সতর্কতা অবিলম্বে নিরাপদ স্থানে যান"
        ),

        SupportedLanguage.TAMIL to LanguageProfile(
            language = SupportedLanguage.TAMIL,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-ta/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-ta/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-ta/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-ta/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-ta/ta.onnx",
                configPath = "vits-piper-ta/ta.onnx.json",
                espeakDataPath = "vits-piper-ta/espeak-ng-data",
                tokensPath = "vits-piper-ta/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = TamilTextNormalizer(),
            sampleTranscript = "புயல் எச்சரிக்கை உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும்"
        ),

        SupportedLanguage.TELUGU to LanguageProfile(
            language = SupportedLanguage.TELUGU,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-te/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-te/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-te/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-te/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-te/te.onnx",
                configPath = "vits-piper-te/te.onnx.json",
                espeakDataPath = "vits-piper-te/espeak-ng-data",
                tokensPath = "vits-piper-te/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = TeluguTextNormalizer(),
            sampleTranscript = "తుఫాను హెచ్చరిక వెంటనే సురక్షిత ప్రాంతానికి వెళ్లండి"
        ),

        SupportedLanguage.KANNADA to LanguageProfile(
            language = SupportedLanguage.KANNADA,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-kn/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-kn/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-kn/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-kn/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-kn/kn.onnx",
                configPath = "vits-piper-kn/kn.onnx.json",
                espeakDataPath = "vits-piper-kn/espeak-ng-data",
                tokensPath = "vits-piper-kn/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = KannadaTextNormalizer(),
            sampleTranscript = "ಚಂಡಮಾರುತದ ಎಚ್ಚರಿಕೆ ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ"
        ),

        SupportedLanguage.MALAYALAM to LanguageProfile(
            language = SupportedLanguage.MALAYALAM,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-ml/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-ml/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-ml/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-ml/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-ml/ml.onnx",
                configPath = "vits-piper-ml/ml.onnx.json",
                espeakDataPath = "vits-piper-ml/espeak-ng-data",
                tokensPath = "vits-piper-ml/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = MalayalamTextNormalizer(),
            sampleTranscript = "ചുഴലിക്കാറ്റ് മുന്നറിയിപ്പ് ഉടൻ സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക"
        ),

        SupportedLanguage.MARATHI to LanguageProfile(
            language = SupportedLanguage.MARATHI,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-mr/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-mr/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-mr/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-mr/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-mr/mr.onnx",
                configPath = "vits-piper-mr/mr.onnx.json",
                espeakDataPath = "vits-piper-mr/espeak-ng-data",
                tokensPath = "vits-piper-mr/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = DevanagariTextNormalizer(isMarathi = true),
            sampleTranscript = "चक्रीवादळाचा इशारा त्वरित सुरक्षित स्थळी जा"
        ),

        SupportedLanguage.GUJARATI to LanguageProfile(
            language = SupportedLanguage.GUJARATI,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-gu/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-gu/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-gu/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-gu/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-gu/gu.onnx",
                configPath = "vits-piper-gu/gu.onnx.json",
                espeakDataPath = "vits-piper-gu/espeak-ng-data",
                tokensPath = "vits-piper-gu/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = GujaratiTextNormalizer(),
            sampleTranscript = "વાવાઝોડાની ચેતવણી તરત જ સુરક્ષિત સ્થળે જાઓ"
        ),

        SupportedLanguage.ODIA to LanguageProfile(
            language = SupportedLanguage.ODIA,
            sttConfig = SttModelConfig(
                encoderPath = "sherpa-onnx-zipformer-or/encoder.int8.onnx",
                decoderPath = "sherpa-onnx-zipformer-or/decoder.int8.onnx",
                joinerPath = "sherpa-onnx-zipformer-or/joiner.int8.onnx",
                tokensPath = "sherpa-onnx-zipformer-or/tokens.txt",
                modelType = "zipformer",
                sampleRate = 16000,
                isBundled = false
            ),
            ttsConfig = TtsModelConfig(
                modelPath = "vits-piper-or/or.onnx",
                configPath = "vits-piper-or/or.onnx.json",
                espeakDataPath = "vits-piper-or/espeak-ng-data",
                tokensPath = "vits-piper-or/tokens.txt",
                sampleRate = 16000,
                isBundled = false
            ),
            normalizer = OdiaTextNormalizer(),
            sampleTranscript = "ବାତ୍ୟା ଚେତାବନୀ ତୁରନ୍ତ ନିରାପଦ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ"
        )
    )

    fun getProfile(language: SupportedLanguage): LanguageProfile = profiles.getValue(language)

    fun getSttConfig(language: SupportedLanguage): SttModelConfig = getProfile(language).sttConfig

    fun getTtsConfig(language: SupportedLanguage): TtsModelConfig = getProfile(language).ttsConfig

    fun getNormalizer(language: SupportedLanguage): TextNormalizer = getProfile(language).normalizer

    fun normalize(text: String, language: SupportedLanguage): String = getNormalizer(language).normalize(text)

    fun getSampleTranscript(language: SupportedLanguage): String = getProfile(language).sampleTranscript

    fun isSttBundled(language: SupportedLanguage): Boolean = getSttConfig(language).isBundled

    fun isTtsBundled(language: SupportedLanguage): Boolean = getTtsConfig(language).isBundled

    fun getSupportedLanguages(): List<SupportedLanguage> = SupportedLanguage.entries
}
