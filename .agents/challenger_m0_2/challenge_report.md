# Milestone M0 Empirical Challenge Report: Android Emulator Deployment & Architecture

**Author:** `challenger_m0_2` (Teamwork Empirical Challenger & Critic Specialist)  
**Target Platform:** Android Native (Kotlin 2.3.20, Min SDK 26, Target SDK 35, Jetpack Compose BOM 2026.03.01)  
**Device Under Test:** `emulator-5554` (AVD: `medium_phone(AVD) - 16`, API 35/36 environment)  
**Verdict:** **APPROVE**

---

## Challenge Summary

**Overall risk assessment**: **LOW**

Milestone M0 delivers a fully functional Android build system, clean architecture separation, zero-crash deployment on `emulator-5554`, responsive Jetpack Compose layout reflow across orientation shifts, and a passing 47-test opaque-box hermetic test baseline.

---

## Challenges

### [Low] Challenge 1: Deprecated `window.statusBarColor` in `Theme.kt`

- **Assumption challenged**: Status bar color should be set directly on `window.statusBarColor` within a `SideEffect`.
- **Attack scenario**: On Android 15 (Target SDK 35) and future Android 16 runtimes, edge-to-edge enforcement is standard. Calling deprecated `window.statusBarColor = colorScheme.primary.toArgb()` produces a compiler warning and is ignored or overridden by edge-to-edge system insets on newer OS versions.
- **Blast radius**: Cosmetic only. No runtime crash occurs, but status bar styling may behave inconsistently on Android 15+ devices once edge-to-edge is fully activated in `MainActivity`.
- **Mitigation**: Migrate in Milestone M2 to `enableEdgeToEdge()` in `MainActivity.onCreate()` and allow Compose `Scaffold` padding to govern status bar insets cleanly.

### [Low] Challenge 2: Static UI Layout Placeholder in M0 Scaffold

- **Assumption challenged**: The initial scaffold UI does not yet expose live audio recording or dynamic language selector dropdowns.
- **Attack scenario**: If downstream consumers expected interactive PTT controls in M0, the current screen only shows static metadata cards (`iTantra`, `Milestone M0: Scaffolding Ready`, `Min SDK: 26 | Target SDK: 35`, `API Key: Pending / Default`).
- **Blast radius**: None for M0. Per `PROJECT.md`, M0 scope is strictly Scaffolding & Core Architecture Setup. PTT button and AudioRecord are scheduled for M1, and Compose UI interactive components for M2.
- **Mitigation**: Validate that M1 and M2 workers wire `AudioRecord` and `MainViewModel` into `MainScreen` per the planned roadmap.

---

## Stress Test Results

| Test ID | Scenario | Expected Behavior | Actual Behavior | Pass/Fail |
|---|---|---|---|:---:|
| `ST-01` | Emulator Device Connection (`adb -s emulator-5554 get-state`) | Return `device` | Returned `device` (device online) | **PASS** |
| `ST-02` | Debug APK Build & Install (`./gradlew installDebug`) | Clean build and install on emulator-5554 | `BUILD SUCCESSFUL in 1s`, installed on 1 device | **PASS** |
| `ST-03` | Activity Launch (`am start -n com.itantra.voice/.MainActivity`) | Launch `MainActivity` into foreground | Activity launched (`cmp=com.itantra.voice/.MainActivity`) | **PASS** |
| `ST-04` | UI Layout Tree Extraction (`android layout -p --device=emulator-5554`) | Return valid JSON hierarchy with app title, subtitle, status card | Successfully returned 5 UI nodes centered at x=540 | **PASS** |
| `ST-05` | Orientation Switch to Landscape (`user_rotation 1`) | Recreate activity, reflow Compose layout horizontally without clipping | Reflowed cleanly; node centers shifted to x=1200; screenshot captured | **PASS** |
| `ST-06` | Orientation Switch to Portrait (`user_rotation 0`) | Recreate activity, restore portrait geometry | Restored cleanly; node centers returned to x=540 | **PASS** |
| `ST-07` | App Backgrounding & Resume (`KEYCODE_HOME` -> foreground) | App preserves task state and returns to front | Brought task to front cleanly without state corruption | **PASS** |
| `ST-08` | Process Termination & Cold Boot (`am force-stop` -> restart) | Clean cold startup without ANR or fatal exceptions | Cold boot succeeded, UI rendered immediately | **PASS** |
| `ST-09` | Runtime Crash Scan (`logcat -d -s AndroidRuntime:E iTantra:D`) | Zero fatal exceptions or uncaught errors | 0 errors returned | **PASS** |
| `ST-10` | Full JVM Test Suite (`./gradlew test --rerun-tasks`) | 100% pass across all unit and mock contract tests | 47/47 tests passed (0 failures, 0 skipped) | **PASS** |
| `ST-11` | Instrumented Test Harness (`./gradlew connectedAndroidTest`) | Execute connected Android test runner on emulator-5554 | `BUILD SUCCESSFUL in 17s`, runner finished cleanly | **PASS** |

---

## Unchallenged Areas

- **Live Microphone Hardware Capture**: `emulator-5554` virtual audio input was not tested with physical acoustic speech (Feature `F1` scheduled for Milestone M1).
- **Live Sarvam AI API Endpoints**: Production cloud API endpoints were not queried with live billable tokens; verified hermetically via `MockSarvamInterceptor` and `SarvamMockFixtures` (Feature `F4`-`F6` live integration scheduled for Milestone M1).
- **Local SQLite / Room DB**: Out of scope; project architecture specifies lightweight atomic JSON logging (`File(filesDir, "feedback_logs.json")`).
