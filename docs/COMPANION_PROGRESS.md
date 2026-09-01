# AI Companion — Development Progress / Handoff

Updated: 2026-09-01

This is the operational handoff between ChatGPT conversations. Always read this file together with `docs/COMPANION_VISION.md` before continuing, and update it after every meaningful implementation/test batch.

## Current product direction

The party-host phase is retired as the main product. We are building a persistent personal AI companion for an Android tablet + Bluetooth/external speaker. The priorities are natural dialogue, interruption, durable memory, adaptation, tools and later an avatar.

## Repository / branches

Repository: `andreibarakovskii-spec/Martin-AI-Host`

Active branch: `companion-core-v1`

Recovery branches:
- `voice-orb-v1`
- `companion-core-v1-baseline` from party-era green commit `ec2f3dff13535788325c7b49fb9d0c54873ceb81`

Party-era reference:
- 0.10.5
- run #230 green

## Current version — 0.11.1

Version:
- versionCode 111
- versionName `0.11.1-companion-barge-in`
- app label `Сергей AI Companion`

Current tested HEAD:
- `b3737851236cc30bd44c14169427afe2a1e0117d`

GitHub Actions:
- workflow `Build Companion Core APK`
- run #240
- run id `33508686512`
- job id `99858815271`
- result: COMPLETED / SUCCESS
- unit tests: success
- APK integrity check: success
- Android 35 CompanionActivity launch: success
- native FastVoiceSmokeTest: success

Artifact:
- `Sergey-AI-Companion-0.11.1-APK`
- artifact id `9800759326`
- archive digest `sha256:f5acca95134a91ee7beed9153666bf6d668abef4bb4388f9a81e41a436953b98`

Extracted APK:
- `sergey-ai-companion-0.11.1.apk`
- size `238614765` bytes
- SHA-256 `bca514ca489f6ef2699002b7d265dcd56df11fd60c12044361c9165ba3418373`

IMPORTANT: CI/emulator proves the code builds and launches. Barge-in over a real Bluetooth speaker is NOT confirmed until a real-device diagnostic test is completed.

## Companion Core architecture already implemented

Primary launcher/runtime is `CompanionActivity`, not `PremiumMainActivity`.

Normal dialogue path:
`ContinuousSpeechEngine -> GroqTranscriber -> ConversationDirector -> GrokClient -> MartinSpeaker`

`PartyDirector` is not used in the normal companion conversation path. Legacy party code stays non-exported for recovery/reference.

### ConversationDirector

Decisions:
- IGNORE
- RESPOND
- STOP
- MUSIC
- VISION

Supports a short continuation window so after an explicit `Сергей...` the user can continue naturally without repeating the name.

### AttentionManager

Attention classes:
- DIRECT
- LIKELY
- AMBIENT

Current v1 policy uses direct name, questions/actions, recent conversation continuation, repair/interrupt phrases and obvious ambient fragments. It is intentionally conservative and still needs real-world tuning.

### CompanionPrompt

`GrokClient` now uses `CompanionPrompt.system(context)` rather than the birthday/tamada prompt.

Base behavior:
- calm/intelligent/warm;
- concise natural Russian;
- rare dry humor;
- no automatic party behavior;
- no fake tool execution claims;
- no invented memories/people/camera facts;
- truthful that long-term memory is not built yet.

### Diagnostics

Companion events include:
- `companion_runtime`
- `companion_listening_start`
- `companion_stt_start/done/error`
- `companion_decision`
- `companion_ignored`
- `companion_ai_start/done/error`
- `companion_turn`
- `companion_cancel`

## 0.11.1 — strict barge-in prototype

Implemented Priority 1A as a conservative two-stage interrupt path rather than opening full STT over TTS.

### BargeInPolicy

New `BargeInPolicy.java` validates short STT candidates heard while the assistant is speaking.

Accepts strong signals such as:
- `стоп`
- `погоди`
- `подожди`
- `стой`
- `замолчи`
- `хватит`
- assistant name + additional speech, e.g. `Сергей, я про другое`
- explicit repair phrases

Rejects:
- weak ambient words;
- assistant name alone;
- candidate text with >=80% token overlap with current TTS (likely echo/self-speech).

Unit tests cover explicit stop, assistant-name interruption, ambient rejection, TTS echo rejection and name-alone rejection.

### ContinuousSpeechEngine interrupt monitor

The existing normal STT gate remains closed during `SPEAKING`, but a separate strict audio candidate monitor stays active.

Current interrupt capture settings:
- 180 ms pre-roll
- min speech 240 ms
- end silence 260 ms
- max candidate 2400 ms
- cooldown 1200 ms
- stricter level threshold approximately `max(-28 dB, noise floor + 18 dB)`
- existing VOICE_COMMUNICATION input + hardware AEC/NoiseSuppressor where Android/device supports them

It emits:
- `barge_candidate`
- `barge_audio_ready`

### CompanionActivity barge-in flow

