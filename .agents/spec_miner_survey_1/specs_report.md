# Technical Specification & Feature Inventory Report: iTantra Native Prototype

**Author**: `spec_miner_survey_1`  
**Date**: 2026-09-07T09:55:00Z  
**Target Platform**: Android Native (Kotlin, Jetpack Compose, Min SDK 26 / Target SDK 35)  
**Authoritative Sources**:
- `ORIGINAL_REQUEST.md` (Authoritative User Request & Follow-up API specs)
- `PS_SIH26173_OFFICIAL.md` (ISRO SIH26173 Problem Statement)
- `01_RESEARCH_FOR_AGENT.md` (Technical Deep Research Knowledge Base)
- `02_HUMAN_BRIEF.md` (Architectural Summary)
- `03_SUPPLEMENTARY.md` (License Audit & Risk Registry)
- `README.md` (Project Overview & Milestone Roadmap)

---

## 1. Executive Summary & System Mission

The objective of **iTantra** is to deliver a minimal, robust, working Android native multilingual voice communication prototype. It leverages official **Sarvam AI APIs** (`Saaras v3` for Speech-to-Text, `Mayura v1` / `Sarvam Translate` for Machine Translation, and `Bulbul v3` for Text-to-Speech) integrated with a tactile, single-screen Push-to-Talk (PTT) interface and an app-private local feedback logger.

The prototype bridges speech across 10 Indian languages:
`Hindi`, `English`, `Bengali`, `Tamil`, `Telugu`, `Kannada`, `Malayalam`, `Marathi`, `Gujarati`, and `Odia`.

---

