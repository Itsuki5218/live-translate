# 同声译

把手机**正在播放的声音**实时转成字幕并翻译，浮在其他应用上方。不需要 root，不收任何数据。

```
系统播放 ──► AudioPlaybackCapture ──► 切段 ──► 识别 ──► 翻译 ──► 悬浮字幕
             (MediaProjection)      2.5s/0.36s   云端     云端
                                                  或本地   或本地
```

## 特性

- **内录系统声音**：`MediaProjection` + `AudioPlaybackCapture`，Android 10（API 29）以上。
- **两种引擎**
  - 云端：任何 OpenAI 兼容接口，`/audio/transcriptions` + `/chat/completions`。
  - 本地：`whisper.cpp` + `llama.cpp` 直接在手机上跑，三档模型，完全离线。
- **悬浮字幕**：多行堆叠，每行一个独立底框；停留时间、字色、底色可调，可拖动位置。
- **纸面终端界面**：暖白纸底、墨黑排版、单一信号色、零圆角、1px 发丝线、硬投影、机械动效。

## 环境要求

- Android 10（API 29）及以上
- `arm64-v8a` 设备（内置原生库只提供 64 位 ARM）

## 安装

从 [Releases](../../releases) 下载 APK 侧载，然后授权：

1. **麦克风**——`AudioRecord` 即使用内录也需要该权限。
2. **通知**——采集跑在前台服务里。
3. **显示在其他应用上层**——悬浮字幕需要。

点开始翻译，在系统弹窗里选**整个屏幕**，然后切到要看的应用。

## 云端配置

默认指向硅基流动（`https://api.siliconflow.cn/v1`），当时提供免费的识别模型和翻译模型。任何兼容服务都能用：*引擎 → 云端 API → 添加自定义接口*。

| 字段 | 说明 |
|---|---|
| 接口地址 | 以 `/v1` 结尾，应用会自行拼 `/audio/transcriptions` 与 `/chat/completions` |
| API Key | `Authorization: Bearer ...` |
| 识别模型 | 如 `FunAudioLLM/SenseVoiceSmall`、`whisper-1` |
| 翻译模型 | 如 `tencent/Hunyuan-MT-7B`、`gpt-4o-mini` |

## 本地模型

在应用内从 `hf-mirror.com` 下载，只有你点了才会下载。

| 档位 | 识别 | 翻译 | 体积 |
|---|---|---|---|
| 轻量 | Whisper base | Qwen2.5-0.5B | 约 545 MB |
| 均衡 | Whisper small | Qwen3-1.7B | 约 1.6 GB |
| 高质量 | Whisper medium (q5) | Qwen2.5-3B | 约 2.6 GB |

CPU 推理是瓶颈：视档位和机型，每句大约 1~4 秒。

## 构建

不需要 Gradle：

```bash
python build_native.py   # 交叉编译 whisper.cpp + llama.cpp -> build/archives
python build_jni.py      # 链接 app/jni/*.cpp -> app/src/main/jniLibs/arm64-v8a
python build.py          # aapt2 + javac + d8 + apksigner -> livetranslate.apk
```

依赖与环境变量见 [docs/BUILD.md](docs/BUILD.md)。

## 隐私

- **云端模式**会把每段音频上传到你配置的服务商，这是设计使然；请自行选择可信服务，或改用本地模式。
- **本地模式**全程不出设备。
- 应用不收集任何数据，没有统计上报，只在本地保存设置和模型文件。

## 已知限制

- 主动禁止被录制的内容（多数 DRM 视频、部分音乐应用）抓不到声音。
- 部分定制系统需要额外放开悬浮窗、后台运行权限。
- 切段是延迟与完整性的折中：每 2.5 秒或静音 0.36 秒切一次。
- 本地档位的翻译质量明显不如云端大模型。

## 许可

MIT，见 [LICENSE](LICENSE)。第三方组件与模型授权见 [docs/BUILD.md](docs/BUILD.md#6-第三方与模型授权)。
