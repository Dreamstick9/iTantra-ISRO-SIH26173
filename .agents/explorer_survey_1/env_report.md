# Android Environment & Infrastructure Audit Report — iTantra

**Date**: 2026-09-07  
**Author**: `explorer_survey_1`  
**Workspace**: `/Users/spirit/Downloads/spiritsih`  
**Target Application**: iTantra — Native Android Multilingual Voice Communication Prototype  

---

## 1. Executive Summary

A comprehensive environment, SDK, build toolchain, and emulator audit was conducted on macOS Darwin arm64.
- **Android Project Status**: The root directory `/Users/spirit/Downloads/spiritsih` contains research, documentation, and agent folders, but **no Android project scaffold yet**. It must be initialized.
- **Crucial CLI Constraint Discovered**: `android create` strictly fails when pointed at a non-empty directory. An initialization strategy (scaffold into a temporary directory and copy over) has been developed and verified.
- **Java / JDK**: Amazon Corretto JDK 21.0.12 LTS (`/Users/spirit/.jdk/amazon-corretto-21.jdk/Contents/Home`) is fully configured and active.
- **Android SDK & Tools**:
  - `ANDROID_HOME` / `ANDROID_SDK_ROOT`: `/Users/spirit/Library/Android/sdk`
  - Platforms installed: `android-34`, `android-35`, `android-36`
  - Build-tools installed: `34.0.0`, `35.0.0`, `36.0.0`
  - `adb`: Version 1.0.41 (37.0.1-15733141) at `/Users/spirit/Library/Android/sdk/platform-tools/adb`
  - Android CLI: Version 1.0.15985488 at `/Users/spirit/.local/bin/android`
- **Emulator Status**:
  - AVD `medium_phone` (`arm64-v8a`, Android 36/API 36 system image with Google Play) was identified and **successfully launched**.
  - Currently running as device `emulator-5554`.
  - Verified features: `android.hardware.microphone=true`, `android.hardware.audio.output=true`, validated active internet connectivity (Wi-Fi + Cellular NAT).
  - CLI capabilities verified: `android screen capture` and `android layout -p` successfully interact with the running emulator.
- **Build Performance Benchmark**:
  - Verified with test scaffold: Gradle 9.1.0 + AGP 9.0.1 + Kotlin 2.3.20 + Compose BOM compiles in **12s** (`assembleDebug`) and executes unit tests in **2s** (`test`).

---

## 2. Workspace State Audit

```
/Users/spirit/Downloads/spiritsih/
├── .agents/                      # Multi-agent coordination metadata
├── .git/                         # Git repository
├── .gitignore                    # Initial gitignore
├── 01_RESEARCH_FOR_AGENT.md      # SIH deep research knowledge base
├── 02_HUMAN_BRIEF.md             # Human overview
├── 03_SUPPLEMENTARY.md           # Audio pipeline supplementary notes
├── ORIGINAL_REQUEST.md           # Authoritative user requirements & Sarvam AI specs
├── PS_SIH26173_OFFICIAL.md       # ISRO problem statement text
├── README.md                     # Repository README
├── SIH26173_iTantra_Research_Package/
└── docs/                         # Architecture documentation
```

