# Milestone M1 Changes Report: Audio Engine & Sarvam AI Pipeline

**Agent:** `worker_m1_1`  
**Milestone:** M1 (Audio Engine & Sarvam AI Pipeline)  
**Date:** 2026-09-07T10:29:00Z  

---

## 1. Summary of Changes

Milestone M1 establishes the core audio capture, WAV encoding, audio playback, and Sarvam AI network communication pipeline for the iTantra Android application, supporting the end-to-end Hindi -> English communication flow and full 10-language Indic matrix compatibility.

---

## 2. Modified & Created Files

### Production Code

1. **`app/build.gradle.kts`**:
   - Added quote stripping and trimming for `sarvamApiKey` (`rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()`).
   - Added support for both `sarvam.api.key` and `SARVAM_API_KEY` property names from `local.properties` and system environment variables.

2. **`app/src/main/java/com/itantra/voice/data/Language.kt`**:
   - Enhanced `Language.fromBcp47(code: String)` to sanitize input strings by trimming whitespace and normalizing underscore delimiters (`_`) to RFC 5646 hyphens (`-`).

3. **`app/src/main/java/com/itantra/voice/audio/WavEncoder.kt`**:
   - Implemented pure-Kotlin in-memory PCM-to-WAV converter.
   - Generates standard canonical 44-byte RIFF header (ChunkID "RIFF", Format "WAVE", Subchunk1ID "fmt ", AudioFormat 1 for linear PCM, NumChannels 1, SampleRate 16000, ByteRate 32000, BlockAlign 2, BitsPerSample 16, Subchunk2ID "data").
   - Encodes all multi-byte integer header fields in standard little-endian format.
   - Supports zero-length and arbitrary PCM byte lengths safely without clipping.

4. **`app/src/main/java/com/itantra/voice/audio/AudioRecorder.kt`**:
   - Implemented native `AudioRecord` engine configured for 16 kHz Mono 16-bit PCM.
   - Sized hardware buffer via `AudioRecord.getMinBufferSize` with 2x scaling (minimum 4096-byte floor).
   - Non-blocking coroutine streaming loop executed on `Dispatchers.IO`.
   - Real-time normalized peak amplitude calculation emitted via `StateFlow<Float>` (0.0f to 1.0f) for UI visualization.
   - 30-second auto-stop threshold (`MAX_RECORDING_BYTES = 960,000 bytes`) preventing buffer overflows.
   - Introduced `NativeAudioRecord` interface to allow seamless hardware recording on device and deterministic JVM unit testing.

5. **`app/src/main/java/com/itantra/voice/audio/AudioPlayer.kt`**:
   - Implemented native audio player decoding Base64 WAV audio responses from Sarvam Bulbul v3.
   - Atomically writes decoded WAV bytes to an app-private cache file (`tts_playback_*.wav`) and cleans it up upon completion or interruption.
   - Plays audio via `MediaPlayer` with `AudioAttributes.CONTENT_TYPE_SPEECH`.
   - Supports standard accessibility audio routing (`USAGE_ASSISTANCE_ACCESSIBILITY`) and emergency broadcast override (`USAGE_ALARM`).
   - Provides completion and error callback hooks with zero audio clicks/pops or resource leaks.

6. **`app/src/main/java/com/itantra/voice/network/SarvamApiModels.kt`**:
   - Strictly typed request and response DTOs serialized via `kotlinx.serialization`:
     - `SpeechResponse`: `transcript`, `language_code`
     - `TranslationRequest`: `input`, `source_language_code`, `target_language_code`, `model="mayura:v1"`, `mode="formal"`
     - `TranslationResponse`: `translated_text`
     - `TtsRequest`: `inputs`, `target_language_code`, `speaker="meera"`, `model="bulbul:v3"`, `speech_sample_rate=16000`
     - `TtsResponse`: `audios`
     - `SarvamErrorResponse`: `error` (`message`, `code`)

7. **`app/src/main/java/com/itantra/voice/network/SarvamApiClient.kt`**:
   - OkHttp 4.12.0 client connecting to official Sarvam endpoints:
     - `POST /speech-to-text`: multipart form-data (`model="saaras:v3"`, `language_code`, `mode="transcribe"`, `file="recording.wav"`)
     - `POST /translate`: JSON payload (`input`, `source_language_code`, `target_language_code`, `model="mayura:v1"`, `mode="formal"`)
     - `POST /text-to-speech`: JSON payload (`inputs`, `target_language_code`, `speaker="meera"`, `model="bulbul:v3"`, `speech_sample_rate=16000`)
   - `api-subscription-key` authentication header interceptor dynamically reading `BuildConfig.SARVAM_API_KEY`.
   - Redacted authentication header logging via `HttpLoggingInterceptor`.
   - 30-second read and write timeouts.
   - Domain exception hierarchy: `AuthenticationException` (401), `ForbiddenException` (403), `RateLimitException` (429), `ServerException` (5xx), `NetworkException` (timeouts, connectivity loss), and `EmptyResponseException`.

---

### Test Suites

8. **`app/src/test/java/com/itantra/voice/audio/WavEncoderTest.kt`**:
   - 12 unit tests verifying 44-byte RIFF header structure, little-endian byte offsets, 16 kHz mono 16-bit audio format parameters, zero-length PCM, odd byte lengths, 30s buffer sizes, custom sample rates, stereo channels, and boundary validations.

9. **`app/src/test/java/com/itantra/voice/audio/AudioRecorderTest.kt`**:
   - 9 unit tests verifying 16 kHz Mono 16-bit PCM configuration, chunk streaming, `stopRecording()` byte return, real-time amplitude calculation, hardware release, zero-byte recordings, 30s auto-stop threshold, uninitialized hardware handling, and read error recovery.

10. **`app/src/test/java/com/itantra/voice/audio/AudioPlayerTest.kt`**:
    - 12 unit tests verifying Base64 WAV decoding, speech audio attributes, completion callbacks, error callbacks, temp file cleanup, `USAGE_ALARM` emergency routing, corrupt header rejection, short payload handling, invalid Base64 rejection, rapid playback interruption, and multithreaded synchronization.

11. **`app/src/test/java/com/itantra/voice/network/SarvamApiClientTest.kt`**:
    - 11 unit tests verifying Saaras v3 STT multipart dispatch, Mayura v1 Translate JSON dispatch, Bulbul v3 TTS JSON dispatch, HTTP 401 mapping, HTTP 429 rate limit mapping, HTTP 500 server error mapping, fast validation failures for empty audio/text, empty audio array handling, and placeholder key detection.

12. **`app/src/test/java/com/itantra/voice/LanguageEdgeCaseStressTest.kt`**:
    - Updated assertions to verify the newly implemented underscore-to-hyphen and whitespace trimming normalization in `Language.fromBcp47()`.

---

## 3. Verification Commands & Results

- `./gradlew assembleDebug`: **BUILD SUCCESSFUL** (APK binary generated)
- `./gradlew testDebugUnitTest --rerun-tasks`: **BUILD SUCCESSFUL** (97 tests executed across 11 test suites, 0 failures, 0 errors, 0 skipped, 100% pass rate)
- `./gradlew lintDebug`: **BUILD SUCCESSFUL** (0 errors)
