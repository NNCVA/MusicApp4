package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.musicapp.player.R
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme

/**
 * 通用列表操作/吸顶控制栏（ListActionBar）。
 *
 * 承载常规状态下的播放全部、项数显示与尾部操作区（如排序菜单），
 * 以及多选状态下的取消、已选计数与全选/反选切换。
 * 具有不透明的 surface 背景，适用于在 [androidx.compose.foundation.lazy.LazyColumn] 中作为 stickyHeader。
 */
@Composable
fun ListActionBar(
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    // 常规模式参数
    itemCount: Int = 0,
    showPlayAll: Boolean = true,
    hasPlayableItems: Boolean = true,
    onPlayAll: () -> Unit = {},
    itemCountDescription: String? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
    // 多选模式参数
    selectedCount: Int = 0,
    isAllSelected: Boolean = false,
    onClearSelection: () -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
) {
    val dimensions = MusicTheme.dimensions

    Crossfade(
        targetState = isSelectionMode,
        label = "ListActionBarCrossfade",
        modifier = modifier
            .fillMaxWidth()
            .height(dimensions.minimumTouchTarget)
            .background(backgroundColor)
            .padding(
                start = dimensions.topBarHorizontalPadding,
                end = dimensions.contentHorizontalPadding,
            ),
    ) { isSelection ->
        if (isSelection) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            ) {
                BareIconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_common_close_circle),
                        contentDescription = stringResource(R.string.selection_cancel),
                        tint = MusicTheme.colors.onSurface,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }

                Text(
                    text = pluralStringResource(
                        R.plurals.selection_count,
                        selectedCount,
                        selectedCount,
                    ),
                    style = MusicTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MusicTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val hasSelection = selectedCount > 0
                val selectAllIcon = if (hasSelection) {
                    R.drawable.ic_common_radio_button_checked
                } else {
                    R.drawable.ic_common_radio_button_unchecked
                }
                val selectAllDesc = if (isAllSelected) {
                    stringResource(R.string.selection_deselect_all)
                } else {
                    stringResource(R.string.selection_select_all)
                }

                BareIconButton(
                    onClick = onToggleSelectAll,
                    modifier = Modifier.size(dimensions.minimumTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(selectAllIcon),
                        contentDescription = selectAllDesc,
                        tint = MusicTheme.colors.onSurface,
                        modifier = Modifier.size(dimensions.spaceLarge),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
            ) {
                if (showPlayAll) {
                    BareIconButton(
                        onClick = onPlayAll,
                        enabled = hasPlayableItems,
                        modifier = Modifier.size(dimensions.minimumTouchTarget),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playback_play_circle),
                            contentDescription = stringResource(R.string.category_play_all),
                            tint = if (hasPlayableItems) {
                                MusicTheme.colors.onSurface
                            } else {
                                MusicTheme.colors.onSurface.copy(alpha = MusicAlpha.Disabled)
                            },
                            modifier = Modifier.size(dimensions.spaceLarge),
                        )
                    }
                }

                val resolvedCountDesc = itemCountDescription ?: pluralStringResource(
                    R.plurals.category_track_count,
                    itemCount,
                    itemCount,
                )

                Text(
                    text = "$itemCount",
                    style = MusicTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MusicTheme.colors.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = resolvedCountDesc
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                trailingContent()
            }
        }
    }
}
