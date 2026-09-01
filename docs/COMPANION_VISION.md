# AI Companion — Product Vision and Architecture

Updated: 2026-09-01

## New project direction

The project is no longer primarily a party host. The goal is a persistent personal AI companion for Android tablets, connected to a speaker, that feels closer to a science-fiction virtual assistant than a command-based smart speaker.

The core product promise:

> A personal AI that lives with the user, remembers them over months and years, adapts its communication and personality, understands context, can act through tools, and eventually has a customizable visual avatar.

The main differentiation must not be a prettier UI or a larger list of voice commands. The moat should be: **natural conversation + durable personal memory + relationship model + adaptive personality + controlled proactivity + skills/actions**.

## Product principles

1. Voice-first, hands-free interaction.
2. Natural conversation rather than command syntax.
3. The user can interrupt the assistant while it is speaking.
4. The assistant must not respond to every ambient sentence.
5. Memory is structured, selective, temporal and user-controllable — not a raw transcript dump.
6. Personality adapts slowly and predictably instead of randomly rewriting itself.
7. Privacy by design: sensitive personal state should prefer encrypted local storage; cloud use must be explicit and separable.
8. The assistant should know when to speak and when to remain silent.
9. Capabilities are modular skills/tools, not hard-coded into one giant prompt.
10. Avatar development comes after conversation and memory feel genuinely alive.
11. Every major stage is tested on a real Android tablet/phone before the next stage.

## Target experience

A tablet can stay in a room as the assistant's physical presence. Audio is routed to a Bluetooth speaker. The assistant can wake by name, listen continuously when appropriate, identify the current user with confidence estimates, remember prior conversations, follow unfinished goals, and execute useful actions.

Example:

User: "I forgot to order the boxes again."

A weak assistant immediately explains how to order boxes. Our assistant understands this may be a remark, remembers the relevant business context, and — depending on learned preference and current context — can wait, ask "Add it for tomorrow?", or do nothing.

Another day it can naturally refer to an unfinished item: "You wanted to check the second memory design. We never finished that."

## 1. Realtime Voice Engine

Pipeline:

microphone -> echo/self-speech protection -> VAD -> wake/attention -> STT or realtime speech model -> conversation brain -> streaming response -> TTS/speech -> speaker

Requirements:

- local wake word where practical;
- robust VAD;
- true barge-in;
- immediate cancellation of generation/TTS after interruption;
- audio pre-roll so first words are not lost;
- protection against hearing its own speaker output;
- Bluetooth audio routing;
- streaming partial processing;
- target perceived reaction latency roughly 0.5–0.8 s where network/model conditions allow;
- detailed diagnostic timeline for every turn.

Existing Martin/Sergey work to reuse: microphone handling, ContinuousSpeechEngine ideas, TurnManager, STT integration, speaker/TTS integration, Bluetooth/audio routing experience, diagnostics and Voice Orb states.

## 2. Conversation Brain

Do not make one LLM prompt responsible for everything. Introduce a Conversation Director before final response generation.

For each detected utterance it should estimate:

- is this addressed to the assistant or ambient speech?
- conversation vs question vs command vs continuation;
- whether an action/tool is requested;
- whether memory retrieval is required;
- emotional/social intent;
- whether clarification is needed;
- whether a response is useful at all;
- expected response length/style;
- interruption/repair intent such as "wait", "no, I meant...".

The assistant should support natural discourse repair, references such as "that thing from yesterday", incomplete phrases and topic continuation.

## 3. Memory Engine — core product moat

Memory must be separated into types.

### Working memory
Recent conversation and current situation: active topic, people present, current task, recent tool calls.

### Episodic memory
Events and conversations tied to time: what happened, when, with whom and why it may matter later.

### Semantic memory
Relatively stable facts about the user, projects, places, objects and relationships.

### Preference memory
Learned interaction preferences: desired brevity, humor tolerance, preferred workflows, recurring choices and corrections.

### Relationship memory
People/entities and their relationships to the user and assistant. Identity must not depend on a transient camera tracking ID.

### Goal memory
Active goals, unfinished tasks, commitments, decisions and checkpoints.

### Memory write pipeline

conversation/event -> candidate extraction -> importance/usefulness score -> type classification -> dedup/conflict check -> temporal validity -> embedding/index -> relationship links -> encrypted persistence

Do not store every utterance as durable memory.

### Memory retrieval pipeline

current context -> retrieval query plan -> keyword + vector + temporal + relationship retrieval -> reranking -> small relevant memory bundle -> response model

Memory records should support:

- created_at;
- valid_from / valid_to where appropriate;
- confidence;
- source;
- person/entity links;
- importance;
- last_used;
- supersedes/conflicts-with;
- user-confirmed flag;
- delete/edit controls.

Long term direction: temporal/personal memory graph rather than vector search alone.

## 4. Soul / Adaptive Personality Engine

Separate persistent personality state from the LLM system prompt.

Possible controlled dimensions:

- warmth;
- humor;
- initiative;
- verbosity;
- formality;
- emotional expressiveness;
- directness;
- use of callbacks to shared history;
- proactive interruption threshold.

Adapt these slowly from observed user feedback and explicit settings. Keep bounded ranges and audit changes. The model must not freely rewrite its own identity.

The relationship with each household member can differ while the assistant retains one coherent core identity.

## 5. Skills / Agent actions

Architecture:

intent -> planner/router -> permission/risk check -> skill/tool -> result -> memory update if useful -> spoken/displayed response

