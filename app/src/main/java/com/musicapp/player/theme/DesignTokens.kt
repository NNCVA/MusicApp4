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
    val permanentSidebarWidth: Dp = 256.dp,
    val trackListItemHeight: Dp = 80.dp,
    val miniPlayerHeight: Dp = 80.dp,
    val adaptiveGridMinimumCellWidth: Dp = 160.dp,
    val minimumTouchTarget: Dp = 48.dp,
    val radarSize: Dp = 176.dp,
    val radarStrokeWidth: Dp = 2.dp,
    val dialogListMaxHeight: Dp = 360.dp,
    val spaceExtraSmall: Dp = 4.dp,
    val spaceSmall: Dp = 8.dp,
    val spaceMedium: Dp = 16.dp,
    val spaceLarge: Dp = 24.dp,
    val spaceExtraLarge: Dp = 32.dp,
    val contentHorizontalPadding: Dp,
) {
    companion object {
        val Compact = MusicDimensions(windowWidthTier = MusicWindowWidthTier.COMPACT, contentHorizontalPadding = 16.dp)
        val Medium = MusicDimensions(windowWidthTier = MusicWindowWidthTier.MEDIUM, contentHorizontalPadding = 24.dp)
        val Expanded = MusicDimensions(windowWidthTier = MusicWindowWidthTier.EXPANDED, contentHorizontalPadding = 32.dp)

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
