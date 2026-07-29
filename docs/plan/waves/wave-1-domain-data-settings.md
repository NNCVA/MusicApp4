# Wave 1：领域、Room 与设置事实来源

状态：`DONE`

## 目标与进入条件

冻结稳定身份、七张 Room 表、DataStore 设置、Repository 接口与持久化规则，为媒体扫描、播放和 UI 提供唯一事实来源。进入条件为 Wave 0 `DONE`。

## 执行单元概览

| ID | 状态 | 交付结果 | 依赖 | 最窄验证 |
|---|---|---|---|---|
| W1-01 | `DONE` | 领域身份与值对象 | Wave 0 | JVM 单元测试 |
| W1-02 | `DONE` | 曲目、隐藏与路径规则 Room 契约 | W1-01 | Room 设备测试 |
| W1-03 | `DONE` | 播放列表、历史与快照 Room 契约 | W1-01 | Room 设备测试 |
| W1-04 | `DONE` | Preferences DataStore 设置 | W1-01 | DataStore 测试 |
| W1-05 | `DONE` | Repository 接口与 Fake | W1-02～W1-04 | 契约测试 |
| W1-06 | `DONE` | 播放列表与历史纯规则 | W1-01、W1-03 | JVM 单元测试 |
| W1-07 | `DONE` | 队列、模式与快照数据契约 | W1-01、W1-03 | 往返/不变量测试 |
| W1-08 | `DONE` | v1 Schema 与 Release 门禁 | W1-02～W1-07 | Schema/Release 检查 |

## W1-01：领域身份与值对象

状态：`DONE`

- 目标：实现 TrackId、AlbumId、ArtistId、PlaylistId、播放模式、播放实例及路径规则值对象。
- 边界：`core/domain` 纯 Kotlin；不得引用 Room、MediaStore 或 Media3 类型。
- 验证：`:app:testDebugUnitTest` 定向运行身份相等性、序列化边界和非法值测试。
- 完成：曲目身份固定为“存储卷名 + MediaStore ID”，领域词汇与 `docs/CONTEXT.md` 一致。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—领域身份、值对象与非法值 JVM 测试通过；阻塞—无。

## W1-02：媒体库 Room 契约

状态：`DONE`

- 目标：建立 `tracks`、`hidden_tracks`、`path_rules` 表、索引、DAO 与事务。
- 边界：Room Entity/DAO/mapper；路径规则只存 Room，DataStore 仅存扫描模式。
- 实施：`tracks` 在 v1 即包含 W3 所需的修改时间、MIME、相对路径、可用状态、最后成功代次及必要元数据；失效不删除关联。
- 验证：真实 SQLite 设备测试覆盖唯一键、索引查询、Upsert、失效/恢复和事务回滚。
- 完成：字段足以支持 W3 扫描与缓存键，避免 W3 立即破坏性改 Schema。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—真实 SQLite 建库、唯一键、索引、Upsert、失效恢复和事务回滚设备测试通过；阻塞—无。

## W1-03：业务与播放 Room 契约

状态：`DONE`

- 目标：建立 `playlists`、`playlist_tracks`、`play_history`、`playback_snapshot` 表及事务。
- 边界：四表 Entity/DAO/mapper；不实现播放引擎或页面。
- 验证：设备测试覆盖规范化名称唯一性、列表位置唯一性、批量原子性、历史 Upsert 和快照往返。
- 完成：七张表总数、外键/保留策略和单一活动快照均与规格一致。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—七表、唯一性、原子批量、历史 Upsert、快照往返及外键策略设备测试通过；阻塞—无。

## W1-04：设置 DataStore

状态：`DONE`

- 目标：实现主题来源、预设、明暗、语言、Aero、淡出淡入时长和扫描模式的 SettingsRepository。
- 边界：Preferences DataStore、序列化与默认值；不得保存路径规则或业务数据。
- 验证：默认值、逐项更新、非法值回退、并发更新及“重置只恢复设置”测试。
- 完成：500 ms、系统语言/明暗、Material You 首选和扫描全部等设置默认值可稳定读取；播放模式由播放快照管理。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—DataStore 默认值、更新、回退、并发与重置设备测试通过；阻塞—无。

## W1-05：Repository 接口与 Fake

状态：`DONE`

- 目标：定义 MediaLibrary、Playlist、History、PlaybackSnapshot 与 PathRule Repository 接口及 Fake。
- 边界：接口位于领域可见层，实现位于 data；不得向 UI 暴露 DAO、Cursor 或 Preferences。
- 验证：同一契约测试分别运行 Room 实现与 Fake，确认 Flow 初值、更新和错误语义一致。
- 完成：W2 可仅依赖 Fake，W3/W4 可替换为真实实现。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—Room 与 Fake 的 Flow 初值、更新和类型化错误契约测试通过；阻塞—无。

## W1-06：播放列表与历史规则

状态：`DONE`

- 目标：实现名称 Unicode 规范化、1–50 字符、忽略大小写重名、批量新增/跳过及历史阈值纯规则。
- 边界：纯 Kotlin 用例；持久化只经 W1-05 接口。
- 验证：空白、组合字符、边界长度、重名、选择顺序、重复项及 `min(30 秒, 时长 50%)` 测试。
- 完成：规则输出新增/跳过计数，单播放实例最多记一次历史。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—名称规范化、批量计数和历史阈值 JVM 测试通过；阻塞—无。

## W1-07：队列、模式与快照契约

状态：`DONE`

- 目标：冻结原始队列、稳定随机序列、当前项、模式、位置和实例计时的可持久化结构。
- 边界：数据模型、编码器和不变量；具体队列状态机归 W4-01。
- 验证：三模式、空/单项/多项、旧值缺省、编码往返和损坏快照拒绝测试。
- 完成：快照可无损表达 W4 所需状态，进程恢复不隐式开始播放。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—三模式快照 JSON 往返、不变量和损坏整体拒绝 JVM 测试通过；阻塞—无。

## W1-08：v1 Schema 与门禁

状态：`DONE`

- 目标：导出并冻结 v1 Schema，执行数据库、覆盖率、Release 和 Manifest 门禁。
- 边界：Schema JSON、Schema 测试及报告；没有 v2 时不创建伪迁移。
- 验证：v1 建库设备测试、`:app:testDebugUnitTest`、覆盖率、`:app:assembleRelease`、Manifest/R8 冒烟。
- 完成：W1-01～W1-07 全部 `DONE`，核心逻辑达到行 80%/分支 70%，Schema 纳入版本控制。
- 执行记录：开始—2026-07-29 11:29 CST；完成—2026-07-29 12:12 CST；执行者—Codex；提交—本提交；证据—v1 Schema 已纳入版本控制，行覆盖率 93.3%、分支覆盖率 76.1%，Release 与 Manifest 门禁通过；阻塞—无。

## Wave 1 门禁

状态：`DONE`

v1 Schema、Repository 接口和快照格式冻结；路径规则只有 Room 一个事实来源，DataStore 只保存扫描模式与用户设置；全部设备测试与 Release 冒烟有证据。
