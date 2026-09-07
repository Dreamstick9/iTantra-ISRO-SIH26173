# Handoff Report: Specification Mining & Feature Inventory

**Agent**: `spec_miner_survey_1`  
**Working Directory**: `/Users/spirit/Downloads/spiritsih/.agents/spec_miner_survey_1`  
**Handoff Type**: Hard (Task Complete)  
**Parent Conversation ID**: `9da65215-7c76-4ab0-8790-34b4580958ab` (original parent: `d2dc27dd-22a8-43b3-872c-adab2ea405c8`)  
**Artifact Produced**: `/Users/spirit/Downloads/spiritsih/.agents/spec_miner_survey_1/specs_report.md`

---

## 1. Observation

1. **Authoritative Request (`ORIGINAL_REQUEST.md`)**:
   - Lines 5–26 define the scope: *"iTantra: A minimal, working Android native multilingual voice communication prototype using official Sarvam AI APIs (Saaras v3 STT, Sarvam Translate, Bulbul v3 TTS) with a single-screen PTT interface and local feedback logger."*
   - Lines 12–14 (R1): Native audio recording with `AudioRecord` (16 kHz, 16-bit Mono PCM encoded to WAV); press-and-hold PTT gesture; real-time transition from `IDLE` to `RECORDING` on press, `RECORDING` to `TRANSCRIBING` on release; clean `RECORD_AUDIO` permissions handling.
   - Lines 16–26 (R2): Typed requests passing explicit language metadata:
     - `SpeechRequest(sourceLanguage, audio)` -> `POST https://api.sarvam.ai/speech-to-text` (`multipart/form-data`: `file`, `model="saaras:v3"`, `language_code`, `mode="transcribe"`).
     - `TranslationRequest(sourceLanguage, targetLanguage, text)` -> `POST https://api.sarvam.ai/translate` (JSON: `input`, `source_language_code`, `target_language_code`, `model="mayura:v1"`, `mode="formal"`).
     - `TtsRequest(targetLanguage, text, voice)` -> `POST https://api.sarvam.ai/text-to-speech` (JSON: `inputs`, `target_language_code`, `speaker="meera"`, `model="bulbul:v3"`, `speech_sample_rate=16000`).
     - Explicit milestones: Milestone 1 (Hindi -> English), Milestone 2 (English -> Marathi), Milestone 3 (Tamil -> Hindi), Milestone 4 (10-language selector matrix).
   - Lines 27–36 (R3): Strictly ONE main screen in Jetpack Compose + MVVM; hierarchy includes Header & status indicator, Language dropdowns, HOLD TO SPEAK PTT button, Source transcription card, Translated text card + Replay Audio button, Quick Feedback bar (👍 / 👎); 7-state finite state machine: `IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`.
   - Lines 37–41 (R4): Local feedback logger saving `timestamp`, `source_language`, `target_language`, `source_text`, `translated_text`, `feedback` in an app-private JSON file; secure API key resolution via `BuildConfig.SARVAM_API_KEY` / `local.properties`; human-readable error banners for mic permissions, network, API keys, rate limits, empty results.
   - Lines 63–133 (Follow-up): Exact verified API contracts including `api-subscription-key` header, 10 BCP-47 language codes (`en-IN`, `hi-IN`, `bn-IN`, `ta-IN`, `te-IN`, `kn-IN`, `ml-IN`, `mr-IN`, `gu-IN`, `od-IN`), and Base64-encoded WAV response from Bulbul v3.

2. **ISRO Domain Context (`PS_SIH26173_OFFICIAL.md` & `01_RESEARCH_FOR_AGENT.md`)**:
   - `PS_SIH26173_OFFICIAL.md` lines 19–20: Two-way walkie-talkie mode, non-interruptible highest-volume playback for alert messages (`USAGE_ALARM`), speech-to-speech over low-bitrate links.
   - `01_RESEARCH_FOR_AGENT.md` lines 33, 112–114: NavIC satellite short-message constraints (27-byte subframe, 277-byte packet).

3. **Workspace Filesystem (`list_dir` on `/Users/spirit/Downloads/spiritsih`)**:
   - `iTantra_App/` directory did not exist in root yet. The prototype code implementation is pending construction by worker agents based on these specifications.

