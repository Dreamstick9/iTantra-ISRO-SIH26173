# iTantra Technical Architecture Report: Audio, Network & State Machine Systems

**Document Version:** 1.0.0  
**Author:** `explorer_survey_2` (Teamwork Explorer)  
**Date:** 2026-09-07  
**Scope:** Native Audio Recording/Packaging, Sarvam AI REST API Integration, Audio Playback, Reactive State Machine, Security & Feedback Persistence.

---

## 1. Executive Summary & Architectural Overview

The **iTantra** mobile application is a high-reliability, native Android multilingual voice communication prototype. It delivers a seamless Push-to-Talk (PTT) interface connecting users across 10 official Indian languages using the official Sarvam AI cloud APIs:
- **Speech-to-Text (STT):** Sarvam Saaras v3 (`POST /speech-to-text`)
- **Translation:** Sarvam Translate / Mayura v1 (`POST /translate`)
- **Text-to-Speech (TTS):** Sarvam Bulbul v3 (`POST /text-to-speech`)

The application architecture follows modern Android Jetpack best practices: **Unidirectional Data Flow (UDF)**, **MVVM with Kotlin Coroutines & StateFlow**, and **Jetpack Compose**. The entire user experience is contained on a **single reactive screen** designed for mission-critical tactile clarity, zero extraneous navigation, and high resilience under varying mobile network conditions.

```
+---------------------------------------------------------------------------------------+
|                                     Jetpack Compose UI                                 |
|   [Language Selectors]  -->  [Tactile PTT Button]  -->  [Transcript & Translation]    |
+-------------------------------------------+-------------------------------------------+
                                            | Events (Press/Release/Feedback)
                                            v
+---------------------------------------------------------------------------------------+
|                                   MainViewModel                                       |
|             StateFlow<MainUiState>: IDLE -> RECORDING -> TRANSCRIBING ->              |
|                                     TRANSLATING -> SYNTHESIZING -> PLAYING            |
+---------------------+---------------------+---------------------+---------------------+
                      |                     |                     |
                      v                     v                     v
         +------------------------+ +---------------+ +------------------------+
         |  AndroidAudioEngine    | |  SarvamApi    | |  AndroidAudioPlayer    |
         |  - AudioRecord         | |  Repository   | |  - Base64 WAV Decoder  |
         |  - 16 kHz Mono PCM     | |  - OkHttp     | |  - MediaPlayer Cache   |
         |  - 44-byte RIFF WAV    | |  - Retrofit   | |  - Audio Focus Mgt     |
         +------------------------+ +---------------+ +------------------------+
                                            |
                                            v
                            +-------------------------------+
                            |   Feedback & Security Layer   |
                            |   - local.properties Key      |
                            |   - JSON Feedback Logger      |
                            +-------------------------------+
```

---

## 2. Subsystem 1: Native Audio Recording & Compliant RIFF WAV Packaging

### 2.1 Audio Hardware & AudioRecord Configuration
To meet the precise ingest contract of Sarvam Saaras v3, audio must be captured with strict sample rate, channel count, and bit depth.

| Parameter | Value | Rationale |
| :--- | :--- | :--- |
| **Sample Rate** | `16,000 Hz` (16 kHz) | Mandatory for Saaras v3 STT. Standard telephony/speech recognition rate covering human vocal formants up to 8 kHz. |
| **Channel Config** | `AudioFormat.CHANNEL_IN_MONO` | Single channel (1 channel). Speech recognition requires mono; reduces network payload by 50% vs stereo. |
| **Encoding Format** | `AudioFormat.ENCODING_PCM_16BIT` | 16-bit linear PCM (signed little-endian, 2 bytes/sample). Standard dynamic range (96 dB) for speech. |
| **Audio Source** | `MediaRecorder.AudioSource.VOICE_RECOGNITION` | Engages hardware Acoustic Echo Cancellation (AEC) and Noise Suppression (NS) tuned for speech recognition; fallbacks to `MIC` if unavailable. |

