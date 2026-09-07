# Handoff Report — Milestone M0 Architecture & Scaffolding Verification

**Agent**: `challenger_m0_1` (Empirical Challenger)  
**Date**: 2026-09-07T10:14:30Z  
**Target Milestone**: Milestone M0 (Scaffolding & Core Architecture Setup)  
**Verdict**: **APPROVE**  
**Handoff Type**: **Hard** (Task Complete)

---

## 1. Observation

1. **Clean Rebuild Verification**:
   - Executed `./gradlew clean assembleDebug --no-build-cache` in working directory `/Users/spirit/Downloads/spiritsih`.
   - Tool output:
     ```
     BUILD SUCCESSFUL in 3s
     39 actionable tasks: 39 executed
     Configuration cache entry reused.
     ```
   - Produced APK at `app/build/outputs/apk/debug/app-debug.apk` with size `19,418,684` bytes.

2. **AAPT Inspection of Generated APK**:
   - Command: `/Users/spirit/Library/Android/sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk`
   - Verbatim output:
     ```
     package: name='com.itantra.voice' versionCode='1' versionName='1.0.0' compileSdkVersion='36'
     sdkVersion:'26'
     targetSdkVersion:'35'
     uses-permission: name='android.permission.RECORD_AUDIO'
     uses-permission: name='android.permission.INTERNET'
     uses-permission: name='android.permission.ACCESS_NETWORK_STATE'
     uses-permission: name='android.permission.MODIFY_AUDIO_SETTINGS'
     launchable-activity: name='com.itantra.voice.MainActivity'
     native-code: 'arm64-v8a' 'armeabi-v7a' 'x86' 'x86_64'
     ```

3. **Complete Unit Test Suite Execution**:
   - Executed `./gradlew testDebugUnitTest --no-build-cache --rerun-tasks`.
   - Tool output:
     ```
     BUILD SUCCESSFUL in 2s
     26 actionable tasks: 26 executed
     ```
   - Total test suites executed: 7 test suites, 53 tests passed, 0 failures, 0 skipped:
     - `com.itantra.voice.BuildConfigTest`: 10 tests passed
     - `com.itantra.voice.audio.WavAudioOracleTest`: 10 tests passed
     - `com.itantra.voice.data.LanguageTest`: 10 tests passed
     - `com.itantra.voice.LanguageEdgeCaseStressTest`: 6 tests passed
     - `com.itantra.voice.network.SarvamMockContractTest`: 7 tests passed
     - `com.itantra.voice.e2e.Tier3CrossFeatureTest`: 5 tests passed
     - `com.itantra.voice.e2e.Tier4RealWorldScenarioTest`: 5 tests passed

4. **Empirical Edge Case Verification of `Language.kt`**:
   - Bytecode inspection of `Language$Companion.class` revealed:
     ```
     0: aload_1
     1: ldc #27 // String code
     3: invokestatic #33 // Method kotlin/jvm/internal/Intrinsics.checkNotNullParameter:(Ljava/lang/Object;Ljava/lang/String;)V
     ...
     59: invokestatic #63 // Method kotlin/text/StringsKt.equals:(Ljava/lang/String;Ljava/lang/String;Z)Z
     ```
   - Empirically proved via `LanguageEdgeCaseStressTest.kt`:
     - `Language.fromBcp47("HI-in")` -> resolves `Language.HINDI` (PASS).
     - `Language.fromBcp47("en_in")` -> returns `null` because `it.bcp47Code` is `"en-IN"` and underscores are not normalized to hyphens (PASS/Observed).
     - `Language.fromBcp47(" en-IN ")` -> returns `null` because input is not pre-trimmed (PASS/Observed).
     - `Language.fromBcp47(null)` via Java reflection -> throws `InvocationTargetException` wrapping `NullPointerException` (PASS/Observed).

5. **`BuildConfig.SARVAM_API_KEY` Fallback Behavior**:
   - Modified `local.properties` to remove `sarvam.api.key` and re-generated `BuildConfig.java`.
   - Verified verbatim line in `app/build/generated/source/buildConfig/debug/com/itantra/voice/BuildConfig.java:13`:
     `public static final String SARVAM_API_KEY = "";`
   - All unit tests and compilation succeeded with empty key.
   - Restored original `local.properties` (`sarvam.api.key=YOUR_API_KEY_HERE`).

