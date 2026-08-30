package com.musicapp.player.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `Aero card container is translucent while dynamic Aero is active`() {
        val surfaceContainer = Color(0xFF336699)

        val result = resolveAeroCardContainerColor(
            surfaceContainer = surfaceContainer,
            transparencyEnabled = true,
        )

        assertEquals(surfaceContainer.copy(alpha = AERO_CARD_CONTAINER_ALPHA), result)
    }

    @Test
    fun `Aero card container is opaque when Aero transparency is disabled`() {
        val surfaceContainer = Color(0x80336699)

        val result = resolveAeroCardContainerColor(
            surfaceContainer = surfaceContainer,
            transparencyEnabled = false,
        )

        assertEquals(surfaceContainer.copy(alpha = 1f), result)
    }
}
