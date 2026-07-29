# AGENTS.md

## 重要
- 必须使用中文输出！！！
- 注意，必要时使用 .agents/skills/ 目录下的skills ！！！
- 项目文档全放在 docs/ 目录下！！！
- 多使用子代理并行执行
- 调用所有工具（Tool）时，必须严格检查并填写当前 schema 规定的全部必填字段，杜绝 SchemaError。

## 项目概述
MusicApp 的首版目标是一款基于 Kotlin 与 Jetpack Compose 的现代化 Android 本地音乐播放器。
首版目标包括 7 种主流音频格式、基于 MediaStore 的媒体库同步、Media3/ExoPlayer 与 MediaSession、同步歌词、多主题与 Material You、Aero 动态背景、中英双语、播放列表、播放历史，以及单播放器淡出淡入切歌。

## 当前实施状态
- 当前仓库只有可编译的 Compose/Navigation 3 空白骨架。
- Room、DataStore、Hilt、Media3、媒体库、业务页面和测试基础设施均属于待实现目标，不得将设计文档描述误报为已完成代码。

## 核心功能与需求汇总（26 核心需求）
1. **音频文件扫描器**：通过 MediaStore 同步 7 种音频格式（.mp3, .flac, .wav, .aac, .m4a, .ogg, .opus），支持“扫描全部”和“仅扫描指定目录”两种路径模式，排除规则优先。
2. **曲目列表**：展示所有符合规则的曲目，支持多选、加入播放列表/队列、下一首播放、隐藏曲目，以及按标题/艺术家/专辑/添加时间/时长排序；不物理删除音频文件。
3. **侧边栏路由导航**：快速进入单曲、专辑、艺术家、播放列表、播放历史、文件夹浏览、设置、关于；`<600 dp` 使用半屏模态抽屉，`600–839 dp` 使用 Rail，`≥840 dp` 使用常驻侧栏。
4. **播放控制**：基础播放动作（播放、暂停、上一首、下一首、快进/快退、进度条拖拽控制）。
5. **播放模式**：列表循环、单曲循环、随机播放三种模式互斥，首次启动默认列表循环。单曲循环只影响自然结束，手动上一首/下一首仍切换队列；列表循环在队尾回到队首；随机播放同时持久化原始队列与稳定随机序列，一轮结束后重新生成下一轮并继续播放，且两轮边界不连续重复同一曲目；切换到其他模式时恢复原始顺序并保留当前曲目。
6. **淡入淡出切歌**：使用单个 ExoPlayer 将前曲淡出至静音后切歌，再将后曲淡入；不重叠播放两首曲目。总时长允许用户在 `0–2000 ms` 范围内以 `250 ms` 步进调节，默认 `500 ms`。
7. **播放列表管理**：创建、重命名、删除播放列表，批量添加/移除曲目。
8. **播放列表详情**：查看特定播放列表内的歌曲，并支持一键播放全列表。
9. **播放历史记录**：曲目实际播放达到 `min(30 秒, 曲目时长的 50%)` 后记录；同一曲目更新最后播放时间与累计次数。
10. **同步歌词**：歌词来源优先级为同名外部 `.lrc`、内嵌 SYLT、内嵌 USLT。所有来源均无时间戳时展示原始内嵌文本或 `lyrics_not_found`，并清空三行同步歌词值。
11. **专辑封面提取**：自动提取内嵌封面并提供占位图；全屏播放详情使用圆形封面，其他位置统一使用圆角矩形。
12. **迷你播放器 (Mini Player)**：屏幕底部常驻展示当前播放曲目信息及快捷控制按钮，高度与曲目列表项统一为 `80 dp`。
13. **全屏播放器 (Full Screen Player)**：点击或上滑迷你播放器进入独立全屏路由，返回或下滑关闭，不做跟手连续缩放；包含封面、同步歌词、当前播放队列三个横向页面。
14. **播放队列**：实时查看当前播放队列，支持移除及点击跳转，不提供拖拽排序；普通加入队列时新增曲目追加到原始队列末尾并随机插入当前随机序列的未播放区间，“下一首播放”则插入当前随机项之后并追加到原始队列末尾；移除当前曲目后切到下一首。
15. **曲目/专辑/艺术家/文件夹分类浏览**：按不同维度的分类浏览本地音乐库。
16. **通知栏控制**：系统媒体面板显示上一首、播放/暂停、下一首及曲目信息；通知始终允许划除，划除时停止播放服务并保留持久化恢复点。
17. **MediaSession 系统集成**：与 Android 系统底层 MediaSession 深度绑定，支持锁屏控制、蓝牙耳机按键控制及 Audio Focus 音频焦点控制（来电自动暂停等）。
18. **歌曲信息查看器**：紧凑窗口使用 Bottom Sheet，中等及展开窗口使用最大宽度 `640 dp` 的对话框，展示编码、比特率、采样率、文件大小、路径等，只允许复制路径。
19. **运行时权限管理**：Android 13+ 请求 `READ_MEDIA_AUDIO`，低版本请求 `READ_EXTERNAL_STORAGE`；MediaSession 通知使用系统豁免，当前版本不声明或请求 `POST_NOTIFICATIONS`。
20. **多主题切换**：支持 Android 12+ Material You 动态取色，以及默认蓝、翡翠绿、日落橙、紫罗兰四套浅色/深色预设。
21. **浅色 / 深色模式**：每套主题均完整支持 Light / Dark 模式。
22. **Aero 动态背景**：动态 Canvas 背景视觉特效（流体网格 Fluid Mesh、光晕气场 Glow Aura、纯色静态），支持低电量自动暂停降级保护。
23. **中英双语切换**：界面全字符串本地化，选项为跟随系统、简体中文、English；使用系统应用语言能力并允许 Activity 重建。
24. **扫描器雷达动画**：首次无缓存扫描使用全屏雷达；已有缓存后的同步不阻塞内容。首次与手动扫描完成后显示包含全部曲目的结果对话框，自动同步不弹出。
25. **设置与偏好存储**：使用 Preferences DataStore 管理主题、语言、Aero、淡出淡入时长、路径规则等；“重置应用配置”只恢复设置默认值，不删除播放列表、历史、缓存或物理音频。
26. **关于页面**：展示应用版本号、开发者元数据、开源许可协议及致谢清单。

