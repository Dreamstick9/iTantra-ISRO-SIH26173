# Project: iTantra Native Android Multilingual Prototype

## Architecture
iTantra is an Android native multilingual voice communication prototype connecting Indian language speakers using official Sarvam AI APIs.
- **Platform**: Android Native (Kotlin 2.3.20, Min SDK 26, Target SDK 35, Jetpack Compose BOM 2026.03.01, AGP 9.0.1, Gradle 9.1.0).
- **Core Modules & Layers**:
  - `com.itantra.voice.audio`: Native `AudioRecord` background stream (16 kHz Mono 16-bit PCM), pure Kotlin 44-byte RIFF `WavEncoder`, and pop-free `AudioPlayer` for Base64 WAV playback via `MediaPlayer`.
  - `com.itantra.voice.network`: OkHttp 4.12.0 client with `api-subscription-key` authentication header interceptor, connecting to official Sarvam endpoints (`saaras:v3` STT, `mayura:v1` translation, `bulbul:v3` TTS).
  - `com.itantra.voice.data`: Language definitions (10 BCP-47 Indic languages), atomic file-based `FeedbackLogger` (`context.filesDir/feedback_logs.json`), and typed data models.
  - `com.itantra.voice.ui`: Jetpack Compose single-screen UI (`MainScreen`), unidirectional 7-state finite state machine (`MainViewModel`), tactile Hold-to-Speak PTT touch gesture handler, and human-readable error banners.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---|---|---|---|
| F1 | Native PTT Audio Recording | 16 kHz 16-bit Mono PCM capture via AudioRecord on Dispatchers.IO | M1 | ORIGINAL_REQUEST §R1 |
| F2 | In-Memory PCM-to-WAV Encoder | Pure Kotlin 44-byte canonical RIFF WAV container encoder | M1 | ORIGINAL_REQUEST §R1 |
| F3 | Runtime Mic Permission Handler | Runtime RECORD_AUDIO verification with user explanation | M2 | ORIGINAL_REQUEST §R1, §R4 |
| F4 | Sarvam Saaras v3 STT Client | POST /speech-to-text multipart (model="saaras:v3", mode="transcribe") | M1 | ORIGINAL_REQUEST §R2 |
| F5 | Sarvam Translate Client | POST /translate JSON (model="mayura:v1", mode="formal") | M1 | ORIGINAL_REQUEST §R2 |
| F6 | Sarvam Bulbul v3 TTS Client | POST /text-to-speech JSON (model="bulbul:v3", speaker="meera", rate=16000) | M1 | ORIGINAL_REQUEST §R2 |
| F7 | Native WAV Audio Player | Base64 WAV decode and streaming via MediaPlayer without pops | M1 | ORIGINAL_REQUEST §R2 |
| F8 | Replay Audio Trigger | Replays cached translated speech without re-querying APIs | M2 | ORIGINAL_REQUEST §R3 |
| F9 | Single-Screen Compose View | Unified screen: Header, Selectors, PTT button, Cards, Feedback | M2 | ORIGINAL_REQUEST §R3 |
| F10 | Tactile PTT Button | Press-and-hold touch pointerInput gesture handler | M2 | ORIGINAL_REQUEST §R3 |
| F11 | Language Dropdown Selectors | Independent source and target language dropdown pickers | M2, M3 | ORIGINAL_REQUEST §R2, §R3 |
| F12 | Dual Text Display Cards | Cards for source recognized transcript and translated text | M2 | ORIGINAL_REQUEST §R3 |
| F13 | Reactive 7-State Machine | IDLE, RECORDING, TRANSCRIBING, TRANSLATING, SYNTHESIZING, PLAYING, ERROR | M2 | ORIGINAL_REQUEST §R3 |
| F14 | Quick Feedback Bar | Binary thumbs up / thumbs down buttons | M3 | ORIGINAL_REQUEST §R3, §R4 |
| F15 | Local JSON Feedback Logger | App-private atomic persistence in feedback_logs.json | M3 | ORIGINAL_REQUEST §R4 |
| F16 | Secure API Key Resolution | BuildConfig.SARVAM_API_KEY from local.properties | M0 | ORIGINAL_REQUEST §R4 |
| F17 | Real-Time Telemetry & Status | Status badges and STT/Translate/TTS millisecond latency readouts | M2 | ORIGINAL_REQUEST §R3, README |
| F18 | Emergency Audio Route | USAGE_ALARM / priority audio attributes | M1 | PS_SIH26173_OFFICIAL |

