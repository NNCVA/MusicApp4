# 使用覆盖式全局消息气泡

> **状态：已接受（带修订附注）(Accepted with Amended Notes)**。
>
> **修订附注（2026-09）**：
> 气泡即时覆盖替换策略、居中布局、200ms 淡入淡出动效与底部抬高契约保持完全一致。表面色彩由最初记录的固定白色升级为 Material 3 语义反色方案（`color = MaterialTheme.colorScheme.inverseSurface`, `contentColor = MaterialTheme.colorScheme.inverseOnSurface`），自适应系统明暗模式与无障碍对比度要求。

MusicApp 的短时文本反馈统一使用应用级白色胶囊消息气泡，气泡内文字居中显示；新消息产生时立即替换当前消息，不保留等待显示的旧消息。气泡相对原有底部位置上移 `16 dp`，新旧消息使用 `200 ms` 淡入淡出交叉过渡。该方案优先保证用户看到最新操作结果，并避免连续操作时旧提示延迟出现；确认对话框、加载状态和扫描结果对话框继续由各自的专用界面承载。
