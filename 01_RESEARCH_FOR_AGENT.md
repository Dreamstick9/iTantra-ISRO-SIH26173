# SIH26173 iTantra — DEEP RESEARCH KNOWLEDGE BASE (agent-consumption format)

<!--
AUDIENCE: autonomous coding/planning agent. Not prose for humans.
FORMAT CONVENTIONS:
- Every fact carries a STATUS tag:
  [V] = adversarially verified by 3-vote (or 2-vote) fact-check against live primary source, 2026-09-05.
  [E] = extracted verbatim from a fetched primary source but NOT adversarially verified (verification budget dropped it). Treat as high-probability true; re-fetch source before citing in judged material.
  [U] = unverified/contested; re-check before use.
  [GT] = ground truth: official PS text fetched directly from sih.gov.in HTML on 2026-09-05 (see PS_SIH26173_OFFICIAL.md).
- SRC: URLs are primary sources. Dates matter; model catalogs churn (Piper mr_IN voice landed ~2026-08; sherpa-onnx catalog has 2026-02 models). Re-check coverage gaps before finalizing model choices.
- All RTF numbers are Raspberry Pi 4B (Cortex-A72) measurements = conservative proxy for low/mid Android big cores.
-->

## 0. TASK DEFINITION (ground truth)

[GT] PS ID 26173, org ISRO/Dept. of Space, category Software, theme Smart Automation, deadline on portal 30-09-2026, 0/500 submissions at fetch time.
[GT] BUILD: Android app. Fully-offline STT + TTS for exactly 10 languages: {Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English}. Low/mid-range phones.
[GT] STT mode: VAD-triggered (activate on pauses/stoppages) → form sentences → stream TEXT over WiFi/Bluetooth to (a) embedded device OR (b) another phone running same app. Minimal latency.
[GT] TTS mode: receive text → intelligible speech played as voice note. Alert-type messages: highest volume, non-interruptible.
[GT] Acceptance demo: two phones, one in STT mode + one in TTS mode, over WiFi or Bluetooth = push-to-talk walkie-talkie. PTT off ⇒ "works like a phone" (normal app behavior).
[GT] Scoring: Accuracy 40% (STT WER low; TTS legibility/flow high). Efficiency 20% (model size, app size, RAM/flash, CPU during idle listening). Latency 20% (speech-end→STT-text delay; text→audio-start delay + RTF; end-to-end sentence-said→sentence-heard delta). Remaining 20% unstated (assume presentation/innovation/completeness).
[GT] Restrictions: open-source ONLY, proprietary/commercial voice SDKs prohibited; frameworks: TFLite / PyTorch Mobile "or similar" (sherpa-onnx/ONNX Runtime qualifies as "similar" — both open source); fully offline, no internet-hosted APIs for STT or TTS.
[GT] Sibling context: ISRO filed 11 PS in SIH2026 (26166–26176). SIH26172 (Hardware) = "Low Latency and Efficient Voice Activator for Edge Devices": KWS on <256KB RAM, <10% idle CPU, ESP32/RPi, custom keyword, streams audio onward. Same authoring team; iTantra is the phone-side half of a low-bitrate voice-messaging chain. Design the transport so an SIH26172-style embedded device slots in.

## 1. DECISION-CRITICAL SYNTHESIS

