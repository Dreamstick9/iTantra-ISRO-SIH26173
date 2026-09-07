package com.itantra.voice.data

enum class Language(
    val displayName: String,
    val nativeName: String,
    val bcp47Code: String
) {
    ENGLISH("English", "English", "en-IN"),
    HINDI("Hindi", "हिन्दी", "hi-IN"),
    BENGALI("Bengali", "বাংলা", "bn-IN"),
    TAMIL("Tamil", "தமிழ்", "ta-IN"),
    TELUGU("Telugu", "తెలుగు", "te-IN"),
    KANNADA("Kannada", "ಕನ್ನಡ", "kn-IN"),
    MALAYALAM("Malayalam", "മലയാളം", "ml-IN"),
    MARATHI("Marathi", "मराठी", "mr-IN"),
    GUJARATI("Gujarati", "ગુજરાતી", "gu-IN"),
    ODIA("Odia", "ଓଡ଼ିଆ", "od-IN");

    companion object {
        val DEFAULT_SOURCE = HINDI
        val DEFAULT_TARGET = ENGLISH

        fun fromBcp47(code: String): Language? {
            val normalized = code.trim().replace('_', '-')
            return entries.firstOrNull { it.bcp47Code.equals(normalized, ignoreCase = true) }
        }
    }
}
