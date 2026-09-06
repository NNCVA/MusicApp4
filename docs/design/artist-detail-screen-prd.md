# 艺术家详情页 (ArtistDetailScreen) 产品需求文档 (PRD)

## 1. 文档概述与背景

### 1.1 需求背景
当前应用中的艺术家详情页仅为一个基础占位列表页面（基于 `CategoryHeader` + `CategoryTrackList`），缺乏沉浸式的视觉呈现、艺术家专属头像展示以及关联专辑聚合能力。
参考 Salt Player 原型设计并结合产品精简与领域模型决策，构建一个信息层次鲜明、视觉纯净、交互直觉并符合应用统一设计规范的全新**艺术家详情页面 (`ArtistDetailScreen`)**。

### 1.2 目标用户与价值
- **沉浸辨识**：提供醒目的大号圆形艺术家头像与居中排版名称，居中聚焦，增强视觉归属感。
- **直觉播放**：通过操作栏标准播放按钮（`▶`）一键顺序播放该艺术家的全部曲目，单曲点播快速建立艺术家播放上下文。
- **双层浏览**：自上而下呈现该艺术家的“全部单曲”与“参与专辑”，支持无缝下钻至专辑详情页。
- **轻量纯粹**：去除冗余的折叠与二级快捷按钮，单曲列表与标准曲目行保持 100% 一致，三点菜单提供完整管理能力。
- **健壮收纳**：散收无专辑歌曲完整收录进“未知专辑”，保障媒体库全部音频的可达性与可发现性。

---

## 2. 领域词汇对齐 (Domain Alignment)

严格遵循 `docs/CONTEXT.md` 规范：

- **艺术家**：由设备媒体库判定属于同一创作者标识的曲目组；本页呈现该艺术家名下的全部曲目及其衍生专辑聚合。
- **曲目**：媒体库中符合准入规则并由应用管理的单个本地音频资源。
- **专辑**：由设备媒体库判定属于同一发行集合的曲目组。
- **专辑参与艺术家**：统计该艺术家在当前专辑内的贡献曲目数（如“1 首”），而非全库总数。
- **播放上下文**：用户点击单曲或播放全部时，以当前艺术家的全部有效曲目构建新的播放队列（`PlaybackContextSource.ARTIST`）。
- **行级管理菜单**：挂载在单曲列表项尾部的三点更多操作入口（`TrackActionsMenu`），统一承载加入歌单、下一首播放、单曲信息查看、隐藏等完整业务。
- **弹性过度滚动**：页面滚动容器触顶或触底时产生的非线性阻尼拉伸动效（`rememberBounceOverscrollEffect`）。

---

## 3. 页面布局与视觉层级

页面整体采用纯色 Surface 底色（不叠加高斯模糊底图），以单列表容器（`LazyColumn`）承载，自上而下分为以下核心区块：

```
┌─────────────────────────────────────────────────────────┐
│ [←] (透明状态栏与沉浸式顶栏，滚动后渐显艺术家名称)       │
├─────────────────────────────────────────────────────────┤
│                          ( ● )                          │
│                      [大号圆形头像]                      │
│                       Alan Walker                       │
├─────────────────────────────────────────────────────────┤
│ [▶ 2]                                                   │
├─────────────────────────────────────────────────────────┤
│ [封面] The Spectre                                   [⋮]│
│        [HQ] Alan Walker - The Spectre                   │
│ [封面] Tired                                         [⋮]│
│        [HQ] Alan Walker/Gavin James - Tired             │
├─────────────────────────────────────────────────────────┤
│ 专辑                                                    │
│ [封面] The Spectre                                      │
│        未知专辑艺术家                                   │
│        1 首                                             │
│ [封面] Tired                                            │
│        未知专辑艺术家                                   │
│        1 首                                             │
│ [封面] 未知专辑                                         │
│        Alan Walker                                      │
│        1 首                                             │
├─────────────────────────────────────────────────────────┤
│ [Mini Player 常驻底栏避让 (80dp + 导航栏安全高度)]       │
└─────────────────────────────────────────────────────────┘
```

### 3.1 顶部导航栏 (TopBar)
- **固定高度**：56dp，顶部添加系统状态栏内边距避让（`WindowInsets.safeDrawing`）。
- **左侧**：标准返回图标按钮（`ic_navigation_back`，触控热区 48dp）。
- **动态平滑淡入**：初始状态（列表处于顶部）顶栏为透明背景且无标题，保持背景纯净；当列表向上滚动、Hero 头像逐渐离开视口后（通过 `derivedStateOf` 检测滚动偏移），顶栏平滑淡入（`fadeIn`/`fadeOut`）显示艺术家名称。

### 3.2 艺术家 Hero 区域 (ArtistHeroSection)
- **居中圆形头像**：尺寸 112dp，使用 `CircleShape` 裁剪。通过 Coil `AsyncImage` 加载 `AudioArtworkRequest.ArtistArtworkRequest`。
- **占位/加载失败回退**：展示 `surfaceVariant` 背景与默认唱片图标。
- **艺术家名称**：居中展示，字号采用 `MusicTheme.typography.titleLarge` / `headlineSmall`（加粗），最多 2 行截断（`Ellipsis`）。

