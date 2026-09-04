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

A later documentation/validation commit on `companion-core-v1` (`57f8cc18ab94fb4818cd2d2c32b4af991905c448`, workflow run #302) also built successfully and produced `imagination-0.12.1-APK` and QA artifacts. Keep run #301 metrics as the verified emulator voice benchmark baseline; the real-device diagnostic below now supersedes emulator-only latency assumptions for target-device planning.

## Current production voice baseline
Production does not select Android/vendor TTS. IMA 0.12.1 uses the local female Piper/VITS model `vits-piper-ru_RU-irina-medium` through sherpa-onnx 1.13.2 and our AudioTrack path. The model is app-owned/cross-device, downloaded once, verified, then kept locally.

Run #301 emulator/native benchmark:
- native full synthesis: 452 ms;
- first streaming PCM callback: 188 ms;
- streaming smoke synthesis: 188 ms;
- sample rate: 22,050 Hz.

Existing pre-arm, Grok SSE streaming, cancellation, barge-in and ConversationWorkingMemory are retained.

## Real-device diagnostic — 0.12.1 on Realme RMX3834
User diagnostic archive received 2026-09-04. Session metadata:
- device: `realme RMX3834`;
- Android 35;
- app: `0.12.1-ima-female-voice`;
- session length: about 300 s;
- 34 STT submissions, about 78.8 s total submitted speech;
- AEC enabled, NS disabled, source `VOICE_COMMUNICATION`;
- no dropped tasks reported.

### What worked
- continuous capture stayed alive for the full five-minute session;
- direct and continuation turns generally flowed without manually restarting recognition;
- barge-in cancellation itself is very fast once confirmed: observed cancellation latency 1–3 ms in almost all cases, one 42 ms outlier;
- stop commands such as «Подожди» / «Стоп» reliably cancelled speech;
- working conversation memory retained earlier context such as the requested city, although STT misrecognized «Дзержинск» as «Держимск»;
- response streaming/queued speech no longer shows the old same-response self-cancellation bug;
- device diagnostics remained active and `dropped_tasks=0` at session end.

### Problems found
1. **Real-device voice latency is still too high.** Across 17 measured AI responses, `response_ready_to_first_audio_ms` averaged about 1403 ms, median 1562 ms, range 798–1827 ms. Individual `tts_first_pcm` events across chunks averaged about 1580 ms, median about 1535 ms, with 349–3098 ms range. This does not meet the intended <300–500 ms first-PCM target on the target phone.
2. **Short natural interruptions are rejected too often.** Examples such as «Синтез речи», «И вот», «Я просил…», «Има», «В каком городе я просил тебя…» were classified `barge_rejected/not_explicit_enough` while IMA was speaking. The current barge-in policy is too strict for natural correction/continuation and makes the user repeat themselves.
3. **Wake/address recognition needs fuzzy alias handling.** The first «Има, привет. Давай познакомимся» was transcribed as «Нима…» and incorrectly ignored as ambient. Later variants included «Имма», «Ема», «Тима», «Сима». Address detection must tolerate realistic STT confusions for a short name without making ambient false positives explode.
4. **Resume-after-interruption semantics are incomplete.** After the user stopped IMA and asked to continue from where she stopped, the model continued the topic but not the exact interrupted answer position. We need explicit interrupted-response state: original response id, spoken text/offset, remaining semantic units and resume intent.
5. **STT proper-noun correction is weak.** «Дзержинск» became «Держимск», and IMA then repeated the wrong form from working memory. Known/user-confirmed entities need confidence-aware normalization/correction rather than blindly persisting first ASR output.
6. **Skills gap is visible.** When asked for tomorrow's weather, IMA said she had no current forecast access. Weather remains a planned skill and should route through the skill layer instead of conversational fallback once enabled.
7. **Memory growth needs investigation.** `native_heap` rose from roughly 153 MB near session start to about 679 MB by the end of the five-minute diagnostic; Java heap rose from about 5.3 MB to 18.5 MB. This may include native model/audio buffers/caches, but the magnitude is large enough to require a repeated-session leak/retention test before long-running companion use.
8. **Power-save mode was enabled** during the session, so latency/thermal benchmarking should be repeated with power saver off before treating the numbers as final hardware limits.

### Immediate decisions from this test
- Do not replace Piper/Irina yet; first separate model synthesis cost from queue/chunking/scheduler latency on ARM.
- Make barge-in confirmation context-sensitive: short speech during IMA output should be accepted when it is a direct address, correction, negation, continuation, question start or semantically related phrase; explicit stop words remain immediate.
- Add tolerant address-name matching around IMA/Има variants produced by STT, with stricter thresholds when there is no active conversation context.
- Add `InterruptedResponseState` so «продолжи с того места» resumes the interrupted answer rather than regenerating a loosely related continuation.
- Add entity correction/confirmation path before durable memory writes for low-confidence proper nouns.
- Add native memory instrumentation by turn/session and verify speaker/model resources are reused/released as intended.

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

## Known limitations
1. Piper/Irina remains a fixed speaker model; prosody v1 is useful but not the final expressive neural IMA voice.
2. Backend-neutral response sessions exist, but `CompanionActivity` still has legacy `speak()` call sites that should migrate to explicit begin/append/finish as streaming assembly is refined.
3. Bluetooth AEC/self-echo remains device-dependent.
4. Long-term Memory Engine, reliable diarization, active camera identity context, encrypted key storage and configurable AssistantIdentity remain incomplete.
5. Final generative IMA Voice base architecture/weights are not selected yet.
6. Renderer-neutral Avatar Runtime contract is specified but not yet implemented in code.
7. Real-device 0.12.1 first-audio latency is around 0.8–1.8 s in the captured session and requires optimization.
8. Native heap growth during the five-minute session must be explained before long-duration background use.
9. Barge-in policy rejects too many short natural corrections/continuations.
10. Exact resume from interrupted speech is not implemented yet.

## Next priorities — continue from here
1. Fix real-device turn-taking regressions found in the 0.12.1 diagnostic: tolerant IMA address matching, context-sensitive short barge-in acceptance, and exact interrupted-response resume state.
2. Instrument native memory per turn and run repeated 5/15/30-minute sessions to identify retained TTS/model/audio buffers or confirm expected caching.
3. Profile first-audio latency by stage on ARM: AI ready -> prosody -> synthesis start -> first PCM -> AudioTrack start; then optimize the dominant stage before changing voice model.
4. Migrate Grok streaming response assembly to explicit `beginResponse -> appendResponse -> finishResponse/cancelResponse` calls.
5. Implement the renderer-neutral `AvatarState` / `AvatarController` contract without coupling it to a final 3D engine.
6. Expose actual speech PCM amplitude/timing (and visemes when backend supports them) to the Avatar Runtime for lip-sync.
7. Build the IMA Voice benchmark matrix for license-compatible Russian-capable generative speech architectures on Android ARM.
8. Add richer semantic/prosody planning independent of the synthesis backend.
9. Continue Conversation Behavior Engine / `ShouldSpeak`, then Memory Engine v1 once voice turn-taking is stable.
10. Add initial Skills routing (weather/reminders/calendar/music) after turn-taking is dependable enough not to mask dialogue bugs.

## Documentation discipline
After every meaningful implementation/test batch, update this file with exact branch/version/commit, Actions run, tested behavior, failures/limitations, next priorities and real-device status. Architecture/product changes also update `docs/COMPANION_VISION.md`.

Future-chat rule: if a new chat starts with a request to continue IMA/imagination, read these two docs first, inspect `companion-core-v1` and latest GitHub Actions, and continue from `Next priorities`. Do not fall back to Martin-era assumptions just because legacy repository/class names still contain Martin.