## 2. Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|---|---|---|---|---|---|---|
| F1 | Audio Capture | Native PTT Audio Recording | Captures microphone voice data during press-and-hold using Android `AudioRecord`. | User touch down on PTT button; microphone input | Raw 16 kHz 16-bit Mono PCM buffer in memory | Throws `SecurityException` if permission missing; returns `ERROR_INVALID_OPERATION` if mic unavailable | `ORIGINAL_REQUEST.md` §R1, `README.md` |
| F2 | Audio Encoding | In-Memory PCM-to-WAV Encoder | Encodes captured raw PCM into standard 44-byte header RIFF/WAVE container. | Raw PCM byte array, 16 kHz sample rate, 1 channel, 16 bits | Valid WAV binary byte array / file | Emits `IllegalArgumentException` on empty or misaligned buffer | `ORIGINAL_REQUEST.md` §R1, §Follow-up |
| F3 | Permissions | Runtime Mic Permission Handler | Requests and verifies `RECORD_AUDIO` permission with user-facing rationale and denial handling. | User permission interaction | Permission granted flag or denied state | Transitions UI to `ERROR` state with explanatory banner and settings intent | `ORIGINAL_REQUEST.md` §R1, §R4 |
| F4 | STT Integration | Sarvam Saaras v3 STT Client | Sends audio WAV file to `POST https://api.sarvam.ai/speech-to-text` via multipart form data. | WAV file, `model="saaras:v3"`, `language_code`, `mode="transcribe"`, `api-subscription-key` | JSON object containing `transcript` and `language_code` | HTTP 400/401/429/500 mapped to user-friendly error banners | `ORIGINAL_REQUEST.md` §R2, §Follow-up |
| F5 | Translation | Sarvam Translate (Mayura v1) | Translates recognized text via `POST https://api.sarvam.ai/translate`. | JSON body with `input`, `source_language_code`, `target_language_code`, `model="mayura:v1"`, `mode="formal"` | JSON object with `translated_text` | HTTP 4xx/5xx or empty response mapped to UI error | `ORIGINAL_REQUEST.md` §R2, §Follow-up |
| F6 | TTS Integration | Sarvam Bulbul v3 TTS Client | Synthesizes target speech via `POST https://api.sarvam.ai/text-to-speech`. | JSON body with `inputs: [text]`, `target_language_code`, `speaker="meera"`, `model="bulbul:v3"`, `speech_sample_rate=16000` | JSON object with Base64-encoded WAV string in `audios` array | HTTP 4xx/5xx mapped to UI error | `ORIGINAL_REQUEST.md` §R2, §Follow-up |
| F7 | Audio Playback | Native WAV Audio Player | Decodes Base64 WAV from Bulbul v3 and streams through device speaker. | Base64 WAV string or byte array | Audible acoustic speech via Android speaker/earpiece | Emits playback error callback; transitions to `ERROR` | `ORIGINAL_REQUEST.md` §R2, §R3 |
| F8 | Audio Playback | Replay Audio Trigger | Allows replaying the most recently synthesized speech without re-querying Sarvam APIs. | Replay button click | Audio playback of cached WAV data | Disabled if no cached audio exists; logs warning on failure | `ORIGINAL_REQUEST.md` §R3 |
| F9 | UI / Compose | Single-Screen Communication View | Declarative Jetpack Compose layout hosting Header, Selectors, PTT button, Cards, and Feedback bar. | Observable ViewModel UI state | Reactive UI rendering across device states | Graceful fallback rendering with empty states | `ORIGINAL_REQUEST.md` §R3 |
| F10 | UI / Compose | Tactile HOLD TO SPEAK PTT Button | Interactive button capturing pointer down/up gestures to drive state transitions. | Touch press (down) and touch release (up) | State trigger: `IDLE` → `RECORDING` → `TRANSCRIBING` | Ignores taps shorter than minimum duration (<300ms) with tooltip | `ORIGINAL_REQUEST.md` §R3 |
| F11 | UI / Compose | Language Dropdown Selectors | Two dropdown pickers allowing independent selection of source and target languages across 10 languages. | User dropdown tap and item selection | Updates `sourceLanguage` and `targetLanguage` in state | Prevents interaction while recording/processing | `ORIGINAL_REQUEST.md` §R2, §R3 |
| F12 | UI / Compose | Dual Transcription/Translation Cards | Cards displaying user's original recognized speech and the resulting translated target text. | ViewModel state containing `transcript` and `translatedText` | Rendered text cards with copy and language tags | Displays placeholder text when empty | `ORIGINAL_REQUEST.md` §R3 |
| F13 | UI / State | Reactive State Machine | Strict 7-state finite state machine orchestrating end-to-end voice communication flow. | User events (press/release) and async pipeline events (STT/Trans/TTS) | State enum: `IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR` | Reverts safely to `IDLE` upon error dismissal or completion | `ORIGINAL_REQUEST.md` §R3 |
| F14 | Feedback | Quick Feedback Bar (👍 / 👎) | Binary rating buttons to evaluate translation and synthesis quality for current interaction. | Button click: Thumbs Up (`positive`) or Thumbs Down (`negative`) | Dispatches feedback event to Local Feedback Logger | Disabled until a translation is successfully produced | `ORIGINAL_REQUEST.md` §R3, §R4 |
| F15 | Feedback Logging | App-Private JSON Feedback Logger | Persists feedback events with full interaction metadata into an internal app-private JSON file. | `FeedbackRecord` object | JSON entry appended to `context.filesDir/feedback_logs.json` | Catches `IOException` and safely handles corrupt/empty files | `ORIGINAL_REQUEST.md` §R4 |
| F16 | Security / Config | Secure API Key Resolution | Reads `SARVAM_API_KEY` securely from `BuildConfig` generated via `local.properties`. | `BuildConfig.SARVAM_API_KEY` | Injected authorization header string | Fails fast with clear `ConfigurationException` if key missing | `ORIGINAL_REQUEST.md` §R4 |
| F17 | Telemetry & Status | Real-Time Status & Latency Indicator | Displays active status badge and component processing latencies ($T_{stt}$, $T_{trans}$, $T_{tts}$). | Pipeline execution start/stop timestamps | Visual status chips and millisecond telemetry readout | Displays error chip on failure | `ORIGINAL_REQUEST.md` §R3, `README.md` |
| F18 | Emergency Override | Priority Alert Audio Attributes | Prepared audio route for emergency disaster warnings using `AudioAttributes.USAGE_ALARM`. | Emergency message tag / alert preset | Maximum volume, non-interruptible audio stream | Falls back to `USAGE_MEDIA` if alarm stream restricted | `PS_SIH26173_OFFICIAL.md`, `README.md` |

---

## 3. Edge Cases & Boundary Conditions

