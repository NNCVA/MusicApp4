package com.musicapp.player.feature.tracks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.musicapp.player.core.designsystem.component.TrackActionsMenu as CoreTrackActionsMenu
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track

/** Displays the shared track actions used by selection and individual-track menus. */
@Composable
fun TrackActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    track: Track? = null,
    onAddToQueue: () -> Unit = {},
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (AlbumId) -> Unit = {},
    onShowTrackInfo: () -> Unit = {},
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
) {
    CoreTrackActionsMenu(
        expanded = expanded,
        track = track,
        playlists = playlists,
        onDismissRequest = onDismissRequest,
        onAddToQueue = onAddToQueue,
        onPlayNext = onPlayNext,
        onAddToPlaylist = onAddToPlaylist,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToAlbum = onNavigateToAlbum,
        onShowTrackInfo = onShowTrackInfo,
        onHide = onHide,
        modifier = modifier,
    )
}

