# AGENTS.md

## 项目入口与权威性

- 必须使用中文输出；项目说明统一放在 `README.md`，过程和验证文档统一放在 `docs/`。
- 当前页面行为以 `app/` 下的代码、自动化测试和实际设备验证为准。`docs/plan/archive/`、页面级 `docs/design/archive/` 和首版实施规格只作历史参考，不得作为当前实现门槛。
- 当前仍维护的设计资料包括通用组件规范 [`docs/design/selection-and-toggle-controls.md`](docs/design/selection-and-toggle-controls.md)、资源/许可证规范 [`docs/design/resource-governance.md`](docs/design/resource-governance.md)，以及业务规则规范（[`docs/design/artist-splitting-rules.md`](docs/design/artist-splitting-rules.md)、[`docs/design/album-grouping-rules.md`](docs/design/album-grouping-rules.md)、[`docs/design/audio-format-registry.md`](docs/design/audio-format-registry.md)）。
- 架构决策见 [`docs/adr/README.md`](docs/adr/README.md)。发现 ADR 与代码冲突时，先记录具体代码证据和影响，再决定更新或标记 ADR；不要把过时 ADR 当作实现要求。
- 已验证的实现失误与规避经验统一记录在 [`docs/lessons-learned.md`](docs/lessons-learned.md)，不替代当前代码、测试或 ADR。

## 检索、协作与修改边界

- 仓库存在 `.codegraph/` 时，理解或定位代码先使用 CodeGraph，再读取必要的原文件；不要把 `.codegraph` 数据库当作普通文本加载。
- 渐进式披露：先读本文件，再按任务读取单个权威文档或 skill；不要默认遍历整个 `docs/`、`.agents/skills/`、日志或图片目录。
- 只有存在真正独立、文件互斥且可单独验证的任务包时才并行委派；主线程必须核对最终 diff 和验证结果。所有代理都共享工作树，必须保留既有 staged、unstaged 和 untracked 修改。
- 修改前检查 `git status --short --branch` 与文件所有权；修改后检查限定 diff、链接和格式。禁止 broad rollback、`reset` 或覆盖无关文件。

## 构建与测试

- 构建、测试或设备验证前先读取 [`docs/verification.md`](docs/verification.md)；测试策略、分层边界、目录归属与 Runner 规则见 [`docs/testing.md`](docs/testing.md)。环境快照不能替代本次实际输出。
- 执行 Gradle 前使用当前 macOS 的 Java 21 daemon 配置：

  ```shell
  export JAVA_HOME="$(jenv prefix 21 2>/dev/null || echo '/Users/a1/.jenv/versions/21')"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

- 常用门禁（单模块 `:app`）：
  - JVM 单测：`./gradlew :app:testDebugUnitTest --no-daemon --console=plain`
  - Lint：`./gradlew :app:lintDebug --no-daemon --console=plain`
  - Debug APK：`./gradlew :app:assembleDebug --no-daemon --console=plain`
  - 完整本地门禁：`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain`
  - 设备 Runtime 测试：`./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain`
- 没有设备时只能运行 `assembleDebugAndroidTest` 检查测试 APK 编译；报告中必须区分编译检查与 Runtime 测试。

## 硬性架构约束

- 保持单向数据流；Composable 只提交动作并观察状态，数据访问经 Repository，播放控制经 MediaController。
- Room 是媒体库、播放列表、历史、隐藏状态、路径规则和播放快照的应用内事实来源；界面不得直接查询 MediaStore 或 DataStore。
- 只有 `MediaLibraryService` 可以持有 ExoPlayer/MediaSession；Activity 和 ViewModel 只能通过 MediaController 控制播放。
- 界面文本必须进入双语资源；圆角、间距和字号使用设计令牌；交互图标必须提供本地化 content description。
- 组件默认 `exported=false`，PendingIntent 默认不可变；新增资源按资源治理文档记录来源、许可证和修改。
- 所有依赖 Room 的列表与详情页，未就绪（`!isLoaded`）期间严格使用纯色空白占位，严禁引入 `LoadingState` 转圈动画导致首屏/切页闪烁（对齐 ADR-0013）。
- 二级详情页（Album/Artist/Playlist/Folder Detail）严禁承载全局扫描等一级管理入口；实体缺失展示全屏 Unavailable 状态并保留返回；曲目为空时必须隐藏播放全部等行动栏。
- 纯业务规则和少量平台适配放在 `app/src/test`；Room、Hilt、MediaLibraryService、真实资源和启动行为放在 `app/src/androidTest`。

## 任务路由与验收

- 行为问题先读当前实现和相关测试，必要时补读 [`docs/CONTEXT.md`](docs/CONTEXT.md)；不要从页面级 PRD 或早期计划推断当前行为。
- UI、自适应、Navigation 3、样式或安全任务只加载命中的 skill 和通用组件规范；页面设计 review 仅作历史背景。
- Android 构建、Lint、单测、Runtime 或性能任务必须按 [`docs/verification.md`](docs/verification.md) 报告实际命令、结果和未覆盖范围；性能结论区分静态风险、门禁结果和设备实测证据。
- 任务路由或子任务包需要细则时，读取 [`docs/routing-guide.md`](docs/routing-guide.md) 与 [`docs/task-packet.md`](docs/task-packet.md)。
