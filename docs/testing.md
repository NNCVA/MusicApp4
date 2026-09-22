# 测试策略

Android Runtime 集成测试 = 在真实 Android 运行时的设备或模拟器上执行的 instrumentation test；Robolectric = 在 JVM 中模拟选定 Android API 的平台适配测试。本项目采用“高覆盖快速反馈 JVM 单测（纯逻辑 + Robolectric 适配）与真实环境 Android Runtime 集成测试分层协同”的测试策略。

## 测试分层与目录

| 层级 | 目录 | 适用范围 | 运行方式 |
|---|---|---|---|
| 纯逻辑 JVM 单测 | `app/src/test/java` | 领域规则、状态机、队列策略（如 `PlaybackQueueCoordinator` 模式切换零卡顿与自动纠偏）、路径规则大小写匹配（`PathRuleMatcher`）、歌词解析、格式化、ViewModel 同步状态机（如 `TracksViewModel` 静默全量刷新反馈与 `PlaylistDetailViewModel` 离线防呆拦截）及不需要真实 Android 环境的确定性业务逻辑 | `:app:testDebugUnitTest` |
| Robolectric 平台适配测试 | `app/src/test/java` | 需要 `Context`、权限、MediaStore 协议解析、SAF 外置卷与 StorageManager 映射解析（`ScanFolderResolver`）、图片管道或 Android API 模拟的适配逻辑 | `:app:testDebugUnitTest`，使用 `RobolectricTestRunner` |
| Android Runtime 集成测试 | `app/src/androidTest/java` | Room 数据库/迁移/Repository、媒体库同步、Hilt 依赖图、MediaLibraryService、真实资源启动、以及 Compose UI 交互/手势与无障碍语义树验证 | `AndroidJUnit4`，通过 `:app:connectedDebugAndroidTest` |

`src/test` 不以覆盖 Android Runtime 为目标，也不把 Room/Hilt/Service 的真实行为强行放回 Robolectric。设备、视觉与完整交互验收仍由人工执行。

播放详情进度条的 pending、确认、超时和取消状态属于不依赖平台的 feature 规则，放在 `app/src/test`；进度条 Compose 手势、重组后的语义和显示优先级放在 `app/src/androidTest`。两层分别验证状态决策和真实触控渲染，不把 MediaController 或设备回执伪装成 JVM 通过。

## Android Runtime 测试归属

以下典型测试归 `app/src/androidTest/java/com/musicapp/player`（包含架构持久化与 Compose 运行时语义两大部分）：

**1. 持久化、依赖图与服务组件**：
- `data/local/MusicDatabaseMigrationTest.kt`
- `data/HistoryRepositoryTest.kt`
- `data/MediaLibraryRepositoryTest.kt`
- `data/PlaylistRepositoryTest.kt`
- `data/PlaybackSnapshotRepositoryTest.kt`
- `data/sync/MediaLibrarySyncTest.kt`
- `di/ApplicationGraphTest.kt`
- `media/service/PlaybackServiceHiltTest.kt`
- `feature/about/AboutMetadataTest.kt`

**2. Compose UI 交互、手势与无障碍语义**：
- `core/designsystem/component/EmptyStateSemanticsTest.kt`
- `core/designsystem/component/TrackRowSemanticsTest.kt`
- `core/designsystem/component/ActionCardTest.kt`
- `core/designsystem/component/InsetPillSliderTest.kt`
- `feature/player/PlayerProgressBarTest.kt`
- `feature/player/PlayerLandscapeContentTest.kt`
- `ui/shell/AppShellGestureTest.kt`
- `ui/shell/AppShellSidebarFreeTest.kt`

`ApplicationStartupIntegrationTest` 同样位于 `app/src/androidTest`，用于确认测试 Application 能读取真实应用资源并完成启动级冒烟。`src/test/ProjectSmokeTest.kt` 已不再作为 JVM 冒烟测试；`src/test` 保留纯业务单测和少量 Robolectric 平台适配测试。

## 何时新增哪类测试

1. 不依赖 `Context`、Room、Hilt、Media3 或真实资源的规则、Reducer、Parser、Coordinator 和 ViewModel 行为，新增到 `app/src/test`。
2. 只需模拟少量 Android API、无需真实数据库、依赖图、Service 或设备状态的平台适配，新增到 `app/src/test` 并使用 Robolectric；测试应保持窄范围，不能用它替代 Runtime 集成测试。
3. 需要真实 SQLite/Room 迁移或 DAO、Repository 的实际事务、Hilt 注入图、MediaLibraryService、PackageManager/资源或应用启动行为，新增到 `app/src/androidTest`，使用 AndroidJUnit4；不要为迁移测试另建 JVM 冒烟。

新增测试先选择被测行为的事实边界，再选择目录；不为同一实现细节在两层重复测试。纯逻辑测试使用 Fake 隔离平台边界，集成测试验证真实边界及其生命周期。

## JVM 单测轻量化与防膨胀准则

为保证本地门禁与 CI 快速反馈（秒级完成），`app/src/test` 单测套件严格执行以下轻量化与防膨胀约束：

1. **严禁混入性能基准与伪测试**：禁止在常规单测集中加入仅使用 `println` / `measureNanoTime` 打印耗时而无确定性断言的 Benchmark 代码。
2. **严禁引入重型离屏图形渲染**：禁止在 JVM 单测中使用 `@GraphicsMode(Mode.NATIVE)` 进行高分辨率离屏 Bitmap 渲染合成或逐像素色彩遍历；图形层仅验证纯数学计算、布局视口规格与降级策略，渲染由真机人工或集成测试覆盖。
3. **消除多 SDK 沙箱重复加载**：Robolectric 测试在套件内保持统一的 SDK 配置（当前统一为 SDK 35），严禁在单个测试方法使用不同 SDK 注解导致 Robolectric 在单次测试中重复初始化多个沙箱运行时。
4. **拒绝形式化与低价值断言**：严禁创建仅校验代码常量（如 Alpha、Duration 数值）、单行透传委托或 getter/setter 的形式化测试；此类常量与界面行为已在 `androidTest` 中落地端到端断言，无需在 JVM 单测中镜像重复。
5. **内聚收敛与去碎片化**：相关联的步进计算、策略解析或参数规整优先合流至对应领域核心 Coordinator 或 Policy 测试类中，避免为单行独立函数创建碎片化测试文件。

## Hilt、Room 与 Runner 规则

- 所有 instrumentation 测试使用 `@RunWith(AndroidJUnit4::class)`。
- 使用 Hilt 的测试额外使用 `@HiltAndroidTest`、`HiltAndroidRule`，并在访问注入字段前调用 `hiltRule.inject()`；需要替换绑定时使用测试专用绑定，不修改生产依赖图来迎合测试。
- instrumentation Runner 保持为 `com.musicapp.player.HiltTestRunner`，由它创建 `HiltTestApplication`。测试不得绕过 Runner 自行创建不完整的 Application 图。
- Room Repository 测试通过 `ApplicationProvider` 获取 Context，优先使用隔离的内存数据库；每个测试类在清理阶段关闭数据库。Migration 测试使用独立数据库名称、明确添加 Migration，结束后清理数据库文件。
- Repository 集成测试验证真实 Room/SQLite 行为和事务边界；纯业务规则仍使用 Fake 或 JVM 单测。测试数据必须在测试内创建，不能依赖设备已有媒体库或上一次测试留下的状态。

## 验证命令

环境选择、Gradle 命令、设备要求、无设备回退和结果报告统一见 [`verification.md`](verification.md)。本文件只维护测试分层、目录归属、Runner、Room/Hilt 边界，不复制门禁命令。
