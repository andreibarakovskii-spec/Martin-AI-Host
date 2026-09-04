# imagination — Development Progress / Handoff

Updated: 2026-09-04

Read together with `docs/COMPANION_VISION.md` before continuing. Update this file after every meaningful implementation/test batch.

## Product identity
- Product: **imagination**
- Default assistant: **IMA** («Има»)
- Legacy Martin names may remain in stable internal classes during audio stabilization.
- Display/wake name will become configurable.

## Repository
- `andreibarakovskii-spec/Martin-AI-Host`
- branch `companion-core-v1`

## Current tested voice build — 0.12.1 IMA Female Voice
- versionCode 121
- versionName `0.12.1-ima-female-voice`
- tested HEAD `765993aea0fe4c74845d432a2a3ff730f8fe6845`
- workflow run #301 / `33851745313`: SUCCESS
- build job `100955884170`: SUCCESS
- Android 35 Pixel 6 x86_64 emulator launch/install: success
- native Piper/VITS female voice smoke: success, `OK (1 test)`
- APK artifact `imagination-0.12.1-APK`, id `9928665743`
- QA artifact `imagination-0.12.1-QA`, id `9928753654`
- extracted APK SHA-256 `53aa6807bf05e690ba08ee410eb0cccf8f71bb5962bec80492d92c38da9ac97e`
- APK size 238,647,517 bytes

## Current production voice baseline
Production does not select Android/vendor TTS. IMA 0.12.1 uses the local female Piper/VITS model `vits-piper-ru_RU-irina-medium` through sherpa-onnx 1.13.2 and our AudioTrack path. The model is app-owned/cross-device, downloaded once, verified, then kept locally.

Run #301 emulator/native benchmark (not a Realme/tablet performance claim):
- native full synthesis: 452 ms;
- first streaming PCM callback: 188 ms;
- streaming smoke synthesis: 188 ms;
- sample rate: 22,050 Hz.

Existing pre-arm, Grok SSE streaming, cancellation, barge-in and ConversationWorkingMemory are retained.

## IMA Voice Engine v1 — implemented foundation
0.12.1 introduces the first backend-neutral IMA Voice layer instead of treating Piper as the final product voice.

### Voice response session contract
`MartinSpeaker` now exposes:
`beginResponse(emotion, energy) -> appendResponse(text, emotion, energy) -> finishResponse()` and `cancelResponse()`.

`MartinNeuralSpeaker` implements the contract while keeping one generation/queue for same-response fragments so appending a later fragment does not cancel earlier queued speech. This is the foundation for swapping Piper for a future generative voice backend without changing the conversation stack.

### Prosody / emotion layer v1
`ImaProsodyPlanner` maps conversational style into controls supported by the current backend. Supported profiles include neutral, warm, calm, empathetic, happy, playful, excited, curious and confident. It currently controls synthesis speed, silence scale, gain/energy and inter-sentence pause timing. It intentionally does not fake a female voice by pitch-shifting; Irina is a real female model.

This is a prosody/emotion control layer over a fixed Piper voice, not yet a true expressive speaker-conditioned generative voice model.

## Strategic IMA Voice direction
Target architecture remains:
`LLM stream -> semantic/prosody planner -> normalization/phonemes -> speaker profile -> speech generator -> vocoder/codec -> incremental PCM -> AudioTrack`

Piper/Irina is the stable local fallback while a more expressive generative backend is benchmarked and adapted behind the same speaker/session abstraction.

First milestone requirements remain:
1. Russian-first conversational quality.
2. Target <300–500 ms to first PCM on supported Android ARM hardware.
3. Incremental synthesis and fast cancellation/barge-in.
4. Explicit emotion/prosody controls.
5. Same output behavior across phone/tablet vendors.
6. Future speaker embeddings / Personal Voice from short samples.
7. Local inference for normal speech after model install.
8. Reproducible benchmark of first PCM, RTF, RAM, thermals, model size and pronunciation.
9. License must permit intended product use/adaptation before adopting a base architecture.
10. Do not train a foundation speech model from scratch in v1; benchmark/adapt a suitable open architecture first.

## Personal/Legacy Voice direction
One base engine should eventually support replaceable encrypted speaker profiles. Voice identity remains separate from factual memory/personality. Personal voice import requires rights/consent flow, deletion and quality checks. Memorial/Legacy Voice is explicitly AI-generated and must not claim the deceased person is literally present.

## Real-device validation required for 0.12.1
1. Measure Irina first PCM/playback on target Realme ARM64.
2. Test several ordinary turns and long replies.
3. Test multiple interruptions while IMA speaks.
4. Judge Russian pronunciation, female voice naturalness, prosody/emotion differences and pause quality.
5. Check CPU/temperature, Bluetooth behavior and gaps between queued fragments.
6. Export diagnostics for comparison with 0.11.4/0.12.0.

## Known limitations
1. 188 ms first PCM is Android 35 x86_64 emulator evidence, not a Realme ARM claim.
2. Piper/Irina remains a fixed speaker model; prosody v1 is useful but not the final expressive neural IMA voice.
3. Backend-neutral response sessions are implemented in `MartinSpeaker`/`MartinNeuralSpeaker`; `CompanionActivity` still has legacy `speak()` call sites and should be migrated to explicit begin/append/finish as the streaming assembler is refined. The current stable generation queue already prevents the old prefix/remainder self-cancellation behavior.
4. Bluetooth AEC/self-echo remains device-dependent.
5. Long-term Memory Engine, reliable diarization, active camera identity context, encrypted key storage and configurable AssistantIdentity remain incomplete.
6. The final generative IMA Voice base architecture/weights are not selected yet; no claim of a proprietary foundation speech model is made.

## Next priorities
1. Analyze the user's real-device 0.12.1 diagnostic.
2. Migrate Grok streaming response assembly to explicit `beginResponse -> appendResponse -> finishResponse/cancelResponse` calls.
3. Build the IMA Voice benchmark matrix for license-compatible Russian-capable generative speech architectures on Android ARM, not desktop-only demos.
4. Add richer semantic/prosody planning independent of the synthesis backend.
5. Integrate the first winning generative candidate only after native smoke + Android ARM benchmark; keep Irina as fallback.
6. Continue Conversation Behavior Engine / ShouldSpeak and then Memory Engine v1 once voice turn-taking is stable.

## Documentation discipline
After each meaningful code/test batch update this file with exact commit/run/test results and remaining failures. Architecture/product changes also update `docs/COMPANION_VISION.md`.