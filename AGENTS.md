# AGENTS.md

## 入口规则

- 必须使用中文输出；项目文档统一放在 `docs/`。
- 项目说明入口是 [`README.md`](README.md)；当前实现状态、行为和架构以 [`docs/design/implementation-spec.md`](docs/design/implementation-spec.md) 为准。
- 仓库存在 `.codegraph/` 时，理解或定位代码先使用 CodeGraph，再读取必要的原文件；不要把 `.codegraph` 数据库当作普通文本加载。
- 渐进式披露 = 先读取本文件和任务路由，再按任务读取单个权威文档、skill 或 reference；不要默认读取整个 `docs/`、`.agents/skills/`、日志或图片目录。
- 只有在任务命中时才读取 `.agents/skills/*/SKILL.md`，reference 继续按具体问题加载；调用工具前严格填写当前 schema 的必填字段。
- 只有存在真正独立、文件互斥且可单独验证的任务包时才并行委派；主线程必须核对实际 diff 和验证结果。
- 保留用户已有的 staged、unstaged 和 untracked 修改；不要使用 broad rollback、reset 或覆盖无关文件。
- 构建、测试或设备验证前先读取 [`docs/verification.md`](docs/verification.md)；环境快照不能替代实际任务结果。

## 权威文档路由

- 行为、架构、数据和产品约束：[`docs/design/implementation-spec.md`](docs/design/implementation-spec.md)。
- 领域术语：[`docs/CONTEXT.md`](docs/CONTEXT.md)；不可逆且有真实取舍的决定记录在 [`docs/adr/`](docs/adr/)。
- 测试分层与 Runner 归属：[`docs/testing.md`](docs/testing.md)；环境、门禁命令、设备检查和回退：[`docs/verification.md`](docs/verification.md)。
- 设计约束索引：[`docs/design/design-review-index.md`](docs/design/design-review-index.md)；只读取与当前 UI、导航或交互问题相关的 review。
- 代理路由和任务包格式：[`docs/routing-guide.md`](docs/routing-guide.md) 与 [`docs/task-packet.md`](docs/task-packet.md)。
- 实施计划索引：[`docs/plan/implementation-plan.md`](docs/plan/implementation-plan.md)；Wave 与逐过程计划只在需要范围映射或执行依赖时读取。
- 图标、许可证和资源来源：[`docs/design/resource-governance.md`](docs/design/resource-governance.md) 及对应的许可证清单。

## 硬性架构约束

- 保持单向数据流；Composable 只提交动作并观察状态，数据访问经 Repository，播放控制经 MediaController。
- Room 是媒体库、播放列表、历史、隐藏状态、路径规则和播放快照的应用内事实来源；界面不得直接查询 MediaStore 或 DataStore。
- 只有 MediaLibraryService 可以持有 ExoPlayer/MediaSession；Activity 和 ViewModel 只能通过 MediaController 控制播放。
- 界面文本必须进入双语资源；圆角、间距和字号使用设计令牌；交互图标必须提供本地化 content description。
- 组件默认 `exported=false`，PendingIntent 默认不可变；新增资源按资源治理文档记录来源、许可证和修改。
- 纯业务规则和少量平台适配放在 `app/src/test`；Room、Hilt、MediaLibraryService、真实资源和启动行为放在 `app/src/androidTest`。

## 任务路由与验收

- 代码检索先用 CodeGraph；行为问题补读实现规格对应章节；领域命名补读 `CONTEXT.md`。
- Android 构建、Lint、单测、Runtime 或性能任务补读验证/测试文档；性能结论必须区分静态风险、门禁结果和设备实测证据。
- UI、自适应、Navigation 3、样式或安全任务只加载命中的项目 skill 与对应 design review，不并行加载所有 Android skill。
- 修改前检查状态和文件所有权；修改后检查 diff、相对链接和格式，并只报告实际执行过的验证。