1. [V] NO single open model family covers all 10 languages for STT or for TTS with an Android-ready runtime. Winning architecture is necessarily a hybrid; teams that pick "just Whisper" or "just Vosk" will fail coverage, size, or license.
2. [V] AI4Bharat (MIT-licensed) = accuracy anchor for the 9 Indic languages; ships NO mobile runtime → export/quantization work is the differentiator and the schedule risk.
3. [V] The projects WITH Android runtimes have gaps: Vosk covers 4–5/10 languages (Telugu unusable @ 87.9 WER); sherpa-onnx official streaming-ASR catalog covers only Bengali+English of the 10; official Piper voices cover 6/10 languages.
4. [V] LICENSE TRAP: MMS-TTS (all 10 langs) is CC-BY-NC-4.0 = NOT open source (OSD §6) → do not ship; use only as offline quality baseline, disclosed. Individual Piper voices inherit dataset licenses: hi_IN-pratham is CC-BY-NC-SA (trained on AI4Bharat corpus) — audit EVERY voice's MODEL_CARD.
5. [V] Latency target is provably reachable: small-VITS/Piper class TTS = RTF 0.35–0.81 on RPi4 single-thread; int8 cuts model ~3× (VITS VCTK 116→37 MB; zipformer encoder 250→68 MB class).
6. [E] The non-generic framing that fits ISRO's actual ecosystem: NavIC short-message budget is 220 bits/sub-frame (27 bytes), messages 220–2220 bits total (≈27–277 B); ISRO's deployed fisherman receivers already do satellite→Bluetooth→phone-app→multilingual audio alert. iTantra = the generalized, speech-in/speech-out version of a system ISRO already fields. Design the wire format to fit a 27-byte sub-frame and demo an ESP32 bridge emulating a NavIC/DAT-SG receiver.

## 2. STT MODEL MATRIX (angle 1)

### 2.1 AI4Bharat IndicConformer-600M
- [V] 600M params, multilingual Conformer, hybrid CTC+RNNT decoding, 16 kHz in, MIT license, 22 languages = 9/10 targets (NO English). ~2.4 GB fp32 ⇒ teacher/reference only, not deployable raw. HF repo is access-gated (contact-info wall, not a license wall); requires trust_remote_code=True. Smaller ~120M per-language Conformer variants exist in the suite ([V], noted by verifiers — enumerate at HF org ai4bharat).
- SRC: https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual · https://github.com/AI4Bharat/IndicConformerASR

### 2.2 Vistaar (benchmark + training data)
- [V] Vistaar = 59 benchmarks × 12 languages (Kathbath, Kathbath-Hard, FLEURS, CommonVoice, IndicTTS, MUCS, Gramvaani). IndicWhisper (Whisper-medium 769M per-language fine-tunes) lowest WER on 39/59, avg −4.1 WER (2023 result vs 5-system set).
- [V] Vistaar-train = 13 public datasets, 10,736 h across 12 langs: hi 2150, ta 1625, mr 1513, kn 1077, gu 712, bn 691, or 819, te 806, ml 590 (h). All SIH Indic languages covered. MIT release. Some constituent datasets (IndicTTS, NPTEL) have own access terms.
- [V] PITCH AMMO: on Hindi Vistaar subset, IndicWhisper 13.6 avg WER (Kathbath 10.3) vs Google STT 23.9, Azure 20.0, IndicWav2Vec 21.0. Caveat: in-domain vs zero-shot comparison, mid-2023. Use as "open-source-only restriction costs nothing" evidence, not deployment claim.
- SRC: https://arxiv.org/pdf/2305.15386 · https://github.com/AI4Bharat/vistaar

### 2.3 IndicWav2Vec
- [V] Fine-tuned ASR for 9/10 targets (bn gu hi mr or ta te + separate kn, ml models). MIT (HF ports tagged apache-2.0). Repo dormant since 2022-06; "Mobile — Coming soon"; NO ONNX/TorchScript export code, NO streaming, Flask-only deployment. Android use = your own export pipeline.
- [U] Repo benchmark WERs (verifier votes all errored, numbers read but unconfirmed): hi 16.0→14.7 w/KenLM; bn 16.6→13.6; ta 27.3→25.0 ⇒ LM decoding buys ~1.3–3 WER pts. Re-read README table before citing.
- SRC: https://github.com/AI4Bharat/IndicWav2Vec

