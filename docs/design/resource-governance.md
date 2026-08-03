# MusicApp 资源治理

本项目只接收用途明确、来源可追溯、许可允许再分发且能够遵守现有设计令牌与无障碍约束的外部视觉资源。外部组件库可以作为视觉参考，但不得因此绕过 `MusicTheme`、Material 3、自适应布局或首版功能边界。

## 准入规则

- 单色操作与状态图标优先保存为 Android `VectorDrawable`，位图只用于无法合理矢量化的封面、插画或截图。
- 文件名按语义分组：通用操作使用 `ic_common_*`，状态使用 `ic_status_*`，导航专用资源使用 `ic_navigation_*` 或 `ic_sidebar_*`。
- 矢量资源使用中性路径颜色，由 Compose `Icon` 或调用方按 `MusicTheme.colors` 着色；可交互图标必须使用双语字符串提供语义说明，纯装饰图标使用 `contentDescription = null`。
- 每个外部资源必须记录上游仓库、具体源文件、许可证、版权归属与本项目修改；使用后同步更新离线开源许可。
- 不接收来源不明的壁纸、品牌图标、演示截图或仅有单密度版本的通用 PNG。SVG 必须先转换为 Android `VectorDrawable`，不得直接放入 `res/drawable`。
- 未列入已接受需求或未来计划的资源不进入应用模块，避免形成无用途的素材仓库。

## SaltUI 已批准资源

来源仓库：`https://github.com/Moriafly/SaltUI.git`

审查基线：`c2888ce11de992b277a355fa47a30013b360aede`

许可证：Apache License 2.0

版权：Moriafly；三个 `ImageVector` 源文件标注 Copyright (C) 2025 Moriafly

| MusicApp 资源 | SaltUI 源文件 | 计划用途 | 修改 |
|---|---|---|---|
| `ic_common_dropdown.xml` | `ui2/src/commonMain/composeResources/drawable/ic_arrow_drop_down.xml` | 设置选择项与展开控件 | 重命名并补充来源说明 |
| `ic_common_chevron_right.xml` | `ui2/src/commonMain/kotlin/com/moriafly/salt/ui/icons/ChevronRight.kt` | 普通信息列表的进入指示 | 从 `ImageVector` 转为 `VectorDrawable` 并重命名 |
| `ic_status_check.xml` | `ui2/src/commonMain/kotlin/com/moriafly/salt/ui/icons/Check.kt` | 已选择、已完成状态 | 从 `ImageVector` 转为 `VectorDrawable` 并重命名 |
| `ic_status_success.xml` | `ui2/src/commonMain/kotlin/com/moriafly/salt/ui/icons/Success.kt` | 扫描或操作成功状态 | 从 `ImageVector` 转为 `VectorDrawable` 并重命名 |

这四个资源在未来页面风格计划实施前由各自 XML 的 `tools:ignore="UnusedResources"` 做局部抑制；接入实际页面后必须移除对应抑制。

## 明确排除

- SaltUI 的二维码、认证、密码可见性图标当前没有 MusicApp 业务用途。
- SaltUI 的壁纸、iPhone 背景、Compose 标识、启动图标和演示截图不进入项目。
- SaltUI 的 `SideBar`、`BottomSheetScaffold`、主题系统、Lazy/Pager 副本和 Haze 依赖不直接引入；其尺寸、版本或交互契约与 MusicApp 当前实现不一致。
- 文件头声明 LGPL 的 SaltUI 组件源码不复制到本项目。
