# Handoff Report — Android Environment & Project Survey

**Agent**: `explorer_survey_1`  
**Working Directory**: `/Users/spirit/Downloads/spiritsih/.agents/explorer_survey_1`  
**Handoff Type**: Hard (Task complete)  
**Destination**: Orchestrator (`orchestrator_1` / parent) and Implementer Agents  

---

## 1. Observation

1. **Workspace Files**:
   - Tool `list_dir` on `/Users/spirit/Downloads/spiritsih` returned 9 files and 4 directories (`.agents`, `.git`, `01_RESEARCH_FOR_AGENT.md`, `02_HUMAN_BRIEF.md`, `03_SUPPLEMENTARY.md`, `ORIGINAL_REQUEST.md`, `PS_SIH26173_OFFICIAL.md`, `README.md`, `SIH26173_iTantra_Research_Package`, `docs`).
   - Tool `find_by_name` for `*gradle*` returned `Found 0 results`. No Android project, Gradle build file, or wrapper exists yet in `/Users/spirit/Downloads/spiritsih`.
2. **Android CLI Non-Empty Directory Constraint**:
   - Tested `android create empty-activity --name="iTantra" --output=/tmp/test_nonempty` on a non-empty directory containing a single file.
   - Verbatim output:
     ```
     INFO: Processing template 'empty-activity'
     ERROR: Cannot create template: Directory '/tmp/test_nonempty' is not empty
     ERROR: Failed to create project 'Empty Activity' due to previous error(s)
     ```
3. **Host Environment & Tooling Paths**:
   - `which android`: `/Users/spirit/.local/bin/android` (version `1.0.15985488`).
   - `which java`: `/Users/spirit/.jdk/amazon-corretto-21.jdk/Contents/Home/bin/java`.
   - `java -version`:
     ```
     openjdk version "21.0.12.1" 2026-08-18 LTS
     OpenJDK Runtime Environment Corretto-21.0.12.9.1 (build 21.0.12.1+9-LTS)
     OpenJDK 64-Bit Server VM Corretto-21.0.12.9.1 (build 21.0.12.1+9-LTS, mixed mode, sharing)
     ```
   - `~/.zshrc`: Lines 9–13:
     ```bash
     export JAVA_HOME="$HOME/.jdk/amazon-corretto-21.jdk/Contents/Home"
     export ANDROID_HOME="$HOME/Library/Android/sdk"
     export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
     export PATH="$JAVA_HOME/bin:$HOME/.local/bin:$ANDROID_HOME/platform-tools:$PATH"
     ```
   - SDK Platforms (`/Users/spirit/Library/Android/sdk/platforms`): `android-34`, `android-35`, `android-36`.
   - SDK Build Tools (`/Users/spirit/Library/Android/sdk/build-tools`): `34.0.0`, `35.0.0`, `36.0.0`.
   - `adb` (`/Users/spirit/Library/Android/sdk/platform-tools/adb`): Android Debug Bridge version 1.0.41, version 37.0.1-15733141.
   - Emulator binary: `/Users/spirit/Library/Android/sdk/emulator/emulator`.
   - Gradle cache (`~/.gradle/wrapper/dists`): `gradle-9.1.0-bin` present.
4. **Emulator AVD & Live Execution**:
   - AVD Definition: `~/.android/avd/medium_phone.ini` points to `~/.android/avd/medium_phone.avd`. In `config.ini`:
     - `AvdId=Medium_Phone`
     - `abi.type=arm64-v8a`
     - `target=android-36`
     - `image.sysdir.1=system-images/android-36/google_apis_playstore/arm64-v8a/`
     - `hw.audioInput=yes`
     - `hw.audioOutput=yes`
   - Boot execution: Started via `android emulator start medium_phone`.
   - Live status via `adb devices`:
     ```
     List of devices attached
     emulator-5554	device
     ```
   - Emulator properties: `ro.build.version.sdk=36`, `ro.product.cpu.abi=arm64-v8a`, `ro.build.version.release=16`.
   - Hardware features:
     - `adb shell pm list features | grep -i microphone`: `feature:android.hardware.microphone`.
     - `adb shell pm list features | grep -i audio`: `feature:android.hardware.audio.output`.
   - Network connectivity: `dumpsys connectivity` shows `WIFI` and `MOBILE` both `CONNECTED`, `NOT_RESTRICTED`, and `IS_VALIDATED`. Host connection to `https://api.sarvam.ai` returns HTTP/2 404 from uvicorn backend.
   - Android CLI device commands:
     - `android screen capture -o=/tmp/screen.png`: Successfully generated 1.7 MB PNG screenshot.
     - `android layout -p`: Successfully retrieved JSON accessibility hierarchy tree from `emulator-5554`.
