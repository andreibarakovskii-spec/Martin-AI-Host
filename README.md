# Martin AI Host

Party-ready Android host built around an embedded Godot 4.7.2 rigged 3D character.

## Production MVP

- real rigged GLB rendered by `GodotFragment` (no 2D portrait on the launcher)
- idle/listening/thinking/talking/happy/game/DJ states and animation actions
- continuous 16 kHz microphone capture with adaptive VAD + 700 ms pre-roll
- Android acoustic echo cancellation / noise suppression when supported
- on-device neural Russian TTS (Supertonic via Soniqo)
- Groq Whisper transcription + Groq/Grok host dialogue
- local-only front-camera face tracking: camera frames are not saved or uploaded; only normalized gaze coordinates reach Godot
- one complete voice-first game wired into the main host: «Что? Где? Когда?» with natural spoken answers and score attribution
- Bluetooth-friendly party audio routing

The visible model is intentionally isolated behind `godot-avatar/models/production/martin.glb`: a better licensed rigged cat can replace this GLB later without changing camera/audio/game logic.
