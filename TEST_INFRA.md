# iTantra E2E Test Infrastructure & 4-Tier Test Suite Specification

**Document Version:** 1.0.0  
**Status:** PUBLISHED & READY FOR EXECUTION  
**Platform:** Android Native (Kotlin 2.3.20, AGP 9.0.1, Min SDK 26, Target SDK 35, Jetpack Compose BOM 2026.03.01)  
**Author:** `test_writer_1` (Teamwork Quality Assurance & Testing Track)  
**Authoritative References:** `PROJECT.md`, `ORIGINAL_REQUEST.md`, `PS_SIH26173_OFFICIAL.md`  

---

## 1. Executive Summary & Test Philosophy

### 1.1 Mission & Scope
The **iTantra** test infrastructure provides an airtight, deterministic, opaque-box end-to-end verification harness for the native Android multilingual voice communication prototype. It ensures that every subsystem—from native 16 kHz PCM audio recording and 44-byte RIFF WAV encoding, to official Sarvam AI cloud REST APIs (`Saaras v3`, `Mayura v1`, `Bulbul v3`), reactive 7-state finite state machine, single-screen Jetpack Compose UI, and app-private JSON feedback persistence—executes with 100% mathematical and behavioral fidelity.

### 1.2 Core Testing Principles

1. **Opaque-Box E2E Testing**:
   Tests exercise the system strictly through observable inputs (PTT touch press/release, microphone PCM bytes, language selections, feedback clicks) and verify observable outputs (44-byte WAV headers, HTTP multipart/JSON payloads, UI state transitions, decoded audio playback, written JSON log files). Implementation internals are treated as opaque.

2. **Deterministic Reference Oracles**:
   Expected values are mathematically and contractually derived directly from `ORIGINAL_REQUEST.md` and the verified Sarvam AI API specifications. No test relies on vague heuristics or volatile third-party networks.

3. **Hermetic Offline Test Harness**:
   To guarantee rapid, zero-flakiness test execution without incurring external cloud API costs, network jitter, or quota exhaustion, network tests utilize an in-memory HTTP mock engine and MockWebServer. Live integration tests can be toggled via `SARVAM_API_KEY`.

4. **Progressive Testability**:
   Tests are layered by milestone dependencies (M0 -> M1 -> M2 -> M3 -> M4). Unit and contract tests compile and verify independently as modules are implemented.

5. **Adversarial Hardening**:
   Boundary cases rigorously verify special characters, control characters, extreme buffer sizes, short taps (<300ms), 401/429/500 HTTP errors, corrupted files, and rapid multi-touch debouncing.

---

## 2. Feature Inventory & Test Coverage Matrix

The 18 features defined in `PROJECT.md § Feature Inventory` are systematically mapped across the 4 test tiers:

| # | Feature Code | Feature Name | Milestone | Tier 1 (Nominal) | Tier 2 (Boundaries) | Tier 3 (Cross-Feature) | Tier 4 (Real-World) | Primary Test Suite File |
|---|---|---|---|:---:|:---:|:---:|:---:|---|
| 1 | `F1` | Native PTT Audio Recording | M1 | $\ge 5$ | $\ge 5$ | Yes | Yes | `audio/AudioRecorderTest.kt` |
| 2 | `F2` | In-Memory PCM-to-WAV Encoder | M1 | $\ge 5$ | $\ge 5$ | Yes | Yes | `audio/WavEncoderTest.kt` |
| 3 | `F3` | Runtime Mic Permission Handler | M2 | $\ge 5$ | $\ge 5$ | Yes | Yes | `ui/PermissionHandlerTest.kt` |
| 4 | `F4` | Sarvam Saaras v3 STT Client | M1 | $\ge 5$ | $\ge 5$ | Yes | Yes | `network/SarvamApiClientTest.kt` |
| 5 | `F5` | Sarvam Translate Client (Mayura v1) | M1 | $\ge 5$ | $\ge 5$ | Yes | Yes | `network/SarvamApiClientTest.kt` |
| 6 | `F6` | Sarvam Bulbul v3 TTS Client | M1 | $\ge 5$ | $\ge 5$ | Yes | Yes | `network/SarvamApiClientTest.kt` |
| 7 | `F7` | Native WAV Audio Player | M1 | $\ge 5$ | $\ge 5$ | Yes | Yes | `audio/AudioPlayerTest.kt` |
| 8 | `F8` | Replay Audio Trigger | M2 | $\ge 5$ | $\ge 5$ | Yes | Yes | `ui/MainViewModelTest.kt` |
| 9 | `F9` | Single-Screen Compose View | M2 | $\ge 5$ | $\ge 5$ | Yes | Yes | `ui/MainScreenComposeTest.kt` |
| 10 | `F10` | Tactile PTT Button | M2 | $\ge 5$ | $\ge 5$ | Yes | Yes | `ui/MainViewModelTest.kt` |
| 11 | `F11` | Language Dropdown Selectors | M2, M3 | $\ge 5$ | $\ge 5$ | Yes | Yes | `data/LanguageTest.kt` |
| 12 | `F12` | Dual Text Display Cards | M2 | $\ge 5$ | $\ge 5$ | Yes | Yes | `ui/MainScreenComposeTest.kt` |
| 13 | `F13` | Reactive 7-State Machine | M2 | $\ge 5$ | $\ge 5$ | Yes | Yes | `ui/MainViewModelTest.kt` |
| 14 | `F14` | Quick Feedback Bar | M3 | $\ge 5$ | $\ge 5$ | Yes | Yes | `data/FeedbackLoggerTest.kt` |
| 15 | `F15` | Local JSON Feedback Logger | M3 | $\ge 5$ | $\ge 5$ | Yes | Yes | `data/FeedbackLoggerTest.kt` |
| 16 | `F16` | Secure API Key Resolution | M0 | $\ge 5$ | $\ge 5$ | Yes | Yes | `network/ApiKeySecurityTest.kt` |
| 17 | `F17` | Real-Time Telemetry & Status | M2 | $\ge 5$ | $\ge 5$ | Yes | Yes | `ui/MainViewModelTest.kt` |
| 18 | `F18` | Emergency Audio Route | M1 | $\ge 5$ | $\ge 5$ | Yes | Yes | `audio/AudioPlayerTest.kt` |

---

## 3. Tier 1: Feature Coverage Specifications ($\ge 5$ Test Cases per Feature = 90 Cases)

### F1: Native PTT Audio Recording
- **T1-F1-01**: `test_recording_initialization_configures_16khz_mono_16bit_pcm`  
  *Input*: Trigger recording start.  
  *Expected*: `AudioRecord` initialized with SampleRate=16000, ChannelConfig=CHANNEL_IN_MONO, AudioFormat=ENCODING_PCM_16BIT.
- **T1-F1-02**: `test_recording_streams_pcm_chunks_to_output_buffer`  
  *Input*: Feed 100ms simulated microphone PCM stream.  
  *Expected*: Internal buffer accumulates exactly 3,200 bytes ($16000 \times 1 \times 2 \times 0.1$).
- **T1-F1-03**: `test_stop_recording_returns_captured_pcm_byte_array`  
  *Input*: Invoke `stopRecording()`.  
  *Expected*: Returns captured non-empty `ByteArray` matching accumulated length and sets `isRecording = false`.
- **T1-F1-04**: `test_audio_amplitude_flow_emits_dynamic_peak_values`  
  *Input*: Feed PCM samples with alternating peak amplitudes (1000, 5000, 15000).  
  *Expected*: Amplitude `StateFlow` emits corresponding peak levels for UI audio visualizer.
- **T1-F1-05**: `test_recording_resource_release_stops_hardware_cleanly`  
  *Input*: Start recording and subsequently invoke `release()`.  
  *Expected*: `recordingState == RECORDSTATE_STOPPED`, audio record instance released, background job cancelled.

### F2: In-Memory PCM-to-WAV Encoder
- **T2-F2-01**: `test_wav_encoder_prepends_canonical_44_byte_riff_header`  
  *Input*: 32,000 raw PCM bytes (1.0 sec of 16 kHz Mono 16-bit audio).  
  *Expected*: Output length equals exactly $32000 + 44 = 32044$ bytes.
- **T2-F2-02**: `test_wav_header_magic_identifiers_riff_wave_fmt_data`  
  *Input*: Encoded WAV byte array.  
  *Expected*: Bytes 0-3 == ASCII `"RIFF"`, Bytes 8-11 == `"WAVE"`, Bytes 12-15 == `"fmt "`, Bytes 36-39 == `"data"`.
