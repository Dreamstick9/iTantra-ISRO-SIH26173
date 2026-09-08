package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import java.util.logging.Logger

/**
 * Routes each stage to [preferred], falling back to [fallback] when the preferred
 * pipeline is unavailable.
 *
 * The fallback is deliberately availability-based rather than error-based: silently
 * retrying a failed utterance on a different engine would double the latency the
 * SIH26173 brief scores on, and would hide a misconfigured cloud key behind a
 * quietly-degraded transcript. An engine that is present but fails still surfaces
 * its own error.
 */
class FallbackSpeechPipeline(
    private val preferred: SpeechPipeline,
    private val fallback: SpeechPipeline
) : SpeechPipeline {

    private val log = Logger.getLogger("FallbackSpeechPipeline")

    /**
     * The engine currently serving requests.
     *
     * Resolved in [prepare] rather than per call because [capturesOwnAudio] has to be
     * answerable synchronously when push-to-talk is pressed. Switching engines midway
     * through an utterance would strand the audio: the on-device engine captures through
     * its own recogniser session while the cloud engine expects a recorded WAV, so the
     * choice has to be settled before capture starts.
     */
    @Volatile
    private var resolved: SpeechPipeline = preferred

    /** The engine that will serve the next utterance. */
    val activePipeline: SpeechPipeline get() = resolved

    override suspend fun prepare() {
        preferred.prepare()
        fallback.prepare()
        val next = if (preferred.isAvailable()) preferred else fallback
        if (next !== resolved) {
            log.info("Speech engine resolved to ${next.displayName}")
        }
        resolved = next
    }

    private fun active(): SpeechPipeline = resolved

    override val mode: PipelineMode get() = resolved.mode
    override val displayName: String get() = resolved.displayName

    override val capturesOwnAudio: Boolean get() = resolved.capturesOwnAudio
    override val amplitude: kotlinx.coroutines.flow.StateFlow<Float> get() = resolved.amplitude

    override fun beginCapture(source: Language) = resolved.beginCapture(source)
    override fun cancelCapture() = resolved.cancelCapture()

    /** The composite is usable when either engine is. */
    override suspend fun isAvailable(): Boolean =
        preferred.isAvailable() || fallback.isAvailable()

    override suspend fun transcribe(wavData: ByteArray, source: Language) =
        active().transcribe(wavData, source)

    override suspend fun translate(text: String, source: Language, target: Language) =
        active().translate(text, source, target)

    override suspend fun synthesize(text: String, target: Language, isEmergency: Boolean) =
        active().synthesize(text, target, isEmergency)

    override fun release() {
        preferred.release()
        fallback.release()
    }
}
