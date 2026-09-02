# imagination — Development Progress / Handoff

Updated: 2026-09-02

Read together with `docs/COMPANION_VISION.md` before continuing. Update this file after every meaningful implementation/test batch.

## Product identity

- Full product/platform name: **imagination**
- Current default assistant name/wake name: **IMA** (Russian pronunciation: «Има»)
- Legacy Martin/Sergey names may remain in internal stable classes while audio is being stabilized.
- Future requirement: display name and wake/address name must be user-configurable.

## Repository

- Repository: `andreibarakovskii-spec/Martin-AI-Host`
- Active branch: `companion-core-v1`
- Recovery branches: `voice-orb-v1`, `companion-core-v1-baseline`

## Current version — 0.11.4 Streaming Voice

- versionCode: 114
- versionName: `0.11.4-streaming-voice`
- app label: `imagination`
- diagnostic label: `imagination — тест`
- tested HEAD: `e7363f72aa879c9e7d77a7c1b4f7917e8129f96c`
- workflow: `Build imagination APK`
- run #284 / run id `33616208531`
- job id `100202509576`
- result: COMPLETED / SUCCESS
- unit tests/build: success
- APK integrity: success
- Android 35 emulator launch: success
- native FastVoiceSmokeTest: success, including real sherpa streaming callback verification
- APK artifact: `imagination-0.11.4-APK`, artifact id `9841098856`, archive digest `sha256:144f62c9c557339021c182878ab72657b5ff9a5a3112a7f79e70d29413d49067`
- QA artifact: `imagination-0.11.4-QA`, artifact id `9841219486`
- extracted APK SHA-256: `39531480c6fefacc1f3bb042d2b485c86475240d18b8cb61688b9bc2671060f2`

IMPORTANT: CI/emulator proves build/install/launch and native streaming TTS execution. It does not prove real Bluetooth audible latency or echo behavior on the target Realme device.

## 0.11.4 — what changed

### 1. Hot TTS + speech-start pre-arm

`MartinSpeaker` now has `preArm()`.

`CompanionActivity` calls it as soon as VAD reports the beginning of user speech, before the final STT transcript exists. The local neural engine normally remains loaded in RAM from app startup, so pre-arm does not reload the model; it verifies/maintains the hot voice path and records diagnostics.

Diagnostics:
- `user_speech_prearm`
- `tts_prearm`

Barge-in also pre-arms the next response path while the current playback is being interrupted.

### 2. Grok/xAI/Groq response streaming

`GrokClient` now supports OpenAI-compatible SSE streaming via `replyStreaming()`.

It records:
- `ai_request_start ... stream=true`
- `ai_first_delta_ms`
- final `ai_result`

`CompanionActivity` no longer needs to wait for the complete LLM response before beginning speech. It extracts the first finished sentence, or a bounded natural prefix if the model has not emitted punctuation yet, and sends that prefix to TTS immediately.

When the full response arrives, the already-spoken prefix is removed and only the remainder is spoken. Unit tests cover first-sentence extraction, bounded early prefixes and no-repeat remainder splitting.

Diagnostics:
- `ai_early_speech`
- `companion_ai_done ... stream=true`

### 3. Real PCM streaming from sherpa/Supertonic

The previous sherpa callback received partial `FloatArray samples` but discarded them and used the callback only for cancellation.

`FastVoiceEngine.synthesizeStreaming()` now converts every non-empty callback sample buffer to PCM16 and sends it to the playback sink immediately while inference is still running.

`MartinNeuralSpeaker` keeps one hot model session and one `AudioTrack` for the response. It starts playback on the first PCM callback rather than waiting for a complete generated WAV.

Diagnostics:
- `tts_first_pcm`
- `tts_pcm_push`
- `tts_synthesis_start/end`
- `playback_start/end`

The Android instrumentation smoke test explicitly asserts that real sherpa native inference invokes the streaming callback with non-empty PCM data. Run #284 passed.

### 4. Faster cancellation after interruption

The existing generation token is checked from the streaming PCM callback. When the user interrupts, old inference can stop at a callback boundary instead of needing to finish an entire WAV/chunk before the next request can proceed.

A second full ONNX TTS session was deliberately not added yet. First measure 0.11.4 on the real device; duplicating the model may add hundreds of MB of memory and thermal throttling without being necessary once streaming cancellation works.

### Runtime marker

Expected:
`companion_runtime: v1.4;brand=imagination;assistant=IMA;llm_stream=true;tts_prearm=true;pcm_stream=true;barge=speculative-pause`

## Retained conversation behavior work

The branch also contains the new `ConversationWorkingMemory` / human-like conversation direction work: current topic, recent relevant turns, ambient-vs-conversation handling and groundwork for multi-person dialogue. Reliable speaker identity/diarization is still not implemented and must not be faked.

## Real-device validation for 0.11.4

On the target phone, test:
1. Start talking and verify `user_speech_prearm` appears near speech onset.
2. Ask a normal 2–3 sentence question; measure end of user speech -> `ai_first_delta_ms` -> `ai_early_speech` -> `tts_first_pcm` -> `playback_start`.
3. Compare perceived first-audio latency to 0.11.3.
4. Ask for a long answer and verify the first sentence begins before the complete Grok response is finished.
5. Interrupt mid-answer with a normal new sentence and with `стоп`/`подожди`; verify old PCM stops and the new response starts without waiting for the old inference to finish.
6. Verify no repeated first sentence when the remainder starts.
7. Export diagnostics.

## Still not complete

1. Real Bluetooth first-audio latency for 0.11.4 is not measured yet.
2. Bluetooth AEC/self-echo remains device-dependent.
3. Streaming LLM currently starts TTS on a complete first sentence or bounded prefix, not token-by-token phoneme synthesis.
4. There is still one heavy ONNX TTS session; add a second worker only if real-device logs prove cancellation still blocks new inference materially.
5. A true local keyword recognizer for `стоп/погоди` is not yet implemented.
6. Long-term Memory Engine is not implemented.
7. Speaker diarization / durable voice identity is not implemented.
8. Camera/person context is not active in CompanionActivity.
9. Tokens/keys still need Android Keystore/encrypted storage.
10. Configurable assistant identity and Personal Voice cloning remain roadmap work.

## Next priorities

1. Analyze 0.11.4 real-device diagnostic timing and audible behavior.
2. If first PCM is still slow, benchmark smaller Supertonic model/settings or a different streaming TTS backend without sacrificing voice quality.
3. If interruption still waits on inference cancellation, prototype a two-session TTS pool and measure RAM/temperature/latency before keeping it.
4. Continue Conversation Behavior Engine: semantic topic tracking, `ShouldSpeak`, group-dialogue working memory and speaker diarization.
5. Begin durable Memory Engine v1 after voice turn-taking is sufficiently natural.
6. Implement persistent `AssistantIdentity`, then consent-aware Personal Voice / Legacy Voice.
