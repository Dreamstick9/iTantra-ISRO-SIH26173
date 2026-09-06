package org.isro.itantra.stt

import org.isro.itantra.tts.SupportedLanguage

/**
 * Configuration and constants for On-Device Speech-to-Text (STT) and NavIC Micro-Payload budgeting.
 *
 * ISRO SIH26173 Specifications:
 * - NavIC Subframe Payload Limit: 27 Bytes (high priority alert frame)
 * - NavIC Packet Payload Limit: 277 Bytes (standard telemetry/packet frame)
 */
object SttConfig {

    const val NAVIC_SUBFRAME_MAX_BYTES = 27
    const val NAVIC_PACKET_MAX_BYTES = 277

    /**
     * Map SupportedLanguage to standard BCP-47 locale tags recognized by Android SpeechRecognizer.
     */
    fun getLocaleTag(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.HINDI -> "hi-IN"
            SupportedLanguage.MARATHI -> "mr-IN"
            SupportedLanguage.BENGALI -> "bn-IN"
            SupportedLanguage.TAMIL -> "ta-IN"
            SupportedLanguage.TELUGU -> "te-IN"
            SupportedLanguage.KANNADA -> "kn-IN"
            SupportedLanguage.MALAYALAM -> "ml-IN"
            SupportedLanguage.GUJARATI -> "gu-IN"
            SupportedLanguage.ODIA -> "or-IN"
            SupportedLanguage.ENGLISH -> "en-IN"
        }
    }

    /**
     * Calculates exact UTF-8 byte count for wire transmission budget.
     */
    fun calculateUtf8Bytes(text: String): Int {
        return text.trim().toByteArray(Charsets.UTF_8).size
    }

    fun isSubframeCompliant(text: String): Boolean {
        val bytes = calculateUtf8Bytes(text)
        return bytes in 1..NAVIC_SUBFRAME_MAX_BYTES
    }

    fun isPacketCompliant(text: String): Boolean {
        val bytes = calculateUtf8Bytes(text)
        return bytes in 1..NAVIC_PACKET_MAX_BYTES
    }

    /**
     * Disaster phrases used as realistic fallback when testing in emulators without offline Google Speech packs.
     */
    fun getFallbackDisasterPhrases(language: SupportedLanguage): List<String> {
        return when (language) {
            SupportedLanguage.HINDI -> listOf(
                "चक्रवात चेतावनी! तुरंत सुरक्षित स्थान पर जाएं।",
                "मदद चाहिए! नाव इंजन खराब हो गया है।",
                "बाढ़ का पानी बढ़ रहा है, तुरंत निकासी करें।",
                "चिकित्सा आपातकाल! तत्काल सहायता की आवश्यकता है।"
            )
            SupportedLanguage.MARATHI -> listOf(
                "चक्रीवादळ इशारा! सुरक्षित स्थळी स्थलांतर करा.",
                "मदत हवी आहे! बोट समुद्रात अडकली आहे.",
                "पूर परिस्थिती निर्माण झाली आहे, संपर्क साधा.",
                "वैद्यकीय आणीबाणी! तातडीने रुग्णवाहिका पाठवा."
            )
            SupportedLanguage.BENGALI -> listOf(
                "ঘূর্ণিঝড় সতর্কতা! নিরাপদ আশ্রয়ে যান।",
                "জরুরী সাহায্য দরকার! নৌকা আটকে গেছে।"
            )
            SupportedLanguage.TAMIL -> listOf(
                "புயல் எச்சரிக்கை! பாதுகாப்பான இடத்திற்கு செல்லவும்.",
                "அவசர உதவி தேவை! படகு பழுதடைந்துள்ளது."
            )
            SupportedLanguage.TELUGU -> listOf(
                "తుఫాను హెచ్చరిక! సురక్షిత ప్రాంతానికి తరలించండి.",
                "తక్షణ సహాయం కావాలి! పడవ మరమ్మతుకు గురైంది."
            )
            SupportedLanguage.KANNADA -> listOf(
                "ಚಂಡಮಾರುತ ಎಚ್ಚರಿಕೆ! ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ.",
                "ತುರ್ತು ನೆರವು ಬೇಕಾಗಿದೆ! ದೋಣಿ ಕೆಟ್ಟುಹೋಗಿದೆ."
            )
            SupportedLanguage.MALAYALAM -> listOf(
                "ചുഴലിക്കാറ്റ് മുന്നറിയിപ്പ്! സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക.",
                "അടിയന്തര സഹായം വേണം! ബോട്ട് തകരാറിലായി."
            )
            SupportedLanguage.GUJARATI -> listOf(
                "વાવાઝોડાની ચેતવણી! સુરક્ષિત સ્થળે પહોંચો.",
                "મદદ જોઈએ છે! બોટનું એન્જિન બગડ્યું છે."
            )
            SupportedLanguage.ODIA -> listOf(
                "ବାତ୍ୟା ସତର୍କତା! ନିରାପଦ ସ୍ଥାନକୁ ଚାଲିଯାଆନ୍ତୁ।",
                "ତୁରନ୍ତ ସାହାଯ୍ୟ ଦରକାର! ଡଙ୍ଗା ଖରାପ ହୋଇଛି।"
            )
            SupportedLanguage.ENGLISH -> listOf(
                "Cyclone warning! Evacuate coastal sector immediately.",
                "Mayday! Boat engine failure 5 miles offshore.",
                "Flash flood alert! Move to designated high ground.",
                "Medical emergency! First responder team needed."
            )
        }
    }
}
