# AI Companion — Product Vision and Architecture

Updated: 2026-09-04

## New project direction

The project is a persistent personal AI companion for Android phones/tablets and external speakers. Core promise: natural conversation, durable personal memory, relationship model, adaptive personality, controlled proactivity, skills/actions, and later a customizable avatar.

## Product principles

1. Voice-first, hands-free interaction.
2. Natural conversation rather than command syntax.
3. True barge-in/interruption.
4. Do not respond to every ambient sentence.
5. Structured, selective, temporal, user-controllable memory.
6. Slowly adaptive and bounded personality.
7. Privacy by design; prefer encrypted local state.
8. Separate ShouldSpeak policy.
9. Modular skills/tools.
10. Avatar comes after conversation/memory feel alive.
11. Validate major stages on real Android hardware.
12. Configurable identity: display name, wake/address name, voice, later avatar/personality.
13. Personal/legacy voice is consent- and rights-aware, revocable and clearly AI-generated.
14. Production voice must be app-owned and cross-device; Android/vendor TTS is not the primary voice engine.

## Human-like conversation behavior model

IMA should behave as an attentive room participant, not a command parser. Runtime hypotheses should cover speech/background noise, self-echo, addressee, speaker hypothesis, topic relation, continuation/correction/interruption/topic shift and whether to IGNORE, LISTEN_AND_REMEMBER, RESPOND or ASK_WHO. Working conversation state is short-lived and separate from durable memory.

## 1. Realtime Voice Engine

Pipeline:

microphone -> echo/self-speech protection -> VAD -> attention -> STT/realtime speech -> Conversation Director -> streaming LLM -> IMA Voice Engine -> AudioTrack -> speaker

Requirements: local wake word where practical, robust VAD, barge-in, immediate cancellation, audio pre-roll, self-echo protection, Bluetooth routing, streaming processing and detailed per-turn diagnostics. Target perceived reaction latency is roughly 0.5–0.8 s where hardware/network allow.

### IMA Voice Engine — strategic direction

The current Piper/VITS backend is the stable cross-device baseline, not the final voice technology. Build an app-owned generative speech layer whose behavior and voice are independent of Realme/Samsung/Xiaomi/Android system TTS.

Target pipeline:

LLM text stream -> semantic/prosody planner -> normalization/phonemes -> speaker embedding -> acoustic/speech generator -> neural vocoder/codec -> incremental PCM -> AudioTrack

Goals:
- Russian-first natural conversational speech;
- first audible PCM target <300–500 ms after a speakable text fragment on supported ARM hardware;
- incremental synthesis and cancellation at small boundaries;
- emotion/intonation controls derived from conversational intent, not punctuation alone;
- consistent pronunciation and voice across devices;
- one base speech model with replaceable speaker profiles where technically viable;
- future Personal Voice from short clean samples without retraining a full model per user;
- local inference by default for ordinary speech after model installation;
- explicit model/runtime versioning and reproducible benchmarks;
- Piper remains fallback until IMA Voice beats it on latency, Russian quality, stability and resource use.

Do not train a foundation speech model from zero initially. Start from a license-compatible open speech architecture, benchmark candidates on Android ARM, fine-tune/adapt for Russian conversational speech, then progressively own more of the model/runtime. Candidate selection must be based on measured first-PCM latency, real-time factor, RAM, thermals, APK/model size, Russian pronunciation, expressiveness, streaming/cancellation behavior and licensing.

### Voice profile model

Preferred long-term abstraction:

IMA Voice Base + speaker embedding/profile + prosody/emotion state -> generated speech

Voice identity must be separate from factual memory and personality. Switching a voice must not change what the assistant believes or remembers.

## 2. Conversation Brain

Use a Conversation Director before final generation. Estimate addressee, conversation/question/command/continuation, action intent, memory need, social/emotional intent, clarification need, response usefulness/length and interruption/repair. Support natural discourse repair, incomplete phrases, references and topic continuation.

## 3. Memory Engine

Separate working, episodic, semantic, preference, relationship and goal memory. Durable writes use candidate extraction -> importance -> classification -> dedup/conflict -> temporal validity -> index/links -> encrypted persistence. Do not store every utterance. Retrieval combines keyword/vector/temporal/relationship signals and gives the response model a small relevant bundle.

## 4. Soul / Adaptive Personality Engine

Persistent bounded dimensions can include warmth, humor, initiative, verbosity, formality, expressiveness, directness, shared-history callbacks and proactive interruption threshold. Adapt slowly from explicit/implicit feedback; do not let the LLM freely rewrite its identity.

## 5. Identity and Voice Personalization

Identity is a configurable profile: display name, independent wake/address name, pronunciation hints/aliases, built-in voice, imported personal voice, preview/delete, per-profile voice/personality pairing and later avatar linking.

### Personal Voice

voice samples -> quality/speaker checks -> consent/rights flow -> speaker embedding/approved adaptation -> encrypted voice profile -> IMA Voice Engine -> generated speech -> provenance marker where supported

Requirements: common audio formats, recording guidance/quality score, multiple clips, encrypted/local raw recordings where practical, complete deletion, and strict separation from memory/personality.

