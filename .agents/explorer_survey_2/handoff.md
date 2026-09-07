# Handoff Report — Audio, Network, and State Machine Architecture Investigation

**Agent:** `explorer_survey_2`  
**Working Directory:** `/Users/spirit/Downloads/spiritsih/.agents/explorer_survey_2`  
**Date:** 2026-09-07  
**Report Target:** `architecture_report.md`  
**Handoff Type:** Hard (Task complete)

---

## 1. Observation

1. **User Request & API Specifications:**
   - In `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md`:
     - Lines 12–14 (R1): "Implement tactile press-and-hold PTT recording using native Android AudioRecord (16 kHz, 16-bit Mono PCM encoded to WAV). Real-time transition: IDLE → RECORDING on press, RECORDING → PROCESSING on release. Handle RECORD_AUDIO permissions cleanly."
     - Lines 16–25 (R2): Defines explicit pipeline:
       - `POST https://api.sarvam.ai/speech-to-text` multipart: `file`, `model="saaras:v3"`, `language_code`, `mode="transcribe"`.
       - `POST https://api.sarvam.ai/translate` JSON: `input`, `source_language_code`, `target_language_code`, `model="mayura:v1"`, `mode="formal"`.
       - `POST https://api.sarvam.ai/text-to-speech` JSON: `inputs`, `target_language_code`, `speaker="meera"`, `model="bulbul:v3"`, `speech_sample_rate=16000`.
     - Lines 27–36 (R3): Single communication screen UI with Compose + MVVM, status indicator, language dropdowns, tactile PTT button, transcription card, translated card + replay, feedback bar (👍/👎), reactive state machine: `IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`.
     - Lines 37–41 (R4): "Store user feedback (`timestamp`, `source_language`, `target_language`, `source_text`, `translated_text`, `feedback=positive|negative`) locally in an app-private JSON file. User-friendly human-readable errors... Secure API key handling via local.properties / BuildConfig.SARVAM_API_KEY".
     - Lines 63–132: Follow-up confirming exact headers (`api-subscription-key: <KEY>`), Base64 WAV response from Bulbul v3, and the 10 BCP-47 language codes: `en-IN`, `hi-IN`, `bn-IN`, `ta-IN`, `te-IN`, `kn-IN`, `ml-IN`, `mr-IN`, `gu-IN`, `od-IN`.

2. **Repository Workspace State:**
   - Git command `git status && git log -n 5 --oneline` revealed commit `9818f47 chore: wipe all previous app code to restart from scratch`.
   - Workspace search for `local.properties` and Gradle files returned 0 matches, confirming clean slate for the new architecture.
   - Command `env | grep -i SARVAM` verified no ambient `SARVAM_API_KEY` in environment, mandating robust fallback and clear UI guidance when the key is not set.

3. **Background Systems Context:**
   - In `/Users/spirit/Downloads/spiritsih/docs/ITANTRA_SECURITY_AUDIT_REPORT.md` (lines 165–172, 180–186): Historical audit verified that background audio operations must run on `Dispatchers.IO` to ensure 0 UI thread blocking and ANR defense.

---

## 2. Logic Chain

1. **Audio Capture and WAV Container Packaging (Obs 1):**
   - Saaras v3 STT explicitly requires standard 16 kHz 16-bit Mono PCM WAV audio.
   - Raw audio from `AudioRecord` produces bare PCM samples without container metadata.
   - Therefore, captured audio bytes must be wrapped with a canonical 44-byte RIFF header before HTTP upload.
   - Sizing the buffer using `AudioRecord.getMinBufferSize(16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)` scaled by 2x prevents HAL buffer underruns, while non-blocking streaming into a `ByteArrayOutputStream` on `Dispatchers.IO` ensures zero ANRs.

2. **Network Layer Design & API Authentication (Obs 1, Obs 2):**
   - Official Sarvam endpoints require the custom header `api-subscription-key: <KEY>` (not Bearer tokens).
   - An OkHttp `Interceptor` dynamically injects this header for all requests and redacts it from logs.
   - Connecting directly to `https://api.sarvam.ai/` with 30s read timeouts accommodates cloud speech recognition and neural synthesis latencies over mobile networks.
   - Defining typed Retrofit interfaces with `kotlinx.serialization` guarantees strict metadata handling across all 10 BCP-47 language codes.

