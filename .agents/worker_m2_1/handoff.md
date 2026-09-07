# Handoff Report — Milestone M2: Single-Screen Compose UI & 7-State FSM

**Worker**: `worker_m2_1`  
**Timestamp**: 2026-09-07T11:15:00Z  
**Type**: Hard Handoff (Milestone Complete)  
**Parent Agent**: `9da65215-7c76-4ab0-8790-34b4580958ab`

---

## 1. Observation

1. **Source & UI Implementation**:
   - `app/src/main/java/com/itantra/voice/ui/MainViewModel.kt` implements the 7-state FSM (`IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`), unidirectional `MainUiState`, press-and-hold duration tracking ($\ge 300\text{ms}$ threshold), audio recording, WAV encoding, cloud pipeline dispatch, audio replay, language selection, and error handling.
   - `app/src/main/java/com/itantra/voice/ui/MainScreen.kt` and sub-composables in `app/src/main/java/com/itantra/voice/ui/components/` (`Header.kt`, `TelemetryBar.kt`, `LanguageSelector.kt`, `PttButton.kt`, `TextCards.kt`, `FeedbackBar.kt`, `ErrorBanner.kt`) implement the single-screen Compose hierarchy.
   - `app/src/main/java/com/itantra/voice/MainActivity.kt` configures `enableEdgeToEdge()`, checks and requests runtime `RECORD_AUDIO` permissions, and hosts `MainScreen` inside `ITantraTheme`.
   - `app/src/main/java/com/itantra/voice/ui/theme/Theme.kt` was updated to remove deprecated `window.statusBarColor` and support edge-to-edge transparent system bars.

2. **Test Suite Execution**:
   - Running `./gradlew test --rerun-tasks` executed 164 unit tests across all test suites:
     - `com.itantra.voice.ui.MainViewModelTest`: 17 tests passed (100%).
     - `com.itantra.voice.data.LanguageTest`: 10 tests passed.
     - `com.itantra.voice.audio.WavEncoderTest`: 12 tests passed.
     - `com.itantra.voice.audio.WavAudioOracleTest`: 10 tests passed.
     - `com.itantra.voice.audio.AudioRecorderTest`: 9 tests passed.
     - `com.itantra.voice.audio.AudioPlayerTest`: 12 tests passed.
     - `com.itantra.voice.audio.AudioEngineAdversarialStressTest`: 16 tests passed.
     - `com.itantra.voice.network.SarvamApiClientTest`: 11 tests passed.
     - `com.itantra.voice.network.SarvamApiClientStressTest`: 34 tests passed.
     - `com.itantra.voice.network.SarvamMockContractTest`: 7 tests passed.
     - `com.itantra.voice.BuildConfigTest`: 10 tests passed.
     - `com.itantra.voice.LanguageEdgeCaseStressTest`: 6 tests passed.
     - `com.itantra.voice.e2e.Tier3CrossFeatureTest`: 5 tests passed.
     - `com.itantra.voice.e2e.Tier4RealWorldScenarioTest`: 5 tests passed.
     - Total: **164 PASS, 0 FAIL, 0 SKIPPED (100% Pass Rate)**.

3. **Compilation & Packaging**:
   - Running `./gradlew assembleDebug` completes with **BUILD SUCCESSFUL** and 0 errors, 0 warnings.

4. **Live Device Deployment & Inspection on `emulator-5554`**:
   - Deployed via `./gradlew installDebug`: `Installed on 1 device (medium_phone(AVD) - 16)`.
   - Launched via `adb shell am start -n com.itantra.voice/.MainActivity`.
   - Screen captured via `android screen capture -o=/Users/spirit/Downloads/spiritsih/.agents/worker_m2_1/m2_screenshot.png`.
   - Layout tree inspected via `android layout -p`, confirming all nodes:
     - Header ("iTantra", "Neural Multilingual Transceiver", "IDLE" chip)
     - Telemetry Bar (`STT: --ms | Trans: --ms | TTS: --ms | Total: --ms`)
     - Language Dropdowns ("Hindi [hi-IN] / हिन्दी", "English [en-IN] / English", Swap `⇄`)
     - Text Cards ("INPUT SPEECH: HINDI · hi-IN", "TRANSLATION: ENGLISH · en-IN", "Replay Audio")
     - Feedback Bar ("Rate translation quality:", "👍", "👎")
     - Tactile PTT Button ("HOLD TO SPEAK")

