# 播放历史页面重构 PRD

- 状态：设计冻结，待实现
- 版本：v1.0
- 日期：2026-09-05

## 1. 术语与目标

- **播放历史**：达到有效播放条件的曲目形成的最近播放记录；同一曲目只保留一条记录，并更新最近播放时间与累计次数。
- **删除记录**：只移除播放历史记录，不删除媒体库曲目、音频文件、歌单关系或播放队列。
- **当前可见历史**：按最近播放顺序排列，并经过临时搜索过滤后的记录集合。

目标是将播放历史页统一到 MusicApp 的 AppBar、吸顶操作栏、TrackRow 和 Design Token 体系，同时提供清晰的单条与批量历史管理。

## 2. 当前基线与问题

当前 checkout 的页面证据：

| 现状 | 代码位置 | 与目标的差距 |
|---|---|---|
| `CategoryHeader`、文字“清空历史” | `app/src/main/java/com/musicapp/player/feature/history/HistoryScreen.kt:163-200` | 没有通用 AppBar 与 More 图标 |
| 常驻 `OutlinedTextField` | `HistoryScreen.kt:202-209` | 不符合“无常驻播放历史搜索框” |
| 自定义 `HistoryRow` | `HistoryScreen.kt:236-257, 368-445` | 没有标准封面、质量角标、行尾 More；仍显示播放次数 |
| 顶部选择菜单 | `HistoryScreen.kt:298-364` | 没有底部三动作栏，且包含本页不需要的隐藏/下一首批量动作 |
| `HistoryUiState.isSelectionMode` 由选中集合推导 | `HistoryViewModel.kt:43-62` | 无法表达“已进入多选但 0 项选中” |
| 历史仓库只有观察、记录、清空 | `app/src/main/java/com/musicapp/player/data/repository/Repositories.kt:88-92` | 缺少单条/批量删除接口 |

现有 `TrackRow`、`SearchableTopBar`、`ListActionBar`、`AddToPlaylistDialog`、`ConfirmationDialog` 和 `TrackInfoViewer` 可作为实现基础。

## 3. 页面结构

```text
应用内容安全区
└─ SearchableTopBar（固定）
   ├─ 紧凑窗口：抽屉图标
   ├─ 标题：播放历史
   └─ More：搜索 / 多选 / 清空播放历史
└─ LazyColumn（弹性过度滚动）
   ├─ stickyHeader：ListActionBar
   │  ├─ 常规：播放全部图标 + 当前可见历史数
   │  └─ 多选：关闭 + 已选数 + 全选/取消全选
   └─ TrackRow 列表
      └─ 行尾 More（仅常规态）
└─ 多选时：SelectionBottomBar（位于 Mini Player 上方）
```

### 3.1 AppBar

- 使用 `SearchableTopBar`，高度与内边距由 `MusicTheme` 提供。
- `<600dp` 使用 `CategoryNavigationAction.DRAWER`；`≥600dp` 不显示导航按钮。播放历史是一级入口，紧凑窗口不能误显示返回箭头。
- 常规态右侧只有 `ic_common_more_vertical`；多选态隐藏 More，避免页面级操作与批量操作竞争。
- More 菜单顺序固定为：
  1. 搜索
  2. 多选
  3. 分隔线
  4. 清空播放历史（危险项）
- 清空项在无记录或清空执行中禁用；点击后菜单收起并打开二次确认。

### 3.2 临时搜索

- 常规态点击 More → 搜索后，`SearchableTopBar` 临时切换为内联输入；页面不显示常驻搜索框。
- 输入匹配标题、艺术家、专辑和文件名，忽略首尾空格与大小写；不匹配最近播放时间或播放次数。
- 搜索结果保持最近播放倒序，不改变领域排序。
- 关闭搜索或系统返回会清空关键词并恢复标题；系统返回优先关闭搜索，再退出多选，最后交给导航层。
- 搜索态与多选态互斥。搜索结果中长按曲目时先关闭搜索、清空关键词，再进入多选并选中该曲目。

### 3.3 吸顶 `ListActionBar`

