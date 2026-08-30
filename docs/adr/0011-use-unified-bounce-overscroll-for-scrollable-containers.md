# 使用统一物理阻尼弹性过度滚动 (Bounce Overscroll)

> **状态：已接受 (Accepted)**。

## 背景与上下文

Android 12+ 默认的 Stretch Overscroll 在不同 Android 版本和厂商 ROM 上视觉效果割裂（低版本为边缘发光，高版本为边缘拉伸），且默认拉伸行为常覆盖包含 TopBar 和右侧覆盖层（`RightGutterOverlay`）在内的整个父容器，导致快速字母索引条与顶部操作栏在滑动到边界时发生形变或坐标抖动。为了在全应用各页面提供细腻、平滑且统一的现代触控质感（类似 SaltUI / iOS 阻尼回弹风格），需在 DesignSystem 层建立自研的统一弹性过度滚动组件。

## 决策内容

1. **自研统一弹性滚动组件 (`Modifier.bounceOverscroll()`)**：
   在 `core/designsystem/component` 下封装自包含的 `Modifier.bounceOverscroll()` 与物理状态模型，利用 Compose `NestedScrollConnection` 捕获未消费手势位移，并通过 `graphicsLayer { translationY = ... }` 驱动平滑位移，完全解耦底层系统版本的拉伸实现。

2. **非线性对数阻尼与软硬位移约束**：
   - 持续拖拽（User Drag）：手势位移采用非线性阻尼衰减算法，随着拉伸幅度增大阻尼阻力逐渐增强（`resistanceFactor = (1 - (|offset| / maxDisplacement)^2) * 0.4`），最大位移软硬上限固定为 `200dp`。
   - 弹簧复位（Spring Settle）：松手后采用平滑舒缓的低刚度弹簧动画（`dampingRatio = Spring.DampingRatioLowBouncy`, `stiffness = 100f`）平稳吸附回零位，节奏细腻柔和。

3. **惯性冲击（Fling）缓冲吸收**：
   快速滑动撞击边界时，将未消费的剩余惯性速度转化为短暂的微幅冲程（最大限制为 `36dp`）并立即弹簧吸附复位，提供灵动的冲击吸收触感。

4. **视口隔离与边界保护**：
   - 阻尼位移仅施加于列表/内容本身的滚动视口，TopBar 与右侧字母索引条（`RightGutterOverlay`）保持绝对锚定，确保字母索引触控与顶部按钮始终精准可用。
   - PlayerSheet 内部保持其独有的 `PlayerGestureRouter` 展开/折叠与队列手势路由，不产生手势冲突。
   - 全局覆盖单曲、专辑、艺术家、歌单、文件夹、历史、设置、关于、扫描以及所有二级详情页的垂直滚动容器。

## 代价与权衡

自研 NestedScroll 方案保证了跨版本一致性与对覆盖层的零侵入，但需要维护精确的双向 PreScroll / PostScroll 消费逻辑，以确保在用户从拉伸态向回滑动时优先回弹至原位再恢复列表正常滚动。