- **T2-F2-03**: `test_wav_header_chunk_size_and_subchunk_sizes_little_endian`  
  *Input*: $N = 6400$ bytes PCM.  
  *Expected*: Offset 4-7 (ChunkSize) == $N + 36 = 6436$; Offset 16-19 (Subchunk1Size) == 16; Offset 40-43 (Subchunk2Size) == $N = 6400$.
- **T2-F2-04**: `test_wav_header_audio_format_parameters_pcm_16khz_mono_16bit`  
  *Input*: Encoded WAV byte array.  
  *Expected*: AudioFormat (offset 20) == 1; NumChannels (offset 22) == 1; SampleRate (offset 24) == 16000; ByteRate (offset 28) == 32000; BlockAlign (offset 32) == 2; BitsPerSample (offset 34) == 16.
- **T2-F2-05**: `test_wav_header_preserves_pcm_payload_integrity`  
  *Input*: Distinct known PCM sequence `[0x01, 0x02, 0x7F, -0x80]`.  
  *Expected*: Bytes 44..47 of output WAV match input byte-for-byte.

### F3: Runtime Mic Permission Handler
- **T1-F3-01**: `test_permission_granted_permits_recording_flow`  
  *Input*: Mic permission check returns `PackageManager.PERMISSION_GRANTED`.  
  *Expected*: `MainUiState.micPermissionGranted == true`, PTT touch gesture initiates recording.
- **T1-F3-02**: `test_permission_denied_prevents_recording_and_prompts_request`  
  *Input*: Mic permission check returns `PERMISSION_DENIED`.  
  *Expected*: PTT touch triggers permission request launcher instead of `AudioRecord`.
- **T1-F3-03**: `test_permission_denial_displays_explanatory_warning_banner`  
  *Input*: User denies permission request.  
  *Expected*: UI displays banner: *"Microphone permission required. Please allow audio recording in device Settings."*
- **T1-F3-04**: `test_permission_rationale_triggers_rationale_dialog`  
  *Input*: `shouldShowRequestPermissionRationale == true`.  
  *Expected*: Rationale dialog shown with explanation of why voice transcription requires mic access.
- **T1-F3-05**: `test_permission_grant_clears_error_banner_dynamically`  
  *Input*: User accepts permission dialog.  
  *Expected*: `MainUiState.pipelineState` transitions to `IDLE`, error banner is dismissed.

### F4: Sarvam Saaras v3 STT Client
- **T1-F4-01**: `test_stt_request_endpoint_url_and_method`  
  *Input*: Valid WAV byte array and language `"hi-IN"`.  
  *Expected*: Dispatches `POST https://api.sarvam.ai/speech-to-text`.
- **T1-F4-02**: `test_stt_request_multipart_fields_model_lang_mode`  
  *Input*: Dispatch STT call.  
  *Expected*: Multipart contains part `model="saaras:v3"`, part `language_code="hi-IN"`, part `mode="transcribe"`, and part `file` (`audio/wav`).
- **T1-F4-03**: `test_stt_request_headers_include_api_subscription_key`  
  *Input*: Network request interceptor.  
  *Expected*: Header `api-subscription-key: <API_KEY>` is present.
- **T1-F4-04**: `test_stt_response_deserialization_success`  
  *Input*: HTTP 200 with JSON `{"transcript": "नमस्ते दुनिया", "language_code": "hi-IN"}`.  
  *Expected*: `SpeechResponse.transcript == "नमस्ते दुनिया"`, `languageCode == "hi-IN"`.
- **T1-F4-05**: `test_stt_supports_all_10_indic_language_codes`  
  *Input*: Iterate language codes (`hi-IN`, `en-IN`, `bn-IN`, `ta-IN`, `te-IN`, `kn-IN`, `ml-IN`, `mr-IN`, `gu-IN`, `od-IN`).  
  *Expected*: Request multipart form field `language_code` reflects target code precisely.

### F5: Sarvam Translate Client (Mayura v1)
- **T1-F5-01**: `test_translate_request_endpoint_url_and_method`  
  *Input*: Text `"नमस्ते"`, source `"hi-IN"`, target `"en-IN"`.  
  *Expected*: Dispatches `POST https://api.sarvam.ai/translate`.
- **T1-F5-02**: `test_translate_request_json_body_serialization`  
  *Input*: Dispatch translation call.  
  *Expected*: JSON body matches `{"input":"नमस्ते","source_language_code":"hi-IN","target_language_code":"en-IN","model":"mayura:v1","mode":"formal"}`.
- **T1-F5-03**: `test_translate_request_headers_include_auth_and_content_type`  
  *Input*: Network interceptor inspection.  
  *Expected*: `api-subscription-key: <KEY>`, `Content-Type: application/json`.
- **T1-F5-04**: `test_translate_response_deserialization_success`  
  *Input*: HTTP 200 with JSON `{"translated_text": "Hello world"}`.  
  *Expected*: `TranslationResponse.translatedText == "Hello world"`.
- **T1-F5-05**: `test_translate_supports_milestone_language_pairs`  
  *Input*: Milestone 1 (`hi-IN` -> `en-IN`), Milestone 2 (`en-IN` -> `mr-IN`), Milestone 3 (`ta-IN` -> `hi-IN`).  
  *Expected*: Requests construct correct source and target language code parameters.

### F6: Sarvam Bulbul v3 TTS Client
- **T1-F6-01**: `test_tts_request_endpoint_url_and_method`  
  *Input*: Text `"Hello world"`, target `"en-IN"`.  
  *Expected*: Dispatches `POST https://api.sarvam.ai/text-to-speech`.
- **T1-F6-02**: `test_tts_request_json_body_serialization`  
  *Input*: Dispatch TTS call.  
  *Expected*: JSON body matches `{"inputs":["Hello world"],"target_language_code":"en-IN","speaker":"meera","model":"bulbul:v3","speech_sample_rate":16000}`.
- **T1-F6-03**: `test_tts_request_headers_include_auth_and_content_type`  
  *Input*: Network interceptor inspection.  
  *Expected*: `api-subscription-key: <KEY>`, `Content-Type: application/json`.
- **T1-F6-04**: `test_tts_response_deserialization_success`  
  *Input*: HTTP 200 with JSON `{"audios": ["UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA="]}`.  
  *Expected*: `TtsResponse.audios.first()` contains non-empty Base64 string.
- **T1-F6-05**: `test_tts_supports_default_speaker_meera_across_all_languages`  
  *Input*: Iterate 10 Indic languages with default voice.  
  *Expected*: `speaker == "meera"` serialized across all requests.

### F7: Native WAV Audio Player
- **T1-F7-01**: `test_audio_player_decodes_base64_wav_to_bytes`  
  *Input*: Base64 string from Bulbul v3.  
  *Expected*: Successfully decoded to byte array without `IllegalArgumentException`.
- **T1-F7-02**: `test_audio_player_initializes_speech_audio_attributes`  
  *Input*: Audio playback trigger.  
  *Expected*: `AudioAttributes.CONTENT_TYPE_SPEECH` and `USAGE_ASSISTANCE_ACCESSIBILITY` configured.
- **T1-F7-03**: `test_audio_player_triggers_completion_callback_on_finish`  
  *Input*: Play 100ms test audio.  
  *Expected*: `onCompletion()` lambda invoked, UI transitions from `PLAYING` to `IDLE`.
- **T1-F7-04**: `test_audio_player_triggers_error_callback_on_playback_failure`  
  *Input*: Pass unplayable corrupted byte sequence.  
  *Expected*: `onError(String)` invoked, UI transitions to `ERROR`.
- **T1-F7-05**: `test_audio_player_cleans_up_cache_file_and_releases_mediaplayer`  
  *Input*: Complete or cancel playback.  
  *Expected*: Temp `.wav` file in `context.cacheDir` deleted, `mediaPlayer` released.

### F8: Replay Audio Trigger
- **T1-F8-01**: `test_replay_button_disabled_when_no_audio_cached`  
  *Input*: Initial state or state before any TTS synthesis.  
  *Expected*: `MainUiState.hasAudioToReplay == false`, Replay button `enabled == false`.
- **T1-F8-02**: `test_replay_button_enabled_after_successful_tts_synthesis`  
  *Input*: TTS completes successfully.  
  *Expected*: `MainUiState.hasAudioToReplay == true`, Replay button `enabled == true`.