### 2.4 Vosk
- [V] Coverage 4–5/10: hi, gu, te, en(-IN); NOTHING for mr kn ml ta or bn(-IN note: alphacep does have a bn streaming model repackaged in sherpa-onnx, see 2.5). vosk-model-small-hi-0.22: 42 MB, WER 20.89 IITM / 24.72 MUCS, Apache-2.0 ⇒ genuinely viable. vosk-model-small-te-0.42: 58 MB, WER 87.9 FLEURS ⇒ unusable (self-reported, against interest). small-en-in ≈ 36 MB. Role: gap filler (esp. Indian English) + proven Android runtime.
- SRC: https://alphacephei.com/vosk/models · https://github.com/alphacep/vosk-api

### 2.5 sherpa-onnx (k2-fsa)
- [V] Best Android deployment runtime (Kotlin/Java API, NDK, streaming + non-streaming, TTS + ASR + VAD in one lib). Official streaming zipformer-transducer catalog covers ONLY bn (repackaged alphacep/vosk-model-small-streaming-bn) + en of our 10. bn model footprint: encoder 87 MB + decoder 2 MB + joiner 1 MB ≈ 90 MB fp32 ⇒ per-language fp32 budget at this class; int8 cuts encoder ~3× (250→68 MB observed on same page) ⇒ int8 mandatory for 10-language app.
- [E] k2-fsa discussion #3199 (2026-02): users asking for Hindi/Tamil streaming models — gap confirmed open as of 2026-02. Neither Dolphin nor Whisper under sherpa-onnx auto-detects language mid-stream ⇒ app must do per-utterance language selection/ID itself.
- SRC: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html · https://huggingface.co/alphacep/vosk-model-small-streaming-bn · https://github.com/k2-fsa/sherpa-onnx/discussions/3199

### 2.6 STT strategy (derived; agent should validate by experiment)
- Primary: export AI4Bharat per-language ~120M Conformer variants (or distill 600M) → ONNX int8 → run CTC branch under sherpa-onnx/ONNX Runtime Mobile. Expected ~30–50 MB/lang int8. NO published quantized-Indic WER exists [open question] — measure on Kathbath/FLEURS slices, publish the table in the pitch (novel contribution).
- English: sherpa-onnx streaming zipformer en (int8) or vosk-small-en-in.
- Fallback per-language if export fails by deadline: hi=Vosk-small-42MB, bn=alphacep streaming zipformer, others=whisper.cpp tiny-int8 (multilingual, non-streaming, weakest accuracy — last resort only).
- On-device language ID: none of the runtimes provides mid-stream LID [E] ⇒ UI language selector per device + optional lightweight LID (e.g., run 2 CTC decoders on first 2 s and compare confidence) = differentiator.

## 3. TTS MODEL MATRIX (angle 2)

### 3.1 AI4Bharat Indic-TTS  ← license-safest full-coverage base
- [V] FastPitch (acoustic) + HiFi-GAN V1 (vocoder), monolingual models, male+female speaker jointly trained (2 voices/lang), 13 languages in README = 9/10 targets. CRITICAL: GitHub release v1-checkpoints-release ALSO contains en.zip (Indian English) and en+hi.zip (Hinglish code-mix) ⇒ practical coverage 10/10 + code-mix voice. Repo has real MIT LICENSE.txt; "We open-source all models" (Bhashini). Caveat: each zip ≈ 1.5 GB TRAINING checkpoints ⇒ team must export to inference graph (Coqui-TTS format → ONNX), prune, quantize; on-Android RTF unmeasured [open question].
- SRC: https://github.com/AI4Bharat/Indic-TTS · https://github.com/AI4Bharat/Indic-TTS/releases/tag/v1-checkpoints-release · ICASSP'23 paper https://arxiv.org/abs/2211.09536
- [E] Paper's ablation selected FastPitch+HiFiGAN-V1 as best architecture combo.

