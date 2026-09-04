# imagination — Development Progress / Handoff

Updated: 2026-09-04

> ## READ THIS FIRST IN EVERY NEW CHAT
> Product: **imagination**. Assistant/persona: **IMA** («Има»).
> Do **not** revert to the old Martin/party-host concept. `Martin*` class/repository names are legacy implementation names only.
> Read this file, `docs/COMPANION_VISION.md`, and `docs/IMA_VOICE_ENGINE.md`, then inspect the active branch and latest GitHub Actions. Do not ask the user to repeat September decisions already recorded here.

## Repository / active work
- repository: `andreibarakovskii-spec/Martin-AI-Host`
- stable integration branch: `companion-core-v1`
- current implementation branch: `ima-0.12.2-turntaking`
- current test build: **0.12.2-ima-turntaking** (`versionCode 122`)

## Voice product decision — authoritative
**Irina/Piper is fallback only.** It is the stable local safety backend while imagination develops its own primary **IMA Voice Engine**.

The intended primary voice is a generative expressive speech engine with semantic prosody, emotions, intonation, energy, natural timing, and later speaker profiles/Personal Voice. The quality bar is closer to expressive generative audio systems (the user referenced Suno) than to conventional fixed TTS. This means expressiveness and personalization, not copying Suno code/weights or using a music generator for dialogue.

Authoritative technical plan: `docs/IMA_VOICE_ENGINE.md`.

Target pipeline:
`LLM stream -> semantic/prosody planner -> normalization/content representation -> speaker profile -> expressive speech generator -> vocoder/codec -> incremental PCM -> AudioTrack`.

Keep Irina available for fallback until a candidate beats it on real Android ARM for Russian quality, first-PCM latency, stability, RAM/thermals, streaming and cancellation.

## Last tested build — 0.12.1 IMA Female Voice
- versionCode 121
- versionName `0.12.1-ima-female-voice`
- Piper/VITS `ru_RU-irina-medium` via sherpa-onnx 1.13.2
- production voice is app-owned/cross-device; Android/vendor TTS is not primary
- emulator run #301 succeeded; first streaming PCM in emulator benchmark: 188 ms
- later validation run #302 also succeeded

## Real-device 0.12.1 diagnostic — Realme RMX3834
User diagnostic received 2026-09-04, ~5 minute session, Android 35.

What worked:
- continuous capture remained alive;
- `dropped_tasks=0`;
- stop commands reliably cancelled output;
- cancellation after barge confirmation was usually 1–3 ms (one 42 ms outlier);
- working conversation memory retained topic context;
- old same-response prefix/remainder self-cancellation was not observed.

Problems found:
1. Real-device first-audio latency is still high: across measured responses roughly 0.8–1.8 s, average ~1.4 s; individual chunk first-PCM events can be slower.
2. Barge-in policy rejected useful short interruptions such as «Има», «Синтез речи», «Я просил…» and question starts.
3. Short wake name IMA suffered STT variants such as «Нима», «Имма», «Ема», «Тима», «Сима».
4. Exact resume after interruption is incomplete: IMA remembers the topic but not the exact spoken offset/remaining semantic units.
5. Proper nouns can be corrupted by STT (example: «Дзержинск» -> «Держимск») and then echoed by working memory.
6. Native heap rose strongly during the session (~153 MB to ~679 MB); must determine cache vs retention/leak.
7. Power-save mode was enabled, so latency/thermal tests must also be repeated with it off.

## 0.12.2 implementation
Branch `ima-0.12.2-turntaking` was created from `companion-core-v1` after the real-device test.

Implemented:
- new `ImaWakeMatcher` for tolerant recognition of observed IMA wake-name STT variants;
- `AttentionManager` now uses the tolerant matcher;
- `BargeInPolicy` accepts the assistant name alone during active TTS, observed wake-name variants, compact two-word non-echo interjections, and natural directed multiword speech while still rejecting long unrelated room sentences and likely TTS echo;
- unit coverage added for IMA wake variants and real-device barge-in regressions;
- app version bumped to `0.12.2-ima-turntaking` / versionCode 122;
- detailed primary/fallback voice architecture recorded in `docs/IMA_VOICE_ENGINE.md`;
- generic edit-distance matching for the 3-letter wake name was removed after CI exposed a false wake on «зима»; only observed safe aliases are accepted now.