- **T1-F8-03**: `test_replay_button_click_plays_cached_wav_without_network_request`  
  *Input*: Click Replay button.  
  *Expected*: `AudioPlayer.playWavBytes` invoked with cached bytes; 0 HTTP requests dispatched.
- **T1-F8-04**: `test_replay_button_transitions_state_to_playing`  
  *Input*: Click Replay button from IDLE.  
  *Expected*: `MainUiState.pipelineState` transitions to `PipelineState.Playing`.
- **T1-F8-05**: `test_replay_completion_returns_state_to_idle`  
  *Input*: Replayed audio finishes playing.  
  *Expected*: `MainUiState.pipelineState` returns to `PipelineState.Idle`, replay button remains enabled.

### F9: Single-Screen Compose View
- **T1-F9-01**: `test_single_screen_hosts_all_required_ui_elements`  
  *Input*: Render `MainScreen`.  
  *Expected*: Header, Language Selectors, PTT Button, Source Card, Target Card, Feedback Bar all present in hierarchy.
- **T1-F9-02**: `test_single_screen_zero_navigation_destinations`  
  *Input*: Inspect Compose navigation graph or activity layout.  
  *Expected*: No NavHost, No bottom navigation bars, No drawer menus; single unified transceiver screen.
- **T1-F9-03**: `test_single_screen_displays_app_title_and_status`  
  *Input*: Launch app.  
  *Expected*: Header displays "iTantra" and status chip "IDLE".
- **T1-F9-04**: `test_single_screen_reactive_state_updates_without_recomposition_leak`  
  *Input*: Advance state machine through `IDLE -> RECORDING -> TRANSCRIBING`.  
  *Expected*: Screen recomposes only state-dependent nodes cleanly.
- **T1-F9-05**: `test_single_screen_material3_theming_consistency`  
  *Input*: Inspect Theme tokens.  
  *Expected*: Primary, surfaceVariant, error colors applied consistently across components.

### F10: Tactile PTT Button
- **T1-F10-01**: `test_ptt_pointer_down_triggers_on_ptt_press`  
  *Input*: Touch down on PTT button.  
  *Expected*: `onPttPress()` invoked, state transitions to `RECORDING`.
- **T1-F10-02**: `test_ptt_pointer_up_triggers_on_ptt_release`  
  *Input*: Touch release after 500ms hold.  
  *Expected*: `onPttRelease()` invoked, state transitions to `TRANSCRIBING`.
- **T1-F10-03**: `test_ptt_button_scale_animation_on_press`  
  *Input*: State changes to `Recording`.  
  *Expected*: Target scale factor expands to 1.15x with spring animation.
- **T1-F10-04**: `test_ptt_button_color_transitions_red_while_recording`  
  *Input*: State is `Recording`.  
  *Expected*: Background color changes to `MaterialTheme.colorScheme.error` (visual red indicator).
- **T1-F10-05**: `test_ptt_button_disabled_while_pipeline_is_busy`  
  *Input*: State is `TRANSCRIBING`, `TRANSLATING`, or `SYNTHESIZING`.  
  *Expected*: PTT button touch input is ignored (`isBusy == true`).

### F11: Language Dropdown Selectors
- **T1-F11-01**: `test_source_language_dropdown_contains_all_10_indic_languages`  
  *Input*: Click Source Language dropdown.  
  *Expected*: 10 items listed: English, Hindi, Bengali, Tamil, Telugu, Kannada, Malayalam, Marathi, Gujarati, Odia.
- **T1-F11-02**: `test_target_language_dropdown_contains_all_10_indic_languages`  
  *Input*: Click Target Language dropdown.  
  *Expected*: 10 items listed with localized script names (हिन्दी, தமிழ், etc.).
- **T1-F11-03**: `test_selecting_source_language_updates_ui_state`  
  *Input*: Select Tamil (`ta-IN`) from source dropdown.  
  *Expected*: `MainUiState.sourceLanguage == SupportedLanguage.TAMIL`.
- **T1-F11-04**: `test_selecting_target_language_updates_ui_state`  
  *Input*: Select Marathi (`mr-IN`) from target dropdown.  
  *Expected*: `MainUiState.targetLanguage == SupportedLanguage.MARATHI`.
- **T1-F11-05**: `test_default_languages_are_hindi_and_english`  
  *Input*: Cold launch application.  
  *Expected*: `sourceLanguage == SupportedLanguage.HINDI` (`hi-IN`), `targetLanguage == SupportedLanguage.ENGLISH` (`en-IN`).

### F12: Dual Text Display Cards
- **T1-F12-01**: `test_source_card_displays_recognized_stt_transcript`  
  *Input*: Saaras v3 returns `"तटीय क्षेत्र में चेतावनी"`.  
  *Expected*: Source card renders text `"तटीय क्षेत्र में चेतावनी"` with language badge `"hi-IN"`.
- **T1-F12-02**: `test_target_card_displays_translated_text`  
  *Input*: Mayura v1 returns `"Coastal area warning"`.  
  *Expected*: Target card renders text `"Coastal area warning"` with language badge `"en-IN"`.
- **T1-F12-03**: `test_cards_display_placeholders_when_idle_and_empty`  
  *Input*: Initial launch state.  
  *Expected*: Cards display placeholder instructions (e.g. *"Hold button to speak..."*).
- **T1-F12-04**: `test_cards_support_complex_indic_scripts_utf8`  
  *Input*: Bengali `"ঘূর্ণিঝড় সতর্কতা"`, Tamil `"புயல் எச்சரிக்கை"`.  
  *Expected*: Rendered text matches verbatim UTF-8 string without glyph corruption.
- **T1-F12-05**: `test_card_content_persists_across_audio_replay`  
  *Input*: User clicks Replay Audio.  
  *Expected*: Both source and target text remain visible on screen without disappearing.

### F13: Reactive 7-State Machine
- **T1-F13-01**: `test_fsm_initial_state_is_idle`  
  *Input*: Instantiate `MainViewModel`.  
  *Expected*: `uiState.value.pipelineState is PipelineState.Idle`.
- **T1-F13-02**: `test_fsm_ptt_press_transitions_idle_to_recording`  
  *Input*: Dispatch `onPttPress()`.  
  *Expected*: State transitions to `PipelineState.Recording`.
- **T1-F13-03**: `test_fsm_ptt_release_transitions_recording_to_transcribing`  
  *Input*: Dispatch `onPttRelease()` after 500ms.  
  *Expected*: State transitions to `PipelineState.Transcribing`.
- **T1-F13-04**: `test_fsm_stt_success_transitions_transcribing_to_translating`  
  *Input*: STT returns non-empty transcript.  
  *Expected*: State transitions to `PipelineState.Translating`.
- **T1-F13-05**: `test_fsm_pipeline_completion_transitions_synthesizing_playing_idle`  
  *Input*: TTS succeeds, audio finishes playing.  
  *Expected*: Sequence `Translating -> Synthesizing -> Playing -> Idle` verified sequentially.

### F14: Quick Feedback Bar
- **T1-F14-01**: `test_feedback_bar_renders_thumbs_up_and_thumbs_down_buttons`  
  *Input*: Render feedback bar.  
  *Expected*: Both positive (👍) and negative (👎) buttons present in UI.
- **T1-F14-02**: `test_feedback_buttons_disabled_when_no_translation_exists`  
  *Input*: `lastTranslatedText.isBlank()`.  
  *Expected*: Both feedback buttons have `enabled == false`.
- **T1-F14-03**: `test_thumbs_up_click_submits_positive_feedback`  
  *Input*: Click Thumbs Up button.  
  *Expected*: Dispatches `FeedbackSubmitted(isPositive = true)` to ViewModel.
- **T1-F14-04**: `test_thumbs_down_click_submits_negative_feedback`  
  *Input*: Click Thumbs Down button.  
  *Expected*: Dispatches `FeedbackSubmitted(isPositive = false)` to ViewModel.
- **T1-F14-05**: `test_feedback_submission_shows_visual_confirmation`  
  *Input*: Submit feedback.  
  *Expected*: `MainUiState.feedbackSubmitted == true`, buttons show checked/thank-you state.

### F15: Local JSON Feedback Logger
- **T1-F15-01**: `test_feedback_logger_creates_private_json_file`  
  *Input*: Log first feedback record.  
  *Expected*: File `context.filesDir/feedback_logs.json` is created with valid JSON.
- **T1-F15-02**: `test_feedback_record_schema_contains_all_required_fields`  
  *Input*: Inspect logged JSON record.  
  *Expected*: Contains `id`, `timestamp`, `timestamp_iso`, `source_language`, `target_language`, `source_text`, `translated_text`, `feedback`.