### Key Findings
1. No Android build configuration files (`build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `app/`) exist currently in the root.
2. The directory is non-empty. Running `android create ... --output=.` will throw:
   `ERROR: Cannot create template: Directory '.' is not empty`
3. Therefore, project bootstrapping must be executed by creating the scaffold in `/tmp/itantra_scaffold` and synchronizing the files into the repository root, followed by configuring the target dependencies.

---

## 3. Host Environment & Tooling Audit

| Component | Path / Value | Status |
| :--- | :--- | :--- |
| **Operating System** | macOS Darwin 25.6.0 (arm64, Apple Silicon) | Verified |
| **Java JDK** | Amazon Corretto 21.0.12.1+9-LTS (`/Users/spirit/.jdk/amazon-corretto-21.jdk/Contents/Home`) | Verified (`JAVA_HOME` set) |
| **Android SDK Root** | `/Users/spirit/Library/Android/sdk` | Verified (`ANDROID_HOME`, `ANDROID_SDK_ROOT` set) |
| **SDK Platforms** | `android-34`, `android-35`, `android-36` | Installed |
| **Build-Tools** | `34.0.0`, `35.0.0`, `36.0.0` | Installed |
| **Platform-Tools (adb)** | `/Users/spirit/Library/Android/sdk/platform-tools/adb` (v1.0.41) | Verified in PATH |
| **Android CLI** | `/Users/spirit/.local/bin/android` (v1.0.15985488) | Verified in PATH |
| **Emulator Binary** | `/Users/spirit/Library/Android/sdk/emulator/emulator` | Verified (invocable via `android emulator` or direct path) |
| **Gradle Cache** | `~/.gradle/wrapper/dists/gradle-9.1.0-bin` | Cached locally |
| **Foojay Toolchain** | Supported via Gradle convention plugin | Compatible |

---

## 4. Emulator & Device Environment Audit

### 4.1 AVD Configuration (`medium_phone`)
Located at `~/.android/avd/medium_phone.avd/config.ini`:
- **Name**: `medium_phone`
- **ABI**: `arm64-v8a`
- **Target OS**: Android 36 (`google_apis_playstore/arm64-v8a`)
- **RAM**: 2048 MB, Heap: 228 MB, Data partition: 6 GB
- **Display**: 1080 x 2400 (420 dpi)
- **Audio Inputs/Outputs**:
  - `hw.audioInput = yes`
  - `hw.audioOutput = yes`

### 4.2 Live Emulator State
- **Device ID**: `emulator-5554`
- **State**: `device` (fully booted and operational)
- **OS Properties**:
  - `ro.build.version.sdk`: `36`
  - `ro.product.cpu.abi`: `arm64-v8a`
- **Hardware Features**:
  - `feature:android.hardware.microphone`: Present
  - `feature:android.hardware.audio.output`: Present
- **Networking**:
  - `dumpsys connectivity`: `WIFI` (`AndroidWifi`) and `MOBILE` validated with `INTERNET` and `NOT_RESTRICTED`.
  - Host resolution & connectivity to `https://api.sarvam.ai`: Tested and functional (HTTP/2 404 response on base URL, server `uvicorn`).
- **Inspection Tools**:
  - `android screen capture -o=screenshot.png`: Operational (generates 1080x2400 PNG).
  - `android layout -p`: Operational (returns full accessibility & view hierarchy JSON).

---

## 5. Recommended Project Architecture & Build Setup

### 5.1 Gradle & Plugin Versions
- **DSL**: Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`)
- **Gradle Version**: `9.1.0` (using standard wrapper)
- **Android Gradle Plugin (AGP)**: `9.0.1`
- **Kotlin**: `2.3.20`
- **Compose Compiler Plugin**: `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.0+ native plugin)
- **Kotlin Serialization**: `org.jetbrains.kotlin.plugin.serialization` (`2.3.20`)
- **Compile SDK**: `35` (or `36`)
- **Target SDK**: `35`
- **Min SDK**: `26` (Android 8.0 Oreo, covers 99.5%+ active devices; provides clean `AudioRecord`, `AudioTrack`, and standard Java 8+ time APIs without desugaring overhead)

### 5.2 Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
androidGradlePlugin = "9.0.1"
androidxCore = "1.18.0"
androidxLifecycle = "2.10.0"
androidxActivity = "1.13.0"
androidxComposeBom = "2026.03.01"
androidxTest = "1.7.0"
androidxTestExt = "1.3.0"
androidxTestRunner = "1.7.0"
androidxTestEspresso = "3.7.0"
coroutines = "1.10.2"
junit = "4.13.2"
kotlin = "2.3.20"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidxCore" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidxActivity" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "androidxComposeBom" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidxLifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidxLifecycle" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "androidxLifecycle" }

kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }

junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "androidxTestEspresso" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }

