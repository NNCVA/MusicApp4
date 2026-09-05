package com.musicapp.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme

/**
 * 底部多选批量操作项元数据。
 */
@Immutable
data class SelectionBarAction(
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isDestructive: Boolean = false,
)

/**
 * 全局统一的底部多选批量操作栏组件。
 *
 * 支持任意数量动作项均分排列（典型为 2 或 3 个动作），
 * 自动插入垂直分割线与顶部水平分割线，统一管理安全边距与破坏性动作样式。
 */
@Composable
fun SelectionBottomBar(
    actions: List<SelectionBarAction>,
    contentInsets: WindowInsets,
    applyBottomInset: Boolean,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    val dimensions = MusicTheme.dimensions
    val colors = MusicTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        tonalElevation = dimensions.spaceExtraSmall,
    ) {
        Column(
            modifier =
                if (applyBottomInset) {
                    Modifier.windowInsetsPadding(contentInsets.only(WindowInsetsSides.Bottom))
                } else {
                    Modifier
                },
        ) {
            HorizontalDivider(
                color = colors.outlineVariant.copy(alpha = MusicAlpha.Divider),
                thickness = 1.dp,
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(dimensions.minimumTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEachIndexed { index, action ->
                    if (index > 0) {
                        VerticalDivider(
                            modifier =
                                Modifier
                                    .height(dimensions.spaceLarge)
                                    .width(1.dp),
                            color = colors.outlineVariant.copy(alpha = MusicAlpha.Divider),
                        )
                    }

                    val itemColor =
                        when {
                            !action.enabled -> colors.onSurface.copy(alpha = MusicAlpha.Disabled)
                            action.isDestructive -> colors.error
                            else -> colors.onSurface
                        }

                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    enabled = action.enabled,
                                    onClick = action.onClick,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = null,
                            tint = itemColor,
                            modifier = Modifier.size(dimensions.spaceLarge),
                        )
                        Spacer(modifier = Modifier.width(dimensions.spaceSmall))
                        Text(
                            text = action.label,
                            style = MusicTheme.typography.titleMedium,
                            color = itemColor,
                        )
                    }
                }
            }
        }
    }
}
