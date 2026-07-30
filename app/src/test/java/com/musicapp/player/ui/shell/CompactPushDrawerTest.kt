package com.musicapp.player.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactPushDrawerTest {
    @Test
    fun `drawer progress follows the shared push offset`() {
        val drawerWidthPx = 160f

        assertEquals(0f, compactDrawerProgress(-drawerWidthPx, drawerWidthPx, false))
        assertEquals(0.5f, compactDrawerProgress(-drawerWidthPx / 2f, drawerWidthPx, false))
        assertEquals(1f, compactDrawerProgress(0f, drawerWidthPx, false))
    }

    @Test
    fun `drawer progress stays within layout bounds`() {
        val drawerWidthPx = 160f

        assertEquals(0f, compactDrawerProgress(-240f, drawerWidthPx, false))
        assertEquals(1f, compactDrawerProgress(80f, drawerWidthPx, false))
    }

    @Test
    fun `uninitialized drawer offset falls back to the settled state`() {
        assertEquals(0f, compactDrawerProgress(Float.NaN, 160f, false))
        assertEquals(1f, compactDrawerProgress(Float.NaN, 160f, true))
    }
}
