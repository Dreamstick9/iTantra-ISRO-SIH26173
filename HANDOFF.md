# Handoff: what changed, and how to drive it

You are picking this up from the **pre-refactor codebase**. This document is the bridge.
It covers where to get the new code, what moved, what broke and why, the new APIs, and how
to run and test everything.

Read `docs/ARCHITECTURE_NOTES.md` for the *why* behind each design decision. This file is
the *what* and the *how*.

---

## 0. Where this code lives — get it first

All of the work described below is on a **fork**, not on the original repository:

| | |
|---|---|
| **Fork (has everything)** | `https://github.com/Dreamstick9/iTantra-ISRO-SIH26173` |
| Upstream (the old code you have) | `https://github.com/Spirit019/iTantra-ISRO-SIH26173` |
| Branch | `main`, and identically `fix/offline-pipeline-and-ui-redesign` |

The fork's `main` is a **fast-forward** of upstream `main` — no history was rewritten and
no commits were dropped, so it merges cleanly. It builds directly on the last commit you
already have:

```
...      docs commits (this file)
d4456b9  fix: make the transceiver work offline, fix concurrency bugs, redesign the UI
7a13e4b  feat(transport): implement Phase A Wi-Fi Direct P2P transport   <- your HEAD
```

`d4456b9` is the one that matters: every code change and bug fix in this document is in
it. 73 files, +4286 / -2392.

### If you have a clone already (recommended)

Add the fork as a second remote and fast-forward. Because it is a descendant of what you
have, this is a clean merge with no conflicts:

```bash
git remote add itantra-fixed https://github.com/Dreamstick9/iTantra-ISRO-SIH26173.git
git fetch itantra-fixed
git log --oneline HEAD..itantra-fixed/main     # see exactly what is incoming
git merge itantra-fixed/main                    # fast-forward
```

To review before taking it:

```bash
git diff HEAD..itantra-fixed/main --stat        # 73 files changed
git diff HEAD..itantra-fixed/main -- app/src/main/java/com/itantra/voice/ui/MainViewModel.kt
```

### If you want a fresh clone

```bash
git clone https://github.com/Dreamstick9/iTantra-ISRO-SIH26173.git
cd iTantra-ISRO-SIH26173
```

### If you only want specific fixes

Every bug in §9 is in the single commit `d4456b9`, so `git cherry-pick` is all-or-nothing.
To take one fix in isolation, read the relevant section here, then pull just that file:

```bash
git checkout itantra-fixed/main -- app/src/main/java/com/itantra/voice/audio/AudioRecorder.kt
```

Be careful doing that: the changes are **interdependent**. Taking `MainViewModel.kt` without
the `pipeline/` package will not compile, and taking `AudioRecorder.kt` alone changes
`stopRecording()` to `suspend` and will break every caller (§3.1). Merging the whole branch
is strongly preferred.

### First thing to do after you have it

```bash
./run-tests.sh          # expect: unit 255 passed, lint clean
```

If that passes, you have a working tree. §7 covers the setup this needs (JDK 17 + 21, SDK
packages, `local.properties`) — **do that first if the command fails**, the failure is
almost always toolchain, not code.

---

## 0.5 "It removed the API and I have no offline pack" — read this

If you had the old cloud-only build working, you had a `sarvam.api.key` in
`local.properties` and it will still work. **The Sarvam API was not removed.**
`SarvamApiClient.kt`, `SarvamApiModels.kt` and `SarvamSpeechPipeline.kt` are all still in
the tree. The pipeline is *pluggable*, not *offline-only*.

Set your key and the cloud engine leads again:

```properties
# local.properties  (git-ignored — create it if it is missing)
sdk.dir=/Users/you/Library/Android/sdk
sarvam.api.key=YOUR_KEY_HERE
itantra.force.offline=false
```

Rebuild. The status line at the top of the screen will read **SARVAM CLOUD**. No offline
language pack is needed on that path.

### The three configurations

| `sarvam.api.key` | `itantra.force.offline` | Engine that leads | Needs an offline pack? | Needs network? |
|---|---|---|---|---|
| set | `false` | **Sarvam Cloud** (on-device is fallback) | No | Yes |
| blank | `false` | **On-device** (cloud is fallback, unusable without a key) | Yes | No |
| anything | `true` | **On-device**, pinned — the Sarvam client is never constructed | Yes | No |

