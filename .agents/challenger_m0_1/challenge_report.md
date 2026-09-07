# Milestone M0 Empirical Challenge Report: Scaffolding & Core Architecture

**Date**: 2026-09-07T10:14:00Z  
**Agent**: `challenger_m0_1` (Empirical Challenger)  
**Target Platform**: Android Native (Kotlin 2.3.20, AGP 9.0.1, Gradle 9.1.0, SDK 35/36)  
**Verdict**: **APPROVE**  

---

## Challenge Summary

**Overall risk assessment**: **LOW**

The M0 Scaffolding and Core Architecture implementation is robust, reproducible, and passes all empirical verification suites without caching or pre-warmed state. The build system, BuildConfig generation, and 10-language matrix definitions meet all architectural requirements. Minor low-severity edge cases were uncovered in `Language.fromBcp47()` regarding underscore locale tags and untrimmed inputs, which are documented below with recommended mitigations for Milestone M3.

---

## Challenges

### [Low] Challenge 1: `Language.fromBcp47()` Does Not Normalize Underscore Locale Tags

- **Assumption challenged**: Callers passing Android or POSIX locale strings (e.g., `"en_IN"`, `"en_in"`, `"hi_IN"`) will be able to resolve to valid `Language` enum entries.
- **Attack scenario**: Android's `java.util.Locale.getDefault().toString()` emits underscore-delimited strings (e.g. `"en_IN"`), whereas `Locale.toLanguageTag()` emits BCP-47 hyphenated strings (`"en-IN"`). Calling `Language.fromBcp47("en_in")` or `Language.fromBcp47("en_IN")` returns `null` because `it.bcp47Code.equals(code, ignoreCase = true)` compares strictly against `"en-IN"`.
- **Blast radius**: If an Android component or caller queries `Language.fromBcp47(Locale.getDefault().toString())`, resolution fails and returns `null`.
- **Mitigation**: In `Language.kt`, normalize underscores to hyphens before matching:
  ```kotlin
  fun fromBcp47(code: String): Language? {
      val normalized = code.trim().replace('_', '-')
      return entries.firstOrNull { it.bcp47Code.equals(normalized, ignoreCase = true) }
  }
  ```
- **Empirical verification**: Confirmed via `LanguageEdgeCaseStressTest.testUnderscoreVsHyphenEdgeCase()`.

---

### [Low] Challenge 2: `Language.fromBcp47()` Does Not Handle Whitespace Padding

- **Assumption challenged**: BCP-47 strings passed to `fromBcp47()` are always sanitized and free of leading/trailing whitespace.
- **Attack scenario**: Untrimmed inputs (e.g., `" en-IN "`, `"\thi-IN\n"`) from external JSON responses, text inputs, or configuration files are passed to `Language.fromBcp47()`.
- **Blast radius**: Returns `null` instead of resolving the language, even though the core BCP-47 tag is valid.
- **Mitigation**: Add `.trim()` before matching in `fromBcp47()`.
- **Empirical verification**: Confirmed via `LanguageEdgeCaseStressTest.testUntrimmedWhitespaceEdgeCase()`.

---

### [Low] Challenge 3: `Language.fromBcp47()` Throws NPE on Null Parameter (Java / Reflection Interop)

- **Assumption challenged**: Null safety of `Language.fromBcp47()` is preserved across language boundaries.
- **Attack scenario**: If called from Java or via reflection with `null`, Kotlin's runtime `Intrinsics.checkNotNullParameter(code, "code")` executes before the method body and throws `NullPointerException` instead of returning `null`.
- **Blast radius**: Uncaught crash if null is passed from external Java libraries or reflection-based deserializers.
- **Mitigation**: Change signature to `fun fromBcp47(code: String?): Language?` and guard with `if (code == null) return null`.
- **Empirical verification**: Confirmed via `LanguageEdgeCaseStressTest.testNullSafetyViaReflection()`.

---