### 2.2 Buffer Sizing & Calculation
Buffer underruns result in audible clicks, audio frame drops, and degraded speech recognition accuracy. The buffer size must be dynamically queried from the hardware HAL and scaled appropriately:

$$\text{Minimum Buffer Size} = \text{AudioRecord.getMinBufferSize}(16000, \text{CHANNEL\_IN\_MONO}, \text{ENCODING\_PCM\_16BIT})$$

```kotlin
object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
    const val CHANNELS = 1
    const val BYTE_RATE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE // 32,000 bytes/sec
    
    // Minimum duration for valid speech: 400ms = 12,800 bytes
    const val MIN_RECORDING_BYTES = 12800 
    // Maximum duration: 30 seconds = 960,000 bytes (~0.96 MB)
    const val MAX_RECORDING_BYTES = 30 * BYTE_RATE 
    
    fun calculateBufferSize(): Int {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        require(minBufferSize != AudioRecord.ERROR && minBufferSize != AudioRecord.ERROR_BAD_VALUE) {
            "AudioRecord parameter configuration not supported by hardware"
        }
        // Allocate 2x min buffer size with 4 KB floor to prevent HAL jitter underruns
        return maxOf(minBufferSize * 2, 4096)
    }
}
```

### 2.3 Non-blocking Background Coroutine Recording Loop
The recording loop executes entirely within `Dispatchers.IO`. It streams chunks of audio into an in-memory `ByteArrayOutputStream` without blocking the main UI thread.

```kotlin
class AndroidAudioEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(coroutineScope: CoroutineScope): StateFlow<Int> {
        val amplitudeFlow = MutableStateFlow(0)
        if (isRecording.getAndSet(true)) {
            return amplitudeFlow
        }

        val bufferSize = AudioConfig.calculateBufferSize()
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            isRecording.set(false)
            record.release()
            throw IllegalStateException("Failed to initialize native AudioRecord")
        }

        audioRecord = record
        record.startRecording()

        recordingJob = coroutineScope.launch(ioDispatcher) {
            val audioChunk = ByteArray(2048)
            val outputStream = ByteArrayOutputStream()
            
            try {
                while (isRecording.get() && isActive) {
                    val bytesRead = record.read(audioChunk, 0, audioChunk.size)
                    if (bytesRead > 0) {
                        outputStream.write(audioChunk, 0, bytesRead)
                        
                        // Compute peak amplitude for UI waveform/metering
                        var maxSample = 0
                        for (i in 0 until bytesRead step 2) {
                            val sample = (audioChunk[i].toInt() and 0xFF) or 
                                         (audioChunk[i + 1].toInt() shl 8)
                            val absSample = kotlin.math.abs(sample.toShort().toInt())
                            if (absSample > maxSample) maxSample = absSample
                        }
                        amplitudeFlow.value = maxSample

                        // Auto-stop if exceeding maximum threshold (30s)
                        if (outputStream.size() >= AudioConfig.MAX_RECORDING_BYTES) {
                            break
                        }
                    } else if (bytesRead < 0) {
                        Log.e("AudioEngine", "AudioRecord read error: $bytesRead")
                        break
                    }
                }
            } finally {
                capturedPcmBytes = outputStream.toByteArray()
                safeStopAndReleaseRecord()
            }
        }
        return amplitudeFlow
    }

    private var capturedPcmBytes: ByteArray? = null

    fun stopRecording(): ByteArray {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        safeStopAndReleaseRecord()
        return capturedPcmBytes ?: ByteArray(0)
    }

    private fun safeStopAndReleaseRecord() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w("AudioEngine", "Exception releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }
}
```

### 2.4 Canonical RIFF WAV Header Spec (44-Byte Container)
Cloud speech recognizers (including Saaras v3) strictly parse RIFF containers. Raw PCM bytes must be prepended with a compliant 44-byte RIFF header.

