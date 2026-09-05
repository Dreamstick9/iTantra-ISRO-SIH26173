package org.isro.itantra.tts

/**
 * Supported Indian and English languages for iTantra (ISRO SIH26173 mandate).
 */
enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeScript: String,
    val sampleRate: Int = 22050
) {
    HINDI("hi", "Hindi", "हिन्दी", 22050),
    ENGLISH("en", "English", "English", 22050),
    MARATHI("mr", "Marathi", "मराठी", 22050),
    BENGALI("bn", "Bengali", "বাংলা", 22050),
    TAMIL("ta", "Tamil", "தமிழ்", 16000),
    TELUGU("te", "Telugu", "తెలుగు", 22050),
    GUJARATI("gu", "Gujarati", "ગુજરાતી", 22050),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ", 22050),
    MALAYALAM("ml", "Malayalam", "മലയാളം", 22050),
    ODIA("or", "Odia", "ଓଡ଼ିଆ", 22050);

    companion object {
        val QUICK_LANGUAGES = listOf(HINDI, ENGLISH, MARATHI, BENGALI, TAMIL)
        fun fromCode(code: String): SupportedLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: HINDI
        }
    }
}

/**
 * Pre-configured Emergency Presets conforming to INCOIS & NavIC MsgID 21
 */
data class EmergencyPreset(
    val id: String,
    val title: String,
    val translations: Map<SupportedLanguage, String>,
    val isNonInterruptible: Boolean = true
) {
    fun getTextFor(language: SupportedLanguage): String {
        return translations[language] ?: translations[SupportedLanguage.ENGLISH] ?: title
    }

    companion object {
        val PRESETS = listOf(
            EmergencyPreset(
                id = "cyclone_warning",
                title = "Cyclone Warning",
                translations = mapOf(
                    SupportedLanguage.ENGLISH to "Cyclone Warning! Severe cyclonic storm approaching. Seek high ground immediately.",
                    SupportedLanguage.HINDI to "चक्रवात चेतावनी! तीव्र चक्रवाती तूफान आ रहा है। तुरंत सुरक्षित स्थान पर जाएं।",
                    SupportedLanguage.MARATHI to "चक्रीवादळ इशारा! तीव्र चक्रीवादळ येत आहे. त्वरित सुरक्षित ठिकाणी जा.",
                    SupportedLanguage.BENGALI to "ঘূর্ণিঝড় সতর্কতা! তীব্র ঘূর্ণিঝড় ধেয়ে আসছে। অবিলম্বে নিরাপদ স্থানে যান।",
                    SupportedLanguage.TAMIL to "புயல் எச்சரிக்கை! கடுமையான புயல் நெருங்குகிறது. உடனே பாதுகாப்பான இடத்திற்கு செல்லவும்.",
                    SupportedLanguage.TELUGU to "తుఫాను హెచ్చరిక! తీవ్ర తుఫాను సమీపిస్తోంది. వెంటనే సురక్షిత ప్రాంతానికి వెళ్ళండి."
                )
            ),
            EmergencyPreset(
                id = "tsunami_alert",
                title = "Tsunami Alert",
                translations = mapOf(
                    SupportedLanguage.ENGLISH to "Tsunami Alert! Giant ocean wave detected. Evacuate coast immediately.",
                    SupportedLanguage.HINDI to "सुनामी चेतावनी! विशाल समुद्री लहरें देखी गई हैं। तट तुरंत खाली करें।",
                    SupportedLanguage.MARATHI to "सुनामी सतर्कता! किनारपट्टी ताबडतोब रिकामी करा आणि उंचावर जा.",
                    SupportedLanguage.BENGALI to "সুনামি সতর্কতা! বিশাল সামুদ্রিক ঢেউ দেখা গেছে। অবিলম্বে উপকূল খালি করুন।",
                    SupportedLanguage.TAMIL to "சுனாமி எச்சரிக்கை! ராட்சத அலைகள் காணப்படுகின்றன. கடற்கரையை உடனே காலி செய்யவும்.",
                    SupportedLanguage.TELUGU to "సునామీ హెచ్చరిక! భారీ సముద్ర అలలు గుర్తించబడ్డాయి. తీరాన్ని వెంటనే ఖాళీ చేయండి."
                )
            ),
            EmergencyPreset(
                id = "evacuate_coastal",
                title = "Evacuate Coastal Area",
                translations = mapOf(
                    SupportedLanguage.ENGLISH to "Evacuate Coastal Area! Move at least 2 kilometers inland immediately.",
                    SupportedLanguage.HINDI to "तटीय क्षेत्र खाली करें! तुरंत कम से कम 2 किलोमीटर अंदर की ओर जाएं।",
                    SupportedLanguage.MARATHI to "किनारपट्टी रिकामी करा! त्वरित अंतर्भागात सुरक्षित स्थळी स्थलांतर करा.",
                    SupportedLanguage.BENGALI to "উপকূলীয় এলাকা খালি করুন! অবিলম্বে অন্তত দুই কিলোমিটার ভেতরে যান।",
                    SupportedLanguage.TAMIL to "கடற்கரை பகுதியை காலி செய்யவும்! உடனே குறைந்தது இரண்டு கிலோமீட்டர் உள்நோக்கி செல்லவும்.",
                    SupportedLanguage.TELUGU to "తీర ప్రాంతాన్ని ఖాళీ చేయండి! వెంటనే కనీసం రెండు కిలోమీటర్లు లోపలికి వెళ్ళండి."
                )
            )
        )
    }
}
