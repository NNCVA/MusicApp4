# 解耦顶栏返回操作与窗口布局策略

## 背景与问题
在多断点自适应（Adaptive / Responsive）架构中，`WindowLayoutPolicy` 依据当前窗口宽度分为：
- `COMPACT_DRAWER`（宽度 < 600dp，手机竖屏或小窗，采用临时抽屉侧边栏）；
- `MEDIUM_SIDEBAR`（宽度 600dp ~ 840dp，手机横屏或中等平板，采用固定中等宽度侧边栏）；
- `EXPANDED_SIDEBAR`（宽度 >= 840dp，大屏平板或桌面，采用常驻宽侧边栏）。

此前通用分类顶栏组件 `CategoryHeader` 将导航图标渲染条件硬编码限制为 `policy == WindowLayoutPolicy.COMPACT_DRAWER && navigationAction != null`。
该逻辑本意是防止在拥有常驻侧边栏的宽屏下冗余显示展开抽屉的汉堡图标（`DRAWER`），但由于将返回图标（`BACK`）与汉堡图标混用同一门控，导致在手机屏幕旋转至横屏、展开折叠屏或平板设备上，进入“关于”、“设置”、“扫描”等次级功能页时，顶栏的返回图标被错误隐去，阻断了用户的直观回退路径。

## 决策
1. **语义与断点解耦（Semantic Decoupling）**：
   - 抽屉汉堡动作（`CategoryNavigationAction.DRAWER`）承载“呼出临时导航抽屉”语义，仅在紧凑无常驻侧边栏模式（`WindowLayoutPolicy.COMPACT_DRAWER`）下渲染；
   - 返回动作（`CategoryNavigationAction.BACK` 及 `onBack != null`）承载“全局线性出栈与返回主页”语义，在所有屏幕方向（横屏/竖屏）、断点及侧边栏形态（`COMPACT_DRAWER`、`MEDIUM_SIDEBAR`、`EXPANDED_SIDEBAR`）下均**无条件常驻展示**。
2. **顶栏规范与显式传参**：
   - 次级功能页（关于、设置、扫描）及各二级详情页在接入 `CategoryHeader` 或 `SearchableTopBar` 时，显式传递 `onBack` 回调，建立明确的回退契约。
   - `CategoryHeader` 内部对 `BACK` 动作与 `DRAWER` 动作分流处理，保障即使通过 `navigationAction = BACK` 接入也能获得全断点一致的返回能力。
3. **交互与导航联动契约不变**：
   - 点击该返回图标时触发标准的 `onBack()`（`Navigator.goBack()`），回退至当前处于活跃状态的媒体库 Home 根页（如单曲列表），侧边栏选中高亮同步切换，与 Android 系统返回键及手势操作完全等价。
