package com.musicapp.player.ui.sidebar

import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.navigation.AboutRoute
import com.musicapp.player.navigation.AlbumsRoute
import com.musicapp.player.navigation.ArtistsRoute
import com.musicapp.player.navigation.FoldersRoute
import com.musicapp.player.navigation.HistoryRoute
import com.musicapp.player.navigation.PlaylistsRoute
import com.musicapp.player.navigation.ScanMusicRoute
import com.musicapp.player.navigation.SettingsRoute
import com.musicapp.player.navigation.TracksRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarModelsTest {
    @Test
    fun `media card follows the accepted destination order`() {
        assertEquals(
            listOf(TracksRoute, AlbumsRoute, ArtistsRoute, FoldersRoute, PlaylistsRoute),
            SidebarGroups.mediaBrowse.map(SidebarEntry::route),
        )
    }

    @Test
    fun `operation card provides top level destination routes`() {
        assertEquals(
            listOf(
                ScanMusicRoute,
                HistoryRoute,
                SettingsRoute,
                AboutRoute,
            ),
            SidebarGroups.appOperations.map(SidebarEntry::route),
        )
    }

    @Test
    fun `theme shortcut cycles through all three modes`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.nextSidebarMode())
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.nextSidebarMode())
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.nextSidebarMode())
    }
}
