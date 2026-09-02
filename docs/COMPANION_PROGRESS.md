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

## Current version — 0.11.3

- versionCode: 113
- versionName: `0.11.3-voice-latency`
- app label: `imagination`
- diagnostic label: `imagination — тест`
- latest implementation/test commit before this handoff: `9e5a2ed5764ff4671f6a79ad82c55592fdac4eb3`
- workflow: `Build imagination APK`
- run #262 / run id `33609132454` was in progress when this handoff was written; re-check before claiming success.
- expected artifact: `imagination-0.11.3-APK`

## 0.11.3 — Voice latency / natural interruption

This release is based on the real-device 0.11.2 diagnostic session. The observed failure was no longer basic cancellation: once a barge-in command had been transcribed, TTS cancellation was fast, but the user still perceived a long delay because remote STT validation happened before cancellation. Natural multiword interruptions were also rejected by the old explicit-only policy, and first local-TTS audio could take several seconds on long chunks.

### Speculative barge pause

`MartinSpeaker` now exposes optional `pauseForBargeIn()` / `resumeAfterBargeIn()` methods.

`MartinNeuralSpeaker` implements them using `AudioTrack.pause()` / `play()` while retaining playback position.

`CompanionActivity` now pauses neural playback immediately when `ContinuousSpeechEngine` emits an interrupt audio chunk, before waiting for remote STT:

1. capture interrupt candidate;
2. immediately pause current AudioTrack when supported;
3. transcribe candidate;
4. if rejected as echo/ambient, resume from the same playback position;
5. if accepted, cancel the old response and route the user phrase normally.

New diagnostics:
- `barge_speculative_pause`
- `barge_playback_pause`
- `barge_playback_resume`
- `barge_rejected` includes `resumed=`
- `barge_confirmed` includes `speculative_pause=`

This should remove the remote-STT delay from the *audible* stop response on the local neural speaker. System Android TTS does not currently support position-preserving speculative pause and falls back to the old validation behavior.

### More natural barge-in policy

`BargeInPolicy` now:
- keeps explicit stop/pause words as strongest signals;
- accepts IMA variants including common STT form `Иму`;
- rejects probable TTS echo before accepting generic conversational speech;
- accepts a non-echo meaningful phrase with at least 3 words / sufficient text length as conversational barge-in;
- keeps tiny acknowledgements such as `ага`, `угу`, `ок` from interrupting.

Unit tests cover the real-device phrase `Я живу в Дзержинске`, IMA STT variant `Иму`, explicit stop, echo rejection and weak ambient rejection.

### Lower TTS startup latency

`SpeechChunks` was changed from long sentence buffers to latency-oriented chunks:
- first generated chunk targets roughly <=56 characters and prefers punctuation boundaries;
- later chunks target roughly <=110 characters;
- full requested text is still preserved.

The local Supertonic/sherpa engine keeps `numSteps=5` for voice quality but now uses 4 CPU threads instead of 2.

`MartinNeuralSpeaker` prefetches the next chunk while the current one plays as before.

New metric:
- `response_ready_to_first_audio_ms` measures time from completed LLM response to TTS playback start.

### Runtime marker

Expected:
`companion_runtime: v1.3;brand=imagination;assistant=IMA;barge=speculative-pause;natural_interrupt=true;tts_startup_chunks=true`

## 0.11.2 retained behavior

- IMA / imagination user-facing naming.
- explicit correction/repair routing.
- interrupted answer state and `продолжи` / `продолжай` / `договори`.
- local STOP/MUSIC/VISION routing.
- strict acoustic interrupt candidate monitor with AEC/NS where supported.

## Real-device validation required for 0.11.3

CI/emulator can prove compilation, installation, launcher startup and native voice smoke. It cannot prove Bluetooth echo behavior or actual latency on the target Realme device.

Test sequence after installing 0.11.3:
1. Ask IMA for a long explanation.
2. While it speaks, say a normal sentence without `стоп`, e.g. `Я живу в Дзержинске`.
3. Verify speech audibly pauses before cloud STT has completed and the new sentence is processed.
4. Start another answer and say only `стоп` / `подожди`.
5. Let IMA speak with no user speech and verify TTS echo does not cause permanent interruption; if a false candidate occurs it should resume.
6. Compare `response_ready_to_first_audio_ms` and `tts_synthesis_end` against the 0.11.2 log.
7. Export diagnostics.

Inspect:
- `barge_speculative_pause`
- `barge_playback_pause`
- `barge_playback_resume`
- `barge_confirmed`
- `barge_rejected`
- `response_ready_to_first_audio_ms`
- `tts_synthesis_start/end`
- `tts_chunks`
- `playback_start`
- `mic_health`

## Still not complete

1. A true local speech keyword recognizer for `стоп/погоди` is still not implemented; 0.11.3 uses speculative playback pause to hide most remote-STT latency instead.
2. Bluetooth AEC/echo rejection remains device-dependent.
3. Exact audible interruption latency must be measured on the real device.
4. Grok responses are still non-streaming; generation must complete before local TTS starts.
5. Local TTS remains whole-chunk inference, not sample-streaming neural synthesis.
6. Long-term Memory Engine is not implemented.
7. Camera/person context is not active in CompanionActivity.
8. Tokens/keys still need Android Keystore/encrypted storage.
9. Assistant name is still hard-coded to IMA; configurable identity is roadmap work.
10. Imported/personal voice cloning is roadmap work.

## Future configurable identity / Personal Voice

Required:
- change display and wake names;
- pronunciation hints/aliases;
- imported voice recordings -> reusable generative TTS profile;
- encrypted local-first source/profile storage where practical;
- consent/rights confirmation and complete deletion;
- clearly disclosed Memorial / Legacy Voice mode for voices of deceased loved ones, kept separate from factual personality/memory cloning by default.

## Next development priority

After the 0.11.3 real-device log:
1. tune false-positive speculative pauses and echo threshold from actual Bluetooth data;
2. decide whether a local stop/wake recognizer is still needed after measuring perceived latency;
3. implement streaming LLM output into incremental TTS so synthesis can begin before the full answer is generated;
4. then begin Memory Engine v1;
5. refactor hard-coded IMA naming into persistent `AssistantIdentity` profile;
6. evaluate/implement consent-aware Personal Voice pipeline.
