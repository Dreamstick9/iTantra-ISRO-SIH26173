# Milestone M1 Review Report: Sarvam AI Network Layer & API Contracts

**Reviewer:** `reviewer_m1_2` (Teamwork Reviewer & Adversarial Critic)  
**Date:** 2026-09-07T10:32:00Z  
**Target:** Milestone M1 — Sarvam AI Network Client (`SarvamApiClient.kt`), Typed Contracts (`SarvamApiModels.kt`), Language Registry (`Language.kt`), and Build Configuration (`app/build.gradle.kts`)  
**Verdict:** **APPROVE**

---

## 1. Review Summary

- **Verdict:** **APPROVE**
- **Integrity Audit:** **PASS** (Zero hardcoded outputs, zero facade/dummy implementations, zero bypasses, authentic offline test interceptors, independent clean builds).
- **Compilation & Test Status:**
  - `./gradlew assembleDebug`: **SUCCESS** (APK generated)
  - `./gradlew testDebugUnitTest --rerun-tasks`: **SUCCESS** (97 of 97 tests passed, 0 failures, 0 errors, 0 skipped)
  - `./gradlew lintDebug`: **SUCCESS** (0 lint errors)
- **API Specification Compliance:** 100% compliant with official Sarvam AI API specifications (`saaras:v3` multipart STT, `mayura:v1` translation, `bulbul:v3` 16 kHz TTS, `api-subscription-key` authentication header, and 10-language Indic matrix BCP-47 codes).

---

## 2. Integrity Verification

As part of adversarial review, the codebase was inspected for integrity violations:
1. **Hardcoded Test Results / Facades:** None. `SarvamApiClient` performs real multipart assembly (`MultipartBody.Builder`), JSON serialization (`kotlinx.serialization`), OkHttp request dispatching, and response deserialization.
2. **Shortcuts & Bypasses:** None. Audio formats match exact 16 kHz Mono 16-bit linear PCM WAV container requirements.
3. **Fabrication of Test Outputs:** None. All test runs were executed live within this session via Gradle daemon; 97 tests verified across 11 test suites.
4. **Credential Safety:** `local.properties` contains `sarvam.api.key=YOUR_API_KEY_HERE` (placeholder). `local.properties` is in `.gitignore`. Logging interceptor explicitly calls `redactHeader("api-subscription-key")`.

---

## 3. Findings

### [Minor] Finding 1: FastAPI Error Format Extension
- **What:** In `mapHttpError`, error JSON is parsed against `SarvamErrorResponse` which checks `error.message` and `message`. Some Python/FastAPI microservices return errors as `{"detail": "..."}`.
- **Where:** `app/src/main/java/com/itantra/voice/network/SarvamApiClient.kt:264-268`
- **Why:** If Sarvam AI returns a 422 or 400 error formatted with `detail` instead of `message` or `error.message`, `serverMessage` will evaluate to `null`.
- **Mitigation / Suggestion:** The client already has robust fallback strings for all HTTP status codes (e.g. 401, 403, 429, 5xx), so it degrades gracefully to standard human-readable messages. In a future milestone, `SarvamErrorResponse` can optionally add `@SerialName("detail") val detail: String? = null`.
- **Severity:** Minor (Non-blocking).

---

## 4. Verified Claims

- **Claim 1: Saaras v3 STT Multipart Contract**
  - Verified via: `SarvamApiClientTest.testTranscribeDispatchesMultipartAndParsesResponse` & `SarvamMockContractTest.testSttMultipartRequestDispatchesToSpeechToTextEndpoint`.
  - Observation: Request is POST multipart/form-data to `/speech-to-text` with parts `model="saaras:v3"`, `language_code="hi-IN"`, `mode="transcribe"`, and `file="recording.wav"`.
  - Status: **PASS**

- **Claim 2: Mayura v1 Translation JSON Contract**
  - Verified via: `SarvamApiClientTest.testTranslateDispatchesJsonAndParsesResponse` & `SarvamMockContractTest.testTranslateRequestDispatchesJsonToTranslateEndpoint`.
  - Observation: Request is POST JSON to `/translate` with `input`, `source_language_code`, `target_language_code`, `model="mayura:v1"`, and `mode="formal"`.
  - Status: **PASS**

