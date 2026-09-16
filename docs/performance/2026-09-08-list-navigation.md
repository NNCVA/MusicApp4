# 歌曲列表与侧边栏性能：第一批（2026-09-08）

代码与机制回归已完成；**机制优化已验证，体验收益尚未确认**。前后各 40 组采样及额外 20 组交错复测已完成，未形成稳定的整体帧耗时改善证据，暂停首次滑动仍有小幅退化疑点。JVM/Lint/APK 通过，Runtime 两项基线既有失败使完整验收尚未通过。测试安装重建私有数据的副作用见下文，不能宣称完全保留了设备应用数据。

## 范围与实现

本轮消除播放进度触发的索引/队列计算，并将文件夹派生数据计算移至后台。保留视觉、弹簧曲线、页面过渡、点击节流、封面加载策略及 Room schema。掉帧指帧未按期限完成；弹簧本身的加减速不属于本轮修复对象。

- `PlayerViewModel`：媒体库去重后在可注入的计算 Dispatcher 上建立 ID 索引；队列投影仅组合队列与索引。进度更新复用队列列表。当前曲目标识先去重，再通过索引查询，仍响应同 ID 曲目的元数据变化。生产构造使用 `Dispatchers.Default`。
- `Navigation`：仅收集 `PlayerShellState`（有效当前曲目 ID、由它派生的播放器可见性）。完整播放器状态由播放器子树收集。
- `FoldersViewModel` / `FolderDetailViewModel`：目录树仅订阅媒体库；目录定位、排序与选择、信息弹窗、批量操作展示分离，昂贵计算在注入的 Dispatcher 上运行。目录树在当前 UI 订阅生命周期内复用；停止订阅超过既有 5 秒超时后重新订阅，会重新构建。未加载时仍保留原空白规则。

工作期间并行的睡眠定时功能提交已包含导航窄订阅和部分播放器缓存/测试。本轮保留这些改动及其他未提交 UI 修改，将播放器主线程缓存替换为后台派生 Flow。因此当前 `git diff` 不再包含整轮导航与测试变更，不能仅凭最终未提交文件列表还原最初基线。

## 环境与可重复性

- 设备：`emulator-5554`，Pixel_10_Pro AVD，Android 17，1280×2856，density 480，约 60 Hz（dumpsys：60.000004）。系统指纹：`google/sdk_gphone16k_arm64/emu64a16k:17/CP31.260623.009/15923651:user/dev-keys`。结论仅代表此模拟器。
- 媒体：现有 29 首歌曲，可滚动；每轮通过“播放全部”重置为 29 项队列，再确认 PLAYING/PAUSED。未添加测试音乐，未删除外部音乐文件。
- 两版均为 Debug、未启用 R8；Java 21 daemon，Gradle 9.5.0，AGP 9.3.0。未在两版之间添加 Compose instrumentation 或改变构建类型。
- 对照使用冻结工作树 `/tmp/musicapp-perf-20260908/checkout`，避免同期功能提交污染变量。优化版在同一快照上仅替换播放器派生链路、导航订阅和文件夹链路；播放器适配保留快照原有构造参数。它不是包含随后睡眠定时提交的当前完整 APK；当前工作树另行运行功能门禁。
- baseline APK SHA-256：`4d3f26c0f9f269e92927061d2ce2fbc8ab73683625116b614f00277003ebe9a0`。
- after APK SHA-256：`54aa8eaafb3300fa8add0f1a28fa8a33b60eb787ae1f19d1f991fee20a5768b0`。

### 采样流程与剔除项

采集脚本 `capture-list-navigation.py` 保留实际坐标、等待、播放状态校验及 Perfetto 配置；坐标来自本次 UI hierarchy。每种播放状态各启动应用五次，每次依次采集：进程首次列表遍历、重复遍历、抽屉单独开收、歌曲→文件夹→歌曲。列表为三个上滑和三个下滑，每次 350 ms；抽屉为两次开收。每个场景 6.5 秒 Perfetto，另采集 gfxinfo framestats。

“首次”指本进程首次列表遍历；启动前有 UI dump，并未清除系统页缓存或磁盘封面缓存，不能当作严格冷封面实验。播放状态校验、启动等待和 UI dump 在计时前进行。Perfetto 与 gfxinfo 的时间窗和统计口径不同，不混合计算。

