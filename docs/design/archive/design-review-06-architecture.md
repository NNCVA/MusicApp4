# 架构与数据设计审阅 06

状态：已接受（2026-07-28）

## 已确认

- 首版保持单一 `:app` Gradle 模块，内部按 `core/data/media/designsystem` 与 `feature/*` 包划分依赖方向。
- 使用 Hilt 管理 Application、Room、Repository、MediaLibraryService 与 ViewModel 的依赖和作用域。
- 已实现 Hilt Application/Activity 入口、应用级 `SupervisorJob` 协程作用域，以及可由测试替换的时钟和随机源；业务单例将在所属过程中接入。
- 已实现 Room v1 七张业务表、复合外键与索引、DAO、事务 Repository/Fake、完整播放快照往返及 Schema 导出；过程 10 已通过 `Migration(1, 2)` 增加同步代次和每卷状态，后续版本继续只通过 Migration 演进。
- 已实现 Navigation 3 八个一级栈及版本化二进制/Base64 快照；进程恢复点只保存 Route Key 与参数，不保存页面业务状态。
- 权限状态机通过平台 Gateway 隔离 Activity Result 与系统设置；权限请求历史属于不可备份的系统协调元数据，不属于 DataStore 用户设置。
- MediaStore Cursor、旧 API 绝对路径与平台异常封装在 `data/mediastore`；上层只消费 `core/media` 领域候选、准入结果和相对目录。
- `data/sync` 以一次 Room 事务提交完整扫描：先推进代次并批量 Upsert，再仅对本轮成功查询的已挂载卷清理缺失曲目；失败路径不推进代次、不覆盖元数据且不删除关联。
- `feature/tracks` 以 `TracksViewModel` 合并 Room 曲目与页面展示状态，排序契约保存在 `SavedStateHandle`，Composable 只提交排序、选择和隐藏动作，不持有 MediaStore、扫描同步或物理删除能力；同步状态与结果反馈由应用壳层承载，供后续独立扫描页面使用。
- `data/metadata` 隔离 MediaMetadataRetriever/MediaExtractor，按“曲目标识 + 修改时间”换键，合并同键请求并与后续内嵌歌词共用全局二并发限制；不可读或损坏内容回退为可重试的缺省结果，不改变 Room 曲目事实。
- `media/service` 在导出的 MediaLibraryService 内唯一创建并持有单 ExoPlayer 与 MediaLibrarySession；Service 内的队列协调器是播放模式、原始队列、稳定随机序列与 Player timeline 的单一状态源。手动与自然切歌经服务内的单播放器淡出淡入状态机串行执行，解码失败按当前播放顺序单轮跳过，实际 `isPlaying` 时长达阈值后由 Room 更新历史。快照协调器串行化立即与 5 秒周期写入，Playback Resumption 在恢复前以 Room 媒体库清理队列，不会因进程或设备重启自动播放。应用代码只消费平台无关的 `PlaybackControllerFacade`，Activity 在 `onStart/onStop` 连接并释放可重建的 MediaController；队列与模式命令只授予同包同 UID 的应用控制器，可信系统控制器仅获得浏览与基础播放白名单，其他外部控制器拒绝连接。

## 已确认推荐

1. **状态流契约**：每个页面 ViewModel 只暴露不可变 `StateFlow<UiState>`，界面通过 `onAction` 提交用户动作；Snackbar、导航结果等一次性行为通过 `SharedFlow<UiEffect>` 发出，Composable 不持有业务状态。
2. **数据源归属**：Room 是媒体库、播放列表、历史、隐藏状态与路径规则的应用内唯一事实来源；MediaStore 只作为同步输入，DataStore 只保存用户设置，其中扫描模式属于设置；界面不得直接查询 MediaStore 或 DataStore。
3. **Room 表结构**：建立 `tracks`、`playlists`、`playlist_tracks`、`play_history`、`hidden_tracks`、`path_rules`、`playback_snapshot`；专辑、艺术家、文件夹通过 `tracks` 查询派生，不建立重复缓存表。
4. **事务与迁移**：扫描结果合并、播放列表曲目位置更新和批量操作必须使用 Room 事务；导出 Schema 并为每次版本升级编写 Migration 与迁移测试，发布构建禁止 destructive migration。
5. **播放器边界**：只有 `MediaLibraryService` 持有 ExoPlayer 和 MediaSession；Activity 与 ViewModel 仅通过 `MediaController` 发送命令和观察状态，不注入或暴露原始 Player 实例。

## 第二批已确认推荐

1. **原子同步**：每次完整扫描分配同步代次，先 Upsert 本次发现曲目；只有已挂载卷的查询和合并全部成功后，才移除未出现的缺失曲目及其关联，扫描失败不得删除或覆盖现有数据。
2. **暂时不可用与缺失曲目**：存储卷卸载、权限丢失或查询整体失败时保留曲目及播放列表、历史关联，并在界面显示“不可用”；重新发现相同曲目标识后自动恢复。已挂载卷成功完整同步后仍未发现的曲目直接移除，不保留关联。
3. **数据库索引**：`tracks` 使用“存储卷 + MediaStore ID”复合主键，并为标题、Artist ID、Album ID、添加时间、时长和相对路径建立查询索引；播放列表关系保证“列表 + 曲目”唯一及位置唯一。
4. **封面缓存**：统一 ImageLoader 按“曲目标识 + 修改时间”缓存内嵌封面，配置内存 LRU 与磁盘 LRU；文件变化自动换键失效，列表只请求目标尺寸缩略图，不解码原始大图。
5. **元数据解析并发**：高级元数据与内嵌歌词按需解析，后台并发上限为 2；结果按“曲目标识 + 修改时间”缓存，页面离开或请求对象改变时取消未完成任务。
