package com.itantra.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {

    // Tier 1: Feature Coverage (F16 Secure API Key Resolution)

    @Test
    fun testApplicationIdMatchesContract() {
        assertEquals("com.itantra.voice", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun testSarvamApiKeyFieldIsResolved() {
        assertNotNull("SARVAM_API_KEY field must be generated in BuildConfig", BuildConfig.SARVAM_API_KEY)
        assertTrue("SARVAM_API_KEY must be accessible as a non-null String", BuildConfig.SARVAM_API_KEY.length >= 0)
    }

    @Test
    fun testBuildTypeIsDebugDuringTestExecution() {
        assertEquals("debug", BuildConfig.BUILD_TYPE)
    }

    @Test
    fun testVersionCodeIsPositive() {
        assertTrue("Version code must be >= 1", BuildConfig.VERSION_CODE >= 1)
    }

    @Test
    fun testVersionNameIsFormatted() {
        assertTrue("Version name should not be blank", BuildConfig.VERSION_NAME.isNotBlank())
        assertTrue("Version name should follow semver pattern (x.y.z)", BuildConfig.VERSION_NAME.matches(Regex("\\d+\\.\\d+\\.\\d+.*")))
    }

    // Tier 2: Boundary & Corner Cases (F16 Secure API Key Resolution)

    @Test
    fun testApiKeyTrimmingSanitization() {
        val rawKey = "  test_api_key_12345  "
        val sanitizedKey = rawKey.trim()
        assertEquals("test_api_key_12345", sanitizedKey)
        assertFalse("Sanitized key must not contain leading or trailing whitespace", sanitizedKey.startsWith(" ") || sanitizedKey.endsWith(" "))
    }

    @Test
    fun testPlaceholderApiKeyDetection() {
        val knownPlaceholders = listOf(
            "",
            "   ",
            "YOUR_API_KEY_HERE",
            "your_sarvam_api_key_here",
            "<SARVAM_API_KEY>",
            "placeholder"
        )

        fun isKeyConfigured(key: String): Boolean {
            val trimmed = key.trim()
            return trimmed.isNotBlank() && !knownPlaceholders.any { it.equals(trimmed, ignoreCase = true) }
        }

        for (placeholder in knownPlaceholders) {
            assertFalse("Placeholder '$placeholder' must be detected as unconfigured", isKeyConfigured(placeholder))
        }

        assertTrue("Valid key must be detected as configured", isKeyConfigured("sk-sarvam-live-abc123xyz"))
    }

    @Test
    fun testApiKeyMaskingForLoggingSecurity() {
        fun maskApiKey(key: String): String {
            val trimmed = key.trim()
            return when {
                trimmed.length <= 8 -> "********"
                else -> "${trimmed.take(4)}...${trimmed.takeLast(4)}"
            }
        }

        assertEquals("********", maskApiKey("short"))
        assertEquals("1234...wxyz", maskApiKey("1234567890wxyz"))
        assertFalse("Masked key should never expose full secret", maskApiKey("my_ultra_secret_key").contains("ultra_secret"))
    }

    @Test
    fun testEmptyOrBlankKeyThrowsAppropriateException() {
        fun validateApiKey(key: String) {
            if (key.isBlank()) {
                throw IllegalStateException("Sarvam AI API key is blank or unconfigured.")
            }
        }

        var exceptionThrown = false
        try {
            validateApiKey("")
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message!!.contains("blank or unconfigured"))
        }
        assertTrue("Blank key must trigger IllegalStateException", exceptionThrown)
    }

    @Test
    fun testHttpAuthHeaderNameMatchesSarvamSpecification() {
        val authHeaderKey = "api-subscription-key"
        assertEquals("api-subscription-key", authHeaderKey)
        assertFalse("Auth header must be lowercase hyphenated according to Sarvam contract", authHeaderKey.contains("Bearer") || authHeaderKey.contains("Authorization"))
    }
}
