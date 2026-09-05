# 音频格式白名单与准入规格维护规范

本文档维护当前项目中关于“音频格式扩展名白名单、MIME 类型识别矩阵、通用二进制类型嗅探与短音频过滤门限”的硬编码业务规则。
代码层面的唯一事实来源为：
[`app/src/main/java/com/musicapp/player/core/media/AudioFormatRegistry.kt`](../../app/src/main/java/com/musicapp/player/core/media/AudioFormatRegistry.kt)。

---

## 1. 业务背景与准入目标

在扫描 Android 设备本地媒体库（`MediaStore.Audio.Media`）时，系统数据库中往往混杂了非音乐文件（如通话录音、应用内置提示音、闹钟/通知音、游戏资源片段、语音备忘录等），以及带有缺失或错误 MIME 类型的音频文件。
为了保证媒体库的纯净与稳定播放，应用在准入层（`AudioAdmissionPolicy`）建立统一的准入校验链：
1. **白名单准入**：仅收录明确受原生解码器良好支持的主流音频格式；
2. **容错嗅探**：对于通用二进制 MIME（如 `application/octet-stream`）或空 MIME，自动降级为依据文件扩展名二次判定；
3. **噪音过滤**：排除系统音频（Ringtone/Alarm/Notification）及可选过滤 60 秒以下短音频。

---

## 2. 受支持音频格式与扩展名白名单

代码常量：`AudioFormatRegistry.SUPPORTED_EXTENSIONS`

| 序号 | 扩展名（小写） | 音频编码 / 格式名称 | 适用容器与类型 |
| :--- | :--- | :--- | :--- |
| 1 | `mp3` | MPEG-1/2 Audio Layer III | `.mp3` 容器 |
| 2 | `flac` | Free Lossless Audio Codec (无损) | 原生 FLAC 容器 |
| 3 | `wav` | Waveform Audio File Format (无损/未压缩) | RIFF WAVE 容器 |
| 4 | `aac` | Advanced Audio Coding | ADTS 原始流或 `.aac` |
| 5 | `m4a` | AAC / ALAC in MP4 Container | ISO/IEC 14496-14 MP4 容器 |
| 6 | `ogg` | Ogg Vorbis | Xiph Ogg 容器 |
| 7 | `opus` | Opus Interactive Audio Codec | Ogg 容器内的 Opus 流 |

---

## 3. 具体 MIME 类型识别矩阵

代码常量：`AudioFormatRegistry.SUPPORTED_MIME_TYPES`

由于不同 Android 系统版本、OEM 厂商驱动以及第三方音频抓轨打标工具写入的 MIME 存在别名或兼容前缀（如 `audio/x-flac`），准入层对以下 22 种 MIME 类型予以直接通行：

```text
audio/mpeg, audio/mp3, audio/x-mp3, audio/x-mpeg
audio/flac, audio/x-flac
audio/wav, audio/x-wav, audio/wave, audio/vnd.wave
audio/aac, audio/aacp, audio/x-aac
audio/mp4, audio/m4a, audio/x-m4a
audio/ogg, audio/x-ogg, application/ogg
audio/opus, audio/x-opus
```

- **规范化处理**：
  - 判定前先通过 `substringBefore(';')` 剔除可能附带的编码参数（如 `audio/mp4; codecs=mp4a.40.2`）；
  - 去除两端空白并统一转换为全小写。

---

## 4. 通用二进制 MIME 与降级嗅探策略

代码常量：`AudioFormatRegistry.GENERIC_MIME_TYPES`

在部分 Android 设备上，外置 SD 卡或特定目录下的音频可能被系统 MediaStore 粗暴标记为通用二进制流。以下类型在收到时**不直接拒绝**，而是自动触发文件扩展名二次嗅探（`displayName.substringAfterLast('.')`）：

| 触发嗅探的 MIME 类型 | 说明与常见来源 |
| :--- | :--- |
| `application/octet-stream` | 最常见的未定型二进制字节流 |
| `binary/octet-stream` | 部分旧版本系统文件管理器写入的 MIME |
| `application/unknown` | 通用未知应用数据 |
| `application/x-unknown` | 通用未知数据兼容标记 |
| `audio/*` | 模糊泛用音频通配符 |
| `unknown/unknown` | 异常空值替代符 |
| `null` / 空字符串 | 系统未提供任何 MIME 信息 |

**优先级判定准则**：
- 若提供了**具体**且**非通用**的 MIME（例如 `text/plain` 或 `video/mp4`），以具体 MIME 为准（即使文件名伪装为 `.mp3` 也会被拒绝）；
- 仅当 MIME 为空或属于上述通用类型时，扩展名白名单才生效并接管判定。

---

## 5. 过滤门限与排除规则

### 5.1 短音频过滤门限
- 代码常量：`AudioFormatRegistry.MIN_AUDIO_DURATION_MS = 60_000L`（60 秒）；
- 当用户在设置中勾选“过滤短音频”时，时长 `< 60,000 ms` 的音频将被直接剔除；
- 无论设置如何，时长 `<= 0 ms` 的破损音频始终无条件剔除（`REJECTED_NON_POSITIVE_DURATION`）。

### 5.2 系统音频排除
- 带有 `isRingtone == true`、`isAlarm == true` 或 `isNotification == true` 属性的音频由系统底层标记，应用默认全部排除；
- 录音（`isRecording`）、播客（`isPodcast`）与有声书（`isAudiobook`）不属于系统铃声，继续保持准入资格。

---

## 6. 新增音频格式扩展指南

若未来计划支持新格式（如 APE / Monkey's Audio 或 DSD DSF/DFF）：

1. **评估解码器支持**：
   确保底层播放引擎（ExoPlayer / FFmpeg 扩展或系统解码器）已具备该格式的解码与解封装支持；
2. **在注册表中声明**：
   - 在 [`AudioFormatRegistry.kt`](../../app/src/main/java/com/musicapp/player/core/media/AudioFormatRegistry.kt) 的 `SUPPORTED_EXTENSIONS` 增加小写扩展名（如 `"ape"`）；
   - 在 `SUPPORTED_MIME_TYPES` 补充对应 MIME 变体（如 `"audio/ape"`, `"audio/x-ape"`）；
3. **更新本文档**：
   在第 2 节与第 3 节中补充新格式信息；
4. **单元测试与门禁验证**：
   在 [`AudioAdmissionPolicyTest.kt`](../../app/src/test/java/com/musicapp/player/core/media/AudioAdmissionPolicyTest.kt) 中补充针对该格式的接受/拒绝用例，并运行本地全套门禁验证。
