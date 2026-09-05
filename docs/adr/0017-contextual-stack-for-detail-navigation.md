# 二级详情页采用上下文宿主栈推入与回退

## 背景与问题
应用此前在多栈导航架构（`NavigationState`）中，对所有二级详情页（`AlbumDetailRoute`、`ArtistDetailRoute` 等）采用了基于 `MusicNavKey.owner()` 静态绑定顶级栈的策略。当用户在某详情页触发跨实体跳转时（例如在艺术家详情中点击某张专辑下钻到专辑详情），导航器会强行切换当前活跃的顶级入口至目标实体的所有者（`AlbumsRoute`），并将该详情页推入目标栈。
这导致用户在二级页面之间来回跳转后按系统返回键或顶栏返回按钮时，不是退回刚刚来源的详情页，而是弹出了当前顶级入口的栈顶，意外回到目标类型的一级列表页面（如专辑列表），甚至是直接退回手机桌面，严重破坏了用户的线性探索心智模型。

## 决策
1. **上下文宿主栈推入（Contextual Push）**：
   - 非侧边栏触发的内容下钻跳转（即目标路由为非 `TopLevelNavKey` 的二级详情页，如 `AlbumDetailRoute`、`ArtistDetailRoute`、`PlaylistDetailRoute`、`FolderDetailRoute` 等），一律推入当前所在的宿主顶级栈（`currentTopLevelRoute`），不再切换当前顶级入口。
   - 维持单顶（Single-Top）防重规则：若当前栈顶已是相同路由，不重复压栈；不同路由则遵循线性入栈，按返回键时严格按浏览链路逐级出栈退回上一层上下文。
2. **侧边栏顶级入口重置契约不变（ADR 0010 保持）**：
   - 用户通过侧边栏点击任意一级入口（`TopLevelNavKey`）时，依然执行 `state.reset(route)`，将目标栈重置为单元素根页面栈。侧边栏仍为顶级入口切换与重置的唯一权威渠道。
3. **放宽快照栈内元素类型约束**：
   - `NavigationState.restore` 中的快照合法性校验调整为：栈底必须为对应的一级入口根页面（`index == 0 && route == root`），后续栈元素允许为任意合法的二级详情路由（`index > 0 && route !is TopLevelNavKey`）。
   - 二进制持久化编解码协议保持完全兼容，旧版本快照可平滑恢复。
