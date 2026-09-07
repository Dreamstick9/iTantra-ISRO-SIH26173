# Dispatch History

## 2026-09-07T09:51:39Z

You are the Project Orchestrator for iTantra.
Your working directory is: /Users/spirit/Downloads/spiritsih/.agents/orchestrator_1
The workspace directory is: /Users/spirit/Downloads/spiritsih
The authoritative user request is located at: /Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md

Your mission is to lead the design, implementation, and verification of iTantra:
A minimal, working Android native multilingual voice communication prototype using official Sarvam AI APIs (Saaras v3 STT, Sarvam Translate, Bulbul v3 TTS) with a single-screen PTT interface and local feedback logger.

Read /Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md carefully for full requirements and acceptance criteria.
Also inspect existing research and context in /Users/spirit/Downloads/spiritsih (e.g. 01_RESEARCH_FOR_AGENT.md, 02_HUMAN_BRIEF.md, 03_SUPPLEMENTARY.md, PS_SIH26173_OFFICIAL.md, etc.).

Maintain your BRIEFING.md and progress.md in your working directory (/Users/spirit/Downloads/spiritsih/.agents/orchestrator_1) regularly so the Sentinel can track progress and liveness.
When you have fully implemented and verified all acceptance criteria, report your completion and handoff.

## 2026-09-07T09:51:58Z

Verified Sarvam AI API Technical Specifications received:

### 1. Authentication Header
- `api-subscription-key: <API_KEY>`

### 2. Speech-to-Text (Saaras v3)
- `POST https://api.sarvam.ai/speech-to-text`
- Request Header: `api-subscription-key: <KEY>`, `Content-Type: multipart/form-data`
- Parameters:
  - `file`: WAV audio file (16 kHz, 16-bit Mono PCM WAV)
  - `model`: `"saaras:v3"`
  - `language_code`: BCP-47 tag (e.g. `"hi-IN"`, `"en-IN"`, etc.)
  - `mode`: `"transcribe"`
- Response JSON:
  ```json
  {
    "transcript": "...",
    "language_code": "hi-IN"
  }
  ```

### 3. Translation (Sarvam Translate / Mayura)
- `POST https://api.sarvam.ai/translate`
- Request Header: `api-subscription-key: <KEY>`, `Content-Type: application/json`
- Request Body:
  ```json
  {
    "input": "...",
    "source_language_code": "hi-IN",
    "target_language_code": "en-IN",
    "model": "mayura:v1",
    "mode": "formal"
  }
  ```
- Response JSON:
  ```json
  {
    "translated_text": "..."
  }
  ```

### 4. Text-to-Speech (Bulbul v3)
- `POST https://api.sarvam.ai/text-to-speech`
- Request Header: `api-subscription-key: <KEY>`, `Content-Type: application/json`
- Request Body:
  ```json
  {
    "inputs": ["..."],
    "target_language_code": "en-IN",
    "speaker": "meera",
    "model": "bulbul:v3",
    "speech_sample_rate": 16000
  }
  ```
- Response JSON (Base64-encoded standard WAV string):
  ```json
  {
    "audios": ["UklGRiQAAABXQVZF..."]
  }
  ```

### 5. Language Codes (BCP-47)
- English: `en-IN`
- Hindi: `hi-IN`
- Bengali: `bn-IN`
- Tamil: `ta-IN`
- Telugu: `te-IN`
- Kannada: `kn-IN`
- Malayalam: `ml-IN`
- Marathi: `mr-IN`
- Gujarati: `gu-IN`
- Odia: `od-IN`

This has also been appended to /Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md. Please incorporate these exact contracts into the implementation.
