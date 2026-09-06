package com.example.itantra.language

import com.example.itantra.language.normalizers.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ModelRegistry, SupportedLanguage, and linguistic normalizers.
 * Validates complete coverage of all 10 Indian languages mandated by SIH26173 (ISRO).
 */
class ModelRegistryTest {

    @Test
    fun testAllTenMandatedLanguagesPresent() {
        val languages = SupportedLanguage.entries
        assertEquals("Must support exactly 10 SIH mandated languages", 10, languages.size)

        val expected = listOf(
            SupportedLanguage.HINDI,
            SupportedLanguage.BENGALI,
            SupportedLanguage.TAMIL,
            SupportedLanguage.TELUGU,
            SupportedLanguage.KANNADA,
            SupportedLanguage.MALAYALAM,
            SupportedLanguage.MARATHI,
            SupportedLanguage.GUJARATI,
            SupportedLanguage.ODIA,
            SupportedLanguage.ENGLISH
        )
        assertTrue(languages.containsAll(expected))
    }

    @Test
    fun testEveryLanguageHasCompleteProfileInRegistry() {
        for (lang in SupportedLanguage.entries) {
            val profile = ModelRegistry.getProfile(lang)
            assertEquals("Profile language must match key", lang, profile.language)

            // STT Config verification
            val stt = profile.sttConfig
            assertTrue("STT encoder path for ${lang.englishName} must not be blank", stt.encoderPath.isNotBlank())
            assertTrue("STT decoder path for ${lang.englishName} must not be blank", stt.decoderPath.isNotBlank())
            assertTrue("STT joiner path for ${lang.englishName} must not be blank", stt.joinerPath.isNotBlank())
            assertTrue("STT tokens path for ${lang.englishName} must not be blank", stt.tokensPath.isNotBlank())
            assertEquals(16000, stt.sampleRate)

            // TTS Config verification
            val tts = profile.ttsConfig
            assertTrue("TTS model path for ${lang.englishName} must not be blank", tts.modelPath.isNotBlank())
            assertTrue("TTS config path for ${lang.englishName} must not be blank", tts.configPath.isNotBlank())
            assertTrue("TTS espeakData path for ${lang.englishName} must not be blank", tts.espeakDataPath.isNotBlank())
            assertTrue("TTS tokens path for ${lang.englishName} must not be blank", tts.tokensPath.isNotBlank())
            assertEquals(16000, tts.sampleRate)

            // Normalizer and Sample Transcript verification
            assertNotNull("Normalizer for ${lang.englishName} must not be null", profile.normalizer)
            assertTrue("Sample transcript for ${lang.englishName} must not be blank", profile.sampleTranscript.isNotBlank())
        }
    }

    @Test
    fun testEnglishBundledModelStatus() {
        assertTrue("English STT model must be bundled in APK assets", ModelRegistry.isSttBundled(SupportedLanguage.ENGLISH))
        assertTrue("English TTS model must be bundled in APK assets", ModelRegistry.isTtsBundled(SupportedLanguage.ENGLISH))

        // Non-English models are staged for dynamic download or local loading
        assertFalse(ModelRegistry.isSttBundled(SupportedLanguage.HINDI))
        assertFalse(ModelRegistry.isTtsBundled(SupportedLanguage.HINDI))
    }

