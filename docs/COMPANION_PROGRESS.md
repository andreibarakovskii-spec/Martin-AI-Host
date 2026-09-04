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

## Current tested voice build — 0.12.0 IMA Voice / Piper
- versionCode 120
- versionName `0.12.0-ima-voice-piper`
- tested HEAD `3f260f1fb18cb24640f9b03a55ce5e5a983da46e`
- workflow run #292 / `33776451303`: SUCCESS
- Android 35 emulator launch: success
- native Piper/VITS smoke: success
- APK artifact `imagination-0.12.0-APK`, id `9901867875`
- extracted APK SHA-256 `a520658abcc44ec5765e6cb4d1e23f74d52471151da05e918bc1b4d0e3e56be7`

## Current production voice baseline
Production no longer selects Android/vendor TTS. Piper/VITS `vits-piper-ru_RU-ruslan-medium` runs through sherpa-onnx 1.13.2 and feeds our AudioTrack path. Model is app-owned/cross-device, downloaded once, verified and stored locally.

Run #292 emulator benchmark (not a Realme/tablet claim):
- full `Андрей, с днём рождения!`: 769 ms;
- `Как настроение?`: 359 ms first callback, 360 ms total.

Existing pre-arm, Grok SSE streaming, cancellation, barge-in and ConversationWorkingMemory are retained.

## New architectural decision — IMA Voice Engine v1
User approved building a proprietary/adapted generative speech layer instead of treating Piper as the final voice technology. Vision was updated on 2026-09-04.

Piper remains the production fallback while IMA Voice Engine is developed behind the same speaker abstraction.

Target:
`LLM stream -> semantic/prosody planner -> normalization/phonemes -> speaker profile -> speech generator -> vocoder/codec -> incremental PCM -> AudioTrack`

First milestone requirements:
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

## Real-device validation still required for 0.12.0
1. Measure Piper first PCM/playback on target Realme.
2. Test several normal turns and interruptions.
3. Check Russian pronunciation/naturalness, CPU/temperature and gaps.
4. Export diagnostics.

## Known limitations
1. Piper ARM/Realme timing not measured yet.
2. Piper voice is fixed and not sufficiently expressive for final IMA Voice.
3. Streamed LLM answer still needs a unified appendable TTS response session so early prefix/remainder never compete as separate generations.
4. Bluetooth AEC/self-echo remains device-dependent.
5. Long-term Memory Engine, reliable diarization, active camera identity context, encrypted key storage and configurable AssistantIdentity remain incomplete.
6. IMA Voice base architecture/weights are not selected yet; no claim of a proprietary foundation model exists yet.

## Next priorities
1. Analyze the user's real-device 0.12.0 diagnostic when received.
2. Build an IMA Voice benchmark matrix for license-compatible Russian-capable speech architectures, measuring Android viability rather than desktop demos.
3. Add a backend-neutral voice interface/session capable of `beginResponse -> append -> finish/cancel`, preserving Piper as fallback.
4. Prototype semantic/prosody tags and make them independent of the TTS backend.
5. Integrate the first winning generative voice candidate only after native smoke + Android ARM benchmark.
6. Continue Conversation Behavior Engine / ShouldSpeak and then Memory Engine v1 once voice turn-taking is stable.

## Documentation discipline
After each meaningful code/test batch update this file with exact commit/run/test results and remaining failures. Architecture/product changes also update `docs/COMPANION_VISION.md`.