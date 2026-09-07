# DISPATCH — 2026-09-07T09:53:00Z

## Mission
You are `spec_miner_survey_1`, a `teamwork_preview_spec_miner` subagent.
Your working directory is: `/Users/spirit/Downloads/spiritsih/.agents/spec_miner_survey_1`
The project workspace is: `/Users/spirit/Downloads/spiritsih`
The authoritative user request is: `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md` (MUST read first).

## Task
1. Read `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md` and related context:
   - `/Users/spirit/Downloads/spiritsih/PS_SIH26173_OFFICIAL.md`
   - `/Users/spirit/Downloads/spiritsih/01_RESEARCH_FOR_AGENT.md`
   - `/Users/spirit/Downloads/spiritsih/02_HUMAN_BRIEF.md`
   - `/Users/spirit/Downloads/spiritsih/03_SUPPLEMENTARY.md`
   - `/Users/spirit/Downloads/spiritsih/README.md`
2. Extract and rigorously document the complete technical specifications:
   - Complete Feature Inventory with requirement traceability (R1, R2, R3, R4, acceptance criteria)
   - Exact official Sarvam AI API endpoints, HTTP methods, headers (`api-subscription-key`), request parameters, payload structures, response schemas, and model names (`saaras:v3`, `mayura:v1` / `sarvam-translate:v1`, `bulbul:v3`)
   - Complete 10-language BCP-47 mapping table and speaker options
   - Audio specifications: sample rate (16 kHz), bit depth (16-bit), channels (mono PCM), WAV container requirements
   - UI specifications: Compose single-screen layout hierarchy, exact PTT state machine transitions (`IDLE`, `RECORDING`, `TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`, `PLAYING`, `ERROR`)
   - Local feedback logging: private JSON format, schema, storage location, error handling cases
3. Write your findings to `/Users/spirit/Downloads/spiritsih/.agents/spec_miner_survey_1/specs_report.md`.
4. Write `/Users/spirit/Downloads/spiritsih/.agents/spec_miner_survey_1/handoff.md` following the Handoff Protocol (Observation, Logic Chain, Caveats, Conclusion, Verification Method).
5. Send a message to your parent (`d2dc27dd-22a8-43b3-872c-adab2ea405c8` or orchestrator conversation ID) with the path to your report.
