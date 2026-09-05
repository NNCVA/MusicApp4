package com.musicapp.player.feature.artists

/**
 * 艺术家拆分规则与受保护白名单配置。
 *
 * 集中维护多艺术家合作分隔符与不可拆分的专有名词（如包含斜杠的乐队名）。
 * 规则说明与扩展指引文档参见：docs/design/artist-splitting-rules.md
 */
object ArtistSplittingRules {
    /**
     * 识别多艺术家合作的分隔符正则表达式。
     *
     * 涵盖以下合作标识符：
     * - 中英文斜杠与反斜杠：[/, \]
     * - 中英文顿号、逗号、分号：[、, ,, ;, ，, ；]
     * - 连词符：[&]
     * - 合作伴唱关键字（前后带空白）：feat.、ft.（不区分大小写）
     */
    val DELIMITER_REGEX: Regex = Regex("(?i)[/、\\\\,;，；&]+|\\s+(?:feat\\.|ft\\.)\\s+")

    /**
     * 包含分隔符字符但属于单一整体艺人/乐队的保留白名单。
     *
     * 匹配时不区分大小写。若有新的专有带斜杠乐队名或艺人名，在此列表中追加即可。
     */
    val PROTECTED_WHITELIST: List<String> = listOf(
        "AC/DC",
    )
}
