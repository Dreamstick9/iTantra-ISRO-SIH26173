# Original User Request

## 2026-09-07T09:51:06Z

iTantra: A minimal, working Android native multilingual voice communication prototype using official Sarvam AI APIs (Saaras v3 STT, Sarvam Translate, Bulbul v3 TTS) with a single-screen PTT interface and local feedback logger.

Working directory: /Users/spirit/Downloads/spiritsih
Integrity mode: development

## Requirements

### R1. Minimal Native Audio Capture & Push-to-Talk (PTT)
- Implement tactile press-and-hold PTT recording using native Android AudioRecord (16 kHz, 16-bit Mono PCM encoded to WAV).
- Real-time transition: IDLE → RECORDING on press, RECORDING → PROCESSING on release. Handle RECORD_AUDIO permissions cleanly.

### R2. Explicit Multilingual Pipeline with Official Sarvam AI Contracts
- Strictly typed requests passing explicit language metadata:
  - `SpeechRequest(sourceLanguage, audio)` → Sarvam Saaras v3 (`POST https://api.sarvam.ai/speech-to-text` multipart: `file`, `model="saaras:v3"`, `language_code`, `mode="transcribe"`)
  - `TranslationRequest(sourceLanguage, targetLanguage, text)` → Sarvam Translate (`POST https://api.sarvam.ai/translate` JSON: `input`, `source_language_code`, `target_language_code`, `model="sarvam-translate:v1"`)
  - `TtsRequest(targetLanguage, text, voice)` → Sarvam Bulbul v3 (`POST https://api.sarvam.ai/text-to-speech` JSON: `inputs`, `target_language_code`, `speaker="meera"`, `model="bulbul:v3"`)
- Development Milestones:
  - Milestone 1: **Hindi → English** pipeline working end-to-end.
  - Milestone 2: Verify language switch with **English → Marathi**.
  - Milestone 3: Verify language switch with **Tamil → Hindi**.
  - Milestone 4: Enable full 10-language selector matrix (English, Hindi, Bengali, Tamil, Telugu, Kannada, Malayalam, Marathi, Gujarati, Odia).

### R3. Single Communication Screen UI (Compose + MVVM)
- Strictly ONE main screen with visual hierarchy:
  - Header & status indicator
  - Source & Target Language dropdown selectors
  - Tactile HOLD TO SPEAK PTT button
  - User source transcription card
  - Translated target text card + Replay Audio button
  - Quick Feedback bar (👍 / 👎)
- Reactive single state machine: `IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`.

### R4. Local Feedback Logger & Robust Error Handling
- Store user feedback (`timestamp`, `source_language`, `target_language`, `source_text`, `translated_text`, `feedback=positive|negative`) locally in an app-private JSON file.
- User-friendly human-readable errors for mic permission, network, API keys, rate limits, empty results.
- Secure API key handling via `local.properties` / `BuildConfig.SARVAM_API_KEY` (never committed or hardcoded).

## Acceptance Criteria

### Audio & Pipeline Execution
- [ ] PTT press-and-hold captures clean 16 kHz PCM audio without memory leaks or clipping.
- [ ] Hindi speech is transcribed accurately by Saaras v3 into Hindi text.
- [ ] Hindi text is translated into English text via Sarvam Translate.
- [ ] English text is converted to natural English speech via Bulbul v3 and played through the device speaker.
- [ ] Pipeline succeeds for English → Marathi and Tamil → Hindi when language selections change.

### UI & Feedback
- [ ] Single main screen displays all states reactively without extraneous dashboards or tabs.
- [ ] Pressing 👍 or 👎 stores the exact interaction locally in JSON.
- [ ] Clear error banners for missing network, denied mic permission, or invalid API key.
- [ ] Clean build and test execution on Android emulator (`medium_phone` / API 35).

## Follow-up — 2026-09-07T09:51:49Z

# Verified Sarvam AI API Technical Specifications from Browser Subagent

Use these exact contracts for the implementation:

### 1. Authentication Header
- `api-subscription-key: <API_KEY>`

### 2. Speech-to-Text (Saaras v3)
- `POST https://api.sarvam.ai/speech-to-text`
- Request Header: `api-subscription-key: <KEY>`, `Content-Type: multipart/form-data`
- Parameters:
  - `file`: WAV audio file (16 kHz, 16-bit Mono PCM WAV)
  - `model`: `"saaras:v3"`
  - `language_code`: BCP-47 tag (e.g. `"hi-IN"`, `"en-IN"`, etc.)
  - `mode`: `"transcribe"`
- Response JSON:
  ```json
  {
    "transcript": "...",
    "language_code": "hi-IN"
  }
  ```

### 3. Translation (Sarvam Translate / Mayura)
- `POST https://api.sarvam.ai/translate`
- Request Header: `api-subscription-key: <KEY>`, `Content-Type: application/json`
- Request Body:
  ```json
  {
    "input": "...",
    "source_language_code": "hi-IN",
    "target_language_code": "en-IN",
    "model": "mayura:v1",
    "mode": "formal"
  }
  ```
- Response JSON:
  ```json
  {
    "translated_text": "..."
  }
  ```

### 4. Text-to-Speech (Bulbul v3)
- `POST https://api.sarvam.ai/text-to-speech`
- Request Header: `api-subscription-key: <KEY>`, `Content-Type: application/json`
- Request Body:
  ```json
  {
    "inputs": ["..."],
    "target_language_code": "en-IN",
    "speaker": "meera",
    "model": "bulbul:v3",
    "speech_sample_rate": 16000
  }
  ```
- Response JSON (Base64-encoded standard WAV string):
  ```json
  {
    "audios": ["UklGRiQAAABXQVZF..."]
  }
  ```

### 5. Language Codes (BCP-47)
- English: `en-IN`
- Hindi: `hi-IN`
- Bengali: `bn-IN`
- Tamil: `ta-IN`
- Telugu: `te-IN`
- Kannada: `kn-IN`
- Malayalam: `ml-IN`
- Marathi: `mr-IN`
- Gujarati: `gu-IN`
- Odia: `od-IN`
