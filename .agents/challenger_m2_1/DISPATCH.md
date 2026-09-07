# DISPATCH: challenger_m2_1

## Role & Identity
You are **challenger_m2_1** (`teamwork_preview_challenger`).
Your working directory is: `/Users/spirit/Downloads/spiritsih/.agents/challenger_m2_1`
Project root: `/Users/spirit/Downloads/spiritsih`
Authoritative user request: `/Users/spirit/Downloads/spiritsih/.agents/ORIGINAL_REQUEST.md`
Project blueprint: `/Users/spirit/Downloads/spiritsih/PROJECT.md`
Worker handoff report: `/Users/spirit/Downloads/spiritsih/.agents/worker_m2_1/handoff.md`

## Assignment
Empirically stress-test the Finite State Machine (`MainViewModel.kt` and `PttState` transitions):
1. Write adversarial test cases (e.g. in `MainViewModelAdversarialStressTest.kt` under `app/src/test/java/com/itantra/voice/ui/`):
   - Rapid repeated PTT tapping (rapid press/release cycles <300ms) to ensure no leaked coroutines, memory leaks, or duplicate network calls.
   - Pressing PTT while in each busy state (`TRANSCRIBING`, `TRANSLATING`, `SYNTHESIZING`) to ensure clicks are strictly ignored.
   - Pressing PTT while in `PLAYING` state to ensure audio stops immediately.
   - Pressing PTT while in `ERROR` state to ensure error is cleared and recording begins.
   - Language switching while in `PLAYING` or `ERROR` state.
   - Replay audio when cache is empty vs present.
   - Coroutine lifecycle cancellation: ensure no hanging jobs when ViewModel is cleared or recording is canceled.
2. Execute tests with `./gradlew test --rerun-tasks` and verify all tests pass.
3. Issue an empirical verdict: `APPROVE` or `REJECT`.
4. Produce `challenge_report.md` and `handoff.md`, and notify the parent orchestrator (`9da65215-7c76-4ab0-8790-34b4580958ab`).