Use `itantra.force.offline=true` for the SIH26173 demo build. The brief forbids
internet-hosted APIs, and with that flag the cloud client is never instantiated, so the
claim is provable rather than asserted.

### Why this needed a fix (and did bite people)

Selection used to prefer the on-device engine whenever it *reported* being available. But
`SpeechRecognizer.isRecognitionAvailable()` returns true on **any** phone that has a
recognition service installed — whether or not an offline language pack exists for the
language you picked. So on-device always won, a configured Sarvam key was never consulted,
and recognition then failed at runtime with *"That language pack is not installed for
offline recognition."*

Android gives no way to query pack presence without attempting recognition, so the engine
is now chosen from **explicit configuration** rather than from an unreliable probe. See
`MainActivity.buildSpeechPipeline()` and `EngineSelectionTest`.

### If you want the offline path anyway

Install a pack on the device — Settings → System → Languages & input → Voice input → tap
the gear next to Google → Offline speech recognition → download your language. An emulator
image without Play Store cannot do this; use a physical device.

---

## 1. The one-paragraph summary


The app could not function out of the box. Every speech call went to Sarvam's cloud API
keyed by `SARVAM_API_KEY`, which is git-ignored and absent, so the auth interceptor
rejected every request before it left the device. That also violated the SIH26173 "fully
offline" mandate. The speech engine now sits behind an interface with **two working
implementations** — a fully offline on-device engine and the original Sarvam cloud client
— and which one leads is chosen from configuration (§0.5). Alongside that, ~15 real bugs
(several concurrency) are fixed, the emergency-alert feature is wired up (it was dead
code), and the UI is rebuilt on a two-colour system.

Tests went 186 → **255 unit + 10 instrumented**, all passing, lint clean.
See [`FEATURES.md`](FEATURES.md) for the full feature list including sender location.

---

## 2. Files: what's new, gone, and moved

### New

```
app/src/main/java/com/itantra/voice/pipeline/
├── SpeechPipeline.kt              the contract everything speaks through
├── OnDeviceSpeechPipeline.kt      offline engine (DEFAULT)
├── SarvamSpeechPipeline.kt        cloud engine (opt-in)
├── OfflineRecognizer.kt           recogniser seam + UnavailableRecognizer
├── FallbackSpeechPipeline.kt      picks which engine serves an utterance
└── recognition/
    └── PlatformSpeechRecognizer.kt  offline STT via platform SpeechRecognizer

app/src/androidTest/java/com/itantra/voice/ui/
└── MainScreenUiTest.kt            7 instrumented Compose tests

app/src/test/java/com/itantra/voice/pipeline/
├── FallbackSpeechPipelineTest.kt
└── SarvamSpeechPipelineTest.kt

app/src/main/java/com/itantra/voice/ui/theme/Shape.kt
docs/ARCHITECTURE_NOTES.md
run-tests.sh
HANDOFF.md                          this file
```

### Deleted

| File | Why |
|---|---|
| `ui/components/Header.kt`, `TelemetryBar.kt`, `TextCards.kt`, `FeedbackBar.kt`, `ErrorBanner.kt`, `LanguageSelector.kt`, `P2pConnectionBar.kt`, `PttButton.kt` | Replaced by the redesigned component set (§6) |
| `ui/package-info.java` | Empty stub |
| `docs/ITANTRA_SECURITY_AUDIT_REPORT.md` | **It described code that does not exist.** It cited `AndroidAudioEngine.kt`, `network_security_config.xml`, CRC32 wire framing and `ITAN`/`IT` magic bytes — none of which are in this repo — and claimed "0 HTTP/HTTPS client calls" while `SarvamApiClient` was the entire pipeline. Replaced by `docs/ARCHITECTURE_NOTES.md`. |
| 19 files in `docs/screenshots/` | Screenshots of a UI deleted in commit `9818f47` ("wipe all previous app code"). They showed tabs and a VAD mode that no longer exist. |

### Unchanged (still safe to rely on)

`audio/WavEncoder.kt`, `data/Language.kt`, `network/SarvamApiModels.kt`,
`transport/TransportEngine.kt`, `transport/TransportMessage.kt`.

