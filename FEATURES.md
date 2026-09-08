# iTantra — features

What the app does, how each part works, and how to demonstrate it.

For migrating from an older checkout see [`HANDOFF.md`](HANDOFF.md). For the reasoning
behind design decisions see [`docs/ARCHITECTURE_NOTES.md`](docs/ARCHITECTURE_NOTES.md).

---

## The idea in one picture

Disaster links — NavIC satellite messaging, LoRa mesh, distress beacons — carry tens to
hundreds of bytes. Voice does not fit. But voice is the inclusive medium: it works for
people who cannot read.

iTantra moves the codec off the link and onto the handsets. Only text crosses the wire.

```
Phone A                                             Phone B
────────────────────────                            ────────────────────────
 hold to talk
 16 kHz mono PCM
      ↓
 speech → text          ─── ~40-90 bytes of UTF-8 ──▶  text → speech
 translate                    over Wi-Fi Direct        spoken aloud
 + GNSS position                                       + sender's position,
                                                         distance and bearing
```

---

## 1. Push-to-talk transceiver

Hold the button, speak, release. The handset records 16 kHz mono PCM, recognises it,
translates it, transmits the **text**, and speaks it on the far end.

- Presses under 300 ms are discarded as accidental taps.
- Captures are capped at 30 s.
- The button is red while transmitting, green while speaking a received message, and the
  ring around it tracks live input level so you can see the microphone is hearing you.

**Demo:** hold, say a sentence, release. The transcript appears under `HEARD`, the
translation under `SPEAKING`, and the phone speaks it.

---

## 2. Three speech engines, switchable in the app

| | Offline (on-device) | ElevenLabs | Sarvam Cloud |
|---|---|---|---|
| Recognition | platform `SpeechRecognizer`, `EXTRA_PREFER_OFFLINE` | Scribe `scribe_v2`, 90+ languages | Saaras v3 |
| Translation | **pass-through** | **via Sarvam** (see below) | Mayura v1 |
| Synthesis | platform `TextToSpeech` → WAV | Flash v2.5 (~75 ms) / v3 | Bulbul v3 |
| Needs network | No | Yes | Yes |
| Needs an offline voice pack | Yes | No | No |

All three sit behind one `SpeechPipeline` interface, so the state machine never learns
which is running. The active engine is shown at the top-right of the screen.

**Switching:** tap the engine name → paste a key → **SAVE**. No rebuild. Keys are stored
app-privately on that device and are not in the APK.

**Precedence:** force-offline → ElevenLabs (if keyed) → Sarvam (if keyed) → offline.

### ElevenLabs specifics

- **Recognition** uses `scribe_v2`, which covers 90+ languages — all ten of ours.
- **Synthesis** picks the fastest model that supports the target language:
  `eleven_flash_v2_5` at **~75 ms** for English, Hindi, Bengali, Tamil, Telugu, Kannada and
  Malayalam; `eleven_v3` (70+ languages) for Marathi, Gujarati and Odia. Latency is scored
  at 20% in the brief, so the fast model is preferred wherever it works rather than
  defaulting to the broad one.
- **Audio format** is requested as `wav_16000` — 16 kHz mono WAV, matching the app's own
  capture rate and arriving with a RIFF header, so it flows straight into the player with
  no transcoding and no base64 hop.
- **Voice selection** is not hardcoded. On first use the app calls `GET /v2/voices` and
  adopts the first voice on your account, so it works with any account. Override it by
  setting a voice id in settings.
- **Language codes** are sent as ISO 639-1 (`hi`), not BCP-47 (`hi-IN`), which is what the
  API expects.

> **ElevenLabs cannot translate text.** They expose no text-translation endpoint — their
> only translation lives inside the *Dubbing* project API, which is asynchronous and
> job-based (create a project, add target languages, poll for completion) and cannot serve
> a push-to-talk loop scored on latency.
>
> So translation is a separate `Translator` in this app. Add a **Sarvam key alongside**
> the ElevenLabs one and ElevenLabs handles recognition and voice while Sarvam handles
> translation.
>
> With no Sarvam key the message is **relayed in the language it was spoken** — the
> transceiver still works, it just does not translate. The output is labelled
> `RELAYED · HINDI · NOT TRANSLATED` rather than claiming English, and the far handset
> synthesises in the source language so the words are pronounced correctly.

**Force offline:** the same sheet has a switch that pins the offline engine. For a
provably air-gapped build, set `itantra.force.offline=true` in `local.properties` — then
the cloud client is never constructed at all.

> Which engine leads is decided by **configuration**, not by probing. Android's
> `isRecognitionAvailable()` returns true on any phone with a recognition service, even
> when no offline pack is installed for your language, so probing always picked offline
> and then failed mid-utterance.

---

## 3. Sender position, fully offline

Every transmission can carry the sender's coordinates, so the receiver knows **where they
are** — the thing that matters most in a distress call.

### Why this is genuinely offline

A GNSS fix is computed on the handset from signals broadcast by the satellites themselves.
No network, SIM or data connection is involved. The app requests `GPS_PROVIDER`
explicitly rather than going through the fused provider, which would silently fall back to
network trilateration.

What *would* need a network — reverse geocoding (coordinates → street address) and map
tiles — is deliberately not used. You get raw coordinates plus a bearing, which is what a
rescue coordinator actually needs and which works with the radio off.

### What the receiver sees

```
SENDER POSITION · 1.2 km NE AWAY
19.07598°N, 72.87766°E
±12 m · tap to copy
```

Distance and compass bearing are computed on-device from the two fixes with the Haversine
formula — no map data, no network. Tapping copies the coordinates.

