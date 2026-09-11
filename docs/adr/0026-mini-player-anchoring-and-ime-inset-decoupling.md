# 迷你播放条吸底锚定与输入法 Inset 解耦契约

## 背景与问题

在先前架构与页面实现中，迷你播放条与主内容区共享 `WindowInsets` 分发，存在以下交互与视觉缺陷：
1. **软键盘弹出导致迷你播放条被顶起**：先前 `PlayerSheet` 收起态时计算 `totalCollapsedHeight = dimensions.miniPlayerHeight + bottomInset`，其中的 `bottomInset` 直接使用 `contentInsets.asPaddingValues().calculateBottomPadding()`，而 `contentInsets` 默认为 `WindowInsets.safeDrawing`。由于 `safeDrawing` 包含 `WindowInsets.ime`（软键盘），在搜索页或任何包含输入框的场景唤起键盘时，迷你播放条会被整体推至键盘上方，破坏了音乐应用底栏常驻锚定的心智，造成视觉跳动与界面遮挡。
2. **“其他的”干扰与层级反向挤压**：系统级动态 Inset（浮动键盘、分屏输入法、剪贴板候选条、导航栏模式切换）及业务级操作浮层（如多选批量操作栏 `SelectionBottomBar`、消息气泡 `MessageBubbleHost`、弹窗）如果混入迷你播放条的底层定位计算，会造成层级混乱与反向挤压。
3. **列表滚动视口与键盘避让的死区问题**：若简单将列表避让写为 `imeBottom + miniPlayerHeight`，当迷你播放条停留在底层被键盘覆盖时，键盘上方将凭空多出 64dp 的空白死区。
4. **焦点与转场动效冲突**：当键盘处于弹起状态时，若用户直接点击切歌或展开全屏播放界面（`PlayerSheet`），键盘若不主动收起，会与全屏展开动画发生视觉分层撕裂。

## 决策

### 1. 迷你播放条物理吸底锚定与动态 Inset 解耦
- **严格吸底定位**：迷你播放条（`MiniPlayer`）在收起状态下的吸底偏移量 `bottomInset` 严格且唯一由系统物理导航栏（`WindowInsets.navigationBars` 或 `WindowInsets.systemBars.only(WindowInsetsSides.Bottom)`）决定。
- **完全免疫 IME 与其他动态 Insets**：迷你播放条不再消费 `WindowInsets.ime` 或其他动态 Insets；软键盘弹出时，键盘直接从屏幕最底部升起并覆盖在迷你播放条上方；键盘收起时迷你播放条原地保持不变，整个过程无任何纵向位移与跳动。

### 2. 列表内容层动态避让与死区消除公式
- **双重内边距融合**：主界面内容列表（如 `LazyColumn`）的底部避让内边距解耦计算，采用最大值融合公式：
  `contentPadding.bottom = max(navBarBottom + miniPlayerHeight, imeBottom)`
- **死区消除**：无键盘时完整露出迷你播放条；键盘升起且高度大于迷你播放条时，列表恰好贴合在软键盘上沿滚动，消除键盘与曲目项之间的 64dp 悬空空白。

### 3. 多选批量操作栏（SelectionBottomBar）吸底锚定与键盘解耦契约
- **严格吸底与层级常驻**：多选操作栏始终停留在迷你播放条正上方（无播放条时停靠在系统导航栏上沿），对软键盘（`WindowInsets.ime`）具备绝对免疫性。软键盘弹起时，键盘直接从前景自下而上覆盖多选栏，多选操作栏保持绝对静止，决不被键盘顶起。
- **多选模式下列表避让解耦**：在多选模式下，列表（`LazyColumn`）底部避让内边距固定在多选栏上沿（`persistentBottom + selectionBarHeight`），完全不感知软键盘高度；软键盘弹起时直接覆盖列表下半部分曲目，收起键盘后列表无缝贴合多选栏上沿，避免列表在打字时被压缩挤压。
- **全场景协同收起软键盘**：
  - **进入多选自动收起**：用户长按曲目进入多选批量管理状态时，主动收起软键盘（`keyboardController?.hide()`），优先释放屏幕空间以展示选中的曲目列表与 `SelectionBottomBar`；
  - **勾选曲目立即收起**：若多选模式下用户重新唤起键盘搜索，在未被遮挡的上方列表区域点击任意曲目进行勾选/取消勾选（或全选/反选）时，立即自动收起软键盘，使被遮挡的多选批量操作栏即刻完整恢复呈现。

### 4. 切歌与全屏播放器展开时的键盘主动收起
- 在键盘处于打开状态时，用户点击任意搜索结果曲目播放，或点击展开全屏播放器（`FullPlayer`）时，统一主动调用软键盘收起操作，保证全屏向上滑动转场动效纯粹顺畅，杜绝动画撕裂与输入法遮挡。

## 影响与验证

- **架构解耦**：`AppShell` 与 `PlayerSheet` 明确区分 MiniPlayer 物理锚定 Insets 与全屏 PlayerSheet Insets，收起态彻底解耦键盘高度；
- **自动化门禁**：通过 JVM 单元测试验证避让公式与各维度边界值计算；通过 UI 测试验证键盘升起时迷你播放条坐标不变、多选模式与展开全屏时键盘收起行为。
