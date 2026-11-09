#### 把 Android 手机**正在播放的声音**实时转成字幕并翻译，浮在其他应用上方。

### 应用方向
Live Translate 可以实时监听手机正在播放的音频内容，包括但不限于视频，音乐，语音通话等
### 流程示意图

系统播放 ──► AudioPlaybackCapture ──► 切段 ──► 识别 ──► 翻译 ──► 悬浮字幕
             (MediaProjection)      2.5s/0.36s   云端     云端
             或本地   或本地


### 特性
- 不需要root权限，因为整体是基于模拟屏幕录制来监听系统声音的
- 可以按个人需求选择云端ai传入api或使用内置的本地模型（效果较差，而且对手机性能要求较高，我自己实测骁龙8e5体验一般般，唯一的好处是本地模型不上传云端不花钱）
- 
### 安装
下载后需要给一部分权限，反正会直接要给就行了
> 需要 Android 10（API 29）及以上，arm64-v8a 设备，本地模型推荐至少使用8+gen3以上的设备，具体下限我也不知道，反正别给自己手机爆破了就行
下面配置这个我懒得写了让大肥鱼写的将就看吧
### 配置
## 云端
默认指向硅基流动，任何 OpenAI 兼容服务都能用：*引擎 → 云端 API → 添加自定义接口*。
| 字段 | 值 |
|---|---|
| 接口地址 | `https://api.siliconflow.cn/v1` |
| 识别模型 | `FunAudioLLM/SenseVoiceSmall` |
| 翻译模型 | `tencent/Hunyuan-MT-7B` |
## 本地
在应用内按需下载，之后完全离线。
| 档位 | 识别 | 翻译 | 体积 |
|---|---|---|---|
| 轻量 | Whisper base | Qwen2.5-0.5B | 约 545 MB |
| 均衡 | Whisper small | Qwen3-1.7B | 约 1.6 GB |
| 高质量 | Whisper medium | Qwen2.5-3B | 约 2.6 GB |
CPU 推理是瓶颈：视档位和机型，每句大约 1~4 秒。

> 依赖与环境变量见 [docs/BUILD.md](docs/BUILD.md)。
### 操作界面和使用示范
> 开始界面，点击中间圆形按钮开始翻译，会跟你要录屏权限，请给录制整个屏幕的权限，不是那个单独选一个应用，
> 然后上面两个框可以选择你想要的翻译源语言和目标语言，下面可以选择使用的模型
> <img width="1220" height="2656" alt="Screenshot_2026-11-09-16-21-51-816_io github liv" src="https://github.com/user-attachments/assets/d6efea07-8ac8-4bfe-922f-46ba8ab773bf" />
> 然后这里可以选择你预设的api或者本地模型，正常情况你就开0.5b的那个就行了，其他俩延迟巨几把高
> <img width="1220" height="2656" alt="Screenshot_2026-11-09-16-22-01-732_io github liv" src="https://github.com/user-attachments/assets/66661ced-274e-40c0-98ff-1f19ccc923c6" />
> <img width="1220" height="2656" alt="Screenshot_2026-11-09-16-30-43-135_io github liv" src="https://github.com/user-attachments/assets/bf2296e2-13d3-40e3-afd6-fea0358e9e98" />
> 在主界面点击设置可以选择提词器的样式
> <img width="1220" height="2656" alt="Screenshot_2026-11-09-16-30-46-516_io github liv" src="https://github.com/user-attachments/assets/6f62234c-eeaa-42d7-8120-767b6bfa557e" />
# 实际使用图例
<img width="1220" height="2656" alt="Screenshot_2026-11-09-16-42-21-615_tv danmaku bi" src="https://github.com/user-attachments/assets/3ba5f02c-382f-42e2-9bbc-7dec00e27c07" />





> 依赖与环境变量见 [docs/BUILD.md](docs/BUILD.md)。




## 隐私
- **云端模式** 首先其次，你都用云端了肯定是上传云端服务器的
- **本地模式** 棍睦
## 已知限制
- 主动禁止被录制的内容（多数 DRM 视频、部分音乐应用）抓不到声音。
- 部分定制系统需要额外放开悬浮窗和后台运行权限。

## 许可
[MIT](LICENSE)。第三方组件与模型授权见 [docs/BUILD.md](docs/BUILD.md#6-第三方与模型授权)。
---
<sub>与文中提到的模型、接口服务商均无隶属关系。</sub>