- **T1-F15-03**: `test_feedback_logger_appends_records_to_json_array`  
  *Input*: Log 3 consecutive feedback entries.  
  *Expected*: JSON array in file has `length == 3`, preserving earlier entries.
- **T1-F15-04**: `test_feedback_logger_atomic_write_via_temp_file`  
  *Input*: Trigger feedback write.  
  *Expected*: File written to `.tmp` first, then atomically renamed to target file.
- **T1-F15-05**: `test_feedback_logger_reads_back_logged_records_accurately`  
  *Input*: Call `readRecords()`.  
  *Expected*: Returns list of `FeedbackRecord` matching written data verbatim.

### F16: Secure API Key Resolution
- **T1-F16-01**: `test_api_key_resolved_from_build_config`  
  *Input*: Inspect `BuildConfig.SARVAM_API_KEY`.  
  *Expected*: Value loaded from Gradle `local.properties` or environment variable.
- **T1-F16-02**: `test_auth_interceptor_attaches_header_when_key_present`  
  *Input*: OkHttp request with configured key `"test-api-key-123"`.  
  *Expected*: Outgoing request has header `api-subscription-key: test-api-key-123`.
- **T1-F16-03**: `test_blank_api_key_throws_invalid_api_key_exception`  
  *Input*: API key is `""` or whitespace.  
  *Expected*: Throws `InvalidApiKeyException` before any network socket is opened.
- **T1-F16-04**: `test_api_key_is_redacted_in_logging_interceptor`  
  *Input*: HttpLoggingInterceptor logs request.  
  *Expected*: Header value printed as `api-subscription-key: ██` (never plaintext).
- **T1-F16-05**: `test_api_key_is_never_committed_to_git`  
  *Input*: Run git check on repo.  
  *Expected*: `local.properties` matches `.gitignore`; no hardcoded API key in `app/src/main`.

### F17: Real-Time Telemetry & Status
- **T1-F17-01**: `test_telemetry_displays_active_status_chip`  
  *Input*: Observe status badge across states.  
  *Expected*: Displays "IDLE", "LISTENING", "TRANSCRIBING", "TRANSLATING", "SYNTHESIZING", "SPEAKING", "ERROR".
- **T1-F17-02**: `test_telemetry_records_stt_processing_latency`  
  *Input*: Saaras v3 takes 450ms.  
  *Expected*: `LatencyStats.sttMs == 450`, UI displays `STT: 450ms`.
- **T1-F17-03**: `test_telemetry_records_translation_latency`  
  *Input*: Mayura v1 takes 280ms.  
  *Expected*: `LatencyStats.translateMs == 280`, UI displays `Trans: 280ms`.
- **T1-F17-04**: `test_telemetry_records_tts_synthesis_latency`  
  *Input*: Bulbul v3 takes 390ms.  
  *Expected*: `LatencyStats.ttsMs == 390`, UI displays `TTS: 390ms`.
- **T1-F17-05**: `test_telemetry_calculates_total_pipeline_latency`  
  *Input*: Total time elapsed.  
  *Expected*: `LatencyStats.totalMs == sttMs + translateMs + ttsMs` ($1120\text{ms}$).

### F18: Emergency Audio Route
- **T1-F18-01**: `test_emergency_audio_attributes_set_usage_alarm`  
  *Input*: Play audio with emergency flag set.  
  *Expected*: Audio player configured with `AudioAttributes.USAGE_ALARM`.
- **T1-F18-02**: `test_emergency_audio_sets_content_type_speech`  
  *Input*: Emergency playback config.  
  *Expected*: `AudioAttributes.CONTENT_TYPE_SPEECH` configured.
- **T1-F18-03**: `test_emergency_playback_routes_to_device_loudspeaker`  
  *Input*: Emergency broadcast audio.  
  *Expected*: Audio streams to built-in speaker with maximum audio channel priority.
- **T1-F18-04**: `test_emergency_fallback_to_media_when_alarm_restricted`  
  *Input*: System policy restricts alarm stream.  
  *Expected*: Gracefully falls back to `USAGE_ASSISTANCE_ACCESSIBILITY` / `USAGE_MEDIA`.
- **T1-F18-05**: `test_emergency_priority_bypasses_silent_switch`  
  *Input*: Device ringer mode is `RINGER_MODE_SILENT`.  
  *Expected*: Emergency audio route sounds audibly on speaker.

---

## 4. Tier 2: Boundary & Corner Cases Specifications ($\ge 5$ Test Cases per Feature = 90 Cases)

### F1: Audio Capture Boundaries
- **T2-F1-01**: `test_zero_byte_recording_handles_empty_buffer_without_crash`  
  *Input*: PTT released instantaneously (0 bytes read).  
  *Expected*: Discard buffer safely, return empty array, revert to `IDLE`.
- **T2-F1-02**: `test_max_duration_recording_auto_stops_at_30_seconds`  
  *Input*: Hold PTT for >30 seconds (960,000 bytes at 32 kB/s).  
  *Expected*: Recording loop auto-stops, dispatches captured audio, prevents OOM.
- **T2-F1-03**: `test_uninitialized_audiorecord_transitions_to_error`  
  *Input*: Simulate `AudioRecord.state == STATE_UNINITIALIZED` (hardware failure).  
  *Expected*: Catches `IllegalStateException`, transitions UI to `ERROR` with user message.
- **T2-F1-04**: `test_microphone_in_use_by_other_app_handled_gracefully`  
  *Input*: AudioRecord read returns `AudioRecord.ERROR_INVALID_OPERATION`.  
  *Expected*: Stops recording, transitions to `ERROR`: *"Microphone unavailable"*.
- **T2-F1-05**: `test_buffer_underrun_hal_jitter_handled_by_double_buffer`  
  *Input*: HAL delays read cycle by 50ms.  
  *Expected*: 4 KB floor buffer prevents audio frame dropping or crash.

### F2: PCM-to-WAV Encoder Boundaries
- **T2-F2-01**: `test_wav_encode_zero_length_pcm_produces_valid_44_byte_header`  
  *Input*: `ByteArray(0)`.  
  *Expected*: Returns exactly 44 bytes with ChunkSize=36, Subchunk2Size=0.
- **T2-F2-02**: `test_wav_encode_odd_byte_length_aligns_properly`  
  *Input*: 101 raw bytes (misaligned 16-bit PCM).  
  *Expected*: Encodes without `ArrayIndexOutOfBoundsException`, preserves 101 bytes in data subchunk.
- **T2-F2-03**: `test_wav_encode_large_pcm_buffer_calculates_correct_32bit_integers`  
  *Input*: $10\text{ MB}$ raw PCM byte array ($10,485,760$ bytes).  
  *Expected*: ChunkSize == $10485796$, Subchunk2Size == $10485760$, byte-rate and block-align intact.
- **T2-F2-04**: `test_wav_encode_custom_sample_rates_8khz_22khz_44khz`  
  *Input*: Sample rates: 8000, 22050, 44100.  
  *Expected*: ByteRate matches $\text{rate} \times 1 \times 2$; BlockAlign == 2.
- **T2-F2-05**: `test_wav_encode_negative_or_zero_sample_rate_throws_exception`  
  *Input*: `sampleRate = -16000` or `channels = 0`.  
  *Expected*: Throws `IllegalArgumentException` with descriptive message.

### F3: Mic Permission Boundaries
- **T2-F3-01**: `test_permission_denied_with_never_ask_again_opens_settings_dialog`  
  *Input*: `shouldShowRequestPermissionRationale == false` and permission denied.  
  *Expected*: Shows dialog with intent to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
- **T2-F3-02**: `test_permission_revoked_at_runtime_while_app_backgrounded`  
  *Input*: User revokes permission in Android Settings while app is in background.  
  *Expected*: On resume, app checks permission afresh, detects denial, resets state to `IDLE`.
- **T2-F3-03**: `test_rapid_multiple_permission_checks_debounce_cleanly`  
  *Input*: Rapid multiple PTT presses before permission result returns.  
  *Expected*: Single permission request active, no duplicate activity launcher crashes.
- **T2-F3-04**: `test_permission_denial_error_banner_provides_one_tap_retry`  
  *Input*: Click "Grant Permission" button in error banner.  
  *Expected*: Re-launches system permission contract.
- **T2-F3-05**: `test_security_exception_on_audiorecord_caught_without_crash`  
  *Input*: Bypass check, instantiate `AudioRecord` without permission.  
  *Expected*: `SecurityException` caught, mapped to user-friendly permission error banner.

