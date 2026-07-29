# Wave 4：播放内核与系统媒体

状态：`TODO`

Android journey = 在真实设备或模拟器上，从应用播放入口跨越 MediaController、MediaLibraryService、Player、系统媒体面板与持久化层验证完整行为的端到端旅程。

## 目标

交付可脱离业务 UI 验证的双队列播放引擎、单 ExoPlayer 的 MediaLibraryService、受控 MediaSession、MediaController 门面、淡出淡入、音频焦点、历史、快照、通知与用户触发恢复闭环。

## 进入条件

- Wave 1 的播放模式、播放队列、播放实例和播放快照模型及 Repository 契约已冻结。
- Wave 3 可提供稳定曲目标识、可用 URI 与按需元数据；失效曲目可被明确识别。
- Wave 0 的 Fake Player、可控时钟/随机源、设备 Runner 与 journey 任务可用；API 26、33、36 任一环境缺失时相应门禁保持 `BLOCKED`。

## 执行单元概览

| ID | 状态 | 交付结果 | 依赖 | 最窄验证 |
|---|---|---|---|---|
| W4-01 | `TODO` | 双队列与三模式纯播放引擎 | Wave 1 模型 | 队列引擎本地测试 |
| W4-02 | `TODO` | 基础播放决策与坏文件遍历 | W4-01 | 播放决策本地测试 |
| W4-03 | `TODO` | 单 Player 的 MediaLibraryService/MediaSession | W4-01、W4-02 | API 26/33/36 服务设备测试 |
| W4-04 | `TODO` | 应用侧 MediaController 门面 | W4-03 | Controller 设备测试 |
| W4-05 | `TODO` | 单播放器淡出淡入状态机 | W4-02、W4-03 | Fake Player 本地测试 |
| W4-06 | `TODO` | 音频焦点与私密输出断开收敛 | W4-03、W4-05 | Fake 事件本地测试 + 设备测试 |
| W4-07 | `TODO` | 播放实例计时与历史写入 | W4-03 | 可控时钟本地测试 + Room 设备测试 |
| W4-08 | `TODO` | 快照触发、断点恢复与恢复资格 | W4-01、W4-03、W4-07 | Room/进程恢复设备测试 |
| W4-09 | `TODO` | 通知、锁屏与系统三主操作 | W4-03、W4-08 | API 26/33/36 系统媒体设备测试 |
| W4-10 | `TODO` | 播放通知与进程恢复跨层验收 | W4-04～W4-09 | 两条 Android journey + Wave 门禁任务 |

## W4-01：双队列与三模式纯引擎

状态：`TODO`

### 目标

先于 Service 交付不依赖 Media3 的原始队列、稳定随机序列与列表循环/单曲循环/随机播放状态机。

### 范围与文件边界

- `app/src/main/java/**/media/queue/`：不可变队列状态、命令、Reducer 和随机序列策略。
- `app/src/test/java/**/media/queue/`：纯本地测试与性质场景。
- 本单元不得修改 Service、通知、Compose 或平台控制器文件。

### 实施要点

- 原始队列与随机序列同时持久化；随机一轮稳定，下一轮重新生成且边界不连续重复当前曲目。
- 退出随机保留当前曲目并恢复原始顺序；单曲循环只影响自然结束，手动上一首/下一首仍按当前播放顺序切换。
- 普通加入追加原始队尾并随机插入未播放区；“下一首播放”按选择顺序插入当前随机项后并追加原始队尾。
- 移除当前项同时更新双队列并切下一首；仅余当前项时停止并清空。所有模式均无拖拽排序。

### 验证

- 本地测试覆盖空/单项/多项队列、三模式自然结束、手动前后切换、队首队尾、模式切换和稳定随机轮次。
- 覆盖普通加入、下一首播放、移除已播放/未播放/当前项、重复命令及可控随机源下两轮边界。

### 完成条件

- 所有状态迁移为确定性纯函数，给定相同状态、命令和随机源得到相同结果。
- 队列不变量由测试锁定：当前项存在于活动序列，双队列成员一致，已播放前缀不被插入操作改写。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-02：基础播放决策与错误遍历

状态：`TODO`

### 目标

交付从队列动作到 Player 意图的纯决策层，固定准备、Seek、上一首、下一首和坏文件遍历语义。

### 范围与文件边界

- `app/src/main/java/**/media/playback/engine/`：播放状态、命令、Effect 与错误遍历策略。
- `app/src/test/java/**/media/playback/engine/`：纯本地测试。
- 不持有 ExoPlayer，不实现 Service 生命周期。

