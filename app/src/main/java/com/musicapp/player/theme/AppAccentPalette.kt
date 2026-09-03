package com.musicapp.player.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用级语义操作色调色板。
 *
 * 这些颜色用于侧边栏图标、下拉菜单操作图标等 UI 元素，
 * 表达操作语义（播放、添加、删除等），独立于 Material ColorScheme preset。
 *
 * 调用方通过 [LocalAppAccentPalette] 或 [MusicTheme.accentPalette] 获取当前实例，
 * 不应在组件内直接硬编码色值。
 */
@Immutable
data class AppAccentPalette(
    /** 播放操作图标色（蓝色） */
    val play: Color = Color(0xFF4E8EE8),
    /** 添加操作图标色（绿色） */
    val add: Color = Color(0xFF31A985),
    /** 重命名/编辑操作图标色（橙色） */
    val rename: Color = Color(0xFFF2A93B),
    /** 信息操作图标色（青色） */
    val info: Color = Color(0xFF33A6B8),
    /** 全选操作图标色（靛蓝色） */
    val selectAll: Color = Color(0xFF6D7FE8),
    /** 隐藏操作图标色（橙色，与 rename 共享） */
    val hide: Color = Color(0xFFF2A93B),
    /** 删除/危险操作图标色（红色） */
    val delete: Color = Color(0xFFE25555),
    /** 退出操作图标色（红色） */
    val exit: Color = Color(0xFFE25555),
    /** 主题切换图标色（橙色） */
    val theme: Color = Color(0xFFF2A93B),
    /** 均衡器图标色（紫色） */
    val equalizer: Color = Color(0xFF9B6BE8),
    /**
     * 媒体类侧边栏导航项图标色列表（按顺序分配给各媒体导航项）。
     */
    val mediaIconColors: List<Color> = listOf(
        Color(0xFF4E8EE8),
        Color(0xFF9B6BE8),
        Color(0xFFE45F91),
        Color(0xFFF2A93B),
        Color(0xFF31A985),
    ),
    /**
     * 操作类侧边栏导航项图标色列表（按顺序分配给各操作导航项）。
     */
    val operationIconColors: List<Color> = listOf(
        Color(0xFF33A6B8),
        Color(0xFF6D7FE8),
        Color(0xFF7D8795),
        Color(0xFF4E8EE8),
    ),
)

/** 默认调色板实例，颜色固定、不随 preset 主题变化。 */
val DefaultAppAccentPalette = AppAccentPalette()

internal val LocalAppAccentPalette = staticCompositionLocalOf { DefaultAppAccentPalette }
