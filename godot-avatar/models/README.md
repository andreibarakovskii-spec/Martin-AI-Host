# Martin production avatar slot

Place the production character at `martin.glb` (preferred for Android) or import a VRM and point `AvatarRuntimeLoader.model_path` at it.

The runtime no longer depends on the placeholder mesh. `AvatarRuntimeLoader` instantiates the model and `AvatarModelAdapter` discovers Skeleton3D, AnimationPlayer, meshes, facial blend shapes and visemes.

## Production target
- anthropomorphic grey cat, game-quality but mobile-friendly
- humanoid skeleton suitable for retargeting
- facial morphs: blink L/R, jaw open, smile; Oculus visemes preferred
- 1K PBR texture set; shared materials where possible
- idle/listen/talk/gesture/toast/dance animations may be supplied by the model or retargeted
- commercial/redistribution-compatible license required

Do not commit a third-party model until its redistribution license has been verified.
