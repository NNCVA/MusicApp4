package com.musicapp.player.core.designsystem.component

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDropdownMenuPositionProviderTest {

    private val density = Density(density = 1f, fontScale = 1f)

    @Test
    fun calculatePosition_placesBelowAnchor_whenSpaceIsSufficient() {
        val provider = AppDropdownMenuPositionProvider(
            contentOffset = DpOffset.Zero,
            density = density,
        )
        val anchor = IntRect(left = 100, top = 200, right = 150, bottom = 250)
        val windowSize = IntSize(width = 1000, height = 2000)
        val popupSize = IntSize(width = 200, height = 300)

        val position = provider.calculatePosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popupSize,
        )

        // y should be at anchor.bottom = 250
        assertEquals(250, position.y)
    }

    @Test
    fun calculatePosition_placesAboveAnchor_whenSpaceBelowIsInsufficient() {
        val provider = AppDropdownMenuPositionProvider(
            contentOffset = DpOffset.Zero,
            density = density,
        )
        val anchor = IntRect(left = 100, top = 1800, right = 150, bottom = 1850)
        val windowSize = IntSize(width = 1000, height = 2000)
        val popupSize = IntSize(width = 200, height = 300)

        val position = provider.calculatePosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popupSize,
        )

        // y should be placed above anchor.top: 1800 - 300 = 1500
        assertEquals(1500, position.y)
    }

    @Test
    fun calculatePosition_appliesContentOffsetsCorrectly() {
        val provider = AppDropdownMenuPositionProvider(
            contentOffset = DpOffset(x = 10.dp, y = 20.dp),
            density = density,
        )
        val anchor = IntRect(left = 100, top = 200, right = 150, bottom = 250)
        val windowSize = IntSize(width = 1000, height = 2000)
        val popupSize = IntSize(width = 200, height = 300)

        val position = provider.calculatePosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = popupSize,
        )

        assertEquals(270, position.y)
    }
}