Initial skills:

- reminders;
- calendar;
- notes;
- web research;
- weather;
- music;
- timers;
- messaging where permissions allow;
- smart-home integrations later.

Design skills as plugins/interfaces so future integrations and MCP-compatible tools can be added without changing the conversation core.

## 6. Controlled Proactivity

Create a Context Engine that can observe permitted signals such as:

- time/day;
- calendar;
- active goals/reminders;
- recent conversation state;
- presence/identity confidence;
- device state;
- unfinished actions.

Then use a separate `ShouldSpeak` policy.

Proactivity needs a budget/cooldown and user-adjustable levels. A useful assistant occasionally reminds or suggests; an uncontrolled one becomes annoying.

## 7. Person and environment perception

Do not use MLKit trackingId as persistent identity.

Future person hypothesis can combine:

- face embedding (with consent);
- voice identity (with consent);
- clothing appearance;
- body/silhouette cues;
- recent presence continuity;
- explicit self-identification.

Output should be confidence-based, e.g. `Andrey 0.87`. At low confidence the assistant asks rather than inventing identity.

Camera analysis should be event-driven/local where possible instead of continuously uploading video.

## 8. Avatar roadmap

### Avatar v1
Lightweight expressive Orb/presence: listening, thinking, speaking, idle/breathing, attention and emotion cues.

### Avatar v2
Character creator inspired by game avatar builders: face/body style, hair, clothes, voice, name and personality presets.

### Avatar v3
Full 3D avatar using a standard character format such as VRM where suitable, facial blendshapes, eye gaze, lip sync, gestures, idle animation and emotion state.

Do not block Companion Core development on avatar realism.

## 9. What to reuse from Martin/Sergey

Keep/rework:

- Android application foundation;
- microphone/audio routing;
- TurnManager concepts;
- VAD/STT work;
- TTS/speaker work;
- Yandex Music integration where legally/technically viable;
- diagnostics;
- camera experiments;
- local settings;
- Voice Orb UI/state machine;
- lessons from self-hearing and first-word loss.

Retire from the main runtime:

- birthday-specific behavior;
- PartyDirector as the primary brain;
- contests and scorekeeping;
- automatic party game offers;
- host/tamada persona assumptions.

Party features may later survive as an optional skill/package rather than core behavior.

## 10. Development stages

### Stage 1 — Companion Core / natural voice
Refactor the current Android project into a general companion. Build reliable continuous conversation, interruption, attention detection and diagnostics.

### Stage 2 — Memory Engine v1
Structured local memory, extraction, retrieval, conflict/update rules, memory viewer/delete controls and tests across multiple days.

### Stage 3 — Soul Engine
Stable base personality plus bounded adaptive communication preferences.

### Stage 4 — Context and proactivity
ShouldSpeak policy, cooldowns, unfinished-goal callbacks and useful contextual interventions.

### Stage 5 — Skills/agent system
Modular tools for reminders, calendar, research, notes, music and other actions.

### Stage 6 — Multimodal person context
Reliable identity hypotheses and room/person context without trusting camera tracker IDs.

### Stage 7 — Avatar v1
Expressive lightweight visual presence synchronized with listening/thinking/speaking.

### Stage 8 — Avatar creator / 3D
Customizable characters, voices and visual personality.

### Stage 9 — accounts/sync/family
Optional encrypted sync, multiple devices and separate household-member memory/relationships.

### Stage 10 — ecosystem/commercialization
Skill ecosystem, subscriptions, onboarding, privacy controls, telemetry/quality metrics and distribution.

## 11. First sellable MVP

The MVP is intentionally narrower than the final vision.

A tablet stays in the home and connects to a speaker. The user can wake the assistant by name and talk naturally for 20–30 minutes. They can interrupt it. It remembers important information across days and can correctly understand references to prior discussions. It can search, remember a note, create a reminder and play music. It has a lightweight living visual presence.

If this experience is substantially more personal and coherent than a smart speaker, it is already commercially testable.

## 12. Business model hypothesis

Do not market it as "ChatGPT in a tablet". Position it around persistent personal continuity.

Potential tiers:

- Free: basic AI, limited memory, basic avatar/presence.
- Plus: long-term memory, premium voice, controlled proactivity, tools, richer avatar customization and optional sync.
- Family: multiple recognized household members with separate memories/preferences/relationships.

Initially sell software and recommend compatible tablets/speakers instead of manufacturing hardware. A dedicated hardware bundle can come later after product-market fit.

## 13. Defensible technology

The durable competitive advantage should be the combination of:

**Personal Memory Graph + Relationship Model + Adaptive Personality + Proactivity Policy + Interaction History**

LLM providers, voices and avatars can be replaceable infrastructure. A user's accumulated, privacy-controlled relationship and memory with their companion is the valuable layer.

## 14. Immediate implementation decision

Do not start with the 3D avatar.

Create/refactor toward `companion-core-v1` and first make stages 1–4 feel genuinely alive. Test real conversations repeatedly on the target Android device, keep diagnostic logs, and evolve behavior from failures observed in actual use.

## Continuity rule for future development

This file is the stable product/architecture specification.

Development status must be maintained separately in `docs/COMPANION_PROGRESS.md` after meaningful work. That progress file should always contain:

- current branch/version/commit;
- what is already working;
- what changed most recently;
- known bugs/limitations;
- next concrete tasks in priority order;
- real-device test status;
- important architectural decisions;
- enough context for a new ChatGPT conversation to continue without relying on old chat history.
