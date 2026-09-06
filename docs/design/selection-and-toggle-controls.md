# 选项与开关组件开发规范 (Selection & Toggle Controls Specification)

本规范定义了 MusicApp 中所有单选项（Radio）、开关项（Switch）、复选卡片（Checkbox）及各类配置项的开发规则与质量门禁，确保交互反馈一致、符合无障碍标准，并具备完备的测试支持。

---

## 1. 核心约束与开发规则

### 规则 1：整行/整卡片全热区交互与最小尺寸
- **整行/整卡片可点**：禁止仅内部的 `RadioButton` 或 `Switch` 单独响应点击。最外层容器（`Row` 或 `Surface`/`Card`）必须整体响应点击或切换。
- **语义修饰符绑定**：
  - 单选列表项：必须在外层容器使用 `Modifier.selectable(selected = ..., role = Role.RadioButton, onClick = ...)`。
  - 开关卡片/开关行：必须在外层容器使用 `Modifier.toggleable(value = ..., role = Role.Switch, onValueChange = ...)`。
  - 复选列表项：必须在外层容器使用 `Modifier.toggleable(value = ..., role = Role.Checkbox, onValueChange = ...)` 或 `triStateToggleable`。
- **触控热区尺寸**：整项高度必须满足 `Modifier.heightIn(min = MusicTheme.dimensions.minimumTouchTarget)`（至少 48dp）。

### 规则 2：水波纹按压反馈与圆角裁剪 (Ripple & Clip)
- **带圆角容器必须先行 Clip**：在具有圆角背景或 `shape` 的卡片/容器（如 `Surface`、`Card`、自定义背景项）上应用 `toggleable`/`selectable`/`clickable` 时，必须在交互修饰符**之前**显式调用 `.clip(shape)`。
  ```kotlin
  Modifier
      .fillMaxWidth()
      .clip(MusicTheme.shapes.large) // 先裁剪圆角，防止水波纹和按下高亮溢出成矩形
      .toggleable(
          value = checked,
          role = Role.Switch,
          onValueChange = onCheckedChange,
      )
  ```

### 规则 3：无障碍语义树合并与子组件清理 (Accessibility & Semantics)
- **合并外层语义**：外层容器必须添加 `.semantics(mergeDescendants = true) {}`，将标题文本、辅助说明与选择状态合并为一个单一无障碍节点。
- **子组件点击回调设为空**：内部原生组件的点击回调必须传入 `null`（例如 `RadioButton(..., onClick = null)`、`Switch(..., onCheckedChange = null)`、`Checkbox(..., onCheckedChange = null)`）。
- **彻底清除子组件冗余语义**：内部原生组件必须附带 `Modifier.clearAndSetSemantics {}`，彻底清除系统组件默认暴露给无障碍树的交互节点，避免 TalkBack 读屏出现“外层读一遍、内部控件又聚焦读一遍”的双重焦点和重复朗读缺陷。

### 规则 4：单向数据流与可见性约束
- **无状态声明**：选项与开关组件必须设计为无状态（Stateless Composable），仅通过参数接收当前值（`selected`/`checked`）和变更回调（`onSelect`/`onCheckedChange`）。
- **组件可见性为 `internal`**：纯 UI 选项组件禁止声明为 `private`，必须声明为 `internal`，以便在 `app/src/androidTest` 中编写隔离的 Composable 仪器测试。

### 规则 5：双语资源与设计令牌
- 选项与卡片的所有标题、副标题文本必须通过 `stringResource(...)` 引用双语字符串资源，严禁硬编码。
- 间距（`dimensions.space*`）、圆角（`shapes.*`）、字号与排版（`typography.*`）以及颜色（`colors.*` / `aeroCardContainerColor`）必须统一使用 `MusicTheme` 设计令牌。

---

## 2. 标准代码模板

### 2.1 单选组模板 (Single-Choice Group)

```kotlin
@Composable
internal fun <T> ChoiceGroup(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MusicTheme.typography.titleMedium,
        color = MusicTheme.colors.onSurface,
        modifier = Modifier.padding(bottom = MusicTheme.dimensions.spaceSmall),
    )
    values.forEach { value ->
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = MusicTheme.dimensions.minimumTouchTarget)
                .selectable(
                    selected = value == selected,
                    role = Role.RadioButton,
                    onClick = { onSelect(value) },
                )
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = value == selected,
                onClick = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = label(value),
                style = MusicTheme.typography.bodyLarge,
                color = MusicTheme.colors.onSurface,
            )
        }
    }
}
```

### 2.2 开关卡片模板 (Switch Card)

```kotlin
@Composable
internal fun SwitchCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MusicTheme.shapes.large)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {},
        shape = MusicTheme.shapes.large,
        color = MusicTheme.aeroCardContainerColor,
        contentColor = MusicTheme.colors.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .padding(
                    horizontal = dimensions.contentHorizontalPadding,
                    vertical = dimensions.spaceMedium,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
```

---

## 3. 自动化测试要求 (Testing Verification)

凡新增或修改选项/开关类组件，必须在 `app/src/androidTest` 编写对应的 Compose UI 仪器测试，且必须包含以下两类断言：