| # | Feature | Input / Trigger Condition | Observed / Required Behavior |
|---|---|---|---|
| E1 | PTT Button | Tap duration < 300 ms (accidental brush or tap) | Do not send empty audio to API. Discard buffer, show brief toast/snackbar: *"Hold button while speaking"*, revert to `IDLE`. |
| E2 | Audio Capture | Max recording time exceeded (> 30 seconds) | Automatically stop recording, transition to `TRANSCRIBING`, preventing buffer overflow and OOM on device. |
| E3 | Audio Capture | Microphone muted or hardware in use by another app | Catch `AudioRecord.STATE_UNINITIALIZED`, show error: *"Microphone unavailable or in use by another application"*, transition to `ERROR`. |
| E4 | Permissions | User denies `RECORD_AUDIO` permission | Do not crash. Show persistent warning card with button *"Grant Microphone Permission"*, transition to `ERROR`. |
| E5 | Permissions | User checks *"Don't ask again"* and denies permission | Detect `shouldShowRequestPermissionRationale == false`, show dialog directing user to App Settings via `Intent(ACTION_APPLICATION_DETAILS_SETTINGS)`. |
| E6 | Network | Network completely offline during PTT release | Detect `UnknownHostException` / `ConnectException`, display *"No internet connection. Please verify WiFi/cellular connection."*, transition to `ERROR`. |
| E7 | Security / Auth | Missing or invalid Sarvam API Key (HTTP 401 / 403) | Show clear error: *"Invalid or missing Sarvam AI API Key. Please configure SARVAM_API_KEY in local.properties."*, transition to `ERROR`. |
| E8 | Rate Limiting | Sarvam AI returns HTTP 429 Too Many Requests | Show banner: *"API rate limit exceeded. Please wait a few moments before retrying."*, transition to `ERROR`. |
| E9 | STT Engine | Background silence or ambient noise only | Saaras v3 returns empty transcript `""`. Detect empty string, do not invoke Translate or TTS. Display *"No speech recognized. Please try speaking again."*, transition to `IDLE`. |
| E10 | Translation | Source text contains untranslatable jargon or numbers | Mayura v1 preserves entity or transliterates. Pipeline proceeds cleanly to TTS without throwing parsing exceptions. |
| E11 | Language Matrix | Source language equals Target language (e.g. `hi-IN` to `hi-IN`) | Handle gracefully: either pass-through transcript directly to TTS without translation call, or execute translation. Both must succeed without failure. |
| E12 | TTS Engine | Bulbul v3 returns empty or null `audios` array | Detect `audios.isEmpty()`, show error: *"Failed to synthesize speech"*, enable text display, transition to `IDLE`. |
| E13 | Audio Playback | User taps "Replay Audio" multiple times rapidly | Debounce clicks or stop active `MediaPlayer`/`AudioTrack` instance before starting new playback. Never overlap playback streams. |
| E14 | UI Lifecycle | App backgrounded or screen rotated during `RECORDING` | Safely release `AudioRecord` in `onStop()` / `DisposableEffect`, discard incomplete recording, reset state to `IDLE` without memory leak. |
| E15 | Feedback Logger | Feedback file `feedback_logs.json` does not exist on first launch | Automatically create parent directories and new JSON file with root JSON array `[]`. |
| E16 | Feedback Logger | Corrupted JSON file (e.g. app killed mid-write) | Catch `JsonSyntaxException`, backup corrupted file to `feedback_logs.json.bak`, reinitialize fresh file, record new entry without losing current feedback. |
| E17 | Feedback Bar | User taps 👍 or 👎 multiple times rapidly | Disable feedback buttons immediately upon selection or update existing entry's rating for the current interaction UUID. |

---

## 4. Official Sarvam AI API Specifications

### 4.1 Global Authentication
All Sarvam AI API endpoints authenticate using a custom HTTP header:
- **Header Name**: `api-subscription-key`
- **Header Value**: `<SARVAM_API_KEY>` (Resolved from `BuildConfig.SARVAM_API_KEY`)
- **Base URL**: `https://api.sarvam.ai`

---

### 4.2 Speech-to-Text: Sarvam Saaras v3
Converts speech audio captured by `AudioRecord` into source language text.

