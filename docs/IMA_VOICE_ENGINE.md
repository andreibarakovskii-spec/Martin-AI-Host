# IMA Voice Engine — generative expressive voice plan

Updated: 2026-09-04

## Product decision

Irina/Piper is **fallback only**. It remains the proven local cross-device safety voice while imagination develops its own primary expressive speech engine.

The target is not a conventional fixed TTS voice with speed/pitch tweaks. IMA Voice should behave more like a generative audio model in expressiveness: semantic phrasing, changing intonation, emotional contour, energy, timing, breaths/pauses and eventually controlled non-verbal vocalizations — while preserving exact spoken content far more reliably than a music generator.

"Suno-like" means the product quality bar for expressiveness/personal voice identity, not copying Suno code, weights, branding or music-generation architecture.

## Primary architecture

`LLM stream -> semantic chunker -> prosody/emotion planner -> text normalization -> phoneme/content representation -> speaker profile -> expressive speech generator -> vocoder/codec -> incremental PCM -> AudioTrack`

The engine must expose one backend-neutral contract to Companion Core:

- `beginResponse(style/emotion/energy)`
- `appendResponse(text, style/emotion/energy)`
- `finishResponse()`
- `cancelResponse()`
- streaming PCM callback
- optional timing/viseme callback
- telemetry: first-PCM, RTF, RAM, CPU, thermals, cancellation latency

## Expression model

Do not infer emotion only from punctuation. Conversation Director / semantic planner supplies a bounded expressive state.

Initial dimensions:
- emotion: neutral, warm, calm, empathetic, happy, playful, excited, curious, confident, concerned;
- energy: 0..1;
- pace: slow..fast;
- emphasis map over words/phrases;
- pause strength / phrase boundaries;
- sentence-final contour;
- certainty / softness;
- optional breath/laugh/sigh events only when semantically appropriate.

The speech model may smooth these controls over time so a response sounds like one continuous human turn rather than unrelated synthesized sentences.

## Voice identity

Separate voice identity from memory and personality:

`IMA Voice Base + speaker profile + expressive state -> audio`

Required future capabilities:
- built-in IMA voices;
- replaceable speaker embeddings/profiles;
- Personal Voice from short clean samples where licensing/quality permits;
- encrypted local speaker profile;
- preview/delete/revoke;
- same voice identity across supported Android vendors;
- no dependency on vendor/system TTS.

## Streaming and latency

The engine must be designed for conversation, not offline rendering.

Targets on supported ARM64 hardware:
- first audible PCM: ideally 300–500 ms after a speakable fragment;
- synthesis must stream incrementally;
- cancellation should happen at very small boundaries;
- next fragment must append to the current response without cancelling already queued speech;
- pre-arm model/runtime while the user is speaking;
- preserve a stable AudioTrack path and Bluetooth routing.

Latency measurements must separate:
1. LLM first speakable text;
2. semantic/prosody planning;
3. normalization/tokenization;
4. acoustic generation;
5. first PCM callback;
6. AudioTrack first audible playback.

## Candidate strategy

Do not train a foundation speech model from zero for v1. Benchmark license-compatible open expressive speech architectures that can realistically run or be adapted for Android ARM64.

A candidate is not accepted based on desktop demos alone. Benchmark matrix must include:
- Russian pronunciation;
- naturalness;
- emotional range;
- speaker consistency;
- prompt/control adherence;
- first-PCM latency;
- real-time factor;
- RAM/native heap;
- CPU/thermals/battery;
- model/download size;
- incremental streaming;
- cancellation/barge-in behavior;
- Android runtime feasibility;
- commercial/adaptation license.

## Fallback policy

Piper/Irina remains installed/available as the safety backend until the new IMA backend wins real-device tests.

Fallback triggers may include:
- primary model unavailable/corrupt;
- unsupported device capability;
- primary runtime crash/OOM;
- thermal/resource protection;
- user-selected low-resource/offline-safe mode.

Fallback must not silently change memory/personality. Only the voice backend changes.

## 0.12.x implementation order

1. Stabilize wake-name tolerance and natural barge-in from the Realme 0.12.1 log.
2. Complete explicit response-session use in CompanionActivity (`begin/append/finish/cancel`).
3. Add detailed first-PCM pipeline telemetry and native-memory profiling.
4. Build candidate benchmark harness behind the existing speaker abstraction.
5. Add semantic prosody packets independent of Piper controls.
6. Integrate first generative candidate as an experimental primary backend.
7. Keep Irina selectable as fallback throughout A/B real-device testing.

## Non-goals

- Do not pitch-shift Irina and call it a new voice.
- Do not rely on Android vendor TTS as production voice.
- Do not clone a person's voice without consent/rights flow.
- Do not replace conversational reliability with unconstrained audio generation.
- Do not block Companion Core, memory or ShouldSpeak work on a perfect voice model.

## Continuity rule

Every new chat must treat this file plus `COMPANION_VISION.md` and `COMPANION_PROGRESS.md` as authoritative. The intended primary voice is the IMA generative expressive engine; Irina/Piper is the stable fallback, even if a current test APK still speaks with Irina.
