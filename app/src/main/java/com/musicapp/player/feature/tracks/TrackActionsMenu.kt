package com.musicapp.player.feature.tracks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.musicapp.player.core.designsystem.component.TrackActionsMenu as CoreTrackActionsMenu
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId

/** Displays the shared track actions used by selection and individual-track menus. */
@Composable
fun TrackActionsMenu(
    expanded: Boolean,
    playlists: List<Playlist>,
    onDismissRequest: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShowTrackInfo: () -> Unit = {},
    onHide: () -> Unit,
    onAddToPlaylist: (PlaylistId) -> Unit,
    modifier: Modifier = Modifier,
) {
    CoreTrackActionsMenu(
        expanded = expanded,
        playlists = playlists,
        onDismissRequest = onDismissRequest,
        onAddToQueue = onAddToQueue,
        onPlayNext = onPlayNext,
        onShowTrackInfo = onShowTrackInfo,
        onHide = onHide,
        onAddToPlaylist = onAddToPlaylist,
        modifier = modifier,
    )
}
