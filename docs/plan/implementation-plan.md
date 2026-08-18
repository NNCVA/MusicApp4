# MusicApp 首版短计划

状态：历史计划索引（2026-07-29）；当前实现状态以代码和实现规格为准。

详细行为以 [`../design/implementation-spec.md`](../design/implementation-spec.md) 为准，实际执行与验证以 [`implementation-execution-plan.md`](implementation-execution-plan.md) 为准，Wave 范围映射见 [`implementation-wave-plan.md`](implementation-wave-plan.md)。

## 目标

从当前可编译的 Compose/Navigation 3 空白骨架出发，按依赖顺序完成本地媒体库、后台播放、播放器界面、分类与播放列表、设置与 CI 收口。

## 执行顺序

1. **工程、数据与壳层**：先建立测试底座、Hilt、领域契约、Room、DataStore、设计令牌、自适应壳层和八个可保存返回栈；测试按纯逻辑 JVM、少量 Robolectric 与 Android Runtime 集成分层。
2. **媒体库与播放闭环**：按权限、MediaStore 查询、原子同步、曲目页、最薄 Media3 服务、完整队列与系统恢复递进。
3. **产品页面与收口**：完成播放器、歌词、分类、播放列表、设置、Aero、关于，再用最小 CI 收口。

## 验证

每个阶段的验证分层、环境选择、门禁命令、设备要求和回退规则统一以 [`../verification.md`](../verification.md) 与 [`../testing.md`](../testing.md) 为准；本索引不重复维护命令。