首次旧脚本因页面未就绪、队列为空而剔除（`rejected-not-ready/`）。之后完成的 `before/` 40 组也不用于最终对照：`connectedDebugAndroidTest` 在该环境中卸载/重装了应用，导致私有数据重建，原计划的优化版采样被就绪断言拦截。重新授权读取音频并扫描后恢复 29 首媒体索引，再用 `before-restored/` 和 `after-restored/` 完整重采。**原私有数据未在 Runtime 测试前备份，无法证明原偏好、歌单和历史完整恢复；外部音乐仍可扫描。**恢复后的私有数据已保存为本机 `restored-app-data.tar`，不纳入仓库。

采样期间不运行 Gradle 或批量轨迹分析。Debug/JIT、模拟器宿主调度、Perfetto/adb、热状态及按先后顺序采样均可能产生波动；未进行随机交错的真机实验。

## 对照结果

每项为五轮中位数，箭头为基线 → 优化版。P95/P99 为应用 `actual_frame_timeline_slice.dur`（ms）；超时比例使用 `on_time_finish = 0`，不以固定 16.67 ms 代替轨迹中的帧期限。CPU 为主线程 `thread_state = Running` 累计时间（ms / 6.5 秒窗口）。分位数取排序后最接近 `(n−1)×p` 的样本。

| 场景 | P95 ms | P99 ms | 超时帧 % | 主线程 CPU ms |
|---|---:|---:|---:|---:|
| 暂停 / 抽屉 | 15.57 → 13.61 | 22.74 → 14.70 | 0.26 → 0.26 | 650 → 625 |
| 暂停 / 首次滑动 | 23.18 → 25.07 | 28.71 → 29.61 | 3.39 → 3.91 | 1685 → 1656 |
| 暂停 / 重复滑动 | 17.69 → 17.28 | 24.44 → 21.86 | 0.26 → 0.00 | 1333 → 1297 |
| 暂停 / 分类切换 | 12.84 → 13.58 | 43.24 → 46.48 | 3.84 → 3.36 | 976 → 968 |
| 播放 / 抽屉 | 13.12 → 9.92 | 14.45 → 16.82 | 0.00 → 0.00 | 781 → 758 |
| 播放 / 首次滑动 | 24.71 → 23.61 | 29.55 → 28.20 | 4.40 → 3.89 | 1900 → 1799 |
| 播放 / 重复滑动 | 18.11 → 17.31 | 22.61 → 23.42 | 0.26 → 0.00 | 1437 → 1401 |
| 播放 / 分类切换 | 13.40 → 11.92 | 28.44 → 27.57 | 3.58 → 3.54 | 1100 → 1059 |

[完整 80 组逐轮统计](2026-09-08-list-navigation-runs.csv)同时保存 gfxinfo P95/P99 及其 jank 比例。当前 29 首的小媒体库不能代表大库收益。

初测存在反向信号：暂停首次滑动 P95 五轮均升高，播放重复滑动 P99 五轮均升高。各组范围仍有重叠，主线程 CPU 中位数均下降，但不能据此直接消除退化疑点，另做交错复测。

### 对疑似退化的交错复测

复测脚本 `recheck-list-navigation.py` 每轮安装两个版本，奇数轮基线在先、偶数轮优化版在先；每版分别重启并采集暂停首次滑动、播放重复滑动，共 20 组。播放重复滑动的前置遍历不加 Perfetto，之后等待 1 秒，两版一致；因此复测独立报告，不与初测合并。

| 场景 | P95 ms | P99 ms | 超时帧 % | 主线程 CPU ms |
|---|---:|---:|---:|---:|
| 暂停 / 首次滑动 | 21.27 → 22.82 | 28.46 → 29.08 | 1.56 → 1.55 | 1617 → 1674 |
| 播放 / 重复滑动 | 15.81 → 16.65 | 20.65 → 20.54 | 0.00 → 0.00 | 1390 → 1387 |

两项疑似退化指标在复测均为 2/5 轮升高，初测的五轮同向升高未复现。暂停首次 P95 范围为 20.70–26.90 → 18.82–37.29 ms；播放重复 P99 为 19.91–22.56 → 18.64–45.79 ms，显示明显波动。暂停首次 P95 中位数仍升高约 1.55 ms，不能据此排除小幅退化，也不宣称显著改善。[完整复测统计](2026-09-08-list-navigation-recheck.csv)保留全部样本，包括极端值。

后续验收需要解决两项既有 Runtime 失败，并在受控真机/更大媒体库确认收益与暂停首次滑动风险；本轮停止扩展性能改动，保留原视觉与封面行为。

### 主线程与封面证据

