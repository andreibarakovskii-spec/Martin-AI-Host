# imagination — Development Progress / Handoff

Updated: 2026-09-02

Read together with `docs/COMPANION_VISION.md` before continuing. Update this file after every meaningful implementation/test batch.

## Product identity

- Full product/platform name: **imagination**
- Short name and assistant wake/name: **IMA** (Russian pronunciation: «Има»)
- Legacy Martin/Sergey names may remain inside old technical class names while audio is stabilizing, but must not be shown to the user.
- Future requirement: assistant display name and wake/address name must be user-configurable rather than permanently fixed to IMA.

## Repository

- Repository: `andreibarakovskii-spec/Martin-AI-Host`
- Active branch: `companion-core-v1`
- Recovery branches: `voice-orb-v1`, `companion-core-v1-baseline`

## Current version — 0.11.2

- versionCode: 112
- versionName: `0.11.2-ima-repair`
- app label: `imagination`
- diagnostic label: `imagination — тест`
- latest implementation commit: `97eb5e39554052f49c143043315728931417b41e`
- workflow: `Build imagination APK`
- run #250 / run id `33516439563`: SUCCESS
- artifact: `imagination-0.11.2-APK`, artifact id `9803892114`

## 0.11.2 changes

### IMA naming

- UI title changed from Сергей to `IMA`.
- subtitle identifies `imagination`.
- app labels and build artifact renamed.
- `CompanionPrompt` defines the assistant as IMA from imagination.
- `AttentionManager`, `ConversationDirector`, and `BargeInPolicy` recognize `IMA`, `ima`, and Russian `Има`.
- old Сергей/Мартин wake names were removed from the new Companion Core routing path.

### Repair and continuation

`ConversationDirector` now has a dedicated `CONTINUE` decision.

Recognized continuation commands:
- `продолжи`
- `продолжай`
- `договори`

Recognized repair patterns include:
- `не так`
- `я имел/имела в виду`
- `я про другое`
- `не речь, а ...`
- `мне нужна/нужен ...`

Repair text is explicitly marked for the LLM so an STT mistake such as «эпизодическая речь» can be replaced by the corrected «эпизодическая память» instead of starting an unrelated topic.

`CompanionActivity` stores the assistant text that was interrupted. A later `продолжай` asks the model to continue that answer without repeating its beginning.

### Barge-in policy

- strong stop words are evaluated before TTS echo similarity, so a short `стоп` is not rejected merely because the assistant itself used the word;
- IMA name + additional speech is accepted;
- repair phrases remain accepted;
- ambient and likely TTS echo remain rejected.

### Tests added/updated

Unit tests now cover:
- Latin `IMA` direct address;
- Russian `Има` direct address;
- continuation without repeating the name;
- dedicated `CONTINUE` route;
- correction/repair routing;
- IMA interruption phrases;
- stop winning even when the current TTS contains the word `стоп`;
- ambient and TTS echo rejection.

## Real-device findings from 0.11.1

The supplied diagnostic session showed that barge-in was not yet reliable enough to call complete. The main user-visible failure was losing/corrupting the requested term and then answering about «эпизодическая речь» instead of «эпизодическая память». Commands such as `погоди`, `стоп`, and `продолжай` were not handled consistently.

0.11.2 addresses naming, repair semantics, continuation state, and stop-policy ordering. It does **not** yet prove reliable Bluetooth interruption; another real-device diagnostic test is required.

## Newly requested product feature — configurable identity and personal voice

Add a dedicated Identity & Personal Voice system to the roadmap.

### Configurable assistant identity

Required:
- user can change assistant display name;
- user can change wake/address name separately;
- pronunciation hints and alternate aliases;
- changes propagate to AttentionManager/ConversationDirector/BargeInPolicy/prompts/UI without hard-coded names.

### Imported personal voice / voice cloning

Required user flow:
1. choose or record one or more voice samples;
2. check recording quality;
3. complete consent/rights confirmation;
4. derive or create a reusable voice profile using an approved voice-cloning/speaker-conditioning engine;
5. preview generated speech;
6. use that profile as the assistant TTS voice;
7. allow switching voices and permanent deletion.

Important: this is generative TTS based on the supplied voice, not playback of prerecorded phrases.

Privacy/security requirements:
- raw voice recordings and derived voice profiles should be encrypted and local-first where practical;
- never expose samples in diagnostics;
- support complete deletion of source audio and derived profile;
- do not use uploaded voice recordings to automatically infer facts/personality/memories;
- separate voice identity from assistant factual identity/personality.

### Memorial / Legacy Voice use case

The requested use case includes preserving the voice of a deceased loved one so the assistant can speak with that voice. Product wording and behavior must treat this as an AI memorial/legacy voice, not as proof that the deceased person is literally alive or conscious in the assistant.

Required safeguards/product behavior:
- explicit disclosure that speech and responses are AI-generated;
- uploader attests they have appropriate permission/rights to use the recording;
- extra confirmation for a deceased person's voice;
- memorial voice is separate from memory/personality cloning by default;
- assistant does not claim to literally be the deceased person unless a separately designed memorial persona mode exists and is explicitly enabled with appropriate safeguards;
- user can instantly switch to a neutral voice and permanently delete the memorial voice profile.

Future optional extension: a clearly labeled Legacy Profile using user-supplied stories/photos/memories. This would remain an AI reconstruction based on provided material, not a continuation of consciousness.

## Still not complete

1. Stop detection still relies on remote STT after an audio candidate; a true local keyword interrupter is not implemented yet.
2. Bluetooth AEC/echo rejection remains device-dependent.
3. Exact interruption latency must be measured on the real device.
4. Streaming LLM/TTS is not implemented.
5. Long-term Memory Engine is not implemented.
6. Internal legacy class names and preference key `martin` remain technical debt and should be migrated carefully later.
7. Camera/person context is not active in CompanionActivity.
8. Tokens/keys still need Android Keystore/encrypted storage.
9. Assistant name is still hard-coded to IMA in 0.11.2; configurable identity is roadmap work.
10. Imported/personal voice cloning is roadmap work and not implemented in 0.11.2.

## Next real-device test — 0.11.2

1. Say: `IMA, расскажи подробно, что такое эпизодическая память.`
2. During speech say: `IMA, погоди, я про другое.`
3. Correct it with: `Нет, не речь, а память.`
4. Say: `продолжай` and verify that IMA continues the interrupted topic.
5. Start another long reply and say only: `стоп`.
6. Let IMA speak without interruption and check that it does not stop from its own speaker output.
7. Export diagnostics.

Inspect:
- `companion_runtime` with `brand=imagination;assistant=IMA`
- `companion_decision` reasons `repair` and `continue_previous_answer`
- `barge_candidate`
- `barge_audio_ready`
- `barge_stt_start`
- `barge_confirmed`
- `barge_rejected`
- `tts_cancel_latency_ms`

## Next development priority

After the 0.11.2 real-device log:
1. tune interrupt capture/AEC thresholds;
2. add a local fast stop/pause keyword detector if remote STT remains slow;
3. add end-of-user-speech to first-audio latency metrics and streaming response/TTS;
4. begin Memory Engine v1;
5. refactor hard-coded IMA naming into a persistent AssistantIdentity profile;
6. evaluate/implement a consent-aware Personal Voice pipeline and memorial/legacy voice mode.
