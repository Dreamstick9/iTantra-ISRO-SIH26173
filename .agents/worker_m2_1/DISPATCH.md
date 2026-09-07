# DISPATCH — 2026-09-07T10:37:00Z

## Mission
You are `worker_m2_1`, a `teamwork_preview_worker` subagent executing Milestone M2: Single-Screen Compose UI & 7-State FSM.
Your working directory is: `/Users/spirit/Downloads/spiritsih/.agents/worker_m2_1`
The project workspace is: `/Users/spirit/Downloads/spiritsih`
The authoritative user request is: `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md` (MUST read first).
The project index is: `/Users/spirit/Downloads/spiritsih/PROJECT.md` (MUST read).
The test specification is: `/Users/spirit/Downloads/spiritsih/TEST_INFRA.md` and `TEST_READY.md`.

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Write Ownership
You exclusively own and will create/modify:
- `app/src/main/java/com/itantra/voice/ui/MainViewModel.kt`
- `app/src/main/java/com/itantra/voice/ui/MainScreen.kt`
- `app/src/main/java/com/itantra/voice/MainActivity.kt`
- `app/src/main/java/com/itantra/voice/ui/components/` (any composables such as PttButton, LanguageSelector, TextCards, ErrorBanner, TelemetryBar)
- `app/src/main/java/com/itantra/voice/ui/theme/Theme.kt` (apply enableEdgeToEdge and remove deprecated statusBarColor)
- `app/src/test/java/com/itantra/voice/ui/MainViewModelTest.kt`

## Task
1. Read `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md`, `PROJECT.md`, and `specs_report.md`.
2. Implement `MainViewModel.kt` in `com.itantra.voice.ui`:
   - State machine enum `PttState`: `IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`.
   - UI State: `MainUiState(state, sourceLanguage, targetLanguage, sourceTranscript, translatedText, hasAudioToReplay, errorMessage, latencies, amplitude, isHolding)`.
   - Methods:
     - `onPttPress()`: starts `AudioRecorder`, tracks press start timestamp, updates state to `RECORDING`.
     - `onPttRelease()`: checks duration: if $<300$ ms, aborts with *"Hold button while speaking"* and returns to `IDLE`; if $\ge 300$ ms, stops `AudioRecorder`, calls `WavEncoder.encode`, transitions to `TRANSCRIBING`.
     - In background: dispatches STT to `SarvamApiClient.transcribe(wavData, sourceLang.bcp47Code)`. If transcript is empty, shows *"No speech detected"* and returns to `IDLE`.
     - If STT succeeds: updates `sourceTranscript`, transitions to `TRANSLATING`, calls `SarvamApiClient.translate(transcript, sourceLang.bcp47Code, targetLang.bcp47Code)`.
     - If translation succeeds: updates `translatedText`, transitions to `SYNTHESIZING`, calls `SarvamApiClient.synthesize(translatedText, targetLang.bcp47Code)`.
     - If synthesis succeeds: transitions to `PLAYING`, plays audio via `AudioPlayer.playBase64Wav`, on complete returns to `IDLE` with `hasAudioToReplay = true`.
     - On any error: transitions to `ERROR` with user-friendly error banner message.
     - `onReplayAudio()`: plays cached audio via `AudioPlayer` without making network calls.
     - `onSourceLanguageChange(Language)` and `onTargetLanguageChange(Language)`.
     - `onDismissError()`.
3. Implement `MainScreen.kt` in `com.itantra.voice.ui`:
   - Single-screen Compose layout hierarchy:
     - **Header**: App title "iTantra", subtitle "Neural Multilingual Transceiver", and status chip (`IDLE`, `RECORDING`, etc.).
     - **Telemetry Bar**: real-time latency readouts (`STT: --ms | Trans: --ms | TTS: --ms | Total: --ms`).
     - **Language Selectors**: Source and Target dropdown selectors with swap button (⇄), showing localized script names and BCP-47 tags.
     - **PTT Button**: Big circular tactile "HOLD TO SPEAK" button using pointerInput gesture (`awaitEachGesture` with down/up detection), scaling up during press with pulsing visual indicator and red color.
     - **Source Transcript Card**: Card with language badge displaying recognized text.
     - **Translated Text Card**: Card with language badge displaying translated text, plus inline `Replay Audio` button (▶).
     - **Quick Feedback Bar**: Thumbs up (👍) and Thumbs down (👎) buttons.
     - **Error Banner**: Animated expandable error card with clear message and dismiss button.
