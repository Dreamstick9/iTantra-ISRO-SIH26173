package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Speech recognition source for [OnDeviceSpeechPipeline].
 *
 * Kept separate from the pipeline because the two viable on-device recognisers have
 * incompatible shapes: Android's `SpeechRecognizer` owns the microphone and streams
 * its own audio, whereas a bundled neural model (Vosk, sherpa-onnx) consumes a WAV
 * buffer. Both are expressible here, and both stay swappable without touching the FSM.
 *
 * Lifecycle: [beginCapture] is called the moment push-to-talk is pressed, and
 * [transcribe] once it is released. Buffer-based recognisers ignore [beginCapture]
 * and decode the WAV; microphone-owning recognisers start their session in
 * [beginCapture] and have [transcribe] await the text it produced.
 */
interface OfflineRecognizer {

    /** True when a recogniser is present and usable on this device. */
    suspend fun isAvailable(): Boolean

    /**
     * True when this recogniser opens the microphone itself, in which case the caller
     * must not run its own [com.itantra.voice.audio.AudioRecorder] concurrently.
     */
    val ownsMicrophone: Boolean get() = false

    /** Live input level in 0f..1f, published only by microphone-owning recognisers. */
    val amplitude: StateFlow<Float> get() = RecognizerZeroAmplitude

    /**
     * Signals that the operator has pressed push-to-talk. Microphone-owning
     * implementations start listening here; buffer-based ones do nothing.
     */
    fun beginCapture(source: Language) = Unit

    /**
     * Signals that push-to-talk was released without a usable utterance, so any
     * in-flight recognition session should be torn down.
     */
    fun cancelCapture() = Unit

    /**
     * Returns recognised text for the utterance, or a blank string when the speaker
     * was silent. Implementations that own the microphone ignore [wavData] and return
     * the text captured since the matching [beginCapture].
     */
    suspend fun transcribe(wavData: ByteArray, source: Language): Result<String>

    fun release()
}

internal val RecognizerZeroAmplitude: StateFlow<Float> = MutableStateFlow(0f)

/**
 * Recogniser used when no on-device engine is present.
 *
 * Reports unavailable and fails transcription with an actionable message rather than
 * silently returning empty text, which would surface to the operator as the far more
 * confusing "No speech detected".
 */
object UnavailableRecognizer : OfflineRecognizer {
    override suspend fun isAvailable(): Boolean = false

    override suspend fun transcribe(wavData: ByteArray, source: Language): Result<String> =
        Result.failure(
            IllegalStateException(
                "No on-device speech recogniser is available. Install Google's speech services and its offline language pack, or add a Sarvam API key to use the cloud pipeline."
            )
        )

    override fun release() = Unit
}
