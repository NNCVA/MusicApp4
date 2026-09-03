package com.musicapp.player.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
    onAddToQueue: () -> Unit = {},
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
            text = { Text(stringResource(R.string.selection_play_next)) },
            iconTint = MenuIconPalette.Play,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_playback_skip_next),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onPlayNext()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_add_to_playlist)) },
            iconTint = MenuIconPalette.Add,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_common_add),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onAddToPlaylist()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_track_info)) },
            iconTint = MenuIconPalette.Info,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sidebar_about),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onShowTrackInfo()
            },
        )
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.selection_hide)) },
            iconTint = MenuIconPalette.Hide,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_common_close),
                    contentDescription = null,
                )
            },
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

