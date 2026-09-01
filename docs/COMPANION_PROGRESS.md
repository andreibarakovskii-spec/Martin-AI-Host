# AI Companion — Development Progress / Handoff

Updated: 2026-09-01

This file is the operational handoff between ChatGPT conversations. **Update it after every meaningful development batch, test result, architecture change or newly discovered blocker.** A new conversation should read this file together with `docs/COMPANION_VISION.md` before changing the project.

## Current direction

The birthday/party-host phase is finished. The product is being transformed into a persistent personal AI companion for an Android tablet + external/Bluetooth speaker.

Primary objective now: make conversation, memory and adaptation dramatically better than a command-oriented smart speaker before investing heavily in a 3D avatar.

## Repository state at pivot

Repository: `andreibarakovskii-spec/Martin-AI-Host`

Existing active development branch before the companion refactor: `voice-orb-v1`

Last tested party-era app version: `0.10.5`

Last known green party-era commit before these documentation commits: `ec2f3dff13535788325c7b49fb9d0c54873ceb81`

Last known full CI: GitHub Actions run #230 — unit tests/build/APK check/Android 35 emulator launch passed.

Documentation pivot commit:
- vision created in commit `17df1bc4bba71363843ff6f85859be2300b41052`

## What already works / reusable foundation

- Android APK project and CI pipeline.
- Voice Orb style lightweight UI.
- Microphone/STT pipeline experiments.
- ContinuousSpeechEngine / TurnManager concepts.
- TTS/speaker output.
- Audio gating so the assistant does not simply transcribe all of its own TTS.
- Bluetooth/external speaker experience.
- Diagnostic event logs used successfully for real-device debugging.
- Direct Yandex Music playback path proven on a real phone in 0.10.3/0.10.4 era tests.
- Music stream retry/fallback work.
- Basic camera face tracking and explicit name binding experiments.
- GuestAppearanceMemory experiments.
- EveningMemory prototype demonstrating persistent structured state.
- Real-device lessons about Whisper hallucinations, self-hearing, trackingId instability, lost first words and rigid intent parsing.

## What should NOT remain core behavior

- PartyDirector as the main conversation controller.
- Birthday/tamada assumptions.
- Contest scheduling.
- Scorekeeping as a primary feature.
- Automatic party game offers.
- Melody game state machine in the core assistant.

These can later become optional skills if useful.

## Most important lessons from real tests

1. A voice assistant cannot depend on exact command phrases. Natural paraphrases and ASR errors must be handled semantically.
2. Camera `trackingId` is temporary and cannot represent identity.
3. Music/speaker audio creates ASR hallucinations and self-hearing; echo/attention handling must be architectural, not a growing blacklist.
4. The user must be able to interrupt TTS naturally; simple STT gating is not enough.
5. First-word loss after switching listening states is highly noticeable.
6. Logs need explicit timestamps for VAD, STT start/end, model request, first token/audio, TTS, interruption and tool execution.
7. Memory must not be raw transcript storage. It needs extraction, types, importance, conflict/update handling and temporal validity.

## New architecture target

See `docs/COMPANION_VISION.md` for the full specification.

Main modules to introduce/refactor toward:

- `CompanionRuntime`
- `RealtimeVoiceEngine`
- `AttentionManager`
- `ConversationDirector`
- `MemoryEngine`
- `MemoryRetriever`
- `MemoryExtractor`
- `SoulEngine`
- `ContextEngine`
- `ShouldSpeakPolicy`
- `SkillRegistry` / `ToolRouter`
- `PersonContext`
- `AvatarState`

Names are provisional; architecture matters more than exact class names.

## NEXT — Priority 0: preserve and measure baseline

Before destructive refactoring:

- keep the last known working 0.10.5 state recoverable/tagged or otherwise easy to compare;
- define companion-specific diagnostics and latency metrics;
- create regression tests for the working microphone/audio lifecycle;
- decide whether `companion-core-v1` is a new branch from the stable state or a clean module/refactor within the existing branch.

## NEXT — Priority 1: Companion Core v1

Goal: a natural 20–30 minute voice conversation without party logic.

Tasks:

