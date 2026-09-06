package com.example.itantra.language

/**
 * 10 Indian Languages explicitly mandated by SIH26173 (ISRO).
 */
enum class SupportedLanguage(
    val isoCode: String,
    val bcp47Tag: String,
    val englishName: String,
    val nativeName: String,
    val scriptName: String
) {
    HINDI("hi", "hi-IN", "Hindi", "हिन्दी", "Devanagari"),
    BENGALI("bn", "bn-IN", "Bengali", "বাংলা", "Bengali"),
    TAMIL("ta", "ta-IN", "Tamil", "தமிழ்", "Tamil"),
    TELUGU("te", "te-IN", "Telugu", "తెలుగు", "Telugu"),
    KANNADA("kn", "kn-IN", "Kannada", "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", "ml-IN", "Malayalam", "മലയാളം", "Malayalam"),
    MARATHI("mr", "mr-IN", "Marathi", "मराठी", "Devanagari"),
    GUJARATI("gu", "gu-IN", "Gujarati", "ગુજરાતી", "Gujarati"),
    ODIA("or", "or-IN", "Odia", "ଓଡ଼ିଆ", "Odia"),
    ENGLISH("en", "en-IN", "English", "English", "Latin");

    companion object {
        val DEFAULT: SupportedLanguage = HINDI

        fun fromCode(code: String?, fallback: SupportedLanguage = HINDI): SupportedLanguage {
            if (code.isNullOrBlank()) return fallback
            val cleaned = code.trim().lowercase()
            return entries.firstOrNull {
                it.isoCode.equals(cleaned, ignoreCase = true) ||
                it.bcp47Tag.equals(cleaned, ignoreCase = true) ||
                cleaned.startsWith(it.isoCode)
            } ?: fallback
        }
    }
}