### F4: Sarvam Saaras v3 STT Boundaries
- **T2-F4-01**: `test_stt_http_401_unauthorized_displays_invalid_api_key_banner`  
  *Input*: Sarvam returns HTTP 401 `{"error": "Unauthorized"}`.  
  *Expected*: State transitions to `ERROR`, banner: *"Invalid API key. Please verify your Sarvam AI subscription key."*
- **T2-F4-02**: `test_stt_http_429_too_many_requests_displays_rate_limit_banner`  
  *Input*: Sarvam returns HTTP 429 `{"error": "Rate limit exceeded"}`.  
  *Expected*: State transitions to `ERROR`, banner: *"Server rate limit reached. Please wait a few seconds before trying again."*
- **T2-F4-03**: `test_stt_http_500_503_server_error_displays_service_unavailable`  
  *Input*: Sarvam returns HTTP 500 or 503.  
  *Expected*: State transitions to `ERROR`, banner: *"Sarvam AI service is temporarily unavailable. Please try again shortly."*
- **T2-F4-04**: `test_stt_empty_transcript_displays_no_speech_detected_and_reverts_idle`  
  *Input*: Sarvam returns HTTP 200 with `{"transcript": "", "language_code": "hi-IN"}` (silence).  
  *Expected*: Does not invoke Translation or TTS; transitions to `IDLE` with *"No speech detected"*.
- **T2-F4-05**: `test_stt_socket_timeout_displays_network_timeout_banner`  
  *Input*: Mock server delays response by >30 seconds.  
  *Expected*: `SocketTimeoutException` caught, displays *"Network timed out while processing speech"*, state to `ERROR`.

### F5: Sarvam Translate Boundaries
- **T2-F5-01**: `test_translate_special_characters_emojis_symbols_preserves_text`  
  *Input*: Text with symbols: *"Alert! Cyclone #12 & wind > 100km/h @ coast 🌊"*.  
  *Expected*: JSON escapes cleanly, request succeeds without parsing error.
- **T2-F5-02**: `test_translate_identical_source_and_target_languages_passthrough`  
  *Input*: Source `"hi-IN"`, Target `"hi-IN"`, text `"नमस्ते"`.  
  *Expected*: Either transparent pass-through or successful translation returning `"नमस्ते"`.
- **T2-F5-03**: `test_translate_http_400_bad_request_maps_to_translation_error`  
  *Input*: Sarvam returns HTTP 400 `{"error": "Unsupported language pair"}`.  
  *Expected*: Transitions to `ERROR`, banner: *"Translation failed for this language pair"*.
- **T2-F5-04**: `test_translate_corrupted_json_response_handled_gracefully`  
  *Input*: Sarvam returns truncated JSON `{"translated_text": `.  
  *Expected*: Catches `JsonSyntaxException` / `SerializationException`, transitions to `ERROR`.
- **T2-F5-05**: `test_translate_empty_source_string_aborts_pipeline`  
  *Input*: Empty source string `""` passed to translate.  
  *Expected*: Throws `IllegalArgumentException` before network request, returns to `IDLE`.

### F6: Sarvam Bulbul v3 TTS Boundaries
- **T2-F6-01**: `test_tts_empty_audios_array_displays_synthesis_error`  
  *Input*: Sarvam returns HTTP 200 with `{"audios": []}`.  
  *Expected*: Transitions to `ERROR`, banner: *"Speech synthesis failed. Target text remains available on screen."*
- **T2-F6-02**: `test_tts_invalid_base64_string_handled_gracefully`  
  *Input*: Sarvam returns `{"audios": ["not-a-valid-base64-string!@#"]}`.  
  *Expected*: Catches `IllegalArgumentException` on Base64 decode, transitions to `ERROR`.
- **T2-F6-03**: `test_tts_http_429_rate_limit_displays_cooldown_banner`  
  *Input*: Sarvam returns HTTP 429 on TTS call.  
  *Expected*: Transitions to `ERROR` with rate limit message; preserves translated text on screen.
- **T2-F6-04**: `test_tts_very_long_text_input_handled_without_buffer_overflow`  
  *Input*: 1,000-word paragraph passed to TTS.  
  *Expected*: Request payload constructs valid JSON without crashing.
- **T2-F6-05**: `test_tts_http_502_bad_gateway_handles_retry`  
  *Input*: Sarvam returns HTTP 502 Bad Gateway.  
  *Expected*: Transitions to `ERROR`, enables retry without losing translated text.

### F7: Audio Player Boundaries
- **T2-F7-01**: `test_player_corrupt_wav_header_triggers_error_callback`  
  *Input*: Decoded bytes with corrupted RIFF signature.  
  *Expected*: `MediaPlayer.onError` triggered, error callback invoked, no crash.
- **T2-F7-02**: `test_player_interrupted_by_new_recording_stops_immediately`  
  *Input*: User presses PTT while audio is actively playing.  
  *Expected*: `MediaPlayer.stop()` called immediately, player released, recording starts.
- **T2-F7-03**: `test_player_audio_focus_loss_pauses_or_releases_player`  
  *Input*: Audio focus changes to `AUDIOFOCUS_LOSS`.  
  *Expected*: Player stops and releases cleanly.
- **T2-F7-04**: `test_player_cache_dir_full_handles_ioexception`  
  *Input*: `File.createTempFile` throws `IOException` (disk full).  
  *Expected*: Error callback invoked with descriptive message; no unhandled exception.
- **T2-F7-05**: `test_player_concurrent_start_stop_calls_synchronized`  
  *Input*: Concurrent calls to `playWavBytes` and `stopAndRelease` from multiple threads.  
  *Expected*: Synchronized lock prevents race conditions or `IllegalStateException`.

### F8: Replay Trigger Boundaries
- **T2-F8-01**: `test_replay_rapid_double_click_debounces_playback`  
  *Input*: Two clicks on Replay button within 50ms.  
  *Expected*: First playback starts, second click ignored (debounced); single audio stream.
- **T2-F8-02**: `test_replay_clicked_while_already_playing_restarts_or_ignores`  
  *Input*: Click Replay button while state is `Playing`.  
  *Expected*: Replay stops current player and restarts from beginning cleanly.
- **T2-F8-03**: `test_replay_clicked_after_network_error_preserves_last_valid_audio`  
  *Input*: Previous turn produced audio; current turn failed STT.  
  *Expected*: Last valid audio remains cached and playable via Replay.
- **T2-F8-04**: `test_replay_cache_file_deleted_by_os_handles_gracefully`  
  *Input*: Cache file deleted from disk by Android OS memory cleaner.  
  *Expected*: Catches `FileNotFoundException`, alerts user, disables replay button.
- **T2-F8-05**: `test_replay_disabled_during_active_recording`  
  *Input*: State is `Recording`.  
  *Expected*: Replay button is disabled to prevent acoustic feedback into microphone.

### F9: Single-Screen UI Boundaries
- **T2-F9-01**: `test_screen_configuration_change_preserves_pipeline_state`  
  *Input*: Rotate screen from Portrait to Landscape during `TRANSLATING`.  
  *Expected*: ViewModel retains state, UI resumes in `TRANSLATING` with existing transcript.
- **T2-F9-02**: `test_screen_compact_display_320dp_width_no_overflow`  
  *Input*: Set device width to 320dp (small phone screen).  
  *Expected*: Elements wrap or scroll; zero layout clipping or Compose layout exceptions.
- **T2-F9-03**: `test_screen_extreme_text_length_scrolls_inside_cards`  
  *Input*: 500-word translation rendered in target card.  
  *Expected*: Card provides vertical scroll; PTT button remains accessible at bottom.
- **T2-F9-04**: `test_screen_dark_and_light_theme_switch_contrast_ratios`  
  *Input*: Toggle `isSystemInDarkTheme()`.  
  *Expected*: Text-to-background contrast ratio $\ge 4.5:1$ (WCAG AA compliant).
- **T2-F9-05**: `test_screen_back_press_releases_audio_and_resets_pipeline`  
  *Input*: System Back gesture during `RECORDING`.  
  *Expected*: Cancels recording coroutine, releases mic, returns to `IDLE`.

### F10: PTT Gesture Boundaries
- **T2-F10-01**: `test_accidental_short_tap_less_than_300ms_reverts_to_idle`  
  *Input*: Touch down and release within 150ms.  
  *Expected*: Discards buffer, shows toast: *"Hold button while speaking"*, reverts to `IDLE`.
