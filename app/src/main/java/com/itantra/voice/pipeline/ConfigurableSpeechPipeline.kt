package com.itantra.voice.pipeline

import com.itantra.voice.data.Language
import com.itantra.voice.network.SarvamApiClient
import kotlinx.coroutines.flow.StateFlow
import java.util.logging.Logger

/**
 * Chooses between the offline and cloud engines from **configuration**, re-reading that
 * configuration on every [prepare].
 *
 * Replaces availability probing, which could not work: `SpeechRecognizer`
 * `.isRecognitionAvailable()` is true on any phone that has a recognition service
 * installed, whether or not an offline language pack exists for the chosen language. That
 * made the offline engine always win and a configured Sarvam key never get used, and
 * recognition then failed at runtime with "language pack not installed". Android exposes
 * no way to query pack presence without attempting recognition, so the operator's explicit
 * configuration decides instead.
 *
 * Precedence, highest first:
 *  1. [preferOffline] — operator pinned the offline engine
 *  2. a usable key from [keyProvider] — cloud engine
 *  3. otherwise — offline engine
 *
 * Resolution happens in [prepare] rather than per call because [capturesOwnAudio] must be
 * answerable synchronously the instant push-to-talk is pressed: the offline engine captures
 * through its own recogniser session while the cloud engine expects a recorded WAV, so a
 * mid-utterance switch would leave the audio captured by nobody.
 */
class ConfigurableSpeechPipeline(
    private val onDevice: SpeechPipeline,
    private val cloud: SpeechPipeline,
    private val keyProvider: () -> String,
    private val preferOffline: () -> Boolean
) : SpeechPipeline {

    private val log = Logger.getLogger("ConfigurableSpeechPipeline")

    @Volatile
    private var resolved: SpeechPipeline = onDevice

    /** The engine that will serve the next utterance. */
    val activePipeline: SpeechPipeline get() = resolved

    override suspend fun prepare() {
        val next = when {
            preferOffline() -> onDevice
            !SarvamApiClient.isPlaceholderKey(keyProvider()) -> cloud
            else -> onDevice
        }
        next.prepare()
        if (next !== resolved) log.info("Speech engine resolved to ${next.displayName}")
        resolved = next
    }

    override val mode: PipelineMode get() = resolved.mode
    override val displayName: String get() = resolved.displayName
    override val capturesOwnAudio: Boolean get() = resolved.capturesOwnAudio
    override val amplitude: StateFlow<Float> get() = resolved.amplitude

    override fun beginCapture(source: Language) = resolved.beginCapture(source)
    override fun cancelCapture() = resolved.cancelCapture()

    /** True when the engine the configuration selected can actually run. */
    override suspend fun isAvailable(): Boolean = resolved.isAvailable()

    override suspend fun transcribe(wavData: ByteArray, source: Language) =
        resolved.transcribe(wavData, source)

    override suspend fun translate(text: String, source: Language, target: Language) =
        resolved.translate(text, source, target)

    override suspend fun synthesize(text: String, target: Language, isEmergency: Boolean) =
        resolved.synthesize(text, target, isEmergency)

    override fun release() {
        onDevice.release()
        cloud.release()
    }
}
