package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Result of a speech-to-text stage.
 *
 * @param transcript Recognised text in the source language. May be blank when the
 *        speaker was silent; callers must treat blank as "no speech", not as an error.
 */
data class TranscriptionResult(val transcript: String)

/**
 * Result of a translation stage.
 *
 * @param text the text to speak on the far handset
 * @param translated false when the text was relayed unchanged because no translator was
 *        configured. The UI uses this to label the output with the language it is
 *        actually in, rather than claiming a translation that did not happen.
 */
data class TranslationResult(val text: String, val translated: Boolean = true)

/**
 * Result of a speech synthesis stage.
 *
 * Exactly one of [wavBytes] is always populated. Pipelines that speak through a
 * platform engine instead of returning audio report [spokenDirectly] = true, in
 * which case [wavBytes] is empty and the caller must not attempt playback itself.
 *
 * @param wavBytes Canonical RIFF/WAVE audio, ready for [com.itantra.voice.audio.AudioPlayer].
 * @param spokenDirectly True when the pipeline already routed the audio to the speaker.
 */
data class SynthesisResult(
    val wavBytes: ByteArray = ByteArray(0),
    val spokenDirectly: Boolean = false
) {
    // ByteArray needs structural equals/hashCode for value semantics in tests and state.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthesisResult) return false
        return spokenDirectly == other.spokenDirectly && wavBytes.contentEquals(other.wavBytes)
    }

    override fun hashCode(): Int = 31 * wavBytes.contentHashCode() + spokenDirectly.hashCode()
}

/** Shared immutable zero-level flow for pipelines that do not report input level. */
internal val ZeroAmplitude: StateFlow<Float> = MutableStateFlow(0f)

/**
 * Identifies which concrete implementation is backing the pipeline, so the UI can
 * tell the operator whether they are running air-gapped or via the cloud.
 */
enum class PipelineMode {
    /** Fully offline, on-device. Satisfies the SIH26173 "fully offline" mandate. */
    ON_DEVICE,

    /** Sarvam AI cloud APIs. Higher accuracy, requires a key and network. */
    CLOUD
}

/**
 * The three stages of the iTantra transceiver, decoupled from any specific engine.
 *
 * Implementations must be safe to call from a background dispatcher and must never
 * throw: every failure is reported as [Result.failure] so the FSM in
 * [com.itantra.voice.ui.MainViewModel] can render a single, uniform error path.
 */
interface SpeechPipeline {

    /** Which engine is backing this pipeline. */
    val mode: PipelineMode

    /**
     * Human-readable engine name shown in the UI status line (e.g. "On-device", "Sarvam Cloud").
     */
    val displayName: String

    /**
     * True when this pipeline opens the microphone itself.
     *
     * Android's `SpeechRecognizer` owns the mic for the duration of a session, so the
     * app must not also run [com.itantra.voice.audio.AudioRecorder] against it — two
     * concurrent capturers cause one of them to receive silence. The caller consults
     * this flag to decide whether to run its own recorder.
     */
    val capturesOwnAudio: Boolean get() = false

    /**
     * Live input level in 0f..1f for the waveform, published only by pipelines that
     * capture their own audio. Others leave it at zero and the caller uses the
     * recorder's own amplitude instead.
     */
    val amplitude: StateFlow<Float> get() = ZeroAmplitude

    /**
     * Called when push-to-talk is pressed, so a pipeline that owns the microphone can
     * start its recognition session at the same instant capture begins.
     */
    fun beginCapture(source: Language) = Unit

    /** Called when a press is abandoned, so any in-flight session is torn down. */
    fun cancelCapture() = Unit

    /**
     * Resolves any deferred setup before capture begins.
     *
     * Composite pipelines use this to decide which engine will serve the next utterance,
     * because [capturesOwnAudio] must be answerable synchronously the instant
     * push-to-talk is pressed. Safe to call repeatedly.
     */
    suspend fun prepare() = Unit

    /**
     * True when the pipeline is usable right now. A cloud pipeline without a
     * configured key, or an on-device pipeline whose engine failed to initialise,
     * reports false so the app can fall back rather than failing mid-utterance.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Transcribes 16 kHz mono 16-bit PCM audio wrapped in a RIFF/WAVE container.
     */
    suspend fun transcribe(wavData: ByteArray, source: Language): Result<TranscriptionResult>

    /**
     * Translates [text] from [source] to [target]. Implementations that cannot
     * translate must return the input unchanged rather than failing, so that a
     * same-language transceiver still works.
     */
    suspend fun translate(text: String, source: Language, target: Language): Result<TranslationResult>

    /**
     * Synthesises [text] as speech in [target].
     *
     * @param isEmergency When true the audio is an alert and must be played at
     *        maximum volume on the alarm stream, per the SIH26173 requirement that
     *        "alert type messages will be announced at highest volume non-interruptible".
     */
    suspend fun synthesize(text: String, target: Language, isEmergency: Boolean = false): Result<SynthesisResult>

    /** Releases any native engine resources. Must be idempotent. */
    fun release()
}
