# 功能测试与 CI 设计审阅 09

状态：已接受（2026-07-29）

## 已实现基线

- 已配置 JUnit4、`kotlinx-coroutines-test`、Turbine、Robolectric、Room Testing 与 Hilt Testing；`ProjectSmokeTest` 保证 `testDebugUnitTest` 至少执行一个真实 JVM 测试。JDK 17 下 Robolectric 使用 API 35 Runtime（API 36 Runtime 要求 Java 21），应用仍保持 `compileSdk`/`targetSdk 36`。
- CI 显式使用 JDK 17，并仅运行 `testDebugUnitTest`、`lintDebug` 与 `assembleDebug` 三项门禁；需要 Android Runtime 的 Hilt 和 DAO 行为分别由 Robolectric 与 Room Testing 验证。

## 已确认约束

1. **功能测试**：为扫描过滤、路径优先级、排序、队列模式、播放历史阈值、淡出淡入状态机、LRC 解析、格式化和 ViewModel 补充 JVM 单元测试。
2. **测试技术栈**：使用 JUnit4、`kotlinx-coroutines-test` 和 Turbine；优先使用 Fake 隔离 Room、MediaStore 与 Media3 平台对象。
3. **最小 CI**：使用 JDK 17 顺序执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`，任一任务失败即阻止合并。
4. **门禁边界**：上述三个 Gradle 任务是唯一 CI 门禁。
