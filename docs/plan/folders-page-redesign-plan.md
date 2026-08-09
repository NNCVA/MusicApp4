# 文件夹页改造方案

状态：实现、主线程审查、JVM 单测、Lint 与 Debug 构建已完成；当前源码未完成 Pixel 8 设备验收（2026-08-10）。

## 1. 参考图与差异

参考图与当前图只用于界面结构对照，图中的卷名、路径、容量、曲目名称和数量不构成测试数据或业务保证。

| 画面 | 截图中可直接观察的事实 | 已确认口径 | 实现推断/待验证 |
| --- | --- | --- | --- |
| 目标一级页 [`docs/pic/folders.png`](../pic/folders.png) | 标题为“文件夹”，顶部有抽屉与搜索操作；上方有两个存储卡片，卡片显示卷名、挂载路径和已用/总容量；下方有 `Music` 文件夹项、曲目数和三点菜单；右侧出现固定 `0/A–Z/#` 索引；底部有 Mini Player。 | 一级页并列显示仅含音乐的存储卷卡片和全部非根、直接含曲目的音乐文件夹快捷项；搜索与固定索引只作用快捷项；卷卡片不参与字母定位；三点菜单只提供“播放全部”。 | 需要把卷元数据和 `musicFolders()` 派生结果合成同一一级状态，并让快捷项保持递归计数/播放集合一致；索引空标签、搜索无结果和 Mini Player 避让需由 UI 树验证。 |
| 目标中间页 [`docs/pic/folders_second.png`](../pic/folders_second.png) | 顶部为返回与卷名；内容区出现 `Music` 文件夹卡片，未显示路径或曲目行。 | 中间页按名称升序展示纯子目录卡片；直接含曲目的节点进入现有文件夹详情，详情继续承载曲目列表与播放入口；目录层级可达任意深度。 | 需保持 `FolderDetailRoute` 的卷名/规范化路径键，区分纯子目录导航与直接含曲目详情，并在截图/UI 树中验证返回栈。 |
| 当前一级页 [`docs/pic_app/folders.png`](../pic_app/folders.png) | 仅看到一个 `external_primary` 行，副文案为“存储卷”和曲目数；标题栏右侧是名称排序；没有存储容量卡片、搜索或右侧索引。 | 目标口径不改变卷与文件夹树身份；改造后排序入口不承担快捷项的搜索/索引职责。 | 当前 UI 把卷根作为普通行，缺少容量信息、快捷项并列、搜索和索引；需要由状态层提供两类入口，避免将卷根重复映射为快捷项。 |
| 当前中间页 [`docs/pic_app/folders_second.png`](../pic_app/folders_second.png) | 标题显示 `external`，右侧有“播放全部、名称↑、标题↑”；下方有“文件夹”分组和 `Music` 行，显示副标题与曲目数。 | 直接含曲目的节点复用现有详情；目标纯中间目录卡片按名称升序且不显示路径。 | 当前页混合目录排序、曲目排序和播放操作；需将中间页目录卡片与详情曲目区分，并将三点入口收敛为“播放全部”。 |

## 2. 已确认的领域与交互口径

- 一级页只列两类入口：有音乐的存储卷卡片，以及每个非根、直接含至少一首当前可见曲目的音乐文件夹快捷项。卷根直接含曲目时仍只计入卷卡片，禁止生成重复快捷项。
- 主存储卷卡片显示本地化“内部存储”，其他存储卷显示平台卷名称；卡片使用真实挂载路径和已用/总容量，容量读取失败时隐藏容量信息，保留卷卡片和可用的导航能力。卷元数据不改变 `FolderId` 的身份。
- 音乐文件夹快捷项可以位于任意深度；同一节点仍可从所属卷沿文件夹树逐级到达。快捷项的递归曲目计数和“播放全部”曲目集合与进入详情后的结果一致。
- 快捷项搜索及固定 `0/A–Z/#` 索引只筛选/定位快捷项名称，不筛选存储卷卡片，也不把路径片段加入索引。重名快捷项不显示文件夹路径，卷与规范化相对路径仍用于内部区分。
- 中间页子目录按名称升序，只呈现纯子目录卡片；当前节点直接含曲目时使用已有文件夹详情，保留详情页的曲目排序、暂时不可用状态和播放入口。一级音乐文件夹快捷项与纯中间目录卡片不显示文件夹路径（包括重名快捷项）；现有文件夹详情内的目录行沿用既有展示约束。三点菜单只触发“播放全部”，不加入重命名、删除或其他目录管理动作。
- 文件夹树只由媒体库当前可见曲目派生；空目录和没有可见曲目的存储卷不生成页面入口。路径按 `relative_path` 规范化，根目录为空字符串，前缀相似的目录按完整路径边界区分。

## 3. 当前代码能力与缺口（CodeGraph/代码核对）