### 3.2 Piper (rhasspy)
- [V] Official piper-voices covers 6/10: hi, bn, mr, ml, te, en. MISSING: gu, kn, ta, or ("ka" dir = Georgian, not Kannada). bn is bn_BD (Bangladesh accent) not bn_IN. Per-voice license inheritance: hi_IN-pratham dataset = CC-BY-NC-SA-4.0 (NOT eligible to ship); mr_IN-google = OpenSLR-64 CC-BY-SA-4.0 (eligible), 22.05 kHz. AUDIT EVERY MODEL_CARD.
- [U] (1 valid vote, 2 errored): all Indic Piper voices exist only at "medium" tier 22,050 Hz, ~63–77 MB ONNX each (hi 63.5, bn_BD 76.8, ml 63.0, mr 76.8, te 63.0); no x_low/low variants ⇒ ~400 MB for 6 langs uncompressed. Re-verify via HF tree API; if true, Piper is NOT the size-efficient path — int8 or retrain x_low needed.
- SRC: https://huggingface.co/rhasspy/piper-voices (+ /api/models/rhasspy/piper-voices/tree/main)

### 3.3 Meta MMS-TTS
- [V] Per-language VITS ~36M params, ALL 10 targets exist (facebook/mms-tts-{hin,ben,tam,tel,kan,mal,mar,guj,ory,eng}); license CC-BY-NC-4.0 on code+weights, fairseq README confirms, no relicense found. NC ⇒ not OSI open source ⇒ treat as DISQUALIFYING for shipping under PS rules; usable only as disclosed offline quality baseline.
- SRC: https://huggingface.co/facebook/mms-tts · https://huggingface.co/facebook/mms-tts-hin · https://huggingface.co/facebook/mms-tts-ory · https://github.com/facebookresearch/fairseq/blob/main/examples/mms/README.md

### 3.4 sherpa-onnx TTS runtime + hidden Indic inventory
- [V] Official VITS docs list 100+ models/40+ langs (none Indic on the page), BUT the GitHub tts-models release (642 assets, ~255 VITS models) DOES contain Indic voices: Piper hi (pratham/priyamvada/rohan), Piper ml (arjun/meera), Coqui+Mimic3 bn, Mimic3 gu. License per voice unaudited [open question].
- [V] RPi4B RTF: vits-icefall-zh-aishell3 0.365 (1 thread)/0.156 (4 thr); Piper-class en 0.79–0.81 (1 thr)/~0.35 (4 thr). Anti-pattern: vits-ljs RTF 6.06 ⇒ model class selection decisive.
- [E] int8 example: VITS VCTK 116 MB → 37 MB.
- SRC: https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html · https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models

### 3.5 TTS strategy (derived)
- Primary path: export Indic-TTS FastPitch+HiFiGAN per language → ONNX → int8 → sherpa-onnx offline-tts API (it supports non-VITS via onnx too; if friction, convert to VITS-style single graph or run via ONNX Runtime directly). 2 voices/lang (M/F) = judge-visible bonus.
- Where license-clean Piper voices exist (mr_IN-google confirmed clean), prefer them for speed of integration; DO NOT ship hi_IN-pratham (NC).
- Gap langs (gu, kn, ta, or): from Indic-TTS checkpoints (they exist there) — this closes the gap Piper can't.
- eSpeak-NG (GPL) = emergency fallback only; robotic quality would tank the 40% accuracy/flow criterion — avoid in demo.
- Text front-end (see §5): normalization decides perceived TTS quality on numbers/dates/alerts.

## 4. TRANSPORT LAYER (angle 3 — all [E], verify by building)

