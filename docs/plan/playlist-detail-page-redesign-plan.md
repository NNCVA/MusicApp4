# 歌单详情页改造技术实施方案 (Playlist Detail Redesign Plan)

**文档状态**：待评审与执行 (Pending Review & Execution)  
**对齐基线**：[`docs/design/playlist-detail-prd.md`](../design/playlist-detail-prd.md)、[`docs/CONTEXT.md`](../CONTEXT.md)、[`docs/adr/0005-use-replacing-message-bubble.md`](../adr/0005-use-replacing-message-bubble.md)、[`docs/adr/0008-use-unified-right-gutter-overlay-and-fixed-index.md`](../adr/0008-use-unified-right-gutter-overlay-and-fixed-index.md)、[`docs/adr/0011-use-unified-bounce-overscroll-for-scrollable-containers.md`](../adr/0011-use-unified-bounce-overscroll-for-scrollable-containers.md)、[`docs/adr/0014-use-unified-app-dropdown-menu.md`](../adr/0014-use-unified-app-dropdown-menu.md)

---

## 1. 方案目标与架构概述

依据已冻结的 PRD 规范 v1.2 以及已抽取的通用组件基线（`TrackRow` & `TrackActionsMenu` in `core/designsystem`），重构并全面升级歌单详情页（`PlaylistDetailScreen`），使其具备：
1. **统一视口与层级架构**：自适应脚手架、滚动折叠联动 TopAppBar、Hero 头部（130×130dp r12 四宫格封面 + 丰富元信息）。
2. **吸顶操作栏与多选体系**：48dp `ResponsiveActionBar` 吸顶联动，常规态（播放全部/随机/排序/批量管理）与多选态（取消/计数/全选反选）`Crossfade` 原地切换；底部 `SelectionBottomBar` 悬浮于 80dp Mini Player 上方（移出歌单/加入其他歌单/加入队列）。
3. **全局索引与列表规范**：直接复用通用 72dp `TrackRow` 并挂载歌单专属行级菜单；严格落实 ADR-0008 统一 `RightGutterOverlay`（文本排序固定 28 逻辑桶 + 72dp 放大气泡，数值/时间排序平滑降级为滚动滑块）。
4. **组件治理与复用**：全量复用 `MessageBubbleHost`、`AppDropdownMenu`、`TextInputDialog`、`ConfirmationDialog`、`TrackInfoViewer` 与 `BounceOverscroll`。

---

## 2. 模块分工与实现步骤

```
歌单详情重构演进路线：
Step 1: 领域与数据层契约完善 (PlaylistModels / Sorting / UseCase)
  ↓
Step 2: ViewModel 状态流重构 (PlaylistDetailViewModel: 搜索/排序/多选/批量/歌曲信息)
  ↓
Step 3: 四宫格封面组件落地 (QuadPlaylistArtwork - 130×130dp r12 自适应拼图与缓存)
  ↓
Step 4: PlaylistDetailScreen UI 全面重构 (TopAppBar联动 / Hero / 吸顶操作栏 / 复用TrackRow / 覆盖层 / 底部浮栏)
  ↓
Step 5: 单元测试与仪器测试覆盖 (ViewModelTest / ScreenTest / GutterModeTest)
  ↓
Step 6: 完整门禁与自动化验证 (testDebugUnitTest + lintDebug + assembleDebug)
```

---

## 3. 详细设计与代码变更清单

