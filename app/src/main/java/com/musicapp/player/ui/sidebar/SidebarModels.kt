package com.musicapp.player.ui.sidebar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.ThemeMode
import com.musicapp.player.navigation.AboutRoute
import com.musicapp.player.navigation.AlbumsRoute
import com.musicapp.player.navigation.ArtistsRoute
import com.musicapp.player.navigation.FoldersRoute
import com.musicapp.player.navigation.HistoryRoute
import com.musicapp.player.navigation.PlaylistsRoute
import com.musicapp.player.navigation.ScanMusicRoute
import com.musicapp.player.navigation.SettingsRoute
import com.musicapp.player.navigation.TopLevelNavKey
import com.musicapp.player.navigation.TracksRoute

internal data class SidebarEntry(
    val route: TopLevelNavKey,
    @param:StringRes val labelResId: Int,
    @param:DrawableRes val iconResId: Int,
)

internal object SidebarGroups {
    val mediaBrowse: List<SidebarEntry> =
        listOf(
            SidebarEntry(TracksRoute, R.string.navigation_tracks, R.drawable.ic_sidebar_tracks),
            SidebarEntry(AlbumsRoute, R.string.navigation_albums, R.drawable.ic_sidebar_albums),
            SidebarEntry(ArtistsRoute, R.string.navigation_artists, R.drawable.ic_sidebar_artists),
            SidebarEntry(FoldersRoute, R.string.navigation_folders, R.drawable.ic_sidebar_folders),
            SidebarEntry(
                PlaylistsRoute,
                R.string.navigation_playlists,
                R.drawable.ic_sidebar_playlists,
            ),
        )

    val appOperations: List<SidebarEntry> =
        listOf(
            SidebarEntry(
                ScanMusicRoute,
                R.string.sidebar_scan_music,
                R.drawable.ic_sidebar_scan,
            ),
            SidebarEntry(HistoryRoute, R.string.navigation_history, R.drawable.ic_sidebar_history),
            SidebarEntry(
                SettingsRoute,
                R.string.navigation_settings,
                R.drawable.ic_sidebar_settings,
            ),
            SidebarEntry(AboutRoute, R.string.navigation_about, R.drawable.ic_sidebar_about),
        )
}

internal fun ThemeMode.nextSidebarMode(): ThemeMode =
    when (this) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
    }