### 3.3 单曲播放控制栏 (TrackActionBar)
- **高度**：48dp（满足最小触控热区）。
- **播放全部按钮**：`ic_playback_play_circle` 图标按钮，点击后以该艺术家全部可用曲目顺序启动播放。
- **曲目计数**：紧随播放图标右侧（如 "2"），字号 `titleMedium` 加粗，无障碍语义合并朗读为“N 首单曲”。
- **精简交互**：无折叠按钮、无多选模式切换按钮，保持最纯粹的播放与计数信息展示。
- **滚动行为**：操作栏作为 `LazyColumn` 的普通内容项，不使用 `stickyHeader` 吸顶。

### 3.4 单曲列表区域 (Tracks Section)
- **列表项复用**：直接复用全局标准 `TrackRow`：
  - `showArtwork = true`（48dp 封面，4dp 圆角 `shapes.extraSmall`）；
  - `showQualityBadge = true`（自动解析位深/采样率角标）；
  - `showMoreMenu = true`（三点菜单 `TrackActionsMenu`，支持下一首播放、加入队列、加入歌单、隐藏、查看歌曲信息等）；
  - 无多选框与额外的加歌按钮；
  - 点击整行直接从该曲目开播当前艺术家播放上下文。

### 3.5 专辑列表区域 (Albums Section)
- **分区标题**：加粗展示“专辑”（`MusicTheme.typography.titleLarge` / `titleMedium`）。
- **排序规则**：专辑列表严格按照**专辑名称字母序升序（A-Z / 拼音升序）**排列。
- **未知专辑收纳**：若该艺术家包含没有专辑元数据的单曲，自动收纳为一个“未知专辑”（`UNKNOWN_ALBUM_ID` / `UNKNOWN_ALBUM_SENTINEL`）项；点击该项可正常导航进入“未知专辑”的详情页面。
- **专辑列表项 (AlbumRow)**：
  - 封面：56dp 方形封面（8dp 圆角 `shapes.small`）；
  - 标题：专辑名称（`titleMedium`）；
  - 副标题 1：专辑艺术家（`bodySmall`，`onSurfaceVariant`）；
  - 副标题 2：该艺术家在该专辑中的曲目数（如“1 首”，`bodySmall`，`onSurfaceVariant`）；
  - 点击响应：整行可点（满足 48dp 触控标准并前置 `.clip()` 水波纹裁剪），点击后通过导航跳转至 `AlbumDetailRoute(albumId, groupKey)`，保留应用层专辑聚合身份。

---

## 4. 架构与数据流规范

### 4.1 核心原则
- **单向数据流 (UDF)**：
  - `ArtistDetailViewModel` 仅依赖 `MediaLibraryRepository.observeTracks()`。
  - 在 `Dispatchers.Default` 线程中过滤出匹配该艺术家的曲目，利用 `AlbumGrouping.group` 聚合出关联专辑列表，并按字母序升序排序。
  - UI Composable 仅消费只读的 `ArtistDetailUiState`。
- **播放控制规范**：
  - 播放全部与单曲点播统一通过 `CategoryPlaybackContextFactory.create(PlaybackContextSource.ARTIST, artistId.name, ...)` 构建播放上下文，并交由 `PlaybackControllerFacade` 调度播放。

### 4.2 UI State 结构设计

```kotlin
data class ArtistDetailUiState(
    val artistId: ArtistId? = null,
    val displayName: String? = null,
    val representativeTrack: Track? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<ArtistAlbumSummary> = emptyList(),
    val isLoaded: Boolean = false,
    val isUnavailable: Boolean = false,
    val currentPlayingTrackId: TrackId? = null,
    val infoTrack: Track? = null,
    val infoMetadata: AdvancedTrackMetadata? = null,
    val playlists: List<Playlist> = emptyList(),
)

data class ArtistAlbumSummary(
    val albumId: AlbumId,
    val title: String,
    val artistName: String,
    val artistTrackCount: Int,
    val representativeTrack: Track,
)
```

---

## 5. 设计令牌与无障碍规范

| UI 维度 | 对应设计令牌 / 规则 | 说明 |
| :--- | :--- | :--- |
| **页面左右间距** | `MusicTheme.dimensions.contentHorizontalPadding` (24dp) | 全局统一对齐 |
| **最小触控热区** | `MusicTheme.dimensions.minimumTouchTarget` (48dp) | 所有可点击项严格保证 |
| **艺术家头像尺寸** | `112dp` (CircleShape) | 醒目视觉中心，完全圆形剪裁 |
| **单曲封面尺寸** | `48dp` (MusicTheme.shapes.extraSmall, 4dp) | 全局单曲行统一标准 |
| **专辑封面尺寸** | `56dp` (MusicTheme.shapes.small, 8dp) | 适中专辑视觉 |
| **圆角交互裁剪** | `.clip(shape).clickable { ... }` | 严格遵守 `selection-and-toggle-controls.md` |
| **无障碍语义** | `.semantics(mergeDescendants = true)` | 播放按钮与曲目数合并朗读，图标提供 contentDescription |
| **双语国际化** | `strings.xml` (中/英) | 严禁硬编码文本 |
| **滚动动效** | `rememberBounceOverscrollEffect` | 列表容器平滑弹簧过度滚动 |