## Milestones
| # | Name | Scope | Dependencies | Status | Outputs / Verification |
|---|---|---|---|---|---|
| M0 | Scaffolding & Core Architecture | Android scaffold, Kotlin DSL, Compose, OkHttp, BuildConfig API key | none | **DONE** | Scaffolding complete, 53 tests pass, APK built, verified on emulator-5554 (Gate: PASS) |
| M1 | Audio Engine & Sarvam AI Pipeline | AudioRecord, WavEncoder, AudioPlayer, Saaras v3, Mayura v1, Bulbul v3 (hi->en) | M0 | **DONE** | AudioRecorder, WavEncoder, AudioPlayer, SarvamApiClient, 147 unit tests pass, lint clean (Gate: PASS) |
| M2 | Single-Screen Compose UI & FSM | MainScreen, PTT Button, MainViewModel 7-state FSM, Permissions | M1 | IN_PROGRESS | Single screen Compose UI, Hold-to-Speak PTT, 7-state FSM |
| M3 | Feedback Logger & Language Matrix | feedback_logs.json, Quick Feedback bar, en->mr, ta->hi, 10-language matrix | M2 | PLANNED | Local feedback logger, language matrix switch |
| M4 | Final Milestone: E2E Verification & Hardening | 100% E2E test pass (Tiers 1-4), Tier 5 adversarial hardening, emulator verification | M3, TEST_READY | PLANNED | 100% E2E test pass, Tier 5 hardening, live emulator demo |

## Interface Contracts

### Audio Engine ↔ Network Pipeline
- `AudioRecorder.stopRecording(): ByteArray` -> returns raw 16 kHz Mono 16-bit PCM byte array.
- `WavEncoder.encode(pcmData: ByteArray, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray` -> returns canonical 44-byte RIFF WAV.
- `SarvamApiClient.transcribe(wavData: ByteArray, languageCode: String): Result<SpeechResponse>`
- `SarvamApiClient.translate(text: String, sourceLang: String, targetLang: String): Result<TranslationResponse>`
- `SarvamApiClient.synthesize(text: String, targetLang: String, speaker: String = "meera"): Result<TtsResponse>`
- `AudioPlayer.playBase64Wav(base64Wav: String, onComplete: () -> Unit, onError: (Throwable) -> Unit)`

### ViewModel ↔ UI Contract
- `MainViewModel.uiState: StateFlow<MainUiState>`
  - `state`: `PttState` (`IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`)
  - `sourceLanguage`: `Language` (default `Language.HINDI`)
  - `targetLanguage`: `Language` (default `Language.ENGLISH`)
  - `sourceTranscript`: `String`
  - `translatedText`: `String`
  - `hasAudioToReplay`: `Boolean`
  - `errorMessage`: `String?`
  - `latencies`: `LatencyStats`
- Events:
  - `onPttPress()`
  - `onPttRelease()`
  - `onSourceLanguageChange(Language)`
  - `onTargetLanguageChange(Language)`
  - `onReplayAudio()`
  - `onFeedback(Boolean)`
  - `onDismissError()`

### Feedback Logger Contract
- `FeedbackLogger.logFeedback(record: FeedbackRecord): Result<Unit>`
- Destination: `File(context.filesDir, "feedback_logs.json")`
- Format: JSON array of `FeedbackRecord(id, timestamp, source_language, target_language, source_text, translated_text, feedback, audio_duration_ms, latency_ms)`

## Code Layout
```
app/src/main/
├── AndroidManifest.xml
└── java/com/itantra/voice/
    ├── MainActivity.kt
    ├── data/
    │   ├── Language.kt
    │   ├── Models.kt
    │   └── FeedbackLogger.kt
    ├── audio/
    │   ├── AudioRecorder.kt
    │   ├── WavEncoder.kt
    │   └── AudioPlayer.kt
    ├── network/
    │   ├── SarvamApiClient.kt
    │   └── SarvamApiModels.kt
    └── ui/
        ├── MainViewModel.kt
        ├── MainScreen.kt
        └── theme/
            ├── Color.kt
            ├── Theme.kt
            └── Type.kt
app/src/test/java/com/itantra/voice/
├── audio/WavEncoderTest.kt
├── audio/AudioRecorderTest.kt
├── audio/AudioPlayerTest.kt
├── audio/AudioEngineAdversarialStressTest.kt
├── data/FeedbackLoggerTest.kt
├── data/LanguageTest.kt
├── network/SarvamApiClientTest.kt
├── network/SarvamApiClientStressTest.kt
└── ui/MainViewModelTest.kt
app/src/androidTest/java/com/itantra/voice/
└── ui/MainScreenE2ETest.kt
```