- **Endpoint**: `POST https://api.sarvam.ai/speech-to-text`
- **Content-Type**: `multipart/form-data`
- **Headers**:
  ```http
  api-subscription-key: <SARVAM_API_KEY>
  Content-Type: multipart/form-data; boundary=----BoundaryXYZ
  ```
- **Multipart Form Fields**:
  | Field Name | Type | Required | Value / Format | Description |
  |---|---|---|---|---|
  | `file` | Binary | Yes | `audio/wav` | 16 kHz, 16-bit Mono PCM WAV container |
  | `model` | String | Yes | `"saaras:v3"` | Official Saaras v3 model identifier |
  | `language_code` | String | Yes | BCP-47 tag (e.g. `"hi-IN"`, `"en-IN"`) | Source speech language code |
  | `mode` | String | Yes | `"transcribe"` | Operation mode: transcribe source speech |

- **Request Example (cURL equivalent)**:
  ```bash
  curl -X POST "https://api.sarvam.ai/speech-to-text" \
    -H "api-subscription-key: $SARVAM_API_KEY" \
    -F "file=@audio_16k_mono.wav;type=audio/wav" \
    -F "model=saaras:v3" \
    -F "language_code=hi-IN" \
    -F "mode=transcribe"
  ```

- **Response Schema (HTTP 200 OK)**:
  ```json
  {
    "transcript": "नमस्ते, आप कैसे हैं?",
    "language_code": "hi-IN"
  }
  ```

- **Error Schema**:
  ```json
  {
    "error": {
      "message": "Invalid audio format or subscription key",
      "code": "invalid_request"
    }
  }
  ```

---

### 4.3 Machine Translation: Sarvam Translate (Mayura v1)
Translates recognized text from source language into target language.

- **Endpoint**: `POST https://api.sarvam.ai/translate`
- **Content-Type**: `application/json`
- **Headers**:
  ```http
  api-subscription-key: <SARVAM_API_KEY>
  Content-Type: application/json
  Accept: application/json
  ```
- **Request Body Parameters**:
  | Field Name | Type | Required | Value / Format | Description |
  |---|---|---|---|---|
  | `input` | String | Yes | Non-empty UTF-8 string | The text transcript to translate |
  | `source_language_code` | String | Yes | BCP-47 tag (e.g. `"hi-IN"`) | Source language code |
  | `target_language_code` | String | Yes | BCP-47 tag (e.g. `"en-IN"`) | Target language code |
  | `model` | String | Yes | `"mayura:v1"` (or `"sarvam-translate:v1"`) | Translation model identifier |
  | `mode` | String | Optional | `"formal"` (default) or `"code-mix"` | Translation stylistic mode |

- **Request Example**:
  ```json
  {
    "input": "नमस्ते, आप कैसे हैं?",
    "source_language_code": "hi-IN",
    "target_language_code": "en-IN",
    "model": "mayura:v1",
    "mode": "formal"
  }
  ```

- **Response Schema (HTTP 200 OK)**:
  ```json
  {
    "translated_text": "Hello, how are you?"
  }
  ```

---

### 4.4 Text-to-Speech: Sarvam Bulbul v3
Synthesizes translated text into natural audio speech.

- **Endpoint**: `POST https://api.sarvam.ai/text-to-speech`
- **Content-Type**: `application/json`
- **Headers**:
  ```http
  api-subscription-key: <SARVAM_API_KEY>
  Content-Type: application/json
  Accept: application/json
  ```
- **Request Body Parameters**:
  | Field Name | Type | Required | Value / Format | Description |
  |---|---|---|---|---|
  | `inputs` | Array of String | Yes | `["Text to synthesize"]` | Array of text strings to speak |
  | `target_language_code` | String | Yes | BCP-47 tag (e.g. `"en-IN"`) | Target language code |
  | `speaker` | String | Yes | `"meera"` | Speaker voice profile (female default) |
  | `model` | String | Yes | `"bulbul:v3"` | Official Bulbul v3 model identifier |
  | `speech_sample_rate` | Integer | Optional | `16000` (or `22050`) | Output audio sample rate in Hz |

