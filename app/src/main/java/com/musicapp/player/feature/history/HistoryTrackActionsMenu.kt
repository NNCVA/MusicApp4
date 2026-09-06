package com.musicapp.player.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuDivider
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.MenuIconPalette
import com.musicapp.player.core.designsystem.component.SelectArtistDialog
import com.musicapp.player.core.designsystem.component.isUnknownAlbum
import com.musicapp.player.core.designsystem.component.isUnknownArtist
import com.musicapp.player.core.designsystem.component.localizedArtistName
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.feature.artists.ArtistGrouping
import com.musicapp.player.theme.MusicTheme
import java.text.DateFormat
import java.util.Date

/**
 * 播放历史单曲项的专属操作菜单。
 */
@Composable
fun HistoryTrackActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entry: HistoryEntry,
    onDeleteRecord: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowTrackInfo: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (AlbumId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val colors = MusicTheme.colors
    val isPlayable = entry.isActionable
    val track = entry.track

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
        if (isPlayable) {
            // 第一组：播放与加入歌单
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
        }

        // 第三组：删除记录 (危险操作)
        AppDropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.history_delete_record),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            },
            isDestructive = true,
            iconTint = MenuIconPalette.Delete,
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_common_delete),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismissRequest()
                onDeleteRecord()
            },
        )

        AppDropdownMenuDivider()

        // 第四组：只读信息：最近播放时间
        val formattedDate =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(entry.history.lastPlayedAtMs))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensions.spaceMedium,
                        vertical = dimensions.spaceSmall,
                    ),
        ) {
            Text(
                text = stringResource(R.string.history_recent_played, formattedDate),
                style = MusicTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