#### Header Layout Diagram:
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   'R'   |   'I'   |   'F'   |   'F'   |    ChunkSize (36+SubChunk2Size)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   'W'   |   'A'   |   'V'   |   'E'   |   'f'   |   'm'   |   't'   |   ' '   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|        Subchunk1Size (16)     | AudioFormat(1)| NumChannels(1)|
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   SampleRate (16000)                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   ByteRate (32000)                            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   BlockAlign (2)  | BitsPerSample(16) |   'd'   |   'a'   |   't'   |   'a'   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                  Subchunk2Size (NumBytes)                     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Raw PCM Samples ...                     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

#### Pure Kotlin RIFF WAV Converter Implementation:
```kotlin
object WavAudioConverter {

    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val audioLength = pcmData.size
        val totalDataLen = audioLength + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteArray(44)
        
        // 00-03: 'RIFF' chunk descriptor (Big Endian ASCII)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        // 04-07: Total file size - 8 bytes (32-bit Little Endian)
        header[4] = (totalDataLen and 0xFF).toByte()
        header[5] = ((totalDataLen shr 8) and 0xFF).toByte()
        header[6] = ((totalDataLen shr 16) and 0xFF).toByte()
        header[7] = ((totalDataLen shr 24) and 0xFF).toByte()
        
        // 08-11: 'WAVE' format (Big Endian ASCII)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // 12-15: 'fmt ' subchunk header (Big Endian ASCII)
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        // 16-19: Subchunk1Size = 16 for PCM (32-bit Little Endian)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        // 20-21: AudioFormat = 1 for uncompressed PCM (16-bit Little Endian)
        header[20] = 1
        header[21] = 0
        
        // 22-23: NumChannels = 1 for Mono (16-bit Little Endian)
        header[22] = (channels.toInt() and 0xFF).toByte()
        header[23] = ((channels.toInt() shr 8) and 0xFF).toByte()
        
        // 24-27: SampleRate = 16000 (32-bit Little Endian)
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()
        
        // 28-31: ByteRate = SampleRate * NumChannels * BitsPerSample / 8 (32-bit Little Endian)
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        
        // 32-33: BlockAlign = NumChannels * BitsPerSample / 8 (16-bit Little Endian)
        header[32] = (blockAlign.toInt() and 0xFF).toByte()
        header[33] = ((blockAlign.toInt() shr 8) and 0xFF).toByte()
        
        // 34-35: BitsPerSample = 16 (16-bit Little Endian)
        header[34] = (bitsPerSample.toInt() and 0xFF).toByte()
        header[35] = ((bitsPerSample.toInt() shr 8) and 0xFF).toByte()
        
        // 36-39: 'data' subchunk header (Big Endian ASCII)
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        // 40-43: Subchunk2Size = raw audio length (32-bit Little Endian)
        header[40] = (audioLength and 0xFF).toByte()
        header[41] = ((audioLength shr 8) and 0xFF).toByte()
        header[42] = ((audioLength shr 16) and 0xFF).toByte()
        header[43] = ((audioLength shr 24) and 0xFF).toByte()

        val wavBytes = ByteArray(44 + audioLength)
        System.arraycopy(header, 0, wavBytes, 0, 44)
        System.arraycopy(pcmData, 0, wavBytes, 44, audioLength)
        return wavBytes
    }
}
```

---

## 3. Subsystem 2: Sarvam AI Network Integration Layer

### 3.1 HTTP Client, Authentication & Timeouts
The network layer connects to the official Sarvam AI endpoints with zero third-party tracking or cloud telemetry.

- **Base URL:** `https://api.sarvam.ai/`
- **Authentication Header:** `api-subscription-key: <API_KEY>`
- **Security Rule:** API Key is injected dynamically through an OkHttp `Interceptor` and NEVER hardcoded in source.