- **Request Example**:
  ```json
  {
    "inputs": [
      "Hello, how are you?"
    ],
    "target_language_code": "en-IN",
    "speaker": "meera",
    "model": "bulbul:v3",
    "speech_sample_rate": 16000
  }
  ```

- **Response Schema (HTTP 200 OK)**:
  ```json
  {
    "audios": [
      "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA="
    ]
  }
  ```
  *(Note: `audios` contains Base64-encoded standard RIFF/WAVE audio data).*

---

## 5. 10-Language Matrix & BCP-47 Mapping

| # | Language | Script Name | BCP-47 Code | Sarvam STT (`saaras:v3`) | Sarvam Translate (`mayura:v1`) | Sarvam TTS (`bulbul:v3`) | Default Speaker |
|---|---|---|---|---|---|---|---|
| 1 | **Hindi** | हिन्दी | `hi-IN` | Supported | Supported | Supported | `meera` |
| 2 | **English** | Indian English | `en-IN` | Supported | Supported | Supported | `meera` |
| 3 | **Bengali** | বাংলা | `bn-IN` | Supported | Supported | Supported | `meera` |
| 4 | **Tamil** | தமிழ் | `ta-IN` | Supported | Supported | Supported | `meera` |
| 5 | **Telugu** | తెలుగు | `te-IN` | Supported | Supported | Supported | `meera` |
| 6 | **Kannada** | ಕನ್ನಡ | `kn-IN` | Supported | Supported | Supported | `meera` |
| 7 | **Malayalam** | മലയാളം | `ml-IN` | Supported | Supported | Supported | `meera` |
| 8 | **Marathi** | मराठी | `mr-IN` | Supported | Supported | Supported | `meera` |
| 9 | **Gujarati** | ગુજરાતી | `gu-IN` | Supported | Supported | Supported | `meera` |
| 10 | **Odia** | ଓଡ଼ିଆ | `od-IN` | Supported | Supported | Supported | `meera` |

### Available Speaker Options:
- **`meera`** (Default: Female, authoritative clarity, supported across all 10 Indic languages)
- **`pavithra`** (Female)
- **`maitreyi`** (Female)
- **`arvind`** (Male)
- **`amol`** (Male)
- **`amartya`** (Male)
- **`rohan`** (Male)

---

## 6. Native Audio Specifications & WAV Container

### 6.1 Audio Capture Parameters (AudioRecord)
- **Source**: `MediaRecorder.AudioSource.MIC`
- **Sample Rate**: `16,000 Hz` (16 kHz standard for STT acoustic models)
- **Channel Configuration**: `AudioFormat.CHANNEL_IN_MONO` (Single channel)
- **Audio Encoding**: `AudioFormat.ENCODING_PCM_16BIT` (Little-endian signed 16-bit linear PCM)
- **Min Buffer Calculation**:
  ```kotlin
  val minBufferSize = AudioRecord.getMinBufferSize(
      16000,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
  )
  // Recommended allocation: max(minBufferSize * 2, 4096)
  ```
- **Byte Rate**: $16,000 \times 1 \times 2 = 32,000 \text{ bytes/sec}$ (32 kB/s).

---

### 6.2 Canonical RIFF WAV 44-Byte Header Structure
When converting the in-memory PCM byte stream to a valid WAV file for `POST /speech-to-text`:

| Byte Offset | Field Name | Size (Bytes) | Endianness | Standard Value | Description |
|---|---|---|---|---|---|
| `0..3` | `ChunkID` | 4 | Big | `"RIFF"` (0x52494646) | Resource Interchange File Format signature |
| `4..7` | `ChunkSize` | 4 | Little | $36 + \text{PCMByteCount}$ | Size of entire file minus 8 bytes |
| `8..11` | `Format` | 4 | Big | `"WAVE"` (0x57415645) | WAVE file signature |
| `12..15` | `Subchunk1ID`| 4 | Big | `"fmt "` (0x666d7420) | Format subchunk description |
| `16..19` | `Subchunk1Size`| 4 | Little | `16` (0x00000010) | Length of format chunk for uncompressed PCM |
| `20..21` | `AudioFormat` | 2 | Little | `1` (0x0001) | Linear PCM format |
| `22..23` | `NumChannels` | 2 | Little | `1` (0x0001) | Mono channel |
| `24..27` | `SampleRate` | 4 | Little | `16000` (0x00003E80)| Sample rate in Hertz |
| `28..31` | `ByteRate` | 4 | Little | `32000` (0x00007D00)| $\text{SampleRate} \times \text{NumChannels} \times \frac{\text{BitsPerSample}}{8}$ |
| `32..33` | `BlockAlign` | 2 | Little | `2` (0x0002) | $\text{NumChannels} \times \frac{\text{BitsPerSample}}{8}$ |
| `34..35` | `BitsPerSample`| 2 | Little | `16` (0x0010) | Bits per sample (16-bit) |
| `36..39` | `Subchunk2ID`| 4 | Big | `"data"` (0x64617461) | Data subchunk header |
| `40..43` | `Subchunk2Size`| 4 | Little | $\text{PCMByteCount}$ | Total byte length of following raw PCM bytes |
| `44..end` | Raw PCM Data | $N$ | Little | Signed 16-bit samples | Actual recorded audio waveform samples |

