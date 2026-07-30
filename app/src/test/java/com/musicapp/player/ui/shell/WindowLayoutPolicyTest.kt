package com.musicapp.player.ui.shell

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutPolicyTest {
    @Test
    fun widthsBelowCompactBreakpointUseHalfWidthDrawer() {
        assertEquals(WindowLayoutPolicy.COMPACT_DRAWER, WindowLayoutPolicy.forWidth(599.dp))
        assertEquals(0.5f, WindowLayoutPolicy.COMPACT_DRAWER.drawerFraction)
    }

    @Test
    fun compactBreakpointStartsPersistentMediumSidebar() {
        assertEquals(WindowLayoutPolicy.MEDIUM_SIDEBAR, WindowLayoutPolicy.forWidth(600.dp))
        assertEquals(240.dp, WindowLayoutPolicy.MEDIUM_SIDEBAR.sidebarWidth)
    }

    @Test
    fun widthsThroughMediumRangeStayOnPersistentMediumSidebar() {
        assertEquals(WindowLayoutPolicy.MEDIUM_SIDEBAR, WindowLayoutPolicy.forWidth(839.dp))
    }

    @Test
    fun expandedBreakpointUsesPermanentSidebar() {
        assertEquals(WindowLayoutPolicy.EXPANDED_SIDEBAR, WindowLayoutPolicy.forWidth(840.dp))
        assertEquals(256.dp, WindowLayoutPolicy.EXPANDED_SIDEBAR.sidebarWidth)
    }
}
