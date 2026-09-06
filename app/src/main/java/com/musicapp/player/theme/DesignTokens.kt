package com.musicapp.player.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MusicWindowWidthTier {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Immutable
data class MusicDimensions(
    val windowWidthTier: MusicWindowWidthTier,
    val compactWidthBreakpoint: Dp = 600.dp,
    val expandedWidthBreakpoint: Dp = 840.dp,
    val mediumSidebarWidth: Dp = 300.dp,
    val permanentSidebarWidth: Dp = 300.dp,
    val sidebarCardCornerRadius: Dp = 16.dp,
    val sidebarSelectionIndicatorWidth: Dp = 4.dp,
    val sidebarOuterPadding: Dp = 16.dp,
    val sidebarCardSpacing: Dp = 16.dp,
    val sidebarCardContentPadding: Dp = 8.dp,
    val trackListItemHeight: Dp = 72.dp,
    val folderListItemHeight: Dp = 80.dp,
    val trackArtworkSize: Dp = 48.dp,
    val detailTopBarHeight: Dp = 56.dp,
    val artistHeroArtworkSize: Dp = 112.dp,
    val albumRowArtworkSize: Dp = 56.dp,
    val albumRowMinHeight: Dp = 80.dp,
    val playlistHeroArtworkSize: Dp = 130.dp,
    val playlistQuadSubArtworkSize: Dp = 63.dp,
    val sectionIndexItemSize: Dp = 12.dp,
    val sectionIndexItemGap: Dp = 3.dp,
    val sectionIndexTouchTargetWidth: Dp = 20.dp,
    val sectionIndexBubbleSize: Dp = 72.dp,
    val miniPlayerHeight: Dp = 80.dp,
    val miniArtworkSize: Dp = 56.dp,
    val miniArtworkCornerRadius: Dp,
    val fullPlayerArtworkSize: Dp = 280.dp,
    val playerHeaderHeight: Dp = 64.dp,
    val playerControlsHeight: Dp = 80.dp,
    val playerSheetElevation: Dp = 6.dp,
    val messageBubbleMaxWidth: Dp = 560.dp,
    val messageBubbleHorizontalPadding: Dp = 24.dp,
    val messageBubbleVerticalPadding: Dp = 12.dp,
    val messageBubbleElevation: Dp = 6.dp,
    val messageBubbleBottomLift: Dp = 16.dp,
    val statusIndicatorSize: Dp = 24.dp,
    val trackInfoDialogMaxWidth: Dp = 640.dp,
    val settingsContentMaxWidth: Dp = 720.dp,
    val adaptiveGridMinimumCellWidth: Dp = 160.dp,
    val categoryCardMinHeight: Dp = 112.dp,
    val categoryCardInfoHeight: Dp = 48.dp,
    val minimumTouchTarget: Dp = 48.dp,
    val dialogListMaxHeight: Dp = 360.dp,
    val spaceExtraSmall: Dp = 4.dp,
    val spaceSmallMedium: Dp = 8.dp,
    val spaceSmall: Dp = 12.dp,
    val spaceMedium: Dp = 16.dp,
    val spaceLarge: Dp = 24.dp,
    val spaceExtraLarge: Dp = 32.dp,
    val topBarHorizontalPadding: Dp = 16.dp,
    val topBarNavigationVisualStartPadding: Dp = 31.dp,
    val dropdownMenuWidth: Dp = 200.dp,
    val dropdownMenuMinWidth: Dp = 128.dp,
    val dropdownMenuMaxWidth: Dp = 280.dp,
    val settingsOptionMinHeight: Dp = 84.dp,
    val settingsOptionIconSize: Dp = 28.dp,
    val sectionIndexBubbleShadowElevation: Dp = 8.dp,
    val sectionIndexBubbleOffsetExtra: Dp = 16.dp,
    val contentHorizontalPadding: Dp,
) {
    companion object {
        val Compact = MusicDimensions(
            windowWidthTier = MusicWindowWidthTier.COMPACT,
            miniArtworkCornerRadius = 12.dp,
            contentHorizontalPadding = 24.dp,
        )
        val Medium = MusicDimensions(
            windowWidthTier = MusicWindowWidthTier.MEDIUM,
            miniArtworkCornerRadius = 16.dp,
            contentHorizontalPadding = 24.dp,
        )
        val Expanded = MusicDimensions(
            windowWidthTier = MusicWindowWidthTier.EXPANDED,
            miniArtworkCornerRadius = 16.dp,
            contentHorizontalPadding = 24.dp,
        )

        fun forTier(tier: MusicWindowWidthTier): MusicDimensions =
            when (tier) {
                MusicWindowWidthTier.COMPACT -> Compact
                MusicWindowWidthTier.MEDIUM -> Medium
                MusicWindowWidthTier.EXPANDED -> Expanded
            }

        fun tierForWidth(width: Dp): MusicWindowWidthTier =
            when {
                width < Compact.compactWidthBreakpoint -> MusicWindowWidthTier.COMPACT
                width < Compact.expandedWidthBreakpoint -> MusicWindowWidthTier.MEDIUM
                else -> MusicWindowWidthTier.EXPANDED
            }
    }
}

@Immutable
data class MusicShapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp),
    val small: CornerBasedShape = RoundedCornerShape(8.dp),
    val medium: CornerBasedShape = RoundedCornerShape(12.dp),
    val large: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(28.dp),
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),
) {
    val material: Shapes
        get() =
            Shapes(
                extraSmall = extraSmall,
                small = small,
                medium = medium,
                large = large,
                extraLarge = extraLarge,
            )
}

internal val DefaultMusicShapes = MusicShapes()

/**
 * 应用统一 alpha 常量。
 *
 * 使用这些常量替代各处散落的 alpha 字面量，确保 disabled/hint/divider 等状态视觉一致。
 */
object MusicAlpha {
    /** Material 3 规范 disabled 状态透明度 */
    const val Disabled = 0.38f
    /** hint / placeholder 文字透明度 */
    const val Hint = 0.70f
    /** 分隔线透明度 */
    const val Divider = 0.50f
    /** 按压态容器背景透明度 */
    const val Pressed = 0.04f
    /** 选中态容器背景透明度 */
    const val Selected = 0.08f
}
