package com.musicapp.player.ui.sidebar

import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.navigation.AboutRoute
import com.musicapp.player.navigation.AlbumsRoute
import com.musicapp.player.navigation.ArtistsRoute
import com.musicapp.player.navigation.FoldersRoute
import com.musicapp.player.navigation.HistoryRoute
import com.musicapp.player.navigation.PlaylistsRoute
import com.musicapp.player.navigation.SettingsRoute
import com.musicapp.player.navigation.TracksRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarModelsTest {
    @Test
    fun `media card follows the accepted destination order`() {
        assertEquals(
            listOf(TracksRoute, AlbumsRoute, ArtistsRoute, FoldersRoute, PlaylistsRoute),
            SidebarGroups.mediaBrowse.map(SidebarEntry.Destination::route),
        )
    }

    @Test
    fun `operation card keeps scan as an action instead of a route`() {
        assertEquals(
            listOf(
                SidebarAction.SCAN_LIBRARY,
                HistoryRoute,
                SettingsRoute,
                AboutRoute,
            ),
            SidebarGroups.appOperations.map { entry ->
                when (entry) {
                    is SidebarEntry.Action -> entry.action
                    is SidebarEntry.Destination -> entry.route
                }
            },
        )
        assertTrue(SidebarGroups.appOperations.first() is SidebarEntry.Action)
    }

    @Test
    fun `theme shortcut cycles through all three modes`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.nextSidebarMode())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.nextSidebarMode())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.nextSidebarMode())
    }

    @Test
    fun `exit choices dispatch exactly one action`() {
        SidebarExitChoice.entries.forEach { choice ->
            val calls = mutableListOf<SidebarExitChoice>()

            dispatchSidebarExitChoice(
                choice = choice,
                onFullExit = { calls += SidebarExitChoice.FULL_EXIT },
                onReturnToDesktop = { calls += SidebarExitChoice.RETURN_TO_DESKTOP },
                onCancel = { calls += SidebarExitChoice.CANCEL },
            )

            assertEquals(listOf(choice), calls)
        }
    }
}
