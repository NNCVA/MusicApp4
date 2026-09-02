package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.theme.MusicAppTheme
import com.musicapp.player.theme.MusicTheme

/**
 * 通用“添加到歌单”选择弹窗。
 *
 * 遵循设计系统规范：
 * - 底层复用通用单确认弹窗 [MessageDialog] 的自定义内容插槽重载；
 * - 标题为“添加到歌单”；
 * - 内容区域展示当前所有歌单列表（最大高度 300dp，支持滚动）；列表为空时展示友好空状态提示；
 * - 底部单个主色全宽胶囊按钮为“创建歌单”，点击触发 [onCreatePlaylist]；
 * - 每行歌单项具备 48dp 最小触控区域与水波纹点击反馈。
 */
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onSelectPlaylist: (PlaylistId) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    MessageDialog(
        title = stringResource(R.string.selection_add_to_playlist_dialog_title),
        confirmLabel = stringResource(R.string.playlist_create_title),
        onConfirm = onCreatePlaylist,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.selection_no_playlists),
                style = MusicTheme.typography.bodyMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
            ) {
                items(playlists, key = { it.id.value }) { playlist ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(dimensions.minimumTouchTarget)
                                .clickable { onSelectPlaylist(playlist.id) }
                                .padding(horizontal = dimensions.spaceSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sidebar_playlists),
                            contentDescription = null,
                            tint = MusicTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(dimensions.spaceLarge),
                        )
                        Spacer(modifier = Modifier.width(dimensions.spaceMedium))
                        Text(
                            text = playlist.displayName,
                            style = MusicTheme.typography.bodyLarge,
                            color = MusicTheme.colors.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "AddToPlaylistDialog with Playlists", showBackground = true)
@Composable
private fun AddToPlaylistDialogWithPlaylistsPreview() {
    MusicAppTheme {
        AddToPlaylistDialog(
            playlists =
                listOf(
                    Playlist(PlaylistId(1L), "我的至爱金曲", "我的至爱金曲", emptyList(), 0L),
                    Playlist(PlaylistId(2L), "轻音乐与白噪音", "轻音乐与白噪音", emptyList(), 0L),
                    Playlist(PlaylistId(3L), "驾车流行电台", "驾车流行电台", emptyList(), 0L),
                ),
            onSelectPlaylist = {},
            onCreatePlaylist = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "AddToPlaylistDialog Empty", showBackground = true)
@Composable
private fun AddToPlaylistDialogEmptyPreview() {
    MusicAppTheme {
        AddToPlaylistDialog(
            playlists = emptyList(),
            onSelectPlaylist = {},
            onCreatePlaylist = {},
            onDismiss = {},
        )
    }
}
