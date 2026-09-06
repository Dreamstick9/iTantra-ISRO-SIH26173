package org.isro.itantra.stt

import org.isro.itantra.tts.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttConfigTest {

    @Test
    fun testLocaleTags_allSupportedLanguagesMappedCorrectly() {
        assertEquals("hi-IN", SttConfig.getLocaleTag(SupportedLanguage.HINDI))
        assertEquals("mr-IN", SttConfig.getLocaleTag(SupportedLanguage.MARATHI))
        assertEquals("bn-IN", SttConfig.getLocaleTag(SupportedLanguage.BENGALI))
        assertEquals("ta-IN", SttConfig.getLocaleTag(SupportedLanguage.TAMIL))
        assertEquals("te-IN", SttConfig.getLocaleTag(SupportedLanguage.TELUGU))
        assertEquals("kn-IN", SttConfig.getLocaleTag(SupportedLanguage.KANNADA))
        assertEquals("ml-IN", SttConfig.getLocaleTag(SupportedLanguage.MALAYALAM))
        assertEquals("gu-IN", SttConfig.getLocaleTag(SupportedLanguage.GUJARATI))
        assertEquals("or-IN", SttConfig.getLocaleTag(SupportedLanguage.ODIA))
        assertEquals("en-IN", SttConfig.getLocaleTag(SupportedLanguage.ENGLISH))
    }

    @Test
    fun testNavicSubframeBudget_27BytesLimit() {
        // ASCII short message <= 27 bytes
        val shortAscii = "EVACUATE NOW"
        val bytes = SttConfig.calculateUtf8Bytes(shortAscii)
        assertTrue(bytes <= SttConfig.NAVIC_SUBFRAME_MAX_BYTES)
        assertTrue(SttConfig.isSubframeCompliant(shortAscii))

        // Exact 27 bytes
        val exact27 = "A".repeat(27)
        assertEquals(27, SttConfig.calculateUtf8Bytes(exact27))
        assertTrue(SttConfig.isSubframeCompliant(exact27))

        // 28 bytes exceeds subframe
        val exceed28 = "A".repeat(28)
        assertEquals(28, SttConfig.calculateUtf8Bytes(exceed28))
        assertFalse(SttConfig.isSubframeCompliant(exceed28))
        assertTrue(SttConfig.isPacketCompliant(exceed28))
    }

    @Test
    fun testNavicPacketBudget_277BytesLimit() {
        val longMessage = "Cyclone Alert! Emergency shelter open at primary school. Move to high ground immediately. First aid and water supplies available on site."
        val bytes = SttConfig.calculateUtf8Bytes(longMessage)
        assertTrue(bytes > SttConfig.NAVIC_SUBFRAME_MAX_BYTES)
        assertTrue(bytes <= SttConfig.NAVIC_PACKET_MAX_BYTES)
        assertTrue(SttConfig.isPacketCompliant(longMessage))

        val exceedPacket = "A".repeat(278)
        assertFalse(SttConfig.isPacketCompliant(exceedPacket))
    }

    @Test
    fun testFallbackDisasterPhrases_presentForAllLanguages() {
        SupportedLanguage.values().forEach { lang ->
            val phrases = SttConfig.getFallbackDisasterPhrases(lang)
            assertTrue("Language ${lang.name} must have fallback disaster phrases", phrases.isNotEmpty())
            phrases.forEach { phrase ->
                assertTrue("Disaster phrase cannot be empty", phrase.isNotBlank())
            }
        }
    }
}
