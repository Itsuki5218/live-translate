# Contributing

Thanks for looking. This is a small, deliberately dependency-free Android app; keeping it that way
is part of the design.

## Ground rules

- **No AndroidX, no Kotlin, no third-party Java libraries.** The UI is framework widgets plus XML
  drawables, and the build is `aapt2` + `javac` + `d8`.
- **No custom `onDraw` on views unless it is unavoidable.** A previous iteration used bitmap shaders
  and blur effects and crashed on several GPU drivers. The current UI is plain views and shapes.
- **Every change must keep the app launching.** If you touch `HomeActivity` or a layout, build and
  install it before opening a PR.
- **No telemetry, no analytics, no network calls outside the configured API endpoints and the model
  download URLs.**

## Pull requests

1. Fork, create a branch, keep the diff focused.
2. Run the three build scripts (see [docs/BUILD.md](../docs/BUILD.md)) and state the device/Android
   version you tested on.
3. Describe user-visible changes with a screenshot if the UI moved.
4. By submitting you agree your contribution is licensed under MIT.

## Reporting bugs

Please include:

- Device model and Android version
- Cloud or local mode, and which local tier
- What the status line under the main button says (it echoes API errors verbatim)
- Whether the app captured audio at all (the floating subtitle shows "正在聆听系统声音" when it did)

## Ideas that fit the project

- More cloud presets that are actually free-tier friendly
- Better chunking strategies (VAD alternatives, overlap merging)
- On-device translation alternatives to the current Qwen tiers
- Accessibility: larger type, screen-reader labels, high-contrast palette
