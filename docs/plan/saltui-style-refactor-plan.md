# SaltUI 风格页面重构计划

状态：未来计划，当前版本不实施。

## 目标

在保留 MusicApp 的 Material 3、`MusicTheme`、三档自适应布局和现有业务行为的前提下，吸收 SaltUI 的圆角分组、主副标题列表行、整行交互与轻量状态反馈风格。

## 范围

- 设置页：将离散控件整理为圆角分组卡片，并统一选择项、开关、滑杆和危险操作的行级结构。
- 关于页：统一版本、开发者、致谢和许可入口的信息层级。
- 普通信息列表：为文件夹、播放列表详情和歌曲信息等非曲目控制行建立统一的主标题、副标题、尾部值与进入指示。
- 状态提示：在扫描完成、设置保存和数据管理结果中使用统一的成功、警告和错误视觉结构。

## 固定边界

- 不替换应用级 Player Sheet，不改变 `80 dp` Mini Player、Full 三页结构、拖动手势或交叉淡入淡出。
- 不替换当前侧边栏，不改变紧凑窗口 `50%` 推移布局、中等窗口 `240 dp` 或展开窗口 `256 dp` 宽度。
- 不引入 `SaltTheme`、SaltUI 整库、Haze alpha 依赖或 Compose Multiplatform 的 Lazy/Pager 实现。
- 所有颜色、圆角、间距与字号继续来自 `MusicTheme`；所有可见文本和语义说明继续使用 English 与简体中文资源。
- 曲目列表项继续保持 `80 dp`，所有可点击目标不小于 `48 dp`，并继续消费系统安全区。

## 实施顺序

1. 在 MusicApp 内建立最小的分组容器、信息行、选择行和状态面板组件，并为设计令牌消费与语义行为添加测试。
2. 先迁移设置页和关于页，确认紧凑、中等、展开窗口及 Light/Dark、动态色均无回归。
3. 再评估普通信息列表与状态提示；曲目列表、播放器和侧边栏保持独立验收，不随本计划迁移。

## 已准备资源

可使用 `ic_common_dropdown`、`ic_common_chevron_right`、`ic_status_check` 与 `ic_status_success`。来源、许可证及修改记录见 [`docs/design/resource-governance.md`](../design/resource-governance.md)。
