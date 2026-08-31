# 使用系统启动门控与状态机治理实现冷启动无感化加载

> **状态：已接受 (Accepted)**。关联 [CONTEXT.md](../CONTEXT.md#L14-L24)。

MusicApp 采用 `androidx.core:core-splashscreen` 的 `SplashScreen.setKeepOnScreenCondition` 作为冷启动门控，将系统启动图挂起直至本地 Room 数据库完成首个有效曲库数据流的发派（或命中 `1200 ms` 超时兜底）。门控释放后首帧直接呈现完整歌曲列表，杜绝加载转圈、骨架跳动与首屏闪烁。

所有依赖 Room 持久化投影的 ViewModel（单曲、专辑、艺术家、歌单）初始就绪状态严格重置为 `false`；UI 只有在确凿接收到首次非空流且数据总数为 0 时才允许渲染 `EmptyState`（“未找到单曲”），未就绪期间仅展示纯色透明占位。冷启动的 MediaStore 增量比对延迟至首帧渲染完成后由后台静默派发，并在单个 Room 事务中原子提交，通过 Compose 稳定 Key 实现原位局部 Diff 刷新。
