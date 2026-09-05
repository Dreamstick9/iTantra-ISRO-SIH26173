# SIH26173 — Supplementary: License Audit, Strategy, Risks & Checklists

*Material found necessary during research that belongs in neither the agent knowledge base nor the beginner brief.*

## 1. License audit table (fill ✅/❌ before shipping ANY model)

| Asset | License | SIH-eligible? | Note |
|---|---|---|---|
| AI4Bharat IndicConformer (600M + 120M variants) | MIT | ✅ | HF repo access-gated (form, not license); verified 2026-09-05 |
| AI4Bharat IndicWav2Vec (all artifacts) | MIT (HF ports Apache-2.0) | ✅ | Verified |
| AI4Bharat Indic-TTS checkpoints | MIT (repo LICENSE.txt; "we open-source all models") | ✅ | MIT covering checkpoint binaries is inferred from repo license + statement, not per-asset grant — say so if asked |
| Vistaar training data (13 datasets) | MIT release; constituents vary | ⚠️ | IndicTTS & NPTEL corpora have own access terms — audit per dataset if retraining |
| Vosk models (hi/gu/te/en-IN) | Apache-2.0 (hi verified) | ✅ hi/en | Telugu unusable anyway (87.9 WER) |
| sherpa-onnx runtime | Apache-2.0 | ✅ | |
| sherpa-onnx tts-models release Indic assets (Coqui bn, Mimic3 bn/gu, Piper hi/ml) | UNAUDITED | ❓ | Audit each before use — Mimic3 voices often AGPL/CC mixtures |
| Piper runtime + piper-voices repo | MIT | ✅ repo | License is PER-VOICE, see next rows |
| Piper hi_IN-pratham | dataset CC-BY-NC-SA-4.0 | ❌ | Do NOT ship (non-commercial) |
| Piper mr_IN-google (OpenSLR-64) | CC-BY-SA-4.0 | ✅ | Verified clean |
| Piper bn_BD, ml_IN, te_IN voices | UNAUDITED | ❓ | Check each MODEL_CARD; bn is Bangladesh accent |
| Meta MMS-TTS (all 10 langs) | CC-BY-NC-4.0 | ❌ | Not OSI open source (OSD §6). Baseline comparisons only, disclosed |
| Kenpath indic-text-normalization | Apache-2.0 | ✅ | No Odia — we build it |
| Unishox2 | Apache-2.0 | ✅ | No Java binding — we write JNI |
| KenLM | LGPL-2.1 | ✅ (dynamic link) | Keep as separate .so |
| eSpeak-NG | GPL-3.0 | ⚠️ | Fallback only; GPL fine for open submission but drags whole app if statically mixed |
| Google Nearby Connections | Closed (Play Services) | ❌ | Named avoidance = pitch point |

## 2. What generic teams will do vs. our counters

| Generic move (what an LLM suggests by default) | Why it fails | Our counter |
|---|---|---|
| Whisper-tiny multilingual for STT | Non-streaming, weak on Indic (Vistaar shows fine-tuned models −4.1 WER better), no VAD gating story | Quantized AI4Bharat Conformers + silero-VAD gating (scores the idle-CPU metric directly) |
| Android's built-in SpeechRecognizer / Google TTS | Proprietary, often online — instant disqualification | All-open pipeline, license table in hand |
| MMS-TTS "because it covers everything" | CC-BY-NC = not open source | AI4Bharat Indic-TTS (MIT) incl. Hinglish voice |
| Google Nearby for P2P | Closed-source Play Services | WiFi Direct/Aware TCP + RFCOMM failover, documented |
| Send raw UTF-8 strings | 3 B/char Indic ⇒ misses the "low bitrate" soul of the PS | Unishox2 + alert code-book sized to NavIC's 27-byte sub-frame |
| Ignore text normalization | "₹1,500" read digit-by-digit ruins the 40% flow score | WFST normalization + code-mix segmentation (MOS 3.82 vs 3.3 evidence) |
| Treat it as a chat app | Judges have seen Briar/Bridgefy clones | Speech↔text semantic transcoding + ISRO ecosystem fit + live metrics overlay |

## 3. Judge-pitch narrative arc (60 seconds)