- **T2-F10-02**: `test_gesture_pointer_drag_outside_button_bounds_cancels_or_completes`  
  *Input*: User presses PTT and drags finger 100dp outside button.  
  *Expected*: Touch up or cancellation detected, triggers `onPttRelease()` cleanly.
- **T2-F10-03**: `test_multi_touch_second_finger_ignored`  
  *Input*: User presses with one finger, then taps with second finger.  
  *Expected*: Primary gesture tracks original pointer ID; secondary pointers ignored.
- **T2-F10-04**: `test_touch_down_while_state_is_error_clears_error_and_records`  
  *Input*: PTT pressed when UI is in `ERROR` state.  
  *Expected*: Error dismissed, pipeline resets, initiates fresh `RECORDING` cycle.
- **T2-F10-05**: `test_zero_duration_touch_frame_handled_safely`  
  *Input*: Pointer down and up in the exact same event frame.  
  *Expected*: Treated as accidental tap (<300ms), discarded cleanly without network call.

### F11: Language Selector Boundaries
- **T2-F11-01**: `test_rapid_source_target_language_switching_no_race_condition`  
  *Input*: Rapidly select 5 different languages in 200ms.  
  *Expected*: Final selection correctly reflected in `MainUiState`; no thread desync.
- **T2-F11-02**: `test_language_selectors_disabled_while_recording_and_processing`  
  *Input*: State is `RECORDING` or `TRANSLATING`.  
  *Expected*: Dropdowns are non-interactive; selection clicks ignored.
- **T2-F11-03**: `test_invalid_bcp47_code_lookup_returns_null_and_defaults`  
  *Input*: `SupportedLanguage.fromBcp47("xx-YY")`.  
  *Expected*: Returns `null`, falls back to `SupportedLanguage.ENGLISH` safely.
- **T2-F11-04**: `test_language_swap_button_exchanges_source_and_target`  
  *Input*: Source=Hindi, Target=English; tap swap button (⇄).  
  *Expected*: Source becomes English, Target becomes Hindi.
- **T2-F11-05**: `test_language_switch_preserves_card_history_until_next_recording`  
  *Input*: Switch target language from English to Marathi after a conversation turn.  
  *Expected*: Existing English card remains visible until new PTT recording begins.

### F12: Dual Cards Boundaries
- **T2-F12-01**: `test_transcript_with_leading_trailing_whitespace_trimmed_cleanly`  
  *Input*: Saaras returns `"   नमस्ते   \n\t"`.  
  *Expected*: Card renders trimmed text `"नमस्ते"`.
- **T2-F12-02**: `test_transcript_with_embedded_html_tags_renders_raw_text`  
  *Input*: `"<b>Warning</b> <script>alert(1)</script>"`.  
  *Expected*: Renders raw text without interpretation or injection vulnerability.
- **T2-F12-03**: `test_mixed_script_code_mixed_text_renders_cleanly`  
  *Input*: Hinglish: *"Main station par hoon, train 10 baje aayegi"*.  
  *Expected*: Devanagari and Latin characters render with appropriate typography.
- **T2-F12-04**: `test_card_copy_to_clipboard_functionality`  
  *Input*: Long press on translation card.  
  *Expected*: Translated text copied to Android system clipboard with confirmation toast.
- **T2-F12-05**: `test_non_printable_control_characters_filtered`  
  *Input*: Text containing ASCII 0x00 (NUL), 0x07 (BEL).  
  *Expected*: Sanitized, non-printable characters stripped before display.

### F13: Reactive State Machine Boundaries
- **T2-F13-01**: `test_unexpected_runtime_exception_transitions_to_error`  
  *Input*: Coroutine throws `RuntimeException("Unexpected HAL error")`.  
  *Expected*: State transitions to `PipelineState.Error`, UI shows error banner.
- **T2-F13-02**: `test_dismiss_error_reverts_state_to_idle`  
  *Input*: Click "Dismiss" button on error banner.  
  *Expected*: `MainUiState.pipelineState` reverts to `PipelineState.Idle`.
- **T2-F13-03**: `test_coroutine_cancellation_during_stt_resets_to_idle`  
  *Input*: Cancel ViewModel coroutine scope while in `TRANSCRIBING`.  
  *Expected*: Pipeline catches `CancellationException`, cleans up, returns to `IDLE`.
- **T2-F13-04**: `test_illegal_state_transition_attempt_is_rejected`  
  *Input*: Attempt to invoke `synthesize()` directly from `IDLE`.  
  *Expected*: Guard condition blocks invalid transition, maintains `IDLE`.
- **T2-F13-05**: `test_consecutive_errors_display_most_recent_error_message`  
  *Input*: Error 1 occurred, then Error 2 occurred.  
  *Expected*: Error banner displays Error 2 message with latest diagnostic info.

### F14: Quick Feedback Bar Boundaries
- **T2-F14-01**: `test_rapid_feedback_clicking_registers_only_first_click`  
  *Input*: Tap Thumbs Up 5 times within 100ms.  
  *Expected*: Dispatches single feedback record; subsequent taps ignored.
- **T2-F14-02**: `test_toggle_feedback_from_positive_to_negative_updates_record`  
  *Input*: Tap Thumbs Up, then tap Thumbs Down for same interaction.  
  *Expected*: Updates interaction rating to `"negative"` without creating duplicate record.
- **T2-F14-03**: `test_feedback_submitted_state_resets_on_next_recording`  
  *Input*: Feedback submitted; user presses PTT for next turn.  
  *Expected*: `feedbackSubmitted` resets to `false`, buttons re-enable for new turn.
- **T2-F14-04**: `test_feedback_clicked_with_device_offline_logs_locally_without_error`  
  *Input*: Disconnect device network, submit feedback.  
  *Expected*: Successfully written to `feedback_logs.json` (100% local operation).
- **T2-F14-05**: `test_feedback_ui_disabled_during_audio_recording`  
  *Input*: State is `Recording`.  
  *Expected*: Feedback buttons disabled.

### F15: Feedback Logger Boundaries
- **T2-F15-01**: `test_corrupted_json_file_backed_up_and_reinitialized`  
  *Input*: `feedback_logs.json` contains malformed JSON `[{corrupt...`.  
  *Expected*: File backed up to `feedback_logs.json.bak`, fresh file created, record logged.
- **T2-F15-02**: `test_concurrent_multithreaded_feedback_logging_is_thread_safe`  
  *Input*: 10 parallel coroutines log feedback simultaneously.  
  *Expected*: All 10 records present in JSON file; zero JSON syntax corruption.
- **T2-F15-03**: `test_empty_zero_byte_feedback_file_reinitialized_cleanly`  
  *Input*: `feedback_logs.json` exists with length 0 bytes.  
  *Expected*: Treats as empty list, logs new record in valid JSON array.
- **T2-F15-04**: `test_disk_write_permission_error_returns_result_failure`  
  *Input*: Make target directory read-only.  
  *Expected*: Catches `IOException`, returns `Result.failure(e)`, UI does not crash.
- **T2-F15-05**: `test_large_feedback_log_file_appends_without_loading_full_history`  
  *Input*: File contains 5,000 existing records.  
  *Expected*: Appends new record efficiently without memory spike.

### F16: API Key Security Boundaries
- **T2-F16-01**: `test_placeholder_api_key_flags_configuration_warning`  
  *Input*: API key is `"YOUR_API_KEY_HERE"` or `"your_sarvam_api_key_here"`.  
  *Expected*: UI shows configuration warning card: *"API Key: Pending / Default"*.
- **T2-F16-02**: `test_api_key_with_leading_trailing_spaces_trimmed`  
  *Input*: `"  secret_key_123  "`.  
  *Expected*: Auth interceptor trims whitespace and injects `"secret_key_123"`.
- **T2-F16-03**: `test_network_logging_redacts_auth_header_completely`  
  *Input*: HttpLoggingInterceptor logs headers at Level.BODY.  
  *Expected*: Log output shows `api-subscription-key: ████` (never plaintext).
- **T2-F16-04**: `test_missing_local_properties_defaults_to_empty_string`  
  *Input*: Delete `local.properties`.  
  *Expected*: Build succeeds; `BuildConfig.SARVAM_API_KEY` defaults to `""`.
- **T2-F16-05**: `test_environment_variable_fallback_for_api_key`  
  *Input*: `local.properties` missing key, but `System.getenv("SARVAM_API_KEY")` is set.  
  *Expected*: Resolves key from environment variable.

