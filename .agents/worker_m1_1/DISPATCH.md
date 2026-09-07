# DISPATCH — 2026-09-07T10:16:00Z

## Mission
You are `worker_m1_1`, a `teamwork_preview_worker` subagent executing Milestone M1: Audio Engine & Sarvam AI Pipeline (Hindi -> English E2E).
Your working directory is: `/Users/spirit/Downloads/spiritsih/.agents/worker_m1_1`
The project workspace is: `/Users/spirit/Downloads/spiritsih`
The authoritative user request is: `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md` (MUST read first).
The project index is: `/Users/spirit/Downloads/spiritsih/PROJECT.md` (MUST read).
The test specification is: `/Users/spirit/Downloads/spiritsih/TEST_INFRA.md` and `TEST_READY.md`.
Architecture blueprints: `/Users/spirit/Downloads/spiritsih/.agents/explorer_survey_2/architecture_report.md`.

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Write Ownership
You exclusively own and will create/modify:
- `app/src/main/java/com/itantra/voice/audio/AudioRecorder.kt`
- `app/src/main/java/com/itantra/voice/audio/WavEncoder.kt`
- `app/src/main/java/com/itantra/voice/audio/AudioPlayer.kt`
- `app/src/main/java/com/itantra/voice/network/SarvamApiClient.kt`
- `app/src/main/java/com/itantra/voice/network/SarvamApiModels.kt`
- `app/src/main/java/com/itantra/voice/data/Language.kt` (apply underscore & trim normalization)
- `app/build.gradle.kts` (apply quote sanitization for `sarvamApiKey`)
- `app/src/test/java/com/itantra/voice/audio/WavEncoderTest.kt`
- `app/src/test/java/com/itantra/voice/audio/AudioPlayerTest.kt`
- `app/src/test/java/com/itantra/voice/network/SarvamApiClientTest.kt`

## Task
1. Read `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md`, `PROJECT.md`, and `architecture_report.md`.
2. Implement the Native Audio Engine in `com.itantra.voice.audio`:
   - `AudioRecorder.kt`: Native `AudioRecord` implementation for 16 kHz Mono 16-bit PCM.
     - Sized via `AudioRecord.getMinBufferSize` with 2x scaling (minimum 4096 bytes).
     - Non-blocking coroutine streaming on `Dispatchers.IO`.
     - Max recording duration auto-stop (30 seconds) to prevent buffer overflow.
     - `StateFlow<Float>` emitting real-time audio amplitude for UI visualization.
     - Safe cancellation and hardware release.
   - `WavEncoder.kt`: In-memory pure Kotlin PCM-to-WAV converter.
     - Prepends standard canonical 44-byte RIFF header (ChunkID="RIFF", Format="WAVE", Subchunk1ID="fmt ", AudioFormat=1, NumChannels=1, SampleRate=16000, ByteRate=32000, BlockAlign=2, BitsPerSample=16, Subchunk2ID="data").
     - Little-endian 16-bit and 32-bit integer encoding.
     - Handles zero-length and arbitrary byte lengths safely without clipping.
   - `AudioPlayer.kt`: Base64 WAV playback via Android `MediaPlayer`.
     - Decodes Base64 WAV string from Bulbul v3.
     - Writes to temporary `.wav` file in `context.cacheDir`.
     - Plays via `MediaPlayer` with `AudioAttributes` (`CONTENT_TYPE_SPEECH`, default `USAGE_ASSISTANCE_ACCESSIBILITY`, and `USAGE_ALARM` support for emergency broadcast).
     - Completion callback and error callback.
     - Atomic cleanup of temporary files on completion/release, zero pops or audio resource leaks.
3. Implement the Sarvam AI Network Client in `com.itantra.voice.network`:
   - `SarvamApiModels.kt`: Strictly typed data models with `kotlinx.serialization` for requests and responses:
     - `SpeechResponse(val transcript: String, val language_code: String?)`
     - `TranslationRequest(val input: String, val source_language_code: String, val target_language_code: String, val model: String = "mayura:v1", val mode: String = "formal")`
     - `TranslationResponse(val translated_text: String)`
     - `TtsRequest(val inputs: List<String>, val target_language_code: String, val speaker: String = "meera", val model: String = "bulbul:v3", val speech_sample_rate: Int = 16000)`
     - `TtsResponse(val audios: List<String>)`
   - `SarvamApiClient.kt`: Retrofit or pure OkHttp 4.12.0 client:
     - `api-subscription-key` header interceptor dynamically reading `BuildConfig.SARVAM_API_KEY`.
     - Sensitive header redaction in logging interceptor.
     - 30-second read and write timeouts.
     - `transcribe(wavData: ByteArray, languageCode: String): Result<SpeechResponse>` (multipart form-data: `file` as WAV, `model="saaras:v3"`, `language_code`, `mode="transcribe"`).
     - `translate(text: String, sourceLang: String, targetLang: String): Result<TranslationResponse>` (JSON POST to `/translate`).
     - `synthesize(text: String, targetLang: String, speaker: String = "meera"): Result<TtsResponse>` (JSON POST to `/text-to-speech`).
     - Clear mapping of HTTP 401, 403, 429, 500, empty responses, and socket timeouts to descriptive exceptions.
4. Enhance `Language.kt` and `app/build.gradle.kts`:
   - In `Language.kt`: Normalize underscores and whitespace in `fromBcp47(code: String)` (`code.trim().replace('_', '-')`).
   - In `app/build.gradle.kts`: Strip wrapping quotes from `sarvamApiKey`.
5. Unit and Integration Test Verification:
   - Run `./gradlew assembleDebug`
   - Run `./gradlew test` (ensure all tests pass 100%, including `WavAudioOracleTest`, `SarvamMockContractTest`, `Tier3CrossFeatureTest`, and `Tier4RealWorldScenarioTest`).
6. Write `/Users/spirit/Downloads/spiritsih/.agents/worker_m1_1/changes.md` and `handoff.md`, then send a completion message.

## 2026-09-07T10:17:00Z
Implement Milestone M1: Audio Engine (AudioRecorder, WavEncoder, AudioPlayer) and Sarvam AI Pipeline (SarvamApiClient, SarvamApiModels) for Hindi -> English E2E. Verify with assembleDebug and test, write changes.md and handoff.md, and send a message when done.

