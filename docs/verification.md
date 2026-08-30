# 验证与回退

测试分层、目录归属、Hilt/Room/Runner 规则见 [testing.md](testing.md)。本文件补充门禁执行顺序、环境检查和失败回退；命令是验证方法，除“本次 macOS 复核结果”外，不代表已经运行通过。

项目的 Android 命令以 macOS/Linux 的 POSIX shell 写法为主：使用 ./gradlew。Windows PowerShell 使用同一组任务参数，但将 Wrapper 替换为 .\gradlew.bat；不要在 macOS 上执行 .bat 文件。

## Static check（可选：Codex 配置）

这部分只检查 .codex/ 下的代理配置，不属于 Android 构建门禁。tomllib 要求 Python 3.11 或更高版本；当前 macOS 的 /usr/bin/python3 为 3.9，因此使用已安装的 python3.12（也可替换为其他 3.11+ 命令）：

~~~shell
python3.12 - <<'PY'
from pathlib import Path
import tomllib

config_path = Path('.codex/config.toml')
if not config_path.is_file() or config_path.stat().st_size == 0:
    print('SKIP: .codex/config.toml is empty; this is not an Android build gate.')
    raise SystemExit(0)

config = tomllib.loads(config_path.read_text())
luna = tomllib.loads(Path('.codex/agents/luna-worker.toml').read_text())
sol = tomllib.loads(Path('.codex/agents/sol-advisor.toml').read_text())
assert config['model'] == 'gpt-5.6-luna'
assert config['model_reasoning_effort'] == 'max'
assert config['agents']['default_subagent_model'] == 'gpt-5.6-luna'
assert config['agents']['default_subagent_reasoning_effort'] == 'max'
assert luna['name'] == 'luna_worker' and luna['model'] == 'gpt-5.6-luna'
assert luna['model_reasoning_effort'] == 'max'
assert sol['name'] == 'sol_advisor' and sol['model'] == 'gpt-5.6-sol'
print('Static configuration checks passed.')
PY
~~~

当前仓库的 .codex/config.toml 为空，因此该检查会显示 SKIP；它不影响 Gradle 或 Android 构建。

## Runtime checks（可选：Codex 代理运行时）

完成 .codex/ 配置后，启动新任务执行以下检查：

1. 让主任务完成一个小范围编辑，确认任务使用 GPT-5.6 Luna Max，且没有无必要的 Sol 委派。
2. 提出两个互相独立的只读检查或互斥编辑，确认执行归属 luna_worker，每个文件只有一个可写 owner。
3. 提出一个有歧义且影响较大的设计决策，确认 sol_advisor 只收到决策问题和证据，之后由 Luna 继续执行。

静态 TOML 检查不能证明模型可用或运行时加载成功；只有 Agent activity 或工具输出明确标识时，才能报告实际模型使用情况。

## Android 测试门禁

CI 使用 JDK 17；应用的 Java/Kotlin 编译目标也是 17，而当前 Gradle daemon 约束为 Java 21。三者角色不同，详见“Select the JVM”一节。

### macOS/Linux 命令（主路径）

先运行 JVM 单测、Lint 和 Debug 构建：

~~~shell
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
~~~

没有设备时，只编译 Android 测试 APK：

~~~shell
./gradlew :app:assembleDebugAndroidTest --no-daemon --console=plain
~~~

有设备或模拟器时，再运行 Android Runtime 集成测试：

~~~shell
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
~~~

也可以一次调用全部门禁；报告结果时仍按任务分别记录：

~~~shell
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain
~~~

connectedDebugAndroidTest 必须在可用设备或模拟器上运行。assembleDebugAndroidTest 只证明测试 APK 可以编译，不能作为 Android Runtime 集成测试证据。app/src/androidTest 的测试使用 AndroidJUnit4，Hilt 测试使用 com.musicapp.player.HiltTestRunner；app/src/test 执行纯逻辑单测和少量 Robolectric 平台适配测试。

Windows PowerShell 的等价写法是将上面每条命令的 ./gradlew 替换为 .\gradlew.bat；任务参数、--no-daemon 和 --console=plain 保持不变。

## Local Android build troubleshooting

### 当前 macOS 工作站环境快照（2026-08-29）

以下是本机复核得到的环境记录。路径属于本机快照，不应写入可移植的 Gradle 配置；换机器时只需按本机路径调整 JAVA_HOME 和 Android SDK。

| 工具 | 当前 macOS 值 | 说明 |
| --- | --- | --- |
| Gradle daemon JDK | /Users/a1/.jenv/versions/21 | Eclipse Temurin 21.0.12；toolchainVersion=21，不限制 vendor |
| 应用编译 JDK | JDK 17 | app/build.gradle.kts 使用 jvmToolchain(17)；Gradle 当前检测到 Temurin 17.0.20 |
| Gradle Wrapper | gradlew | Gradle 9.5.0；脚本权限为可执行（100755） |
| Android Gradle Plugin | gradle/libs.versions.toml | 9.3.0 |
| Android SDK | /Users/a1/Library/Android/sdk | local.properties 中的 sdk.dir，文件被 Git 忽略 |
| SDK Platform | platforms/android-37.0 | API 37.0 revision 2；android.jar 和 source.properties 均存在 |
| Build Tools | build-tools/36.0.0 | 当前已安装并由构建实际使用；37.0.0 未安装，项目未显式固定 buildToolsVersion |
| Platform Tools | platform-tools/adb | 37.0.1 |
| 模拟器/设备 | Pixel_10_Pro、Pixel_9；emulator-5554 | 当前在线设备 API 37、arm64-v8a |
| SDK 命令行工具 | cmdline-tools/latest | 22.0；sdkmanager 会提示 deprecated，提示本身不等于 Gradle 任务失败 |