[plugins]
android-application = { id = "com.android.application", version.ref = "androidGradlePlugin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### 5.3 Module Structure & Package Layout

```
app/src/main/
├── AndroidManifest.xml
└── java/com/itantra/voice/
    ├── MainActivity.kt               # Entry Activity, permission request, Compose container
    ├── data/
    │   ├── Language.kt               # 10 official languages enum (en-IN, hi-IN, mr-IN, etc.)
    │   ├── FeedbackLogger.kt         # Local private JSON storage (feedback_logs.json)
    │   └── Models.kt                 # State models, API DTOs, Feedback data classes
    ├── audio/
    │   ├── AudioRecorder.kt          # Native AudioRecord (16 kHz, 16-bit Mono PCM)
    │   ├── WavEncoder.kt             # PCM byte array -> 44-byte RIFF/WAV encoder
    │   └── AudioPlayer.kt            # Base64 WAV playback via AudioTrack / MediaPlayer
    ├── network/
    │   └── SarvamApiClient.kt        # Saaras v3 STT, Translate / Mayura, Bulbul v3 TTS
    └── ui/
        ├── MainViewModel.kt          # Single reactive state machine (IDLE..ERROR)
        ├── MainScreen.kt             # Header, Selectors, PTT Button, Text Cards, Feedback
        └── theme/
            ├── Color.kt
            ├── Theme.kt
            └── Type.kt
```

### 5.4 API Key & Security Handling
Per Requirement R4, API keys must **never** be hardcoded or committed:
1. In `local.properties`:
   ```properties
   sdk.dir=/Users/spirit/Library/Android/sdk
   sarvam.api.key=YOUR_API_KEY_HERE
   ```
2. In `app/build.gradle.kts`:
   ```kotlin
   val localProperties = Properties().apply {
       val file = rootProject.file("local.properties")
       if (file.exists()) file.inputStream().use { load(it) }
   }
   val sarvamApiKey = localProperties.getProperty("sarvam.api.key")
       ?: System.getenv("SARVAM_API_KEY")
       ?: ""
   
   defaultConfig {
       buildConfigField("String", "SARVAM_API_KEY", "\"$sarvamApiKey\"")
   }
   buildFeatures {
       buildConfig = true
       compose = true
   }
   ```
3. In `.gitignore`: ensure `local.properties` is strictly ignored.
4. When `sarvam.api.key` is empty, the app handles it cleanly with a user-friendly configuration error banner rather than crashing.

---

## 6. Project Initialization Execution Plan for Implementers

Since `/Users/spirit/Downloads/spiritsih` has existing repo files:
1. **Bootstrap Scaffold**:
   ```bash
   rm -rf /tmp/it_scaffold
   android create empty-activity --name="iTantra" --output=/tmp/it_scaffold
   cp -R /tmp/it_scaffold/gradle .
   cp /tmp/it_scaffold/gradlew .
   cp /tmp/it_scaffold/gradlew.bat .
   cp /tmp/it_scaffold/gradle.properties .
   cp /tmp/it_scaffold/local.properties .
   cp /tmp/it_scaffold/settings.gradle.kts .
   cp /tmp/it_scaffold/build.gradle.kts .
   cp -R /tmp/it_scaffold/app .
   rm -rf /tmp/it_scaffold
   ```
2. **Apply Version Catalog & Build Configuration**:
   Update `gradle/libs.versions.toml`, `settings.gradle.kts`, and `app/build.gradle.kts` with the OkHttp, Serialization, and Compose BOM settings detailed above.
3. **Verify Build**:
   ```bash
   ./gradlew assembleDebug
   ```
4. **Deploy to Running Emulator**:
   ```bash
   ./gradlew installDebug
   adb shell am start -n com.itantra.voice/.MainActivity
   ```
5. **Inspect & Verify**:
   - UI tree inspection: `android layout -p`
   - Visual verification: `android screen capture -o=screenshot.png`
   - Automated tests: `./gradlew test` and `./gradlew connectedAndroidTest`

---

## 7. Verification Summary

- [x] Android SDK, Build-tools, Platforms, and Platform-tools confirmed functional.
- [x] JDK 21 Amazon Corretto verified and linked via `JAVA_HOME`.
- [x] Gradle 9.1.0 cached and tested with AGP 9.0.1 (12s compile time).
- [x] Emulator `medium_phone` running on `emulator-5554` with hardware audio input/output and validated network.
- [x] `android screen capture` and `android layout` verified against the running emulator.
- [x] Safe project initialization workflow verified.
