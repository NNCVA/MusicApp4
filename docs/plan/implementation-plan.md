# MusicApp 首版短计划

状态：可执行（2026-07-28）

详细行为以 [`../design/implementation-spec.md`](../design/implementation-spec.md) 为准，任务拆分与门禁以 [`implementation-wave-plan.md`](implementation-wave-plan.md) 为准。

## 目标

从当前可编译的 Compose/Navigation 3 空白骨架出发，按依赖顺序完成本地媒体库、后台播放、播放器界面、分类与播放列表、设置与发布验收。

## 执行顺序

1. **Wave 0–2：基础稳定**
   - 建立依赖、测试、Hilt、Room、DataStore、领域模型、设计令牌、自适应壳层和八个可保存返回栈。
2. **Wave 3–4：核心闭环**
   - 完成权限与 MediaStore 原子同步，再实现 MediaLibraryService、播放队列、三种互斥播放模式、淡出淡入、通知和恢复。
3. **Wave 5–8：产品完成**
   - 并行完成播放器/歌词与分类/播放列表，随后组装设置、Aero、关于，最后执行完整 Release 验收。

## 固定门禁

- 每个 Wave 同步交付 English、简体中文、无障碍、测试和截图，不集中拖到末尾。
- 每个新增核心包达到行覆盖率 `80%`、分支覆盖率 `70%`。
- 每个 Wave 通过本地测试、相关设备测试、截图、覆盖率、Debug 构建和 Lint。
- Wave 1 与 Wave 4 增加 Release 冒烟；Wave 8 全量复跑并签收。
- `docs/design/design-review-10-performance-release-questions.md` 保持搁置，不构成首版门禁。