### 4.1 Phone↔phone
- Wi-Fi Direct (P2P): API 14+, no AP/internet, plain TCP sockets (group owner = server, e.g. :8888). Faster + longer range than BT. SRC: https://developer.android.com/develop/connectivity/wifi/wifip2p
- Wi-Fi Aware: API 26+, no AP needed; sendMessage() path ≈ 255 B, unreliable/unordered/dup-possible ⇒ needs app-level ack OR use its TCP-socket-over-IPv6 data path; hardware-optional (check FEATURE_WIFI_AWARE), dies if user disables WiFi/Location ⇒ always keep fallback. SRC: https://developer.android.com/develop/connectivity/wifi/wifi-aware
- Bluetooth Classic RFCOMM/SPP: universal on Android, the standard for ESP32 BluetoothSerial bridges; line-delimited framing works. iOS lacks SPP (irrelevant — PS mandates Android).
- BLE GATT: default ATT MTU 23 B (~20 usable after 3 B ATT header); negotiate up to 512; realistic phone throughput ≈ 12.2 kB/s (185 B MTU, 7 pkts/interval, 15 ms interval); conn interval 7.5 ms–4 s. Use BLE only for the embedded-bridge path or as last-resort phone link. SRC: https://interrupt.memfault.com/blog/ble-throughput-primer
- Google Nearby Connections is closed-source Play-Services ⇒ conflicts with open-source-only spirit; avoid, state why in pitch (differentiator vs naive teams).
- Recommended stack: transport abstraction with priority order WiFi-Aware-socket → WiFi-Direct-TCP → BT-RFCOMM → BLE-GATT, auto-failover, all carrying one wire format (§4.3).

### 4.2 Embedded bridge
- Proven pattern: Meshtastic Android app ↔ ESP32 LoRa node via USB-serial or Bluetooth; WiFi/TCP node connection is ESP32-only. Store-and-forward server needs ESP32+PSRAM (T-Beam/T3S3); Meshtastic S&F has NO per-client delivery tracking ⇒ duplicates possible — our protocol adds msg IDs + acks + dedup (documented gap we fix). LoRa duty cycle: EU-band 10%/h cap (India 865–867 MHz similarly constrained) ⇒ byte budgets are regulatory, not just throughput. SRC: https://meshtastic.org/docs/... 
- ESP32 BluetoothSerial (Arduino): SerialBT.begin("name"), newline framing, UART side 115200 baud ≈ 11.5 kB/s — orders of magnitude more than compressed text needs. SRC: https://randomnerdtutorials.com/esp32-bluetooth-classic-arduino-ide/
- DEMO MOVE: ESP32 emulates a NavIC-receiver/DAT-SG (per §6 architecture precedent) → phone app voices incoming "satellite" alerts.

### 4.3 Wire format + compression
- Unishox2 (siara-cc): Apache-2.0; hybrid entropy+dictionary+delta coding purpose-built for short Unicode strings; full Unicode via UTF-8; stated use cases: text over LoRa/BLE, Arduino/ESP8266-class devices; already integrated in Meshtastic. Good ≤ few kB; bad on large/binary. Ports: C/C++, Python, JS — NO first-party Java/Android binding ⇒ write a small JNI wrapper (cite as engineering contribution). Per-script Indic ratios NOT published in README (live in figshare paper DOI 10.6084/m9.figshare.17056334.v2) ⇒ MEASURE ratios for Devanagari/Bengali/Odia/Dravidian sentences and publish table (novel, quantitative, judge-friendly). SRC: https://github.com/siara-cc/Unishox2
- Note: UTF-8 Indic ≈ 3 B/char; naive Devanagari sentence ~40 chars ≈ 120 B raw. Target: fit common alert sentences ≤ 27 B (one NavIC sub-frame) via Unishox2 + template/code-book for alert classes (CAP-style event codes), free text ≤ 277 B (max NavIC message).
- Frame spec (design): [ver|type|priority|lang|msgId|ackReq|len|payload(Unishox2)|crc]. type∈{ALERT, VOICE_NOTE, CHAT, ACK, PACK_OFFER}; priority mirrors NavIC practice (§6): alerts preempt, dedupe on msgId, store-and-forward queue.

## 5. TEXT FRONT-END (accuracy-40% force multiplier — all [E])