可见的主线程事件主要是 traversal/draw、postAndWait、animation、AndroidOwner:measureAndLayout、Recomposer:recompose；它们包含嵌套和等待，wall duration 不能相加当作 CPU 时间。例如优化版暂停分类切换第 3 轮，`AndroidOwner:measureAndLayout` 最大 93.59 ms，`Record View#draw()` 最大 102.31 ms；无新增函数级标记，不能把该长片段归因到 FolderTree。

优化后的首次滑动 10 组共记录 19 个名称以 ` GC` 结尾的应用 GC slice，13 个超时帧与 GC 区间重叠或距离不超过 5 ms（暂停 7、播放 6）。重复滑动 10 组只有 1 个 GC，未与超时帧相邻。这里的 GC slice 数不是暂停次数；并发 GC 与长帧相邻也不等于因果。轨迹未提供足够的封面提取、转码和解码专属事件或调用栈，不能确认这些 GC 来自封面，第二批封面改造保持未实施。

## 功能验证

在当前工作树执行（先设置 `JAVA_HOME` 为 `jenv prefix 21`，并加入 PATH）：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
```

- JVM：692 项，0 失败、0 错误、0 跳过；Lint / Debug APK 构建成功。日志 `full-gates.log`。
- 新增回归覆盖：1,000 首媒体库在 20 次进度更新中零重复读取、队列实例复用、导航状态无重复发射；换曲、重排、同 ID 元数据更新、媒体移除后的投影及封面刷新；选择/信息弹窗保留排序列表实例；排序和卷信息变化复用目录节点；媒体更新、隐藏、缺失目录状态。
- Runtime：59 项，57 通过、2 失败，日志 `runtime.log`。`EmptyStateSemanticsTest.emptyStateWithoutActionDisplaysTitleAndDescription` 的文本显示断言，以及 `TrackRowSemanticsTest.selectionBottomBarDisablesActionsWhenEmpty` 的未合并语义树禁用断言失败。
- 原基线 APK 使用相同测试 APK 单独复跑两个测试类，4 项中同样 2 项失败（`baseline-runtime-subset.log`）。可确认失败不由本轮性能生产代码引入，但完整 Runtime 门禁仍未通过，不能写“全部验收通过”。本轮未改动这两个组件或断言。
- **后续修复注记（2026-09-09）**：上述两项测试断言已在 commit `88613dcd` 修复（调整为空态语义整合后的 contentDescription 判定及禁用态节点校验）；当前全量 Runtime 测试已扩充至 80 项并全部通过。

基线失败复核历史命令（注：当前环境测试 Runner 对应 Application ID 为 `com.musicapp4.player.debug.test`）：

```sh
adb -s emulator-5554 install -r /tmp/musicapp-perf-20260908/baseline.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r \
  -e class 'com.musicapp.player.core.designsystem.component.EmptyStateSemanticsTest,com.musicapp.player.core.designsystem.component.TrackRowSemanticsTest' \
  com.musicapp4.player.debug.test/com.musicapp.player.HiltTestRunner
```

对照构建在冻结快照使用 `./gradlew :app:assembleDebug --no-daemon --console=plain`；安装使用 `adb install -r`。采样：

```sh
python3 docs/performance/capture-list-navigation.py before-restored
# 安装同一冻结快照生成的优化 APK 后
python3 docs/performance/capture-list-navigation.py after-restored
python3 docs/performance/analyze-list-navigation.py before-restored
python3 docs/performance/analyze-list-navigation.py after-restored
# 对两项疑似退化的补充复测及分析
python3 docs/performance/recheck-list-navigation.py
python3 docs/performance/analyze-list-navigation.py check-before
python3 docs/performance/analyze-list-navigation.py check-after
```

脚本默认产物目录 `/tmp/musicapp-perf-20260908`，可通过 `MUSICAPP_PERF_ROOT` 指定。分析工具为官方 `https://get.perfetto.dev/trace_processor` 下载入口，脚本要求它位于产物目录。原始轨迹、帧统计、UI dump、播放状态、APK 和构建日志只保存在本机该目录，未提交大型二进制或个人媒体信息。

结束时使用 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 装回当前完整工作树的 APK，歌曲列表仍为 29 首，并将播放暂停。对照版本未作为最终安装版本留下。最终限定 diff 和 Markdown 本地链接检查通过，脚本语法检查通过；没有提交 Git commit。

## 延后问题

- 字母索引高亮独立记录，本轮不处理。
- GC 邻近证据见上文，封面提取、转码、解码归因仍缺专属轨迹。未修改缩略图尺寸、质量、缓存预算或失效规则；证据不足时不能直接进入封面改造。
- 未新增 ADR，未将性能实现写入领域词汇表。
