package com.musicapp.player.feature.tracks

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.theme.MusicAppTheme

/** Displays the shared track actions used by selection and individual-track menus. */
@Composable
internal fun TrackActionsMenu(
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
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_add_to_queue)) },
            onClick = {
                onDismissRequest()
                onAddToQueue()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_play_next)) },
            onClick = {
                onDismissRequest()
                onPlayNext()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_track_info)) },
            onClick = {
                onDismissRequest()
                onShowTrackInfo()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.selection_hide)) },
            onClick = {
                onDismissRequest()
                onHide()
            },
        )
        if (playlists.isEmpty()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.selection_no_playlists)) },
                onClick = {},
                enabled = false,
            )
        } else {
            playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.selection_add_to_playlist_named,
                                playlist.displayName,
                            ),
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        onAddToPlaylist(playlist.id)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackActionsMenuPreview() {
    MusicAppTheme {
        TrackActionsMenu(
            expanded = true,
            playlists = emptyList(),
            onDismissRequest = {},
            onAddToQueue = {},
            onPlayNext = {},
            onShowTrackInfo = {},
            onHide = {},
            onAddToPlaylist = {},
        )
    }
}