- Kenpath indic-text-normalization: Apache-2.0, WFST/Pynini, numbers/dates/currency/measurements → spoken form; 1–5 ms CPU latency (vs ~500 ms local-LLM, ~2000 ms API); 19 languages incl. hi bn mr te kn ta ml gu + en-IN; ODIA NOT COVERED ⇒ build Odia WFST rules = concrete gap to fill & claim. Positioned as NeMo text-norm extension (Pynini on Android = build friction: consider precompiled FST + lightweight runtime, or port rules to pure-Kotlin FST executor). SRC: https://www.kenpath.io/en/open-source/indic-text-normalization · github.com/kenpath/indic-text-normalization
- Code-mixing is a genuine open gap: Interspeech 2022 (Manghat et al.) — no normalization modules existed for Malayalam-English code-switched text; their 2-stage (script-detect → FST) approach: 87.5% word accuracy; normalized input lifted TTS MOS to 3.82 vs 3.3/3.25 baselines. Abugida complications (inherent vowel, agglutination) require phonetic sub-unit splitting. ⇒ Implement script-mix segmentation: per-token Unicode-range script ID → route EN tokens to en-TTS phonemizer or transliterate → measurable MOS gain; AI4Bharat en+hi.zip Hinglish voice [V] is the shortcut for hi. SRC: https://www.isca-archive.org/interspeech_2022/manghat22_interspeech.pdf
- ASR post-processing for WER wins [U, from unverified IndicWav2Vec table]: KenLM shallow fusion ≈ −1.3…−3 WER. KenLM is LGPL, runs on-device (small n-gram per lang, prune to ~10–20 MB). Also: inverse text normalization for display, punctuation restoration (train tiny punct model or rule-based on pause features from VAD).

## 6. ISRO CONTEXT = THE NON-GENERIC STORY (angle 4 — [E], sources are ISRO/UNOOSA decks + Indian gov secondary)

- NavIC short-message service: message Type 18, broadcast L5 1176.45 MHz + S 2492.028 MHz (via sats 1A/1E); 292-bit sub-frame carries 220 bits payload; total message 220–2220 bits (≈27–277 B) via fragmentation; 50 sps nav data rate. Priority-scheduled, time-shared channel; disaster/distress = highest priority, broadcast by multiple sats. SIS ICD public at isro.gov.in/irnss-programme (fetch ICD for exact bitfields before finalizing frame spec). SRC: https://www.unoosa.org/documents/pdf/icg/2019/icg14/15.pdf · https://www.unoosa.org/documents/pdf/icg/2022/ICG16/wgc-04.pdf
- INCOIS allocations: MsgID 20 = PFZ (9 zones/sub-frame); MsgID 21 = emergency, service IDs: High-Wave 0111, Cyclone 1111, Tsunami 0011. Traffic since 2022-01: 10,853 PFZ msgs, 104 high-wave, 52 cyclone, 325 SG-DAT acks. CAP (Common Alerting Protocol) integration (IMD/CWC/SASE) was in end-to-end testing 2022. ⇒ Model our ALERT class + priority QoS on this real taxonomy; support CAP-style structured alerts that TTS voices in local language.
- NavIC Messaging Receiver (fishermen): embedded receiver + Bluetooth to phone + audio alarm + 5-day battery; companion app gives audio/visual alerts in 13 regional languages. ⇒ ISRO ALREADY deploys receiver→BT→phone→multilingual-audio-alert. iTantra generalizes it to two-way speech. This is the single most alignment-proving fact for the pitch.
- GSAT-6 MSS / Nabhmitra (sub-20 m boats): terminal fwd link 9.6 kbps, return 2.4 kbps, dynamic TDMA, built-in GAGAN/NavIC, Bluetooth/WiFi interface to mobile app; hub ISRO Ahmedabad; 960+ terminals (TN/Gujarat/Puducherry). ⇒ Concrete kbps envelope for "low bitrate links": design so live text streams fit 2.4 kbps return comfortably (they do: even 277 B/msg is trivial; voice codecs at 2.4 kbps sound robotic — text+TTS beats them; cite MELPe/Codec2-class quality vs natural TTS).
- DAT / DAT-SG (fishermen distress): 1st-gen since 2010, >20,000 units, INSAT DRT transponder 402.65–402.67 MHz, six manual one-way message types + position. DAT-SG: two-way (ack + limited short messaging via NavIC), UHF tx + NavIC rx, Bluetooth to fisherman's phone, SAGARMITRA web NMS, operational 24×7 (declared 2024-01). SRC: https://www.drishtiias.com/daily-updates/daily-news-analysis/distress-alert-transmitter-for-fishermen
- GEMINI (INCOIS+AAI, GAGAN): portable receiver, ONE-WAY only (documented gap: no calls), ~300 nmi reach vs 10–12 km cellular, ₹9,000 (90% subsidy planned), payloads = emergency info, Ocean State Forecast, PFZ. ⇒ Gap analysis for pitch: existing ISRO last-mile devices are one-way and text/visual; iTantra adds voice-in/voice-out inclusivity (PS background explicitly cites literacy).
- Background rationale in PS [GT] = inclusivity for non-literate users in distress scenarios. Tie every UX decision to this (voice-first UI, minimal reading, alert auto-playback).

