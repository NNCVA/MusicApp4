# MusicApp 首版短计划

状态：可执行（2026-07-29）

详细行为以 [`../design/implementation-spec.md`](../design/implementation-spec.md) 为准，任务拆分与门禁以 [`implementation-wave-plan.md`](implementation-wave-plan.md) 为准。

## 目标

从当前可编译的 Compose/Navigation 3 空白骨架出发，按依赖顺序完成本地媒体库、后台播放、播放器界面、分类与播放列表、设置与 CI 收口。

## 执行顺序

1. **Wave 0–2：基础稳定**
   - 建立依赖、测试、Hilt、Room、DataStore、领域模型、设计令牌、自适应壳层和八个可保存返回栈。
2. **Wave 3–4：核心闭环**
   - 完成权限与 MediaStore 原子同步，再实现 MediaLibraryService、播放队列、三种互斥播放模式、淡出淡入、通知和恢复。
3. **Wave 5–8：产品完成**
   - 并行完成播放器/歌词与分类/播放列表，随后组装设置、Aero、关于，最后用最小 CI 收口。

## 固定门禁

- 每个 Wave 同步交付功能实现、English/简体中文资源、必要单元测试和文档。
- CI 固定使用 JDK 17 执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`。
- 上述三个 Gradle 任务是唯一 CI 门禁。
