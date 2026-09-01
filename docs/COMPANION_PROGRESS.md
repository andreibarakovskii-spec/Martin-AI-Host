# AI Companion — Development Progress / Handoff

Updated: 2026-09-01

This file is the operational handoff between ChatGPT conversations. Update it after every meaningful code batch, test result, architecture change or newly discovered blocker. Read it together with `docs/COMPANION_VISION.md` before continuing development.

## Current product direction

The party-host phase is finished. The product is now a persistent personal AI companion for an Android tablet + external/Bluetooth speaker.

Primary objective: make conversation, interruption handling, long-term memory and adaptation feel substantially more natural than a command-oriented smart speaker before investing heavily in a 3D avatar.

## Branches / recoverable baseline

Repository: `andreibarakovskii-spec/Martin-AI-Host`

Active companion branch:
- `companion-core-v1`

Preserved party-era branches/baselines:
- `voice-orb-v1`
- `companion-core-v1-baseline` created from party-era green commit `ec2f3dff13535788325c7b49fb9d0c54873ceb81`

Last party-era tested version:
- `0.10.5`
- GitHub Actions run #230 green

Current companion version:
- `0.11.0-companion-core-v1`
- versionCode 110
- app label: `Сергей AI Companion`

Current Companion Core HEAD used for first CI:
- `a29f3f92001c294dd881256bfd7b9e5d8c4be905`

## Companion Core v1 — implemented 2026-09-01

### New launcher/runtime

Created `CompanionActivity.java` and made it the launcher activity.

Important architectural change:
- normal conversation no longer runs through `PartyDirector`;
- old `PremiumMainActivity` remains in the APK as non-exported legacy/party code for recovery/reference;
- primary runtime path is now:
  `ContinuousSpeechEngine -> GroqTranscriber -> ConversationDirector -> GrokClient -> MartinSpeaker`.

The new screen keeps the lightweight Voice Orb and provides Settings + Diagnostics access.

### ConversationDirector

Added `ConversationDirector.java`.

Initial deterministic decisions:
- `IGNORE`
- `RESPOND`
- `STOP`
- `MUSIC`
- `VISION`

It keeps a short continuation window, allowing conversation to continue without repeating the assistant name after an explicit address.

Examples covered by tests:
- `Сергей, что мы сегодня делаем?` -> RESPOND and strips assistant name;
- ambient statement before a conversation -> IGNORE;
- follow-up without wake word inside continuation window -> RESPOND;
- stale unrelated statement after continuation window -> IGNORE;
- `Сергей, погоди` -> STOP;
- music request -> MUSIC;
- visual request -> VISION.

### AttentionManager

Added `AttentionManager.java`.

Initial attention classes:
- `DIRECT`
- `LIKELY`
- `AMBIENT`

Current deterministic signals include:
- direct assistant-name mention;
- repair/interrupt phrases;
- questions;
- clear action verbs;
- recent active conversation continuation;
- obvious ambient/noise fragments.

This is intentionally conservative and will later be augmented by a semantic/model-based classifier.

### Companion identity/prompt

Added `CompanionPrompt.java` and switched `GrokClient.system()` to it.

Removed the birthday/tamada system identity from the main LLM prompt.

Current base personality:
- calm;
- intelligent;
- warm;
- rare dry humor;
- concise natural Russian speech;
- no automatic games/party behavior;
- no fake tool execution claims;
- no invented memories/identities/camera facts;
- explicit truth that long-term memory is not implemented yet.

`GrokClient` still uses its short in-memory dialogue history. This is temporary working context, not Memory Engine v1.

### Diagnostics added

Companion runtime emits new events including:
- `companion_runtime`
- `companion_listening_start`
- `companion_stt_start`
- `companion_stt_done`
- `companion_stt_error`
- `companion_decision`
- `companion_ignored`
- `companion_ai_start`
- `companion_ai_done`
- `companion_ai_error`
- `companion_turn`
- `companion_cancel`

This gives a first baseline for STT/model latency and routing behavior on the real device.

## CI status

GitHub Actions workflow renamed/configured for the companion branch:
- workflow: `Build Companion Core APK`
- run: #233
- run id: `33507743984`
- job id: `99855750508`
- head: `a29f3f92001c294dd881256bfd7b9e5d8c4be905`
- result: COMPLETED / SUCCESS

Green steps include:
- unit tests and build;
- APK integrity check;
- artifact upload;
- Android 35 emulator launch;
- native `FastVoiceSmokeTest`.

The emulator explicitly launches:
`com.imagine.martinhost.diagnostics/com.imagine.martinhost.CompanionActivity`

Artifact:
- name: `Sergey-AI-Companion-0.11.0-APK`
- artifact id: `9800384758`
- artifact archive digest: `sha256:bfae0a7e0370c406ef8b259ff65b8ab3ee1637dd8a287ab8766a86716b22a666`

Extracted APK from artifact:
- file: `sergey-ai-companion-0.11.0.apk`
- size: `238614769` bytes
- SHA-256: `6746008bac44e29dd0611a6120666dd2d6c42d4525ffc56287e282859d305265`

## What is confirmed working now

Confirmed by CI/emulator:
- new Companion launcher exists and starts;
- new classes compile;
- routing unit tests pass;
- APK installs;
- existing native voice dependency smoke test still passes;
- party activity is no longer launcher;
- Companion Prompt is used by GrokClient.

NOT yet confirmed on a real tablet/phone for 0.11.0:
- quality of real conversational attention gating;
- Bluetooth speaker behavior;
- real STT latency;
- real TTS latency;
- music routing from new CompanionActivity;
- handling of noisy-room ambient speech.