---

## 3. Breaking API changes

These will fail to compile against the old codebase. Fix them in this order.

### 3.1 `AudioRecorder.stopRecording()` is now `suspend`

```kotlin
// BEFORE
val pcm = audioRecorder.stopRecording()

// AFTER — must be called from a coroutine
viewModelScope.launch {
    val pcm = audioRecorder.stopRecording()
}
```

**Why:** it now awaits the capture coroutine's drain. Previously `stopRecording()` drained
the hardware buffer on the *caller's* thread while the capture coroutine was still calling
`read()` on the same `AudioRecord`. Two threads reading one device split the tail of every
utterance between them — that is the source of the intermittent truncated audio and the
spurious "No speech detected" that commit `d85ffa5` tried and failed to fix.

`release()` is still non-suspending for teardown paths; it cancels and discards.

### 3.2 `MainViewModel` takes a `SpeechPipeline`, not a `SarvamApiClient`

```kotlin
// BEFORE
MainViewModel(audioRecorder = ..., sarvamApiClient = SarvamApiClient(), ...)

// AFTER
MainViewModel(audioRecorder = ..., speechPipeline = myPipeline, ...)
```

### 3.3 `AudioPlayer` plays bytes, not base64

```kotlin
// BEFORE
player.playBase64Wav(base64, onComplete = {}, onError = {})

// AFTER
player.playWavBytes(wavBytes, isEmergency = false, onComplete = {}, onError = {})
```

`playBase64Wav` still exists and still works; the ViewModel just no longer uses it, because
the offline engine produces raw bytes and base64 was a Sarvam transport detail leaking into
the player.

`MainViewModel.cachedAudioBase64: String?` → **`cachedAudio: ByteArray?`**.

### 3.4 `AudioPlayer` constructor takes an alarm-volume controller

```kotlin
AudioPlayer(cacheDir, playerFactory = { ... }, alarmVolumeController = NoOpAlarmVolumeController)
AudioPlayer(context)  // production: wires SystemAlarmVolumeController for you
```

### 3.5 Removed

- `MainViewModel.onSendTestMessage()` — a debug button that transmitted the literal string
  `"HELLO FROM ITANTRA"`. Gone from the UI and the API.

### 3.6 Changed behaviour that tests assert on

| Thing | Before | After |
|---|---|---|
| Non-emergency audio usage | `USAGE_ASSISTANCE_ACCESSIBILITY` (11) | `USAGE_MEDIA` (1) — accessibility is the screen-reader stream and ducks differently |
| Short-tap message | `"Hold button while speaking"` | `"Hold the button while speaking."` |
| Silence message | `"No speech detected"` | `"No speech detected."` |
| Permission message | `"Microphone permission required. Please allow…"` | `"Microphone permission is required. Allow audio recording in Settings."` |
| PTT without permission | entered `RECORDING`, then failed inside `AudioRecord` | refused up front, stays `IDLE`, shows a notice |
| `PersistentTcpSocketManager.incomingMessages` type | `Flow<TransportMessage>` | `SharedFlow<TransportMessage>` |

---

## 4. The new speech pipeline

### 4.1 The contract

```kotlin
interface SpeechPipeline {
    val mode: PipelineMode              // ON_DEVICE | CLOUD
    val displayName: String             // "On-device" | "Sarvam Cloud"
    val capturesOwnAudio: Boolean       // does this engine own the microphone?
    val amplitude: StateFlow<Float>     // input level, if it owns the mic

    suspend fun prepare()               // resolve engine choice BEFORE capture
    suspend fun isAvailable(): Boolean

    fun beginCapture(source: Language)  // called on PTT press
    fun cancelCapture()                 // called on abandoned press

    suspend fun transcribe(wavData: ByteArray, source: Language): Result<TranscriptionResult>
    suspend fun translate(text: String, source: Language, target: Language): Result<TranslationResult>
    suspend fun synthesize(text: String, target: Language, isEmergency: Boolean): Result<SynthesisResult>

    fun release()
}
```

Nothing throws. Every failure is a `Result.failure` so the FSM has one uniform error path.

### 4.2 The `capturesOwnAudio` trap — read this before you touch capture