### 实施要点

- 点击曲目立即发布当前项与准备态；准备超过 `300 ms` 才发布缓冲状态，失败恢复可操作状态。
- 快进/快退固定 `10 秒` 并钳制到 `0..duration`；上一首不先回曲首。
- 单曲准备或解码失败只标记本次播放不可用并遍历下一项；遍历一轮失败后停止并输出资源化错误键。

### 验证

- 本地测试用虚拟时间覆盖 `299/300/301 ms`、未知/零时长、Seek 边界和准备成功/失败竞态。
- 覆盖不同模式的上一首、下一首以及一项、部分坏项、全坏项的一轮遍历终止。

### 完成条件

- 决策层只输出平台无关 Effect，错误遍历有明确上限且不会死循环。
- UI 可见状态不依赖后续 Compose 实现即可由测试断言。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-03：MediaLibraryService 与 MediaSession

状态：`TODO`

### 目标

交付唯一持有单个 ExoPlayer 与 MediaSession 的 MediaLibraryService，并落实控制器连接边界。

### 范围与文件边界

- `app/src/main/java/**/media/service/`：Service 生命周期、Player/Session 创建释放、连接策略与标准命令映射。
- `app/src/main/AndroidManifest.xml`：Service、前台服务类型与所需权限。
- `app/src/androidTest/java/**/media/service/`：绑定、生命周期与连接策略设备测试。

### 实施要点

