package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

const val CIRCULAR_RIPPLE_ICON_DISABLED_ALPHA = 0.38f

/**
 * 圆形水波纹图标按钮 (CircularRippleIconButton)
 *
 * 遵循交互规范：
 * 1. 触控热区裁剪为正圆形（CircleShape），水波纹严格限制在圆形边界内扩散（Bounded Circle）。
 * 2. 按压时无阴影或变暗效果，图标保持 100% 不透明度。
 * 3. 禁用状态 (enabled = false) 透明度降为 38% (0.38f)，且不响应点击与按压手势。
 */
@Composable
fun CircularRippleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer {
                alpha = if (enabled) 1.0f else CIRCULAR_RIPPLE_ICON_DISABLED_ALPHA
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