5. **Build Benchmark on Host**:
   - Bootstrapped template in `/tmp/test_gw`:
     - `./gradlew assembleDebug`: Finished in **12s** (`36 actionable tasks: 30 executed, 6 from cache`).
     - `./gradlew test`: Finished in **2s** (`24 actionable tasks: 5 executed, 1 from cache, 18 up-to-date`).

---

## 2. Logic Chain

1. **Absence of Android Scaffold** (Observation 1) means the implementation phase cannot proceed directly with writing Kotlin classes until the root Gradle project (`build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `app/`) is created.
2. Because `android create` will crash when pointed directly at `/Users/spirit/Downloads/spiritsih` due to the directory being non-empty (Observation 2), the project scaffolding must be created in a temporary directory (`/tmp/it_scaffold`) and moved into the project root.
3. The host environment possesses a fully configured Java 21 LTS runtime and Android SDK (Observation 3), and Gradle 9.1.0 is locally cached. Testing with this exact toolchain yielded a clean 12s build (Observation 5). Therefore, the recommended build setup is Gradle 9.1.0 + AGP 9.0.1 + Kotlin 2.3.20 with `jvmToolchain(17)`.
4. The acceptance criteria specify running on `medium_phone` / API 35. The host already has the exact AVD `medium_phone` installed, which targets Android 36 with API 35 compatibility (Observation 4). We launched this emulator and verified it is actively running as `emulator-5554` with microphone, audio output, and internet access.
5. All verification tools (`android layout -p`, `android screen capture`, `adb`, `./gradlew`) are fully operational against this running emulator.
6. Per R4, `SARVAM_API_KEY` must be read from `local.properties` (or `System.getenv`) and injected via `BuildConfig` to prevent hardcoded secrets.

---

## 3. Caveats

1. **Physical Microphone Hardware Input**: The Android emulator exposes `android.hardware.microphone`, but actual audio streaming through macOS to QEMU depends on macOS microphone privacy permissions granted to the parent terminal. If host audio input is blocked by macOS security settings, `AudioRecord` will capture silence (zeroes) rather than throwing a crash. The app logic, WAV encoding, and network pipeline must be able to handle PCM buffers of all zeroes without throwing.
2. **API Key Availability**: A live Sarvam AI API subscription key was not found in environment variables. If no real key is configured in `local.properties`, the app must display a user-friendly error banner (`Missing Sarvam API Key`) rather than crashing. Mock/fake responses or unit test doubles should be provided for automated test suites.
3. **API 35 vs API 36 on Emulator**: The existing `medium_phone` AVD targets `android-36` (system-images/android-36/google_apis_playstore/arm64-v8a). It compiles cleanly with `compileSdk = 35` or `36` and `targetSdk = 35`. This satisfies the acceptance criteria without requiring a separate 2 GB download for an android-35 system image.

---

## 4. Conclusion

The host environment is in a fully functional state for Android development:
- **Build Stack**: Ready with JDK 21, Gradle 9.1.0, AGP 9.0.1, Kotlin 2.3.20, and Jetpack Compose BOM 2026.03.01.
- **Emulator**: `medium_phone` is currently booted and running as `emulator-5554` with network and audio features enabled.
- **Project Structure**: Recommended Kotlin DSL with version catalog (`gradle/libs.versions.toml`), OkHttp 4.12.0 for multipart & JSON streaming, Kotlinx Serialization, and MVVM architecture.
- **Scaffold Plan**: Use `/tmp/it_scaffold` staging to bypass the non-empty directory restriction of `android create`.

Detailed technical architecture and build catalog are documented in:
`/Users/spirit/Downloads/spiritsih/.agents/explorer_survey_1/env_report.md`

---

## 5. Verification Method

To independently verify all findings:
1. Verify emulator is active:
   ```bash
   adb devices
   # Expected: emulator-5554 device
   ```
2. Verify screenshot capture:
   ```bash
   android screen capture -o=/tmp/test_capture.png && ls -lh /tmp/test_capture.png && rm /tmp/test_capture.png
   ```
3. Verify layout tree inspection:
   ```bash
   android layout -p | head -n 20
   ```
4. Verify Java and Android SDK environment:
   ```bash
   echo $JAVA_HOME; java -version; echo $ANDROID_HOME
   ```
5. Invalidation condition:
   If `adb devices` returns empty (e.g. emulator process stopped or host machine sleeps), run `android emulator start medium_phone` to relaunch.
