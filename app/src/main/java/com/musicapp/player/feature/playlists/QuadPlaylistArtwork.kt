package com.musicapp.player.feature.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.image.toArtworkRequest
import com.musicapp.player.theme.MusicTheme

val PLAYLIST_HERO_ARTWORK_SIZE: Dp = 130.dp
val PLAYLIST_HERO_ARTWORK_CORNER_RADIUS: Dp = 12.dp
val PLAYLIST_QUAD_GRID_GAP: Dp = 4.dp
val PLAYLIST_QUAD_SUB_ARTWORK_SIZE: Dp = 63.dp

/**
 * 歌单详情页 Hero 区域四宫格封面组件 (Quad Playlist Artwork)。
 *
 * 规范与降级算法：
 * - 尺寸与圆角：130×130dp，圆角 12dp (MusicShapes.medium)
 * - 当歌单可用封面数 >= 4：取前 4 首单曲封面组成 2×2 网格（每个子格 63×63dp，间隙 4dp）
 * - 当歌单可用封面数 1 ~ 3：使用首张单曲封面全尺寸铺满 (130×130dp)
 * - 当 0 张封面或空歌单：展示系统默认歌单矢量占位图 (ic_playlist_album)
 */
@Composable
fun QuadPlaylistArtwork(
    playlist: Playlist?,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PLAYLIST_HERO_ARTWORK_CORNER_RADIUS)
    val playlistName = playlist?.displayName ?: stringResource(R.string.playlist_unknown_name)
    val contentDesc = stringResource(R.string.track_artwork_description, playlistName)

    Box(
        modifier = modifier
            .size(PLAYLIST_HERO_ARTWORK_SIZE)
            .clip(shape)
            .background(MusicTheme.colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val top4Tracks = remember(tracks) {
            tracks.take(4)
        }

        when {
            top4Tracks.size >= 4 -> {
                Column(
                    modifier = Modifier.size(PLAYLIST_HERO_ARTWORK_SIZE),
                    verticalArrangement = Arrangement.spacedBy(PLAYLIST_QUAD_GRID_GAP),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PLAYLIST_QUAD_GRID_GAP),
                    ) {
                        SubArtworkImage(track = top4Tracks[0])
                        SubArtworkImage(track = top4Tracks[1])
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(PLAYLIST_QUAD_GRID_GAP),
                    ) {
                        SubArtworkImage(track = top4Tracks[2])
                        SubArtworkImage(track = top4Tracks[3])
                    }
                }
            }
            top4Tracks.isNotEmpty() -> {
                AsyncImage(
                    model = top4Tracks.first().toArtworkRequest(),
                    contentDescription = contentDesc,
                    modifier = Modifier.size(PLAYLIST_HERO_ARTWORK_SIZE),
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_playlist_album),
                    placeholder = painterResource(R.drawable.ic_playlist_album),
                )
            }
            else -> {
                AsyncImage(
                    model = R.drawable.ic_playlist_album,
                    contentDescription = contentDesc,
                    modifier = Modifier.size(PLAYLIST_HERO_ARTWORK_SIZE),
                    contentScale = ContentScale.Fit,
                    error = painterResource(R.drawable.ic_playlist_album),
                    placeholder = painterResource(R.drawable.ic_playlist_album),
                )
            }
        }
    }
}

@Composable
private fun SubArtworkImage(
    track: Track,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = track.toArtworkRequest(),
        contentDescription = null,
        modifier = modifier
            .size(PLAYLIST_QUAD_SUB_ARTWORK_SIZE)
            .background(MusicTheme.colors.secondaryContainer),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.ic_playlist_album),
        placeholder = painterResource(R.drawable.ic_playlist_album),
    )
}
