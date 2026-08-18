# MusicApp 资源治理

本项目只接收用途明确、来源可追溯、许可允许再分发且能够遵守现有设计令牌与无障碍约束的外部视觉资源。外部组件库可以作为视觉参考，但不得因此绕过 `MusicTheme`、Material 3、自适应布局或首版功能边界。

## 准入规则

- 单色操作与状态图标优先保存为 Android `VectorDrawable`，位图只用于无法合理矢量化的封面、插画或截图。
- 文件名按语义分组：通用操作使用 `ic_common_*`，状态使用 `ic_status_*`，导航专用资源使用 `ic_navigation_*` 或 `ic_sidebar_*`，播放器专用资源使用 `ic_playback_*`。
- 矢量资源使用中性路径颜色，由 Compose `Icon` 或调用方按 `MusicTheme.colors` 着色；业务状态到资源 ID 的映射集中定义，不在各个 Composable 中散落 `Icons.*` 或重复的 `when` 映射。
- 可交互图标必须使用双语字符串提供语义说明，纯装饰图标使用 `contentDescription = null`；颜色不能作为播放模式、选中态或错误态的唯一表达。
- 每个外部资源必须记录上游仓库、具体源文件、许可证、版权归属与本项目修改；使用后同步更新离线开源许可。
- 从旧版 MusicApp 仓库恢复资源时还要记录中间仓库及其固定 revision；若图形能追溯到更早的第三方上游，以第三方许可证为准，不能用旧仓库路径替代许可证证据。
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

## Material Icons 播放资源

上游仓库：`https://github.com/google/material-design-icons.git`

审查基线：`50f0603134ce7b70b2d71b686cc13e8b57ccb74c`

许可证：Apache License 2.0

版权与发布方：Google；官方仓库未提供独立 `NOTICE` 文件。

中间来源：`https://github.com/NNCVA/MusicApp2.git`，revision `f18fbf683d6e2854d14b1e415cc18732a0b09694`。MusicApp2 未附独立许可证；下列 path 数据已逐项与 Google 官方 24 px filled SVG 核对一致，因此许可与版权记录采用 Google 上游。

| MusicApp 资源 | Google 源文件 | MusicApp2 中间文件 | 用途与修改 |
|---|---|---|---|
| `ic_playback_play.xml` | `src/av/play_arrow/materialicons/24px.svg` | `app/src/main/res/drawable/ic_play.xml` | 播放；转为 `VectorDrawable`、重命名并改为中性路径色 |
| `ic_playback_pause.xml` | `src/av/pause/materialicons/24px.svg` | `app/src/main/res/drawable/ic_pause.xml` | 暂停；转为 `VectorDrawable`、重命名并改为中性路径色 |
| `ic_playback_skip_previous.xml` | `src/av/skip_previous/materialicons/24px.svg` | `app/src/main/res/drawable/ic_skip_previous.xml` | 上一首；转为 `VectorDrawable`、重命名并改为中性路径色 |
| `ic_playback_skip_next.xml` | `src/av/skip_next/materialicons/24px.svg` | `app/src/main/res/drawable/ic_skip_next.xml` | 下一首；转为 `VectorDrawable`、重命名并改为中性路径色 |
| `ic_playback_repeat.xml` | `src/av/repeat/materialicons/24px.svg` | `app/src/main/res/drawable/ic_repeat.xml` | 列表循环；转为 `VectorDrawable`、重命名并改为中性路径色 |
| `ic_playback_repeat_one.xml` | `src/av/repeat_one/materialicons/24px.svg` | `app/src/main/res/drawable/ic_repeat_one.xml` | 单曲循环；转为 `VectorDrawable`、重命名并改为中性路径色 |
| `ic_playback_shuffle.xml` | `src/av/shuffle/materialicons/24px.svg` | `app/src/main/res/drawable/ic_shuffle.xml` | 随机播放；转为 `VectorDrawable`、重命名并改为中性路径色 |

这组资源只用于 Mini、Full Player 与队列中的实际播放操作；播放模式通过图形和 TalkBack 语义共同表达，所有图标按钮保持至少 `48 dp` 点击目标。

## MusicApp2 封面占位资源（来源/许可证待确认）

中间来源仓库：`https://github.com/NNCVA/MusicApp2.git`

固定 revision：`f18fbf683d6e2854d14b1e415cc18732a0b09694`

中间来源文件：`app/src/main/res/drawable/ic_playlist_album.xml`

该文件没有文件级来源、作者或许可证声明。许可证状态：来源/许可证待确认。MusicApp2 仓库内的 `META-INF/LICENSE` 不归因于该文件，也不作为该文件的许可证证据。

本项目用途：统一专辑/歌曲/艺术家/播放器封面降级占位。

修改：迁移到 `app/src/main/res/drawable/ic_playlist_album.xml`；path 数据未改。

## 明确排除

- SaltUI 的二维码、认证、密码可见性图标当前没有 MusicApp 业务用途。
- SaltUI 的壁纸、iPhone 背景、Compose 标识、启动图标和演示截图不进入项目。
- SaltUI 的 `SideBar`、`BottomSheetScaffold`、主题系统、Lazy/Pager 副本和 Haze 依赖不直接引入；其尺寸、版本或交互契约与 MusicApp 当前实现不一致。
- 文件头声明 LGPL 的 SaltUI 组件源码不复制到本项目。
