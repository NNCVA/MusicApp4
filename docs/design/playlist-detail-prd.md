# 歌单详情页产品需求文档 (Playlist Detail PRD)

**文档状态**：已完全冻结并确认 (Decisions Finalized & Approved)  
**文档版本**：v1.2  
**适用范围**：MusicApp 歌单详情页面 (`PlaylistDetailScreen`)  
**对齐基线**：[`docs/design/implementation-spec.md`](implementation-spec.md)、[`docs/CONTEXT.md`](../CONTEXT.md)、[`docs/adr/0005-use-replacing-message-bubble.md`](../adr/0005-use-replacing-message-bubble.md)、[`docs/adr/0008-use-unified-right-gutter-overlay-and-fixed-index.md`](../adr/0008-use-unified-right-gutter-overlay-and-fixed-index.md)、[`docs/adr/0011-use-unified-bounce-overscroll-for-scrollable-containers.md`](../adr/0011-use-unified-bounce-overscroll-for-scrollable-containers.md)、[`docs/adr/0014-use-unified-app-dropdown-menu.md`](../adr/0014-use-unified-app-dropdown-menu.md)

---

## 1. 需求背景与产品定位

### 1.1 业务背景
歌单是用户组织管理个人音频资源的核心资产载体。原歌单详情页仅具备基础的列表展示与简易移除功能，缺少封面视觉感知、头部元信息汇总、快捷批量操作以及与全局一致的索引/滑块交互。

### 1.2 设计目标
1. **视觉与品牌一致性**：引入 130×130dp (r12) 四宫格封面及信息丰富的 Hero 区域，统一 Aero 动态背景与 Material You 设计令牌。
2. **高效播放与管理**：提供吸顶式 `ResponsiveActionBar`，一键触达“播放全部”、“随机播放”、“排序”和“批量管理”。
3. **统一列表与索引体验**：复用标准 `TrackRow` 视觉规范，接入全局统一的 `RightGutterOverlay`（文本排序启用固定 28 逻辑桶索引，时间/时长等排序平滑降级为滚动滑块）。
4. **完善的多选批量体系**：对齐 Tracks 页面的多选手势与底部批量操作栏（移出歌单、加入其他歌单、加入队列），与底部 80dp Mini Player 优雅协同。
5. **高度复用通用组件规范**：全面复用设计系统中的气泡通知（`MessageBubbleHost`）、通用对话框（`TextInputDialog` / `ConfirmationDialog`）、下拉菜单（`AppDropdownMenu`）与阻尼过度滚动（`BounceOverscroll`）。

---

## 2. 页面架构与视图层级

```
AdaptiveMusicScaffold
├── TopAppBar（通用顶部导航栏）
│   ├── NavigationIcon: [←] 返回（多选/搜索优先退出）
│   ├── Title: 滚动联动标题（初始静止时显示淡色“歌单详情”或留空，Hero 滚出视口后平滑淡入当前“歌单名”）
│   └── Actions: [⋮] 更多菜单（搜索 / 排序 / 重命名 / 删除歌单）
│
├── 内容视口 (LazyColumn / 弹性过度滚动视口)
│   ├── Hero Header（歌单头部信息）
│   │   ├── Cover: 130×130dp 四宫格封面 (r12，可用封面≥4张为2×2网格；1~3张单图全铺；0张默认占位)
│   │   └── Meta: 歌单名、共 N 首、总时长 (X小时Y分)、创建于 yyyy-MM-dd
│   │
│   ├── 吸顶操作栏 (ResponsiveActionBar - stickyHeader, 高48dp)
│   │   ├── 常规态: [▶ 播放全部] | [🔀 随机] | [⇅ 排序] | [☰✓ 批量管理]
│   │   └── 多选态 (Crossfade 原地切换): [✕ 取消] + 已选 N 首 + [☑ 全选/反选]
│   │
│   └── ListViewport (曲目列表视口 - TrackRow.PlaylistMember)
│       ├── TrackRow: 封面 (48dp r8) + 标题 (最多2行) + 音质角标 (SQ/Hi-Res) + 艺术家/专辑名 + 状态标签 + 行尾 [⋮] 或 勾选框
│       └── 局部空状态 (EmptyState - 当歌单曲目数为 0 或搜索无结果时呈现)
│
├── 统一右侧覆盖层 (RightGutterOverlay - ADR-0008)
│   ├── 文本类排序（标题/艺术家/专辑）：GutterMode.Index (0 → A…Z → # 固定 28 逻辑桶 + 72dp 字母放大气泡)
│   └── 数值/时间类排序（添加时间/时长/大小）：GutterMode.Scrollbar (滚动滑块模式)
│
├── 底部批量操作栏 (SelectionBottomBar - 仅多选激活时悬浮展开)
│   └── 位于 Mini Player 上方：[移出歌单] | [加入其他歌单] | [加入队列]
│
└── 底部 Mini Player (迷你播放器)
    └── 有播放曲目时常驻占位 80dp（内含 48dp 触控胶囊卡片），无曲目时不占位
```

