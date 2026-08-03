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
import com.musicapp.player.navigation.SettingsRoute
import com.musicapp.player.navigation.TopLevelNavKey
import com.musicapp.player.navigation.TracksRoute

internal sealed interface SidebarEntry {
    @get:StringRes
    val labelResId: Int

    @get:DrawableRes
    val iconResId: Int

    data class Destination(
        val route: TopLevelNavKey,
        @param:StringRes override val labelResId: Int,
        @param:DrawableRes override val iconResId: Int,
    ) : SidebarEntry

    data class Action(
        val action: SidebarAction,
        @param:StringRes override val labelResId: Int,
        @param:DrawableRes override val iconResId: Int,
    ) : SidebarEntry
}

internal enum class SidebarAction {
    SCAN_LIBRARY,
}

internal object SidebarGroups {
    val mediaBrowse: List<SidebarEntry.Destination> =
        listOf(
            SidebarEntry.Destination(TracksRoute, R.string.navigation_tracks, R.drawable.ic_sidebar_tracks),
            SidebarEntry.Destination(AlbumsRoute, R.string.navigation_albums, R.drawable.ic_sidebar_albums),
            SidebarEntry.Destination(ArtistsRoute, R.string.navigation_artists, R.drawable.ic_sidebar_artists),
            SidebarEntry.Destination(FoldersRoute, R.string.navigation_folders, R.drawable.ic_sidebar_folders),
            SidebarEntry.Destination(
                PlaylistsRoute,
                R.string.navigation_playlists,
                R.drawable.ic_sidebar_playlists,
            ),
        )

    val appOperations: List<SidebarEntry> =
        listOf(
            SidebarEntry.Action(
                SidebarAction.SCAN_LIBRARY,
                R.string.sidebar_scan_music,
                R.drawable.ic_sidebar_scan,
            ),
            SidebarEntry.Destination(HistoryRoute, R.string.navigation_history, R.drawable.ic_sidebar_history),
            SidebarEntry.Destination(
                SettingsRoute,
                R.string.navigation_settings,
                R.drawable.ic_sidebar_settings,
            ),
            SidebarEntry.Destination(AboutRoute, R.string.navigation_about, R.drawable.ic_sidebar_about),
        )
}

internal fun ThemeMode.nextSidebarMode(): ThemeMode =
    when (this) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
    }

internal enum class SidebarExitChoice {
    FULL_EXIT,
    RETURN_TO_DESKTOP,
    CANCEL,
}

internal inline fun dispatchSidebarExitChoice(
    choice: SidebarExitChoice,
    onFullExit: () -> Unit,
    onReturnToDesktop: () -> Unit,
    onCancel: () -> Unit,
) {
    when (choice) {
        SidebarExitChoice.FULL_EXIT -> onFullExit()
        SidebarExitChoice.RETURN_TO_DESKTOP -> onReturnToDesktop()
        SidebarExitChoice.CANCEL -> onCancel()
    }
}
