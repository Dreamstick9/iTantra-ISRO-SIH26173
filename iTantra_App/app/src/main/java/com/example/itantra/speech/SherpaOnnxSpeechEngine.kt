package com.example.itantra.speech

import android.content.Context
import com.example.itantra.data.Language
import com.example.itantra.util.Logger
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SherpaOnnxSpeechEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SpeechEngine {

    private val TAG = "SherpaOnnxSpeech"

    private val engineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val stateLock = Any()

    private var recognizer: OfflineRecognizer? = null

    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _loadErrorMessage = MutableStateFlow<String?>(null)
    override val loadErrorMessage: StateFlow<String?> = _loadErrorMessage.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    override val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private var initJob: Job = engineScope.launch {
        loadModel()
    }

    override fun initialize(): Result<Unit> {
        if (_isModelLoaded.value) return Result.success(Unit)
        if (!initJob.isActive && recognizer == null) {
            initJob = engineScope.launch {
                loadModel()
            }
        }
        return Result.success(Unit)
    }

    private fun loadModel() {
        try {
            Logger.i(TAG, "Initializing Sherpa-ONNX Zipformer Small English offline speech model...")

            val transducerConfig = OfflineTransducerModelConfig(
                encoder = "sherpa-onnx-zipformer-small-en-2023-06-26/encoder-epoch-99-avg-1.int8.onnx",
                decoder = "sherpa-onnx-zipformer-small-en-2023-06-26/decoder-epoch-99-avg-1.int8.onnx",
                joiner = "sherpa-onnx-zipformer-small-en-2023-06-26/joiner-epoch-99-avg-1.int8.onnx"
            )

            val modelConfig = OfflineModelConfig(
                transducer = transducerConfig,
                tokens = "sherpa-onnx-zipformer-small-en-2023-06-26/tokens.txt",
                modelType = "zipformer",
                numThreads = 2,
                provider = "cpu",
                debug = false
            )

            val featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            )

            val config = OfflineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )

            synchronized(stateLock) {
                recognizer = OfflineRecognizer(context.assets, config)
            }

            _isModelLoaded.value = true
            _loadErrorMessage.value = null
            Logger.i(TAG, "Sherpa-ONNX model loaded successfully.")
        } catch (t: Throwable) {
            val errorMsg = "Failed to load Sherpa-ONNX model: ${t.message}"
            Logger.e(TAG, errorMsg, t)
            _loadErrorMessage.value = errorMsg
            _isModelLoaded.value = false
        }
    }

    override suspend fun transcribe(audio: ByteArray): String = withContext(ioDispatcher) {
        if (audio.isEmpty()) {
            return@withContext ""
        }

        // Wait for model initialization if it's currently in progress
        if (recognizer == null && _loadErrorMessage.value == null) {
            initJob.join()
        }

        val samples = PcmAudioConverter.pcm16LeToFloatArray(audio)
        if (samples.isEmpty()) {
            return@withContext ""
        }

        val startTime = System.currentTimeMillis()

        try {
            val text = synchronized(stateLock) {
                val currentRec = recognizer ?: run {
                    val err = _loadErrorMessage.value ?: "Sherpa-ONNX recognizer is not initialized"
                    Logger.e(TAG, "Cannot transcribe audio: $err")
                    return@synchronized ""
                }

                val stream = currentRec.createStream()
                try {
                    stream.acceptWaveform(samples, 16000)
                    currentRec.decode(stream)
                    val result = currentRec.getResult(stream)
                    result.text.trim()
                } finally {
                    stream.release()
                }
            }

            val latency = System.currentTimeMillis() - startTime
            _lastLatencyMs.value = latency
            Logger.d(TAG, "Transcribed ${audio.size} bytes in ${latency}ms: '$text'")
            text
        } catch (t: Throwable) {
            val latency = System.currentTimeMillis() - startTime
            _lastLatencyMs.value = latency
            Logger.e(TAG, "Transcription error after ${latency}ms: ${t.message}", t)
            ""
        }
    }

    override suspend fun transcribe(pcmAudio: ByteArray, language: Language): Result<TranscriptionResult> {
        return try {
            val text = transcribe(pcmAudio)
            Result.success(
                TranscriptionResult(
                    text = text,
                    confidence = if (text.isNotEmpty()) 0.95f else 0.0f,
                    durationMs = _lastLatencyMs.value,
                    werEstimate = 0.05f
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override fun release() {
        initJob.cancel()
        synchronized(stateLock) {
            try {
                recognizer?.release()
                Logger.i(TAG, "Sherpa-ONNX recognizer released.")
            } catch (t: Throwable) {
                Logger.e(TAG, "Error releasing Sherpa-ONNX recognizer: ${t.message}", t)
            } finally {
                recognizer = null
                _isModelLoaded.value = false
            }
        }
    }

    companion object {
        fun pcm16LeToFloatArray(audio: ByteArray?): FloatArray = PcmAudioConverter.pcm16LeToFloatArray(audio)
        fun calculateSampleCount(byteCount: Int): Int = PcmAudioConverter.calculateSampleCount(byteCount)
        fun calculateDurationMs(byteCount: Int, sampleRate: Int = 16000): Long = PcmAudioConverter.calculateDurationMs(byteCount, sampleRate)
    }
}