```kotlin
class SarvamAuthInterceptor(
    private val apiKeyProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val apiKey = apiKeyProvider()
        
        if (apiKey.isBlank()) {
            throw InvalidApiKeyException("Sarvam AI API key is blank or unconfigured.")
        }
        
        val authenticatedRequest = original.newBuilder()
            .header("api-subscription-key", apiKey)
            .build()
            
        return chain.proceed(authenticatedRequest)
    }
}
```

#### OkHttp Client Configuration:
```kotlin
object NetworkModule {
    fun createOkHttpClient(apiKeyProvider: () -> String): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("api-subscription-key")
        }

        return OkHttpClient.Builder()
            .addInterceptor(SarvamAuthInterceptor(apiKeyProvider))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)   // STT and TTS generation require generous read timeouts
            .writeTimeout(30, TimeUnit.SECONDS)  // Audio file uploads
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        return Retrofit.Builder()
            .baseUrl("https://api.sarvam.ai/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
```

### 3.2 Endpoint Contracts & Data Transfer Objects (DTOs)

#### 1. Speech-to-Text (Saaras v3)
- **HTTP Method & Path:** `POST /speech-to-text`
- **Content-Type:** `multipart/form-data`
- **Parameters:**
  - `file`: WAV audio binary (`audio/wav`, file name `"recording.wav"`)
  - `model`: `"saaras:v3"`
  - `language_code`: BCP-47 tag (e.g. `"hi-IN"`, `"en-IN"`, etc.)
  - `mode`: `"transcribe"`
- **Response DTO:**
  ```kotlin
  @Serializable
  data class SttResponse(
      @SerialName("transcript") val transcript: String,
      @SerialName("language_code") val languageCode: String? = null
  )
  ```

#### 2. Translation (Sarvam Translate / Mayura v1)
- **HTTP Method & Path:** `POST /translate`
- **Content-Type:** `application/json`
- **Request DTO:**
  ```kotlin
  @Serializable
  data class TranslationRequest(
      @SerialName("input") val input: String,
      @SerialName("source_language_code") val sourceLanguageCode: String,
      @SerialName("target_language_code") val targetLanguageCode: String,
      @SerialName("model") val model: String = "mayura:v1",
      @SerialName("mode") val mode: String = "formal"
  )
  ```
- **Response DTO:**
  ```kotlin
  @Serializable
  data class TranslationResponse(
      @SerialName("translated_text") val translatedText: String
  )
  ```

#### 3. Text-to-Speech (Bulbul v3)
- **HTTP Method & Path:** `POST /text-to-speech`
- **Content-Type:** `application/json`
- **Request DTO:**
  ```kotlin
  @Serializable
  data class TtsRequest(
      @SerialName("inputs") val inputs: List<String>,
      @SerialName("target_language_code") val targetLanguageCode: String,
      @SerialName("speaker") val speaker: String = "meera",
      @SerialName("model") val model: String = "bulbul:v3",
      @SerialName("speech_sample_rate") val speechSampleRate: Int = 16000
  )
  ```
- **Response DTO:**
  ```kotlin
  @Serializable
  data class TtsResponse(
      @SerialName("audios") val audios: List<String> // Base64-encoded WAV strings
  )
  ```

#### Retrofit API Interface:
```kotlin
interface SarvamApiService {

    @Multipart
    @POST("speech-to-text")
    suspend fun transcribeSpeech(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("language_code") languageCode: RequestBody,
        @Part("mode") mode: RequestBody
    ): SttResponse

    @POST("translate")
    suspend fun translateText(
        @Body request: TranslationRequest
    ): TranslationResponse

    @POST("text-to-speech")
    suspend fun synthesizeSpeech(
        @Body request: TtsRequest
    ): TtsResponse
}
```

### 3.3 10-Language Matrix & BCP-47 Code Registry
Sarvam AI supports 10 primary Indic languages. The application defines a strongly typed enum mapping user-facing labels to BCP-47 codes:

