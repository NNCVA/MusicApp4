# MusicApp

MusicApp 是一款使用 Kotlin 与 Jetpack Compose 构建的 Android 本地音乐播放器。应用完全离线运行，围绕本地媒体库、后台播放、同步歌词、播放列表和自适应界面提供核心体验。

## 当前状态

主要页面和核心播放/媒体库流程已完成大部分开发，测试基础设施已接入。当前页面行为以 `app/` 下代码、自动化测试和实际设备验证为准；早期视觉对照图、页面 PRD 和实施计划仅用于历史追溯，不再作为开发门槛。

## 核心功能

- 通过 MediaStore 扫描 `.mp3`、`.flac`、`.wav`、`.aac`、`.m4a`、`.ogg`、`.opus`，支持全盘或指定目录同步、排除规则和增量更新。
- 提供单曲、专辑、艺术家、文件夹、播放列表和播放历史浏览，以及排序、多选、隐藏和批量操作。
- 使用单一 ExoPlayer、MediaLibraryService 与 MediaSession 实现后台播放、系统媒体控制、播放快照和三种播放模式。
- 支持可调节的单播放器淡出淡入切歌、稳定随机队列、播放队列编辑和“下一首播放”。
- 支持外部 LRC、内嵌 SYLT/USLT 同步歌词、内嵌封面、高级音频元数据和歌曲信息查看。
- 提供 Mini/Full Player Sheet、手机/平板/折叠屏自适应导航、Material You、多套明暗主题、Aero 动态背景及中英双语。

## 技术栈

| 类别 | 方案 |
| --- | --- |
| 平台 | Android，`minSdk 26`，`targetSdk 37` |
| 语言与工具链 | Kotlin 2.3.20；Gradle daemon 使用 JDK 21，应用编译 toolchain 使用 JDK 17 |
| UI | Jetpack Compose，Material 3，Navigation 3 |
| 播放 | AndroidX Media3，ExoPlayer，MediaSession，MediaLibraryService |
| 架构 | 单向数据流、ViewModel、Coroutines、StateFlow、Hilt |
| 数据 | Room、Preferences DataStore、MediaStore |
| 模块 | 单一 `:app` 模块，按 `core/*`、`data/*`、`feature/*` 分层 |

## 构建与运行

准备符合项目 Gradle 与 Android SDK 配置的 JDK、Android Studio 后，在仓库根目录执行；本机环境与版本以 [`docs/verification.md`](docs/verification.md) 和项目配置为准：

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

连接 Android 设备或启动模拟器后可安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次进入媒体库时，Android 13 及以上版本需要授予“音乐和音频”权限，Android 12 及以下版本需要授予存储读取权限。

## 质量验证

构建门禁、无设备回退和结果报告统一见 [`docs/verification.md`](docs/verification.md)；测试分层和 Runner 归属见 [`docs/testing.md`](docs/testing.md)。README 不重复维护门禁命令，避免环境快照和验证口径漂移。

## 项目结构

```text
app/src/main/java/com/musicapp/player/
├── core/       # 领域模型、设计系统、歌词、媒体与播放规则
├── data/       # Room、DataStore、MediaStore、Repository 与同步
├── feature/    # 媒体库、播放器、歌词、播放列表、设置等页面
├── media/      # Media3 播放、MediaSession 与后台服务
├── navigation/ # Navigation 3 路由与状态
└── ui/         # 应用壳层与共享 UI
```

## 文档

- [构建与验证准则](docs/verification.md)
- [测试策略与 Runner](docs/testing.md)
- [领域词汇](docs/CONTEXT.md)
- [通用组件规范](docs/design/selection-and-toggle-controls.md)
- [资源与许可证治理](docs/design/resource-governance.md)
- [架构决策记录与冲突状态](docs/adr/README.md)
- [早期页面设计说明](docs/design/README.md)
- [早期实施计划](docs/plan/README.md)

页面级设计、首版实施规格和计划文件集中在 `docs/design/archive/`、`docs/plan/archive/` 中供历史追溯，但不再作为当前实现依据。