- `SearchableTopBar` 固定在列表外；`ListActionBar` 作为 `LazyColumn` 的 `stickyHeader`，背景不透明，避免内容穿透。
- 有记录或有搜索结果时显示；空历史直接显示空态，不额外占用吸顶高度。
- 常规态左侧使用 `ic_playback_play_circle`，点击后以当前可见且可播放记录按最近播放倒序建立 `PlaybackContextSource.HISTORY` 队列；右侧显示当前可见记录数。
- 多选态显示关闭、已选数和全选/取消全选。多选可处于 0 项已选状态；全选包含当前可见的全部历史记录，包括暂时不可用记录。

### 3.4 TrackRow 列表

- 使用标准 `TrackRow` 布局：48dp 封面、标题、质量角标、“艺术家 · 专辑”副标题、48dp 行尾操作热区。
- 行高使用 `MusicTheme.dimensions.trackListItemHeight`；不显示播放次数，不显示独立的第三行日期。
- 常规态单击可播放曲目，以当前可见可播放历史建立队列并从该行开始；不可播放项单击无效。
- 常规态长按进入多选并选中该行；多选态单击整行切换选择。
- 多选态隐藏行尾 More，避免 More 与整行选择竞争；选择控件不应产生第二个无障碍焦点。
- `TEMPORARILY_UNAVAILABLE` 记录保留 TrackRow 视觉结构，可选择、可删除，但播放相关动作禁用。
- 关联不到 `Track` 的旧记录使用稳定占位标题和封面，仍可选择、可删除；播放、下一首、加入歌单和歌曲信息禁用。已由成功完整同步确认的缺失曲目按现有领域规则清理，不作为常驻页面状态设计。

### 3.5 行尾 More 菜单

使用独立 `HistoryTrackActionsMenu`，避免给全局菜单增加布尔参数。菜单顺序固定为：

1. 删除记录（危险）
2. 下一首播放
3. 加入歌单
4. 歌曲信息
5. 只读信息：最近播放：`本地化日期时间`

“最近播放”不是可点击动作，不触发行点击；不可播放记录只保留删除和最近播放信息。菜单采用 `AppDropdownMenu` 外壳，动作项使用 `AppDropdownMenuItem`，最近播放使用不可操作的文本行；统一遵守圆角、裁剪、48dp 热区和主题色规范。

### 3.6 底部多选操作栏

- 多选态出现，位于 Mini Player 上方；无 Mini Player 时贴合系统底部安全区。
- 固定三等分顺序：`删除记录 | 加入歌单 | 加入队列`。
- 0 项选中时三项均禁用；有选中项时删除可用，加入歌单/加入队列仅在存在可播放选中项时可用。
- 删除使用错误色；其他动作使用 `MusicTheme.colors.onSurface` 与现有操作色板。
- 加入歌单复用 `AddToPlaylistDialog`，加入队列按当前可见历史顺序追加；不可播放项跳过并在结果反馈中报告。
- 底部栏只承载批量动作，单行“下一首播放”不在底部栏重复出现。

## 4. 状态与动作

| 状态 | 进入 | 主要内容 | 返回/退出 |
|---|---|---|---|
| 常规 | 打开页面或退出多选 | AppBar、吸顶播放/计数、TrackRow | 交给导航层 |
| 搜索 | More → 搜索 | AppBar 内联输入、过滤后的列表 | 关闭搜索并清空关键词 |
| 多选空态 | More → 多选 | 吸顶栏显示 0，底部栏禁用 | 关闭按钮/系统返回 |
| 多选有选中 | 长按或多选态点击行 | 已选计数、全选、底部三动作 | 关闭按钮/系统返回/成功动作 |
| 删除确认 | 单行或批量删除 | `ConfirmationDialog`，显示数量 | 确认执行或取消 |
| 清空确认 | AppBar → 清空 | 全量确认 | 确认执行或取消 |
| 歌曲信息 | 行菜单 → 歌曲信息 | `TrackInfoViewer` | 关闭或系统返回 |
| 加歌单 | 行菜单或底部栏 | `AddToPlaylistDialog` | 选歌单/创建/关闭 |

### 4.1 选择规则

