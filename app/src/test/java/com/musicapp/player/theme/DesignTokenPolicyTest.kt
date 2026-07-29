package com.musicapp.player.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicapp.player.core.domain.model.ColorSource
import com.musicapp.player.core.domain.model.PresetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
            assertEquals(256.dp, dimensions.permanentSidebarWidth)
            assertEquals(80.dp, dimensions.trackListItemHeight)
            assertEquals(dimensions.trackListItemHeight, dimensions.miniPlayerHeight)
            assertEquals(160.dp, dimensions.adaptiveGridMinimumCellWidth)
            assertEquals(48.dp, dimensions.minimumTouchTarget)
        }
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