- 本应用控制器可连接；受信系统控制器仅获得标准浏览/播放命令；其他控制器拒绝。
- 外部控制器不获得队列编辑、模式切换或自定义业务命令；安全异常日志只含调用包与通用原因。
- 任务划走时播放中继续，未播放允许停止；Service 外不得持有 Player/MediaSession。
- Manifest 只声明 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK` 与受控 Service，不加入 `POST_NOTIFICATIONS`。

### 验证

- API 26、33、36 设备验证创建/销毁、任务划走、前台服务启动限制与单 Player 实例。
- 使用本应用、受信系统和不可信测试控制器验证可连接性与命令集合；无法提供不可信控制器进程时标记 `BLOCKED`。

### 完成条件

- 架构检查证明只有 Service 持有 Player/Session；外部命令边界由设备测试锁定。
- Release Manifest 冒烟确认 Service 导出、前台服务类型和权限符合规格。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-04：MediaController 门面

状态：`TODO`

### 目标

交付供 Activity/ViewModel 使用的唯一播放控制门面，把连接生命周期、命令和会话状态转换为稳定应用契约。

### 范围与文件边界

- `app/src/main/java/**/media/controller/`：门面接口、Media3 实现、连接状态与会话状态映射。
- `app/src/main/java/**/di/`：Controller 门面绑定；不修改 Player 所有权。
- `app/src/test/java/**/media/controller/`：Fake Session/状态映射本地测试。
- `app/src/androidTest/java/**/media/controller/`：真实 Service 连接设备测试。

### 实施要点

- 暴露播放、暂停、上一首、下一首、Seek、建队、加入、下一首播放、移除和模式切换命令，以及只读 `StateFlow`。
- 应用生命周期内复用连接，断连与 Service 重建形成显式状态；ViewModel 不接触 Media3 类型。
- 外部自定义命令与应用内部命令在 Session 侧再次校验，不能仅依赖 UI 隐藏。

### 验证

- 本地测试覆盖连接中、已连接、断开、错误与会话状态去重映射。
- API 26、33、36 设备从测试 ViewModel 经门面发命令，验证 Service 收到且状态回流；不得直接操作 Player 作为替代。

### 完成条件

- 应用层编译依赖仅为门面/domain 类型，所有播放状态都有单一会话来源。
- 连接断开不会泄漏 Activity，重连不会重复订阅或重复发命令。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-05：单播放器淡出淡入

状态：`TODO`

### 目标

交付可由 Fake Player 完整验证的单播放器淡出至静音、切换、再淡入状态机。

### 范围与文件边界

- `app/src/main/java/**/media/transition/`：状态机、音量曲线调度与 Player 端口。
- `app/src/main/java/**/media/service/`：仅接入状态机 Effect。
- `app/src/test/java/**/media/transition/`：Fake Player、虚拟时钟和竞态测试。

### 实施要点

- 总时长 `0–2000 ms`、步进 `250 ms`、默认 `500 ms`，设置值从下一次切歌生效；始终只有一个 Player。
- 自动切歌和手动上一首/下一首触发；暂停、恢复、Seek、焦点变化不触发。
- 连续切歌最后指令生效，静音后只切最后目标；失败时保持静音遍历，成功后淡入，全失败恢复正常音量并停止。
- 暂停、焦点丢失或私密输出断开取消待切目标，恢复正常音量并暂停在已成功切换曲目。

### 验证

- 本地测试覆盖 `0/250/500/2000 ms`、非法设置钳制或拒绝、自动/手动触发与不触发动作。
- 用 Fake Player/虚拟时间覆盖连续命令、淡出中暂停、切换中焦点丢失、目标失败、全轮失败和释放。

### 完成条件

- 任一时刻最多一个媒体项实际发声，状态机结束或取消后音量状态为 1。
- 所有竞态由确定性本地测试覆盖，Service 只执行 Effect 不复制状态机分支。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-06：音频焦点与输出断开

状态：`TODO`

### 目标

交付短暂/永久焦点丢失、duck 与私密输出断开的确定性收敛行为。

### 范围与文件边界

- `app/src/main/java/**/media/audio/`：焦点/输出事件端口、Reducer 与平台注册适配器。
- `app/src/main/java/**/media/service/`：只接入音频事件与播放 Effect。
- `app/src/test/java/**/media/audio/`：Fake 事件本地测试。
- `app/src/androidTest/java/**/media/audio/`：真实输出/焦点设备测试。

### 实施要点

- ExoPlayer 自动管理音频焦点：短暂丢失暂停并在恢复后继续，永久丢失禁止自动恢复，允许系统 duck。
- 私密输出断开立即暂停，重新连接不自动播放；动态 Receiver 使用 `RECEIVER_NOT_EXPORTED`。
- 音频事件与淡出淡入共享单一决策入口，避免焦点回调绕过 W4-05 的取消语义。

### 验证

- 本地测试覆盖短暂丢失/恢复、永久丢失、duck、断开/重连以及与淡出各阶段组合。
- API 26、33、36 设备验证 Audio Focus；私密输出断开需真实或可认证测试通道，环境不具备时保持 `BLOCKED`。

### 完成条件

- 禁止自动恢复场景不会因后续焦点或设备重连事件重新播放。
- Receiver 注册范围、导出属性和释放生命周期通过设备与 Manifest 检查。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-07：播放实例计时与历史

状态：`TODO`

### 目标

交付真实播放时间累计和每实例一次的播放历史写入。

### 范围与文件边界

- `app/src/main/java/**/media/history/`：播放实例状态机、计时端口和历史写入协调器。
- `app/src/test/java/**/media/history/`：可控时钟纯逻辑测试。
- `app/src/androidTest/java/**/media/history/`：真实 Room 写入设备测试。

### 实施要点

- 每次切入曲目创建实例；暂停、恢复、进程恢复延续，切离后再次进入创建新实例。
- 仅 Player 正在播放时累计；暂停、缓冲和 Seek 跳过区间不计，重复片段正常累计。
- 达到 `min(30 秒, 曲目时长 50%)` 后当前实例只增加一次历史次数并更新时间。

### 验证

- 本地测试覆盖短曲/长曲阈值、暂停、缓冲、Seek、重复片段、切离返回和进程恢复。
- 设备端 Room 测试验证同一曲目单行累计、一次实例只递增一次、事务失败可重试且不双计。

### 完成条件

- 计时不依赖 UI 轮询，阈值与去重规则由状态机和数据库测试共同证明。
- 历史 Repository 失败不破坏播放，且后续成功不会产生同实例重复计数。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-08：播放快照与用户触发恢复

状态：`TODO`

### 目标

交付快照写入触发、`5 秒` 位置更新、进程重启断点恢复和恢复资格状态。

### 范围与文件边界

- `app/src/main/java/**/media/snapshot/`：快照组装、节流、恢复与资格策略。
- `app/src/main/java/**/media/service/`：生命周期触发接入。
- `app/src/test/java/**/media/snapshot/`：虚拟时间本地测试。
- `app/src/androidTest/java/**/media/snapshot/`：Room 与进程恢复设备测试。

### 实施要点

- 队列变化、切歌、Seek、暂停、Service 销毁立即写快照；播放中每 `5 秒` 更新位置和实例计时。
- 重开应用恢复界面、双队列、模式、当前项和断点，但不自动播放。
- 系统 Playback Resumption 始终由用户操作触发；通知划除撤销本会话资格，下一次用户主动播放重新允许。

### 验证

- 本地测试覆盖触发去重、`4999/5000 ms`、写入失败重试、划除撤销与主动播放恢复资格。
- API 26 验证应用内断点恢复；API 33、36 额外验证平台 Playback Resumption 能力，平台不支持的 API 不伪造等价通过。

### 完成条件

- 进程重建后快照完整往返且保持暂停，播放实例累计连续。
- 通知划除后的旧会话不能被系统恢复卡片重新播放。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-09：通知、锁屏与系统控制

状态：`TODO`

### 目标

交付系统媒体面板/锁屏的上一首、播放暂停、下一首、元数据与通知划除停止语义。

### 范围与文件边界

- `app/src/main/java/**/media/notification/`：通知元数据、删除动作与不可变 PendingIntent。
- `app/src/main/java/**/media/service/`：系统命令和划除 Effect 接入。
- `app/src/androidTest/java/**/media/notification/`：系统媒体设备测试。
- `app/src/main/res/values*/strings.xml`：通知和无障碍文案。

### 实施要点

- 系统媒体面板只暴露上一首、播放暂停、下一首与曲目信息；耳机/蓝牙按键使用 MediaSession 默认映射。
- 通知始终允许划除；划除时停止、保存快照、清空运行时队列、停止服务并撤销恢复资格。
- PendingIntent 默认不可变，通知内容与 Release 日志不得泄露路径、URI 或数据库内容。

### 验证

- API 26、33、36 验证后台播放、锁屏/通知三主操作、通知划除、任务划走差异与服务停止。
- 使用系统或测试媒体控制器验证耳机标准命令；无法接入蓝牙/耳机设备时对应项保持 `BLOCKED`。

### 完成条件

- 通知状态、Player 状态和 Controller 状态一致，划除后无运行时队列、前台服务或旧恢复资格残留。
- Manifest/安全检查通过且未声明 `POST_NOTIFICATIONS`。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## W4-10：播放系统闭环验收

状态：`TODO`

### 目标

完成“播放与通知”和“进程终止与恢复”两条跨层旅程，并执行 Wave 4 全量门禁。

### 范围与文件边界

- `app/src/androidTest/java/**/journey/PlaybackAndNotificationJourney*`。
- `app/src/androidTest/java/**/journey/ProcessDeathRecoveryJourney*`。
- 本单元不新增业务行为，只补齐旅程夹具与证据采集。

### 实施要点

- 播放旅程从媒体库曲目建队，经 Controller、Service 到后台通知/锁屏控制，再划除并核对停止与快照。
- 恢复旅程覆盖双队列、模式、位置、实例计时的进程终止恢复，确认重开不自动播放且用户主动恢复有效。
- 固定记录设备 API、系统版本、命令、测试媒体和关键状态；环境缺失单列阻塞原因。

### 验证

- API 26、33、36 分别运行两条 Android journey；API 33/36 额外记录 Playback Resumption 结果。
- 运行 Wave 0 冻结的 Wave 4 本地测试、设备测试、覆盖率、架构/Manifest 检查、`:app:assembleDebug`、`:app:assembleRelease` 与 `lintDebug`。

### 完成条件

- 两条旅程在三档 API 均有真实通过证据；缺少设备、媒体控制器或输出设备时状态为 `BLOCKED`。
- Wave 4 新增核心逻辑行覆盖率达到 `80%`、分支覆盖率达到 `70%`。

### 执行记录

开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—

## Wave 4 门禁

状态：`TODO`

- W4-01～W4-10 全部为 `DONE`；队列纯引擎先于 Service 完成并由本地测试冻结。
- 只有 MediaLibraryService 持有单个 ExoPlayer/MediaSession，Activity 与 ViewModel 只依赖 MediaController 门面。
- API 26、33、36 的后台播放、通知划除、可信/不可信控制器、标准耳机命令、进程终止与恢复均有设备证据；环境缺失则 Wave 为 `BLOCKED`。
- 淡出淡入、焦点、私密输出、坏文件、历史与快照竞态均有可控时钟/Fake 的本地测试，Room 行为有真实 SQLite 设备测试。
- 前台服务权限、Service 导出、受信控制器、不可变 PendingIntent、无 `POST_NOTIFICATIONS` 通过 Release Manifest 检查。
- Wave 4 本地测试、设备测试、两条 Android journey、覆盖率、`:app:assembleDebug`、`:app:assembleRelease` 和 `lintDebug` 均有真实通过证据。