当前工作树通过 `codegraph explore` 检查了 `FolderTree`、`FolderNode`、`FolderVolumeItem`、`FolderVolumeMetadataSource`、`FoldersViewModel`、`FolderDetailViewModel`、`FoldersScreen` 与 `FolderDetailRoute`。以下是实现基线，不代表门禁结果：

- `app/src/main/java/com/musicapp/player/feature/folders/FolderModels.kt` 已按卷构建树，`FolderId` 以卷名和规范化相对路径区分身份，递归曲目按标题稳定排序；当前工作树新增 `isVolumeRoot`、`hasDirectTracks`、`FolderVolumeItem` 和 `FolderTree.musicFolders()`，可表达卷根元数据与全部音乐文件夹快捷项。
- `app/src/main/java/com/musicapp/player/feature/folders/FolderVolumeMetadataSource.kt` 已建立可替换平台边界：通过 `MediaStore.getExternalVolumeNames` 与 `StorageManager` 枚举卷，读取描述、挂载根路径及 `StatFs` 容量；容量字段可为空，挂载/查询异常不会伪造数值。
- `FoldersViewModel` 当前将 `observeTracks()` 与可替换卷元数据流合并，派生有音乐卷卡片、全部音乐文件夹快捷项和固定名称顺序，并提供卷/快捷项递归播放；`FolderDetailViewModel` 标记纯子目录浏览态，保留直接含曲目节点的现有详情和卷元数据。
- `FoldersScreen` 当前工作树已渲染卷卡片、快捷项卡片、搜索、固定索引和三点“播放全部”；`FolderSectionIndex` 负责 `0/A–Z/#` 分组、拼音首字母和含卷卡片偏移的定位。主线程已完成实际 diff、JVM 单测、Lint 与 Debug 构建审查；当前源码的 Pixel 8 设备门禁按用户要求未完成，结果见第 10 节。
- `FolderDetailRoute` 继续使用“卷名 + 规范化相对路径”作为稳定导航键；本次不改变 Navigation 3 路由身份或 Room 表结构。

## 4. 实现切面与本次执行文件所有权

下列是本次执行的临时文件所有权，提交后由主线程检查实际 diff；不等同于长期模块所有权。

- state worker：`FolderModels.kt`、`FoldersViewModel.kt`、`FolderVolumeMetadataSource.kt` 及文件夹状态/树逻辑测试；负责派生模型、元数据流和状态契约。
- UI worker：`FoldersScreen.kt`、`FolderSectionIndex` 相关 UI、对应测试、双语字符串、`ic_common_storage.xml` 和 `ic_common_folder.xml`；负责卡片层级、搜索/固定索引、三点菜单、无障碍语义和资源接入。
- docs worker（本文件）：`docs/plan/folders-page-redesign-plan.md`、`docs/design/implementation-spec.md`、`docs/design/resource-governance.md`、`app/src/main/res/raw/open_source_licenses.txt`；负责已确认口径、当前基线和可审计资源记录。
- 主线程：整合上述切面，核对实际 diff，运行并记录真实门禁及 Pixel 8 设备证据；已同步更新 `docs/CONTEXT.md` 的文件夹浏览领域术语。

## 5. 边界场景

- 同一相对路径出现在多个卷：保留多个独立 `FolderId`，一级快捷项允许同名但不显示路径；卷卡片仍按真实卷身份分开。
- 卷根直接含曲目、子目录也含曲目：根曲目归卷卡片，非根直接含曲目的节点生成一个快捷项；该节点从树进入时显示已有详情，递归计数包含自身与后代。
- 祖先目录只有后代曲目：祖先只作为树中的中间节点，不生成一级快捷项；其子目录卡片按名称升序，直到抵达直接含曲目的节点。
- 任意深度、`Music` 与 `Music Videos` 等前缀相似路径：按规范化路径段和边界匹配，不能因字符串前缀合并曲目或节点。
- 容量读取失败、卷正在挂载或权限暂时不可用：容量字段隐藏或为空，禁止展示过期/猜测数值；媒体库事实与导航身份仍由现有数据源决定。
- 搜索无结果、索引空标签、重名快捷项、系统字体放大、横竖屏或窗口变宽：只影响快捷项列表的可见/定位状态，不改变卷卡片集合、树身份和递归播放集合。
- 快捷项没有可用曲目或播放全部结果为空：沿用既有播放控制的可操作性和反馈规则，不替换现有队列；不物理删除音频或目录。

## 6. 目标验收矩阵