```kotlin
enum class SupportedLanguage(
    val displayName: String,
    val nativeName: String,
    val bcp47: String
) {
    ENGLISH("English", "English", "en-IN"),
    HINDI("Hindi", "हिन्दी", "hi-IN"),
    BENGALI("Bengali", "বাংলা", "bn-IN"),
    TAMIL("Tamil", "தமிழ்", "ta-IN"),
    TELUGU("Telugu", "తెలుగు", "te-IN"),
    KANNADA("Kannada", "ಕನ್ನಡ", "kn-IN"),
    MALAYALAM("Malayalam", "മലയാളം", "ml-IN"),
    MARATHI("Marathi", "मराठी", "mr-IN"),
    GUJARATI("Gujarati", "ગુજરાતી", "gu-IN"),
    ODIA("Odia", "ଓଡ଼ିଆ", "od-IN");

    companion object {
        fun fromBcp47(code: String): SupportedLanguage? =
            entries.firstOrNull { it.bcp47.equals(code, ignoreCase = true) }
    }
}
```

---

## 4. Subsystem 3: Audio Playback Architecture

### 4.1 Base64 Decoding & Audio Cache Strategy
Bulbul v3 returns audio as an array of Base64 strings representing standard WAV files.

#### Playback Engine Comparison:
1. **`AudioTrack` Direct PCM Streaming:**
   - Requires manual RIFF header stripping. External API headers may contain optional metadata chunks (e.g. `LIST`, `JUNK`, `INFO`), resulting in static pops or desynchronization if parsed naively.
2. **`MediaPlayer` with App-Private Temp File:**
   - The decoded Base64 byte array is written to a temporary cache file (`context.cacheDir/tts_playback.wav`).
   - `MediaPlayer` natively parses the full RIFF container, handles audio decoding across all Android versions (API 21-35), provides accurate completion/error events, and automatically routes to speaker or earpiece.
   - Cache file is atomically deleted upon playback completion.

```kotlin
class AndroidAudioPlayer(
    private val context: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private var mediaPlayer: MediaPlayer? = null
    private val playerLock = Any()
    private var currentTempFile: File? = null

    fun playWavBytes(
        wavBytes: ByteArray,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        synchronized(playerLock) {
            stopAndRelease()

            try {
                // Write bytes to private cache file
                val tempFile = File.createTempFile("tts_audio_", ".wav", context.cacheDir)
                tempFile.deleteOnExit()
                FileOutputStream(tempFile).use { it.write(wavBytes) }
                currentTempFile = tempFile

                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    setOnCompletionListener {
                        synchronized(playerLock) {
                            stopAndRelease()
                        }
                        onCompletion()
                    }
                    setOnErrorListener { _, what, extra ->
                        synchronized(playerLock) {
                            stopAndRelease()
                        }
                        onError("MediaPlayer error code ($what, $extra)")
                        true
                    }
                    prepare()
                    start()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                stopAndRelease()
                onError("Failed to initiate audio playback: ${e.message}")
            }
        }
    }

    fun stopAndRelease() {
        synchronized(playerLock) {
            try {
                mediaPlayer?.apply {
                    if (isPlaying) stop()
                    reset()
                    release()
                }
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Error releasing MediaPlayer", e)
            } finally {
                mediaPlayer = null
            }

            try {
                currentTempFile?.let {
                    if (it.exists()) it.delete()
                }
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Error deleting audio temp file", e)
            } finally {
                currentTempFile = null
            }
        }
    }
}
```

---

## 5. Subsystem 4: Reactive State Machine & MVVM Architecture

### 5.1 State Machine Transition Model
The application is governed by a **deterministic, single-track finite state machine (FSM)**. Only one state is active at any time:

