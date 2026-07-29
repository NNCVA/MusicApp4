# 功能测试与 CI 设计审阅 09

状态：已接受（2026-07-29）

## 当前基线

- 项目当前没有本地单元测试和测试依赖，`testDebugUnitTest` 目前是空任务。
- 仓库当前没有 CI 工作流，系统默认 Java 也不可用；CI 需显式配置 JDK 17。

## 已确认约束

1. **功能测试**：为扫描过滤、路径优先级、排序、队列模式、播放历史阈值、淡出淡入状态机、LRC 解析、格式化和 ViewModel 补充 JVM 单元测试。
2. **测试技术栈**：使用 JUnit4、`kotlinx-coroutines-test` 和 Turbine；优先使用 Fake 隔离 Room、MediaStore 与 Media3 平台对象。
3. **最小 CI**：使用 JDK 17 顺序执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`，任一任务失败即阻止合并。
4. **门禁边界**：上述三个 Gradle 任务是唯一 CI 门禁。
