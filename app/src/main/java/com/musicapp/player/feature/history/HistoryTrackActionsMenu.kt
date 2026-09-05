package com.musicapp.player.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.AppDropdownMenu
import com.musicapp.player.core.designsystem.component.AppDropdownMenuItem
import com.musicapp.player.core.designsystem.component.MenuIconPalette
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import java.text.DateFormat
import java.util.Date

/**
 * 播放历史单曲项的专属操作菜单。
 *
 * 顺序严格固定为：
 * 1. 删除记录（危险）
 * 2. 下一首播放（可播放时显示）
 * 3. 加入歌单（可播放时显示）
 * 4. 歌曲信息（可播放时显示）
 * 5. 只读信息：最近播放：本地化日期时间
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
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    val colors = MusicTheme.colors
    val isPlayable = entry.isActionable

    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        // 1. 删除记录 (危险操作)
        AppDropdownMenuItem(
            text = { Text(stringResource(R.string.history_delete_record)) },
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

        if (isPlayable) {
            // 2. 下一首播放
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

            // 3. 加入歌单
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

            // 4. 歌曲信息
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
        }

        HorizontalDivider(
            color = colors.outlineVariant.copy(alpha = MusicAlpha.Divider),
            thickness = 1.dp,
        )

        // 5. 只读信息：最近播放时间
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
