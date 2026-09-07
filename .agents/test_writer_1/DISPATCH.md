# DISPATCH — 2026-09-07T10:05:00Z

## Mission
You are `test_writer_1`, a `teamwork_preview_test_writer` subagent leading the E2E Testing Track.
Your working directory is: `/Users/spirit/Downloads/spiritsih/.agents/test_writer_1`
The project workspace is: `/Users/spirit/Downloads/spiritsih`
The authoritative user request is: `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md` (MUST read first).
The project index is: `/Users/spirit/Downloads/spiritsih/PROJECT.md` (MUST read).

## Task
1. Read `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md`, `/Users/spirit/Downloads/spiritsih/PROJECT.md`, and the survey reports in `.agents/spec_miner_survey_1/specs_report.md` and `.agents/explorer_survey_2/architecture_report.md`.
2. Design the complete E2E opaque-box test infrastructure and test suite based on the 4-tier methodology:
   - **Tier 1 - Feature Coverage**: $\ge 5$ test cases per feature (for all 18 features in `PROJECT.md § Feature Inventory`).
   - **Tier 2 - Boundary & Corner Cases**: $\ge 5$ test cases per feature covering boundaries (short taps <300ms, long speech, empty transcripts, network timeouts, 401/429 errors, malformed JSON, etc.).
   - **Tier 3 - Cross-Feature Combinations**: Pairwise interactions (e.g. language switch + PTT + TTS replay, network failure during translation + retry, etc.).
   - **Tier 4 - Real-World Application Scenarios**: $\ge 5$ realistic multi-turn field communication workflows (e.g. disaster warning in Hindi translated to English and synthesized, Tamil query translated to Hindi, emergency broadcast playback, user feedback logging).
3. Create `/Users/spirit/Downloads/spiritsih/TEST_INFRA.md` documenting test philosophy, feature inventory matrix, runner commands, and tier thresholds.
4. Prepare the test files and test harness (e.g. unit/integration test cases, mock HTTP server/fixtures for Sarvam AI contracts).
5. When the full test suite is designed and ready, publish `/Users/spirit/Downloads/spiritsih/TEST_READY.md` containing the test runner command, coverage table, and feature checklist.
6. Write `/Users/spirit/Downloads/spiritsih/.agents/test_writer_1/handoff.md` and send a message when done.

## 2026-09-07T10:06:00Z
You are test_writer_1. Your working directory is /Users/spirit/Downloads/spiritsih/.agents/test_writer_1. Read /Users/spirit/Downloads/spiritsih/.agents/test_writer_1/DISPATCH.md and /Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md. Follow all instructions in DISPATCH.md to create TEST_INFRA.md, design the 4-tier opaque-box E2E test suite, publish TEST_READY.md, write handoff.md, and notify when complete.