Key commits on the 0.12.2 branch:
- `a2be273a623cac474b8c67148bec55f0e5d480ad` — tolerant IMA wake matcher;
- `f28ba7a16162a06f793144722b24fba469e153c9` — attention gate uses IMA matcher;
- `cf9efafc1e16ccfd753be20113891804c730fba8` — tuned barge-in policy from Realme evidence;
- `498fe8d7e5d8cc6d75de72310815e5d444986c38` — barge-in regression tests;
- `bbfa15eee0642ad37053bd2101ac592e7f6b121b` — 0.12.2 version bump;
- `eb2b0061a70eb844121bc9c4826c083a0986b72a` — IMA Voice Engine primary/fallback plan;
- `164a35bab4ad297a389e0b48dbbcd93777643a5e` — fix wake matcher false positive found by CI.

## 0.12.2 CI / APK status
- run #307 / `33856432286` failed one unit regression: `ImaWakeMatcherTest.rejectsUnrelatedWords` because generic fuzzy matching treated «зима» as IMA; fixed in commit `164a35b`.
- run #308 / `33856681488` on commit `164a35b`: unit tests and Android debug build **PASS**; APK integrity check **PASS**; APK artifact uploaded successfully.
- APK artifact: `imagination-0.12.2-APK`, artifact id `9930531241`, artifact digest `sha256:1ecd39d58857e364cd0afa26df4e44eac6fe7d466d9d8699e26e4e5131ae3504` (digest of artifact ZIP).
- extracted APK SHA-256: `b25843d09d2375e5d83ec8d02a0173995aa87b39b416184f70b6f291308d5f26`.
- emulator/native voice smoke for run #308 was still running when this handoff entry was written; do not call full CI green until that final step completes. The APK itself has already passed compile/tests/integrity checks and is available for real-device testing.

## IMA Voice Engine foundation already present
Backend-neutral speaker session contract exists:
`beginResponse(emotion, energy) -> appendResponse(text, emotion, energy) -> finishResponse()` plus `cancelResponse()`.

Current Piper layer has `ImaProsodyPlanner` profiles such as neutral, warm, calm, empathetic, happy, playful, excited, curious and confident. This is only a control/fallback layer over a fixed voice, **not** the final expressive IMA voice.

## Immediate next work
1. Finish emulator/native smoke for 0.12.2 and record the result.
2. Real-device test 0.12.2 on the user's Realme with power saver OFF; specifically test IMA/Nima/Imma/Ema/Tima/Sima address variants, «зима» false-wake rejection, short barge-ins, repeated interruptions, first-audio latency and native heap growth.
3. Implement `InterruptedResponseState` with response id, actual spoken text/offset, remaining semantic units, and explicit resume intent.
4. Migrate `CompanionActivity` streaming assembly to explicit `beginResponse -> appendResponse -> finishResponse/cancelResponse` so the same abstraction can host the future generative voice engine.
5. Instrument ARM latency by stage: LLM speakable text -> prosody -> synthesis start -> first PCM -> AudioTrack audible start.
6. Add per-turn native memory telemetry and repeated 5/15/30-minute retention tests.
7. Build the generative voice candidate benchmark harness behind the same speaker abstraction. Irina stays fallback only.
8. Add richer semantic prosody packets independent of Piper parameters: emotion, energy, pace, emphasis, pause strength, final contour and optional safe non-verbal events.
9. Continue Conversation Behavior / `ShouldSpeak`, then Memory Engine v1 after turn-taking is dependable.
10. Avatar Runtime remains planned but must not delay voice/conversation work.

## Product direction summary
IMA is a persistent personal AI companion, not a party host. Core sequence:
`Realtime Voice -> Conversation Director -> Working Memory -> durable Memory Engine -> Soul/Adaptive Personality -> Context/ShouldSpeak -> Skills -> Person/environment perception -> Avatar`.

IMA should behave like an attentive room participant: distinguish ambient speech, self-echo, direct address, continuation, correction, interruption and topic shift; possible actions include `IGNORE`, `LISTEN_AND_REMEMBER`, `RESPOND`, `ASK_WHO`.

## Documentation discipline
After every meaningful implementation/test batch update this file with exact branch/version/commit, Actions status, real-device findings, what changed, failures/limitations and next priorities. Architecture changes also update `COMPANION_VISION.md`; voice architecture changes also update `IMA_VOICE_ENGINE.md`.

Future-chat rule: continue from the active 0.12.2 branch/work listed above. Never infer product identity from legacy `Martin*` symbols.
