package org.isro.itantra.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsConfigTest {

    @Test
    fun testSupportedLanguagesCountAndCodes() {
        val languages = SupportedLanguage.entries
        assertTrue("Must support at least 10 languages", languages.size >= 10)

        assertEquals("hi", SupportedLanguage.HINDI.code)
        assertEquals("mr", SupportedLanguage.MARATHI.code)
        assertEquals("en", SupportedLanguage.ENGLISH.code)
        assertEquals("bn", SupportedLanguage.BENGALI.code)
        assertEquals("ta", SupportedLanguage.TAMIL.code)
        assertEquals("te", SupportedLanguage.TELUGU.code)
    }

    @Test
    fun testFromCodeResolution() {
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("hi"))
        assertEquals(SupportedLanguage.MARATHI, SupportedLanguage.fromCode("MR"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("en"))
        // Unknown defaults to Hindi
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("unknown_xyz"))
    }

    @Test
    fun testSampleRates() {
        assertEquals(22050, SupportedLanguage.HINDI.sampleRate)
        assertEquals(22050, SupportedLanguage.MARATHI.sampleRate)
        assertEquals(22050, SupportedLanguage.ENGLISH.sampleRate)
        assertEquals(16000, SupportedLanguage.TAMIL.sampleRate)
    }

    @Test
    fun testEmergencyPresets() {
        val presets = EmergencyPreset.PRESETS
        assertEquals(3, presets.size)

        val cyclone = presets.find { it.id == "cyclone_warning" }
        assertNotNull(cyclone)
        assertTrue(cyclone!!.getTextFor(SupportedLanguage.HINDI).contains("चक्रवात"))
        assertTrue(cyclone.getTextFor(SupportedLanguage.ENGLISH).contains("Cyclone Warning"))
        assertTrue(cyclone.getTextFor(SupportedLanguage.MARATHI).contains("चक्रीवादळ"))

        val tsunami = presets.find { it.id == "tsunami_alert" }
        assertNotNull(tsunami)
        assertTrue(tsunami!!.getTextFor(SupportedLanguage.HINDI).contains("सुनामी"))

        val evacuate = presets.find { it.id == "evacuate_coastal" }
        assertNotNull(evacuate)
        assertTrue(evacuate!!.getTextFor(SupportedLanguage.ENGLISH).contains("Evacuate"))
    }
}