1. ISRO already delivers life-saving alerts as ≤277-byte NavIC text messages to fishermen — via Bluetooth receivers paired to phones, in 13 languages. **One-way. Text-only.**
2. iTantra closes the loop: speak → text → 27–277 bytes → speech. Two-way voice over links where voice is impossible; works for users who cannot read (PS's own inclusivity rationale).
3. New engineering, not glue: first mobile port of AI4Bharat's MIT models (which beat Google/Azure on Hindi WER), int8-quantized, with measured WER/RTF tables — contributed upstream to sherpa-onnx where Indic models are a documented open request (discussion #3199).
4. Live demo: 2 phones PTT walkie-talkie (WiFi Direct → pull WiFi, auto-fails to Bluetooth), ESP32 "satellite receiver" injects a cyclone alert → app announces at max volume, non-interruptible. Metrics overlay shows WER/latency/RTF as it happens.

## 4. Feature ideas ranked (build order by score-impact ÷ effort)

1. VAD-gated recognition (idle CPU metric) — trivial with silero-VAD in sherpa-onnx.
2. Metrics overlay (makes all three scored criteria visible) — cheap, huge demo value.
3. Alert QoS (USAGE_ALARM AudioAttributes, max volume, audio-focus non-interruptible, msgId dedup) — required by PS, mirrors NavIC priorities.
4. Transport auto-failover WiFi→BT — required ("wifi/Bluetooth"), demoable failure recovery.
5. Unishox2 JNI + compression-ratio table per script — non-generic, quantitative.
6. Code-mix (Hinglish) handling + WFST normalization — attacks the 40% criterion where competitors are weakest.
7. KenLM shallow fusion (−1.3 to −3 WER, unverified numbers — measure) — medium effort.
8. Language packs + P2P pack sharing over WiFi Direct — offline-first story, medium effort.
9. ESP32 NavIC-emulator bridge — high wow, moderate effort (BluetoothSerial line framing is trivial).
10. Store-and-forward relay w/ acks (fixes Meshtastic's documented dup gap) — nice-to-have.
11. Odia normalizer (gap in Kenpath) — small, claimable contribution.

## 5. Facts to re-verify before submission (flagged unverified/volatile)

- [ ] IndicWav2Vec README WER table (hi 16.0/14.7 etc.) — verifier votes errored; re-read repo.
- [ ] Piper Indic voices "medium-only, 63–77 MB, no x_low" — 1 valid vote only; re-check HF tree (mr_IN voice appeared ~2026-08 — catalog moves).
- [ ] sherpa-onnx streaming Indic gap still open? (models dated 2026-02 exist; gap may close before finals — if AI4Bharat or k2 publish Indic streaming models, ADOPT rather than compete.)
- [ ] NavIC SIS ICD exact bitfields — fetch from isro.gov.in/irnss-programme.
- [ ] sherpa tts-models Indic asset licenses.
- [ ] Vosk figures are Alpha Cephei self-reported (~2021-22); Google/Azure WER comparison is mid-2023 zero-shot — phrase as "reported" in slides.
- [ ] Metric weights sum to 80% in PS — the last 20% is unstated; ask organizers or assume presentation.

## 6. Session working-file index

- [PS_SIH26173_OFFICIAL.md](PS_SIH26173_OFFICIAL.md) — verbatim official PS (ground truth).
- [01_RESEARCH_FOR_AGENT.md](01_RESEARCH_FOR_AGENT.md) — full machine-oriented knowledge base w/ verification tags + build plan.
- [02_HUMAN_BRIEF.md](02_HUMAN_BRIEF.md) — beginner explanation + proposed solution.
- Raw SIH portal snapshot: scratchpad `sih2026ps.html` (2.7 MB, all 233 PS with full descriptions — includes sibling SIH26172 full text, already quoted in agent doc §0).
- Deep-research workflow output (full findings JSON + 106-agent journal): session task `wvzqmf40b` / run `wf_5a0edc65-6d2`.

## 7. ISRO sibling problem statements (SIH 2026) — context for Q&A

26166 Chandrayaan-2 image correspondence · 26167 SatQuery VLM for remote sensing · 26168 AI dead-reckoning nav · 26169 virtual camera tracking (FSO alignment) · 26170 anomaly detection burn-in · 26171 on-device visual perception browser agents · **26172 voice activator edge device (HW — the KWS front-end to our chain)** · **26173 iTantra (this)** · 26174 astronaut HAR for BAS · 26175 DepthWizard height estimation · 26176 ORCA marine agents.
Pattern: ISRO's 2026 software asks are all *edge/on-device AI under constraints* — pitch accordingly.
