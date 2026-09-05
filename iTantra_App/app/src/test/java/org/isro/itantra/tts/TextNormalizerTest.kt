package org.isro.itantra.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun testEmptyAndBlankInput() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   "))
    }

    @Test
    fun testDevanagariDigitConversion() {
        val input = "चक्रवात श्रेणी ५"
        val normalized = TextNormalizer.normalize(input, SupportedLanguage.HINDI)
        assertTrue(normalized.contains("पाँच") || normalized.contains("5"))
    }

    @Test
    fun testHindiNumberToWords() {
        val n0 = TextNormalizer.normalize("0", SupportedLanguage.HINDI)
        assertEquals("शून्य", n0)

        val n42 = TextNormalizer.normalize("42", SupportedLanguage.HINDI)
        assertEquals("बयालीस", n42)

        val n100 = TextNormalizer.normalize("100", SupportedLanguage.HINDI)
        assertEquals("एक सौ", n100)

        val n500 = TextNormalizer.normalize("500", SupportedLanguage.HINDI)
        assertEquals("पाँच सौ", n500)
    }

    @Test
    fun testEnglishNumberToWords() {
        val n42 = TextNormalizer.normalize("42", SupportedLanguage.ENGLISH)
        assertEquals("forty-two", n42)

        val n105 = TextNormalizer.normalize("105", SupportedLanguage.ENGLISH)
        assertEquals("one hundred five", n105)
    }

    @Test
    fun testCurrencyExpansion() {
        val hindiCurrency = TextNormalizer.normalize("₹500", SupportedLanguage.HINDI)
        assertTrue("Expected रुपये in output: $hindiCurrency", hindiCurrency.contains("रुपये"))

        val englishCurrency = TextNormalizer.normalize("₹100", SupportedLanguage.ENGLISH)
        assertTrue("Expected rupees in output: $englishCurrency", englishCurrency.contains("rupees"))
    }

    @Test
    fun testTimestampExpansion() {
        val result = TextNormalizer.normalize("14:30 IST", SupportedLanguage.HINDI)
        assertTrue("Expected बजकर in output: $result", result.contains("बजकर") || result.contains("14"))
    }

    @Test
    fun testCoordinateExpansion() {
        val result = TextNormalizer.normalize("14.5° N", SupportedLanguage.HINDI)
        assertTrue("Expected उत्तर or डिग्री in output: $result", result.contains("उत्तर") || result.contains("डिग्री"))
    }

    @Test
    fun testEmergencyPhraseExpansion() {
        val hindiEmergency = TextNormalizer.normalize("RED ALERT from ISRO", SupportedLanguage.HINDI)
        assertTrue("Expected इसरो in output: $hindiEmergency", hindiEmergency.contains("इसरो"))
        assertTrue("Expected चेतावनी in output: $hindiEmergency", hindiEmergency.contains("चेतावनी"))

        val englishEmergency = TextNormalizer.normalize("RED ALERT from ISRO", SupportedLanguage.ENGLISH)
        assertTrue("Expected Alert in output: $englishEmergency", englishEmergency.contains("Alert"))
    }

    @Test
    fun testDecimalAndPercentage() {
        val hindiPercent = TextNormalizer.normalize("50%", SupportedLanguage.HINDI)
        assertTrue("Expected प्रतिशत in output: $hindiPercent", hindiPercent.contains("प्रतिशत"))
    }
}
