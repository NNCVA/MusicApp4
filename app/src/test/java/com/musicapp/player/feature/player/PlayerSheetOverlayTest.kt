package com.musicapp.player.feature.player

import androidx.compose.ui.graphics.Color
import com.musicapp.player.theme.AERO_CARD_CONTAINER_ALPHA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSheetOverlayTest {

    @Test
    fun `resolveMiniPlayerOverlayColor returns null when custom background is disabled`() {
        assertNull(
            resolveMiniPlayerOverlayColor(
                customBackgroundEnabled = false,
                isDarkTheme = true,
            ),
        )
        assertNull(
            resolveMiniPlayerOverlayColor(
                customBackgroundEnabled = false,
                isDarkTheme = false,
            ),
        )
    }

    @Test
    fun `resolveMiniPlayerOverlayColor returns semi-transparent black in dark theme when custom background is enabled`() {
        val overlayColor = resolveMiniPlayerOverlayColor(
            customBackgroundEnabled = true,
            isDarkTheme = true,
        )
        val expectedColor = Color.Black.copy(alpha = AERO_CARD_CONTAINER_ALPHA)
        assertEquals(expectedColor, overlayColor)
    }

    @Test
    fun `resolveMiniPlayerOverlayColor returns semi-transparent white in light theme when custom background is enabled`() {
        val overlayColor = resolveMiniPlayerOverlayColor(
            customBackgroundEnabled = true,
            isDarkTheme = false,
        )
        val expectedColor = Color.White.copy(alpha = AERO_CARD_CONTAINER_ALPHA)
        assertEquals(expectedColor, overlayColor)
    }
}
