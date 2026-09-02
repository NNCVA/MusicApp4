package com.musicapp.player.feature.tracks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
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
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_add_to_queue)) },
            onClick = {
                onDismissRequest()
                onAddToQueue()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_play_next)) },
            onClick = {
                onDismissRequest()
                onPlayNext()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_track_info)) },
            onClick = {
                onDismissRequest()
                onShowTrackInfo()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_hide)) },
            onClick = {
                onDismissRequest()
                onHide()
            },
        )
        if (playlists.isEmpty()) {
            AppDropdownMenuItem(
                text = { Text(stringResource(R.string.selection_no_playlists)) },
                onClick = {},
                enabled = false,
            )
        } else {
            playlists.forEach { playlist ->
                AppDropdownMenuItem(
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
