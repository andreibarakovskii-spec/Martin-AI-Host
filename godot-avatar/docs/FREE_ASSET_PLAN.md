# Martin avatar — free-only asset plan

## Hard requirement
All avatar/model/animation assets used in the distributable APK must be free for commercial use. Prefer CC0. Do not import paid, trial, personal-use-only, non-commercial, or attribution-unclear assets.

## Selected free foundations

### Cat visual/rig reference
- Cat Pilot (OpenGameArt) — CC0/public domain; original Blender model, texture, rig, animations (walk/run/cheer/goggles) and eye open/close blendshapes.
- Use as a CAT anatomy/rig donor or adaptation base, not as the final unchanged Martin look.

### Animation donor
- Quaternius Universal Animation Library — CC0; 120+ humanoid animations and retargetable universal rig.
- Use selected party-safe clips: idle variants, talking/emotes, cheering, dancing/locomotion where appropriate.

### Godot integration
- Existing Martin AvatarModelAdapter + AvatarRuntimeLoader.
- Retarget imported humanoid animation clips to Martin skeleton/BoneMap.
- Facial speech remains driven by MartinBridge speech level / viseme adapter.

## Target Martin customization
- Grey cat fur and darker tabby accents.
- Large expressive green eyes.
- Friendly adult mascot proportions (not childish chibi).
- Black premium host/DJ outfit with violet accent.
- Remove pilot-specific accessories from Cat Pilot base if used.
- Add mouth visemes / jaw-open morph if absent; retain blink shapes.

## Mobile budget
- Prefer <= 25k triangles for final avatar.
- 1K texture atlas preferred; 2K only if visual test proves necessary.
- One main skinned mesh where practical.
- Reuse materials; avoid expensive transparency/fur shells.
- Target 30+ FPS on mid-range Android.

## Required runtime states
idle, listening, thinking, talking, happy, game, toast, dj, dance.

## License gate
Before any third-party asset is committed into `assets/models/`, save provenance + license in `assets/models/LICENSES.md`. CI must reject missing provenance for production model files.