本机 shell 会话可使用：

~~~shell
export JAVA_HOME=/Users/a1/.jenv/versions/21
export ANDROID_SDK_ROOT=/Users/a1/Library/Android/sdk
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"

java -version
./gradlew --version --no-daemon --console=plain
./gradlew javaToolchains --no-daemon --console=plain
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --list_installed
"$ANDROID_SDK_ROOT/emulator/emulator" -list-avds
adb devices -l
~~~

### 当前环境注意项

- Wrapper 版本由 gradle/wrapper/gradle-wrapper.properties 固定为 9.5.0，macOS 使用 ./gradlew；gradlew.bat 只用于 Windows。
- Gradle 使用被忽略的 local.properties 定位 SDK；不要把包含本机绝对路径的 local.properties 提交到仓库。
- API 37 平台和 Build Tools 36.0.0 已足以完成当前构建；若未来显式要求 Build Tools 37，应先安装并重新运行门禁。
- --no-daemon 仍会启动一个 single-use Daemon；命令结束后该进程会退出。需要清理残留的 Gradle daemon 时使用 ./gradlew --stop。
- 在受限执行环境中若出现 ~/.gradle/*.lck (Operation not permitted)，先检查用户对 Gradle 用户目录的写权限；这属于环境权限问题，不应直接判定为源码或 Wrapper 错误。
- 环境变量路径一致性与 Configuration Cache：~/.jenv/versions/21 通常是符号链接（指向 ~/.jdks/jdk-21.0.12+8/Contents/Home）。在沙盒或自动化执行中如果切换使用软链接和物理路径，Gradle Configuration Cache 会因为 JAVA_HOME 字符串变化判定为环境变更（提示 `Calculating task graph as configuration cache cannot be reused because environment variable 'JAVA_HOME' has changed`），导致重新计算 task graph 与守护进程重启。建议保持前后 JAVA_HOME 路径字符串定义一致。
- README.md 与 design 规范已统一同步为 compileSdk/targetSdk=37，与 app/build.gradle.kts 保持一致。

### Select the JVM before running Gradle

Gradle daemon JVM 和应用编译 toolchain 分开配置：

- gradle/gradle-daemon-jvm.properties 只要求 toolchainVersion=21，当前 Gradle 输出为 Java 21、任意 vendor。
- app/build.gradle.kts 的 sourceCompatibility、targetCompatibility 和 jvmToolchain 均为 17。
- --no-daemon 不会绕过 daemon JVM criteria；它只避免复用长期 daemon。
- ./gradlew javaToolchains --no-daemon --console=plain 可确认 JDK 17/21 的检测和自动准备状态。

在使用 jenv 的 macOS 会话中：

~~~shell
export JAVA_HOME="$(jenv prefix 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
./gradlew help --no-daemon --console=plain
~~~

没有 jenv 时，使用已安装的 JDK 21 路径或 /usr/libexec/java_home -v 21；不要把 Windows 注册表、盘符路径或 java.exe 写入 macOS 配置。

### Diagnose a silent Gradle invocation

无 console 输出不能单独证明 Gradle 已挂起。先查看当前 Java/Gradle 进程，再检查对应进程：

~~~shell
ps -axo pid,ppid,lstart,command | grep -E '[j]ava|[g]radle'

"$JAVA_HOME/bin/jstack" <gradle-process-id>

find "${GRADLE_USER_HOME:-$HOME/.gradle}/jdks" -type f -name '*.part' -print
~~~

解释证据后再处理：

- SecureFileDownloader 栈表示 Gradle 正在准备 JDK；长期不变化的 .part 文件表示下载或续传可能停滞。
- Wrapper 停留在 DaemonClient.monitorBuild 可能是 daemon 或 Kotlin compiler 子进程仍在运行。
- 只终止本次命令创建的 PID；不要结束所有 Java 进程。
- 当前命令结束后，Gradle 管理的 daemon 可用下面的命令停止：

~~~shell
./gradlew --stop
~~~

### Verify and report in layers

先运行最窄的检查，再扩大范围；每个任务独立报告：

~~~shell
# 指定单个 JVM 测试类
./gradlew :app:testDebugUnitTest --tests "<fully-qualified-test-class>" --no-daemon --console=plain

# JVM 单测、Lint、Debug APK
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain

# 有设备时的 Android Runtime 集成测试
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
~~~

没有设备时，使用 ./gradlew :app:assembleDebugAndroidTest --no-daemon --console=plain 仅检查 instrumentation 编译。合并调用可能先完成 assembleDebug、再在单测或设备门禁失败；因此应按任务分别记录结果，不能只凭某一个 APK 生成成功就标记完整门禁通过。

## 本次 macOS 复核结果（2026-08-29）

以下结果来自当前 fix_detail 工作树；gradle/libs.versions.toml 在复核前已有未提交修改，本轮没有修改该文件：

- ./gradlew --version --no-daemon --console=plain：Gradle 9.5.0，Launcher JDK 21.0.12，macOS arm64；通过。
- ./gradlew help --no-daemon --console=plain：BUILD SUCCESSFUL；通过。
- ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain：BUILD SUCCESSFUL，395 项 JVM 测试无失败，Lint 和 Debug APK 通过。
- ./gradlew :app:assembleDebugAndroidTest --no-daemon --console=plain：BUILD SUCCESSFUL；测试 APK 编译通过。
- ./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain：BUILD SUCCESSFUL，在线 API 37 arm64 模拟器运行 31 项测试无失败。
- git diff --check：通过；除既有 gradle/libs.versions.toml 外没有新增工作树修改。