The two engines capture audio in **incompatible** ways:

- `OnDeviceSpeechPipeline` uses Android's `SpeechRecognizer`, which **owns the microphone**
  for a session and streams its own audio. `transcribe()` is handed an **empty ByteArray**.
- `SarvamSpeechPipeline` uploads a **recorded WAV** produced by `AudioRecorder`.

Only one may hold the mic. Running `AudioRecord` alongside a recogniser session makes one of
the two receive silence. So `MainViewModel` starts exactly one capture source:

```kotlin
if (speechPipeline?.capturesOwnAudio == true) {
    speechPipeline?.beginCapture(current.sourceLanguage)   // recogniser owns the mic
} else {
    audioRecorder.startRecording(viewModelScope)           // we own the mic
}
```

`capturesOwnAudio` must be answerable **synchronously** the instant PTT is pressed, but
availability checks suspend. So `FallbackSpeechPipeline` resolves the engine once in
`prepare()` and caches it. **Do not make this per-call** — the engine could then change
between the press and the release, leaving the utterance captured by nobody.

`MainViewModel.setSpeechPipeline()` calls `prepare()` for you. Call `prepare()` again if you
want to re-resolve (e.g. the user just installed a voice pack).

### 4.3 Writing your own engine

Implement `SpeechPipeline` and hand it to the ViewModel. For a **bundled neural model**
(Vosk, sherpa-onnx) that consumes a WAV buffer, implement `OfflineRecognizer` instead and
drop it into `OnDeviceSpeechPipeline` — you get the TTS half and the wiring for free:

```kotlin
class VoskRecognizer : OfflineRecognizer {
    override val ownsMicrophone = false      // we take a WAV, so AudioRecorder runs
    override suspend fun isAvailable() = modelLoaded
    override suspend fun transcribe(wavData: ByteArray, source: Language): Result<String> {
        return Result.success(vosk.decode(wavData))
    }
    override fun release() { vosk.close() }
}

OnDeviceSpeechPipeline(context, cacheDir, recognizer = VoskRecognizer())
```

That is the intended path for closing the offline-translation gap in §8.

---

## 5. Emergency alert mode (was dead code)

`AudioPlayer` already had an `isEmergency` parameter. **Nothing ever set it.** The
SIH26173 requirement that alerts "be announced at highest volume non-interruptible" was
unimplemented despite being listed as feature F18.

Now:

1. `viewModel.onToggleEmergencyMode()` arms it. `uiState.isEmergencyMode` reflects it.
2. Armed transmissions go out as `TransportMessageType.ALERT` with `priority = 1`.
3. On the receiver, `handleIncomingRemoteMessage` sees `ALERT` and passes
   `isEmergency = true` down the chain.
4. `AudioPlayer` routes that to `USAGE_ALARM` **and** raises `STREAM_ALARM` to maximum via
   `SystemAlarmVolumeController`, so a handset with media volume down still announces it.

Test it without two phones:

```kotlin
transport.emit(TransportMessage(
    type = TransportMessageType.ALERT,
    sourceLanguage = "hi-IN", targetLanguage = "en-IN",
    text = "CYCLONE WARNING", priority = 1
))
// assert fakePipeline.lastSynthesisWasEmergency == true
```

See `MainViewModelTest.testIncomingAlertIsAnnouncedAsEmergency`.

---

## 6. UI

### Component map (old → new)

| Old | New |
|---|---|
| `Header.kt` + `P2pConnectionBar.kt` | `StatusBar.kt` — one line: link state left, active engine right |
| `LanguageSelector.kt` | `LanguageRow.kt` — plain text, not boxed dropdowns |
| `TextCards.kt` | `TranscriptPanel.kt` — hairline-separated, fills flexible space |
| `PttButton.kt` | `TalkButton.kt` |
| `FeedbackBar.kt` | `BottomControls.kt` — also hosts the emergency toggle |
| `ErrorBanner.kt` | `NoticeBar.kt` |
| `TelemetryBar.kt` | `LatencyStrip.kt` |
| (none) | `PairingSheet.kt` — bottom sheet, replaces the peer `AlertDialog` |

### The colour rule

Two accents. They are **state language, not decoration**:

