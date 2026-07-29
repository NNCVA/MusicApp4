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
    fun compactBreakpointStartsNavigationRail() {
        assertEquals(WindowLayoutPolicy.MEDIUM_RAIL, WindowLayoutPolicy.forWidth(600.dp))
    }

    @Test
    fun widthsThroughMediumRangeStayOnNavigationRail() {
        assertEquals(WindowLayoutPolicy.MEDIUM_RAIL, WindowLayoutPolicy.forWidth(839.dp))
    }

    @Test
    fun expandedBreakpointUsesPermanentSidebar() {
        assertEquals(WindowLayoutPolicy.EXPANDED_SIDEBAR, WindowLayoutPolicy.forWidth(840.dp))
        assertEquals(256.dp, WindowLayoutPolicy.EXPANDED_SIDEBAR.sidebarWidth)
    }
}
