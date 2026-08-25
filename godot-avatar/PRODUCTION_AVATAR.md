# Martin production avatar pipeline

The procedural cat scene is now a fallback/test rig. The production path accepts a rigged `.glb` or `.vrm` avatar through `AvatarModelAdapter.gd`.

## Minimum model contract

- Humanoid or custom rig in `Skeleton3D`.
- A face `MeshInstance3D` with blend shapes.
- Preferred facial naming: ARKit (`jawOpen`, `eyeBlinkLeft`, `mouthSmile...`) and/or Oculus visemes (`viseme_aa`, `viseme_PP`, `viseme_O`, ...).
- 1024px textures for the Android build target.
- AnimationTree states named to Martin's behaviour vocabulary where available: `idle`, `listening`, `thinking`, `talking`, `happy`, `game`, `toast`, `dj`, `dance`.

The adapter intentionally supports aliases so a replacement model does not require changes to Groq, voice, party games, guest memory, scoring, or Android integration.

## Runtime contract

Martin AI emits:

- state
- emotion + intensity
- gesture
- energy
- look target
- speech level / viseme weights

The model adapter maps those values to AnimationTree states and face blend shapes.

## Licensing rule

Do not copy demo avatar meshes/textures from reference repositories unless their asset license explicitly permits our intended use. Reference code can be used only according to its repository license. Prefer an original/licensed Martin model exported as GLB/VRM.
