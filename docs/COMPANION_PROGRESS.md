# imagination — Development Progress / Handoff

Updated: 2026-09-04

> ## READ THIS FIRST IN EVERY NEW CHAT
> Current product: **imagination**. Current assistant/persona: **IMA** («Има»).
> Do **not** continue the old Martin/party-host concept. Names such as `MartinSpeaker`, `MartinNeuralSpeaker`, repository `Martin-AI-Host` and other `Martin*` symbols are legacy implementation names only and may remain temporarily while the stable audio stack is migrated. They are not the current product identity.
> Start from branch `companion-core-v1`, read this file together with `docs/COMPANION_VISION.md`, inspect the latest Actions/build status, then continue from the exact priorities below. Do not ask the user to repeat September decisions if they are recorded here.

## Product identity
- Product: **imagination**
- Default assistant: **IMA** («Има»)
- Product discussions, new UX and new architecture use IMA/imagination terminology.
- Legacy Martin names may remain in stable internal classes during migration only.
- Display/wake name will become configurable.

## Repository
- `andreibarakovskii-spec/Martin-AI-Host`
- active development branch: `companion-core-v1`

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

A later documentation/validation commit on `companion-core-v1` (`57f8cc18ab94fb4818cd2d2c32b4af991905c448`, workflow run #302) also built successfully and produced `imagination-0.12.1-APK` and QA artifacts. Keep run #301 metrics as the verified voice benchmark baseline unless newer real-device evidence replaces them.

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
Legacy interface name `MartinSpeaker` currently exposes:
`beginResponse(emotion, energy) -> appendResponse(text, emotion, energy) -> finishResponse()` and `cancelResponse()`.

Legacy implementation name `MartinNeuralSpeaker` implements the contract while keeping one generation/queue for same-response fragments so appending a later fragment does not cancel earlier queued speech. These names are technical debt; future renaming must not break the stabilized audio path.

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

## External reference reviewed — AI AVATAR / Your Companion
Reference: Google Play package `com.wallet.walkthedog`, reviewed 2026-09-04 as product/UX inspiration only.

Useful concepts to adopt for IMA:
- persistent avatar identity independent of the current chat/session;
- avatar creation/customization separated from core conversation runtime;
- continuous visible emotional presence, not animation only while speaking;
- lip-sync driven by actual generated audio timing/amplitude/visemes rather than text timing;
- simple/full-screen companion presentation when visual mode is open;
- future avatar/outfit customization layered on top of the same IMA identity;
- companion remains useful in background/voice-first mode and does not require the avatar screen to be open.

Architecture decision:
- add a renderer-neutral `AvatarState` / `AvatarController` boundary;
- suggested runtime state: idle/listening/thinking/speaking/interrupted/background + emotion + energy + gaze/attention + PCM/viseme lip-sync + short gesture events;
- avatar rendering must never delay or block voice startup;
- Memory Engine, Soul/Personality, Voice Identity, Perception and Avatar stay independent modules;
- the avatar is IMA's visual body, not the source of memory, identity or personality;
- local-first/user-controlled privacy remains preferred for person/environment perception.

The stable architecture decision is also recorded in `docs/COMPANION_VISION.md` (commits `dbb352fda9a27d6da0fd4538202973605d622f81` and `4f0c2c53fe5c70be5dc27d5deeaf4da61fe0ed46`).

## September product direction — authoritative summary
IMA is no longer a party host. The product is a persistent personal AI companion for phone/tablet/external speaker.

Core sequence:
`Realtime Voice -> Conversation Director -> Working Memory -> durable Memory Engine -> Soul/Adaptive Personality -> Context/ShouldSpeak -> Skills/Agent actions -> Person/environment perception -> Avatar`.

Key behavior goals:
- IMA behaves like an attentive room participant, not a wake-word command parser;
- distinguish ambient speech, self-echo, direct address, continuation, interruption, correction and topic shift;
- action choices include `IGNORE`, `LISTEN_AND_REMEMBER`, `RESPOND`, `ASK_WHO`;
- long-term memory must be selective/structured, not a transcript dump;
- personality adapts slowly within bounded dimensions;
- controlled proactivity is handled by a separate `ShouldSpeak` policy;
- avatar is important but must not block natural conversation/memory work.

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
3. Backend-neutral response sessions exist, but `CompanionActivity` still has legacy `speak()` call sites that should migrate to explicit begin/append/finish as streaming assembly is refined.
4. Bluetooth AEC/self-echo remains device-dependent.
5. Long-term Memory Engine, reliable diarization, active camera identity context, encrypted key storage and configurable AssistantIdentity remain incomplete.
6. Final generative IMA Voice base architecture/weights are not selected yet.
7. Renderer-neutral Avatar Runtime contract is now specified but not yet implemented in code.

## Next priorities — continue from here
1. Analyze the user's real-device 0.12.1 diagnostic when available.
2. Migrate Grok streaming response assembly to explicit `beginResponse -> appendResponse -> finishResponse/cancelResponse` calls.
3. Implement the renderer-neutral `AvatarState` / `AvatarController` contract without coupling it to a final 3D engine.
4. Expose actual speech PCM amplitude/timing (and visemes when backend supports them) to the Avatar Runtime for lip-sync.
5. Build the IMA Voice benchmark matrix for license-compatible Russian-capable generative speech architectures on Android ARM.
6. Add richer semantic/prosody planning independent of the synthesis backend.
7. Continue Conversation Behavior Engine / `ShouldSpeak`, then Memory Engine v1 once voice turn-taking is stable.
8. After Memory/Soul foundations are stable, extend multimodal person context and avatar creator/3D.

## Documentation discipline
After every meaningful implementation/test batch, update this file with exact branch/version/commit, Actions run, tested behavior, failures/limitations, next priorities and real-device status. Architecture/product changes also update `docs/COMPANION_VISION.md`.

Future-chat rule: if a new chat starts with a request to continue IMA/imagination, read these two docs first, inspect `companion-core-v1` and latest GitHub Actions, and continue from `Next priorities`. Do not fall back to Martin-era assumptions just because legacy repository/class names still contain Martin.