## 技术栈与架构设计
- **平台基线**：`minSdk 26`，`targetSdk 36`
- **开发语言**：Kotlin 2.x
- **UI 框架**：Jetpack Compose, Material 3
- **导航框架**：Jetpack Navigation 3 (`androidx.navigation3`)
- **媒体播放引擎**：AndroidX Media3 (ExoPlayer + MediaSession + MediaLibraryService)
- **依赖注入**：Hilt
- **状态与架构模式**：单向数据流 (UDF), ViewModel, Kotlin Coroutines, StateFlow
- **数据持久化**：Room 保存媒体库、播放列表、历史、隐藏状态、路径规则与播放快照；DataStore 仅保存用户设置
- **模块结构**：首版保持单一 `:app` Gradle 模块，内部按 `core/*` 与 `feature/*` 包分层

## 编码规范与约定
- 保持严格的响应式数据流，使用 StateFlow/SharedFlow 暴露 ViewModel 状态。
- Composable 只向 ViewModel 提交动作并观察状态；数据访问经 Repository，播放控制经 MediaController。
- Room 是媒体库、播放列表、历史与隐藏状态的应用内事实来源；界面不得直接查询 MediaStore 或 DataStore。
- 只有 MediaLibraryService 可以持有 ExoPlayer/MediaSession；Activity 和 ViewModel 只能通过 MediaController 控制播放。
- 严禁硬编码文本，所有界面字符串必须定义在资源文件中 (`values/strings.xml`, `values-zh-rCN/strings.xml`)。
- 严禁在页面中硬编码圆角、间距和字号；统一通过 `MusicDimensions`、`MusicShapes`、`MusicTypography` 设计令牌读取。
- 遵循 Android Edge-to-Edge 边到边沉浸式设计规范与 Material 3 设计指南。
- 除启动 Activity 与受可信控制器校验的 MediaLibraryService 外，其他组件默认 `exported=false`；PendingIntent 默认不可变。
- CI 固定使用 JDK 17 执行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`，不增加其他门禁。
- 已接受的 CI 策略见 `docs/design/design-review-09-functional-testing-and-ci.md`。

## 架构与设计参考文档 (设计规范提示词)
- 总索引：`docs/design/design-review-index.md`
- 首版实现规格：`docs/design/implementation-spec.md`
- 首版短计划：`docs/plan/implementation-plan.md`
- 首版开发计划：`docs/plan/implementation-wave-plan.md`
- 领域词汇：`docs/CONTEXT.md`
- 架构决策：`docs/adr/`
- `docs/design/design-review-01` 至 `09`、`11` 和 `12` 为已接受的首版实现约束。
