# 使用状态感知的 Compose OverscrollEffect

> **状态：已接受 (Accepted)**。取代 [0011-use-unified-bounce-overscroll-for-scrollable-containers.md](0011-use-unified-bounce-overscroll-for-scrollable-containers.md)。

MusicApp 的垂直列表统一通过自定义 `OverscrollEffect` 装饰列表自身的滚动消费，只有滚动状态确认到达对应边界且容器返回真实未消费输入时才显示回弹；不再使用额外的 `NestedScrollConnection` 平移整个列表视口，以避免中段快速滑动误回弹、系统默认效果叠加以及内容侵入固定顶栏。拖拽位移上限为视口高度的 `10%` 且不超过 `96 dp`，fling 位移上限为 `24 dp`；短列表、程序化滚动和系统动画缩放为零时不显示也不消费回弹，顶栏、右侧覆盖层、滚动条、Mini Player 与 Snackbar 始终固定。

Player Queue 复用相同物理模型，但禁用顶部回弹：队列顶部仅向下的拖动或剩余 fling 交给 Player Sheet，向上输入继续滚动队列；队列底部向上只由列表回弹消费。该边界选择以统一跨版本行为和可测试性为优先，代价是维护一份状态感知的效果与 Queue 专用手势路由测试。
