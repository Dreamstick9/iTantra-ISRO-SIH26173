package com.itantra.voice.pipeline.recognition

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.itantra.voice.data.Language
import com.itantra.voice.pipeline.OfflineRecognizer
import com.itantra.voice.pipeline.toLocale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Offline recogniser backed by the platform [SpeechRecognizer] with
 * [RecognizerIntent.EXTRA_PREFER_OFFLINE] set, so recognition runs on-device and the
 * app stays functional on an air-gapped phone.
 *
 * [SpeechRecognizer] is main-thread-affine: every call must be made on the main
 * looper, and its callbacks arrive there too. All interaction is therefore posted to
 * [mainHandler], while the suspending [transcribe] simply awaits the result.
 */
class PlatformSpeechRecognizer(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val recognizerFactory: (Context) -> SpeechRecognizer = { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
    }
) : OfflineRecognizer {

    private val log = Logger.getLogger("PlatformSpeechRecognizer")

    private val released = AtomicBoolean(false)

    override val ownsMicrophone: Boolean = true

    private val _amplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /** Guarded by [mainHandler]; only ever touched on the main looper. */
    private var recognizer: SpeechRecognizer? = null

    /** Completed by the recognition callbacks with the final transcript or a failure. */
    @Volatile
    private var pending: CompletableDeferred<Result<String>>? = null

    override suspend fun isAvailable(): Boolean {
        if (released.get()) return false
        return runCatching {
            SpeechRecognizer.isRecognitionAvailable(context) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        }.getOrDefault(false)
    }

    override fun beginCapture(source: Language) {
        if (released.get()) return

        val deferred = CompletableDeferred<Result<String>>()
        pending = deferred

        mainHandler.post {
            if (released.get()) {
                deferred.complete(Result.failure(IllegalStateException("Recogniser released.")))
                return@post
            }
            runCatching {
                // A recogniser instance is single-use per session; recreate each time.
                recognizer?.destroy()
                val engine = recognizerFactory(context)
                recognizer = engine
                engine.setRecognitionListener(SessionListener(deferred))
                engine.startListening(buildIntent(source))
            }.onFailure { error ->
                log.warning("Failed to start on-device recognition: ${error.message}")
                deferred.complete(
                    Result.failure(IllegalStateException("Could not start on-device recognition: ${error.message}"))
                )
            }
        }
    }

    override fun cancelCapture() {
        _amplitude.value = 0f
        pending?.complete(Result.success(""))
        pending = null
        mainHandler.post { runCatching { recognizer?.cancel() } }
    }

    /**
     * [wavData] is ignored: this recogniser captured its own audio from the microphone
     * during the push-to-talk press. The parameter is part of the shared
     * [OfflineRecognizer] contract so buffer-based engines can slot in unchanged.
     */
    override suspend fun transcribe(wavData: ByteArray, source: Language): Result<String> {
        val deferred = pending
            ?: return Result.failure(IllegalStateException("No recognition session was started."))

        // stopListening tells the engine the utterance is over; results follow shortly.
        mainHandler.post { runCatching { recognizer?.stopListening() } }

        val result = withTimeoutOrNull(RESULT_TIMEOUT_MS) { deferred.await() }
        pending = null

        return result ?: Result.failure(
            IllegalStateException("On-device recognition timed out. Try speaking again.")
        )
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        pending?.complete(Result.failure(IllegalStateException("Recogniser released.")))
        pending = null
        mainHandler.post {
            runCatching {
                recognizer?.cancel()
                recognizer?.destroy()
            }
            recognizer = null
        }
    }

    private fun buildIntent(source: Language): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, source.toLocale().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // The SIH26173 brief mandates a fully offline pipeline.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

    /**
     * Completes [deferred] exactly once. [SpeechRecognizer] can deliver both a result
     * and an error for a single session, so the first terminal callback wins.
     */
    private inner class SessionListener(
        private val deferred: CompletableDeferred<Result<String>>
    ) : RecognitionListener {

        override fun onResults(results: Bundle?) {
            _amplitude.value = 0f
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            deferred.complete(Result.success(text.trim()))
        }

        override fun onError(error: Int) {
            _amplitude.value = 0f
            // Silence is a normal outcome of push-to-talk, not a failure to report.
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                deferred.complete(Result.success(""))
                return
            }
            deferred.complete(Result.failure(IllegalStateException(describeError(error))))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        /**
         * [SpeechRecognizer] reports roughly -2 dB (silence) to 10 dB (loud); normalise
         * that range so the waveform behaves the same as it does under AudioRecord.
         */
        override fun onRmsChanged(rmsdB: Float) {
            _amplitude.value = ((rmsdB - RMS_FLOOR_DB) / (RMS_CEILING_DB - RMS_FLOOR_DB))
                .coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error during recognition."
        SpeechRecognizer.ERROR_CLIENT -> "Recognition client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for speech recognition."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "The installed recogniser needs a network. Install an offline language pack in Settings > Voice input."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy. Try again in a moment."
        SpeechRecognizer.ERROR_SERVER -> "Recognition service error."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "That language pack is not installed for offline recognition."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
            "The on-device recogniser does not support that language."
        else -> "On-device recognition failed (code $code)."
    }

    private companion object {
        const val RESULT_TIMEOUT_MS = 12_000L
        const val RMS_FLOOR_DB = -2f
        const val RMS_CEILING_DB = 10f
    }
}