- **Red (`Signal`)** — transmitting, armed, emergency alert
- **Green (`Link`)** — linked to a peer, received message

Everything else is a neutral ramp. Dynamic colour is deliberately **off**: on a
disaster-response tool the meaning of red and green must not change with the user's
wallpaper.

If you add a colour, you are almost certainly doing it wrong — check whether it is really a
third state or just decoration.

> **Gotcha:** Material 3 falls back to its baseline purple-tinted tonal palette for any
> colour role you leave unset. Menus, sheets and dialogs use `surfaceContainer*`, which is
> why the language dropdown rendered lilac until those roles were pinned in `Theme.kt`.
> **If you add a Material component and it looks purple, that's the cause.**

### Test tags for UI tests

```kotlin
TALK_BUTTON_TAG       // "talk_button"
EMERGENCY_TOGGLE_TAG  // "emergency_toggle"
NOTICE_BAR_TAG        // "notice_bar"
PAIRING_SHEET_TAG     // "pairing_sheet"
```

---

## 7. Running and testing

### Setup (one time)

```bash
brew install --cask android-commandlinetools
brew install openjdk@17 openjdk@21
```

Gradle runs on JDK 21 but the project compiles against a **JDK 17 toolchain**. Point Gradle
at it once, in `~/.gradle/gradle.properties`:

```properties
org.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

Without this the build tries to auto-download JDK 17 from foojay and will fail if two
Gradle processes race for the same lock.

SDK packages:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" "platforms;android-36" "platforms;android-35" \
  "build-tools;36.0.0" "emulator" "cmdline-tools;latest" \
  "system-images;android-35;google_apis;arm64-v8a"
```

> `avdmanager` from the Homebrew cask cannot see the SDK's system images. Install
> `cmdline-tools;latest` **into** `$ANDROID_HOME` (as above) and use
> `$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager`.

`local.properties` (git-ignored — create it):

```properties
sdk.dir=/Users/you/Library/Android/sdk

# Leave blank to run fully offline. The app works with no key.
sarvam.api.key=
```

### The test script

```bash
./run-tests.sh            # unit tests + lint + APK
./run-tests.sh --device   # also boots a headless emulator and runs the UI tests
```

`--device` boots the AVD itself if none is attached. No human interaction anywhere.

Expected:

```
  unit            255 tests  0 failures  0 errors   [PASS]
  instrumented     10 tests  0 failures  0 errors   [PASS]
```

Create the AVD once:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
  -n itantra_test -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_6
```

### Manual smoke test on the emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.itantra.voice android.permission.RECORD_AUDIO
adb shell pm grant com.itantra.voice android.permission.NEARBY_WIFI_DEVICES
adb shell am start -n com.itantra.voice/.MainActivity

# hold the talk button for 2 s (button centre on a 1080x2400 screen)
adb shell input swipe 540 1898 540 1898 2000
adb exec-out screencap -p > /tmp/shot.png
```

> **Expected on an emulator:** the button turns solid red, the system mic indicator
> appears, and on release you get *"That language pack is not installed for offline
> recognition."* **This is correct.** A `google_apis` image has no Play Store and cannot
> download offline voice packs. End-to-end recognition needs a physical device with a pack
> installed (Settings → System → Languages & input → Voice input → offline speech
> recognition). Everything else is covered by the test suites.

### Writing tests

The seams to inject at:

| Seam | Interface | Used by |
|---|---|---|
| Position | `LocationProvider` | `MainViewModel.setLocationProvider(...)` |
| Microphone | `NativeAudioRecord` | `AudioRecorder(recordProvider = { ... })` |
| Speaker | `NativeMediaPlayer` | `AudioPlayer(cacheDir, playerFactory = { ... })` |
| Speech engine | `SpeechPipeline` | `MainViewModel(speechPipeline = ...)` |
| Translation | `Translator` | `ElevenLabsSpeechPipeline(translator = ...)` |
| Transport | `TransportEngine` | `MainViewModel(transportEngine = ...)` |
| Alarm volume | `AlarmVolumeController` | `AudioPlayer(alarmVolumeController = ...)` |
| Clock | `() -> Long` | `MainViewModel(timeProvider = { fixedTime })` |

