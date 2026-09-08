# Architecture notes

Design decisions that are not obvious from the code, and the reasoning behind them.

## Why the speech pipeline is an interface

The app originally called `SarvamApiClient` directly from `MainViewModel`. That made the
transceiver unusable without a cloud API key, and put it in direct conflict with the
SIH26173 requirement for a fully offline pipeline.

`SpeechPipeline` now expresses the three stages (recognise → translate → synthesise) with
no reference to any engine. `MainViewModel` drives the state machine against that contract
and never learns which engine served an utterance.

## Why engine selection happens before capture, not per call

The two engines capture audio in incompatible ways:

- `OnDeviceSpeechPipeline` uses Android's `SpeechRecognizer`, which **owns the microphone**
  for the duration of a session and streams its own audio.
- `SarvamSpeechPipeline` uploads a **recorded WAV** produced by `AudioRecorder`.

Only one of them may hold the microphone; running `AudioRecord` alongside a recogniser
session makes one of the two receive silence. So `SpeechPipeline` exposes
`capturesOwnAudio`, and `MainViewModel` starts exactly one capture source.

That flag has to be answerable *synchronously* the instant push-to-talk is pressed, but
availability checks are suspending. `FallbackSpeechPipeline` therefore resolves the active
engine in `prepare()` and caches it. Choosing per call would let the engine change between
the press and the release, stranding the utterance with nobody having captured it.

## Why network latency is not derived from the sender's timestamp

`TransportMessage` carries a `timestamp`, and an earlier version computed link latency as
`now - message.timestamp` on the receiver. Two handsets have independent clocks, so that
expression measures clock skew, not transit time — it can be negative, or wildly inflated.
Only locally-measured send duration is reported.

## Why the incoming-message flow has no replay buffer

`PersistentTcpSocketManager.incomingMessages` was a `MutableSharedFlow(replay = 1)`. A
replay buffer re-delivers the last message to every *new* collector, so the receiving
handset spoke the previous transmission again on every reconnect and on every ViewModel
rebind. It is now `replay = 0`, and the flow is typed as `SharedFlow` so callers that need
to guarantee they are attached before a send can observe subscription.

## Why the group owner accepts in a loop

`ServerSocket.accept()` was called once. After the first peer disconnected the group owner
was deaf and the app had to be restarted. It now accepts, serves one peer, and returns to
accepting when that peer drops.

Relatedly, `ServerSocket(port)` binds in the constructor, so the subsequent
`reuseAddress = true` had no effect and re-listening inside `TIME_WAIT` failed with
"Address already in use". The socket is now constructed unbound, configured, then bound.

Peer teardown (`closePeerSocket`) is deliberately separate from full shutdown
(`closeAll`): the former must not cancel the accept job, because that is the coroutine
waiting to re-accept the peer.

## Why capture drains inside the recording coroutine

`stopRecording()` used to stop the record, drain the hardware buffer on the caller's
thread, and only then cancel the streaming coroutine — so two threads called
`AudioRecord.read()` concurrently and split the tail of the utterance between them. That
was the source of the intermittent truncated audio and spurious "No speech detected".

The capture coroutine is now the sole reader; it drains in its own `finally`, and
`stopRecording()` is suspending so it can await that without blocking the main thread.

## Why alerts use a separate audio route

SIH26173 requires alert messages to be "announced at highest volume non-interruptible".
Ordinary speech plays on `USAGE_MEDIA`. Alerts play on `USAGE_ALARM` with `STREAM_ALARM`
forced to maximum, so a handset with its media volume turned down still announces a
cyclone warning audibly.

## Known gaps

- **On-device translation.** No open-source Indic translation model is bundled, so the
  offline pipeline relays recognised text verbatim. Cross-language translation currently
  requires the Sarvam pipeline. This is the remaining gap against the brief.
- **VAD.** The brief describes STT activating "after detecting pauses and stoppages".
  Capture is push-to-talk only; there is no voice-activity segmentation.
- **Bluetooth RFCOMM.** `TransportEngine` and `TransportType` are shaped for it, but only
  Wi-Fi Direct is implemented.
