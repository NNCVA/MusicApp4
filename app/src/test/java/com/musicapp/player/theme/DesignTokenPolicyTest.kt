package com.musicapp.player.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokenPolicyTest {
    @Test
    fun `window tiers use the accepted boundaries`() {
        assertEquals(MusicWindowWidthTier.COMPACT, MusicDimensions.tierForWidth(599.dp))
        assertEquals(MusicWindowWidthTier.MEDIUM, MusicDimensions.tierForWidth(600.dp))
        assertEquals(MusicWindowWidthTier.MEDIUM, MusicDimensions.tierForWidth(839.dp))
        assertEquals(MusicWindowWidthTier.EXPANDED, MusicDimensions.tierForWidth(840.dp))
    }

    @Test
    fun `fixed layout and accessibility dimensions match policy`() {
        MusicWindowWidthTier.entries.forEach { tier ->
            val dimensions = MusicDimensions.forTier(tier)
            assertEquals(600.dp, dimensions.compactWidthBreakpoint)
            assertEquals(840.dp, dimensions.expandedWidthBreakpoint)
            assertEquals(240.dp, dimensions.mediumSidebarWidth)
            assertEquals(256.dp, dimensions.permanentSidebarWidth)
            assertEquals(16.dp, dimensions.sidebarCardCornerRadius)
            assertEquals(4.dp, dimensions.sidebarSelectionIndicatorWidth)
            assertEquals(16.dp, dimensions.sidebarOuterPadding)
            assertEquals(16.dp, dimensions.sidebarCardSpacing)
            assertEquals(8.dp, dimensions.sidebarCardContentPadding)
            assertEquals(72.dp, dimensions.trackListItemHeight)
            assertEquals(80.dp, dimensions.folderListItemHeight)
            assertEquals(130.dp, dimensions.playlistHeroArtworkSize)
            assertEquals(63.dp, dimensions.playlistQuadSubArtworkSize)
            assertEquals(80.dp, dimensions.miniPlayerHeight)
            assertEquals(160.dp, dimensions.adaptiveGridMinimumCellWidth)
            assertEquals(48.dp, dimensions.categoryCardInfoHeight)
            assertEquals(48.dp, dimensions.minimumTouchTarget)
            assertEquals(360.dp, dimensions.dialogListMaxHeight)
            assertEquals(8.dp, dimensions.spaceSmallMedium)
            assertEquals(31.dp, dimensions.topBarNavigationVisualStartPadding)
        }
    }

    @Test
    fun `track rows meet the minimum touch target`() {
        MusicWindowWidthTier.entries.forEach { tier ->
            val dimensions = MusicDimensions.forTier(tier)

            assertTrue(
                "${tier.name} track rows must remain accessible",
                dimensions.trackListItemHeight >= dimensions.minimumTouchTarget,
            )
            assertTrue(
                "${tier.name} folder rows must remain accessible",
                dimensions.folderListItemHeight >= dimensions.minimumTouchTarget,
            )
        }
    }

    @Test
    fun `mini artwork corner follows the window width tier`() {
        assertEquals(12.dp, MusicDimensions.Compact.miniArtworkCornerRadius)
        assertEquals(16.dp, MusicDimensions.Medium.miniArtworkCornerRadius)
        assertEquals(16.dp, MusicDimensions.Expanded.miniArtworkCornerRadius)
    }

    @Test
    fun `shape and typography token objects are complete`() {
        val shapes = MusicShapes()
        val typography = MusicTypography()

        assertNotNull(shapes.extraSmall)
        assertNotNull(shapes.extraLarge)
        assertNotNull(shapes.material)
        assertNotNull(typography.titleLarge)
        assertNotNull(typography.bodyLarge)
        assertNotNull(typography.labelSmall)
        assertEquals(16.sp, typography.compactTrackTitle.fontSize)
        assertEquals(20.sp, typography.compactTrackTitle.lineHeight)
        assertEquals(12.sp, typography.compactTrackArtist.fontSize)
        assertEquals(16.sp, typography.compactTrackArtist.lineHeight)
        assertEquals(18.sp, typography.expandedTrackTitle.fontSize)
        assertEquals(22.sp, typography.expandedTrackTitle.lineHeight)
        assertEquals(14.sp, typography.expandedTrackArtist.fontSize)
        assertEquals(18.sp, typography.expandedTrackArtist.lineHeight)
        assertNotNull(typography.material)
    }

    @Test
    fun `every preset provides distinct complete light and dark schemes`() {
        val lightPrimaries = mutableSetOf<Color>()
        PresetTheme.entries.forEach { preset ->
            val light = presetColorScheme(preset, darkTheme = false)
            val dark = presetColorScheme(preset, darkTheme = true)

            assertNotEquals(Color.Unspecified, light.primary)
            assertNotEquals(Color.Unspecified, light.surfaceContainerHighest)
            assertNotEquals(Color.Unspecified, dark.primary)
            assertNotEquals(Color.Unspecified, dark.surfaceContainerHighest)
            assertNotEquals(light.primary, dark.primary)
            lightPrimaries += light.primary
        }
        assertEquals(PresetTheme.entries.size, lightPrimaries.size)
    }

    @Test
    fun `dynamic color falls back to default blue when platform does not support it`() {
        assertEquals(
            PresetTheme.DEFAULT_BLUE,
            resolvePresetTheme(
                colorSource = ColorSource.DYNAMIC,
                presetTheme = PresetTheme.SUNSET_ORANGE,
                supportsDynamicColor = false,
            ),
        )
        assertEquals(
            null,
            resolvePresetTheme(
                colorSource = ColorSource.DYNAMIC,
                presetTheme = PresetTheme.SUNSET_ORANGE,
                supportsDynamicColor = true,
            ),
        )
        assertEquals(
            PresetTheme.SUNSET_ORANGE,
            resolvePresetTheme(
                colorSource = ColorSource.PRESET,
                presetTheme = PresetTheme.SUNSET_ORANGE,
                supportsDynamicColor = false,
            ),
        )
    }
}
