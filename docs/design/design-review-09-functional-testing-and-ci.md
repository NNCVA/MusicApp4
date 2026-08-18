# 功能测试与 CI 设计审阅 09

状态：已接受（2026-07-29；测试策略修订：2026-08-08）

修订说明：原审阅记录按 JVM 单测为主编写，并将 Robolectric/Room Testing 作为 Android Runtime 行为的替代；现按实际测试迁移方案补充 Android Runtime 集成测试优先的分层和设备门禁。原接受日期与功能约束保留。

## 已实现基线

- 保留 JUnit4、`kotlinx-coroutines-test`、Turbine 与少量 Robolectric 平台适配测试；纯业务规则继续放在 `app/src/test`。JDK 17 下 Robolectric 使用 API 35 Runtime（API 36 Runtime 要求 Java 21），应用仍保持 `compileSdk`/`targetSdk 36`。
- 需要真实 Android Runtime 的 Room、Hilt、Service、资源和启动行为迁移到 `app/src/androidTest`，统一使用 `AndroidJUnit4`；Hilt 测试使用 `com.musicapp.player.HiltTestRunner`。迁移范围为 `MusicDatabaseMigrationTest`、`HistoryRepositoryTest`、`MediaLibraryRepositoryTest`、`PlaylistRepositoryTest`、`PlaybackSnapshotRepositoryTest`、`MediaLibrarySyncTest`、`ApplicationGraphTest`、`PlaybackServiceHiltTest` 与 `AboutMetadataTest`，并新增 `ApplicationStartupIntegrationTest` 作为资源启动冒烟；`ProjectSmokeTest` 不再承担测试冒烟职责。
- CI 显式使用 JDK 17，先运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`，再在设备或模拟器上运行 `connectedDebugAndroidTest`。无设备时的 `assembleDebugAndroidTest` 仅是 Android 测试编译检查，不能替代 Runtime 集成测试。

## 已确认约束

1. **功能测试**：扫描过滤、路径优先级、排序、队列模式、播放历史阈值、淡出淡入状态机、LRC 解析、格式化和 ViewModel 等纯业务行为使用少量 JVM 单测；需要真实数据库、依赖图、Service 或资源的行为使用 Android Runtime 集成测试。
2. **测试技术栈**：JVM 层使用 JUnit4、`kotlinx-coroutines-test`、Turbine 和少量 Robolectric；Android Runtime 层使用 `AndroidJUnit4`、Hilt Testing 与 Room Testing。Fake 仅用于隔离不属于当前测试目标的平台边界。
3. **CI 门禁**：使用 JDK 17 顺序执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug` 和 `:app:connectedDebugAndroidTest`，任一任务失败即阻止合并。
4. **无设备回退**：没有设备或模拟器时可执行 `:app:assembleDebugAndroidTest` 做编译检查；该任务结果只能标记为编译检查，不能标记为 Android Runtime 集成测试通过。
