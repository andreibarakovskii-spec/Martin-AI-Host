# Third-party assets and license audit

## Martin v2.3 production model
- Geometry: generated procedurally in Blender by repository scripts (`build_martin_v21.py` + `build_martin_v23.py`).
- Rig and animations: generated procedurally by repository scripts.
- Fur/material detail: generated procedurally in Blender/Godot; no purchased texture pack is embedded.
- Typeface used for the small runtime `M` / `MARTIN` mesh: Blender built-in font converted to mesh during generation.
- Runtime engine: Godot Engine (MIT license).
- Authoring/build tool: Blender (GPL; generated model output is not required to be GPL).
- Paid, trial-only, watermarked, or non-commercial assets: **none**.

## Legacy / optional CC0 source kept for provenance
- Asset: Cat Pilot (Rigged + Animated)
- Author: Tomcat94
- Source: https://opengameart.org/content/cat-pilot-rigged-animated
- License: CC0 / public domain
- Status: retained as a free fallback/provenance reference; Martin v2.3 production geometry does not require this asset.

The production target is safe to build without any paid asset dependency. CC0/public-domain material is preferred whenever an external asset is introduced later.
