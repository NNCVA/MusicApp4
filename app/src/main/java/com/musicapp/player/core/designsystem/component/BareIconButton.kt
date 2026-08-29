package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.musicapp.player.theme.MusicTheme

const val BARE_ICON_PRESSED_ALPHA = 0.60f
const val BARE_ICON_PRESS_DURATION_MS = 60
const val BARE_ICON_RELEASE_DURATION_MS = 120

/**
 * 裸图标按钮 (BareIconButton)
 *
 * 遵循交互规范：
 * 1. 触控区保持透明、尺寸为 48×48dp (minimumTouchTarget)，不叠加可见 Ripple。
 * 2. 内部图标在按压时从 alpha 1.0 在 60ms 内降至 0.60。
 * 3. 松手、移出按钮、开始滚动或手势被接管时，在 120ms 内恢复至 alpha 1.0。
 * 4. 禁用状态下不响应点击与按压手势。
 */
@Composable
fun BareIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) BARE_ICON_PRESSED_ALPHA else 1.0f,
        animationSpec = tween(
            durationMillis = if (isPressed && enabled) BARE_ICON_PRESS_DURATION_MS else BARE_ICON_RELEASE_DURATION_MS,
            easing = LinearEasing,
        ),
        label = "BareIconButtonAlpha",
    )

    val dimensions = MusicTheme.dimensions
    Box(
        modifier = modifier
            .size(dimensions.minimumTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer {
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
