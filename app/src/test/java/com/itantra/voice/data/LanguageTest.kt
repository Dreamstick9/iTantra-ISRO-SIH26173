package com.itantra.voice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageTest {

    // Tier 1 Tests (Feature Coverage for F11)

    @Test
    fun testTenLanguagesPresent() {
        assertEquals(10, Language.entries.size)
    }

    @Test
    fun testDefaultLanguages() {
        assertEquals(Language.HINDI, Language.DEFAULT_SOURCE)
        assertEquals(Language.ENGLISH, Language.DEFAULT_TARGET)
        assertEquals("hi-IN", Language.DEFAULT_SOURCE.bcp47Code)
        assertEquals("en-IN", Language.DEFAULT_TARGET.bcp47Code)
    }

    @Test
    fun testBcp47CodeMappings() {
        val expected = mapOf(
            Language.ENGLISH to "en-IN",
            Language.HINDI to "hi-IN",
            Language.BENGALI to "bn-IN",
            Language.TAMIL to "ta-IN",
            Language.TELUGU to "te-IN",
            Language.KANNADA to "kn-IN",
            Language.MALAYALAM to "ml-IN",
            Language.MARATHI to "mr-IN",
            Language.GUJARATI to "gu-IN",
            Language.ODIA to "od-IN"
        )

        for ((language, code) in expected) {
            assertEquals("Language $language should have code $code", code, language.bcp47Code)
            assertEquals("Lookup by code $code should return $language", language, Language.fromBcp47(code))
        }
    }

    @Test
    fun testDisplayNamesAreProperlyFormatted() {
        for (language in Language.entries) {
            assertTrue("Display name should not be blank for $language", language.displayName.isNotBlank())
            assertTrue("Display name should start with uppercase: ${language.displayName}", language.displayName[0].isUpperCase())
        }
    }

    @Test
    fun testMilestoneLanguagePairsExist() {
        // Milestone 1: Hindi -> English
        val m1Source = Language.fromBcp47("hi-IN")
        val m1Target = Language.fromBcp47("en-IN")
        assertNotNull(m1Source)
        assertNotNull(m1Target)
        assertNotEquals(m1Source, m1Target)

        // Milestone 2: English -> Marathi
        val m2Source = Language.fromBcp47("en-IN")
        val m2Target = Language.fromBcp47("mr-IN")
        assertNotNull(m2Source)
        assertNotNull(m2Target)
        assertNotEquals(m2Source, m2Target)

        // Milestone 3: Tamil -> Hindi
        val m3Source = Language.fromBcp47("ta-IN")
        val m3Target = Language.fromBcp47("hi-IN")
        assertNotNull(m3Source)
        assertNotNull(m3Target)
        assertNotEquals(m3Source, m3Target)
    }

    // Tier 2 Tests (Boundary & Corner Cases for F11)

    @Test
    fun testInvalidBcp47ReturnsNull() {
        assertNull(Language.fromBcp47("invalid-code"))
        assertNull(Language.fromBcp47(""))
        assertNull(Language.fromBcp47("   "))
        assertNull(Language.fromBcp47("fr-FR"))
        assertNull(Language.fromBcp47("zh-CN"))
    }

    @Test
    fun testCaseInsensitiveBcp47Lookup() {
        assertEquals(Language.HINDI, Language.fromBcp47("HI-IN"))
        assertEquals(Language.HINDI, Language.fromBcp47("hi-in"))
        assertEquals(Language.TAMIL, Language.fromBcp47("TA-in"))
        assertEquals(Language.BENGALI, Language.fromBcp47("Bn-In"))
    }

    @Test
    fun testAllBcp47CodesAreUnique() {
        val codes = Language.entries.map { it.bcp47Code }
        val uniqueCodes = codes.toSet()
        assertEquals("Every language must have a unique BCP-47 code", codes.size, uniqueCodes.size)
    }

    @Test
    fun testNativeNamesContainIndicScripts() {
        val indicLanguages = listOf(
            Language.HINDI to "हिन्दी",
            Language.BENGALI to "বাংলা",
            Language.TAMIL to "தமிழ்",
            Language.TELUGU to "తెలుగు",
            Language.KANNADA to "ಕನ್ನಡ",
            Language.MALAYALAM to "മലയാളം",
            Language.MARATHI to "मराठी",
            Language.GUJARATI to "ગુજરાતી",
            Language.ODIA to "ଓଡ଼ିଆ"
        )

        for ((language, expectedNative) in indicLanguages) {
            assertEquals("Native name for $language must match exact script string", expectedNative, language.nativeName)
            // Assert that characters are outside pure ASCII range (> 127)
            assertTrue("Native name for $language must contain Indic characters", language.nativeName.any { it.code > 127 })
        }
    }

    @Test
    fun testAllLanguagesHaveStandardIndiaSubtag() {
        for (language in Language.entries) {
            assertTrue("BCP-47 tag for $language must end with '-IN'", language.bcp47Code.endsWith("-IN"))
            assertEquals("BCP-47 tag length must be 5 (e.g. xx-IN)", 5, language.bcp47Code.length)
        }
    }
}
