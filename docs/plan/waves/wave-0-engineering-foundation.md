# Wave 0：工程与质量底座

状态：`DONE`

## 目标与进入条件

从当前可编译空骨架建立后续 Wave 可复用的依赖注入、平台依赖、测试、质量任务与 Release 底座。进入条件仅为当前 `:app` Debug 骨架可配置；现有 `AGENTS.md` 修改不得覆盖。

## 执行单元概览

| ID | 状态 | 交付结果 | 依赖 | 最窄验证 |
|---|---|---|---|---|
| W0-01 | `DONE` | 版本目录与包边界 | 无 | Gradle 配置 + Debug 编译 |
| W0-02 | `DONE` | Hilt Application 与可替换系统源 | W0-01 | Hilt 编译测试 |
| W0-03 | `DONE` | Room/DataStore/Media3 基础接入 | W0-01、W0-02 | 空实现编译 + 配置检查 |
| W0-04 | `DONE` | 本地单元测试底座 | W0-01 | 空壳本地测试 |
| W0-05 | `DONE` | 设备、Compose 与截图测试底座 | W0-02、W0-04 | Runner 发现 + 设备冒烟 |
| W0-06 | `DONE` | 覆盖率、资源与架构检查任务 | W0-03～W0-05 | 目标 Gradle 任务可发现 |
| W0-07 | `DONE` | Release、备份、Manifest 与 R8 底座 | W0-03 | Release 配置及静态检查 |

## W0-01：版本目录与包边界

状态：`DONE`

- 目标：集中声明 Hilt、Room、DataStore、Media3、Lifecycle、协程、图片/元数据和测试依赖，建立 `core/*`、`data`、`media`、`feature/*` 包目录。
- 边界：`gradle/libs.versions.toml`、根/app Gradle 文件及空包说明；不创建业务模型。
- 验证：`./gradlew :app:assembleDebug`、`./gradlew :app:dependencies`；所有版本只从版本目录读取。
- 完成：Debug 编译通过，依赖无重复声明，仍为单一 `:app` 模块。
- 执行记录：开始—2026-07-29 10:30 CST；完成—2026-07-29 11:28 CST；执行者—Codex；提交—`3537332`；证据—`:app:tasks --all`、`:app:assembleDebug`；阻塞—无。

## W0-02：Hilt 与可替换系统源

状态：`DONE`

- 目标：建立 `MusicApplication`、Hilt 插件/测试 Application、可替换 `Clock` 与随机源。
- 边界：Application、DI 基础包和测试替换绑定；不绑定尚未实现的 Repository。
- 验证：目标 Hilt 编译测试、Application 启动设备冒烟。
- 完成：生产与测试 Application 均可创建，时钟和随机源可由测试替换。
- 执行记录：开始—2026-07-29 10:30 CST；完成—2026-07-29 11:28 CST；执行者—Codex；提交—`3537332`；证据—Hilt/KSP 主与测试源编译、Pixel 8 API 34 Hilt Application 设备测试；阻塞—无。

## W0-03：平台依赖基础接入

状态：`DONE`

- 目标：让 Room、Preferences DataStore、Media3 和元数据/图片库具备最小可编译接缝。
- 边界：插件、依赖、Schema 导出目录、数据库配置骨架和平台接口；不得提前实现七张表或播放服务。
- 验证：`./gradlew :app:assembleDebug`；确认 Schema 导出配置可解析。v1 Schema 由 W1-02 生成，不属于本单元完成条件。
- 完成：各库可被 DI 引用，Room 的实体与 v1 数据库延后到 Wave 1，且未声明 `INTERNET` 或 `POST_NOTIFICATIONS`。
- 执行记录：开始—2026-07-29 10:30 CST；完成—2026-07-29 11:28 CST；执行者—Codex；提交—`3537332`；证据—`:app:assembleDebug`、Room `copyRoomSchemas` 配置可解析且 v1 Schema 未提前生成；阻塞—无。

## W0-04：本地测试底座

状态：`DONE`

