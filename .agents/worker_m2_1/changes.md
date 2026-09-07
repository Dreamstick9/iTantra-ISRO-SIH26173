# Changes Report — Milestone M2: Single-Screen Compose UI & 7-State FSM

**Worker**: `worker_m2_1`  
**Timestamp**: 2026-09-07T11:15:00Z  
**Target Platform**: Android Native (Kotlin 2.3.20, Min SDK 26, Target SDK 35, Jetpack Compose BOM 2026.03.01)

---

## 1. Summary of Changes

Milestone M2 implements the complete native Jetpack Compose single-screen UI, the 7-state finite state machine (`MainViewModel`), the tactile Push-to-Talk (PTT) pointer input gesture handler, runtime audio permissions, real-time latency telemetry, dual speech text display cards, language switching, and comprehensive unit tests.

---

## 2. File Modification Details

### 2.1 `app/src/main/java/com/itantra/voice/ui/MainViewModel.kt` (Created)
- **7-State FSM**:
  - Defined `enum class PttState`: `IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`.
  - Defined `data class LatencyStats`: `sttMs`, `translateMs`, `ttsMs`, `totalMs`.
  - Defined `data class MainUiState`: contains reactive state, source/target languages, recognized transcript, translated text, audio replay readiness, error messages, amplitude, and holding state.
- **Pipeline Orchestration**:
  - `onPttPress()`: starts `AudioRecorder.startRecording()`, tracks start timestamp, resets previous latencies, transitions state to `RECORDING`.
  - `onPttRelease()`: enforces $\ge 300\text{ms}$ threshold. Cancels accidental short taps (<300ms) with *"Hold button while speaking"* to `IDLE` without network requests. Encodes PCM to 44-byte RIFF WAV and executes the pipeline in background coroutine.
  - Speech pipeline:
    1. Saaras v3 STT: if empty, displays *"No speech detected"* and aborts downstream calls; otherwise updates `sourceTranscript` and transitions to `TRANSLATING`.
    2. Mayura v1 Translate: translates input text, records stage latency, updates `translatedText` and transitions to `SYNTHESIZING`.
    3. Bulbul v3 TTS: generates Base64 WAV speech audio, stores in `cachedAudioBase64`, updates `hasAudioToReplay = true`, transitions to `PLAYING`.
    4. Playback: calls `AudioPlayer.playBase64Wav()`; upon completion cleanly transitions back to `IDLE`.
  - `onReplayAudio()`: replays cached audio through `AudioPlayer` without making network requests.
  - `onSourceLanguageChange(Language)`, `onTargetLanguageChange(Language)`, `onSwapLanguages()`.
  - `onDismissError()`: clears error messages and resets state to `IDLE`.
  - `onPermissionResult(Boolean)`: reacts to runtime microphone permission results.

### 2.2 `app/src/main/java/com/itantra/voice/ui/components/` (Created)
- **`Header.kt`**: Displays "iTantra" title, "Neural Multilingual Transceiver" subtitle, and dynamic reactive status badge with color coding per state.
- **`TelemetryBar.kt`**: Displays real-time latency readouts (`STT: --ms | Trans: --ms | TTS: --ms | Total: --ms`).
- **`LanguageSelector.kt`**: Dropdown selectors for source and target Indic languages showing native names and BCP-47 badges, plus ⇄ swap button.
- **`PttButton.kt`**: Tactile 130dp circular button with `awaitEachGesture` down/up touch detection, 1.15x scale animation, amplitude-responsive pulsing outer ring, and dynamic state-driven color shifts (Red during recording, Teal during playback, Gray when busy).
- **`TextCards.kt`**: Dual display cards for source speech input and translated target text with UTF-8 Indic script rendering, copy-to-clipboard, and inline `Replay Audio` (▶) trigger.
- **`FeedbackBar.kt`**: Quick binary feedback bar (👍 / 👎) with visual submission confirmation.
- **`ErrorBanner.kt`**: Animated expandable warning banner with clear user-facing error text, "Grant Permission" retry action, and dismiss button.

### 2.3 `app/src/main/java/com/itantra/voice/ui/MainScreen.kt` (Created)
- Single unified transceiver screen assembling Header, Telemetry, Error Banner, Language Selectors, Text Cards, Feedback Bar, and PTT Button in a strict, distraction-free visual hierarchy without navigation destinations or tabs.

### 2.4 `app/src/main/java/com/itantra/voice/ui/theme/Theme.kt` (Modified)
- Removed deprecated `window.statusBarColor` assignment.
- Integrated modern window insets controller for edge-to-edge rendering.

### 2.5 `app/src/main/java/com/itantra/voice/MainActivity.kt` (Modified)
- Enabled `enableEdgeToEdge()` configuration.
- Added runtime `RECORD_AUDIO` permission check and contract launcher.
- Hosted `MainScreen` inside `ITantraTheme` with `MainViewModel` lifecycle binding and audio cache directory initialization.

### 2.6 `app/src/test/java/com/itantra/voice/ui/MainViewModelTest.kt` (Created)
- 17 unit tests verifying:
  1. Initial state is `IDLE`.
  2. PTT press transitions to `RECORDING`.
  3. Short tap (<300ms) cancels to `IDLE` with 0 network calls.
  4. Full pipeline success: `IDLE -> RECORDING -> TRANSCRIBING -> TRANSLATING -> SYNTHESIZING -> PLAYING -> IDLE`.
  5. STT network error transitions to `ERROR`.
  6. Translation network error transitions to `ERROR`.
  7. TTS synthesis error transitions to `ERROR`.
  8. Silence STT aborts downstream pipeline to `IDLE`.
  9. Replay plays cached audio without network requests.
  10. Language switching updates state.
  11. Language swap button exchanges source and target.
  12. Dismiss error reverts state to `IDLE`.
  13. PTT press while error clears error and starts recording.
  14. PTT press while playing stops playback immediately.
  15. Feedback submission updates feedback state.
  16. Permission result handling updates UI error state.
  17. Telemetry resets on fresh recording press.

---

## 3. Verification Results

- **Unit Tests**:
  Command: `./gradlew test --rerun-tasks`
  Result: **164 actionable tests executed, 164 passed, 0 failed, 0 skipped (100% pass rate)**.
- **Build APK**:
  Command: `./gradlew assembleDebug`
  Result: **BUILD SUCCESSFUL in 1s**.
- **Emulator Deployment**:
  Target: `medium_phone(AVD) - 16` (`emulator-5554`)
  Command: `./gradlew installDebug && adb shell am start -n com.itantra.voice/.MainActivity`
  Result: Successfully installed and launched.
- **Visual & Layout Inspection**:
  - Screenshot: Captured to `/Users/spirit/Downloads/spiritsih/.agents/worker_m2_1/m2_screenshot.png`.
  - Layout tree: Captured via `android layout -p`, confirming all UI elements, accessibility labels, and interaction handlers are active in the hierarchy.
