# Changes Report — Milestone M0: Scaffolding & Core Architecture Setup

**Agent**: `worker_m0_1`  
**Date**: 2026-09-07  
**Workspace**: `/Users/spirit/Downloads/spiritsih`  

---

## 1. Summary of Changes

Milestone M0 establishes the Android native project scaffolding, Gradle Kotlin DSL configuration, version catalog, security isolation for API keys, initial package hierarchy, and automated verification on the live emulator.

### Modified & Created Files
1. **Gradle Build Files**:
   - `gradle/libs.versions.toml`: Centralized version catalog with AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01, OkHttp 4.12.0, Kotlinx Serialization 1.7.3, Coroutines 1.10.2, and AndroidX testing suites.
   - `settings.gradle.kts`: Configured plugin repositories and project name `iTantra`.
   - `build.gradle.kts` (root): Configured top-level plugin aliases for application, Compose compiler, and Kotlin serialization.
   - `app/build.gradle.kts`: Configured namespace and applicationId `com.itantra.voice`, `compileSdk = 36`, `minSdk = 26`, `targetSdk = 35`, Java 17 toolchain, Compose and BuildConfig features enabled, and `BuildConfig.SARVAM_API_KEY` dynamic resolution from `local.properties` (with environment variable fallback).
   - `gradle.properties`: Optimized JVM options and AndroidX settings.
   - `local.properties`: Configured SDK path (`sdk.dir=/Users/spirit/Library/Android/sdk`) and placeholder key `sarvam.api.key=YOUR_API_KEY_HERE`. Verified ignored by `.gitignore`.

2. **Application Manifest & Resources**:
   - `app/src/main/AndroidManifest.xml`: Configured application metadata and required permissions (`RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, `MODIFY_AUDIO_SETTINGS`) with exported `MainActivity`.
   - `app/src/main/res/`: Launcher icons, themes, and string definitions.

3. **Package Structure (`com.itantra.voice`)**:
   - `app/src/main/java/com/itantra/voice/MainActivity.kt`: Entry activity hosting Compose `ITantraTheme` and M0 scaffolding verification screen displaying app status, SDK levels, and API key presence.
   - `app/src/main/java/com/itantra/voice/ui/theme/Color.kt`: Color palette including Indic branding accents (`IndicBlue`, `IndicOrange`, `IndicGreen`).
   - `app/src/main/java/com/itantra/voice/ui/theme/Theme.kt`: Material 3 theme implementation with dynamic color support and dark/light palettes.
   - `app/src/main/java/com/itantra/voice/ui/theme/Type.kt`: Material 3 typography definitions.
   - `app/src/main/java/com/itantra/voice/data/Language.kt`: 10 official Indic languages enum with English/native display names, BCP-47 codes, default source/target, and case-insensitive lookup.
   - Subpackages created:
     - `com.itantra.voice.audio` (documented via `package-info.java`)
     - `com.itantra.voice.network` (documented via `package-info.java`)
     - `com.itantra.voice.data` (contains `Language.kt`)
     - `com.itantra.voice.ui` (contains `theme/` and documented via `package-info.java`)

4. **Unit Tests**:
   - `app/src/test/java/com/itantra/voice/data/LanguageTest.kt`: Validates 10 Indic languages, BCP-47 mapping contract (`hi-IN`, `en-IN`, etc.), default source/target languages, and lookup helpers.
   - `app/src/test/java/com/itantra/voice/BuildConfigTest.kt`: Validates application ID and BuildConfig resolution.

---

## 2. Execution & Build Verification

### 2.1 Assemble Debug
Command:
```bash
./gradlew assembleDebug
```
Output:
```
BUILD SUCCESSFUL in 9s
38 actionable tasks: 10 executed, 28 up-to-date
```
Artifact Generated:
- `app/build/outputs/apk/debug/app-debug.apk` (19 MB)

### 2.2 Unit Test Execution
Command:
```bash
./gradlew test
```
Output:
```
BUILD SUCCESSFUL in 3s
26 actionable tasks: 5 executed, 1 from cache, 20 up-to-date
```
Test Results:
- `com.itantra.voice.BuildConfigTest`: 2 tests passed (100%)
- `com.itantra.voice.data.LanguageTest`: 5 tests passed (100%)

### 2.3 Deployment to Live Emulator
Command:
```bash
./gradlew installDebug
adb shell am start -n com.itantra.voice/.MainActivity
```
Output:
```
Installing APK 'app-debug.apk' on 'medium_phone(AVD) - 16' for :app:debug
Installed on 1 device.
BUILD SUCCESSFUL in 2s
Starting: Intent { cmp=com.itantra.voice/.MainActivity }
```

### 2.4 Device Verification & Layout Inspection
Command:
```bash
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"
android layout -p
```
Output:
```json
[
  {
    "text": "iTantra",
    "center": "[540,952]",
    "key": 3506402
  },
  {
    "text": "Multilingual Voice Communication Prototype",
    "center": "[540,1036]",
    "key": 3506402
  },
  {
    "text": "Milestone M0: Scaffolding Ready",
    "center": "[540,1220]",
    "key": 3506402
  },
  {
    "text": "Package: com.itantra.voice\nMin SDK: 26 | Target SDK: 35",
    "center": "[541,1321]",
    "key": 3506402
  },
  {
    "text": "API Key: Pending / Default",
    "center": "[540,1416]",
    "key": 3506402
  }
]
```

### 2.5 Screenshot Capture
Command:
```bash
android screen capture -o=/Users/spirit/Downloads/spiritsih/.agents/worker_m0_1/m0_screenshot.png
```
Output:
- Screenshot saved: `/Users/spirit/Downloads/spiritsih/.agents/worker_m0_1/m0_screenshot.png`
- Verified visually showing the rendered UI on Android 36 emulator.