    @Test
    fun testSupportedLanguageFromCode() {
        // Test ISO 639-1 resolution
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("hi"))
        assertEquals(SupportedLanguage.BENGALI, SupportedLanguage.fromCode("bn"))
        assertEquals(SupportedLanguage.TAMIL, SupportedLanguage.fromCode("ta"))
        assertEquals(SupportedLanguage.TELUGU, SupportedLanguage.fromCode("te"))
        assertEquals(SupportedLanguage.KANNADA, SupportedLanguage.fromCode("kn"))
        assertEquals(SupportedLanguage.MALAYALAM, SupportedLanguage.fromCode("ml"))
        assertEquals(SupportedLanguage.MARATHI, SupportedLanguage.fromCode("mr"))
        assertEquals(SupportedLanguage.GUJARATI, SupportedLanguage.fromCode("gu"))
        assertEquals(SupportedLanguage.ODIA, SupportedLanguage.fromCode("or"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("en"))

        // Test BCP-47 tag resolution
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("hi-IN"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("en-IN"))
        assertEquals(SupportedLanguage.TAMIL, SupportedLanguage.fromCode("ta-IN"))

        // Test case insensitivity and whitespace trimming
        assertEquals(SupportedLanguage.BENGALI, SupportedLanguage.fromCode("  BN  "))

        // Test fallback for null or unknown code
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode(null))
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("unknown"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("unknown", SupportedLanguage.ENGLISH))
    }

    @Test
    fun testEnglishNormalization() {
        val normalizer = EnglishTextNormalizer()
        val input = "  Wind speed 120 km/h , evacuate in 15 min . Call Dr. Smith   sos  "
        val output = normalizer.normalize(input)

        assertTrue(output.contains("120 kilometers per hour"))
        assertTrue(output.contains("15 minutes"))
        assertTrue(output.contains("doctor"))
        assertTrue(output.contains("S-O-S"))
        assertFalse(output.contains("  ")) // No double whitespace
    }

    @Test
    fun testDevanagariHindiNormalization() {
        val normalizer = DevanagariTextNormalizer(isMarathi = false)
        val input = "चक्रवात चेतावनी ॥ तुरंत सुरक्षित स्थान पर जाएं \u200B \u200E"
        val output = normalizer.normalize(input)

        assertTrue("Double danda ॥ must be normalized to single danda ।", output.contains("।"))
        assertFalse("Must not contain double danda ॥", output.contains("॥"))
        assertFalse("Must not contain zero-width space", output.contains("\u200B"))
        assertFalse("Must not contain LTR mark", output.contains("\u200E"))
    }

    @Test
    fun testMarathiNormalization() {
        val normalizer = DevanagariTextNormalizer(isMarathi = true)
        val input = "चक्रीवादळाचा इशारा ॥ \u200B त्वरित सुरक्षित स्थळी जा"
        val output = normalizer.normalize(input)

        assertTrue(output.contains("।"))
        assertFalse(output.contains("॥"))
        assertFalse(output.contains("\u200B"))
    }

    @Test
    fun testBengaliNormalization() {
        val normalizer = BengaliTextNormalizer()
        val input = "ঘূর্ণিঝড় সতর্কতা ॥ \u200B অবিলম্বে নিরাপদ স্থানে যান"
        val output = normalizer.normalize(input)

        assertTrue(output.contains("।"))
        assertFalse(output.contains("॥"))
        assertFalse(output.contains("\u200B"))
    }

    @Test
    fun testDravidianLanguagesNormalization() {
        val tamilNormalizer = TamilTextNormalizer()
        val tamilInput = "புயல் \u200B\u200C எச்சரிக்கை \u200D"
        val tamilOutput = tamilNormalizer.normalize(tamilInput)
        assertEquals("புயல் எச்சரிக்கை", tamilOutput)

        val teluguNormalizer = TeluguTextNormalizer()
        val teluguInput = "తుఫాను \u200B\u200C హెచ్చరిక \u200D"
        val teluguOutput = teluguNormalizer.normalize(teluguInput)
        assertEquals("తుఫాను హెచ్చరిక", teluguOutput)

        val kannadaNormalizer = KannadaTextNormalizer()
        val kannadaInput = "ಚಂಡಮಾರುತದ \u200B ಎಚ್ಚರಿಕೆ \u200C"
        val kannadaOutput = kannadaNormalizer.normalize(kannadaInput)
        assertEquals("ಚಂಡಮಾರುತದ ಎಚ್ಚರಿಕೆ", kannadaOutput)

        val malayalamNormalizer = MalayalamTextNormalizer()
        val malayalamInput = "ചുഴലിക്കാറ്റ് \u200B മുന്നറിയിപ്പ് \u200C"
        val malayalamOutput = malayalamNormalizer.normalize(malayalamInput)
        assertEquals("ചുഴലിക്കാറ്റ് മുന്നറിയിപ്പ്", malayalamOutput)
    }

    @Test
    fun testWesternAndEasternLanguagesNormalization() {
        val gujaratiNormalizer = GujaratiTextNormalizer()
        val gujInput = "વાવાઝોડાની \u200B ચેતવણી \u200C"
        val gujOutput = gujaratiNormalizer.normalize(gujInput)
        assertEquals("વાવાઝોડાની ચેતવણી", gujOutput)

        val odiaNormalizer = OdiaTextNormalizer()
        val odiaInput = "ବାତ୍ୟା ଚେତାବନୀ ॥ \u200B"
        val odiaOutput = odiaNormalizer.normalize(odiaInput)
        assertTrue(odiaOutput.contains("।"))
        assertFalse(odiaOutput.contains("॥"))
        assertFalse(odiaOutput.contains("\u200B"))
    }

    @Test
    fun testModelRegistryDelegatedNormalize() {
        val rawHindi = "सावधान ॥ \u200B"
        val normalizedHindi = ModelRegistry.normalize(rawHindi, SupportedLanguage.HINDI)
        assertEquals("सावधान ।", normalizedHindi)

        val rawEnglish = "  100 km/h  "
        val normalizedEnglish = ModelRegistry.normalize(rawEnglish, SupportedLanguage.ENGLISH)
        assertEquals("100 kilometers per hour", normalizedEnglish)
    }

    @Test
    fun testSampleTranscriptsMatchNativeScripts() {
        assertEquals("चक्रवात चेतावनी तुरंत सुरक्षित स्थान पर जाएं", ModelRegistry.getSampleTranscript(SupportedLanguage.HINDI))
        assertEquals("ঘূর্ণিঝড় সতর্কতা অবিলম্বে নিরাপদ স্থানে যান", ModelRegistry.getSampleTranscript(SupportedLanguage.BENGALI))
        assertEquals("புயல் எச்சரிக்கை உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும்", ModelRegistry.getSampleTranscript(SupportedLanguage.TAMIL))
        assertEquals("తుఫాను హెచ్చరిక వెంటనే సురక్షిత ప్రాంతానికి వెళ్లండి", ModelRegistry.getSampleTranscript(SupportedLanguage.TELUGU))
        assertEquals("ಚಂಡಮಾರುತದ ಎಚ್ಚರಿಕೆ ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ", ModelRegistry.getSampleTranscript(SupportedLanguage.KANNADA))
        assertEquals("ചുഴലിക്കാറ്റ് മുന്നറിയിപ്പ് ഉടൻ സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക", ModelRegistry.getSampleTranscript(SupportedLanguage.MALAYALAM))
        assertEquals("चक्रीवादळाचा इशारा त्वरित सुरक्षित स्थळी जा", ModelRegistry.getSampleTranscript(SupportedLanguage.MARATHI))
        assertEquals("વાવાઝોડાની ચેતવણી તરત જ સુરક્ષિત સ્થળે જાઓ", ModelRegistry.getSampleTranscript(SupportedLanguage.GUJARATI))
        assertEquals("ବାତ୍ୟା ଚେତାବନୀ ତୁରନ୍ତ ନିରାପଦ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ", ModelRegistry.getSampleTranscript(SupportedLanguage.ODIA))
        assertEquals("Cyclone alert evacuate to high ground immediately", ModelRegistry.getSampleTranscript(SupportedLanguage.ENGLISH))
    }
}
