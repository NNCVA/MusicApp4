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