---

## 3. 详细功能与交互规格 (已确认决策)

### 3.1 TopAppBar（顶部导航栏与联动）
- **左侧导航**：返回键 `[←]`，点击返回上一级歌单列表；若当前处于多选或搜索模式，按返回键优先退出多选或搜索。
- **标题展示与滚动折叠联动**（**已决策·选项A**）：
  - 初始静止状态时，TopAppBar 标题显示淡色“歌单详情”（或留空），将视觉焦点让给 Hero 区域的大字号歌单名；
  - 当列表向上滚动、Hero 区域完全滚出顶部视口后，TopAppBar 标题平滑淡入当前“歌单名”，提供清晰的上下文认知；向下回滚时反向淡出。
- **右侧菜单 `[⋮]`**：
  - **搜索**：展开页内搜索栏，支持实时过滤。
  - **排序**：唤起排序菜单。
  - **重命名**：弹出 `TextInputDialog` 重命名歌单。
  - **删除歌单**：弹出 `ConfirmationDialog` 二次确认危险操作对话框，确认后删除歌单并自动返回上一级。

---

### 3.2 Hero 头部区域
- **尺寸与排版**：水平 Row 布局，左侧为 130×130dp 封面，右侧为元信息列，垂直居中对齐，左右留白符合 `dimensions.contentHorizontalPadding`。
- **四宫格封面 (Quad Cover Artwork)**（**已决策·选项A**）：
  - **尺寸与圆角**：`130×130dp`，圆角 `12dp`（`MusicShapes.medium`）。
  - **拼图算法**：
    - 当歌单可用封面数 $\ge 4$：取前 4 张可用内嵌封面组成 $2 \times 2$ 宫格（每个子格 $63 \times 63dp$，间隙 $4dp$）；
    - 当歌单可用封面数为 $1 \sim 3$：使用第 1 张有效封面**全尺寸单图铺满 (130×130dp)**，保证视觉饱满，不出现不对称网格；
    - 当 0 张可用封面或空歌单：展示系统默认歌单矢量占位图 (`ic_playlist_album`)。
  - **性能与缓存**：基于 Coil `AsyncImage` 并行加载与内存磁盘双缓存。
- **元信息展示**：
  - **歌单标题**：`typography.headlineSmall`，加粗，最多 2 行，超出省略。
  - **曲目数**：`共 N 首`（使用本地化复数字符串 `category_track_count`）。
  - **总播放时长**：计算歌单所有可用曲目总时长（如 `38 分钟`、`1 小时 24 分钟`）。
  - **创建日期**：`创建于 yyyy-MM-dd`（由 `Playlist.createdAtMs` 格式化）。

---

### 3.3 吸顶操作栏 (ResponsiveActionBar)
- **吸顶机制**：在 `LazyColumn` 中作为 `stickyHeader` 承载，随页面上滑当 Hero 滚出视口后自动吸附在 `TopAppBar` 正下方；背景采用半透明高斯模糊/Aero 材质。
- **多选联动**（**已决策·选项A**）：
  - 常规态：固定 `48dp` 高度，包含【播放全部】、【随机】、【排序】、【批量管理】；
  - 进入多选模式时，吸顶栏原地 `Crossfade` 切换为多选状态栏（`[✕ 取消] + 已选 N 首 + [☑ 全选/反选]`），触手可及。
- **四个核心动作**：
  1. **播放全部 (Play All)**：以当前列表排序从第一首可用曲目建立播放队列并立即播放。
  2. **随机播放 (Shuffle)**：生成稳定随机序列（`Stable Shuffle Sequence`），建立播放上下文并从随机首曲开始播放。
  3. **排序 (Sort)**（**已决策·选项A**）：
     - 排序作为当前页面的浏览/播放视图，不破坏底层 `Playlist.trackIds` 的原始添加序列；
     - 提供：【默认添加顺序】、【歌曲标题】、【艺术家】、【专辑】、【歌曲时长】、【文件大小】；
     - 选【默认添加顺序】随时恢复原添加顺序。
  4. **批量管理 (Batch Manage)**：激活多选模式，操作栏原地切换为多选状态。