- 单独维护 `isSelectionMode`，不能用 `selectedTrackIds.isNotEmpty()` 代替。
- 选择集合按当前可见顺序提交批量动作；关键词变化会清空选择。
- 全选作用于当前可见历史记录，包括不可用记录；批量播放动作只处理可播放记录。
- 批量动作执行期间锁定重复提交；成功清空选择并退出多选，失败保留选择。

### 4.2 播放规则

- 吸顶播放和行点击均使用 `PlaybackContextSource.HISTORY`。
- 队列只包含 `Availability.AVAILABLE` 曲目，顺序为 `lastPlayedAtMs DESC`，时间相同时使用当前历史展示约定的稳定 `TrackId` tie-break（`mediaStoreId DESC`、`volumeName ASC`）；实现与 Repository/UI 测试必须保持同一方向。
- 单行“下一首播放”只对可播放曲目启用，并通过 `PlaybackControllerFacade` 发送。

### 4.3 删除规则

- 单条和批量删除均先确认，确认文案使用复数资源并包含记录数量。
- `HistoryRepository` 新增按 `TrackId` 集合删除能力；Room DAO 在一次事务中完成，失败不得部分提交。
- 清空历史沿用现有全量确认与 `clearHistory()`。
- 删除成功后刷新记录数、关闭相关菜单、退出多选并发送消息气泡；失败保留页面状态并发送失败消息。

## 5. 数据与架构契约

- Room 是播放历史唯一事实来源；Composable 不直接查询 Room、MediaStore 或 DataStore。
- `PlayHistory.playCount` 继续用于记录逻辑，但不在本页任何 UI 展示或搜索。
- 删除历史不影响 `tracks`、物理文件、`playlist_tracks`、`queue_entries` 或当前播放状态。
- 歌曲信息由 `HistoryViewModel` 复用现有 `TrackMetadataRepository` 读取，状态包含 `infoTrack`、`infoMetadata` 和 `isInfoLoading`，展示统一 `TrackInfoViewer`。
- `HistoryEntry` 需要同时表达历史记录和可选曲目；不可用项的管理动作与播放动作分离，不把“删除记录”误实现成“删除曲目”。

## 6. 组件复用与新增边界

| 组件 | 处理方式 | 备注 |
|---|---|---|
| `SearchableTopBar` | 直接复用 | 通过 `trailingContent` 挂 More；搜索仅临时开启 |
| `ListActionBar` | 复用并补零选择态/历史计数语义 | 作为 `stickyHeader` |
| `TrackRow` | 复用布局，修正选择态单一语义与子点击冲突 | 历史页传入专用行菜单 |
| `HistoryTrackActionsMenu` | 新增 | 仅负责历史记录动作和只读最近播放时间 |
| `SelectionBottomBar` | 从 Tracks/PlaylistDetail 共同抽取 | 通过 action slot 传入，避免业务分支和布尔参数膨胀 |
| `AddToPlaylistDialog` | 直接复用 | 单条和批量均支持创建歌单 |
| `ConfirmationDialog` | 直接复用 | 单条、批量、全量删除均为危险确认 |
| `TrackInfoViewer` | 直接复用 | 紧凑窗口 BottomSheet，宽窗口 Dialog |
| `AppDropdownMenu` / `BareIconButton` | 直接复用 | 菜单无外层垂直留白，图标热区 48dp |

## 7. Design Token 约束

实现只引用 `MusicTheme`，以下为当前 token 快照，不在页面代码中重新声明数值：

| 语义 | Token | 当前值 |
|---|---|---:|
| AppBar 高度 | `dimensions.playerHeaderHeight` | 64dp |
| 吸顶/图标热区 | `dimensions.minimumTouchTarget` | 48dp |
| TrackRow 高度 | `dimensions.trackListItemHeight` | 72dp |
| 封面尺寸 | `dimensions.trackArtworkSize` | 48dp |
| 顶栏水平内边距 | `dimensions.topBarHorizontalPadding` | 16dp |
| 内容水平内边距 | `dimensions.contentHorizontalPadding` | 24dp |
| 常用间距 | `spaceSmall / spaceMedium / spaceLarge` | 12 / 16 / 24dp |
| 行/菜单圆角 | `shapes.medium / shapes.large` | 12 / 16dp |
| 正文/标题 | `typography.titleLarge / titleMedium / bodySmall` | 主题定义 |
| 禁用透明度 | `MusicAlpha.Disabled` | 0.38 |
| 分隔线透明度 | `MusicAlpha.Divider` | 0.50 |