While TTS is speaking:
1. strict monitor captures a short candidate;
2. candidate is transcribed;
3. `BargeInPolicy` compares it against current assistant speech;
4. rejected candidate logs `barge_rejected` and TTS continues;
5. accepted candidate logs `barge_confirmed`;
6. current AI/STT/TTS is cancelled;
7. cancellation time logs `tts_cancel_latency_ms`;
8. recognized user phrase returns to the ordinary ConversationDirector path.

Runtime diagnostic marker:
`companion_runtime: v1.1;director=local;party_director=false;barge_monitor=strict`

## What is confirmed

By CI/emulator:
- CompanionActivity is the launcher and starts;
- routing and barge-in unit tests pass;
- APK installs;
- voice/native dependency smoke test passes;
- strict interrupt code compiles with legacy activities;
- party activity is not the default runtime.

Not yet confirmed on real device:
- Bluetooth echo rejection;
- whether real user speech reliably crosses the strict interrupt threshold;
- actual interruption latency including remote STT;
- whether speaker/TTS ever falsely triggers `barge_candidate`/`barge_confirmed`;
- long continuous dialogue quality;
- music route in companion mode.

## Current major limitations

1. Long-term Memory Engine is not implemented. `GrokClient` only has short volatile history.
2. AttentionManager remains heuristic v1.
3. Barge-in currently uses remote STT for short candidates, so perceived interrupt latency may still be too high; later we should add a local wake/keyword/interrupt recognizer if logs justify it.
4. Vision intent is recognized, but new CompanionActivity intentionally does not yet use the legacy camera/person stack.
5. Internal names such as `MartinSpeaker`, `PartyAudioRouter`, `PartyMusic` remain technical debt; do not mass-rename while audio is stabilizing.
6. Direct Yandex Music relies on reverse-engineered behavior and needs companion-mode real-device verification.
7. Yandex OAuth token storage must later move to Android Keystore/encrypted storage.
8. Future agent actions need an explicit permission/risk layer.

## IMMEDIATE REAL-DEVICE TEST — 0.11.1

Install 0.11.1 on the target Android device and connect the intended Bluetooth speaker.

Test sequence:
1. Start voice mode.
2. Say: `Сергей, расскажи подробно, что такое эпизодическая память.`
3. While he is speaking say: `Сергей, погоди, я про другое.`
4. Verify whether speech stops and the new phrase is processed.
5. Start another longer response and say only `стоп` while he speaks.
6. Let the speaker play normally without talking and verify it does NOT interrupt itself.
7. Have another person say unrelated room speech while he speaks and see whether it is rejected.
8. Export/send the diagnostic ZIP.

In the log inspect:
- `barge_candidate`
- `barge_audio_ready`
- `barge_stt_start`
- `barge_confirmed`
- `barge_rejected`
- `tts_cancel_latency_ms`
- `mic_health` with `interrupt_monitor=true`

Do not claim barge-in is finished until this real-device test passes.

## NEXT — Priority 1B after barge-in log

Tune thresholds/echo rejection from the real log, then improve:
- semantic attention classifier for ambiguous speech;
- conversation-session expiry;
- discourse repair/corrections;
- streaming LLM response where provider supports it;
- earlier/streaming TTS;
- end-of-user-speech -> first-assistant-audio latency metric;
- local fast interruption detection if remote STT is too slow.

## NEXT — Priority 2: Memory Engine v1

After barge-in is stable enough, implement durable structured memory:
- working
- episodic
- semantic
- preference
- relationship
- goal

Pipeline:
`conversation/event -> candidate extraction -> importance -> type -> dedup/conflict -> temporal metadata -> encrypted persistence`

Retrieval:
`current context -> keyword + semantic + temporal retrieval -> rerank -> compact relevant memory context`

Records need id/type/content/timestamps/validity/confidence/importance/source/entity links/last_used/supersedes/conflicts/user-confirmed.

Also required:
- encrypted local store;
- memory viewer;
- edit/delete/clear/export;
- simulated multi-day tests;
- never save every transcript line by default.

## Later plan

3. Soul Engine: bounded adaptive warmth/humor/verbosity/formality/initiative/directness + per-user relationship state.
4. Context + controlled proactivity: `ShouldSpeakPolicy`, budgets/cooldowns, goals/time/calendar/presence.
5. Skill/tool registry + permissions: reminders, calendar, notes, web, weather, music, messaging, smart home.
6. PersonContext with confidence-based face/voice/appearance/recent-presence fusion.
7. Avatar v1, then avatar creator/3D.
8. Optional encrypted sync/family profiles.
9. Commercial onboarding/subscriptions/skill ecosystem.

## Mandatory continuation rule

Every new ChatGPT session must:
1. read `docs/COMPANION_VISION.md`;
2. read this file;
3. inspect current `companion-core-v1` HEAD and latest CI;
4. continue from the NEXT section;
5. update this file after meaningful changes/tests;
6. update Vision only if architecture/product direction materially changes;
7. never mark a feature done without code/tests or real-device evidence.
