package com.musicapp.player.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FullPlayerStateTest {
    @Test
    fun `full player exposes artwork lyrics and queue in stable order`() {
        assertEquals(listOf(FullPlayerPage.ARTWORK, FullPlayerPage.LYRICS, FullPlayerPage.QUEUE), FullPlayerPage.entries)
    }

    @Test
    fun `page navigation clamps at both ends`() {
        assertEquals(FullPlayerPage.ARTWORK, FullPlayerState().previous().page)
        assertEquals(FullPlayerPage.LYRICS, FullPlayerState().next().page)
        assertEquals(FullPlayerPage.QUEUE, FullPlayerState(FullPlayerPage.QUEUE).next().page)
        assertEquals(FullPlayerPage.QUEUE, FullPlayerState().select(FullPlayerPage.QUEUE).page)
    }
}
