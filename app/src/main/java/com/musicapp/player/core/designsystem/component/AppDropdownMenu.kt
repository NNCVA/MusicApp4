package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.musicapp.player.theme.MusicTheme

/**
 * 通用行级与页级下拉管理菜单。
 *
 * 遵循设计系统规范：
 * - 移除容器内部默认的 8dp 垂直 Padding，使首项与末项的高亮完全贴合卡片顶部和底部；
 * - 外层 Surface 配合显式 `.clip(shape)`，使首末菜单项按压/选中高亮自动沿用外层圆角；
 * - 菜单项保证至少 48dp 触控热区与规范水波纹反馈；
 * - 风格与通用弹窗和消息气泡保持统一。
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MusicTheme.shapes.large,
    containerColor: Color = MusicTheme.colors.surface,
    tonalElevation: Dp = MusicTheme.dimensions.spaceExtraSmall,
    shadowElevation: Dp = MusicTheme.dimensions.spaceExtraSmall,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (expanded) {
        val density = LocalDensity.current
        val popupPositionProvider =
            remember(offset, density) {
                AppDropdownMenuPositionProvider(offset, density)
            }
        Popup(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties,
        ) {
            Surface(
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                border = border,
                modifier =
                    modifier
                        .widthIn(min = 128.dp, max = 280.dp)
                        .width(IntrinsicSize.Max)
                        .clip(shape),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 下拉菜单项组件。
 */
@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = MusicTheme.dimensions.spaceMedium),
) {
    val dimensions = MusicTheme.dimensions
    val contentColor =
        when {
            !enabled -> MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.38f)
            isDestructive -> MusicTheme.colors.error
            else -> MusicTheme.colors.onSurface
        }
    val iconColor =
        when {
            !enabled -> MusicTheme.colors.onSurfaceVariant.copy(alpha = 0.38f)
            isDestructive -> MusicTheme.colors.error
            else -> MusicTheme.colors.onSurfaceVariant
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.minimumTouchTarget)
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                    role = Role.Button,
                )
                .semantics(mergeDescendants = true) {}
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(
                LocalContentColor provides iconColor,
            ) {
                Box(
                    modifier = Modifier.size(dimensions.spaceLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingIcon()
                }
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalTextStyle provides MusicTheme.typography.bodyLarge,
            ) {
                text()
            }
        }
        if (trailingIcon != null) {
            CompositionLocalProvider(
                LocalContentColor provides iconColor,
            ) {
                Box(
                    modifier = Modifier.size(dimensions.spaceLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * 下拉菜单分割线组件。
 */
@Composable
fun AppDropdownMenuDivider(
    modifier: Modifier = Modifier,
    color: Color = MusicTheme.colors.outlineVariant.copy(alpha = 0.5f),
    thickness: Dp = 1.dp,
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        color = color,
        thickness = thickness,
    )
}

@Immutable
internal data class AppDropdownMenuPositionProvider(
    val contentOffset: DpOffset,
    val density: Density,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val contentOffsetPxX = with(density) { contentOffset.x.roundToPx() }
        val contentOffsetPxY = with(density) { contentOffset.y.roundToPx() }

        val x =
            if (layoutDirection == LayoutDirection.Ltr) {
                val rightAligned = anchorBounds.right - popupContentSize.width - contentOffsetPxX
                val leftAligned = anchorBounds.left + contentOffsetPxX
                if (rightAligned >= 0 && rightAligned + popupContentSize.width <= windowSize.width) {
                    rightAligned
                } else if (leftAligned + popupContentSize.width <= windowSize.width) {
                    leftAligned
                } else {
                    (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                }
            } else {
                val leftAligned = anchorBounds.left + contentOffsetPxX
                if (leftAligned >= 0 && leftAligned + popupContentSize.width <= windowSize.width) {
                    leftAligned
                } else {
                    anchorBounds.right - popupContentSize.width - contentOffsetPxX
                }
            }

        val spaceBelow = windowSize.height - anchorBounds.bottom
        val spaceAbove = anchorBounds.top
        val y =
            if (spaceBelow >= popupContentSize.height || spaceBelow >= spaceAbove) {
                (anchorBounds.bottom + contentOffsetPxY).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
            } else {
                (anchorBounds.top - popupContentSize.height - contentOffsetPxY).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
            }

        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y)
    }
}
