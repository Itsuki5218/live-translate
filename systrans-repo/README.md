# 同声译 · Live Translate

Real-time subtitle translation for **system audio** on Android. It captures what your phone is
playing — a video, a stream, an online class — transcribes it, translates it, and floats the
result on top of other apps as small printed blocks.

No root. No screen recording of the whole device. No telemetry.

```
system playback ──► AudioPlaybackCapture ──► VAD chunks ──► ASR ──► translation ──► floating subtitles
                    (MediaProjection)        2.5s / 0.36s   cloud     cloud
                                                            or local  or local
```

## Features

- **Internal audio capture** via `MediaProjection` + `AudioPlaybackCapture` (Android 10 / API 29+).
- **Two engines**
  - *Cloud*: any OpenAI-compatible endpoint — `POST /audio/transcriptions` and `POST /chat/completions`.
  - *Local*: `whisper.cpp` + `llama.cpp` running on the phone, three size tiers, works offline.
- **Floating subtitles**: multiple stacked lines, each with its own block, adjustable dwell time,
  text colour and block colour, draggable anywhere.
- **Editorial paper UI**: warm paper, ink type, one signal colour, zero radius, hairline rules,
  hard offset shadows, mechanical motion.

## Requirements

- Android 10 (API 29) or newer
- `arm64-v8a` device (the bundled native libraries are 64-bit ARM only)

## Install

Grab the APK from [Releases](../../releases), sideload it, then grant:

1. Microphone (`RECORD_AUDIO`) — required by `AudioRecord` even for playback capture.
2. Notifications — the capture runs in a foreground service.
3. Display over other apps — for the floating subtitles.

Start translation, accept the screen-capture prompt (choose **entire screen**), then switch to
whatever you want to watch.

## Cloud configuration

Defaults to SiliconFlow (`https://api.siliconflow.cn/v1`), which offers a free ASR model and a free
translation model at the time of writing. Any compatible provider works; add it in
*Engine → Cloud API → Add*.

| Field | Meaning |
|---|---|
| Base URL | `.../v1` — the app appends `/audio/transcriptions` and `/chat/completions` |
| API key | `Authorization: Bearer ...` |
| ASR model | e.g. `FunAudioLLM/SenseVoiceSmall`, `whisper-1` |
| Chat model | e.g. `tencent/Hunyuan-MT-7B`, `gpt-4o-mini` |

## Local models

Downloads happen in-app from `hf-mirror.com`, and only when you ask for them.

| Tier | ASR | Translation | Size |
|---|---|---|---|
| Light | Whisper base | Qwen2.5-0.5B | ~545 MB |
| Balanced | Whisper small | Qwen3-1.7B | ~1.6 GB |
| Quality | Whisper medium (q5) | Qwen2.5-3B | ~2.6 GB |

CPU inference is the bottleneck: expect roughly 1–4 s per line depending on the tier and device.

## Build

Three scripts, no Gradle required:

```bash
python build_native.py   # cross-compile whisper.cpp + llama.cpp -> build/archives
python build_jni.py      # link app/jni/*.cpp -> app/src/main/jniLibs/arm64-v8a
python build.py          # aapt2 + javac + d8 + apksigner -> livetranslate.apk
```

Prerequisites and environment variables are documented in [docs/BUILD.md](docs/BUILD.md).

## Privacy

- **Cloud mode** uploads each audio chunk to the provider you configured. That is inherent to the
  design; pick a provider you trust, or use local mode.
- **Local mode** never leaves the device.
- The app collects nothing, has no analytics, and stores only its own settings and model files.

## Known limitations

- Apps that opt out of playback capture (most DRM video and some music apps) produce silence.
- Some OEM skins need extra permissions for floating windows and background operation.
- Segmentation trades latency for completeness: chunks are cut every 2.5 s or after 0.36 s of silence.
- Translation quality of the local tiers trails cloud models significantly.

## License

MIT — see [LICENSE](LICENSE). Third-party components and model licences are listed in
[docs/BUILD.md](docs/BUILD.md#5-third-party).

中文说明见 [README.zh-CN.md](README.zh-CN.md).
