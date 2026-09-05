# SIH26173 — Official Problem Statement (verbatim from sih.gov.in, fetched 2026-09-05)

| Field | Value |
|---|---|
| Problem Statement ID | 26173 (SIH26173) |
| Title | iTantra -Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links |
| Organization | Indian Space Research Organisation (ISRO) |
| Department | Department of Space / Indian Space Research Organisation |
| Category | Software |
| Theme | Smart Automation |
| Dataset Link | (none provided) |
| Submissions at fetch time | 0/500 |
| Deadline shown on portal | 30 September 2026 |

## Description (verbatim)

**Background** As vocal audio information is very data intensive making it difficult to transmit through low data rate links. In alert and distress based scenarios Transmitting Audio information is critical instead of written message as it will be more inclusive and will cater to everyone even if they are literate or not.

**Description** Build an Android App with lightweight, highly accurate STT and TTS models for 10 Indian Languages (Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English) that runs locally on a low-power device. The system's STT module when activated after detecting pauses and stoppages should form the sentences detected and must instantly and efficiently stream the data through wifi/Bluetooth connected embedded device or another phone with same application with minimal latency. The systems TTS module when activated after receiving the Text data should convert it into intelligible speech which will be played as a voice note and alert type messages will be announced at highest volume non-interruptible. To verify the complete loop two phones with same app one in TTS mode and another in STT mode can be connected via wifi or Bluetooth and it should work like a walkie talkie using push to talk feature, if turned off it should work like a phone.

**Key Metrics for Evaluation**

- Efficiency: Model size, App size (RAM/Flash footprint) and CPU usage during idle listening. **(20%)**
- Accuracy: Low Word Error Rate for STT and High human legibility and flow for TTS. **(40%)**
- Latency: The Time delay between the Words said and STT completion, Time delay between the text received and audio processed and played for TTS along with RTF (Real Time Factor). The time delta between the sentence said and the same sentence started as audio in another phone. **(20%)**

**Software & Framework Restrictions**

- Open-Source Only: The use of proprietary, closed-source, or commercial voice-activation SDKs is strictly prohibited.
- Allowed Frameworks: Teams must build their pipelines using open-source machine learning and TinyML frameworks. Recommended tools include TensorFlow Lite for Microcontrollers, PyTorch Mobile or similar.
- Fully Offline Working: Model or pipeline should work fully offline only and no internet hosted API based solutions are expected and encouraged for the STT or TTS.

**Expected Solution** Teams are expected to deliver a robust, deployable system architecture. A successful submission must strictly satisfy the following technical boundaries:

- Hardware & Runtime Environment: The Android application must run smoothly on Low and Mid range mobile phones.

## Notes (not part of official text)

- The stated metric weights sum to 80% (20+40+20); the remaining 20% is unstated — plausibly presentation/innovation/completeness judged at hackathon.
- Sibling ISRO PS SIH26172 is "Low Latency and Efficient Voice Activator for Edge Devices" (keyword spotting on MCU) — same ISRO team likely authored both; iTantra is the phone-side STT/TTS half of a low-bitrate voice-messaging chain.
- "Neural Transceiver Radio Access" framing = semantic communication: send recognized TEXT over the low-bitrate link instead of voice audio, re-synthesize speech at the receiver.
