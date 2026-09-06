package com.example.itantra.speech

import com.example.itantra.data.Language
import com.example.itantra.util.Logger

class MockSpeechEngine : SpeechEngine {
    private val TAG = "MockSpeech"

    var simulatedTranscriptionText: String? = null
    var simulatedSttLatencyMs: Long = 180L
    var simulatedTtsComputeTimeMs: Long = 320L
    var simulatedTtsAudioDurationMs: Long = 1900L
    var transcribeCallCount: Int = 0
        private set
    var synthesizeCallCount: Int = 0
        private set

    override fun initialize(): Result<Unit> {
        Logger.i(TAG, "MockSpeechEngine initialized (10 Indian languages mock).")
        return Result.success(Unit)
    }

    override suspend fun transcribe(pcmAudio: ByteArray, language: Language): Result<TranscriptionResult> {
        transcribeCallCount++
        val text = simulatedTranscriptionText ?: when (language) {
            Language.HINDI -> "चक्रवात चेतावनी तुरंत सुरक्षित स्थान पर जाएं"
            Language.MARATHI -> "चक्रीवादळाचा इशारा त्वरित सुरक्षित स्थळी जा"
            Language.BENGALI -> "ঘূর্ণিঝড় সতর্কতা অবিলম্বে নিরাপদ স্থানে যান"
            Language.TAMIL -> "புயல் எச்சரிக்கை உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும்"
            Language.TELUGU -> "తుఫాను హెచ్చరిక వెంటనే సురక్షిత ప్రాంతానికి వెళ్లండి"
            Language.KANNADA -> "ಚಂಡಮಾರುತದ ಎಚ್ಚರಿಕೆ ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ"
            Language.MALAYALAM -> "ചുഴലിക്കാറ്റ് മുന്നറിയിപ്പ് ഉടൻ സുരക്ഷിത സ്ഥാനത്തേക്ക് മാറുക"
            Language.GUJARATI -> "વાવાઝોડાની ચેતવણી તરત જ સુરક્ષિત સ્થળે જાઓ"
            Language.ODIA -> "ବାତ୍ୟା ଚେତାବନୀ ତୁରନ୍ତ ନିରାପଦ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ"
            Language.ENGLISH -> "Cyclone alert evacuate to high ground immediately"
        }

        Logger.i(TAG, "STT Transcribed [${language.isoCode}]: '$text' in ${simulatedSttLatencyMs}ms.")
        return Result.success(
            TranscriptionResult(
                text = text,
                confidence = 0.96f,
                durationMs = simulatedSttLatencyMs,
                werEstimate = 0.04f
            )
        )
    }

    override suspend fun synthesize(text: String, language: Language, isEmergency: Boolean): Result<SynthesisResult> {
        synthesizeCallCount++
        val rtf = simulatedTtsComputeTimeMs.toDouble() / simulatedTtsAudioDurationMs.toDouble()
        val byteCount = ((simulatedTtsAudioDurationMs * 16000L * 2L) / 1000L).toInt()
        val dummyPcm = ByteArray(byteCount) { (it % 120).toByte() }

        Logger.i(TAG, "TTS Synthesized [${language.isoCode}]: length=${simulatedTtsAudioDurationMs}ms, RTF=%.3f".format(rtf))

        return Result.success(
            SynthesisResult(
                audioPcm = dummyPcm,
                sampleRate = 16000,
                audioDurationMs = simulatedTtsAudioDurationMs,
                computeTimeMs = simulatedTtsComputeTimeMs,
                rtf = rtf
            )
        )
    }

    override fun normalize(text: String, language: Language): String {
        return text.trim()
    }

    override fun release() {
        Logger.i(TAG, "MockSpeechEngine released.")
    }
}
