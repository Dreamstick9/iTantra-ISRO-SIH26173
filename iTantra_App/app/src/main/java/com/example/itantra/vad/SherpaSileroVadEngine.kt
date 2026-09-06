package com.example.itantra.vad

import android.content.Context
import com.example.itantra.util.Logger
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.TenVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Native Silero VAD implementation wrapping sherpa-onnx Vad engine.
 * Completely offline, running via ONNX Runtime on CPU.
 */
class SherpaSileroVadEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VadEngine {

    private val TAG = "SherpaSileroVad"

    private val engineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val stateLock = Any()

    private var vad: Vad? = null

    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _loadErrorMessage = MutableStateFlow<String?>(null)
    override val loadErrorMessage: StateFlow<String?> = _loadErrorMessage.asStateFlow()

    private var initJob: Job = engineScope.launch {
        loadModel()
    }

    override fun initialize(): Result<Unit> {
        if (_isModelLoaded.value) return Result.success(Unit)
        if (!initJob.isActive && vad == null) {
            initJob = engineScope.launch {
                loadModel()
            }
        }
        return Result.success(Unit)
    }

    private fun loadModel() {
        try {
            Logger.i(TAG, "Initializing Sherpa-ONNX Silero VAD model...")

            val sileroConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.6f, // 600 ms of silence to declare end of utterance
                minSpeechDuration = 0.25f, // 250 ms to confirm speech onset
                windowSize = 512,          // 512 samples = 32 ms at 16 kHz
                maxSpeechDuration = 20.0f  // 20s max duration
            )

            val vadModelConfig = VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                tenVadModelConfig = TenVadModelConfig(),
                sampleRate = 16000,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )

            synchronized(stateLock) {
                vad = Vad(context.assets, vadModelConfig)
            }

            _isModelLoaded.value = true
            _loadErrorMessage.value = null
            Logger.i(TAG, "Sherpa-ONNX Silero VAD loaded successfully.")
        } catch (t: Throwable) {
            val err = "Failed to load Silero VAD model: ${t.message}"
            Logger.e(TAG, err, t)
            _loadErrorMessage.value = err
            _isModelLoaded.value = false
        }
    }

    override fun acceptWaveform(samples: FloatArray) {
        synchronized(stateLock) {
            vad?.acceptWaveform(samples)
        }
    }

    override fun isSpeechDetected(): Boolean {
        return synchronized(stateLock) {
            vad?.isSpeechDetected() ?: false
        }
    }

    override fun hasSegment(): Boolean {
        return synchronized(stateLock) {
            vad?.let { !it.empty() } ?: false
        }
    }

    override fun popSegment(): FloatArray? {
        return synchronized(stateLock) {
            val v = vad ?: return@synchronized null
            if (!v.empty()) {
                val seg = v.front()
                v.pop()
                seg.samples
            } else {
                null
            }
        }
    }

    override fun reset() {
        synchronized(stateLock) {
            vad?.reset()
        }
    }

    override fun release() {
        synchronized(stateLock) {
            vad?.release()
            vad = null
            _isModelLoaded.value = false
        }
    }
}
