package com.example.itantra.tts

import android.content.Context
import com.example.itantra.audio.AudioTrackPlayer
import com.example.itantra.util.Logger
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * 100% On-Device Offline Neural Text-to-Speech Engine utilizing sherpa-onnx VITS Piper models.
 * Runs completely locally with zero network calls, safe thread boundaries, and deterministic AudioTrack output.
 */
class SherpaOnnxTtsEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val audioTrackPlayer: AudioTrackPlayer = AudioTrackPlayer(ioDispatcher)
) : TtsEngine {

    private val TAG = "SherpaOnnxTts"

    private val engineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val stateLock = Any()

    private var tts: OfflineTts? = null

    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _playbackState = MutableStateFlow(TtsPlaybackState.IDLE)
    override val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    override val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _lastRtf = MutableStateFlow(0.0)
    override val lastRtf: StateFlow<Double> = _lastRtf.asStateFlow()

    private val _lastDurationMs = MutableStateFlow(0L)
    override val lastDurationMs: StateFlow<Long> = _lastDurationMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var initJob: Job = engineScope.launch {
        loadModel()
    }

    init {
        // Observe AudioTrackPlayer playback states and mirror them
        engineScope.launch {
            audioTrackPlayer.playbackState.collect { trackState ->
                if (_playbackState.value != TtsPlaybackState.SYNTHESIZING) {
                    _playbackState.value = trackState
                }
            }
        }
    }

    private fun loadModel() {
        _playbackState.value = TtsPlaybackState.INITIALIZING
        val startTime = System.currentTimeMillis()
        try {
            Logger.i(TAG, "Preparing Sherpa-ONNX VITS Piper English offline speech model...")

            // Copy espeak-ng-data from APK assets to internal storage if not already cached
            val baseDir = copyDataDir(context, "vits-piper-en_US-amy-low/espeak-ng-data")
            val absoluteDataDir = "$baseDir/vits-piper-en_US-amy-low/espeak-ng-data"

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = "vits-piper-en_US-amy-low/en_US-amy-low.onnx",
                tokens = "vits-piper-en_US-amy-low/tokens.txt",
                dataDir = absoluteDataDir,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )

            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = 1
            )

            val loadedTts = OfflineTts(assetManager = context.assets, config = ttsConfig)
            Logger.i(TAG, "Sherpa-ONNX TTS initialized in ${System.currentTimeMillis() - startTime}ms (sample rate: ${loadedTts.sampleRate()} Hz).")

            synchronized(stateLock) {
                tts = loadedTts
            }

            _isModelLoaded.value = true
            _errorMessage.value = null
            _playbackState.value = TtsPlaybackState.IDLE
            Logger.i(TAG, "Offline TTS Model successfully initialized.")

        } catch (t: Throwable) {
            val err = "Failed to initialize Sherpa-ONNX TTS: ${t.message}"
            Logger.e(TAG, err, t)
            _errorMessage.value = err
            _isModelLoaded.value = false
            _playbackState.value = TtsPlaybackState.ERROR
        }
    }

    /**
     * Synthesizes speech from raw text offline using Sherpa-ONNX VITS.
     * Guaranteed to run off the UI thread and produce local AudioData.
     */
    override suspend fun synthesize(text: String): AudioData = withContext(ioDispatcher) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            Logger.w(TAG, "Empty text provided to synthesize(); returning empty AudioData.")
            return@withContext AudioData.EMPTY
        }

        // Wait for model initialization if active
        if (tts == null && _errorMessage.value == null) {
            initJob.join()
        }

        val currentTts = synchronized(stateLock) { tts }
        if (currentTts == null) {
            val err = _errorMessage.value ?: "TTS engine is not initialized"
            Logger.e(TAG, "Cannot synthesize speech: $err")
            _playbackState.value = TtsPlaybackState.ERROR
            return@withContext AudioData.EMPTY
        }

        _playbackState.value = TtsPlaybackState.SYNTHESIZING
        val startTime = System.currentTimeMillis()

        try {
            val generatedAudio = synchronized(stateLock) {
                currentTts.generate(
                    text = trimmed,
                    sid = 0,
                    speed = 1.0f
                )
            }

            val latency = System.currentTimeMillis() - startTime
            val samples = generatedAudio.samples
            val sampleRate = generatedAudio.sampleRate
            val durationMs = if (sampleRate > 0) (samples.size * 1000L) / sampleRate else 0L
            val rtf = if (durationMs > 0) latency.toDouble() / durationMs.toDouble() else 0.0

            _lastLatencyMs.value = latency
            _lastRtf.value = rtf
            _lastDurationMs.value = durationMs

            var maxAmp = 0f
            for (s in samples) {
                val abs = kotlin.math.abs(s)
                if (abs > maxAmp) maxAmp = abs
            }

            // Normalize peak amplitude to 0.95 for maximum audibility and speech clarity
            val normalizedSamples = if (maxAmp in 0.05f..0.90f) {
                val gain = 0.95f / maxAmp
                FloatArray(samples.size) { samples[it] * gain }
            } else {
                samples
            }

            val rawPcm = AudioData.floatArrayToPcm16Le(normalizedSamples)

            try {
                val wavFile = File(context.cacheDir, "last_tts.wav")
                generatedAudio.save(wavFile.absolutePath)
            } catch (e: Exception) {
                Logger.w(TAG, "Could not save debug WAV: ${e.message}")
            }

            Logger.i(TAG, "Synthesized \"$trimmed\" -> ${samples.size} samples (${durationMs}ms audio, peak=$maxAmp) in ${latency}ms (RTF: ${"%.2f".format(rtf)})")

            AudioData(
                samples = normalizedSamples,
                sampleRate = sampleRate,
                rawPcm = rawPcm,
                durationMs = durationMs
            )
        } catch (t: Throwable) {
            val latency = System.currentTimeMillis() - startTime
            _lastLatencyMs.value = latency
            val err = "Synthesis failure: ${t.message}"
            Logger.e(TAG, err, t)
            _errorMessage.value = err
            _playbackState.value = TtsPlaybackState.ERROR
            AudioData.EMPTY
        } finally {
            if (_playbackState.value == TtsPlaybackState.SYNTHESIZING) {
                _playbackState.value = TtsPlaybackState.IDLE
            }
        }
    }

    /**
     * Synthesizes and speaks text directly via AudioTrack on background thread.
     */
    override suspend fun speak(text: String): Unit = withContext(ioDispatcher) {
        val audioData = synthesize(text)
        if (audioData.isEmpty) {
            Logger.w(TAG, "No audio generated for text; skipping playback.")
            return@withContext
        }

        audioTrackPlayer.play(
            pcmData = audioData.rawPcm,
            sampleRate = audioData.sampleRate
        )
    }

    /**
     * Stops active AudioTrack playback immediately.
     */
    override suspend fun stop() {
        audioTrackPlayer.stop()
    }

    override fun release() {
        initJob.cancel()
        audioTrackPlayer.release()
        synchronized(stateLock) {
            try {
                tts?.release()
                Logger.i(TAG, "Sherpa-ONNX TTS engine released.")
            } catch (t: Throwable) {
                Logger.e(TAG, "Error releasing TTS native handle: ${t.message}", t)
            } finally {
                tts = null
                _isModelLoaded.value = false
                _playbackState.value = TtsPlaybackState.IDLE
            }
        }
    }

    companion object {
        private const val TAG_HELPER = "SherpaTtsCopy"

        fun copyDataDir(context: Context, dataDir: String): String {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val markerFile = File(baseDir, "$dataDir/.copied_complete")
            if (!markerFile.exists()) {
                Logger.i(TAG_HELPER, "Extracting espeak-ng linguistic assets to $baseDir...")
                copyAssets(context, dataDir, baseDir)
                try {
                    markerFile.createNewFile()
                } catch (e: Exception) {
                    Logger.w(TAG_HELPER, "Could not create marker file: ${e.message}")
                }
                Logger.i(TAG_HELPER, "Extraction of $dataDir complete.")
            }
            return baseDir.absolutePath
        }

        private fun copyAssets(context: Context, path: String, baseDir: File) {
            val assetManager = context.assets
            try {
                val list = assetManager.list(path)
                if (list.isNullOrEmpty()) {
                    copyFile(context, path, baseDir)
                } else {
                    val dir = File(baseDir, path)
                    dir.mkdirs()
                    for (item in list) {
                        val subPath = if (path.isEmpty()) item else "$path/$item"
                        copyAssets(context, subPath, baseDir)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG_HELPER, "Failed copying asset path $path: ${e.message}", e)
            }
        }

        private fun copyFile(context: Context, filename: String, baseDir: File) {
            val dst = File(baseDir, filename)
            if (dst.exists() && dst.length() > 0) return
            dst.parentFile?.mkdirs()
            try {
                context.assets.open(filename).use { input ->
                    FileOutputStream(dst).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG_HELPER, "Failed copying file $filename: ${e.message}", e)
            }
        }
    }
}