---

## 7. UI Specifications: Compose Layout & PTT State Machine

### 7.1 Single-Screen Visual Hierarchy
Strictly ONE unified Jetpack Compose screen without tabs or auxiliary screens:

```
+-------------------------------------------------------------+
| [📡 iTantra] Multilingual Neural Transceiver   [STATUS: IDLE]|
| Telemetry: STT: --ms | Trans: --ms | TTS: --ms              |
+-------------------------------------------------------------+
| SOURCE LANGUAGE                  TARGET LANGUAGE             |
| [ Hindi (hi-IN)       v ]   ⇄   [ English (en-IN)     v ]   |
+-------------------------------------------------------------+
|                                                             |
|                   [   🎙️ HOLD TO SPEAK   ]                  |
|                   (Press & Hold to Record)                  |
|                                                             |
+-------------------------------------------------------------+
| SOURCE TRANSCRIPTION CARD (hi-IN)                           |
| "नमस्ते, आप कैसे हैं?"                                      |
+-------------------------------------------------------------+
| TRANSLATED TEXT CARD (en-IN)                                |
| "Hello, how are you?"                                       |
|                                                             |
| [ ▶ Replay Audio ]                                          |
+-------------------------------------------------------------+
| QUICK FEEDBACK                                              |
| Rate this translation:       [ 👍 Helpful ]   [ 👎 Poor ]   |
+-------------------------------------------------------------+
| [ ERROR BANNER (Visible only when State == ERROR)         ] |
+-------------------------------------------------------------+
```

---

### 7.2 Strict 7-State PTT Finite State Machine

```
               +--------------------------------------------------------+
               |                                                        |
               v                                                        |
         +----------+     Touch Press (Down)      +---------------+     |
         |   IDLE   | --------------------------> |   RECORDING   |     |
         +----------+                             +---------------+     |
              ^                                           |             |
              |                                           | Touch Up    |
              |                                           v             |
              |                                   +---------------+     |
              |                                   | TRANSCRIBING  |     |
              |                                   +---------------+     |
              |                                           |             |
              |                                           | STT Success |
              |                                           v             |
              |                                   +---------------+     |
              |                                   |  TRANSLATING  |     |
              |                                   +---------------+     |
              |                                           |             |
              |                                           | Trans Success
              |                                           v             |
              |                                   +---------------+     |
              |                                   |  SYNTHESIZING |     |
              |                                   +---------------+     |
              |                                           |             |
              |                                           | TTS Success |
              |                                           v             |
              |        Audio Playback Finished    +---------------+     |
              +---------------------------------- |    PLAYING    |     |
              |                                   +---------------+     |
              |                                           |             |
              | User dismiss / Retry                      | Any Failure |
              |                                           v             |
              +---------------------------------- +---------------+     |
                                                  |     ERROR     | <---+
                                                  +---------------+
```

