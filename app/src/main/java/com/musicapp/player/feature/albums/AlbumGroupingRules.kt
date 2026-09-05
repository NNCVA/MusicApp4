package com.musicapp.player.feature.albums

import com.musicapp.player.core.domain.model.AlbumId

/**
 * 专辑分组聚合规则与硬编码配置。
 *
 * 维护专辑版本修饰词提取、未知专辑归档、群星合辑判定与音轨展示格式化等业务规则。
 * 详细设计与维护指南参见：docs/design/album-grouping-rules.md
 */
object AlbumGroupingRules {
    /**
     * 用于提取专辑标题中版本修饰词的正则表达式（不区分大小写）。
     *
     * 匹配版本词汇如：live、remix、deluxe、acoustic、instrumental、bonus、edition、version、remaster、remastered。
     * 在同名专辑聚类时，版本关键词集合不一致的专辑视为不同版本，保持物理独立。
     */
    val VERSION_KEYWORD_REGEX: Regex = Regex(
        "(?i)\\b(live|remix|deluxe|acoustic|instrumental|bonus|edition|version|remaster|remastered)\\b"
    )

    /**
     * 未知专辑的保留哨兵字符串。
     */
    const val UNKNOWN_ALBUM_SENTINEL: String = "<unknown_album>"

    /**
     * 群星合辑（Various Artists）的保留哨兵字符串。
     */
    const val VARIOUS_ARTISTS_SENTINEL: String = "<various_artists>"

    /**
     * 未知专辑的虚拟 AlbumId。
     */
    val UNKNOWN_ALBUM_ID: AlbumId = AlbumId(volumeName = "virtual", mediaStoreId = Long.MAX_VALUE)

    /**
     * 专辑内无音轨号或冲突曲目的占位符号（EN DASH 字符）。
     */
    const val TRACK_NO_NUMBER_PLACEHOLDER: String = "–"

    /**
     * 判定专辑是否属于群星合辑（Various Artists）的主创作者占比门限因子（50%）。
     *
     * 当主创作者曲目数未超过曲目总数的 50%（maxCount * 2 <= totalTracks），且并非所有曲目均有该创作者署名时，
     * 专辑艺术家归类为群星。
     */
    const val PRIMARY_ARTIST_MIN_RATIO: Double = 0.5
}
