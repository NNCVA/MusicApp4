# MusicApp 资源治理

本项目只接收用途明确、来源可追溯、许可允许再分发且能够遵守现有设计令牌与无障碍约束的外部视觉资源。外部组件库可以作为视觉参考，但不得因此绕过 `MusicTheme`、Material 3、自适应布局或首版功能边界。

## 准入规则

- 项目已批准的外部通用矢量图标库为 **Google Material Icons Round**（Apache-2.0）与 **SaltUI Icons**（Apache-2.0）；单色操作与状态图标必须遵循 Material Icons Round 的平滑圆角规范（如播放、暂停、上一首、下一首、导航与操作控件禁止使用尖角/直角几何），优先保存为 Android `VectorDrawable`，位图只用于无法合理矢量化的封面、插画或截图。
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

## Material Icons 矢量资源（Round 圆角风格）

上游仓库：`https://github.com/google/material-design-icons.git`

审查基线：`50f0603134ce7b70b2d71b686cc13e8b57ccb74c`

许可证：Apache License 2.0

版权与发布方：Google；官方仓库未提供独立 `NOTICE` 文件。

本项目全面采用 Material Icons Round（圆角）矢量规范替换原有尖角风格，确保全应用在播放控制、通用操作、导航及侧边栏等场景保持一致的平滑圆角视觉风格。

### 播放控制资源
| MusicApp 资源 | Google 源文件 (Round) | 用途与修改 |
|---|---|---|
| `ic_playback_play.xml` | `src/av/play_arrow/materialiconsround/24px.svg` | 播放；转为 `VectorDrawable`，采用圆角三角形路径并设为中性路径色 |
| `ic_playback_play_circle.xml` | `src/av/play_circle_outline/materialiconsround/24px.svg` | 列表全部播放操作；转为 `VectorDrawable`，内三角平滑圆角化 |
| `ic_playback_pause.xml` | `src/av/pause/materialiconsround/24px.svg` | 暂停；转为 `VectorDrawable`，采用圆角矩形竖条路径 |
| `ic_playback_skip_previous.xml` | `src/av/skip_previous/materialiconsround/24px.svg` | 上一首；转为 `VectorDrawable`，竖条与三角形顶点全面圆角化 |
| `ic_playback_skip_next.xml` | `src/av/skip_next/materialiconsround/24px.svg` | 下一首；转为 `VectorDrawable`，竖条与三角形顶点全面圆角化 |
| `ic_playback_repeat.xml` | `src/av/repeat/materialiconsround/24px.svg` | 列表循环；转为 `VectorDrawable`，箭头与拐角平滑圆角化 |
| `ic_playback_repeat_one.xml` | `src/av/repeat_one/materialiconsround/24px.svg` | 单曲循环；转为 `VectorDrawable`，循环环线与数字1圆角化 |
| `ic_playback_shuffle.xml` | `src/av/shuffle/materialiconsround/24px.svg` | 随机播放；转为 `VectorDrawable`，交叉箭头拐角与端点平滑圆角化 |

### 通用与导航资源
| MusicApp 资源 | Google 源文件 (Round) | 用途与修改 |
|---|---|---|
| `ic_common_add.xml` | `src/content/add/materialiconsround/24px.svg` | 添加操作；十字端点圆角化 |
| `ic_common_close.xml` | `src/navigation/close/materialiconsround/24px.svg` | 关闭与清除；交叉叉号端点圆角化 |
| `ic_common_folder_add.xml` | `src/file/create_new_folder/materialiconsround/24px.svg` | 新建文件夹；文件夹折角与十字圆角化 |
| `ic_common_open_in_new.xml` | `src/action/open_in_new/materialiconsround/24px.svg` | 外部打开；外链边框与箭头圆角化 |
| `ic_common_search.xml` | `src/action/search/materialiconsround/24px.svg` | 搜索；放大镜轮廓平滑圆角化 |
| `ic_common_sort_alpha.xml` | `src/av/sort_by_alpha/materialiconsround/24px.svg` | 字母排序；排序箭头与字母端点圆角化 |
| `ic_navigation_back.xml` | `src/navigation/arrow_back/materialiconsround/24px.svg` | 导航返回；返回箭头拐角与端点圆角化 |
| `ic_navigation_menu.xml` | `src/navigation/menu/materialiconsround/24px.svg` | 侧栏菜单；汉堡三横线端点圆角化 |

