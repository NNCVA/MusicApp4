package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier

const val UNKNOWN_ARTIST_SENTINEL = "<unknown>"

/**
 * 转换未知艺术家占位符为本地化文案。
 */
@Composable
fun String.localizedArtistName(): String =
    if (this == UNKNOWN_ARTIST_SENTINEL) stringResource(R.string.unknown_artist) else this

/**
 * 格式化单曲副标题（艺术家 · 专辑）。
 */
fun formatTrackSubtitle(artist: String, album: String?): String {
    val cleanAlbum = album?.takeIf { it.isNotBlank() }
    return if (cleanAlbum != null) {
        "$artist · $cleanAlbum"
    } else {
        artist
    }
}

/**
 * 渲染曲目封面图。
 */
@Composable
fun TrackArtwork(
    track: Track,
    modifier: Modifier = Modifier,
) {
    val shape = MusicTheme.shapes.extraSmall
    val artworkDescription = stringResource(R.string.track_artwork_description, track.title)
    AsyncImage(
        model = track,
        contentDescription = artworkDescription,
        modifier = modifier
            .clip(shape)
            .background(MusicTheme.colors.secondaryContainer),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_playlist_album),
        placeholder = painterResource(R.drawable.ic_playlist_album),
    )
}

/**
 * 全局统一的单曲列表项组件。
 *
 * 提供标准化的单曲视图展现：
 * - 封面图 (TrackArtwork) 或 自定义 leadingContent 插槽
 * - 标题 (2 行省略，自适应宽度阶梯字体)
 * - 音质角标 (QualityBadge) 与「艺术家 · 专辑」副标题
 * - 暂时不可用状态指示
 * - 多选模式 Checkbox 勾选框
 * - 常规模式行级管理菜单 (TrackActionsMenu) 或 自定义 trailingContent 插槽
 * - 点击 / 长按手势绑定
 * - 性能测量回调 onLaidOut
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    onAddToQueue: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onHide: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onShowTrackInfo: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onLaidOut: (() -> Unit)? = null,
    showArtwork: Boolean = true,
    showQualityBadge: Boolean = true,
    showMoreMenu: Boolean = true,
    outerHorizontalPadding: Dp = MusicTheme.dimensions.contentHorizontalPadding,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val dimensions = MusicTheme.dimensions
    val compact = dimensions.windowWidthTier == MusicWindowWidthTier.COMPACT
    val artistName = track.artistName.localizedArtistName()
    val subtitle =
        track.albumTitle
            ?.takeIf(String::isNotBlank)
            ?.let { stringResource(R.string.track_artist_album, artistName, it) }
            ?: artistName

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.trackListItemHeight)
            .then(
                if (onLaidOut != null) {
                    Modifier.onGloballyPositioned { onLaidOut() }
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = outerHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else if (showArtwork) {
            TrackArtwork(
                track = track,
                modifier = Modifier.size(dimensions.trackArtworkSize),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = if (compact) {
                    MusicTheme.typography.compactTrackTitle
                } else {
                    MusicTheme.typography.expandedTrackTitle
                },
                color = MusicTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            ) {
                if (showQualityBadge) {
                    track.resolveQuality()?.let { quality ->
                        QualityBadge(quality = quality)
                    }
                }
                Text(
                    text = subtitle,
                    style = if (compact) {
                        MusicTheme.typography.compactTrackArtist
                    } else {
                        MusicTheme.typography.expandedTrackArtist
                    },
                    color = MusicTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (track.availability == Availability.TEMPORARILY_UNAVAILABLE) {
            Text(
                text = stringResource(R.string.track_temporarily_unavailable),
                style = MusicTheme.typography.labelSmall,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (selectionMode) {
            BareIconButton(
                onClick = onClick,
                modifier = Modifier.size(dimensions.minimumTouchTarget),
            ) {
                Icon(
                    painter = painterResource(
                        if (selected) R.drawable.ic_common_check_circle else R.drawable.ic_common_radio_button_unchecked,
                    ),
                    contentDescription = stringResource(
                        if (selected) R.string.selection_deselect_all else R.string.selection_select_all,
                    ),
                    tint = if (selected) MusicTheme.colors.primary else MusicTheme.colors.onSurfaceVariant,
                    modifier = Modifier.size(dimensions.spaceLarge),
                )
            }
        } else if (showMoreMenu) {
            var menuExpanded by remember(track.id) { mutableStateOf(false) }
            Box {
                BareIconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_more_vertical),
                        contentDescription = stringResource(R.string.track_more_actions),
                        tint = MusicTheme.colors.onSurfaceVariant,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
                TrackActionsMenu(
                    expanded = menuExpanded,
                    playlists = playlists,
                    onDismissRequest = { menuExpanded = false },
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onShowTrackInfo = onShowTrackInfo,
                    onHide = onHide,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackRowPreview() {
    MusicAppTheme {
        TrackRow(
            track = Track(
                id = TrackId(volumeName = "primary", mediaStoreId = 1L),
                title = "Song Title",
                artistName = "Artist Name",
                albumTitle = "Album Title",
                durationMs = 180_000L,
                dateAddedMs = 1_000L,
                dateModifiedMs = 2_000L,
                relativePath = "Music/",
                displayName = "Song Title.flac",
                mimeType = "audio/flac",
                sizeBytes = 50_000_000L,
                availability = Availability.AVAILABLE,
            ),
            onClick = {},
        )
    }
}
