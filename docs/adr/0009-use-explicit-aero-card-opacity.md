# 使用 Aero 卡片显式半透明语义与弹出层不透明隔离

> **状态：已接受 (Accepted)**。

## 背景与上下文

在 Aero 动态背景（流体网格、光晕气场）下，页面卡片若直接通过覆盖全局 Material 3 `surfaceContainer*` 颜色的透明度来实现毛玻璃/半透明效果，会导致两个严重问题：
1. **弹出浮层透光混叠**：`DropdownMenu`、`Dialog`、`AlertDialog`、`ModalBottomSheet`、`Snackbar` 等临时表面在弹出时继承半透明背景，导致与底层内容、文本发生严重混叠穿透，对比度下降且视觉杂乱；
2. **缺乏状态调度与降级联动**：在纯色静态模式或系统省电/低电量/后台降级为纯色背景时，半透明卡片如果仍保持固定 alpha，会导致背景泛白或反差不足。

## 决策内容

1. **恢复基础 Material 3 容器不透明度**：
   Material 3 的 `surfaceContainer`、`surfaceContainerHigh` 等基础色彩方案保持 100% 不透明，确保所有标准临时表面（菜单、对话框、底栏、消息气泡）天然具备清晰、不透光的遮罩背景。
2. **引入显式语义 `MusicTheme.aeroCardContainerColor`**：
   仅对满足“页面内持久存在、具备卡片分组视觉、位于 Aero 背景上、明确需要透出背景”的容器赋予半透明背景色语义，不通过组件自动继承。
3. **白名单准入机制**：
   目前显式使用 `aeroCardContainerColor` 的持久卡片白名单为：
   - 侧边栏（`SidebarCard`）
   - 设置页分组卡片（`SettingsCard`）
   - 扫描页各功能卡片（`ScanCard` / `ScanActionCard` 等）
   - 文件夹页存储卷卡片与快捷目录卡片（`FolderVolumeCard` / `FolderShortcutCard` 等）
4. **运行时降级与纯色模式自动回退**：
   `aeroCardContainerColor` 内部绑定 Aero 模式与运行时降级状态。在动态模式下使用 `0.5f` 不透明度；在纯色静态模式或触发低电量/省电降级时，自动回退为完全不透明的表面容器色，无需各个 Screen 手动分支。

## 代价与权衡

- **代价**：新增页面或卡片时不能依赖全局默认透明度，需显式声明 `containerColor = MusicTheme.aeroCardContainerColor`。
- **收益**：从根本上杜绝了弹出浮层透光与文字对比度违规，且在动效降级时具备全局统一的色彩回退保证。
