# 列表排序采用视口位置锁定与项重排平滑过渡

## 背景与问题
此前应用为了解决列表切换排序时的视觉跳转，在 `ScrollResetEffect.kt` 中引入了强制调用 `scrollToItem(0, 0)` 的重置机制。
但这引入了两个严重的体验缺陷：
1. **浏览上下文暴力打断**：用户滚动到列表中间（如第 30 首曲目）对比切换排序时，页面被生硬扔回顶部第 0 首，丢失了当前的浏览位置。
2. **列表动画被打碎**：瞬间调用 `scrollToItem(0)` 重置了 Compose 测量布局树，导致视口无法为重排项目计算位移差，破坏了列表项动画的展示。

若完全移除该重置逻辑，Compose 默认的**基于 Key 的滚动锚定（Key-based Scroll Anchoring）**策略又会导致反向体验灾难：
当用户在最顶部浏览时，视口首项为歌曲 A；一旦切换排序（如 A $\to$ Z 切换为 Z $\to$ A），歌曲 A 被排至列表底部（如第 500 项）。Compose 的 LazyLayout 会自动追随歌曲 A 的 Key，将视口千里漂移到第 500 项，违背了用户“在最顶部想看重排后新顶部内容”的操作心智。

## 决策
1. **视口索引锁定（Index-based Viewport Lock）**：
   - 废弃“强制归零回到第 0 项”的设计，升级为统一扩展 `LockScrollOnChange`。
   - 在重组稳定状态下时刻捕获当前的视口索引（`firstVisibleItemIndex`）与像素偏移（`firstVisibleItemScrollOffset`）。
   - 当检测到排序 Key 变更时，立即触发 `requestScrollToItem(targetIndex, targetOffset)`，显式解除 Compose 的 Key 追随漂移机制，精准将视口锁定在变更前的项索引。
   - 在最顶部（第 0 项）切换排序时，牢牢锁定在顶部第 0 项；在列表中间切换排序时，保留在当前索引，展示重排后该位置的内容。
2. **统一接入 Compose 1.7+ 项重排动画（Item Reordering Animations）**：
   - 在核心列表与网格（单曲列表、专辑列表/网格、艺术家列表、歌单详情、文件夹详情）中，为项容器挂载 `Modifier.animateItem()`。
   - 由于视口基准位置被精准锁定在原位，可见区域内的项目能够在本地平滑计算位移并播放 Material 3 标准弹簧动效，交互自然连贯。
3. **向后兼容与测试保障**：
   - 保留 `ResetScrollOnChange` 作为兼容函数，默认委托给 `LockScrollOnChange`，并在显式传入目标位置时支持自定义跳转。
   - 仪器测试覆盖初次加载保持、顶部排序保持、非顶部视口保持及反序重排阻断 Key 漂移等完整场景。
