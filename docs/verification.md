# 构建与验证准则

本文件只维护当前 macOS 工作站可执行的 Android 门禁。页面行为以当前代码、测试和实际设备结果为准；早期计划、页面设计和视觉对照图不参与验收。

## 当前工具链

- 单模块：`:app`；`minSdk 26`，`compileSdk/targetSdk 37`。
- Gradle Wrapper：9.5.0；Android Gradle Plugin：9.3.0。
- Gradle daemon 使用 Java 21；应用的 Java/Kotlin 编译目标和 `jvmToolchain` 使用 17。
- 本机需要 Android SDK Platform 37，并由被 Git 忽略的 `local.properties` 提供 `sdk.dir`。

## 环境准备

在仓库根目录执行 Gradle 前固定使用 Java 21：

```shell
export JAVA_HOME="$(jenv prefix 21 2>/dev/null || echo '/Users/a1/.jenv/versions/21')"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --version --no-daemon --console=plain
```

不要提交包含本机绝对路径的 `local.properties`。`--no-daemon` 仍可能启动一次性的 Gradle daemon；遇到残留进程可执行 `./gradlew --stop`。

## 本地门禁

每次代码变更至少运行 JVM、Lint 和 Debug 构建：

```shell
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

没有设备或模拟器时，补充确认 Android 测试 APK 可编译：

```shell
./gradlew :app:assembleDebugAndroidTest --no-daemon --console=plain
```

有可用设备或模拟器时，运行 Android Runtime 集成测试：

```shell
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
```

定向 JVM 测试使用：

```shell
./gradlew :app:testDebugUnitTest --tests "<fully-qualified-test-class>" --no-daemon --console=plain
```

Debug APK 输出为 `app/build/outputs/apk/debug/app-debug.apk`。`assembleDebugAndroidTest` 只证明测试 APK 编译成功，不能代替 `connectedDebugAndroidTest`。

## 结果报告与失败分类

- 记录实际执行的命令、退出结果、JVM 测试数量、Lint/构建结果，以及是否运行了设备 Runtime 测试。
- 没有设备时明确写“未运行 Runtime 测试”，不要把测试 APK 编译通过写成集成测试通过。
- 结束前运行 `git diff --check`，并检查只改动了任务范围内的文件。
- `~/.gradle` 锁文件出现 `Operation not permitted` 时，先检查 Gradle 用户目录权限；这是本机缓存/沙箱问题，不直接归因于源码或 Wrapper。
- 依赖解析、SDK 或 JDK 缺失时，记录缺失项和命令输出；不要用历史快照替代本次验证。

测试分层、目录归属和 Runner 规则见 [`testing.md`](testing.md)。