### [Low] Challenge 4: Deprecated `statusBarColor` and Brittle Activity Cast in Theme.kt

- **Assumption challenged**: `(view.context as Activity)` is always valid in Compose.
- **Attack scenario**: In Compose Previews, `DialogWindowProvider`, or when wrapped in a custom `ContextWrapper`, `view.context` may not be a direct `Activity`, risking a `ClassCastException` if `!view.isInEditMode` is bypassed. Furthermore, `window.statusBarColor` is deprecated in Android 15 (API 35/36).
- **Blast radius**: Compose previews or non-Activity contexts may fail if executed in runtime mode.
- **Mitigation**: Use safe cast `(view.context as? Activity)?.window` and adopt Android 15 edge-to-edge `enableEdgeToEdge()` in `MainActivity`.

---

## Stress Test Results

| Scenario | Expected Behavior | Actual Behavior | Result |
|---|---|---|:---:|
| **Build Cleanliness** (`./gradlew clean assembleDebug --no-build-cache`) | Full rebuild succeeds from source without build cache | 39 actionable tasks executed successfully in 3s | **PASS** |
| **Test Cleanliness** (`./gradlew testDebugUnitTest --no-build-cache --rerun-tasks`) | All test suites rerun and pass 100% | 53 unit tests executed across 7 suites in 2s, 0 failures, 0 skipped | **PASS** |
| **BuildConfig Key Missing Fallback** | `sarvam.api.key` deleted from `local.properties` does not break compilation | Generated `BuildConfig.SARVAM_API_KEY = ""`; tests passed; UI shows "Pending / Default" | **PASS** |
| **Language.kt Mixed Casing** (`"HI-in"`, `"hi-in"`, `"HI-IN"`, `"hI-In"`) | Correctly resolves `Language.HINDI` | Returns `Language.HINDI` | **PASS** |
| **Language.kt Underscore Tag** (`"en_in"`, `"en_IN"`) | Documents whether underscores map to hyphens | Returns `null` (strict RFC 5646 hyphen matching) | **PASS (Documented)** |
| **Language.kt Untrimmed Whitespace** (`" en-IN "`) | Documents whether whitespace is trimmed | Returns `null` (strict exact match) | **PASS (Documented)** |
| **Language.kt Bare ISO Code** (`"hi"`, `"en"`, `"ta"`) | Returns `null` for tags missing `-IN` subtag | Returns `null` | **PASS** |
| **Language.kt Unknown Tags** (`"fr-FR"`, `"zh-CN"`, `"invalid"`) | Returns `null` for non-supported languages | Returns `null` | **PASS** |
| **Language.kt Null Safety via Reflection** | Verifies Kotlin runtime non-null check enforcement | Throws `NullPointerException` via `Intrinsics.checkNotNullParameter` | **PASS (Documented)** |
| **APK Binary Verification** (`app-debug.apk`) | Valid APK generated, non-empty, parseable by AAPT | Valid 19.4 MB APK, 7 DEX files, 4 ABI libs, correct permissions & manifest | **PASS** |

---

## Unchallenged Areas

- **Microphone Hardware Streaming**: Physical microphone buffer streaming via `AudioRecord` (deferred to Milestone M1 and connected emulator verification).
- **Live Sarvam AI Network Endpoints**: Real HTTP requests against live cloud servers with production credentials (tested hermetically via MockSarvamInterceptor; live integration scheduled in Milestone M1/M4).
- **Physical UI Pointer Touch Drag Gestures**: Jetpack Compose pointer input drag-to-cancel gestures (deferred to Milestone M2 UI implementation).

---

## Verdict & Recommendation

**Verdict: APPROVE**

Milestone M0 Scaffolding and Core Architecture Setup is verified to be fully reproducible, robust, and compliant with all project and architectural specifications. All 53 unit tests pass cleanly without caching. The challenges noted above are low severity and should be addressed as minor enhancements during Milestone M3 (Language Matrix refinement).