## Important current limitations

### 1. No true barge-in yet

The existing `TurnManager.acceptMicForStt()` only returns true in `LISTENING`.
During `SPEAKING/COOLDOWN`, normal STT is still gated off.

Therefore 0.11.0 does NOT yet meet the final requirement that the user can naturally interrupt the assistant while it speaks.

### 2. No long-term Memory Engine yet

`GrokClient` only keeps a small volatile dialogue history.
There is no durable semantic/episodic/preference/relationship/goal memory yet.

Do not claim that 0.11.0 remembers the user across days.

### 3. Attention is heuristic v1

`AttentionManager` is a deterministic first layer. It can still misclassify natural speech, rhetorical questions and ambient conversations.

Real-device logs are required before making it more aggressive.

### 4. Vision in CompanionActivity is intentionally not connected yet

VISION intent is recognized, but the new Companion runtime currently answers truthfully that visual context is not active. The old camera subsystem remains in legacy code and will be integrated later through `PersonContext`/Context Engine rather than copied blindly.

### 5. Existing infrastructure still has party-era names

Examples:
- package `com.imagine.martinhost`
- `MartinSpeaker`
- `PartyAudioRouter`
- `PartyMusic`

These names are technical debt. Do not rename everything at once while stabilizing audio.

### 6. Music integration is legacy-backed

CompanionActivity can still route through `MusicRequestRouter`, but direct Yandex playback depends on reverse-engineered behavior and needs another real-device verification in companion mode.

### 7. Security/privacy debt

- Yandex OAuth token storage should move to Android Keystore/encrypted storage.
- No formal memory export/delete model exists yet because Memory Engine is not built.
- Future tool permissions need an explicit policy layer.

## NEXT — Priority 1A: true interruption / barge-in

Do this before long-term memory because conversation quality depends on it.

Target design:
1. Separate `capture microphone` from `full STT allowed`.
2. Keep lightweight interrupt detection active during TTS.
3. During assistant speech detect only strong interruption signals first:
   - assistant name + speech;
   - `стоп`;
   - `погоди`;
   - `подожди`;
   - `стой`;
   - deliberate sustained user speech above a stricter threshold.
4. Immediately cancel AI/TTS on validated interruption.
5. Keep pre-roll so the user's continuation is not clipped.
6. Return the captured utterance to normal STT after cancelling assistant speech.
7. Protect strongly against Bluetooth speaker/self-TTS triggering the interruption detector.
8. Add diagnostics:
   - `barge_candidate`
   - `barge_confirmed`
   - `barge_rejected`
   - `tts_cancel_latency_ms`
   - preserved pre-roll duration.
9. Add deterministic unit tests plus emulator build.
10. Verify on real device with speaker.

## NEXT — Priority 1B: improve conversation runtime after barge-in

- semantic attention classifier for ambiguous cases;
- better conversational-session expiry;
- discourse repair (`нет, я про другое`, corrections, partial phrases);
- streaming model response when provider supports it;
- streaming/earlier TTS start;
- measured time from end-of-user-speech to first assistant audio;
- stop relying on broad STT hallucination blacklists as the main echo solution.

## NEXT — Priority 2: Memory Engine v1

Implement memory types:
- working;
- episodic;
- semantic;
- preference;
- relationship;
- goal.

Required pipeline:
`conversation/event -> candidate extraction -> importance score -> type -> dedup/conflict -> temporal metadata -> persistence`

Retrieval target:
`current context -> keyword + semantic + temporal retrieval -> rerank -> small relevant memory bundle`

Memory record requirements:
- id;
- type;
- content;
- created_at;
- valid_from / valid_to where relevant;
- confidence;
- importance;
- source;
- entity/person links;
- last_used;
- supersedes/conflicts-with;
- user-confirmed flag.

Also required:
- encrypted local persistence;
- memory viewer;
- edit/delete;
- clear-all/export path;
- simulated multi-day tests.

## NEXT — Priority 3: Soul Engine

After Memory Engine basics:
- stable base identity;
- bounded warmth/humor/verbosity/formality/initiative/directness parameters;
- slow adaptation;
- per-user relationship state;
- audit why a personality parameter changed;
- reset/edit controls.

## NEXT — Priority 4: Context + controlled proactivity

- Context Engine;
- `ShouldSpeakPolicy` separate from generation;
- time/calendar/goals/device/presence context;
- proactivity budget + cooldown;
- user-selectable proactivity level;
- unfinished-goal callbacks without nagging.

## Later priorities

5. Skill/tool registry and permission layer: reminders, calendar, notes, research, weather, music, messaging, smart home.
6. PersonContext: confidence-based face/voice/appearance/recent-presence fusion.
7. Avatar v1: expressive lightweight presence.
8. Avatar creator / 3D character.
9. Optional encrypted sync + family profiles.
10. Commercial onboarding/subscriptions/skill ecosystem.

## Mandatory workflow rule for future ChatGPT sessions

Before continuing development:
1. Read `docs/COMPANION_VISION.md`.
2. Read this file.
3. Inspect current `companion-core-v1` HEAD and latest CI.
4. Continue from the NEXT priorities rather than reconstructing the project from chat history.
5. After meaningful work, update this file with actual commits/run IDs/test results/limitations.
6. Update `COMPANION_VISION.md` only when product architecture materially changes.
7. Never mark a feature as done unless code/tests or real-device evidence supports it.

## Immediate next action

Implement **Priority 1A — true barge-in** on top of the green 0.11.0 Companion Core baseline, then issue a new test APK and collect a real-device diagnostic log before beginning Memory Engine v1.