- **Claim 3: Bulbul v3 TTS JSON Contract**
  - Verified via: `SarvamApiClientTest.testSynthesizeDispatchesJsonAndParsesResponse` & `SarvamMockContractTest.testTtsRequestDispatchesJsonToTextToSpeechEndpoint`.
  - Observation: Request is POST JSON to `/text-to-speech` with `inputs=["..."]`, `target_language_code="en-IN"`, `speaker="meera"`, `model="bulbul:v3"`, `speech_sample_rate=16000`.
  - Status: **PASS**

- **Claim 4: Error Mapping Taxonomy (401, 403, 429, 500, Network Dropouts)**
  - Verified via: Target-specific tests for `AuthenticationException`, `RateLimitException`, `ServerException`, `InvalidRequestException`, and `EmptyResponseException`.
  - Observation: All HTTP error codes map to explicit domain exceptions; empty payloads fail fast before making network calls.
  - Status: **PASS**

- **Claim 5: Underscore and Case-Insensitive BCP-47 Normalization**
  - Verified via: `LanguageTest` (10 tests) and `LanguageEdgeCaseStressTest` (6 tests).
  - Observation: `Language.fromBcp47("hi_in")` correctly resolves to `Language.HINDI`.
  - Status: **PASS**

- **Claim 6: API Key Sanitization and Security**
  - Verified via: `BuildConfigTest` (10 tests) and `SarvamApiClientTest.testPlaceholderApiKeyDetection`.
  - Observation: Wrapping quotes and whitespace are trimmed; placeholder keys are flagged before dispatch; authorization headers are redacted in logs.
  - Status: **PASS**

---

## 5. Adversarial Challenge & Stress Test Report

### Overall Risk Assessment: LOW

### Challenge 1: Empty or Whitespace Input Payloads
- **Assumption Challenged:** Client could send blank strings or zero-byte audio to Sarvam AI, wasting network resources and triggering API errors.
- **Attack Scenario:** Transcribing 0-byte audio, translating `""` or `"   "`, synthesizing `"   "`.
- **Observed Defense:** `SarvamApiClient` enforces fast-fail guards (`if (wavData.isEmpty())`, `if (text.isBlank())`) returning `InvalidRequestException` without making OkHttp calls (`capturedRequests.size == 0`).
- **Verdict:** **PASS (Robust)**

### Challenge 2: Non-JSON Server Responses (HTML 502/504 Bad Gateway)
- **Assumption Challenged:** Upstream proxy errors (Cloudflare/Nginx) return HTML instead of JSON, which could crash serialization parsers.
- **Attack Scenario:** Server returns HTTP 502 with HTML body `<html>502 Bad Gateway</html>`.
- **Observed Defense:** In `mapHttpError`, `json.decodeFromString<SarvamErrorResponse>(bodyString)` is wrapped in `try { ... } catch (ignored: Exception) { null }`. When JSON decoding fails, `serverMessage` gracefully evaluates to `null` and default human-readable message is returned.
- **Verdict:** **PASS (Robust)**

### Challenge 3: Empty Audios Array in TTS Response
- **Assumption Challenged:** Server returns 200 OK with `{"audios": []}` or `{"audios": [""]}`.
- **Attack Scenario:** TTS synthesis returns no audio chunks.
- **Observed Defense:** `SarvamApiClient.synthesize` checks `response.audios.isEmpty() || response.audios.first().isBlank()`, throwing `EmptyResponseException("TTS response contained empty audio array.")`.
- **Verdict:** **PASS (Robust)**

### Challenge 4: Header Redaction Under Debug Logging
- **Assumption Challenged:** `HttpLoggingInterceptor.Level.BODY` could log the plaintext `api-subscription-key` to logcat.
- **Attack Scenario:** Log inspection of debug build.
- **Observed Defense:** `createLoggingInterceptor()` explicitly configures `.redactHeader("api-subscription-key")`.
- **Verdict:** **PASS (Robust)**

---

## 6. Coverage Gaps & Unverified Items

- **Live Cloud Network Call:** Live requests against production Sarvam AI require a valid paid subscription key in `local.properties`. Unit and E2E contract tests use hermetic `MockSarvamInterceptor` to prevent flakiness and quota depletion. Live testing will be executed during final emulator demo.
- **UI & ViewModel Connection:** ViewModel state machine integration (`MainViewModel`) is scheduled for Milestone M2.
