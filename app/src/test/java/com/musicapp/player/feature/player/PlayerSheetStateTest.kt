package com.musicapp.player.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSheetStateTest {
    @Test
    fun `drag follows pointer and remains between the two anchors`() {
        val half = PlayerSheetState().dragBy(deltaYPx = -400f, travelPx = 800f)
        assertEquals(0.5f, half.expansionProgress)
        assertEquals(1f, half.dragBy(-1_000f, 800f).expansionProgress)
        assertEquals(0f, half.dragBy(1_000f, 800f).expansionProgress)
    }

    @Test
    fun `settle selects only collapsed or expanded and never hidden`() {
        assertEquals(PlayerSheetValue.EXPANDED, PlayerSheetState(0.2f).settle(-700f).value)
        assertEquals(0f, PlayerSheetState(0.4f).settle(0f).expansionProgress)
        assertEquals(1f, PlayerSheetState(0.6f).settle(0f).expansionProgress)
        assertTrue(PlayerLayerAlpha.mini(0f) > PlayerLayerAlpha.full(0f))
        assertTrue(PlayerLayerAlpha.full(1f) > PlayerLayerAlpha.mini(1f))
    }
}
