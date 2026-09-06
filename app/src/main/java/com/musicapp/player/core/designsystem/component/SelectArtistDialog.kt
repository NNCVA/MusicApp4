package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.musicapp.player.R
import com.musicapp.player.theme.MusicTheme

/**
 * 多合作歌手选择弹窗。
 *
 * 遵循设计系统规范：
 * - 复用通用单确认弹窗 [MessageDialog] 的自定义内容插槽重载；
 * - 标题为“选择艺术家”；
 * - 内容区域展示当前歌曲所包含的合作歌手列表；
 * - 点击具体歌手触发 [onSelectArtist] 并关闭弹窗。
 */
@Composable
fun SelectArtistDialog(
    artists: List<String>,
    onSelectArtist: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    MessageDialog(
        title = stringResource(R.string.selection_menu_select_artist_title),
        confirmLabel = stringResource(R.string.dismiss),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MusicTheme.dimensions.dialogListMaxHeight),
            verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmallMedium),
        ) {
            items(artists, key = { it }) { artistName ->
                val rowShape = MusicTheme.shapes.medium
                Surface(
                    onClick = {
                        onSelectArtist(artistName)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensions.minimumTouchTarget)
                        .clip(rowShape)
                        .semantics(mergeDescendants = true) {},
                    shape = rowShape,
                    color = MusicTheme.colors.surfaceContainer,
                    contentColor = MusicTheme.colors.onSurface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = dimensions.spaceMedium,
                                vertical = dimensions.spaceSmall,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sidebar_artists),
                            contentDescription = null,
                            tint = MenuIconPalette.Artist,
                            modifier = Modifier.size(dimensions.spaceLarge),
                        )
                        Spacer(modifier = Modifier.width(dimensions.spaceMedium))
                        Text(
                            text = artistName.localizedArtistName(),
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
