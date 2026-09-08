# iTantra — Offline Multilingual Voice Transceiver (SIH26173)

Android push-to-talk transceiver for ISRO problem statement **SIH26173**. It carries
speech across a low-bitrate link by sending *text* instead of audio: speech is recognised
on the handset, the recognised text is transmitted as a few dozen bytes over Wi-Fi Direct,
and the receiving handset speaks it aloud.

| | |
|---|---|
| Platform | Android 8.0+ (minSdk 26, targetSdk 35) |
| Language | Kotlin 2.3, Jetpack Compose |
| Build | Gradle 9.1, AGP 9.0.1, JDK 17 toolchain |
| Tests | 255 unit, 10 instrumented |

> **What can it do?** [`FEATURES.md`](FEATURES.md) — every feature, how it works, and how
> to demo it.
> **Migrating from an older checkout?** [`HANDOFF.md`](HANDOFF.md) — where to pull the new
> code from, the breaking API changes, and how to run the tests.

---

## The problem

Disaster-relief links — NavIC satellite messaging, distress transmitters, LoRa mesh —
carry payloads measured in tens to hundreds of bytes. Streaming voice across them is not
possible. But in an emergency, voice is the inclusive medium: it works for people who
cannot read.

iTantra resolves that by moving the audio codec off the link and onto the handsets. Only
text crosses the wire.

```
Phone A                                            Phone B
─────────────────────────                          ─────────────────────────
  hold to talk
  16 kHz mono PCM
       ↓
  speech → text        ──── ~40 bytes of UTF-8 ───▶   text → speech
  translate                  over Wi-Fi Direct        spoken aloud
```

## Supported languages

Hindi · Bengali · Tamil · Telugu · Kannada · Malayalam · Marathi · Gujarati · Odia · English (Indian)

---

## Architecture

```
app/src/main/java/com/itantra/voice/
├── MainActivity.kt              assembles the pipeline and transport
├── audio/
│   ├── AudioRecorder.kt         16 kHz mono PCM capture
│   ├── WavEncoder.kt            PCM → canonical 44-byte RIFF/WAVE
│   └── AudioPlayer.kt           playback, incl. alarm-stream routing for alerts
├── pipeline/                    ← speech engines, swappable
│   ├── SpeechPipeline.kt        STT → translate → TTS contract
│   ├── OnDeviceSpeechPipeline   fully offline (default)
│   ├── SarvamSpeechPipeline     Sarvam AI cloud (opt-in)
│   ├── FallbackSpeechPipeline   resolves which engine serves an utterance
│   └── recognition/             PlatformSpeechRecognizer (offline STT)
├── transport/
│   ├── wifidirect/              WifiP2pManager discovery and group formation
│   ├── socket/                  persistent framed TCP link
│   └── framing/                 length-prefixed wire frames
└── ui/                          single Compose screen + components
```

### The speech pipeline is pluggable

`SpeechPipeline` decouples the state machine from any particular engine, so the same
push-to-talk flow drives either implementation:

- **`OnDeviceSpeechPipeline` (default).** Platform `SpeechRecognizer` with
  `EXTRA_PREFER_OFFLINE`, plus the platform `TextToSpeech` engine rendering to WAV. No
  network, no API key. This is the configuration SIH26173 mandates.
- **`ElevenLabsSpeechPipeline` (opt-in).** Scribe `scribe_v2` for recognition (90+
  languages) and Flash v2.5 / v3 for voice, returning 16 kHz WAV. ElevenLabs has no
  text-translation endpoint, so translation is delegated to a separate `Translator`.
- **`SarvamSpeechPipeline` (opt-in).** Sarvam AI's Saaras/Mayura/Bulbul APIs. Also supplies
  `SarvamTranslator`, which is what translates for the ElevenLabs pipeline.

Cloud engines require a key and a network, which the problem statement does not permit for
a submission — hence offline by default.

`FallbackSpeechPipeline` picks between them, and the choice comes from **configuration**
rather than a runtime probe — `SpeechRecognizer.isRecognitionAvailable()` is true on any
phone with a recognition service even when no offline language pack is installed, so
probing would always pick the offline engine and then fail mid-utterance.

The choice is settled in `prepare()` **before** capture begins, not per call: the offline
engine captures through its own recogniser session while the cloud engine expects a
recorded WAV, so switching mid-utterance would leave the audio captured by nobody.

> **Offline translation is a pass-through.** No open-source on-device Indic translation
> model is bundled, so the offline pipeline relays recognised text verbatim rather than
> failing the utterance. Same-language transceiver operation is unaffected; cross-language
> translation currently requires the Sarvam pipeline. This is the one part of the brief
> the offline path does not yet satisfy.

### Emergency alerts