| 验收面 | 目标证据 | 通过条件（执行后填写） |
| --- | --- | --- |
| 一级数据集合 | 状态单测 + Pixel 8 截图/UI 树 | 仅出现有音乐卷卡片与全部非根、直接含曲目的快捷项；根曲目无重复快捷项。 |
| 卷元数据 | 元数据边界测试 + 设备 UI 树 | 主卷显示本地化“内部存储”，其他卷名与挂载路径来自真实平台；已用/总容量可用时显示，读取失败时隐藏容量。 |
| 树与详情 | FolderTree/FolderDetail 测试 + 设备旅程 | 任意深度可达；纯子目录卡片名称升序；直接含曲目节点进入现有详情。 |
| 快捷项交互 | ViewModel/UI 单测 + Pixel 8 UI 树 | 搜索与固定 `0/A–Z/#` 只作用快捷项；三点只播放全部；递归计数与播放集合一致。 |
| 显示与可访问性 | Lint + 双语资源检查 + 截图 | 快捷项不显示路径（包括重名）；图标、按钮和索引有中英语义，遵守设计令牌与触控目标。 |
| 回归门禁 | Gradle 分层门禁、设备报告 | 仅在真实命令输出存在时填写结果；本文件不预填“已通过”或 `31/31`。 |

## 7. 顺序门禁与记录规则

按 [`docs/verification.md`](../verification.md) 的顺序执行，并逐项记录真实结果：

1. `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`
2. `.\gradlew.bat :app:lintDebug --no-daemon --console=plain`
3. `.\gradlew.bat :app:assembleDebug --no-daemon --console=plain`
4. 有设备时执行 `.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain`；无设备只能执行 `.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain` 作为测试 APK 编译检查，不能记为 Runtime 集成测试。
5. 文档/许可证改动完成后执行 `git diff --check -- docs/plan/folders-page-redesign-plan.md docs/design/implementation-spec.md docs/design/resource-governance.md app/src/main/res/raw/open_source_licenses.txt`，并运行本任务约定的限定 `rg` 与相对链接存在性检查。

当前源码没有可报告的 `connectedDebugAndroidTest` 通过结果；历史 `31/31` XML 不作为本次最终源码的设备门禁证据。

## 8. Pixel 8 设备验收清单与历史基线

早期实现基线曾在 Pixel 8 API 34（`emulator-5554`、zh-Hans-CN）上使用现有 32 首 `Music` 媒体库，并临时复制一首曲目到 `Music/CodexFolderQA/Level2/Level3`，采集截图、Android CLI 布局树和 UIAutomator XML。当前最终源码新增列表右侧对齐、索引语义与审查测试修正后，按用户要求未重新执行设备旅程；下列内容保留为复验清单。

- 启动应用并从侧栏进入“文件夹”，采集一级页；断言主卷显示本地化名称、其他卷显示平台名称，并展示真实挂载路径及可用容量，快捷项仅显示文件夹名与递归曲目数，搜索、固定 `0/A–Z/#` 和 Mini Player 语义节点存在。
- 点击卷卡片进入中间页，沿两层以上纯子目录继续下钻；断言子目录卡片按名称升序、页面不暴露路径，返回栈保持卷身份与相对路径。
- 从一级页点击深层音乐文件夹快捷项，采集现有详情；断言直接曲目、递归集合、排序和“播放全部”与快捷项计数一致，三点菜单只出现播放全部。
- 在一级页输入搜索词并点击/拖动 `0/A–Z/#`；断言只有快捷项列表变化，卷卡片与树身份不变，空索引标签不形成筛选结果。
- 多卷同名目录、卷根曲目和前缀相似目录由 `FolderTreeTest` 与 `FoldersViewModelTest` 覆盖；历史设备没有挂载第二个真实存储卷。
- 历史设备截图覆盖紧凑窗口、简体中文和系统当前浅色动态主题；Dark、English、字体放大及可缩放窗口沿用应用级回归范围，不作为当前构建结果。

## 9. 依赖与排除

- 依赖现有 Room `tracks` 事实、MediaStore 卷元数据边界、Navigation 3 `FolderDetailRoute`、共享详情/播放控制和既有设计令牌。
- 不新增 ADR，不改测试分层策略，不改变 26 项首版需求、路由身份、Room 表结构或播放队列语义；不修改用户暂存截图、非本任务资源和 Kotlin/XML 以外的实现文件。

## 10. 本次实测结果

- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`，全量 391 项 JVM 测试通过；文件夹聚焦测试共 17 项通过。
- `:app:lintDebug`：`BUILD SUCCESSFUL`，报告位于 `app/build/reports/lint-results-debug.html`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`；`app-debug.apk` SHA-256 为 `FDE326AB2DCBD085BF1DF9D85738C4BE5013AB43474758476753DF77AFBF4648`。
- `:app:connectedDebugAndroidTest`：按用户要求在执行期间终止，未形成当前源码的设备集成测试结论。
- 运行时 UI 旅程：当前最终源码未重新执行；`app/build/outputs/folder-qa/` 中的既有材料仅保留为早期实现基线证据。