图标沿用 `resource-governance.md` 的 Material Icons Round 约束；新增或补充来源不明的 `more`/历史管理图标前，先补资源治理记录。

## 8. 本地化与无障碍

- 所有可见文本、菜单元数据、确认文案、状态文案和 `contentDescription` 同步写入 English 与简体中文资源；数量使用 `plurals`。
- 建议新增资源键：`history_more_actions`、`history_search_action`、`history_select_action`、`history_delete_record`、`history_delete_selected`、`history_delete_confirm_*`、`history_recent_played`、`history_unavailable_record`、`history_delete_result`、`history_delete_failed`。
- 行外层在多选态提供单一 Checkbox 语义；内部选择图标不保留独立点击/语义节点。圆角容器先 `.clip(shape)` 再挂交互修饰符，并合并后代语义。
- 行尾 More、播放全部、退出多选、全选/取消全选、删除、加入歌单和加入队列都提供明确本地化标签；只读“最近播放”不进入可操作节点。
- 所有交互热区至少 48dp；子按钮消费自身事件，不能冒泡触发行播放或选择。

## 9. 测试与验收

### 9.1 JVM/ViewModel

- 固定最近播放倒序与 tie-break。
- 搜索四字段匹配、trim/大小写、关键词变化清空选择。
- More → 多选的 0 项状态、长按进入、点击切换、全选包含不可用记录。
- 播放全部/行点击只提交可播放曲目且顺序正确。
- 单行/批量删除确认前后状态、执行互斥、成功清选择、失败保留选择。
- 单行下一首、加入歌单、加入队列的可用性和跳过计数。

### 9.2 Android/Room

- 按 `TrackId` 单条与批量删除，跨卷身份不误删。
- 删除事务失败不部分提交；清空仍删除全部历史。
- 删除历史不影响曲目、歌单关系和播放队列数据。

### 9.3 Compose UI

- AppBar More 菜单顺序、禁用态与搜索切换。
- `stickyHeader` 常规/多选态切换，0 项已选可见，计数与按钮语义正确。
- TrackRow 单击、长按、选择态整行热区与行尾 More；More 点击不冒泡到播放/选择。
- 行菜单的删除、下一首、加入歌单、歌曲信息及只读最近播放时间。
- 底部三等分操作栏的顺序、禁用态、错误色和 Mini Player 避让。
- `useUnmergedTree = true` 下外层选择语义唯一，内部控件不产生重复焦点。
- 紧凑/中等/展开窗口的 AppBar 导航图标与底部安全区行为。

### 9.4 静态门禁

- English/简体中文资源键一致性。
- `git diff --check`。
- 实现阶段按 `docs/verification.md` 执行 JVM、Lint、Debug APK 和有设备时的 Runtime 测试；本 PRD 阶段不宣称构建或设备通过。

## 10. 明确不做

- 不提供排序、字母索引、播放次数展示或独立常驻的播放历史搜索输入框。
- 不提供历史页的批量“下一首播放”或“隐藏”。
- 不在历史行删除音频文件、媒体库曲目、歌单成员或播放队列项。
- 不新增独立 SongItem；继续使用标准 `TrackRow` 语义和视觉契约。
- 不把页面级 More 菜单、行级 More 菜单和底部批量栏混成一个万能组件。

## 11. 实施顺序

1. 抽取共享 `SelectionBottomBar`，并先修正 `TrackRow` 选择态的单一无障碍语义。
2. 扩展 `HistoryRepository`/Room/Fake 的按标识删除与事务测试。
3. 为 `HistoryViewModel` 增加显式多选、删除、歌曲信息和播放全部状态/动作。
4. 重组页面为 `SearchableTopBar → LazyColumn/stickyHeader → TrackRow → SelectionBottomBar`，补齐历史专用行菜单。
5. 增加双语资源、Compose UI 测试和三档窗口验收，再按验证文档执行构建门禁。