### Byte cost

The brief scores efficiency, so position is encoded carefully:

- Coordinates are **fixed-point integers** (degrees × 1e5) under two-letter keys:
  `"la":1907598` rather than `"latitude":19.0759837`. 1e5 resolves ~1.1 m, far inside GNSS
  error, so nothing useful is lost.
- Absent fields are **omitted entirely**, not serialised as `null`.
- Attaching a position costs **under 40 bytes**. A full alert with coordinates still fits
  a 277-byte NavIC packet. Both are asserted in `LocationPayloadTest`, not assumed.

### Controls

`GPS ON` / `GPS…` / `GPS OFF` at the bottom of the screen. `GPS…` means sharing is on but
no fix has been acquired yet. Tap to toggle — with sharing off, no coordinates are
transmitted and those bytes leave the link entirely.

A stale sender position is cleared the moment you start a new transmission: leaving the
previous sender's coordinates on screen would be actively dangerous.

**Demo on an emulator:**

```bash
adb emu geo fix 72.8776559 19.0759837     # note: longitude first
```

The indicator turns green. On a phone, take it outdoors — the first fix needs sky view.

---

## 4. Emergency alert mode

Tap **EMERGENCY** to arm. Everything transmitted while armed is flagged `ALERT`, and the
receiving handset announces it through `USAGE_ALARM` with the alarm stream forced to
maximum — so a phone with its media volume turned down still sounds a cyclone warning.

This is the SIH26173 requirement that alerts be "announced at highest volume
non-interruptible".

**Demo:** arm it on phone A, transmit; phone B announces at full volume regardless of its
own volume setting.

---

## 5. Wi-Fi Direct link

Peer discovery and pairing over `WifiP2pManager`, then a persistent length-prefixed TCP
socket. No router, no internet — the two handsets form their own network.

**Demo:** tap `NO LINK · TAP TO PAIR` → **SCAN** → pick the other phone → accept the
invitation on that phone. Both read `LINKED`.

> **Untested on two physical handsets.** An emulator cannot do Wi-Fi P2P, so the framing
> and TCP link are only proven over loopback. Try it before you present, and keep
> single-phone mode as a fallback — the record → recognise → translate → speak loop works
> fine on one device.

---

## 6. Ten Indian languages

Hindi · Bengali · Tamil · Telugu · Kannada · Malayalam · Marathi · Gujarati · Odia ·
English (Indian)

Each is shown in its own script. Source and target are independent, with a swap control
between them. The target language travels with the message, so the receiver synthesises in
the right language without guessing.

---

## 7. Latency telemetry

A per-stage readout appears once there is something to measure:

```
STT      TRANS     TTS      LINK     TOTAL
412ms    180ms     329ms    24ms     945ms
```

The brief scores latency at 20%, so the numbers stay on screen rather than hiding in a
debug menu. `LINK` is measured locally on the sender — deriving it from the sender's
timestamp would measure clock skew between two handsets, not transit time.

---

## 8. Interface

Two colours, one typeface, no elevation. Red and green are state, not decoration:

- **Red** — transmitting, armed, emergency alert
- **Green** — linked to a peer, received message, GPS fix acquired

Everything else is a neutral ramp. Dynamic colour is deliberately off: on a
disaster-response tool the meaning of red and green must not change with the wallpaper.
Light and dark themes both supported.

---

## Verified

```
unit           258 tests  0 failures  [PASS]
instrumented    10 tests  0 failures  [PASS]
lint clean
```

`./run-tests.sh --device` runs everything headless, booting an emulator if none is
attached. No test needs a microphone, a speaker, a network or a second handset — fakes are
injected at the `NativeAudioRecord`, `NativeMediaPlayer`, `SpeechPipeline`,
`TransportEngine` and `LocationProvider` seams. The ElevenLabs client's actual outgoing requests — endpoint,
auth header, model ids, output format — are asserted against a capturing interceptor,
because a wrong endpoint fails identically to a network outage at runtime.

---

## Limits — know these before demoing

| | |
|---|---|
| **Translation needs Sarvam** | Neither the offline engine nor ElevenLabs can translate text. The offline engine bundles no Indic translation model; ElevenLabs has no text-translation endpoint. Cross-language translation requires a Sarvam key, which can sit alongside an ElevenLabs key. |
| **Two-phone pairing** | Never run on real hardware (see §5). |
| **Offline voice packs** | The offline engine needs one installed for your language. Many phones ship without Hindi. If you have none, use a Sarvam key. |
| **No VAD** | Push-to-talk only. The brief also describes STT activating "after detecting pauses and stoppages"; that is not implemented. |
| **Bluetooth** | `TransportEngine` is shaped for RFCOMM but only Wi-Fi Direct is implemented. |
| **GNSS first fix** | Can take a minute outdoors and may never arrive indoors. The app transmits without a position rather than blocking. |

---

## Against the problem statement

| Requirement | Status |
|---|---|
| 10 Indian languages | Done |
| On-device STT | Done |
| On-device TTS | Done |
| PTT transceiver over Wi-Fi | Done — untested on two handsets |
| Text-sized payload | Done — ~40–90 bytes including position |
| Alerts at max volume, non-interruptible | Done |
| Latency + RTF telemetry | Done |
| Fully offline, no cloud API | Done — `itantra.force.offline=true` makes it provable |
| Sender location | Done — GNSS, no network |
| On-device Indic translation | **Not done** |
| VAD / sentence segmentation | **Not done** |
| Bluetooth RFCOMM | **Not done** |