1. Remove PartyDirector from the default conversation path.
2. Add ConversationDirector with explicit decisions: respond / ignore / clarify / tool / memory-recall / continuation.
3. Add attention model so ambient speech does not always trigger a response.
4. Implement true barge-in: microphone can detect user interruption while assistant speaks; cancel generation/TTS immediately.
5. Preserve audio pre-roll to avoid first-word clipping.
6. Improve echo/self-speech handling for Bluetooth speaker use.
7. Stream response/TTS where possible to reduce perceived latency.
8. Add turn diagnostics for every latency stage.
9. Test repeatedly on the actual Android device/tablet and external speaker.

Acceptance target:
- natural free conversation;
- no exact command syntax needed for ordinary dialogue;
- user can interrupt;
- assistant does not answer obvious ambient speech;
- no frequent first-word loss;
- logs make latency/failure cause obvious.

## NEXT — Priority 2: Memory Engine v1

Implement structured memory types:

- working;
- episodic;
- semantic;
- preference;
- relationship;
- goal.

Tasks:

1. Define local encrypted schema.
2. Build memory candidate extractor.
3. Importance/usefulness scoring.
4. Deduplication and contradiction/update rules.
5. Temporal metadata (`created_at`, validity, confidence).
6. Hybrid retrieval: keyword + semantic/vector + recency/time.
7. Rerank retrieved memories before injecting into conversation.
8. Add user-facing memory viewer/edit/delete controls.
9. Never persist every transcript line by default.
10. Add tests across simulated multi-day conversations.

Acceptance target:
- user can refer to meaningful facts/events from previous days naturally;
- outdated facts can be superseded;
- irrelevant memories do not dominate prompts;
- user can inspect and delete remembered information.

## NEXT — Priority 3: Soul Engine

- define stable base identity;
- bounded parameters for humor, brevity, warmth, initiative, formality, expressiveness;
- learn preferences slowly from explicit/implicit feedback;
- separate per-user relationship state from global assistant identity;
- record why a personality parameter changed;
- provide reset/edit controls.

## NEXT — Priority 4: Context + Proactivity

- Context Engine for time/calendar/goals/presence/device state;
- `ShouldSpeakPolicy` independent from the LLM response generator;
- proactivity budget and cooldown;
- unfinished-goal callbacks;
- configurable levels from quiet to proactive;
- never create a system that constantly comments on the user.

## Later priorities

5. Skill/tool architecture: reminders, calendar, notes, web research, weather, music, messaging, smart home.
6. PersonContext: face/voice/appearance/recent-presence fusion with confidence and explicit confirmation.
7. Avatar v1: expressive lightweight visual presence.
8. Avatar creator / 3D VRM-style characters and lip sync.
9. Optional encrypted cloud sync, accounts and family profiles.
10. Commercial onboarding, subscriptions and skill ecosystem.

## Commercial hypothesis

Positioning: not "ChatGPT on a tablet" and not another Alice/Siri clone.

Promise: **a personal AI companion that remembers you, adapts to you, develops continuity with you and can act for you.**

Potential tiers:
- Free: limited memory/basic presence;
- Plus: long-term memory, better voices, tools, proactivity, avatar customization/sync;
- Family: separate recognition, memories and relationships for household members.

Initially use ordinary Android tablets and speakers. Do not manufacture hardware before product-market fit.

## Current known technical debt / limitations

- Current code still contains many Martin/party-era class names and assumptions.
- STT is gated during assistant speech rather than supporting robust barge-in.
- Background recognition/lifecycle behavior needs redesign.
- Current memory prototypes are not sufficient for long-term personal memory.
- Camera identity continuity remains experimental.
- Yandex Music direct API path relies on unofficial/reverse-engineered behavior and can change.
- OAuth token storage should be hardened with Android Keystore/encrypted storage.
- No mature permission model exists yet for future agent actions.
- No formal privacy/export/delete model exists yet for long-term personal memory.

## Mandatory workflow rule for future ChatGPT sessions

Before continuing development:

1. Read `docs/COMPANION_VISION.md`.
2. Read this file.
3. Inspect the current branch/HEAD and recent relevant changes.
4. Continue from the listed NEXT priorities rather than reconstructing the project from chat memory.
5. After meaningful work, update **this file** with what was actually done, tests/run IDs, remaining bugs and next tasks.
6. If product architecture changes materially, update `COMPANION_VISION.md` too.
7. Never write "done" here unless code/tests or a real-device result actually support it.

## Immediate next action

Start **Companion Core v1**. First make a recoverable baseline, map the existing audio/conversation classes and replace the party-oriented default dialogue routing with the new ConversationDirector architecture while preserving working audio and diagnostics.
