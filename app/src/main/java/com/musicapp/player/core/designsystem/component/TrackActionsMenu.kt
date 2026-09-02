package com.musicapp.player.core.designsystem.component

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
fun TrackActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowTrackInfo: () -> Unit = {},
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
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
            text = { Text(stringResource(R.string.selection_add_to_playlist)) },
            onClick = {
                onDismissRequest()
                onAddToPlaylist()
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
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackActionsMenuPreview() {
    MusicAppTheme {
        TrackActionsMenu(
            expanded = true,
            onDismissRequest = {},
            onAddToQueue = {},
            onPlayNext = {},
            onAddToPlaylist = {},
            onShowTrackInfo = {},
            onHide = {},
        )
    }
}

