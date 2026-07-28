# 安全与测试设计审阅 09

状态：已接受（2026-07-28）

## 当前基线

- 项目当前没有本地单元测试、设备测试、Compose UI 测试、截图测试或覆盖率依赖。
- 当前仅有启动 Activity，尚未接入 MediaLibraryService、Receiver、Provider、Room、Hilt 或测试 Runner。

## 已确认推荐

1. **组件暴露清单**：Manifest 中只有启动 Activity 与 MediaLibraryService 可导出；不注册自定义 Deep Link、隐式业务 Intent 或导出 Provider，动态 Receiver 默认使用 `RECEIVER_NOT_EXPORTED`。
2. **Intent 与 PendingIntent**：所有内部跳转使用显式 Intent，外部输入逐字段校验且不转发嵌套 Intent；PendingIntent 默认 `FLAG_IMMUTABLE`，不存在明确需求时不创建可变 PendingIntent。
3. **日志隐私**：发布版日志禁止输出完整文件路径、曲目标题、艺术家、歌词、URI 和数据库内容；调试日志使用脱敏标识，安全异常只记录调用包和通用原因。
4. **发布构建**：Release 开启 R8 代码压缩、资源压缩及优化规则，保留 Room、Hilt、Media3 必需元数据；构建后执行 Lint，并校验 Manifest 中权限和导出组件清单。
5. **测试技术栈**：本地与设备测试使用 JUnit4，协程使用 `kotlinx-coroutines-test`，Flow 使用 Turbine，Compose 使用官方测试 API，覆盖率使用 JaCoCo；优先编写 Fake，仅在无法替换平台对象时引入 MockK。
6. **单元测试范围**：覆盖扫描过滤、路径优先级、排序、队列模式、播放历史阈值、淡出淡入状态机、LRC 解析、格式化和 ViewModel；核心业务包行覆盖率至少 `80%`、分支覆盖率至少 `70%`。
7. **Room 测试**：DAO 与事务使用设备端内存数据库验证真实 SQLite 行为；每个 Schema 版本保存导出文件并提供逐版本及跨版本迁移测试，禁止以重建数据库绕过失败迁移。
8. **Compose 行为测试**：使用可替换 Repository 与播放器 Fake 覆盖加载、空态、错误、多选、导航独立返回栈及状态恢复；优先按语义节点查找，复杂节点才添加 `testTag`。
9. **截图测试矩阵**：每个页面覆盖 `400/610/900 dp` 宽度与 `400/500/1000 dp` 高度的九种组合；另覆盖四套预设、动态取色替代色、浅深模式及 `1.5` 字体缩放。
10. **设备端主流程**：使用 Compose Test 与 UI Automator 维护五条端到端旅程：授权与扫描、播放与通知、播放列表、语言主题切换、进程终止与恢复；系统通知和 Edge-to-Edge 截图在模拟器执行。