1. **全热区点击与状态回调测试**：
   - 分别测试点击组件最左侧区域（如文本）与最右侧区域（如空白或控件区），验证单次点击均能正确触发回调并更新状态。
2. **无障碍未合并树唯一节点测试**：
   - 使用 `composeTestRule.onAllNodes(isSelectable() / isToggleable(), useUnmergedTree = true).assertCountEquals(期望数量)`，验证语义树未被内部子控件污染。

### 测试用例模板：

```kotlin
@RunWith(AndroidJUnit4::class)
class SwitchCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingTitleAndSwitchAreaTogglesOncePerTap() {
        val title = "Sample Switch"
        val changes = mutableListOf<Boolean>()
        var checked by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                SwitchCard(
                    title = title,
                    checked = checked,
                    onCheckedChange = { value ->
                        changes += value
                        checked = value
                    },
                )
            }
        }

        val card = composeTestRule.onNode(isToggleable() and hasText(title))
        card.assertIsOff()
        // 点击左侧文本区域
        card.performTouchInput { click(Offset(8f, height / 2f)) }
        card.assertIsOn()
        assertEquals(listOf(true), changes)

        // 点击右侧控件区域
        card.performTouchInput { click(Offset((width - 8).toFloat(), height / 2f)) }
        card.assertIsOff()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun switchCardExposesOneSwitchSemanticsNode() {
        composeTestRule.setContent {
            MaterialTheme {
                SwitchCard(title = "Sample", checked = false, onCheckedChange = {})
            }
        }

        // 确保未合并语义树中仅存在 1 个可切换节点
        composeTestRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
```

---

## 4. 常见反模式清单 (Anti-Patterns)

| 反模式 | 缺陷表现 | 正确做法 |
| :--- | :--- | :--- |
| **仅子组件可点** | 用户点击文本或空白区域无响应，触控体验差 | 外层容器使用 `selectable` / `toggleable`，子组件 `onClick = null` |
| **未调用 `.clip()` 即挂载交互修饰符** | 卡片按压水波纹（Ripple）溢出圆角变成直角 | 在 `toggleable` / `selectable` 前先调用 `.clip(shape)` |
| **子组件保留 `onClick`/`onCheckedChange`** | 同一行有两个可点击目标，点击可能冲突或出现双重点击动画 | 子组件事件回调传入 `null`，统一由外层容器分发 |
| **未添加 `clearAndSetSemantics`** | TalkBack 读屏聚焦外层后，又聚焦子组件单独朗读，形成无障碍噪音 | 子组件必须添加 `Modifier.clearAndSetSemantics {}` |
| **组件声明为 `private`** | 无法直接在 `androidTest` 中进行独立的 UI 仪器测试 | 组件统一声明为 `internal` |
| **列表排序随旧 Key 漂移或粗暴瞬移** | 切换排序时视口跟随旧数据项剧烈跳跃，或无脑归零打断用户浏览与动画 | 统一使用 `listState.LockScrollOnChange(state.sort)` 锁定视口索引，并为列表项添加 `Modifier.animateItem()` |

---

## 5. 列表排序视口位置锁定与重排动效规范 (List Sort Viewport Lock & Item Animation Specification)

### 5.1 核心约束与设计意图
- **视口索引锁定（阻断 Key 追随漂移）**：Compose 的 `LazyList` / `LazyGrid` 默认会在数据集变更时追踪首个可见项的 Key。当列表按相反顺序重排时，原首项被移到末尾，会导致视口发生数百项的千里跳跃。必须通过 `LockScrollOnChange` 阻断该漂移，强制将视口锁定在变更前的项索引（如在最顶部修改排序则牢牢留在顶部第 0 项，在中间浏览时保留在当前索引）。
- **原地重排动效**：列表与网格项必须配置 Compose 1.7+ 的 `Modifier.animateItem()`。在视口稳定的前提下，让可见区域内的项目平滑播放位移动画，避免生硬跳变。
- **初次加载与返回保护**：进入页面、从详情页返回或发生配置变更（如屏幕旋转）时，不得误触发重置或漂移，必须保留既有的滚动记忆。

### 5.2 标准代码实现
严禁在各 Feature 业务 Composable 中自行编写重复的 `remember` 与 `LaunchedEffect` 样板代码，必须统一使用 `com.musicapp.player.core.designsystem.component` 提供的扩展与修饰符：

```kotlin
// LazyColumn / LazyRow 列表
val listState = rememberLazyListState()
listState.LockScrollOnChange(state.sort)

LazyColumn(state = listState) {
    items(tracks, key = { it.stableKey }) { track ->
        TrackRow(
            track = track,
            modifier = Modifier.animateItem(),
            // ...
        )
    }
}

// LazyVerticalGrid 网格
val gridState = rememberLazyGridState()
gridState.LockScrollOnChange(state.sort)

LazyVerticalGrid(state = gridState) {
    items(albums, key = { it.key }) { album ->
        AlbumCard(
            album = album,
            modifier = Modifier.animateItem(),
            // ...
        )
    }
}
```
