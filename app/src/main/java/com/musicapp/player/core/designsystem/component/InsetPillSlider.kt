package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme

/**
 * 普通数值设置使用的内嵌胶囊滑块。
 *
 * 轨道是统一的胶囊背景，活动部分在同一裁剪区域内填充；Thumb 直径不大于轨道高度，
 * 因而不会越过轨道上下边界。交互与无障碍语义仍由 Material Slider 承载，外部热区保持
 * [MusicTheme.dimensions.minimumTouchTarget]。
 */
@Composable
fun InsetPillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    activeTrackColor: Color = MusicTheme.colors.primary,
    inactiveTrackColor: Color = MusicTheme.colors.outlineVariant,
    thumbColor: Color = Color.White,
) {
    val dimensions = MusicTheme.dimensions
    val sliderColors = SliderDefaults.colors(
        thumbColor = thumbColor,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
        disabledThumbColor = thumbColor.copy(alpha = MusicAlpha.Disabled),
        disabledActiveTrackColor = activeTrackColor.copy(alpha = MusicAlpha.Disabled),
        disabledInactiveTrackColor = inactiveTrackColor.copy(alpha = MusicAlpha.Disabled),
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = dimensions.minimumTouchTarget),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        thumb = {
            Box(
                modifier = Modifier
                    .size(dimensions.sliderThumbDiameter)
                    .clip(CircleShape)
                    .background(
                        if (enabled) sliderColors.thumbColor else sliderColors.disabledThumbColor,
                    ),
            )
        },
        track = { sliderState ->
            val activeColor = if (enabled) {
                sliderColors.activeTrackColor
            } else {
                sliderColors.disabledActiveTrackColor
            }
            val inactiveColor = if (enabled) {
                sliderColors.inactiveTrackColor
            } else {
                sliderColors.disabledInactiveTrackColor
            }
            val activeAlignment = if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.sliderTrackHeight)
                    .clip(MusicTheme.shapes.pill)
                    .background(inactiveColor),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(sliderState.coercedValueAsFraction)
                        .align(activeAlignment)
                        .background(activeColor),
                )
            }
        },
    )
}
