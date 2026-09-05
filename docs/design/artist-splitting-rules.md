# 艺术家分隔符与白名单维护规范

本文档维护当前项目中关于“多艺术家拆分”的硬编码分隔符规则与受保护白名单清单。代码中的唯一事实来源为：
[`app/src/main/java/com/musicapp/player/feature/artists/ArtistSplittingRules.kt`](../../app/src/main/java/com/musicapp/player/feature/artists/ArtistSplittingRules.kt)。

---

## 1. 拆分背景与设计目标

在本地音频元数据中，多位艺术家合作演唱的歌曲往往通过特定标点或连接词拼接在单一字段（`track.artistName`）中（如 `"周杰伦 / 费玉清"`、`"Alpha & Beta"`）。
为了保证：
1. **聚合准确**：艺术家列表（`ArtistsScreen`）、字母/拼音索引栏、艺术家详情页（`ArtistDetailScreen`）以及专辑详情页（`AlbumDetailScreen`）的参与创作列表中，每位参与艺术家均作为独立条目呈现与索引；
2. **专有名词不被误伤**：对于本身包含斜杠等字符的经典乐队/艺人（如 `"AC/DC"`），通过白名单保护避免被误拆分为 `"AC"` 和 `"DC"`；
3. **单曲原始元数据不失真**：单曲条目（`TrackRow`）与播放界面（`PlayerSheet`）等单曲视图依然保留完整原始字符串，不破坏原始音轨信息。

---

## 2. 硬编码分隔符规则清单

代码常量：`ArtistSplittingRules.DELIMITER_REGEX`

正则表达式定义：
```regex
(?i)[/、\\,;，；&]+|\s+(?:feat\.|ft\.)\s+
```

### 覆盖的分隔符清单

| 类别 | 包含字符 / 标记 | 说明与匹配示例 |
| :--- | :--- | :--- |
| **斜杠与反斜杠** | `/`、`\` | 中英文斜杠，如 `"周杰伦 / 费玉清"`、`"ArtistA\ArtistB"` |
| **中文标点** | `、`、`，`、`；` | 常见中文顿号、全角逗号、全角分号，如 `"陶喆、王力宏"` |
| **英文标点** | `,`、`;` | 半角逗号、半角分号，如 `"Alpha, Beta; Gamma"` |
| **连词符** | `&` | 常见合唱连接符，如 `"Queen & David Bowie"` |
| **合作连接词** | `feat.`、`ft.` | 不区分大小写，且前后需有空白字符（避免误伤单词），如 `"Jay feat. Landy"` |

### 拆分后规范化流程
1. **去除空白**：拆分后的每一项均通过 `trim()` 去除首尾空白字符；
2. **过滤空值**：过滤空字符串；
3. **大小写不敏感去重**：同一首歌曲中重复出现的相同歌手（忽略大小写）仅保留首个；
4. **保序展示**：保留合作歌手在歌曲标签中首次出现的先后次序。

---

## 3. 受保护白名单清单

代码常量：`ArtistSplittingRules.PROTECTED_WHITELIST`

白名单内的艺人/乐队名在拆分前会受到安全占位保护，无论独唱还是在合作曲目中均不会被误拆分。

### 当前维护清单

| 序号 | 艺人/乐队标识 | 匹配规则 | 保护理由 |
| :--- | :--- | :--- | :--- |
| 1 | `AC/DC` | 精确不区分大小写（`ac/dc`） | 经典摇滚乐队名自带正斜杠，防止被误切分为 `AC` 与 `DC` |

### 保护实现机制（占位符机制）
1. 在正则拆分前，扫描输入字符串中的白名单项（前后受非字母数字字符或行首行尾边界保护）；
2. 命中项临时替换为安全的字母数字占位符（如 `__PROTECTED_0__`）；
3. 执行多合作分隔符正则拆分；
4. 将各项还原回原始艺人名称。

---

## 4. 白名单与规则扩展维护指南

当需要支持新的特殊乐队或调整分隔符时，请遵循以下步骤：

1. **修改配置文件**：
   在 [`ArtistSplittingRules.kt`](../../app/src/main/java/com/musicapp/player/feature/artists/ArtistSplittingRules.kt) 中的 `PROTECTED_WHITELIST` 追加新条目，或更新 `DELIMITER_REGEX`；
2. **同步更新本文档**：
   在第 2 节或第 3 节的清单表格中补充该变更及其背景原因；
3. **补充自动化单元测试**：
   在 [`ArtistGroupingTest.kt`](../../app/src/test/java/com/musicapp/player/feature/artists/ArtistGroupingTest.kt) 中补充针对新规则的测试用例（覆盖独立曲目、合作曲目以及详情页匹配）；
4. **运行门禁验证**：
   执行 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain` 确保全量单测通过且无回退。
