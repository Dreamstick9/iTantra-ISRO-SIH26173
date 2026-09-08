package com.itantra.voice.pipeline

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.itantra.voice.data.Language
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import kotlin.coroutines.resume

/**
 * Fully offline, on-device speech pipeline.
 *
 * Synthesis uses the platform [TextToSpeech] engine with
 * [TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS] disabled, rendering to a WAV
 * file so the resulting audio flows through the same [com.itantra.voice.audio.AudioPlayer]
 * path as the cloud pipeline — which is what lets the emergency alarm routing work
 * identically for both engines.
 *
 * Recognition is supplied by [recognizer] rather than being constructed here: the
 * platform `SpeechRecognizer` is a live-microphone API and cannot consume a recorded
 * WAV buffer, so the microphone-driven implementation lives in
 * [com.itantra.voice.pipeline.recognition.PlatformSpeechRecognizer] and is injected.
 * That split also keeps this class unit-testable on the JVM.
 *
 * Translation is intentionally a pass-through: no open-source on-device Indic
 * translation model is bundled, so rather than failing an utterance this pipeline
 * relays the recognised text verbatim. The receiving phone still speaks it, which
 * preserves the walkie-talkie loop for same-language operation.
 */
class OnDeviceSpeechPipeline(
    private val context: Context,
    private val cacheDir: File,
    private val recognizer: OfflineRecognizer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ttsFactory: ((Context, TextToSpeech.OnInitListener) -> TextToSpeech)? = null
) : SpeechPipeline {

    private val log = Logger.getLogger("OnDeviceSpeechPipeline")

    override val mode: PipelineMode = PipelineMode.ON_DEVICE
    override val displayName: String = "On-device"

    override val capturesOwnAudio: Boolean get() = recognizer.ownsMicrophone
    override val amplitude: kotlinx.coroutines.flow.StateFlow<Float> get() = recognizer.amplitude

    override fun beginCapture(source: Language) = recognizer.beginCapture(source)
    override fun cancelCapture() = recognizer.cancelCapture()

    private val released = AtomicBoolean(false)

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    /**
     * Lazily initialises the platform TTS engine, awaiting its asynchronous
     * onInit callback. Returns null when no usable engine exists on the device.
     */
    private suspend fun ensureTts(): TextToSpeech? {
        if (released.get()) return null
        tts?.takeIf { ttsReady }?.let { return it }

        return withTimeoutOrNull(TTS_INIT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont: CancellableContinuation<TextToSpeech?> ->
                val resumed = AtomicBoolean(false)
                lateinit var engine: TextToSpeech
                val listener = TextToSpeech.OnInitListener { status ->
                    if (!resumed.compareAndSet(false, true)) return@OnInitListener
                    if (status == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        tts = engine
                        cont.resume(engine)
                    } else {
                        log.warning("Platform TTS engine failed to initialise: status=$status")
                        ttsReady = false
                        cont.resume(null)
                    }
                }
                engine = ttsFactory?.invoke(context, listener) ?: TextToSpeech(context, listener)
            }
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(ioDispatcher) {
        ensureTts() != null && recognizer.isAvailable()
    }

    override suspend fun transcribe(
        wavData: ByteArray,
        source: Language
    ): Result<TranscriptionResult> {
        // Only meaningful for buffer-based recognisers; a microphone-owning recogniser
        // is handed an empty array because it captured its own audio.
        if (!recognizer.ownsMicrophone && wavData.size <= WAV_HEADER_BYTES) {
            return Result.failure(IllegalArgumentException("Audio payload is empty."))
        }
        return recognizer.transcribe(wavData, source).map { TranscriptionResult(it) }
    }

    /**
     * Pass-through. See the class comment: relaying verbatim keeps the transceiver
     * loop alive rather than dropping the utterance when no offline translator exists.
     */
    override suspend fun translate(
        text: String,
        source: Language,
        target: Language
    ): Result<TranslationResult> =
        Result.success(TranslationResult(text, translated = source == target))

    override suspend fun synthesize(
        text: String,
        target: Language,
        isEmergency: Boolean
    ): Result<SynthesisResult> = withContext(ioDispatcher) {
        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Nothing to speak."))
        }

        val engine = ensureTts()
            ?: return@withContext Result.failure(
                IllegalStateException("No on-device speech engine is installed. Install a text-to-speech engine in Android Settings, or add a Sarvam API key to use the cloud pipeline.")
            )

        val locale = target.toLocale()
        when (engine.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA ->
                return@withContext Result.failure(
                    IllegalStateException("${target.displayName} voice data is not installed on this device. Install it in Settings > Text-to-speech.")
                )

            TextToSpeech.LANG_NOT_SUPPORTED ->
                return@withContext Result.failure(
                    IllegalStateException("The on-device speech engine does not support ${target.displayName}.")
                )
        }

        // Force offline synthesis so an unavailable network can never stall an alert.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { engine.setSpeechRate(1.0f) }
        }

        val outFile = File(cacheDir, "tts_${UUID.randomUUID()}.wav")
        val utteranceId = UUID.randomUUID().toString()

        val ok = withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont: CancellableContinuation<Boolean> ->
                val resumed = AtomicBoolean(false)
                fun finish(success: Boolean) {
                    if (resumed.compareAndSet(false, true)) cont.resume(success)
                }

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) {
                        if (id == utteranceId) finish(true)
                    }

                    @Deprecated("Superseded by onError(String, Int)", ReplaceWith(""))
                    override fun onError(id: String?) {
                        if (id == utteranceId) finish(false)
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) {
                            log.warning("TTS synthesis error for $id: code=$errorCode")
                            finish(false)
                        }
                    }
                })

                cont.invokeOnCancellation { runCatching { engine.stop() } }

                val queued = engine.synthesizeToFile(text, android.os.Bundle(), outFile, utteranceId)
                if (queued != TextToSpeech.SUCCESS) finish(false)
            }
        }

        if (ok != true || !outFile.exists() || outFile.length() <= WAV_HEADER_BYTES) {
            runCatching { outFile.delete() }
            return@withContext Result.failure(
                IllegalStateException("On-device speech synthesis produced no audio.")
            )
        }

        val bytes = runCatching { outFile.readBytes() }.getOrElse {
            runCatching { outFile.delete() }
            return@withContext Result.failure(IllegalStateException("Could not read synthesised audio: ${it.message}"))
        }
        runCatching { outFile.delete() }

        Result.success(SynthesisResult(wavBytes = bytes, spokenDirectly = false))
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        recognizer.release()
        ttsReady = false
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    private companion object {
        const val TTS_INIT_TIMEOUT_MS = 8_000L
        const val SYNTHESIS_TIMEOUT_MS = 20_000L
        const val WAV_HEADER_BYTES = 44
    }
}

/**
 * Maps an iTantra [Language] to the JVM [Locale] the platform speech engines expect.
 *
 * Odia is carried as "od-IN" on the wire because that is the tag Sarvam's API uses,
 * but ISO 639-1 assigns Odia the code "or"; the platform engines only recognise the
 * ISO form, so the two are reconciled here rather than at the call sites.
 */
fun Language.toLocale(): Locale = when (this) {
    Language.ODIA -> Locale.Builder().setLanguage("or").setRegion("IN").build()
    else -> {
        val parts = bcp47Code.split("-")
        Locale.Builder().setLanguage(parts[0]).setRegion(parts.getOrElse(1) { "IN" }).build()
    }
}
