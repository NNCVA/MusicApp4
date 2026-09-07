package com.musicapp.player.feature.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResponsivePolicyTest {

    @Test
    fun `isLandscape returns true when width is greater than height`() {
        // Phone in landscape
        assertTrue(PlayerResponsivePolicy.isLandscape(width = 840.dp, height = 390.dp))
        // Tablet in landscape
        assertTrue(PlayerResponsivePolicy.isLandscape(width = 1280.dp, height = 800.dp))
    }

    @Test
    fun `isLandscape returns false when height is greater than or equal to width`() {
        // Phone in portrait
        assertFalse(PlayerResponsivePolicy.isLandscape(width = 390.dp, height = 840.dp))
        // Tablet in portrait
        assertFalse(PlayerResponsivePolicy.isLandscape(width = 800.dp, height = 1280.dp))
        // Square window
        assertFalse(PlayerResponsivePolicy.isLandscape(width = 600.dp, height = 600.dp))
    }

    @Test
    fun `calculateArtworkDiscSize scales with height on compact landscape phones`() {
        // Typical compact phone landscape: height = 360dp, half-width = 420dp
        val size = PlayerResponsivePolicy.calculateArtworkDiscSize(
            width = 420.dp,
            height = 360.dp,
            maxSize = 280.dp,
        )
        // 360 * 0.72 = 259.2dp
        assertEquals(259.2.dp, size)
    }

    @Test
    fun `calculateArtworkDiscSize clamps to maxSize on large screens`() {
        // Large tablet: height = 800dp, half-width = 640dp
        val size = PlayerResponsivePolicy.calculateArtworkDiscSize(
            width = 640.dp,
            height = 800.dp,
            maxSize = 280.dp,
        )
        assertEquals(280.dp, size)
    }
}