---

## 2. Logic Chain

1. From `ORIGINAL_REQUEST.md § R3` and `DISPATCH.md § Task 2 & 3`, the application requires a single-screen transceiver interface with a 7-state finite state machine (`IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`).
2. The `MainViewModel` was structured with an immutable `StateFlow<MainUiState>` and explicit event handlers (`onPttPress`, `onPttRelease`, `onReplayAudio`, `onSourceLanguageChange`, `onTargetLanguageChange`, `onSwapLanguages`, `onFeedback`, `onDismissError`, `onPermissionResult`).
3. Press duration is tracked via `timeProvider()`. When duration is $<300\text{ms}$, it cancels cleanly to `IDLE` with *"Hold button while speaking"*, avoiding wasteful API requests. When duration $\ge 300\text{ms}$, it encodes captured PCM to standard 44-byte RIFF WAV and executes the pipeline.
4. Downstream steps are guarded: if STT yields an empty transcript, it halts downstream calls and resets to `IDLE` with *"No speech detected"*. Successful synthesis caches the Base64 WAV for instant replay via `onReplayAudio()` without network traffic.
5. In `MainScreen`, the UI is bound to `viewModel.uiState`. Tactile interaction is implemented on `PttButton` using `pointerInput` with `awaitEachGesture`, down/up tracking, a 1.15x spring scale animation, dynamic red color while recording, and amplitude-driven ring expansion.
6. The test suite verifies every transition, network failure mode, cancellation threshold, and replay scenario deterministically using hermetic mock fixtures.

---

## 3. Caveats

- **API Key Configuration**: The APK built in debug mode uses `BuildConfig.SARVAM_API_KEY` from `local.properties`. Live cloud calls against real Sarvam endpoints require a valid non-placeholder key in `local.properties`.
- **Microphone Permission**: On cold launch on fresh emulator installations without pre-granted permissions, the runtime permission prompt is triggered, and a permission rationale banner is displayed until granted.

---

## 4. Conclusion

Milestone M2 is fully implemented, verified, and ready for integration:
- Single-screen Jetpack Compose transceiver UI (`MainScreen.kt`) is fully functional.
- Unidirectional 7-State FSM (`MainViewModel.kt`) handles all transitions, error recovery, telemetry tracking, and replay caching.
- Tactile Push-to-Talk button (`PttButton.kt`) responds to press-and-hold gestures with amplitude visualization and short-tap filtering.
- Edge-to-edge support and runtime `RECORD_AUDIO` permissions are fully wired.
- 100% of the 164 unit tests pass cleanly.
- Visual inspection on `emulator-5554` confirmed correct rendering and layout tree.

---

## 5. Verification Method

To independently verify this milestone:

1. **Run Full Test Suite**:
   ```bash
   ./gradlew test --rerun-tasks
   ```
   *Expected*: 164 tests pass with 0 failures.

2. **Run ViewModel Specific Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.itantra.voice.ui.MainViewModelTest"
   ```
   *Expected*: All 17 tests in `MainViewModelTest` pass.

3. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   *Expected*: `BUILD SUCCESSFUL`.

4. **Deploy and Inspect on Emulator**:
   ```bash
   ./gradlew installDebug
   adb shell am start -n com.itantra.voice/.MainActivity
   android screen capture -o=verified_screen.png
   android layout -p
   ```
   *Expected*: Single screen renders without crashes, matching `m2_screenshot.png`.