No test needs a mic, a speaker, a network or a second handset. Copy `FakeSpeechPipeline`
from `MainViewModelTest` as your starting point.

**Two traps that will bite you:**

1. **The emulator runs `-no-audio`, so a real `AudioRecord` cannot be constructed.**
   Instrumented tests must inject `FakeRecord` (see `MainScreenUiTest`) or they fail with
   `PttState.ERROR`.
2. **`incomingMessages` has `replay = 0`.** A message emitted before your collector attaches
   is *gone*. In unit tests call `advanceUntilIdle()` after creating the ViewModel and
   before emitting. In real coroutines use `onSubscription { }` to wait for attachment (see
   `PersistentTcpSocketTest`).

---

## 8. Known gaps — do not assume these work

| Gap | Detail |
|---|---|
| **Offline translation** | `OnDeviceSpeechPipeline.translate()` is a **pass-through** — it returns the input unchanged. No open-source on-device Indic translation model is bundled. Cross-language translation only works on the Sarvam path. This is the main remaining gap against the brief; §4.3 is the way in. |
| **VAD** | The brief wants STT to activate "after detecting pauses and stoppages". Capture is push-to-talk only. There is no voice-activity segmentation. |
| **Bluetooth RFCOMM** | `TransportEngine` and `TransportType.BLUETOOTH` are shaped for it; only Wi-Fi Direct is implemented. |
| **Wi-Fi Direct on two real phones** | The state machine, framing and TCP link are unit-tested over loopback, but a **two-handset pairing has not been run** — an emulator cannot do Wi-Fi P2P. Budget time for this. |
| **`targetSdk` 35 vs `compileSdk` 36** | Left as-is deliberately: bumping targetSdk changes runtime behaviour (edge-to-edge enforcement and more) and was not worth shipping unvetted. Lint warns; it is not an error. |

---

## 9. Bug fixes worth knowing about

If you are cherry-picking rather than merging, these are the ones that matter.

| Bug | Symptom it caused |
|---|---|
| `stopRecording()` drained on the caller's thread while the capture coroutine still read | Truncated audio, intermittent "No speech detected" |
| Post-loop drain ignored `MAX_RECORDING_BYTES` | Captures blew past the 30 s cap |
| `incomingMessages` had `replay = 1` | Receiver re-spoke the previous message on every reconnect and every ViewModel rebind |
| `reuseAddress` set *after* `ServerSocket(port)` had already bound | Reconnect failed with "Address already in use" during `TIME_WAIT` |
| `accept()` called once | Group owner went deaf after the first peer dropped; needed an app restart |
| Peer teardown cancelled the accept job | Same as above — it cancelled the coroutine waiting to re-accept |
| `setTransportEngine` had no re-entry guard | Four new collectors per call; every received message spoken twice |
| PTT not permission-gated | Press without `RECORD_AUDIO` could only fail inside `AudioRecord` |
| Link latency computed as `now - message.timestamp` | Measured clock skew between two handsets, not transit time; could go negative |
| `registerReceiver` had no export flag | Android 14+ compliance |
| Socket failure while `CONNECTING` did not reset state | UI pinned on "Connecting…" with no way back |
| Sticky `CONNECTION_CHANGED` replayed on registration | Cancelled connection attempts that had just started |
| OkHttp `Response` never closed | Leaked a socket on every failed call |
| `HttpLoggingInterceptor.Level.BODY` in release | User speech and base64 audio written to logcat |
| `SimpleDateFormat` used outside the lock | Corrupted timestamps on concurrent feedback writes |
| `MessageFramer` accepted zero-length frames | Misleading JSON parse error instead of a clear one |
| `FINE` location without `COARSE` | Lint error; on Android 12+ the permission dialog never appears |
| Missing `<queries>` for TTS / RecognitionService | Android 11+ package visibility hid the speech engines; offline pipeline reported "no engine installed" |
| `MainScreen` imported `verticalScroll` but never applied it | Content clipped on small screens |

---

## 10. If you only read one thing

Do not run `AudioRecorder` and a `SpeechRecognizer` session at the same time. Check
`SpeechPipeline.capturesOwnAudio` and start exactly one capture source. Everything else in
here is recoverable; that one produces silent audio that looks like a model problem and
will cost you a day.