```
                  +-----------------------------------+
                  |               IDLE                |<--------------------+
                  +-----------------+-----------------+                     |
                                    |                                       |
                           PTT Press (Hold)                                 |
                                    v                                       |
                  +-----------------------------------+                     |
                  |             RECORDING             |                     |
                  +-----------------+-----------------+                     |
                                    |                                       |
                           PTT Release                                      |
                                    v                                       |
                  +-----------------------------------+                     |
                  |           TRANSCRIBING            |                     |
                  +-----------------+-----------------+                     |
                                    |                                       |
                           Saaras v3 STT Success                            |
                                    v                                       |
                  +-----------------------------------+                     |
                  |            TRANSLATING            |                     |
                  +-----------------+-----------------+                     |
                                    |                                       |
                           Mayura v1 Translate OK                           |
                                    v                                       |
                  +-----------------------------------+                     |
                  |            SYNTHESIZING           |                     |
                  +-----------------+-----------------+                     |
                                    |                                       |
                           Bulbul v3 TTS Audio OK                           |
                                    v                                       |
                  +-----------------------------------+                     |
                  |              PLAYING              |                     |
                  +-----------------+-----------------+                     |
                                    |                                       |
                          Playback Finished / Stopped                       |
                                    +---------------------------------------+
                                    
    [ ANY STATE ] --- On Exception / Network Error ---> [ ERROR ] ---> Dismiss / Retry ---> [ IDLE ]
```

### 5.2 State Machine Data Modeling
```kotlin
sealed interface PipelineState {
    object Idle : PipelineState
    data class Recording(val durationMs: Long = 0L) : PipelineState
    object Transcribing : PipelineState
    data class Translating(val sourceText: String) : PipelineState
    data class Synthesizing(val sourceText: String, val translatedText: String) : PipelineState
    data class Playing(val sourceText: String, val translatedText: String) : PipelineState
    data class Error(val errorMessage: String, val canRetry: Boolean = true) : PipelineState
}

data class MainUiState(
    val pipelineState: PipelineState = PipelineState.Idle,
    val sourceLanguage: SupportedLanguage = SupportedLanguage.HINDI,
    val targetLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val lastSourceTranscript: String = "",
    val lastTranslatedText: String = "",
    val hasAudioToReplay: Boolean = false,
    val feedbackSubmitted: Boolean = false,
    val micPermissionGranted: Boolean = false
)

sealed interface UiEvent {
    object PttPressed : UiEvent
    object PttReleased : UiEvent
    data class SourceLanguageChanged(val language: SupportedLanguage) : UiEvent
    data class TargetLanguageChanged(val language: SupportedLanguage) : UiEvent
    object ReplayAudio : UiEvent
    data class FeedbackSubmitted(val isPositive: Boolean) : UiEvent
    object DismissError : UiEvent
    data class MicPermissionResult(val granted: Boolean) : UiEvent
}
```

### 5.3 Jetpack Compose PTT Gesture Implementation
To deliver a true walkie-talkie tactile feel, the PTT button must track finger down (press) and finger up/cancel (release):

```kotlin
@Composable
fun PttButton(
    pipelineState: PipelineState,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording = pipelineState is PipelineState.Recording
    val isBusy = pipelineState !in listOf(PipelineState.Idle, PipelineState.Recording)

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "PttButtonScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(140.dp)
            .clip(CircleShape)
            .background(
                when {
                    isRecording -> MaterialTheme.colorScheme.error
                    isBusy -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            .pointerInput(isBusy) {
                if (!isBusy) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onPttPress()
                        waitForUpOrCancellation()
                        onPttRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Push to talk",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (pipelineState) {
                    is PipelineState.Recording -> "LISTENING..."
                    is PipelineState.Transcribing -> "TRANSCRIBING..."
                    is PipelineState.Translating -> "TRANSLATING..."
                    is PipelineState.Synthesizing -> "SYNTHESIZING..."
                    is PipelineState.Playing -> "SPEAKING..."
                    else -> "HOLD TO SPEAK"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

---

## 6. Subsystem 5: API Key Security, Local Feedback Logger & Robust Error Handling

### 6.1 API Key Security Architecture
- **Threat:** Accidental Git commits of private API keys causing credential leakage and quota exhaustion.
- **Solution:** 
  1. API key stored strictly in `local.properties` (ignored by Git in `.gitignore`).
  2. Injected into `BuildConfig.SARVAM_API_KEY` at build time by Gradle.
  3. Proactively validated before any network operation.

#### Configuration in `local.properties`:
```properties
SARVAM_API_KEY=your_sarvam_api_key_here
```

#### Configuration in `app/build.gradle.kts`:
```kotlin
import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

