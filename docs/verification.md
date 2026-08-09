# 验证与回退

测试分层、目录归属、Hilt/Room/Runner 规则和完整命令见 [`testing.md`](testing.md)。本文件补充门禁执行顺序、环境检查和失败回退；文档中的命令是验证方法，不代表当前运行已经通过。

## Static check

```shell
python3 - <<'PY'
from pathlib import Path
import tomllib

config = tomllib.loads(Path('.codex/config.toml').read_text())
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
```

## Runtime checks

Start a new task after installing the files.

1. Ask for one small bounded edit. Confirm the primary task identifies GPT-5.6 Luna Max and does not spawn Sol.
2. Ask for two independent read-only checks or disjoint edits. Confirm execution is attributed to `luna_worker` and files have one writable owner.
3. Present a deliberately ambiguous, high-impact design question. Confirm `sol_advisor` receives only the decision question and evidence, then Luna resumes execution.

Static TOML validation cannot prove model access or runtime loading. Report actual model use only when Agent activity or tool output identifies it.

## Fallbacks

- If custom agents are unavailable, select Luna Max as the main model and request Sol manually only for escalation cases.
- If Luna Max is unavailable, use the highest supported Luna effort and disclose the substitution.
- If Sol is unavailable, stop for decisions where its review is required or explicitly document the alternate advisor model.
- If parallelism adds more coordination than value, use `LUNA_LOCAL`.

## Android 测试门禁

项目 CI 使用 JDK 17，先执行现有 JVM、Lint 和 Debug 构建门禁，再执行设备或模拟器上的 Android Runtime 集成测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon --console=plain
```

`connectedDebugAndroidTest` 必须在可用设备或模拟器上运行。无设备时可执行下面的编译检查：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
```

该命令只能作为 Android 测试 APK 编译检查，不能作为 Android Runtime 集成测试运行证据，也不能把未执行的设备门禁标记为通过。`app/src/androidTest` 的测试使用 `AndroidJUnit4`，Hilt 测试使用 `com.musicapp.player.HiltTestRunner`；`app/src/test` 仍执行纯逻辑单测和少量 Robolectric 平台适配测试。

## Local Android build troubleshooting

### 当前 Windows 工作站环境快照（2026-08-10）

以下路径和版本来自当前工作站实测，属于本地环境记录，不应写入可移植的 Gradle 配置：

| 工具 | 正确位置 | 实测版本或状态 |
| --- | --- | --- |
| Gradle 守护进程 JDK | `D:\Android\Android Studio\jbr` | JetBrains JDK `21.0.10`，满足 `toolchainVendor=JETBRAINS`、`toolchainVersion=21` |
| 应用编译 JDK | `C:\Program Files\Java\jdk-17` | JDK `17.0.12`，对应 `app/build.gradle.kts` 的 JVM 17 工具链 |
| Gradle Wrapper | [`gradlew.bat`](../gradlew.bat) | 项目要求 Gradle `9.1.0` |
| Standalone Gradle | `D:\gradle\gradle-8.14.3\bin\gradle.bat` | Gradle `8.14.3`，不能替代项目 Wrapper 版本 |
| Android SDK | `D:\AndroidSDK` | 与 [`local.properties`](../local.properties) 一致 |
| SDK Platform | `D:\AndroidSDK\platforms\android-36` | `compileSdk=36` 已满足 |
| Build Tools | `D:\AndroidSDK\build-tools\36.1.0` | 已安装 |
| Platform Tools | `D:\AndroidSDK\platform-tools\adb.exe` | `37.0.0` |
| Emulator | `D:\AndroidSDK\emulator\emulator.exe` | `36.5.10.0` |
| SDK 命令行工具 | `D:\AndroidSDK\cmdline-tools\latest\bin` | `sdkmanager`/`avdmanager` `20.0`，显示 SDK XML version 4 兼容性警告 |
| AVD | `C:\Users\devil\.android\avd\Pixel_8.ini` → `D:\android\avd\Pixel_8.avd` | `Pixel_8`，API 34 Google Play x86_64；实测在线设备 `emulator-5554` |
| Android Studio | `D:\Android\Android Studio\bin\studio64.exe` | `AndroidStudio2025.3.4` |
| Android CLI | `C:\ProgramData\AndroidCLI\android.exe` | `1.0.15498356`；默认 SDK 路径错误，使用 `--sdk D:\AndroidSDK` |