### Memorial / Legacy Voice

A deceased loved one's voice may be preserved only as a clearly disclosed AI memorial/legacy voice. Require rights/permission attestation and extra confirmation; never imply literal consciousness/presence; keep voice cloning separate from memory/personality reconstruction; support one-tap neutral voice and permanent deletion. A future Legacy Profile may use supplied stories/photos/memories but remains explicitly an AI reconstruction.

## 6. Skills / Agent actions

intent -> planner/router -> permission/risk check -> skill/tool -> result -> optional memory update -> spoken/displayed response. Initial skills: reminders, calendar, notes, web research, weather, music, timers, messaging where permitted, later smart home.

## 7. Controlled Proactivity

Context Engine may observe permitted time/calendar/goals/recent conversation/presence/device/unfinished-action signals. Separate ShouldSpeak policy with cooldown/budget and user-adjustable levels.

## 8. Person/environment perception

Do not use transient ML tracking IDs as identity. Future confidence-based person hypothesis can combine consented face/voice embeddings, clothing/body cues, recent continuity and explicit self-identification. At low confidence ask rather than invent. Prefer event-driven/local camera processing.

## 9. Avatar roadmap

Avatar v1: lightweight expressive presence. Avatar v2: character creator. Avatar v3: 3D/VRM-like character, blendshapes, gaze, lip sync, gestures and emotion. Do not block Companion Core on avatar realism.

### AI AVATAR reference — what to adopt

The external AI AVATAR companion app is a useful product/UX reference, not a code dependency. Useful ideas to adopt:
- avatar identity persists independently from the current chat/session;
- create/customize the avatar separately from conversation runtime;
- emotional state is visible continuously, not only while speaking;
- lip sync should follow actual generated audio timing rather than text timing;
- avatar presentation should be full-screen/simple enough that the character feels present, while voice interaction remains primary;
- customization/outfits can be layered later without changing Companion Core;
- background-call style interaction is valuable because IMA must remain useful when the app is not the foreground focus.

Do not copy its architecture blindly. IMA must keep memory, personality, voice identity, perception and avatar as separate modules. The avatar is the visual body of one persistent companion identity, not the source of memory/personality. Privacy-sensitive person context should remain local-first and user-controlled.

### Avatar runtime contract

Introduce a backend-neutral `AvatarState`/`AvatarController` boundary so Companion Core can drive any future 2D/3D renderer without knowing Godot/VRM implementation details.

Suggested state inputs:
- activity: idle, listening, thinking, speaking, interrupted, sleeping/background;
- emotion: neutral, warm, calm, empathetic, happy, playful, excited, curious, confident;
- energy/intensity: 0..1;
- gaze/attention target;
- speaking amplitude/viseme timing from real PCM;
- short gestures/events from Conversation Director.

The avatar must never block voice startup. If rendering stalls or is disabled, conversation must continue normally.

## 10. Development stages

1. Companion Core / natural voice.
2. Memory Engine v1.
3. Soul Engine.
4. Context/proactivity.
5. Skills/agent system.
6. Identity & Personal Voice.
7. Multimodal person context.
8. Avatar v1.
9. Avatar creator / 3D.
10. Accounts/sync/family.
11. Ecosystem/commercialization.

IMA Voice Engine development runs in parallel with Stage 1 and Stage 6: Piper is the stable baseline while the generative voice engine is benchmarked and introduced behind the same speaker abstraction.

## 11. First sellable MVP

A tablet stays in the home and connects to a speaker. The user can wake the assistant by name, talk naturally for 20–30 minutes, interrupt it, retain important information across days, understand prior references, search, save notes/reminders and play music, with a lightweight living visual presence.

## 12. Business model hypothesis

Do not market as “ChatGPT in a tablet”. Position around persistent personal continuity. Potential tiers: Free, Plus (long-term memory/premium voice/proactivity/tools/avatar), Family, and optional Legacy add-on.

## 13. Defensible technology

Primary moat: Personal Memory Graph + Relationship Model + Adaptive Personality + Proactivity Policy + Interaction History. IMA Voice Engine can become an additional proprietary layer, but model providers remain replaceable infrastructure until our own adapted weights/runtime measurably outperform alternatives.

## 14. Immediate implementation decision

Keep Piper as tested production fallback. Begin IMA Voice Engine v1 as a benchmark-driven parallel backend. First milestone: select a license-compatible architecture, establish Android ARM benchmark harness, prove Russian speech with controllable prosody and <500 ms first-PCM target, then integrate behind the existing speaker interface. Do not remove Piper until the new backend wins real-device tests.

For avatar work, define the renderer-neutral Avatar Runtime contract now, but keep implementation lightweight until natural conversation, ShouldSpeak and Memory Engine v1 are stable. Lip-sync must consume real synthesized PCM/amplitude/viseme data when available.

## Continuity rule

This file is the stable product/architecture specification. `docs/COMPANION_PROGRESS.md` is the operational handoff and must be updated after every meaningful implementation/test batch with branch/version/commit, working features, latest changes, bugs/limitations, next priorities, real-device status and architectural decisions.