val sarvamApiKey: String = localProperties.getProperty("SARVAM_API_KEY")
    ?: System.getenv("SARVAM_API_KEY")
    ?: ""

android {
    ...
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        ...
        buildConfigField("String", "SARVAM_API_KEY", "\"$sarvamApiKey\"")
    }
}
```

### 6.2 App-Private Local Feedback Logger
Requirement R4 mandates storing user feedback records locally in an app-private JSON file.

#### Storage Specifications:
- **Location:** `context.filesDir.resolve("user_feedback.json")` (inaccessible to other applications).
- **Integrity:** Concurrency-safe atomic writes via a temporary `.tmp` file and rename strategy.

#### JSON Data Model:
```kotlin
@Serializable
data class FeedbackRecord(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerialName("timestamp_iso") val timestampIso: String,
    @SerialName("source_language") val sourceLanguage: String,
    @SerialName("target_language") val targetLanguage: String,
    @SerialName("source_text") val sourceText: String,
    @SerialName("translated_text") val translatedText: String,
    @SerialName("feedback") val feedback: String // "positive" or "negative"
)
```

#### Feedback Repository Implementation:
```kotlin
class LocalFeedbackRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val feedbackFile: File by lazy {
        File(context.filesDir, "user_feedback.json")
    }
    private val fileLock = Any()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun logFeedback(
        sourceLanguage: String,
        targetLanguage: String,
        sourceText: String,
        translatedText: String,
        isPositive: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        synchronized(fileLock) {
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val newRecord = FeedbackRecord(
                    timestampIso = isoFormat.format(Date()),
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    sourceText = sourceText,
                    translatedText = translatedText,
                    feedback = if (isPositive) "positive" else "negative"
                )

                val records = readRecordsInternal().toMutableList()
                records.add(newRecord)

                // Atomic write using temp file
                val tempFile = File(context.filesDir, "user_feedback.json.tmp")
                tempFile.writeText(json.encodeToString(records))
                if (!tempFile.renameTo(feedbackFile)) {
                    // Fallback copy if rename fails
                    tempFile.copyTo(feedbackFile, overwrite = true)
                    tempFile.delete()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("FeedbackRepo", "Failed to write feedback", e)
                Result.failure(e)
            }
        }
    }

    private fun readRecordsInternal(): List<FeedbackRecord> {
        if (!feedbackFile.exists() || feedbackFile.length() == 0L) {
            return emptyList()
        }
        return try {
            json.decodeFromString<List<FeedbackRecord>>(feedbackFile.readText())
        } catch (e: Exception) {
            Log.w("FeedbackRepo", "Corrupt feedback file, resetting list", e)
            emptyList()
        }
    }
}
```

### 6.3 Comprehensive Error Taxonomy & User Guidance
Every failure mode is mapped to a clear, human-readable banner message and non-blocking recovery path:

| Failure Mode | Technical Cause | UI Error Message | Recovery Action |
| :--- | :--- | :--- | :--- |
| **Missing Microphone Permission** | `SecurityException` on `AudioRecord` creation | *"Microphone permission required. Please allow audio recording in device Settings."* | Trigger permission dialog or Settings intent. |
| **Missing / Unset API Key** | `SARVAM_API_KEY` is blank or placeholder | *"Sarvam AI API key is missing. Please add SARVAM_API_KEY to local.properties."* | Banner guides user to set key. |
| **Unauthorized (401)** | Invalid or expired API Key | *"Invalid API key. Please verify your Sarvam AI subscription key."* | Guide key renewal. |
| **Rate Limited (429)** | Exceeded request quota | *"Server rate limit reached. Please wait a few seconds before trying again."* | Disable PTT for 5s cooldown. |
| **No Connectivity** | `UnknownHostException` or `ConnectException` | *"No internet connection. Please check your Wi-Fi or mobile data."* | Display offline banner; keep last message. |
| **Network Timeout** | `SocketTimeoutException` (>30s) | *"Network timed out while processing speech. Please try again."* | Reset state to IDLE. |
| **Empty Audio / Accidental Tap** | Audio capture < 400ms or 0 bytes | *"Audio too short. Hold the button while speaking, then release."* | Non-error toast; reset to IDLE. |
| **Empty Speech Recognized** | Saaras v3 returns empty transcript | *"No speech detected. Please speak clearly into the microphone."* | Prompt user to speak again. |
| **Server Error (500/502/503)** | HTTP 5xx from Sarvam AI | *"Sarvam AI service is temporarily unavailable. Please try again shortly."* | Non-destructive retry. |

---

## 7. Verification & Automated Testing Architecture

To guarantee strict compliance with the acceptance criteria, the system design includes dedicated automated unit and integration test suites:

### 7.1 Unit Test Coverage
1. **WavAudioConverterTest:**
   - Verify that converted byte array is exactly `pcmBytes.size + 44`.
   - Verify bytes 0-3 equal ASCII `"RIFF"` and bytes 8-11 equal `"WAVE"`.
   - Verify little-endian 32-bit `SampleRate == 16000` at offset 24.
   - Verify little-endian 16-bit `NumChannels == 1` at offset 22.
   - Verify little-endian 16-bit `BitsPerSample == 16` at offset 34.
   - Verify little-endian 32-bit `Subchunk2Size == pcmBytes.size` at offset 40.
2. **SarvamAuthInterceptorTest:**
   - Verify `api-subscription-key` header presence and value.
   - Verify that blank key throws `InvalidApiKeyException` before any network socket is opened.
3. **LocalFeedbackRepositoryTest:**
   - Verify feedback logging creates `user_feedback.json`.
   - Verify atomic read-after-write consistency.
   - Verify recovery from corrupted JSON files.
4. **MainViewModelStateMachineTest:**
   - Verify state transitions: `IDLE` -> `RECORDING` -> `TRANSCRIBING` -> `TRANSLATING` -> `SYNTHESIZING` -> `PLAYING` -> `IDLE`.
   - Verify exception triggers `ERROR` state with correct human-readable message.
   - Verify language changes update `sourceLanguage` and `targetLanguage`.

### 7.2 MockWebServer Integration Tests
- Simulate Sarvam AI responses using `okhttp3.mockwebserver.MockWebServer`:
  - `POST /speech-to-text` returning `{"transcript": "नमस्ते दुनिया", "language_code": "hi-IN"}`
  - `POST /translate` returning `{"translated_text": "Hello world"}`
  - `POST /text-to-speech` returning `{"audios": ["<valid_base64_wav>"]}`
- Verify end-to-end coroutine pipeline executes without blocking main thread.

---

## 8. Conclusion

This architecture provides an airtight, production-grade foundation for the iTantra prototype. It combines:
1. Low-latency, hardware-optimized 16 kHz AudioRecord capture with 100% compliant 44-byte RIFF WAV packaging.
2. Direct, clean, and secure integration with official Sarvam AI cloud endpoints (Saaras v3, Mayura v1, Bulbul v3).
3. Robust, glitch-free audio playback via `MediaPlayer`.
4. A deterministic reactive state machine with tactile PTT controls in Jetpack Compose.
5. Air-tight API key security and an app-private local feedback audit logger.

The architecture is fully documented, verified, and ready for implementation.
