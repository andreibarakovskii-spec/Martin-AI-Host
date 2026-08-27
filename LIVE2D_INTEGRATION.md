# Martin Live2D runtime integration

## Verified upstream (2026-08-24)

- Live2D/CubismJavaSamples supports Android API 21+ and current Android SDK 36.
- CubismJavaFramework supplies model rendering/manipulation framework.
- Live2D Cubism Core for Java is NOT published on GitHub. The official SDK package must provide `Core/android/Live2DCubismCore.aar`.
- A real Cubism model requires model3.json, moc3, textures and optional motions/expressions/physics.

## Martin integration contract

The app state controller maps to model parameters/motions:

- IDLE: breathing + auto blink
- LISTENING: attentive motion + eye tracking
- THINKING: thinking expression/motion
- TALKING: speech motion + ParamMouthOpenY from speech level
- GAME: game motion / microphone accessory
- TOAST: toast motion / glass accessory
- DJ: DJ motion / headphones accessory
- HAPPY: happy expression
- SLEEPING: sleeping expression

## Gate before APK delivery

Do not ship a Live2D APK until all of these exist and pass:

1. `app/libs/Live2DCubismCore.aar` from the official Live2D SDK.
2. A licensed/test Cubism model under `app/src/main/assets/live2d/martin/`.
3. Renderer smoke test confirms model3.json, moc3 and textures load.
4. State test exercises IDLE/LISTENING/THINKING/TALKING/GAME/TOAST/DJ/HAPPY/SLEEPING.
5. TALKING test drives mouth parameter from 0.0 to 1.0.
6. Android build succeeds.

Until (1) and (2) are present, the existing SVG/WebView renderer remains a fallback only and must not be described as Live2D.