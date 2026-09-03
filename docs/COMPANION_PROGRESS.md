# imagination — Development Progress / Handoff

Updated: 2026-09-03

Read together with `docs/COMPANION_VISION.md` before continuing. Update this file after every meaningful implementation/test batch.

## Product identity

- Full product/platform name: **imagination**
- Current default assistant name/wake name: **IMA** (Russian pronunciation: «Има»)
- Legacy Martin names may remain in stable internal classes while audio is being stabilized.
- Future display name and wake/address name must be user-configurable.

## Repository

- Repository: `andreibarakovskii-spec/Martin-AI-Host`
- Active branch: `companion-core-v1`

## Current tested voice build — 0.12.0 IMA Voice / Piper

- versionCode: 120
- versionName: `0.12.0-ima-voice-piper`
- tested HEAD: `3f260f1fb18cb24640f9b03a55ce5e5a983da46e`
- workflow: `Build imagination APK`
- run #292 / run id `33776451303`
- job id `100719243546`
- result: COMPLETED / SUCCESS
- unit tests/build: success
- APK integrity: success
- Android 35 emulator launch: success
- native Piper/VITS FastVoiceSmokeTest: success
- APK artifact: `imagination-0.12.0-APK`, artifact id `9901867875`, archive digest `sha256:be590b194844dc8709e837f6272fb0dd318b0b35a828c2cefd5d6e9bd1162dfd`
- QA artifact: `imagination-0.12.0-QA`, artifact id `9901979902`
- extracted APK SHA-256: `a520658abcc44ec5765e6cb4d1e23f74d52471151da05e918bc1b4d0e3e56be7`

IMPORTANT: emulator timing is not a Realme/tablet benchmark. Real-device Bluetooth latency, thermals and pronunciation still require target-device diagnostics.

## 0.12.0 — app-owned cross-device voice

The production voice path no longer selects Android/vendor TTS. `MartinSpeakerFactory` always creates the app-owned local neural speaker, so Realme/Samsung/Xiaomi/tablet vendor voices cannot change IMA's production voice.

Supertonic was replaced by Russian Piper/VITS `vits-piper-ru_RU-ruslan-medium` through sherpa-onnx 1.13.2. The model package is verified by SHA-256 before installation and stored locally under `ima-voice-v2` after the first download. Required assets are the ONNX model, `tokens.txt`, and `espeak-ng-data`.

Model package:
- official sherpa model asset: `vits-piper-ru_RU-ruslan-medium.tar.bz2`
- download size: about 67 MB
- archive SHA-256: `0690b1cad01f86e8db9ba988af24898bdc1af774e23cb2e46b9c730269b6fd83`
- output sample rate: 22050 Hz
- single consistent Russian voice

The existing pre-arm, Grok SSE streaming, generation cancellation and PCM-to-AudioTrack pipeline are retained.

## Native benchmark from run #292

Android 35 x86_64 emulator, 2 cores; not a handset benchmark:
- Piper full synthesis, `Андрей, с днём рождения!`: **769 ms**, 39,168 samples at 22.05 kHz.
- Piper callback phrase, `Как настроение?`: **359 ms to first callback**, 360 ms total, 44,032 PCM bytes at 22.05 kHz.
- native test result: `OK (1 test)`.

This is substantially below the multi-second Supertonic startup observed on the prior target-device diagnostic, but the actual Realme result must be measured before claiming the same latency on ARM hardware.

## Retained 0.11.4 pipeline

- TTS pre-arm begins when user speech starts.
- Grok/xAI/Groq response uses SSE streaming.
- the first finished sentence/bounded prefix can begin voice output before the complete LLM answer.
- sherpa callback PCM is written to `AudioTrack`.
- barge-in uses speculative playback pause and generation-based cancellation.
- ConversationWorkingMemory / current-topic groundwork remains on the branch.

## Real-device validation for 0.12.0

1. Install 0.12.0 and allow the one-time ~67 MB IMA voice model download/install.
2. Speak for several turns and verify the same embedded voice is used regardless of Android system TTS settings.
3. Measure end of user speech -> STT -> `ai_first_delta_ms` -> `ai_early_speech` -> `tts_first_pcm` -> `playback_start`.
4. Interrupt mid-answer with a normal new sentence and with `стоп`/`подожди`.
5. Check pronunciation, intelligibility, naturalness, CPU temperature and any gaps between chunks.
6. Export diagnostics.

## Known limitations / next priorities

1. Real-device first-PCM/audio latency for Piper is not measured yet.
2. Piper `ruslan-medium` is one male Russian voice; richer emotion/voice choice still needs a later voice layer or Personal Voice pipeline.
3. For short phrases sherpa may emit one callback rather than many tiny PCM callbacks; low latency currently comes primarily from faster VITS inference.
4. First model installation needs network access; after installation inference is local.
5. Bluetooth AEC/self-echo remains device-dependent.
6. The streamed LLM answer still needs a unified appendable TTS response session so early speech and remainder never compete as separate `speak()` generations.
7. Long-term Memory Engine, reliable speaker diarization, active camera person context, encrypted key storage, configurable AssistantIdentity and Personal/Legacy Voice remain incomplete.
8. If ARM latency is still too high, benchmark the official INT8 variant of the same Piper voice before changing architecture again.