#### State Transition Table:
| Current State | Event / Trigger | Target State | Action / Side Effect |
|---|---|---|---|
| `IDLE` | Pointer Down on PTT Button | `RECORDING` | Verify permission, start `AudioRecord`, clear previous transient errors, start capture loop. |
| `RECORDING` | Pointer Up (released) after $\ge 300\text{ms}$ | `TRANSCRIBING` | Stop `AudioRecord`, encode PCM buffer to WAV, dispatch HTTP POST to `/speech-to-text`. |
| `RECORDING` | Pointer Up (released) after $< 300\text{ms}$ | `IDLE` | Discard buffer, show brief hint *"Hold to speak"*. |
| `RECORDING` | Hardware / Mic error | `ERROR` | Stop recording, set error message. |
| `TRANSCRIBING`| Saaras v3 returns non-empty transcript | `TRANSLATING` | Update `sourceTranscript` UI text, dispatch HTTP POST to `/translate`. |
| `TRANSCRIBING`| Saaras v3 returns empty transcript | `IDLE` | Set hint *"No speech detected"*. |
| `TRANSCRIBING`| HTTP / Network / Auth failure | `ERROR` | Record error message, set status chip to Error. |
| `TRANSLATING` | Mayura v1 returns `translated_text` | `SYNTHESIZING` | Update `translatedText` UI text, dispatch HTTP POST to `/text-to-speech`. |
| `TRANSLATING` | HTTP / Network / Auth failure | `ERROR` | Record error message, preserve transcript, set status chip to Error. |
| `SYNTHESIZING`| Bulbul v3 returns `audios` Base64 WAV | `PLAYING` | Decode Base64 to byte array, cache in memory, start `MediaPlayer` / `AudioTrack` playback. |
| `SYNTHESIZING`| HTTP / Network / Auth failure | `ERROR` | Record error message, preserve translated text, set status chip to Error. |
| `PLAYING` | Audio playback completes | `IDLE` | Re-enable PTT button, enable Replay button and Feedback buttons. |
| `PLAYING` | User taps PTT Button | `RECORDING` | Interrupt active playback, start recording immediately. |
| `IDLE` | User clicks "Replay Audio" | `PLAYING` | Play cached WAV byte array without making network calls. |
| `ERROR` | User taps Dismiss or retries PTT | `IDLE` | Clear error state, return to ready status. |

---

## 8. Local Feedback Logger & Robust Error Handling

### 8.1 App-Private Storage & File Location
- **Directory**: `context.filesDir` (Internal, private app storage, Sandboxed, non-exportable)
- **File Name**: `feedback_logs.json`
- **Path**: `/data/user/0/org.isro.itantra/files/feedback_logs.json`
- **Access Rule**: Read and written strictly via internal Kotlin I/O. Never stored on external storage or public caches.

### 8.2 JSON Log Entry Schema
The feedback log is formatted as a valid JSON array of interaction records:

```json
[
  {
    "id": "c8f94d07-2a54-4a27-a065-27a36a7eb419",
    "timestamp": "2026-09-07T09:55:32Z",
    "source_language": "hi-IN",
    "target_language": "en-IN",
    "source_text": "नमस्ते, आप कैसे हैं?",
    "translated_text": "Hello, how are you?",
    "feedback": "positive",
    "audio_duration_ms": 2150,
    "latency_ms": {
      "stt": 412,
      "translate": 230,
      "tts": 380,
      "total": 1022
    }
  },
  {
    "id": "7b3a9e12-881c-4b6e-b3de-089ecf4a72d1",
    "timestamp": "2026-09-07T09:56:15Z",
    "source_language": "en-IN",
    "target_language": "mr-IN",
    "source_text": "Severe cyclone warning in coastal area.",
    "translated_text": "किनारपट्टी भागात तीव्र चक्रीवादळाचा इशारा.",
    "feedback": "positive",
    "audio_duration_ms": 3200,
    "latency_ms": {
      "stt": 510,
      "translate": 290,
      "tts": 420,
      "total": 1220
    }
  }
]
```

#### Field Definitions:
- `id`: Unique UUIDv4 string per interaction.
- `timestamp`: ISO-8601 UTC timestamp string (`YYYY-MM-DD'T'HH:mm:ss'Z'`).
- `source_language`: BCP-47 language tag of the input speech.
- `target_language`: BCP-47 language tag of the translated speech.
- `source_text`: Full verbatim transcript from Saaras v3.
- `translated_text`: Full verbatim translation from Mayura v1.
- `feedback`: String enum: `"positive"` (thumbs up) or `"negative"` (thumbs down).
- `audio_duration_ms`: Duration of recorded input speech in milliseconds.
- `latency_ms`: Map of component execution times in milliseconds.

