package com.itantra.voice

import com.itantra.voice.data.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class LanguageEdgeCaseStressTest {

    @Test
    fun testCasingDifferences() {
        // HI-in with mixed uppercase and lowercase
        val hindiMixed = Language.fromBcp47("HI-in")
        assertEquals(Language.HINDI, hindiMixed)

        // Lowercase hi-in
        val hindiLower = Language.fromBcp47("hi-in")
        assertEquals(Language.HINDI, hindiLower)

        // Uppercase HI-IN
        val hindiUpper = Language.fromBcp47("HI-IN")
        assertEquals(Language.HINDI, hindiUpper)

        // Inverted casing hI-In
        val hindiInverted = Language.fromBcp47("hI-In")
        assertEquals(Language.HINDI, hindiInverted)
    }

    @Test
    fun testUnderscoreVsHyphenEdgeCase() {
        // POSIX / Java Locale.toString() format uses underscore (e.g., "en_IN", "en_in")
        // BCP-47 standard uses hyphen (e.g., "en-IN")
        val enWithUnderscoreLower = Language.fromBcp47("en_in")
        val enWithUnderscoreUpper = Language.fromBcp47("en_IN")

        assertEquals(Language.ENGLISH, enWithUnderscoreLower)
        assertEquals(Language.ENGLISH, enWithUnderscoreUpper)
    }

    @Test
    fun testUntrimmedWhitespaceEdgeCase() {
        // Untrimmed input strings
        val enWithSpaces = Language.fromBcp47(" en-IN ")
        val hiWithTab = Language.fromBcp47("\thi-IN\n")

        assertEquals(Language.ENGLISH, enWithSpaces)
        assertEquals(Language.HINDI, hiWithTab)
    }

    @Test
    fun testBaseLanguageCodesWithoutRegionSubtag() {
        // Bare ISO 639-1 language code without region
        assertNull("Bare 'hi' returns null", Language.fromBcp47("hi"))
        assertNull("Bare 'en' returns null", Language.fromBcp47("en"))
        assertNull("Bare 'ta' returns null", Language.fromBcp47("ta"))
    }

    @Test
    fun testUnknownBcp47Codes() {
        assertNull(Language.fromBcp47("fr-FR"))
        assertNull(Language.fromBcp47("es-ES"))
        assertNull(Language.fromBcp47("zh-CN"))
        assertNull(Language.fromBcp47("de-DE"))
        assertNull(Language.fromBcp47("ja-JP"))
        assertNull(Language.fromBcp47("ar-SA"))
        assertNull(Language.fromBcp47("invalid_tag"))
        assertNull(Language.fromBcp47("12345"))
    }

    @Test
    fun testNullSafetyViaReflection() {
        // Test runtime behavior when null is passed from Java or reflection
        val companion = Language.Companion
        val method = companion::class.java.getMethod("fromBcp47", String::class.java)

        try {
            method.invoke(companion, null as String?)
            fail("Expected InvocationTargetException wrapping NullPointerException for null parameter")
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            assertNotNull(cause)
            assertTrue("Cause should be NullPointerException due to Kotlin Intrinsics", cause is NullPointerException)
            assertTrue(cause?.message?.contains("Parameter specified as non-null is null") == true)
        }
    }
}