---

## 2. Logic Chain

1. **Build Reproducibility and Stability**:
   - Observation 1 demonstrates that the project compiles from a clean state without depending on Gradle build cache, completing in 3 seconds across 39 tasks.
   - Observation 2 demonstrates that the resulting debug APK contains the expected application ID (`com.itantra.voice`), SDK levels (min 26, target 35, compile 36), mandatory hardware permissions (audio recording, internet), and launcher activity.
   - Therefore, the build configuration and architecture scaffolding are reproducible and sound.

2. **Test Suite Integrity and Robustness**:
   - Observation 3 shows that all 53 unit tests across all project layers (data, audio oracle, network contracts, tier 3 cross-feature, tier 4 scenario, and language edge cases) execute and pass 100% on a clean rerun with `--no-build-cache`.
   - Therefore, the baseline code contracts are satisfied.

3. **Language Matrix Corner Cases**:
   - Observation 4 confirms that `Language.fromBcp47()` correctly handles case-insensitivity across mixed upper/lower case variations (`HI-in`, `hi-in`, `hI-In`).
   - However, strings formatted with underscores (`en_in`, `en_IN`) or whitespace (`" en-IN "`) return `null`. While RFC 5646 specifies hyphens, Android/Java `Locale.toString()` uses underscores. This represents a minor, easily mitigated edge case for Milestone M3.
   - Calling `fromBcp47(null)` via Java interop throws NPE due to Kotlin's non-null parameter checks.

4. **Secret Resolution & Fallback**:
   - Observation 5 confirms that when `sarvam.api.key` is missing or empty, `app/build.gradle.kts` falls back gracefully to `""` without throwing a Gradle build-time configuration exception, allowing CI and offline test harnesses to build and execute cleanly.

---

## 3. Caveats

1. **Hardware-Dependent Microphone Capture**: Audio recording via `android.media.AudioRecord` on a physical microphone was not tested on hardware in M0 (M0 only tests scaffolding, pure-Kotlin WAV encoding, and mocks). Real hardware microphone testing is scheduled for Milestone M1 and M4 on the Android emulator (`emulator-5554`).
2. **Live Sarvam AI Quota**: Network tests currently run against hermetic pure-Kotlin mock fixtures (`MockSarvamInterceptor`), avoiding live API quota usage and cloud dependencies.
3. **Locale Underscores**: Callers should either call `Locale.toLanguageTag()` instead of `Locale.toString()` or apply `code.replace('_', '-')` until `Language.kt` incorporates underscore normalization in M3.

---

## 4. Conclusion

**Verdict: APPROVE**

Milestone M0: Scaffolding & Core Architecture Setup is certified complete and verified. The codebase builds cleanly and reproducibly, produces a valid 19.4 MB debug APK, provides a functional fallback for unconfigured API keys, and passes 53 out of 53 tests. The project is ready to proceed to **Milestone M1: Audio Engine & Sarvam AI Pipeline**.

---

## 5. Verification Method

To independently reproduce and verify this assessment:

1. **Clean Rebuild**:
   ```bash
   ./gradlew clean assembleDebug --no-build-cache
   ```
   *Expected outcome*: `BUILD SUCCESSFUL`, producing `app/build/outputs/apk/debug/app-debug.apk`.

2. **Inspect APK Badging**:
   ```bash
   /Users/spirit/Library/Android/sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "package:|sdkVersion:|targetSdkVersion:|uses-permission:"
   ```
   *Expected outcome*: Confirms `com.itantra.voice`, `minSdkVersion: 26`, `targetSdkVersion: 35`, and required permissions.

3. **Run Unit & Edge-Case Test Suites**:
   ```bash
   ./gradlew testDebugUnitTest --no-build-cache --rerun-tasks
   ```
   *Expected outcome*: 53 tests execute and pass with 0 failures, 0 errors, 0 skipped.

4. **Invalidation Conditions**:
   - Any compilation failure on a clean checkout without build cache.
   - Any test failure in `LanguageTest`, `LanguageEdgeCaseStressTest`, `BuildConfigTest`, or `WavAudioOracleTest`.
   - An unparseable or corrupted APK file.
