# Martin Voice Party 0.9.0

Active branch: `voice-orb-v1`. Previous 3D work remains in `archive-martin-3d`.

## What this build implements

- Voice orb driven by microphone RMS and audible PCM amplitude; speech contour uses a 24-band Goertzel spectrum.
- Groq Whisper continuous capture with pre-roll; STT gated during synthesis/playback and Bluetooth tail.
- Groq/xAI conversation: 8 exchanges in RAM, short answers, cancellation on pause/navigation. No background recording.
- Russian Supertonic-3 neural TTS via Soniqo. Model downloads once (~380 MB); playback test in settings. Voice quality and latency depend on the phone. No system TTS fallback.
- Twelve game modes: rules, readiness, rounds, answer checking or explicit organiser scoring, real persistent guest scores.
- Musical quizzes play 6-second clips from user-imported audio. Track metadata editable. Music player: play/pause/next/previous/clear.
- Prepared 90s/00s search list and current Yandex chart link. No remote playlist creation or DRM playback control is claimed.
- Opt-in local camera face presence detection. No identity, speaker identification, emotion inference, recording or cloud video analysis.

## First launch

Android 12+. Configure Groq STT key (a Groq AI key can be reused for STT), then Groq or xAI chat key. Keys stay in private app preferences; backups are disabled. Do not put keys in the repository.
Download/test voice on Wi-Fi in Settings. Add guest names and optional safe facts. Inform guests that speech is sent to Groq and text to the selected AI service.
Import your audio files and edit artist/title/year for musical games. For safe personal privacy, camera is off by default and can be disabled independently. Microphone pauses during host speech; tap stop to interrupt. App must stay in foreground.

Photo-based games are currently **verbal adaptations**, labelled accordingly; photo collages and AI videos are not bundled. Camera is **presence only**, not full video dialogue. No Android-device performance or real-party audio validation is implied by CI.

## Verification

CI builds APK, runs unit tests and attempts an Android 35 emulator launch. Check the actual run result before calling a build verified. The output APK is debug signed; if another signing certificate is installed, export/record settings before uninstalling it.