The brief requires that "alert type messages will be announced at highest volume
non-interruptible". Arming **EMERGENCY** flags outgoing frames as `ALERT`; on the receiving
handset those play through `USAGE_ALARM` with the alarm stream raised to maximum, so a
turned-down media volume cannot silence a warning.

---

## Building

### Prerequisites

- JDK 21 to run Gradle, JDK 17 for the compile toolchain
- Android SDK with platform 36 and build-tools 36

```bash
brew install --cask android-commandlinetools
brew install openjdk@17 openjdk@21
```

Point Gradle at the toolchain once, in `~/.gradle/gradle.properties`:

```properties
org.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

### Configure

`local.properties` is git-ignored. Create it:

```properties
sdk.dir=/Users/you/Library/Android/sdk

# ElevenLabs: speech recognition and voice. Preferred cloud engine.
elevenlabs.api.key=

# Sarvam: supplies TRANSLATION even when ElevenLabs is the speech engine, because
# ElevenLabs has no text-translation endpoint. Also works as a full engine on its own.
sarvam.api.key=

# Set true to pin the offline engine even if a key is present.
# Use this for the SIH26173 demo: the cloud client is never constructed.
itantra.force.offline=false
```

Which engine runs:

| Keys set | `itantra.force.offline` | Engine | Offline pack needed? | Network needed? |
|---|---|---|---|---|
| ElevenLabs | `false` | ElevenLabs | No | Yes |
| Sarvam only | `false` | Sarvam Cloud | No | Yes |
| both | `false` | ElevenLabs for speech, Sarvam for translation | No | Yes |
| none | `false` | On-device | Yes | No |
| any | `true` | On-device, pinned | Yes | No |

The active engine is shown in the status line at the top of the screen.

> The on-device engine needs an offline voice pack installed on the device
> (Settings → System → Languages & input → Voice input → Offline speech recognition).
> Without one it reports *"That language pack is not installed for offline recognition."*
> If you do not have a pack, set a `sarvam.api.key` and the cloud engine is used instead.

### Build and run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Testing

Everything runs headless — no human interaction, no physical device.

```bash
./run-tests.sh            # unit tests + lint + APK
./run-tests.sh --device   # also boots an emulator and runs the UI tests
```

| Suite | Count | Covers |
|---|---|---|
| Unit (`app/src/test`) | 255 | WAV encoding, capture lifecycle, playback routing, the 7-state FSM, wire framing, TCP link, engine selection, HTTP error mapping |
| Instrumented (`app/src/androidTest`) | 10 | Real Compose tree on an emulator: gestures, transcript rendering, language swap, emergency arming, permission gating |

Both suites inject fakes at the `NativeAudioRecord` / `NativeMediaPlayer` / `SpeechPipeline`
seams, so no test needs a microphone, a speaker, a network, or a second handset.

### Verifying on an emulator

An emulator image without Google Play cannot download offline voice packs, so recognition
reports *"That language pack is not installed for offline recognition."* — this is the app
correctly surfacing an environment limitation, not a failure. Capture, transport,
translation, playback routing and the whole UI are still exercised by the test suites.
End-to-end recognition needs a device with an offline voice pack installed
(Settings → System → Languages & input → Voice input → offline speech recognition).

---

## Design

Two colours, one typeface, no elevation. Red and green are the transceiver's state
language rather than decoration:

- **Red** — transmitting, armed, or an emergency alert
- **Green** — linked to a peer, or a received message

Everything else is a neutral ramp, so those two never get diluted. The talk button is the
only large target on the screen; the transcript takes the flexible space and scrolls
internally so the button stays anchored on every screen size.

---

## Status against the problem statement

| Requirement | Status |
|---|---|
| 10 Indian languages | Done |
| On-device STT | Done — platform recogniser, offline-preferring |
| On-device TTS | Done — platform engine, rendered to WAV |
| Push-to-talk transceiver over Wi-Fi | Done — Wi-Fi Direct + persistent framed TCP |
| Text-sized payload over the link | Done — UTF-8 text, typically < 100 bytes |
| Alerts at max volume, non-interruptible | Done — `USAGE_ALARM`, alarm stream forced to max |
| Sender location attached to messages | Done — GNSS, fully offline, < 40 bytes |
| Latency telemetry | Done — per-stage readout on screen |
| Fully offline, no cloud API | Done by default; cloud path exists but is opt-in |
| On-device translation between Indic languages | **Not done** — offline path relays text verbatim; ElevenLabs has no text-translation API either, so translation needs a Sarvam key |
| VAD / automatic sentence segmentation | **Not done** — capture is push-to-talk only |
| Bluetooth RFCOMM transport | **Not done** — `TransportEngine` is ready for it; only Wi-Fi Direct is implemented |

## Licence

MIT.
