# 使用统一规范的行级管理下拉菜单

MusicApp 的行级管理菜单与页级操作菜单统一收敛为设计系统中的 `AppDropdownMenu` 与 `AppDropdownMenuItem` 组件。

1. **消除内边距留白**：移除原生 Material 3 `DropdownMenuContent` 默认的 `8dp` 垂直内边距，使首项与末项的高亮背景在聚焦、按压或悬停时完全贴合到菜单卡片顶部与底部边缘。
2. **圆角联动裁剪**：外层容器采用统一的 `MusicTheme.shapes.large` 圆角并显式执行 `.clip(shape)`，首末菜单项的高亮背景自动顺应卡片圆角，与应用内的胶囊消息气泡（`MessageBubbleHost`）、通用确认弹窗（`ConfirmationDialog`）和文本输入弹窗（`TextInputDialog`）保持高度一致的视觉质感。
3. **触控与无障碍门禁**：每个菜单项强制满足至少 `48dp` 触控热区（`minimumTouchTarget`），自带规范的水波纹反馈与语义合并节点，危险操作（如删除）统一采用 `MusicTheme.colors.error` 警示色。
4. **锚点动效与生命周期**：所有 `AppDropdownMenu`（包括排序、筛选、页级和行级菜单）统一使用以实际 Popup 位置推导的四角锚点缩放与淡入淡出过渡，打开从 `0.8` 缩放至 `1.0` 并淡入，关闭反向执行；出现时长为 `150ms`、消失时长为 `100ms`。关闭期间保留 Popup 但屏蔽菜单交互，过渡中重新打开沿同一状态反向追踪；系统关闭动效时直接到达终态。播放器 More 入口打开的 `TrackInfoViewer` 属于信息查看界面，不属于此菜单过渡。
