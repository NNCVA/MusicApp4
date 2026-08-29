package com.musicapp.player.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactPushDrawerTest {
    @Test
    fun `gesture remains undecided before touch slop`() {
        assertEquals(
            CompactDrawerDragDirection.UNDECIDED,
            resolveCompactDrawerDragDirection(
                totalDragX = 7f,
                totalDragY = 5f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `gesture locks to its dominant direction after touch slop`() {
        assertEquals(
            CompactDrawerDragDirection.HORIZONTAL,
            resolveCompactDrawerDragDirection(
                totalDragX = 12f,
                totalDragY = 4f,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            CompactDrawerDragDirection.VERTICAL,
            resolveCompactDrawerDragDirection(
                totalDragX = 4f,
                totalDragY = 12f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `drawer offset stays within closed and open anchors`() {
        assertEquals(-160f, coerceCompactDrawerOffset(offset = -220f, drawerWidth = 160f))
        assertEquals(-72f, coerceCompactDrawerOffset(offset = -72f, drawerWidth = 160f))
        assertEquals(0f, coerceCompactDrawerOffset(offset = 24f, drawerWidth = 160f))
    }

    @Test
    fun `drawer settles to the nearest anchor`() {
        assertEquals(-160f, compactDrawerSettledOffset(offset = -81f, drawerWidth = 160f))
        assertEquals(0f, compactDrawerSettledOffset(offset = -80f, drawerWidth = 160f))
    }

    @Test
    fun `drawer toggles between open and closed states`() {
        val drawerWidth = 160f
        // When fully closed, toggle opens the drawer
        assertEquals(0f, toggleCompactDrawerOffset(offset = -160f, drawerWidth = drawerWidth))

        // When fully open, toggle closes the drawer
        assertEquals(-160f, toggleCompactDrawerOffset(offset = 0f, drawerWidth = drawerWidth))

        // When animating toward open (targetOffset = 0f), toggle reverses to closed
        assertEquals(
            -160f,
            toggleCompactDrawerOffset(offset = -120f, drawerWidth = drawerWidth, targetOffset = 0f),
        )

        // When animating toward closed (targetOffset = -drawerWidth), toggle reverses to open
        assertEquals(
            0f,
            toggleCompactDrawerOffset(
                offset = -40f,
                drawerWidth = drawerWidth,
                targetOffset = -drawerWidth,
            ),
        )

        // When settled past midpoint towards open, toggle closes
        assertEquals(
            -160f,
            toggleCompactDrawerOffset(offset = -70f, drawerWidth = drawerWidth),
        )

        // When settled past midpoint towards closed, toggle opens
        assertEquals(
            0f,
            toggleCompactDrawerOffset(offset = -90f, drawerWidth = drawerWidth),
        )

        // When drawerWidth is zero or invalid
        assertEquals(0f, toggleCompactDrawerOffset(offset = 0f, drawerWidth = 0f))
    }
}

