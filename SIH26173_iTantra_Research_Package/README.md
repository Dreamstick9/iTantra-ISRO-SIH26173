# SIH26173 "iTantra" — Complete Research Package

**Problem statement:** SIH26173 — *iTantra: Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links*
**Organization:** Indian Space Research Organisation (ISRO) / Department of Space
**Category:** Software · **Theme:** Smart Automation · **Portal deadline shown:** 30 September 2026
**Research compiled:** 5 September 2026

---

## Read in this order

| # | If you are… | Open this |
|---|---|---|
| 1 | A human wanting to understand the problem in 5 minutes | `docs/02_HUMAN_BRIEF.md` |
| 2 | Checking exactly what ISRO asked for (verbatim) | `docs/00_PROBLEM_STATEMENT_OFFICIAL.md` |
| 3 | An AI agent picking up the build | `docs/01_RESEARCH_FOR_AGENT.md` |
| 4 | Preparing the pitch / checking licenses / planning features | `docs/03_SUPPLEMENTARY.md` |

---

## What's in each folder

### `docs/` — the deliverables

- **`00_PROBLEM_STATEMENT_OFFICIAL.md`** — the official problem statement, verbatim, extracted from the sih.gov.in HTML (not a third-party mirror). This is the ground truth every other document is built on. Mirrors and PDF catalogues carry only the *title*; the full background, evaluation weights, and framework restrictions exist only on the portal itself.
- **`01_RESEARCH_FOR_AGENT.md`** — machine-oriented knowledge base. Every fact carries a status tag (`[V]` adversarially verified against a live primary source · `[E]` extracted from a primary source but not vote-verified · `[U]` unverified, re-check · `[GT]` official ground truth). Contains the STT/TTS model matrices with sizes, WER, RTF and licenses; the transport-layer constraints; ISRO's byte budgets; a risk register; open questions; and a milestone build plan. Written to be handed to a coding agent, not read for pleasure.
- **`02_HUMAN_BRIEF.md`** — plain-language explanation of the problem and the proposed solution, for a beginner or a non-technical teammate.
- **`03_SUPPLEMENTARY.md`** — the material that fits neither: a per-asset license audit table, a "what generic teams will do vs. our counter" matrix, the judge-pitch narrative, features ranked by score-impact ÷ effort, and a re-verify-before-submission checklist.

### `evidence/` — primary sources, so nothing has to be taken on trust

- **`sih2026ps_portal_snapshot.html`** (2.7 MB) — full snapshot of sih.gov.in/sih2026PS as fetched on 2026-09-05. Contains all 233 problem statements with complete descriptions. Every quoted line in the docs can be re-derived from this file.
- **`all_problem_statements_index.csv`** — all 233 SIH 2026 problem statements (ID, category, theme, organization, title), extracted from the snapshot.
- **`ISRO_2026_all_problem_statements.md`** — full verbatim text of all 11 ISRO problem statements (SIH26166–26176). Useful context: ISRO's 2026 software asks are uniformly *edge/on-device AI under hard constraints*, which tells you how to pitch.
- **`SIH26172_sibling_hardware_PS.md`** — the sibling problem statement, verbatim. Same ISRO team; it is the embedded keyword-spotting front-end of the same low-bitrate voice chain. SIH26173's own text invites connecting to a "wifi/Bluetooth connected embedded device", so supporting an SIH26172-style device is an invited differentiator rather than scope creep.
- **`SIH2026_master_catalogue_226PS.pdf`** (6 MB) — third-party master catalogue. Included for completeness, but note: it carries titles only, no descriptions. It is the reason a title-only search looks like a dead end.

### `research_raw/` — the audit trail

- **`deep_research_findings.json`** — full output of the deep-research run: 5 search angles, 24 sources fetched, 115 claims extracted, 25 put to a 3-vote adversarial fact-check (23 confirmed, 0 refuted, 2 unverified due to infrastructure errors), plus caveats and open questions. Every finding lists its sources and vote split.
- **`verified_claims_extract.txt`** — the per-source claim dump with verbatim quotes, covering the transport layer, ISRO satellite messaging context, text normalization, and prior art. This is where the NavIC byte budgets and BLE/Wi-Fi throughput numbers come from.
- **`workflow_journal.jsonl`** — the raw agent journal (100 agent results). Machine-readable provenance for every claim above.

Not included: the 106 individual agent transcripts (~68 MB of `agent-*.jsonl`), which live in the session directory and add nothing the journal doesn't summarize.

---

## The three things worth knowing before you build

1. **No single open-source model family covers all 10 required languages** for on-device STT or TTS. Verified gap by gap: Vosk covers 4–5 of 10 (Telugu at 87.9% WER is unusable), sherpa-onnx's streaming ASR catalogue covers only Bengali and English of the ten, and official Piper voices exist for 6 of 10. A hybrid is not a design preference here; it is forced.

2. **Licensing is a scoring trap.** Meta's MMS-TTS covers all 10 languages but is CC-BY-NC-4.0 — not open source under the OSI definition, so it fails the problem statement's "open-source only" rule. Even inside the MIT-tagged Piper repository, individual Indic voices inherit non-commercial licenses from their training data (`hi_IN-pratham` is CC-BY-NC-SA). The audit table in `docs/03_SUPPLEMENTARY.md` records what is safe to ship.

3. **The differentiating build path** is to quantize AI4Bharat's MIT-licensed models — the most accurate open models for these languages, which beat Google's and Azure's paid APIs on published Hindi benchmarks — into an ONNX/sherpa-onnx Android pipeline. Nobody has done this: the repositories have said "Mobile — coming soon" since 2022, and the sherpa-onnx community has an open request for exactly these models. Doing it is both the hardest technical risk and the strongest innovation claim.

---

## Provenance note

The official problem text was read directly from the government portal's HTML rather than any mirror, viewer, or PDF, because every secondary source checked carried the title alone. Model, license, throughput and satellite-messaging figures come from primary sources (HuggingFace model cards, GitHub repositories, arXiv papers, official Android documentation, and ISRO/UNOOSA presentations) and were re-fetched live during verification. Figures that could not be confirmed are marked `[U]` in the agent document and listed again in the supplementary checklist — model catalogues churn quickly, so re-check those before putting them on a slide.