当前 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`、`ANDROID_AVD_HOME` 均为空；PATH 只包含 JDK 17 和 `D:\AndroidSDK\platform-tools`。开始本地验证时可在当前 PowerShell 会话设置：

```powershell
$env:JAVA_HOME = 'D:\Android\Android Studio\jbr'
$env:ANDROID_SDK_ROOT = 'D:\AndroidSDK'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:ANDROID_SDK_ROOT\emulator;$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin;$env:Path"

& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin\sdkmanager.bat" --list_installed
& "$env:ANDROID_SDK_ROOT\emulator\emulator.exe" -list-avds
& "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" devices -l
```

### 当前阻塞项

- Gradle Wrapper 的 `gradle-9.1.0-bin.zip` 尚未完整下载，缓存目录只有 `.zip.part`/`.lck`，因此 `.\gradlew.bat --version` 可能在等待 ZIP 锁时超时；该结果不能归因于 JDK 21 不兼容。
- `D:\gradle\gradle-8.14.3` 可以单独运行，但项目 Wrapper 固定为 9.1.0；不要为了绕过下载问题修改或暂时移动项目的 Wrapper 版本文件。
- `sdkmanager` 的 SDK XML version 4 警告目前是命令行工具与 SDK 元数据版本差异提示，单独出现时不等同于 Gradle 任务失败；仍应分别记录实际任务退出结果。

### Select the JVM before running Gradle

The Gradle daemon JVM and the application's compilation toolchain are separate:

- [`gradle/gradle-daemon-jvm.properties`](../gradle/gradle-daemon-jvm.properties) currently requires JetBrains JDK 21 for the Gradle daemon.
- [`app/build.gradle.kts`](../app/build.gradle.kts) keeps the application compilation toolchain on JDK 17.
- `--no-daemon` still starts a single-use daemon and does not bypass the daemon JVM criteria.
- Commands such as `android describe` invoke Gradle model tasks and therefore need the same daemon JVM setup.

On the current Windows workstation, use compatible local JVMs in this order:

1. Android Studio JBR: `D:\Android\Android Studio\jbr` (JetBrains JDK 21.0.10).
2. Standalone JDK: `D:\Java` (Oracle JDK 21.0.7), for Java 21 commands that do not require the current JetBrains vendor constraint.
3. Gradle automatic download, only when no installed JVM satisfies both the required version and vendor.

Resolve Android Studio from the registry and verify Gradle before starting a long build:

```powershell
$studioPath = (Get-ItemProperty 'HKLM:\SOFTWARE\Android Studio').Path
$env:JAVA_HOME = Join-Path $studioPath 'jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$env:JAVA_HOME\bin\java.exe" -version
.\gradlew.bat help --no-daemon --console=plain
```

Setting `JAVA_HOME=D:\Java` alone does not satisfy the current JetBrains vendor criterion. Confirm the repository criterion before using a different JDK vendor; do not change or temporarily move the tracked criterion file merely to make validation start.

### Diagnose a silent Gradle invocation

No console output does not establish that Gradle is hung. Check the exact Java processes, then inspect the relevant process before terminating anything:

```powershell
Get-CimInstance Win32_Process |
  Where-Object { $_.Name -eq 'java.exe' } |
  Select-Object ProcessId, ParentProcessId, CreationDate, CommandLine

& "$env:JAVA_HOME\bin\jstack.exe" <gradle-process-id>

Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Recurse -File -ErrorAction SilentlyContinue |
  Where-Object { $_.Name -like '*.part' } |
  Select-Object FullName, Length, LastWriteTime
```

Interpret the evidence before acting:

- A stack in `SecureFileDownloader` means Gradle is provisioning a JVM; an old unchanged `.part` file indicates a stalled resume rather than a successful installation.
- A wrapper waiting in `DaemonClient.monitorBuild` can be normal when a daemon or Kotlin compiler child process is active.
- Stop only process IDs created by the current command. Never terminate every Java process on the machine.
- Use `.\gradlew.bat --stop` for Gradle-managed daemons after the active command has ended.

### Verify and report in layers

Run the narrowest relevant check first, then expand to the project gates:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "<fully-qualified-test-class>" --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain
```

When no device is available, use the following only to check instrumentation compilation:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
```

Report each task independently. A combined invocation may complete `assembleDebug` while failing later because of unit tests or the Android Runtime gate, and an SDK XML compatibility warning is not itself a task failure. When the full suite fails, rerun the affected test class alone and separate implementation regressions from reproducible environment failures such as Windows DataStore temporary-file rename errors; never claim the complete gate passed when only targeted checks or Android test compilation are green.
