# AI Companion — Product Vision and Architecture

Updated: 2026-09-02

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
12. Assistant identity is user-configurable: name, wake name, voice and later avatar/personality can be changed without rebuilding the app.
13. Voice cloning/personal voice must be consent- and rights-aware, clearly disclosed, revocable, and never presented as proof that a real deceased or absent person is literally present.

## Human-like conversation behavior model

The companion should behave like an attentive participant in a room conversation, not like a wake-word command parser.

For every audible segment the runtime should build a confidence-based hypothesis over:
- speech vs non-speech/background noise;
- assistant self-speech/echo vs external speech;
- addressed-to-IMA vs conversation-between-people vs unrelated ambient media/noise;
- current speaker identity/hypothesis when available;
- relation to the current topic;
- continuation, correction, interruption, topic shift or new topic;
- whether IMA should answer, only remember the utterance temporarily, or ignore it.

Required behavior:
1. Suppress irrelevant acoustic background before STT where possible and reject non-conversational/low-confidence segments after STT.
2. Keep a short-lived working-memory transcript for the active room conversation, separate from durable memory.
3. A relevant utterance from another person can enter working memory even when it is not addressed to IMA.
4. IMA does not need to speak after every relevant utterance; it may simply listen and use it later.
5. If the group naturally changes topic, working memory should switch active-topic state instead of dragging the previous subject back.
6. If a new participant appears, the context should gain a new speaker hypothesis and IMA should adapt to the group conversation without pretending to know the person's identity.
7. Identity must be confidence-based and may combine voice, face, clothing/body continuity and explicit self-identification; low confidence means ask rather than invent.
8. The final response model receives only a compact relevant context bundle, not an unbounded raw room transcript.

The conversation state should therefore contain at least:
- active topic + topic history/revision;
- recent relevant utterances;
- speaker hypotheses and confidence;
- who IMA believes is being addressed;
- unresolved references/corrections;
- current social mode (one-to-one, group discussion, command/task, idle room);
- last interruption / unfinished assistant response;
- temporary facts mentioned in this conversation.

This working state should decay over minutes and should not automatically become long-term memory.

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

## 5. Identity and Voice Personalization

Identity must become a first-class configurable profile rather than hard-coded text.

User-facing settings should support:

- changing the assistant display name;
- changing the wake/address name independently;
- pronunciation hints and aliases;
- selecting a built-in voice;
- importing a personal voice sample when the user has the right to use it;
- previewing and deleting imported voices;
- per-profile voice/personality pairing;
- later linking the selected identity to a visual avatar.

### Personal Voice / Voice Clone

The user may import one or more clean voice recordings. The system should create a reusable speaker/voice profile and generate future speech in that voice rather than replaying canned clips.

Technical direction:

voice sample(s) -> quality/speaker checks -> consent/rights flow -> speaker embedding or approved voice-clone model -> encrypted voice profile -> TTS generation -> watermark/provenance marker where supported

Requirements:

- accept common audio formats and normalize to the model-required format;
- provide recording guidance and a quality score;
- support multiple clips to improve similarity;
- keep raw recordings local/encrypted by default when architecture permits;
- allow complete deletion of raw samples and derived voice profiles;
- prevent imported samples from silently changing the factual/personality memory of the assistant;
- allow the assistant name and cloned voice to be changed independently.

### Memorial / Legacy Voice mode

A potential use case is a family choosing to preserve the voice of a deceased loved one and using that voice for the assistant. This must be designed as an **AI memorial/legacy voice**, not as a claim that the person literally lives inside the system.

The product should therefore:

- clearly disclose that responses are generated by AI;
- require the uploader to attest that they have appropriate rights/permission to use the recordings;
- provide extra confirmation before enabling a deceased person's voice;
- avoid automatically claiming to be that deceased person unless a separately designed, explicitly enabled memorial persona exists with appropriate safeguards;
- keep voice cloning separate from memory/personality cloning by default;
- provide a one-tap way to switch back to a neutral voice and permanently delete the memorial voice profile;
- consider local-only processing/storage for the most sensitive memorial material where practical.

This can later grow into a richer Legacy Profile with user-supplied stories, photos and memories, but that must remain clearly identified as an AI reconstruction based on supplied material, never a factual continuation of the person's consciousness.

## 6. Skills / Agent actions

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

## 7. Controlled Proactivity

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

## 8. Person and environment perception

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

## 9. Avatar roadmap

### Avatar v1
Lightweight expressive Orb/presence: listening, thinking, speaking, idle/breathing, attention and emotion cues.

### Avatar v2
Character creator inspired by game avatar builders: face/body style, hair, clothes, voice, name and personality presets.

### Avatar v3
Full 3D avatar using a standard character format such as VRM where suitable, facial blendshapes, eye gaze, lip sync, gestures, idle animation and emotion state.

Do not block Companion Core development on avatar realism.

## 10. What to reuse from Martin/Sergey

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

## 11. Development stages

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

### Stage 6 — Identity & Personal Voice
Configurable assistant name/wake name, built-in voice selection, imported personal voice profiles, consent/rights flow, encrypted storage/deletion and a clearly disclosed memorial/legacy voice mode.

### Stage 7 — Multimodal person context
Reliable identity hypotheses and room/person context without trusting camera tracker IDs.

### Stage 8 — Avatar v1
Expressive lightweight visual presence synchronized with listening/thinking/speaking.

### Stage 9 — Avatar creator / 3D
Customizable characters, voices and visual personality.

### Stage 10 — accounts/sync/family
Optional encrypted sync, multiple devices and separate household-member memory/relationships.

### Stage 11 — ecosystem/commercialization
Skill ecosystem, subscriptions, onboarding, privacy controls, telemetry/quality metrics and distribution.

## 12. First sellable MVP

The MVP is intentionally narrower than the final vision.

A tablet stays in the home and connects to a speaker. The user can wake the assistant by name and talk naturally for 20–30 minutes. They can interrupt it. It remembers important information across days and can correctly understand references to prior discussions. It can search, remember a note, create a reminder and play music. It has a lightweight living visual presence.

If this experience is substantially more personal and coherent than a smart speaker, it is already commercially testable.

## 13. Business model hypothesis

Do not market it as "ChatGPT in a tablet". Position it around persistent personal continuity.

Potential tiers:

- Free: basic AI, limited memory, basic avatar/presence.
- Plus: long-term memory, premium voice, controlled proactivity, tools, richer avatar customization and optional sync.
- Family: multiple recognized household members with separate memories/preferences/relationships.
- Legacy add-on: optional personal/memorial voice profile with stronger privacy, consent and deletion controls.

Initially sell software and recommend compatible tablets/speakers instead of manufacturing hardware. A dedicated hardware bundle can come later after product-market fit.

## 14. Defensible technology

The durable competitive advantage should be the combination of:

**Personal Memory Graph + Relationship Model + Adaptive Personality + Proactivity Policy + Interaction History**

LLM providers, voices and avatars can be replaceable infrastructure. A user's accumulated, privacy-controlled relationship and memory with their companion is the valuable layer.

## 15. Immediate implementation decision

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
