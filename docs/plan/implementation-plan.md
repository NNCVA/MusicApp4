# MusicApp 首版短计划

状态：可执行（2026-07-29）

详细行为以 [`../design/implementation-spec.md`](../design/implementation-spec.md) 为准，实际执行与验证以 [`implementation-execution-plan.md`](implementation-execution-plan.md) 为准，Wave 范围映射见 [`implementation-wave-plan.md`](implementation-wave-plan.md)。

## 目标

从当前可编译的 Compose/Navigation 3 空白骨架出发，按依赖顺序完成本地媒体库、后台播放、播放器界面、分类与播放列表、设置与 CI 收口。

## 执行顺序

1. **工程、数据与壳层**：先建立真实单元测试、Hilt、领域契约、Room、DataStore、设计令牌、自适应壳层和八个可保存返回栈。
2. **媒体库与播放闭环**：按权限、MediaStore 查询、原子同步、曲目页、最薄 Media3 服务、完整队列与系统恢复递进。
3. **产品页面与收口**：完成播放器、歌词、分类、播放列表、设置、Aero、关于，再用最小 CI 收口。

## 固定门禁

- 每个 Wave 同步交付功能实现、English/简体中文资源、必要单元测试和文档。
- CI 固定使用 JDK 17 执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`。
- 上述三个 Gradle 任务是唯一 CI 门禁。
- 设备、视觉与交互验收由用户执行；执行计划只提供验收清单，不新增 CI 或代理阻断门禁。