---

### 3.4 列表视口与列表项 (ListViewport & TrackRow)
- **列表项规范**：高度 `72dp`，严格复用 `TrackRow` 统一设计：
  - **左侧**：48×48dp 方形圆角封面（`r8`）。
  - **中间信息**：
    - 第一行：曲目标题（最多 2 行，超长省略）。
    - 第二行：音质角标（`SQ` / `Hi-Res` 等） + 艺术家名 + 专辑名。
  - **状态指示**：`Availability.TEMPORARILY_UNAVAILABLE` 置灰并显示“暂时不可用”。
  - **右侧操作**：
    - 常规模式：固定 `48×48dp` 触控热区的 `[⋮]` 更多菜单；
    - 多选模式：固定 `48×48dp` 勾选图标（选中实心圆勾 `ic_common_check_circle`，未选空心圆 `ic_common_radio_button_unchecked`）。
- **行尾 `[⋮]` 菜单能力**（**已决策·选项A**）：
  - 1. **从歌单中移出 (Remove from Playlist)**（红色/强调）；
  - 2. **下一首播放 (Play Next)**；
  - 3. **加入播放队列 (Add to Queue)**；
  - 4. **加入其他歌单 (Add to Playlist)**；
  - 5. **查看歌曲信息 (Song Info)**。
  - （移除【隐藏曲目】，歌单内统一由【从歌单中移出】承载）。

---

### 3.5 统一右侧覆盖层 (RightGutterOverlay) 联动规则
- **严格遵循 ADR-0008**：
  - **文本类排序（标题、艺术家、专辑）**：激活 `GutterMode.Index`（固定 28 逻辑桶 `0 → A…Z → #`，72dp 放大气泡，跃迁触觉震动，空桶就近寻址）。
  - **数值与时间类排序（添加时间、时长、文件大小）**：激活 `GutterMode.Scrollbar`（滚动指示滑块）。
- 右侧覆盖层与列表并列，不侵占列表测量宽度，列表行尾 `[⋮]` 热区优先。

---

### 3.6 底部批量浮栏 (SelectionBottomBar)
- **呈现位置**：悬浮在底部 Mini Player（80dp）上方；无 Mini Player 时贴底。
- **批量能力**（**已决策·剔除下一首播放**）：
  1. **移出歌单 (Remove Selected)**：批量移出选中曲目；
  2. **加入其他歌单 (Add Selected to Playlist)**：弹出歌单选择弹窗；
  3. **加入播放队列 (Add Selected to Queue)**：批量追加至队列尾部。

---

### 3.7 通用自定义组件复用规范
- **短时操作气泡**：操作反馈统一走全局覆盖式胶囊气泡（`MessageBubbleHost`，遵循 ADR-0005），展示“已从歌单移出 N 首歌曲”等提示，不遮挡操作。
- **弹窗组件**：
  - 重命名歌单复用 `TextInputDialog`；
  - 删除歌单/清空操作复用 `ConfirmationDialog`（危险操作标记 `isDestructive = true`）；
  - 添加至其他歌单复用 `AddToPlaylistSelectionDialog`。
- **下拉菜单**：所有行级与页级菜单统一复用 `AppDropdownMenu` / `AppDropdownMenuItem`（遵循 ADR-0014，内边距归零，圆角联动裁剪，满足 48dp 触控）。
- **阻尼回弹**：滚动视口统一挂载 `rememberBounceOverscrollEffect`（遵循 ADR-0011 / ADR-0012）。

---

## 4. 状态模型与架构契约

```kotlin
data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val sort: PlaylistTrackSort = PlaylistTrackSort.DEFAULT,
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isLibraryLoaded: Boolean = false,
    val operationMessage: PlaylistOperationMessage? = null,
    val batchResult: BatchTrackActionResult? = null,
    val playbackFeedback: PlaylistPlaybackPreparation? = null,
    val infoTrack: Track? = null,
    val infoMetadata: TrackMetadata? = null,
    val isInfoLoading: Boolean = false,
) {
    val totalDurationMs: Long
        get() = tracks.sumOf { it.durationMs }

    val selectedTrackIdsInOrder: List<TrackId>
        get() = (if (isSearching) filteredTracks else tracks)
            .map(Track::id)
            .filter(selectedTrackIds::contains)
}
```
