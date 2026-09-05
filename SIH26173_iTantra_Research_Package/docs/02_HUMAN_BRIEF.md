# iTantra (SIH26173) — What It Is & What We'll Build

*A plain-language guide. No jargon without explanation.*

## The problem, in one paragraph

ISRO wants an **Android app that works like a walkie-talkie — but instead of sending your voice, it sends your words as text**. You press a button and speak in any of 10 Indian languages (Hindi, Bengali, Tamil, Telugu, Kannada, Malayalam, Marathi, Gujarati, Odia, or English). The app converts your speech to text **on the phone itself** (no internet needed), sends that tiny text message over WiFi or Bluetooth to another phone or a small radio device, and the receiving phone **speaks it out loud** in a natural voice. Emergency alerts play at full volume and cannot be silenced.

## Why would anyone want this?

Voice audio is heavy — a few seconds of speech is tens of kilobytes. A sentence as text is under 100 bytes: **hundreds of times smaller**. Over satellite links, disaster-zone radios, or long-range LoRa radios, there isn't enough bandwidth for voice calls — but text always fits. And because the receiving phone *speaks* the message aloud, even someone who cannot read can use it. That's exactly ISRO's world: their NavIC satellites already broadcast cyclone and tsunami alerts to fishermen's devices as short text messages of at most ~277 bytes — and ISRO's existing fishermen receivers already forward alerts to phones over Bluetooth. This app is the natural next step: **two-way voice conversation squeezed through a text-sized pipe**.

## What the judges score

- **40% Accuracy** — does the app hear you correctly (low word-error rate), and does the voice it speaks sound natural?
- **20% Efficiency** — small models, small app, low battery/CPU drain while listening.
- **20% Latency** — how fast speech becomes text, and text becomes speech at the other end.
- Everything must be **open source** and work **fully offline**. Cloud APIs (Google, Azure, etc.) are banned.

## Why a generic solution loses

Most teams will grab one famous model (like Whisper) and one voice engine and call it done. Our research proves that fails:

- **No single open model covers all 10 languages** for speech-to-text or text-to-speech on a phone. Every off-the-shelf option has holes (e.g., Vosk's Telugu model gets 88% of words wrong; Piper has no Gujarati, Kannada, Tamil, or Odia voices).
- **Licenses are a trap.** Meta's MMS voices cover all 10 languages — but their license forbids commercial use, which breaks the "open source only" rule. Even some "open" voice files secretly inherit non-commercial licenses from their training data.
- The best Indian-language models (from **AI4Bharat**, IIT Madras — MIT licensed, free) are the most accurate in the world for these languages — they even beat Google's and Azure's paid cloud services on Hindi tests — but **nobody has ever put them on a phone**. They ship as huge research files with "Mobile: coming soon" in the docs since 2022.

## Our solution

**We do the work nobody has done: shrink India's best open speech models until they fit on a ₹10,000 phone, and wrap them in a radio-smart messaging app.**

1. **Speech-to-text:** take AI4Bharat's per-language models, compress them ~3–4× (a technique called int8 quantization), and run them with sherpa-onnx — an open-source engine built for phones. English is filled in with proven lightweight models.
2. **Text-to-speech:** AI4Bharat's Indic-TTS voices (male + female per language, MIT licensed — including a Hinglish voice for mixed Hindi-English sentences), exported and compressed the same way. Speed is proven: similar models already speak 2–3× faster than real time on a Raspberry Pi, which is weaker than a mid-range phone.
3. **Smart text cleanup:** before speaking, the app converts "₹1,500" into "one thousand five hundred rupees" in the right language, and handles sentences that mix English words into Indian languages — a known weak spot of every existing system.
4. **The link:** phones connect by WiFi Direct or Bluetooth automatically (whichever works), and messages are squeezed with Unishox2 — a compressor built for short Indian-script text, the same one used in LoRa mesh radios. Alert messages get priority, play at maximum volume, and can't be interrupted — mirroring how ISRO's real NavIC alert system ranks tsunami/cyclone warnings.
5. **The ISRO bridge:** we'll demo a small ESP32 radio module acting like an ISRO satellite receiver, pushing an alert into the app — which announces it aloud in the local language. Our message format is designed to fit inside a single NavIC satellite message frame (~27 bytes for common alerts).
6. **Proof on screen:** the app shows live accuracy, latency, and speed numbers during the demo — the exact three things judges score.

## Why this wins

It's not "an app that uses AI" — it's a **missing piece of ISRO's real, deployed alert ecosystem** (NavIC messaging, fishermen's distress transmitters, GEMINI receivers — all one-way and text-only today), built by doing genuinely new engineering (first mobile port of AI4Bharat models, measured and published), with every license checked and every claim backed by a number.

*Full technical details and sources: see [01_RESEARCH_FOR_AGENT.md](01_RESEARCH_FOR_AGENT.md). Official problem text: [PS_SIH26173_OFFICIAL.md](PS_SIH26173_OFFICIAL.md).*