## 7. COMPETITION / PRIOR ART (angle 5 — [E])

- Offline mesh messengers (judges will know): Briar (BT/WiFi-Direct, text), Bridgefy (BLE mesh), BitChat, Meshtastic (LoRa + phone app). ALL are text-only chat; NONE does offline STT/TTS voice loop, none targets Indic languages' speech. Differentiation = speech←→text semantic transcoding + 10-language on-device models + alert QoS; do not compete on mesh routing (out of scope, but store-and-forward relay is cheap to add and fixes Meshtastic's dedup gap).
- sherpa-onnx/k2 community: actively requesting Indic streaming models (discussion #3199, 2026-02) ⇒ publishing our exported int8 Indic ONNX models upstream = open-source contribution judges can verify (and fills a documented ecosystem hole — strong "innovation" story).
- No published prior "iTantra" solution found. PS had 0/500 submissions at fetch (early window).
- Bhashini: AI4Bharat models are its backbone; "open-source all models" statement on Bhashini platform [V via Indic-TTS README]. Aligning with Bhashini = policy-alignment talking point.

## 8. RISK REGISTER (agent: mitigate in this order)

1. Indic-TTS 1.5 GB checkpoints → ONNX export friction (Coqui TTS version pinning, FastPitch export path). Mitigation: start export week 1; fallback = sherpa-onnx tts-models release Indic voices (audit licenses first) or license-clean Piper subset + Indic-TTS for gap langs only.
2. Conformer→ONNX int8 WER degradation unknown (no published numbers [V]). Mitigation: quantize encoder only, keep decoder fp16; measure per-lang on FLEURS slice; if >+3 WER, per-channel quant or QAT-lite.
3. sherpa-onnx offline-tts expects VITS-style graphs; FastPitch+HiFiGAN = 2 graphs. Mitigation: run both via ONNX Runtime Mobile directly (own audio glue), or use piper-style espeak-ng-free phonemizer front-end; budget 1 week.
4. Pynini/WFST on Android build pain. Mitigation: precompile FSTs desktop-side, execute with lightweight OpenFST-lite JNI or reimplement number/date rules for the demo languages in Kotlin.
5. Wi-Fi Aware absent on many low-end phones [E]. Mitigation: transport auto-failover (§4.1); demo primary = Wi-Fi Direct + BT RFCOMM (universally present).
6. RAM on low-end (2–3 GB) phones: load ONE language pair at a time (STT lang A + TTS lang B), mmap ONNX, unload on switch; idle-CPU criterion ⇒ use silero-VAD (tiny, in sherpa-onnx) rather than running ASR continuously — VAD gates the recognizer = directly scores the "CPU during idle listening" metric.
7. License audit gaps: sherpa tts-models Indic assets, Piper voice cards, Vosk model licenses (page lists Apache-2.0 broadly [V for hi]) — complete audit table before submission (template in 03_SUPPLEMENTARY.md).

## 9. OPEN QUESTIONS (from verified run — resolve during build)

1. Real throughput/pairing latency of WiFi-Direct vs Aware vs RFCOMM vs BLE on low-end Android + Unishox2 ratios on short Devanagari/Dravidian sentences — measure, publish table.
2. Exact NavIC byte/bitfield budgets from the SIS ICD (isro.gov.in/irnss-programme) — fetch ICD PDF.
3. Quantized Indic Conformer WER on Kathbath/Vistaar — no public numbers; our measurement = novel contribution.
4. Cheapest license-safe TTS path: AI4Bharat export vs Piper-clean-subset+custom-4 — decide after export spike (Risk 1).

## 10. BUILD PLAN SKELETON (for planning agent)

- M0 (spike, wk1): sherpa-onnx Android demo app running bn streaming ASR + one Piper voice; ONNX export spike for 1 Indic-TTS lang + 1 Conformer-120M lang; measure baseline RTF/WER/RAM on a real low-end phone (target device: 2–4 GB RAM, e.g., ₹8–12k segment).
- M1: STT×3 langs (hi + 2 Dravidian) int8 + VAD gating + KenLM fusion; TTS×3 langs; normalization pipeline (Kenpath + script-mix segmentation).
- M2: transport abstraction (WiFi Direct TCP + RFCOMM) + wire format + Unishox2 JNI + PTT walkie-talkie loop on 2 phones; alert class w/ AudioAttributes USAGE_ALARM + max volume + audio focus non-interruptible; metrics dashboard overlay (live WER vs reference scripts, latency stopwatch, RTF, RAM) — judges see the 40/20/20 numbers live.
- M3: remaining languages as downloadable/side-loadable packs + P2P pack sharing (phone gifts a language pack to another over WiFi Direct — offline-first differentiator); ESP32 NavIC-emulator bridge demo; store-and-forward + dedup; Odia normalizer; polish.
- Deliverables for judges: license audit table, measured model table (size/WER/RTF per lang), NavIC-fit wire-format spec, upstream PR links (sherpa-onnx Indic models), 2-phone + ESP32 live demo.

## 11. SOURCE LEDGER

Primary: hf.co/ai4bharat/indic-conformer-600m-multilingual · github.com/AI4Bharat/{IndicConformerASR, IndicWav2Vec, Indic-TTS, vistaar} · arxiv.org/pdf/2305.15386 · arxiv.org/abs/2211.09536 · alphacephei.com/vosk/models · k2-fsa.github.io/sherpa (online-transducer + vits pages) · github.com/k2-fsa/sherpa-onnx (discussions/3199, releases/tts-models) · hf.co/rhasspy/piper-voices · hf.co/facebook/mms-tts{,-hin,-ory} · fairseq MMS README · github.com/siara-cc/Unishox2 · developer.android.com (wifip2p, wifi-aware) · meshtastic.org docs (initial-config, store-and-forward) · unoosa.org ICG decks (2019 icg14/15.pdf, 2022 ICG16/wgc-04.pdf) · isca-archive.org manghat22_interspeech.pdf · kenpath.io indic-text-normalization.
Secondary: drishtiias.com (DAT-SG 2024-01-23, GEMINI 2019-10-10) · randomnerdtutorials.com (ESP32 BT Classic) · interrupt.memfault.com (BLE throughput primer, 2019).
Ground truth: sih.gov.in/sih2026PS HTML snapshot 2026-09-05 (local: scratchpad/sih2026ps.html; extract: PS_SIH26173_OFFICIAL.md).
Verification stats: 24 sources fetched, 115 claims extracted, 25 verified (23 confirmed 3-0/2-0/2-1, 0 refuted, 2 unverified-by-error), 106 agents, ~5.07M tokens.
