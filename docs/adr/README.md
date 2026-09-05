# 架构决策记录

ADR = Architecture Decision Record（架构决策记录）。当前实现以代码、测试和实际验证为准；ADR 用于保留不可逆取舍，并在行为变更时同步更新。

## 当前状态（2026-09-05 静态核对）

| 状态 | ADR | 说明 |
| --- | --- | --- |
| 当前实现依据 | 0001、0002、0003、0004、0007、0009、0010、0012、0014、0015、0016 | 当前代码中仍能找到对应实现或测试；涉及 UI/手势的行为仍需设备验收。 |
| 已取代 | 0006、0011 | 文件正文已分别标记由 0008、0012 取代。 |
| 需要更新 | 0005 | ADR 写明固定白色气泡；当前 `MessageBubbleHost` 使用 `MaterialTheme.colorScheme.inverseSurface`（见 `app/src/main/java/com/musicapp/player/core/designsystem/snackbar/MessageBubbleHost.kt:70-74`）。 |
| 需要更新 | 0008 | ADR 要求列表不可滚动时隐藏索引并提供滚动条模式；当前 `RightGutterOverlay`/调用方尚未按 `canScrollForward/canScrollBackward` 实现该条件（见 `app/src/main/java/com/musicapp/player/core/designsystem/component/RightGutterOverlay.kt:63-102`）。 |
| 需要更新 | 0013 | ADR 写明 `1200 ms` 启动门控；当前 `MainActivity` 使用 `3000 ms` 超时（见 `app/src/main/java/com/musicapp/player/MainActivity.kt:69-72`）。 |

“需要更新”表示文档与实现存在可定位差异，不代表代码已经判定为缺陷。产品行为确认前保留原始决策；确认后在对应 ADR 中记录新决定和迁移影响。