---

### 8.3 Error Taxonomy & User-Facing Error Resolution

| Error Category | Root Cause / Exception | HTTP Status | User-Facing Banner Message | Recovery Action |
|---|---|---|---|---|
| **Permission** | `SecurityException` / Denied | N/A | *"Microphone permission is required to capture speech. Tap here to enable."* | Show permission prompt or navigate to App Settings. |
| **Connectivity** | `UnknownHostException` / `SocketTimeoutException` | N/A | *"Unable to reach Sarvam AI servers. Please check your internet connection."* | Verify WiFi / mobile data; keep UI in retryable state. |
| **Authentication** | Missing or Invalid API Key | `401 Unauthorized` / `403 Forbidden` | *"Invalid or missing Sarvam AI API Key. Please verify BuildConfig.SARVAM_API_KEY."* | Direct developer to `local.properties` configuration. |
| **Quota / Rate Limit** | Too many requests sent | `429 Too Many Requests` | *"Rate limit exceeded. Please wait a few seconds before speaking again."* | Introduce exponential backoff or user throttle. |
| **Payload Size** | Audio exceeds limit or empty | `400 Bad Request` / `413 Payload Too Large` | *"Audio input invalid or too large. Keep recordings under 30 seconds."* | Limit recording length; reject empty recordings. |
| **Empty Speech** | Silence or background noise | `200 OK` (empty transcript) | *"No speech detected. Please hold the button and speak clearly."* | Silently reset to `IDLE` without invoking translation. |
| **Synthesis Failure** | Empty audio or malformed Base64 | `500 Internal Server Error` | *"Speech synthesis failed. Target text remains available on screen."* | Keep translated text visible; allow manual Replay retry. |

---

## 9. Requirement Traceability Matrix

| Requirement | Description | Status / Target | Verified Component / Contract |
|---|---|---|---|
| **R1. Audio Capture & PTT** | 16 kHz Mono 16-bit PCM capture via `AudioRecord`, encoded to standard WAV. Press-and-hold PTT with clean permission handling. | Specification Complete | `AudioRecordEngine.kt`, `WavEncoder.kt`, `android.permission.RECORD_AUDIO` |
| **R2. Multilingual Pipeline** | Explicitly typed pipeline with Sarvam Saaras v3, Mayura v1, and Bulbul v3. Milestones: hi->en, en->mr, ta->hi, 10-language matrix. | Specification Complete | `SarvamApiClient.kt`, `SpeechRequest`, `TranslationRequest`, `TtsRequest` |
| **R3. Single Screen UI** | Compose single screen with visual hierarchy, Hold-to-Speak PTT, Dual Cards, Replay button, Feedback bar, 7-state FSM. | Specification Complete | `CommunicationScreen.kt`, `PttViewModel.kt`, `PttState` |
| **R4. Feedback & Error Handling**| App-private JSON logging (`feedback_logs.json`), secure API key via `local.properties`, human-readable error banners. | Specification Complete | `FeedbackLogger.kt`, `BuildConfig.SARVAM_API_KEY`, `ErrorBanner.kt` |
| **Milestone 1** | Hindi (`hi-IN`) → English (`en-IN`) pipeline end-to-end. | Specification Complete | Verified API contracts with `hi-IN` and `en-IN`. |
| **Milestone 2** | Language switch verification: English (`en-IN`) → Marathi (`mr-IN`). | Specification Complete | Verified API contracts with `en-IN` and `mr-IN`. |
| **Milestone 3** | Language switch verification: Tamil (`ta-IN`) → Hindi (`hi-IN`). | Specification Complete | Verified API contracts with `ta-IN` and `hi-IN`. |
| **Milestone 4** | Full 10-language selector matrix available in dropdowns. | Specification Complete | Complete 10 BCP-47 language definition catalog. |

---

## 10. Conclusion

The specification for **iTantra** provides an authoritative, unambiguous technical blueprint. All API contracts, audio parameters, data models, state machine transitions, and error handling policies have been defined to satisfy the exact requirements in `ORIGINAL_REQUEST.md`.