### 3.1 Step 1: 领域与状态模型完善
- **文件**：[`PlaylistModels.kt`](file:///Users/a1/develop/repo/MusicApp4/app/src/main/java/com/musicapp/player/feature/playlists/PlaylistModels.kt)
- **改动**：
  - 定义 `PlaylistTrackSortField`（`DEFAULT`, `TITLE`, `ARTIST`, `ALBUM`, `DURATION`, `FILE_SIZE`）及排序方向；
  - 增强 `PlaylistPlaybackContextFactory` 支持按当前视图排序建立播放队列及随机播放序列（`shuffle`）；
  - 扩展 `PlaylistDetailUiState` 完整承载搜索、排序、分组 sections、批量执行结果、TrackInfo 弹窗状态。

### 3.2 Step 2: ViewModel 状态流与业务处理重构
- **文件**：[`PlaylistsViewModel.kt`](file:///Users/a1/develop/repo/MusicApp4/app/src/main/java/com/musicapp/player/feature/playlists/PlaylistsViewModel.kt)
- **改动**：
  - `PlaylistDetailViewModel` 引入 `BatchTrackActionExecutor`，处理【移出歌单】、【加入其他歌单】、【加入队列】；
  - 实现视图级排序（`selectSort`）与分组（`sections` / `sectionPositions` 计算）；
  - 实现页内实时搜索过滤与重置；
  - 接入 `TrackInfoViewer` 元数据加载与状态分发；
  - 完善操作与批量事务反馈流。

### 3.3 Step 3: 四宫格封面组件 (`QuadPlaylistArtwork`)
- **文件**：[`QuadPlaylistArtwork.kt`](file:///Users/a1/develop/repo/MusicApp4/app/src/main/java/com/musicapp/player/feature/playlists/QuadPlaylistArtwork.kt) [NEW]
- **改动**：
  - 尺寸固定 `130×130dp`，圆角 `12dp`（`MusicShapes.medium`）；
  - 封面解析算法：
    - 可用封面 $\ge 4$：渲染 $2 \times 2$ 宫格（子格 $63 \times 63\text{dp}$，间隙 $4\text{dp}$）；
    - 可用封面 $1 \sim 3$：单图全尺寸铺满（$130 \times 130\text{dp}$）；
    - 0 张封面：显示系统默认矢量占位图 (`ic_playlist_album`)；
  - 采用 Coil `AsyncImage` 并行加载与统一缓存策略。

### 3.4 Step 4: 独立 `PlaylistDetailScreen.kt` 视图重构
- **文件**：[`PlaylistDetailScreen.kt`](file:///Users/a1/develop/repo/MusicApp4/app/src/main/java/com/musicapp/player/feature/playlists/PlaylistDetailScreen.kt) [NEW]
- **改动**：
  - 将 `PlaylistDetailScreenRoute` 和 `PlaylistDetailScreen` 从 `PlaylistsScreen.kt` 解耦抽取为独立文件；
  - **TopAppBar**：返回键（优先退出多选/搜索）+ 标题淡入淡出（Hero 完全滚出视口后淡入歌单名）+ 更多菜单（搜索/排序/重命名/删除歌单）；
  - **Hero Header**：`QuadPlaylistArtwork` + 大标题 + 曲目数 + 总时长 + 创建日期；
  - **ResponsiveActionBar**：`stickyHeader` 吸顶，常规态（播放全部/随机/排序/批量管理）与多选态（取消/计数/全选）`Crossfade` 原地切换；
  - **TrackRow**：直接复用 `com.musicapp.player.core.designsystem.component.TrackRow`，并通过 `trailingContent` 挂载歌单专属行级菜单（含【从歌单移出】、【下一首播放】、【加入队列】、【加入其他歌单】、【查看信息】）；
  - **RightGutterOverlay**：文本排序激活固定 28 桶索引 + 72dp 字母放大气泡，其他排序降级滚动滑块；
  - **SelectionBottomBar**：悬浮于 Mini Player 80dp 上方，支持【移出歌单】、【加入其他歌单】、【加入队列】；
  - **弹窗与覆盖层**：接入 `TextInputDialog`、`ConfirmationDialog`、`AddToPlaylistSelectionDialog`、`TrackInfoViewer`、`rememberBounceOverscrollEffect`。

### 3.5 Step 5: 自动化测试套件
- **文件**：
  - [`PlaylistDetailViewModelTest.kt`](file:///Users/a1/develop/repo/MusicApp4/app/src/test/java/com/musicapp/player/feature/playlists/PlaylistDetailViewModelTest.kt) [NEW]
  - [`PlaylistDetailScreenTest.kt`](file:///Users/a1/develop/repo/MusicApp4/app/src/androidTest/java/com/musicapp/player/feature/playlists/PlaylistDetailScreenTest.kt) [NEW]
- **改动**：
  - 单元测试：测试排序切换、搜索过滤、多选状态合并、批量移出、随机播放上下文生成；
  - 仪器测试：测试折叠联动、48dp 最小触控热区、未合并语义树无障碍单节点、四宫格拼图分支、吸顶栏 Crossfade 切换与 RightGutterOverlay 滑动定位。

---

## 4. 验证与门禁标准

严格遵循 [`docs/verification.md`](../verification.md) 与 Java 21 环境变量配置：
1. **全量 JVM 单元测试**：
   ```shell
   export JAVA_HOME="$(jenv prefix 21 2>/dev/null || echo '/Users/a1/.jenv/versions/21')"
   export PATH="$JAVA_HOME/bin:$PATH"
   ./gradlew :app:testDebugUnitTest --no-daemon --console=plain
   ```
2. **Lint 代码规范分析**：
   ```shell
   ./gradlew :app:lintDebug --no-daemon --console=plain
   ```
3. **Debug APK 编译构建**：
   ```shell
   ./gradlew :app:assembleDebug --no-daemon --console=plain
   ```
4. **完整门禁**：
   ```shell
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
   ```