### 侧边栏导航资源
| MusicApp 资源 | Google 源文件 (Round) | 用途与修改 |
|---|---|---|
| `ic_sidebar_about.xml` | `src/action/info/materialiconsround/24px.svg` | 关于；圆角信息图标 |
| `ic_sidebar_albums.xml` | `src/image/album/materialiconsround/24px.svg` | 专辑；圆角黑胶唱片图标 |
| `ic_sidebar_artists.xml` | `src/social/person/materialiconsround/24px.svg` | 艺术家；圆角人像图标 |
| `ic_sidebar_equalizer.xml` | `src/av/equalizer/materialiconsround/24px.svg` | 均衡器；圆角频谱柱状图 |
| `ic_sidebar_exit.xml` | `src/action/power_settings_new/materialiconsround/24px.svg` | 退出应用；圆角电源图标 |
| `ic_sidebar_folders.xml` | `src/file/folder/materialiconsround/24px.svg` | 文件夹；圆角文件夹图标 |
| `ic_sidebar_history.xml` | `src/action/history/materialiconsround/24px.svg` | 历史记录；圆角历史时钟图标 |
| `ic_sidebar_playlists.xml` | `src/av/queue_music/materialiconsround/24px.svg` | 歌单；圆角歌单音符图标 |
| `ic_sidebar_settings.xml` | `src/action/settings/materialiconsround/24px.svg` | 设置；圆角齿轮图标 |
| `ic_sidebar_theme.xml` | `src/image/brightness_medium/materialiconsround/24px.svg` | 主题；圆角八角星芒图标 |
| `ic_sidebar_tracks.xml` | `src/image/audiotrack/materialiconsround/24px.svg` | 单曲；圆角音符图标 |

这组资源在 Mini、Full Player、队列、列表操作、导航栏和侧边栏中按需引用；图标按钮保持至少 `48 dp` 点击目标，并通过 Compose `Icon` 动态着色。

## MusicApp2 封面占位资源（来源/许可证待确认）

中间来源仓库：`https://github.com/NNCVA/MusicApp2.git`

固定 revision：`f18fbf683d6e2854d14b1e415cc18732a0b09694`

中间来源文件：`app/src/main/res/drawable/ic_playlist_album.xml`

该文件没有文件级来源、作者或许可证声明。许可证状态：来源/许可证待确认。MusicApp2 仓库内的 `META-INF/LICENSE` 不归因于该文件，也不作为该文件的许可证证据。

本项目用途：统一专辑/歌曲/艺术家/播放器封面降级占位。

修改：迁移到 `app/src/main/res/drawable/ic_playlist_album.xml`；path 数据未改。

## 项目作者自制/用户提供资源（MusicApp2 中间存放证据）

中间存放仓库：`https://github.com/NNCVA/MusicApp2.git`

固定 revision：`f18fbf683d6e2854d14b1e415cc18732a0b09694`

MusicApp2 仅作为本次资源的中间存放仓库及固定 revision 证据，不作为第三方作者、上游来源或许可证授予方。

作者/提供方：本次迁移的两个资源基本由项目作者本人制作并由用户提供。中间来源文件未附独立的文件级来源、作者或许可证声明；根据用户确认，资源归类为项目作者自制/用户提供资源。

许可证状态：未发现独立第三方许可证；本项目获得用户提供的使用授权。`MusicApp2/META-INF/LICENSE` 不归因于这两个资源，也不作为它们的许可证证据。

| MusicApp 资源 | MusicApp2 中间来源文件 | 资源核验 | 本项目修改 |
|---|---|---|---|
| `app/src/main/res/drawable/ic_sidebar_scan.xml` | `app/src/main/res/drawable/ic_scan.xml` | 项目作者自制/用户提供；未发现独立第三方许可证 | scan path 迁移到 `ic_sidebar_scan.xml` |
| `app/src/main/res/mipmap/music2.png` | `app/src/main/res/mipmap/music2.png` | 项目作者自制/用户提供；512×512 RGBA；SHA-256 `BC894A878D68E0CEE2DE0CD3FF770B0E346671502227DFB7F798401FD953220E`；未发现独立第三方许可证 | PNG 原始二进制复制；Manifest 改用 `@mipmap/music2` |

## MusicApp 自有状态资源

来源：MusicApp 项目内原创；无第三方上游或中间来源。

许可证：随 MusicApp 项目代码发布；不包含第三方版权材料。

| MusicApp 资源 | 用途 | 修改 |
|---|---|---|
| `ic_status_empty_playlist.xml` | 歌单页面无歌单时的居中空态插画 | 使用三个中性几何面构成打开的空盒图形，并由 `MusicTheme` 着色 |

## MusicApp 自有界面资源

来源：MusicApp 项目内原创；无第三方上游、无中间仓库与固定 revision。

许可证：随 MusicApp 项目代码发布；不包含第三方版权材料。

| MusicApp 资源 | 用途 | 修改 |
|---|---|---|
| `ic_common_storage.xml` | 文件夹一级页存储卷卡片的存储卷图标 | 以中性描边和路径绘制存储卷轮廓，保存为 Android `VectorDrawable` 并由 `MusicTheme` 着色 |
| `ic_common_folder.xml` | 文件夹一级快捷项与纯中间目录卡片的文件夹图标 | 以中性描边绘制文件夹轮廓，保存为 Android `VectorDrawable` 并由 `MusicTheme` 着色 |

## 明确排除

- SaltUI 的二维码、认证、密码可见性图标当前没有 MusicApp 业务用途。
- SaltUI 的壁纸、iPhone 背景、Compose 标识、启动图标和演示截图不进入项目。
- SaltUI 的 `SideBar`、`BottomSheetScaffold`、主题系统、Lazy/Pager 副本和 Haze 依赖不直接引入；其尺寸、版本或交互契约与 MusicApp 当前实现不一致。
- 文件头声明 LGPL 的 SaltUI 组件源码不复制到本项目。