- 目标：接入 JUnit4、coroutines-test、Turbine 与通用 Fake 目录，冻结本地测试任务。
- 边界：`app/src/test`、测试依赖、虚拟时间/Flow 冒烟测试。
- 验证：`./gradlew :app:testDebugUnitTest`；测试报告必须包含至少一个真实断言。
- 完成：协程、Flow、可控时钟和随机源均能在 JVM 测试中使用。
- 执行记录：开始—2026-07-29 10:30 CST；完成—2026-07-29 11:28 CST；执行者—Codex；提交—`3537332`；证据—`:app:testDebugUnitTest` 通过 3 个真实断言；阻塞—无。

## W0-05：设备、Compose 与截图测试底座

状态：`DONE`

- 目标：配置 AndroidJUnitRunner、Hilt/Room/Compose 设备测试和截图框架，冻结基线目录及任务名。
- 边界：`app/src/androidTest`、Runner、截图插件与一条空壳 UI 冒烟。
- 验证：`./gradlew tasks --all` 可发现冻结任务；有设备时运行 `:app:connectedDebugAndroidTest`。当前无设备或 UTP 依赖缺失时标记 `BLOCKED`。
- 完成：测试可按类过滤，截图产物路径固定，设备缺失不会被记作通过。
- 执行记录：开始—2026-07-29 10:30 CST；完成—2026-07-29 11:28 CST；执行者—Codex；提交—`3537332`；证据—`:app:connectedDebugAndroidTest` 在 Pixel 8 API 34 通过 2 项，`:app:verifyDebugScreenshots` 通过；阻塞—无（中文工作区由 ASCII 临时副本包装任务规避 alpha15 路径编码缺陷）。

## W0-06：质量任务

状态：`DONE`

- 目标：建立 JaCoCo、字符串一致性、架构静态检查和截图校验任务。
- 边界：Gradle 任务及规则；架构规则覆盖 UI 直连 MediaStore/DataStore、UI 持有 Player、页面绕过设计令牌。
- 验证：`./gradlew tasks --all` 可发现覆盖率、截图、资源和架构任务，并生成空壳报告。
- 完成：报告路径固定；覆盖率阈值只应用于后续核心业务范围，不阻断纯配置代码。
- 执行记录：开始—2026-07-29 10:30 CST；完成—2026-07-29 11:28 CST；执行者—Codex；提交—`3537332`；证据—`:app:wave0HostQualityGate` 通过，报告见 `app/build/reports/{jacoco,quality,screenshotTest}`；阻塞—无。

## W0-07：Release 与安全配置底座

状态：`DONE`

- 目标：补齐 `proguard-rules.pro`、R8/资源压缩、备份规则、Lint 和合并 Manifest 检查。
- 边界：Release Gradle、ProGuard、backup/data-extraction XML、Manifest 静态任务。
- 验证：`:app:assembleRelease`、`:app:lintDebug`、合并 Manifest/权限检查；无需发布签名。
- 完成：Release 可构建，启动 Activity 是唯一导出组件，备份只允许 DataStore 设置且无敏感日志配置。
- 执行记录：开始—2026-07-29 10:30 CST；完成—2026-07-29 11:28 CST；执行者—Codex；提交—`3537332`；证据—`:app:assembleRelease`、`:app:lintDebug`、`:app:verifyManifestSecurity` 通过，Release 实际执行 R8/资源压缩；阻塞—无。

## Wave 0 门禁

状态：`DONE`

W0-01～W0-07 全部 `DONE`；Debug/Release、空壳本地测试、设备冒烟、Lint 和质量任务均有可复查证据。Pixel 8 API 34 设备冒烟已实际执行通过，本次不存在无设备阻塞。

## 冻结的质量任务与产物

- 本地测试与覆盖率：`:app:testDebugUnitTest`、`:app:jacocoDebugUnitTestReport`、`:app:verifyDebugCoverage`；产物在 `app/build/reports/jacoco/jacocoDebugUnitTestReport/`。
- 静态与安全检查：`:app:verifyStringResources`、`:app:verifyArchitecture`、`:app:verifyManifestSecurity`；产物在 `app/build/reports/quality/`。
- 截图与聚合门禁：`:app:updateDebugScreenshotReferences`、`:app:verifyDebugScreenshots`、`:app:wave0HostQualityGate`；基线在 `app/src/screenshotTestDebug/reference/`，报告在 `app/build/reports/screenshotTest/preview/debug/`。