---

## 6. 异常与边界场景处理

1. **空曲目/艺术家缺失**：
   - 若该艺术家在已就绪媒体库中无法匹配任何曲目（如外部删除或实体失效），呈现全屏 `isUnavailable` 失效态（“艺术家不可用”）并保留返回；若艺术家存在但曲目为空，展示纯文本空态说明，严禁越权引导扫描音乐。
2. **纯未知专辑场景**：
   - 若该艺术家所有歌曲均无专辑信息，单曲列表正常展示，专辑分区展示一个“未知专辑”收纳项，点击可查看完整未知专辑详情。
3. **超长文本排版**：
   - 艺术家名称与专辑名称超长时自动截断（最多 2 行），并提供 Semantics 完整朗读。
4. **Mini Player 遮挡**：
   - `Navigation.kt` 传入的 `bottomPadding` 已包含 Mini Player 的 80dp 与系统底部安全区；详情页只追加列表自身的末尾间距，不重复叠加 80dp。

---

## 7. 已确认冻结边界（2026-09-05）

本节记录用户确认后的实现边界，优先级高于原型图片中的历史交互表现；本节不会授权超出范围的页面或全局改造。

### 7.1 规格优先级与页面行为

- PRD 作为本次实现规格，原型图片只保留信息层级与视觉参考。
- 原型中的随机播放图标、折叠按钮和歌曲行加号不进入本页；本页使用“播放全部”、曲目数和行级更多菜单。
- 页面采用纯色 `Surface`，不添加大面积模糊封面背景。
- TopBar 固定 56dp、避让状态栏；顶部时透明无标题，Hero 离开视口后淡入艺术家名，回顶时淡出。
- 操作栏固定 48dp，但随列表滚动，不吸顶、不启用长按多选。

### 7.2 数据身份与专辑聚合

- 艺术家身份使用完整、裁剪后的艺术家标签，不按 `/`、`;`、`feat.` 等合作分隔符拆分；展示名与稳定路由 key 分离。
- 先对当前可见媒体库执行 `AlbumGrouping.group`，再按艺术家曲目 ID 求交集生成 `ArtistAlbumSummary`，避免改变既有 `AlbumGroupKey` 合并结果。
- `ArtistAlbumSummary` 必须携带 `groupKey`；专辑详情导航同时传递 `albumId` 与 `groupKey`。
- 未知专辑使用 `UNKNOWN_ALBUM_ID` / `UNKNOWN_ALBUM_SENTINEL`，沿用全局未知专辑置顶规则。
- 曲目数统计可见曲目（包括暂时不可用曲目）；播放上下文只纳入 `Availability.AVAILABLE` 曲目。

### 7.3 组件与业务能力

- 单曲列表直接使用 `TrackRow`；不嵌套 `CategoryTrackList`，所有区块共用一个 `LazyColumn`。
- 操作栏复用 `ListActionBar` 的普通列表项形态；专辑项使用新增的 `ArtistAlbumRow`，Hero 使用新增的 `ArtistHeroSection`。
- `TrackActionsMenu` 必须具备加入队列、下一首播放、加入歌单、隐藏和歌曲信息；`QualityBadge` 继续沿用当前 MIME/扩展名/码率识别规则。
- `ArtistDetailViewModel` 负责单曲、专辑摘要、播放上下文、歌单与歌曲信息状态；UI 仅消费只读状态。

### 7.4 状态、Insets 与自适应

- 详情状态至少区分 `Loading`、`Content`、`Empty`、`Unavailable`、`StorageOffline` 和 `Error`；只有媒体库就绪后才能判定空状态。
- 详情页不承担扫描职责，严禁保留扫描入口；详情路由不暴露扫描回调。曲目为空时自动隐藏播放全部等行动栏。
- 底部直接沿用调用方传入的 `bottomPadding`，不再额外增加 Mini Player 的 80dp。
- 覆盖 `Compact`、`Medium`、`Expanded` 窗口；新增 `detailTopBarHeight`、`artistHeroArtworkSize`、`albumRowArtworkSize`、`albumRowMinHeight` 等设计令牌。
- 艺术家名采用加粗 `titleLarge`；专辑标题最多两行，并由 `albumRowMinHeight` 保证三行元数据的可读高度。

### 7.5 实施与验收边界

- 本次只改艺术家详情相关 UI、ViewModel、专辑摘要/路由衔接、共享歌曲菜单、双语资源、Token 和对应测试。
- 不引入网络艺术家资料、简介或新的远程图片来源。
- 页面实现后至少补充艺术家身份、专辑交集与 `groupKey`、未知专辑、计数口径、状态和路由的 JVM 测试，并补充详情布局/无障碍交互测试。