3. **Audio Playback Engine Selection (Obs 1):**
   - Bulbul v3 returns Base64-encoded WAV files containing full RIFF headers.
   - Attempting to play raw audio via `AudioTrack` requires manual stripping of RIFF chunks and risk clicks/pops on variable header metadata.
   - In contrast, writing decoded Base64 WAV bytes to an app-private cache file (`context.cacheDir`) and feeding it to Android's `MediaPlayer` provides native container parsing, robust completion/error listeners, automatic speaker routing, and safe lifecycle disposal.

4. **Reactive State Machine & Single Screen Compose UI (Obs 1, Obs 3):**
   - A linear unidirectional state machine (`IDLE` -> `RECORDING` -> `TRANSCRIBING` -> `TRANSLATING` -> `SYNTHESIZING` -> `PLAYING` -> `IDLE` or `ERROR`) exposed via `StateFlow<MainUiState>` prevents invalid concurrent operations (e.g. attempting to transcribe while recording).
   - Jetpack Compose pointer input (`awaitEachGesture`, `awaitFirstDown`, `waitForUpOrCancellation`) implements the tactile hold-to-speak walkie-talkie behavior.

5. **Security & Feedback Persistence (Obs 1, Obs 2):**
   - To satisfy the zero-leakage requirement, `SARVAM_API_KEY` is loaded from `local.properties` via `build.gradle.kts` into `BuildConfig.SARVAM_API_KEY`.
   - When missing, the state machine gracefully halts before making network calls and guides the user.
   - Local feedback is saved directly to `context.filesDir/user_feedback.json` using atomic file writes (`.tmp` file rename) to prevent JSON corruption on app crash or sudden termination.

---

## 3. Caveats

1. **Cloud Service Dependency:**
   - The architecture connects to live Sarvam AI endpoints. End-to-end operational testing requires an active internet connection and a valid Sarvam AI API subscription key.
2. **Local Environment Inspection:**
   - Local Android SDK and CLI tool validation is delegated to peer agent `explorer_survey_1`.
3. **No Code Implementation in this Phase:**
   - This phase is strictly read-only analysis and architecture specification. No production source code files have been written to the project workspace.

---

## 4. Conclusion

The technical architecture for iTantra is fully designed, documented, and verified against all functional and security requirements in `/Users/spirit/Downloads/spiritsih/.agents/explorer_survey_2/architecture_report.md`. It provides complete implementation-ready blueprints for:
- 16 kHz Mono PCM `AudioRecord` background recording with 44-byte RIFF WAV converter.
- OkHttp/Retrofit network client with `api-subscription-key` interceptor and DTOs for Saaras v3, Mayura v1, and Bulbul v3.
- Pop-free `MediaPlayer` audio playback for Base64 WAV data.
- Reactive `StateFlow` finite state machine for Jetpack Compose.
- Secure `BuildConfig` API key injection and atomic local JSON feedback logging.

---

## 5. Verification Method

1. **Inspect Report Artifact:**
   - Review `/Users/spirit/Downloads/spiritsih/.agents/explorer_survey_2/architecture_report.md` for complete code designs and mathematical specifications.
2. **RIFF WAV Header Verification:**
   - Validate header byte array math:
     $$\text{ChunkSize} = 36 + \text{Subchunk2Size}$$
     $$\text{ByteRate} = 16000 \times 1 \times 2 = 32000 \text{ bytes/sec}$$
     $$\text{BlockAlign} = 1 \times 2 = 2 \text{ bytes}$$
3. **Invalidation Conditions:**
   - Any modification to the Sarvam AI API contract by Sarvam AI (e.g. changes to endpoint paths, model identifiers, or auth headers).
   - Inability of the target Android runtime (API 35) to allocate `AudioRecord` with 16 kHz mono PCM.