### F17: Real-Time Telemetry Boundaries
- **T2-F17-01**: `test_telemetry_clock_drift_clamps_negative_latency_to_zero`  
  *Input*: System time shifts backwards during API call.  
  *Expected*: Clamps latency to 0 ms minimum; never displays negative numbers.
- **T2-F17-02**: `test_telemetry_high_latency_displays_warning_indicator`  
  *Input*: STT takes 8,500ms due to slow mobile network.  
  *Expected*: UI displays `STT: 8500ms` with warning color badge.
- **T2-F17-03**: `test_telemetry_partial_pipeline_failure_records_elapsed_stage`  
  *Input*: STT succeeds (400ms), Translation fails with HTTP 500.  
  *Expected*: Telemetry records `sttMs = 400`, `translateMs = 0`, `ttsMs = 0`.
- **T2-F17-04**: `test_telemetry_resets_on_fresh_recording_press`  
  *Input*: Previous turn had latencies; user presses PTT for new turn.  
  *Expected*: Telemetry readouts clear or reset to `--ms`.
- **T2-F17-05**: `test_telemetry_audio_duration_calculation_accuracy`  
  *Input*: 64,000 bytes recorded at 32,000 bytes/sec.  
  *Expected*: `audioDurationMs == 2000` ($2.0\text{s}$).

### F18: Emergency Audio Boundaries
- **T2-F18-01**: `test_emergency_playback_during_ringer_silent_mode`  
  *Input*: Device in `RINGER_MODE_SILENT`.  
  *Expected*: Plays at audible alarm volume through hardware speaker.
- **T2-F18-02**: `test_emergency_playback_during_dnd_do_not_disturb`  
  *Input*: Device DND enabled.  
  *Expected*: Alarm stream bypasses DND filter as designated by Android OS.
- **T2-F18-03**: `test_emergency_audio_wired_headset_unplug_transitions_smoothly`  
  *Input*: Headphones unplugged during emergency playback.  
  *Expected*: Handles `ACTION_AUDIO_BECOMING_NOISY` without crash; continues on speaker.
- **T2-F18-04**: `test_emergency_stream_max_volume_override`  
  *Input*: Media volume slider at 0%.  
  *Expected*: Alarm stream plays according to alarm volume channel, not media volume.
- **T2-F18-05**: `test_emergency_audio_release_on_incoming_phone_call`  
  *Input*: Telephony manager reports `CALL_STATE_RINGING`.  
  *Expected*: Emergency playback pauses or yields priority to cellular emergency call.

---

## 5. Tier 3: Cross-Feature Combinations & Pairwise Matrix

Tier 3 validates seamless multi-module interaction across the entire application stack:

| Test ID | Primary Features | Interaction Description | Expected Observable Outcome |
|---|---|---|---|
| **T3-01** | `F10` + `F11` + `F1` + `F2` + `F4` + `F5` + `F6` + `F7` + `F8` | **Language Switch + PTT Hold + Full Pipeline + Replay**: Switch language to Tamil -> Hindi. Hold PTT, speak Tamil. Audio captured, encoded to WAV, transcribed via Saaras, translated to Hindi via Mayura, synthesized via Bulbul, played via speaker. User taps Replay button. | Replay plays identical Hindi synthesized WAV from memory without dispatching any additional HTTP calls. Both cards remain visible. |
| **T3-02** | `F5` + `F13` + `F17` + `F10` + `F4` | **Network Failure during Translation + Error Banner + User Retry**: STT succeeds, but Mayura v1 returns HTTP 503 during translation. Error banner displayed. User taps PTT to retry. | State resets from `ERROR` to `RECORDING`. Previous transcript cleared. Fresh recording proceeds through all 3 stages successfully. |
| **T3-03** | `F7` + `F10` + `F13` + `F1` | **Rapid PTT Press Interrupting Active Audio Playback**: While Bulbul v3 audio is playing through device speaker, user urgently presses PTT to reply. | Active `MediaPlayer` immediately stops and releases. State transitions directly from `PLAYING` to `RECORDING`. No audio feedback or crash. |
| **T3-04** | `F4` + `F13` + `F12` | **STT Silence (Empty Transcript) Reset**: Saaras v3 returns empty transcript `""` (background noise or silence). | Pipeline does not invoke Translation or TTS. Transitions to `IDLE` with hint *"No speech detected"*. Target card remains clear. |
| **T3-05** | `F14` + `F15` + `F11` + `F13` | **Multi-Turn Feedback Logging Across Different Language Pairs**: User completes Turn 1 (Hindi -> English) and logs 👍. User switches to English -> Marathi, completes Turn 2, logs 👎. | `feedback_logs.json` contains exactly 2 valid records with distinct UUIDs, correct language pairs, and matched feedback ratings. |
| **T3-06** | `F3` + `F13` + `F10` + `F1` | **Permission Denial -> Banner -> Grant -> Immediate PTT**: User denies mic permission -> Error banner shown. User taps "Grant Permission" -> system dialog opens -> user grants. User immediately presses PTT. | State machine moves directly to `RECORDING` and captures 16 kHz PCM without requiring app restart. |
| **T3-07** | `F14` + `F15` + `F13` | **Corrupted Feedback Log Auto-Recovery on Submission**: `feedback_logs.json` is corrupted externally. User clicks 👍 on translation card. | Logger catches JSON error, renames corrupted file to `.bak`, creates clean `feedback_logs.json`, and records new feedback record. |
| **T3-08** | `F18` + `F6` + `F7` + `F13` | **Emergency Audio Route Playback for Disaster Warning**: Pipeline completes high-priority disaster warning translation. Audio player triggers playback using `USAGE_ALARM`. | Audio plays at alarm volume on device speaker, even if media volume was muted. |
| **T3-09** | `F4` + `F13` + `F17` + `F10` | **Rate Limit 429 Error Cooldown & Retry**: Saaras v3 returns HTTP 429 Too Many Requests. Telemetry marks error. Cooldown banner displayed. After 3 seconds, user speaks again. | Second attempt succeeds, telemetry updates with fresh latency stats, state machine completes full cycle. |
| **T3-10** | `F11` + `F13` + `F9` | **Language Dropdown Lock During Live Processing**: While state is `TRANSCRIBING` or `TRANSLATING`, user taps language dropdown selectors. | Dropdown menus do not expand. Selections remain locked to prevent in-flight pipeline corruption. |
| **T3-11** | `F8` + `F10` + `F13` | **Replay Button Disabled During Live Recording**: User holds PTT button. | Replay button is disabled (`enabled == false`) during `RECORDING` to prevent simultaneous acoustic input and output. |
| **T3-12** | `F17` + `F4` + `F5` + `F6` + `F13` | **End-to-End Latency Telemetry Aggregation**: Pipeline completes STT (420ms), Translation (210ms), and TTS (350ms). | `LatencyStats` records all 3 stages; total latency computed as $980\text{ms}$; displayed on UI telemetry bar. |

---

## 6. Tier 4: Real-World Multi-Turn Application Scenarios

Tier 4 exercises authentic field communication scenarios based on ISRO SIH26173 mission requirements:

### Scenario 1: Coastal Disaster Emergency Warning (Hindi $\rightarrow$ English)
- **Context**: Cyclone warning broadcast in Odisha/Andhra coastal belt.
- **Workflow**:
  1. Operator launches iTantra. Default languages: Hindi (`hi-IN`) -> English (`en-IN`).
  2. Operator holds PTT button for 3.2 seconds.
  3. Audio captured: 16 kHz Mono PCM (102,400 bytes).
  4. Encoded to WAV (102,444 bytes with 44-byte RIFF header).
  5. Saaras v3 transcribes: `"तटीय क्षेत्र में भीषण चक्रवात की चेतावनी है, तुरंत सुरक्षित स्थान पर जाएं"`.
  6. Mayura v1 translates: `"There is a warning of severe cyclone in coastal area, move to safe place immediately"`.
  7. Bulbul v3 synthesizes English audio with voice `meera`.
  8. Audio plays through loudspeaker using emergency audio attributes (`USAGE_ALARM`).
  9. Operator reviews cards, clicks 👍 (thumbs up).
  10. Verification: Feedback record logged in `feedback_logs.json` with positive rating, correct transcript, and translation.

