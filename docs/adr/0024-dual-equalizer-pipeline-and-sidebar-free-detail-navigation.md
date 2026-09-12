# 双均衡器音效管道叠加与无侧边栏详情流导航契约

> **状态更新（演进变更）**：
> 本设计中的“双音效管道”已精简重构：已彻底移除系统均衡器（及系统音频会话广播机制），仅保留应用内置均衡器管道（Equalizer, BassBoost, Virtualizer）。设置页与界面全面去 Custom 语义，统一为“均衡器”；无侧边栏详情流（Sidebar-Free Detail Flow）契约与播放详情页无缝直达机制继续完全保留并生效。

## 背景与问题

音乐播放器对音质与听感调节有高阶需求：
1. **调音能力的多样性诉求**：部分设备内置了厂商级的杜比音效（Dolby Atmos）、Dirac 或芯片级 DSP，用户习惯在系统面板中进行全局调节；而另一些用户需要播放器应用内具备高自由度的多频段（如 5 段物理频段）增益调节、低音增强（BassBoost）与环绕声场（Virtualizer）。
2. **两类均衡器的冲突与叠加机制**：若采用互斥开关，会导致启用自定义调音时打断系统音效，或者系统音效覆盖自定义调音；用户明确要求支持系统均衡器与自定义均衡器同时启用并叠加生效。
3. **沉浸式调音界面与侧边栏/MiniPlayer 的布局冲突**：多频段均衡器调节通常需要横向或宽屏空间展示频段滑块、预设曲线与效果微调。若在宽屏/平板上依然常驻 300dp 侧边栏和底部 MiniPlayer，会导致可用视口受挤压，且 MiniPlayer 浮动在调音界面底部干扰音效控件手势操作。
4. **播放器详情页与二级页面的导航联动**：用户在全屏播放详情页（PlayerSheet / PlayerLandscapeContent）中点击快捷入口调出均衡器时，需要丝滑过渡，并在完成调音返回后自动重新展开播放详情页。

## 决策

### 1. 双音效管道（Dual Equalizer Pipeline）架构
- **系统均衡器接入**：
  - 维护系统音频会话广播机制（`AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` 与 `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`）。
  - 当“系统均衡器”启用时，通知系统音频引擎绑定当前音频会话；通过 `ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL` 安全唤起系统控制面板。针对无系统音效面板的设备进行意图解析预检与置灰提示。
- **自定义音效管道**：
  - 由 `MusicPlaybackService` 持有底层 `AudioEffectController`，在音频会话建立（`onAudioSessionIdChanged`）时安全初始化 `Equalizer`、`BassBoost` 和 `Virtualizer` 硬件音效组件。
  - 参数由 `EqualizerRepository`（基于 DataStore 响应式持久化）统一驱动，单向流转到底层音效引擎。
  - 手动微调任一物理频段增益时，预设自动切换为“自定义（Custom）”；支持一键平直重置（Flat）。
- **音效叠加规则**：
  - 允许系统均衡器与自定义均衡器同时开启。底层物理管道上，系统音效与应用级 AudioEffect 挂载于同一 `audioSessionId`，硬件层流水线串联叠加，满足多层次听感调校需求。

### 2. 无侧边栏与无底栏详情流（Sidebar-Free Detail Flow）契约
- **AppShell 布局扩展**：
  - `AppShell` 引入 `sidebarVisible: Boolean` 与 `playerSheetVisible: Boolean` 参数。当当前路由属于无侧边栏详情页（如 `CustomEqualizerRoute`、`SystemEqualizerRoute`）时：
    - 中等与展开窗口（桌面/平板/横屏）：隐藏左侧 300dp 侧边栏，内容区自动全宽展开；
    - 紧凑窗口（手机竖屏）：`sidebarVisible = false` 时完全不挂载侧边栏抽屉与手势层，仅渲染主内容区 `ShellContent`，确保用户点击侧边栏快捷入口后直接全屏切页，彻底杜绝抽屉卡死或残留；返回一级页面时抽屉恢复默认关闭态。
- **播放图层受控隐藏与底部 MiniPlayer 静默避让**：
  - `playerSheetVisible = false` 在无侧边栏详情流期间生效，`AppShell` 完全不挂载全屏播放页与常驻 MiniPlayer 容器层；
  - 内容底部内边距 `miniPlayerPadding` 归零，释放全部屏幕纵向空间供 5 频段滑块与调音控件操作，避免手势误触。
- **播放详情页无缝直达与回弹**：
  - 用户从全屏展开态的 `PlayerSheet` 快捷入口进入自定义均衡器时，标记 `restorePlayerOnBack = true` 且设置 `playerExpanded = false`，播放层立即避让，整个视口直接跳转至全屏自定义均衡器；
  - 用户在均衡器页面点击左上角返回或按系统物理返回键时，导航栈退出并检测该标记，自动调用 `playerViewModel.expandPlayer()` 重新展开播放详情页，保证交互闭环与心智连续。

### 3. 设置中心与入口分布
- 在主设置（SettingsScreen）中新增“均衡器”卡片分区，提供“系统均衡器”与“自定义均衡器”两项配置，包含快捷启闭开关以及整行点击进入二级独立调音页。
- 在左侧抽屉/侧边栏快捷操作栏，以及播放详情页辅助工具栏中，提供一键直达“自定义均衡器”的快捷入口。

## 影响与验证
- **架构解耦**：UI 层与 `MusicPlaybackService` 完全解耦，所有状态流转基于 `EqualizerRepository` 与 `EqualizerSettings` 数据流，无多端同步冲突。
- **自动化门禁**：通过单元测试验证仓储读写、预设重置、频段修改反推预设，以及路由序列化编解码；通过 Instrumentation / UI 测试验证无侧边栏响应式布局与无障碍语义标签。
