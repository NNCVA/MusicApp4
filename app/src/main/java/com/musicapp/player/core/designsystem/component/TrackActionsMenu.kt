package com.musicapp.player.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.albums.UNKNOWN_ALBUM_ID
import com.musicapp.player.feature.artists.ArtistGrouping
import com.musicapp.player.theme.MusicAppTheme

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
    var showSelectArtistDialog by remember { mutableStateOf(false) }
    val splitArtists = remember(track?.artistName) {
        ArtistGrouping.splitArtistNames(track?.artistName)
    }

    if (showSelectArtistDialog && splitArtists.size > 1) {
        SelectArtistDialog(
            artists = splitArtists,
            onSelectArtist = { selectedArtist ->
                onNavigateToArtist(selectedArtist)
            },
            onDismiss = { showSelectArtistDialog = false },
        )
    }

    val rawArtistName = track?.artistName
    val isUnknownArtist = isUnknownArtist(rawArtistName)
    val displayArtistName = rawArtistName?.localizedArtistName() ?: stringResource(R.string.unknown_artist)

    val rawAlbumTitle = track?.albumTitle
    val albumId = track?.albumId
    val isUnknownAlbum = isUnknownAlbum(rawAlbumTitle, albumId)
    val displayAlbumTitle = if (rawAlbumTitle.isNullOrBlank()) {
        stringResource(R.string.album_unknown_title)
    } else {
        rawAlbumTitle
    }

    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        // 第一组：播放与加入歌单
        AppDropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.selection_add_to_queue),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
            iconTint = MenuIconPalette.Add,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_common_queue_add),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onAddToQueue()
            },
        )
        AppDropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.selection_play_next),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
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
            text = {
                Text(
                    text = stringResource(R.string.selection_add_to_playlist),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
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

        AppDropdownMenuDivider()

        // 第二组：艺术家、专辑、歌曲信息
        AppDropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.selection_menu_artist_named, displayArtistName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
            enabled = !isUnknownArtist,
            iconTint = MenuIconPalette.Artist,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sidebar_artists),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                if (splitArtists.size > 1) {
                    showSelectArtistDialog = true
                } else if (splitArtists.isNotEmpty()) {
                    onNavigateToArtist(splitArtists.first())
                }
            },
        )
        AppDropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.selection_menu_album_named, displayAlbumTitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
            enabled = !isUnknownAlbum,
            iconTint = MenuIconPalette.Album,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sidebar_albums),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                albumId?.let(onNavigateToAlbum)
            },
        )
        AppDropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.selection_track_info),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
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

        AppDropdownMenuDivider()

        // 第三组：隐藏
        AppDropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.selection_hide),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
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