### Scenario 2: Tamil Health Inquiry to Hindi First Responder (Tamil $\rightarrow$ Hindi)
- **Context**: Relief camp medical triage query.
- **Workflow**:
  1. User switches Source Language to Tamil (`ta-IN`) and Target Language to Hindi (`hi-IN`).
  2. User holds PTT button for 2.4 seconds.
  3. Audio encoded to WAV and sent to Saaras v3 with `language_code="ta-IN"`.
  4. Saaras v3 transcribes: `"எனக்கு கடுமையான காய்ச்சல் மற்றும் தலைவலி உள்ளது"`.
  5. Mayura v1 translates: `"मुझे तेज बुखार और सिरदर्द है"`.
  6. Bulbul v3 synthesizes Hindi speech with voice `meera`.
  7. Audio plays through device speaker.
  8. First responder clicks "Replay Audio" to verify details. Audio plays again without network requests.
  9. First responder clicks 👍.
  10. Verification: Telemetry shows latencies: STT=480ms, Trans=220ms, TTS=360ms, Total=1060ms. Feedback logged.

### Scenario 3: English Tactical Command to Marathi Field Unit (English $\rightarrow$ Marathi)
- **Context**: Search and rescue team field coordination.
- **Workflow**:
  1. User switches Source Language to English (`en-IN`) and Target Language to Marathi (`mr-IN`).
  2. User holds PTT button for 2.8 seconds, speaking: *"Evacuate sector four and report to base camp immediately"*.
  3. Saaras v3 transcribes: `"Evacuate sector four and report to base camp immediately"`.
  4. Mayura v1 translates: `"सेक्टर चार रिकामे करा आणि त्वरित बेस कॅम्पवर रिपोर्ट करा"`.
  5. Bulbul v3 synthesizes Marathi speech.
  6. Field commander listens, clicks 👎 (negative feedback) to flag minor translation nuance.
  7. Verification: `feedback_logs.json` contains entry with `feedback="negative"` and exact text strings.

### Scenario 4: Bengali Fishermen Weather Advisory to Odia Patrol (Bengali $\rightarrow$ Odia)
- **Context**: Inter-state maritime border safety communication.
- **Workflow**:
  1. User switches Source Language to Bengali (`bn-IN`) and Target Language to Odia (`od-IN`).
  2. User holds PTT button, speaking Bengali weather alert: `"আগামী চব্বিশ ঘণ্টায় সমুদ্রে যাবেন না"`.
  3. Saaras v3 transcribes Bengali text accurately.
  4. Mayura v1 translates to Odia: `"ଆଗାମୀ ଚବିଶ ଘଣ୍ଟାରେ ସମୁଦ୍ରକୁ ଯାଆନ୍ତୁ ନାହିଁ"`.
  5. Bulbul v3 synthesizes Odia speech with 16 kHz sample rate.
  6. Verification: Both complex Indic scripts render without font clipping on single screen. Audio plays cleanly.

### Scenario 5: Intermittent Disaster Zone Outage Recovery (Gujarati $\rightarrow$ Telugu)
- **Context**: Mobile communication in disaster zone with network drops.
- **Workflow**:
  1. User sets Source Language to Gujarati (`gu-IN`) and Target Language to Telugu (`te-IN`).
  2. User presses PTT and speaks. Network drops during Saaras v3 upload -> `SocketTimeoutException`.
  3. UI transitions cleanly to `ERROR` state with banner: *"Unable to reach Sarvam AI servers. Please check your internet connection."*
  4. Connectivity restores. User taps PTT again to retry.
  5. Pipeline executes end-to-end: Gujarati transcript `"પીવાના પાણીની તાત્કાલિક જરૂર છે"` -> Telugu translation `"తాగునీటి తక్షణ అవసరం ఉంది"` -> Telugu speech playback.
  6. Verification: Error banner dismisses automatically, pipeline reaches `IDLE` ready state.

---

## 7. Authoritative Expected Output Derivation & Reference Oracles

Every test assertion in the iTantra test suite is derived from an explicit authoritative source:

1. **Audio WAV Header Structure**:
   - **Source**: `PS_SIH26173_OFFICIAL.md` § Audio Specs & `01_RESEARCH_FOR_AGENT.md` § Audio Architecture.
   - **Formula**:
     $$\text{ChunkSize} = 36 + \text{PCMByteCount}$$
     $$\text{ByteRate} = \text{SampleRate} \times \text{NumChannels} \times \frac{\text{BitsPerSample}}{8} = 16000 \times 1 \times 2 = 32,000 \text{ bytes/s}$$
     $$\text{BlockAlign} = \text{NumChannels} \times \frac{\text{BitsPerSample}}{8} = 1 \times 2 = 2$$
   - **Endianness**: Little-endian for all multi-byte numeric fields; Big-endian ASCII for `"RIFF"`, `"WAVE"`, `"fmt "`, `"data"`.

2. **Sarvam AI REST Payloads**:
   - **Source**: `ORIGINAL_REQUEST.md` § Follow-up Verified Specifications.
   - **STT**: `POST /speech-to-text` multipart with `model="saaras:v3"`, `language_code`, `mode="transcribe"`, `file`.
   - **Translate**: `POST /translate` JSON body `{"input":"...","source_language_code":"...","target_language_code":"...","model":"mayura:v1","mode":"formal"}`.
   - **TTS**: `POST /text-to-speech` JSON body `{"inputs":["..."],"target_language_code":"...","speaker":"meera","model":"bulbul:v3","speech_sample_rate":16000}`.
   - **Auth**: Header `api-subscription-key: <KEY>`.

3. **Feedback Storage Schema**:
   - **Source**: `ORIGINAL_REQUEST.md` § R4 & `PROJECT.md` § Feedback Logger Contract.
   - **Target**: `File(context.filesDir, "feedback_logs.json")`.
   - **Fields**: `id`, `timestamp`, `timestamp_iso`, `source_language`, `target_language`, `source_text`, `translated_text`, `feedback`.

---

## 8. Test Runner Commands & Execution Guide

### 8.1 Unit & Integration Tests (JVM / Hermetic MockWebServer)
Executes all Tier 1, Tier 2, Tier 3, and Tier 4 offline hermetic tests on the local JVM without requiring an attached Android device or live Sarvam AI network connection:

```bash
# Run all unit and integration tests
./gradlew test

# Run tests with detailed stacktrace and continuous info logging
./gradlew test --info --stacktrace

# Run specific test classes
./gradlew test --tests "com.itantra.voice.audio.WavEncoderTest"
./gradlew test --tests "com.itantra.voice.data.LanguageTest"
./gradlew test --tests "com.itantra.voice.data.FeedbackLoggerTest"
./gradlew test --tests "com.itantra.voice.network.SarvamApiClientTest"
./gradlew test --tests "com.itantra.voice.ui.MainViewModelTest"
./gradlew test --tests "com.itantra.voice.e2e.*"
```

### 8.2 Instrumented Android Device / Emulator Tests
Executes UI, AudioRecord hardware, and Compose interaction tests on a running emulator (`emulator-5554`, API 35/36):

```bash
# Verify emulator is connected
adb devices

# Run instrumented tests on connected emulator
./gradlew connectedAndroidTest

# Run specific instrumented UI test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.itantra.voice.ui.MainScreenE2ETest
```

### 8.3 Android CLI Inspection Commands
```bash
# Capture screenshot of running test on emulator
android screen capture -o=test_run_screenshot.png

# Inspect Compose layout tree in JSON
android layout -p com.itantra.voice
```

---

## 9. Quality Thresholds & Milestone Exit Criteria

To achieve certification and declare milestone completion, the test suite must strictly satisfy:

| Metric | Target Threshold | Verification Command |
|---|---|---|
| **Tier 1 Feature Coverage** | 100% Pass ($\ge 90/90$ test cases across all 18 features) | `./gradlew test` |
| **Tier 2 Boundary Cases** | 100% Pass ($\ge 90/90$ boundary cases) | `./gradlew test` |
| **Tier 3 Cross-Feature** | 100% Pass (All 12 pairwise interactions verified) | `./gradlew test --tests "com.itantra.voice.e2e.Tier3*"` |
| **Tier 4 Real-World Scenarios** | 100% Pass (All 5 field communication workflows) | `./gradlew test --tests "com.itantra.voice.e2e.Tier4*"` |
| **Code Layout Compliance** | 100% compliant with `PROJECT.md § Code Layout` | `find app/src/test -name "*.kt"` |
| **Hermetic Reliability** | 0 flaky tests; 100% deterministic mock execution | 3 consecutive runs of `./gradlew test` |
| **Memory / Resource Leak** | 0 AudioRecord or MediaPlayer resource leaks | Verified via test teardown assertions |

---
