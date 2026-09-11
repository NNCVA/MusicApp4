# 架构决策记录

ADR = Architecture Decision Record（架构决策记录）。当前实现以代码、测试和实际验证为准；ADR 用于保留不可逆取舍，并在行为变更时同步更新。以下状态为 2026-09-11 的静态核对，不代替设备或无障碍验收。

## 当前实现依据

0001、0002、0003、0007、0009、0012、0014、0015、0016、0017（导航栈契约）、0018、0019、0020、0021、0022、0023。

## 已合并

- [0010：侧边栏一级入口重置](0010-reset-stack-to-root-on-sidebar-top-level-navigation.md) 已合并至 [0017：导航栈、顶级入口与详情路由契约](0017-contextual-stack-for-detail-navigation.md)。

## 已取代

- [0006：可复用字母分组索引](0006-use-reusable-section-index.md) 已由 [0008](0008-use-unified-right-gutter-overlay-and-fixed-index.md) 取代。
- [0011：统一 Bounce Overscroll](0011-use-unified-bounce-overscroll-for-scrollable-containers.md) 已由 [0012](0012-use-state-aware-compose-overscroll-effect.md) 取代。

## 需要更新

| ADR | 当前差异 | 代码证据 |
| --- | --- | --- |
| [0004](0004-use-grouped-card-navigation.md) | 原文记录中等/展开侧栏为 `240 dp` / `256 dp`；当前两档均为 `300 dp`。 | `app/src/main/java/com/musicapp/player/theme/DesignTokens.kt:21-22` |
| [0005](0005-use-replacing-message-bubble.md) | 原文写固定白色气泡；当前使用 `MaterialTheme.colorScheme.inverseSurface`。 | `app/src/main/java/com/musicapp/player/core/designsystem/snackbar/MessageBubbleHost.kt:70-74` |
| [0008](0008-use-unified-right-gutter-overlay-and-fixed-index.md) | 原文要求按 `canScrollForward/canScrollBackward` 隐藏不可用覆盖层；Folders/Search 调用方已有条件门控，Tracks/Albums/Artists 尚未完整接入，组件本身仍只按 `GutterMode.Hidden` 控制。 | `app/src/main/java/com/musicapp/player/core/designsystem/component/RightGutterOverlay.kt:85-101`；`app/src/main/java/com/musicapp/player/feature/tracks/TracksScreen.kt:279-296` |
| [0013](0013-use-splash-screen-gate-for-seamless-cold-start.md) | 原文记录 `1200 ms` 启动门控；当前超时为 `3000 ms`。 | `app/src/main/java/com/musicapp/player/MainActivity.kt:69-72` |

“需要更新”表示文档与实现存在可定位差异，不代表代码已经判定为缺陷。产品行为确认前保留原始决策；确认后在对应 ADR 中记录新决定和迁移影响。

## 编号规则

每个 ADR 使用唯一四位编号，并与文件名保持一致。原先重复的 `0018-use-viewport-lock-and-item-animations-on-list-sort.md` 已重编号为 [0023](0023-use-viewport-lock-and-item-animations-on-list-sort.md)；后续新增 ADR 从未使用的最大编号之后递增，不复用已取代编号。
