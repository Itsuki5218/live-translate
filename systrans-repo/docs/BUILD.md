# Build guide

The project builds without Gradle. Everything is three Python scripts driving the Android SDK
command-line tools directly.

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | `JAVA_HOME` must be set; `javac` and `java` are used |
| Android SDK | build-tools **34.0.0**, platform **android-34** | `ANDROID_SDK_ROOT` |
| NDK | r26 or newer | `ANDROID_NDK_ROOT` |
| CMake | 3.22+ | `CMAKE` |
| Ninja | 1.10+ | `NINJA` |
| Python | 3.9+ | |

`build.py` also needs `tools-r8.jar` in the repository root (a newer R8 than the one shipped in
build-tools 34, which crashes on modern JDKs):

```bash
curl -LO https://maven.google.com/com/android/tools/r8/9.5.29/r8-9.5.29.jar
mv r8-9.5.29.jar tools-r8.jar
```

## 2. Third-party sources

```bash
mkdir -p third_party && cd third_party
curl -L https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz | tar xz
mv whisper.cpp-* whisper.cpp
curl -L https://github.com/ggml-org/llama.cpp/archive/refs/tags/v0.9.0.tar.gz | tar xz
mv llama.cpp-* llama.cpp
```

## 3. Build

```bash
python build_native.py   # -> build/archives/*.a
python build_jni.py      # -> app/src/main/jniLibs/arm64-v8a/{libwhisperjni,libllamajni}.so
python build.py          # -> livetranslate.apk
```

`build.py` runs `aapt2 compile/link`, `javac`, `d8`, `zipalign` and `apksigner`, and packages
`classes.dex` plus every `.so` under `app/src/main/jniLibs/`.

### Path overrides

| Variable | Default |
|---|---|
| `ANDROID_SDK_ROOT` / `ANDROID_HOME` | `./sdk` |
| `ANDROID_NDK_ROOT` | `./sdk/android-ndk` |
| `BUILD_TOOLS` | `$ANDROID_SDK_ROOT/build-tools/34.0.0` |
| `ANDROID_JAR` | `$ANDROID_SDK_ROOT/platforms/android-34/android.jar` |
| `R8_JAR` | `./tools-r8.jar` |
| `WHISPER_SRC` / `LLAMA_SRC` | `./third_party/whisper.cpp`, `./third_party/llama.cpp` |
| `CMAKE` / `NINJA` | found on `PATH` |

## 4. Why there is no Gradle

The app deliberately has **no AndroidX, no Kotlin, no third-party Java dependencies**, so the whole
build is `aapt2` + `javac` + `d8`. This keeps the APK at ~3.4 MB and makes the native libraries the
only non-trivial part.

Both inference engines are linked **statically** into their own `.so` with
`-fvisibility=hidden -Wl,--exclude-libs,ALL -Wl,-Bsymbolic`. whisper.cpp and llama.cpp ship
different `ggml` versions; sharing them as separate dynamic libraries corrupts symbols at runtime.

## 5. Third-party

| Component | Licence |
|---|---|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) | MIT |
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | MIT |
| Whisper models (ggml) | MIT (OpenAI Whisper weights) |
| Qwen2.5 / Qwen3 GGUF weights | Apache-2.0 |

Model files are **not** distributed with this repository; the app downloads them on request from
`hf-mirror.com`. Check each model card before redistributing weights or using them commercially.

## 6. 第三方与模型授权

| 组件 | 授权 |
|---|---|
| whisper.cpp | MIT |
| llama.cpp | MIT |
| Whisper 模型（ggml） | MIT（OpenAI Whisper 权重） |
| Qwen2.5 / Qwen3 GGUF 权重 | Apache-2.0 |

模型文件不随仓库分发，由应用在用户确认后从 `hf-mirror.com` 下载。商用或再分发权重前请阅读各模型页的许可。

## 7. Troubleshooting

- **`unable to make temporary file`** from clang: point `TMP`/`TEMP` at a writable directory.
- **`JAVA_HOME is not set`** from `d8.bat`: export `JAVA_HOME` before running `build.py`.
- **R8 internal error** on JDK 21+: use the standalone `tools-r8.jar` described above.
- **`zipalign` complains about alignment**: rebuild; the script aligns before signing.
