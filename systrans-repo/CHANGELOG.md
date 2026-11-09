# Changelog

## 5.0

- New design language: Industrial Editorial Paper Terminal — warm paper, ink type, single signal
  colour, zero radius, hairline rules, corner marks, hard offset shadows, mechanical motion.
- Floating subtitles restyled as printed blocks (paper fill, 1px ink rule, square corners).

## 4.x

- Local models split into three tiers (Whisper base/small/medium + Qwen 0.5B/1.7B/3B).
- Download integrity checks, "re-download this tier", and a model self-test that loads both models
  and reports timings.
- Translation failures now fall back to showing the source line instead of dropping it.
- Qwen3 thinking disabled (`/no_think`) and ` thinking` blocks stripped.
- Whisper entropy/temperature fallback tuned for short chunks.

## 3.x

- Segmentation: 2.5 s hard cut, 0.36 s silence cut.
- Multi-line subtitles with per-line dwell time (default 10 s).
- Per-line highlight blocks; text and block colours configurable.
- Fixed the floating window collapsing to zero height after all lines expired.

## 2.x

- Local inference via whisper.cpp and llama.cpp, statically linked per engine.
- Cloud/local engine abstraction.
- Bottom sheet for engine selection, editable API presets.

## 1.0

- First working version: MediaProjection + AudioPlaybackCapture, cloud ASR + translation,
  single-line floating subtitle.