---

## 2. Logic Chain

1. From **Observation 1**, the requirements explicitly dictate the exact interfaces, REST endpoints, payloads, and state machine required for the iTantra prototype.
2. From **Observation 1 (R1 & Follow-up)**, audio capture must be handled via Android's native `AudioRecord` at 16,000 Hz, 16-bit Mono PCM. Because Sarvam Saaras v3 expects a standard WAV file container in multipart form data, an in-memory PCM-to-WAV encoder that adds the standard 44-byte RIFF/WAVE header is strictly necessary.
3. From **Observation 1 (R2 & Follow-up)**, the three core endpoints on `https://api.sarvam.ai` form a sequential pipeline:
   - Speech-to-Text: `POST /speech-to-text` with multipart data (`model="saaras:v3"`).
   - Translation: `POST /translate` with JSON body (`model="mayura:v1"`, `mode="formal"`).
   - Text-to-Speech: `POST /text-to-speech` with JSON body (`model="bulbul:v3"`, `speaker="meera"`).
4. From **Observation 1 (R3)**, the UI must be a single Jetpack Compose screen driven by an immutable state model adhering to the 7-state FSM: `IDLE` -> `RECORDING` -> `TRANSCRIBING` -> `TRANSLATING` -> `SYNTHESIZING` -> `PLAYING` -> `IDLE` (or `ERROR`).
5. From **Observation 1 (R4)**, feedback logging requires an app-private JSON file in `context.filesDir/feedback_logs.json` with thread-safe atomic file appending to prevent data corruption.
6. Synthesizing Observations 1, 2, and 3 produces the comprehensive specification report written in `specs_report.md`, which contains 18 discovered features and 17 edge case behaviors mapped directly to acceptance criteria.

---

## 3. Caveats

1. The translation model in `ORIGINAL_REQUEST.md` line 19 references `model="sarvam-translate:v1"` whereas line 91 references `model="mayura:v1"`. The specification documents `mayura:v1` as primary based on the verified subagent probe, while advising client code to support both aliases.
2. Cloud API latency depends on internet bandwidth and Sarvam AI server loads. Local device network timeouts should be configured to at least 15–30 seconds for audio uploads.
3. While `PS_SIH26173_OFFICIAL.md` outlines a long-term goal of fully-offline on-device models, `ORIGINAL_REQUEST.md` establishes that this prototype's explicit mission is a working native Android implementation powered by official Sarvam AI cloud APIs (`saaras:v3`, `mayura:v1`, `bulbul:v3`).

---

## 4. Conclusion

The specification mining task is complete. All functional requirements (R1, R2, R3, R4), API contracts, audio encoding parameters, BCP-47 language codes, speaker options, UI state transitions, and feedback logging schemas have been extracted, cross-referenced, and documented in:
`/Users/spirit/Downloads/spiritsih/.agents/spec_miner_survey_1/specs_report.md`

The orchestrator and downstream worker agents have an unambiguous technical specification to implement the Android native app.

---

## 5. Verification Method

To verify the deliverables and accuracy of this specification:
1. Inspect the generated report:
   ```bash
   cat /Users/spirit/Downloads/spiritsih/.agents/spec_miner_survey_1/specs_report.md
   ```
2. Verify requirement completeness against `ORIGINAL_REQUEST.md`:
   - Check `POST https://api.sarvam.ai/speech-to-text` multipart contract in Section 4.2.
   - Check `POST https://api.sarvam.ai/translate` JSON body contract in Section 4.3.
   - Check `POST https://api.sarvam.ai/text-to-speech` JSON body contract in Section 4.4.
   - Check the 10 BCP-47 language codes and speaker (`meera`) in Section 5.
   - Check the 44-byte WAV header structure in Section 6.2.
   - Check the 7-state FSM transition table in Section 7.2.
   - Check the JSON schema for `feedback_logs.json` in Section 8.2.
3. Invalidation condition: If Sarvam AI changes endpoint URLs, header keys, or model identifiers (`saaras:v3`, `mayura:v1`, `bulbul:v3`), or if the state machine transitions diverge from the single-screen requirements.