4. Update `MainActivity.kt`:
   - `enableEdgeToEdge()` configuration.
   - Runtime `android.permission.RECORD_AUDIO` check and request launcher.
   - Host `MainScreen` inside `iTantraTheme`.
5. Write comprehensive unit tests in `MainViewModelTest.kt`:
   - Verify initial state is `IDLE`.
   - Verify PTT press transitions to `RECORDING`.
   - Verify short tap (<300ms) cancels to `IDLE` without network requests.
   - Verify full pipeline success: `IDLE -> RECORDING -> TRANSCRIBING -> TRANSLATING -> SYNTHESIZING -> PLAYING -> IDLE`.
   - Verify error handling: network error transitions to `ERROR`.
   - Verify replay plays cached audio without network calls.
   - Verify language switching.
6. Verify build and execution:
   - Run `./gradlew assembleDebug`
   - Run `./gradlew test` (ensure all tests pass 100%).
   - Deploy to running emulator `emulator-5554`:
     `./gradlew installDebug`
     `adb shell am start -n com.itantra.voice/.MainActivity`
   - Capture emulator screenshot: `android screen capture -o=/Users/spirit/Downloads/spiritsih/.agents/worker_m2_1/m2_screenshot.png`.
   - Inspect UI layout tree: `android layout -p`.
7. Write `/Users/spirit/Downloads/spiritsih/.agents/worker_m2_1/changes.md` and `handoff.md`, and notify the orchestrator.

## 2026-09-07T11:00:45Z
**Sender**: 9da65215-7c76-4ab0-8790-34b4580958ab
**Context**: Milestone M2 execution
**Content**: Checking on your progress with `./gradlew test`. The test run in task-112 completed with some assertions in `MainViewModelTest` failing (e.g. lines 309, 347, 405, 447). Please inspect the test failures, fix the issues in `MainViewModel.kt` or `MainViewModelTest.kt`, re-verify with `./gradlew test` and `./gradlew assembleDebug`, deploy to `emulator-5554`, capture the screenshot, and produce your handoff report.
**Action**: Inspect test output, fix failing assertions, complete verification, and report handoff.

## 2026-09-07T11:11:28Z
**Sender**: 9da65215-7c76-4ab0-8790-34b4580958ab
**Context**: Milestone M2 unit testing
**Content**: `./gradlew test` in task-134 is hanging at `:app:testDebugUnitTest`. Root cause: in `MainViewModelTest`, `testPttPressTransitionsToRecording` (and any test invoking `onPttPress()` without stopping) leaves `recordingJob` running on `testDispatcher`. Because `recordingJob` executes a `while (isRecordingInternal.get()) delay(10)` loop, `runTest` hangs indefinitely at the end of the test waiting for child coroutines to complete.
Fix requirement:
1. In `MainViewModelTest.kt`, ensure every test that calls `onPttPress()` (such as `testPttPressTransitionsToRecording`, `testPttPressWhileErrorClearsErrorAndStartsRecording`, etc.) calls `audioRecorder.stopRecording()` before `runTest` exits.
2. In `MainViewModelTest.tearDown()`, ensure `audioRecorder.stopRecording()` is called.
3. In `FakeNativeAudioRecord`, ensure `read()` doesn't get stuck in a 0-byte busy loop without terminating or resetting.
4. Cancel the hanging task-134, apply the fixes, re-run `./gradlew test`, verify 100% pass across all tests, build `assembleDebug`, deploy to `emulator-5554`, capture the screenshot, and produce your handoff report.
**Action**: Cancel hanging test task, apply fixes to `MainViewModelTest.kt`, execute clean verification, and submit handoff.

