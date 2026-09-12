package com.musicapp.player.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import kotlin.math.roundToInt

/**
 * 垂直细轨圆环滑块，专门用于均衡器频段调音台。
 *
 * 采用 2.5dp 细线轨道与镂空细圆环（平时 18dp，按压拖动时放大至 22dp），
 * 保证至少 48dp 触控热区并以 0dB 中心线双向高亮填充。
 */
@Composable
fun VerticalThinRingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = -10f..10f,
    onValueChangeFinished: (() -> Unit)? = null,
    activeTrackColor: Color = MusicTheme.colors.primary,
    inactiveTrackColor: Color = MusicTheme.colors.outlineVariant,
    thumbColor: Color = MusicTheme.colors.primary,
    thumbInnerColor: Color = MusicTheme.colors.surfaceContainerHigh,
    trackWidth: Dp = 2.5.dp,
    touchTargetWidth: Dp = MusicTheme.dimensions.minimumTouchTarget,
) {
    val density = LocalDensity.current
    var isInteracting by remember { mutableStateOf(false) }
    val animatedThumbDiameter by animateDpAsState(
        targetValue = if (isInteracting) 22.dp else 18.dp,
        label = "thumbDiameter",
    )

    val currentAlpha = if (enabled) 1f else MusicAlpha.Disabled
    val effectiveActiveColor = activeTrackColor.copy(alpha = currentAlpha)
    val effectiveInactiveColor = inactiveTrackColor.copy(alpha = currentAlpha)
    val effectiveThumbColor = thumbColor.copy(alpha = currentAlpha)

    Box(
        modifier = modifier
            .width(touchTargetWidth)
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange),
                    range = valueRange,
                )
                stateDescription = "%.1f dB".format(value)
                setProgress { target ->
                    if (enabled) {
                        onValueChange(target.coerceIn(valueRange))
                        onValueChangeFinished?.invoke()
                        true
                    } else {
                        false
                    }
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                val maxDiameterPx = with(density) { 22.dp.toPx() }
                val thumbRadiusPx = maxDiameterPx / 2f
                val rangeSpan = valueRange.endInclusive - valueRange.start

                fun computeValueFromY(y: Float, totalHeight: Float): Float {
                    val usableTop = thumbRadiusPx
                    val usableBottom = totalHeight - thumbRadiusPx
                    if (usableBottom <= usableTop) return valueRange.start
                    val clampedY = y.coerceIn(usableTop, usableBottom)
                    val fraction = 1f - ((clampedY - usableTop) / (usableBottom - usableTop))
                    return valueRange.start + fraction * rangeSpan
                }

                detectTapGestures(
                    onPress = { offset ->
                        isInteracting = true
                        val newVal = computeValueFromY(offset.y, size.height.toFloat())
                        onValueChange(newVal)
                        tryAwaitRelease()
                        isInteracting = false
                        onValueChangeFinished?.invoke()
                    },
                )
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                val maxDiameterPx = with(density) { 22.dp.toPx() }
                val thumbRadiusPx = maxDiameterPx / 2f
                val rangeSpan = valueRange.endInclusive - valueRange.start

                fun computeValueFromY(y: Float, totalHeight: Float): Float {
                    val usableTop = thumbRadiusPx
                    val usableBottom = totalHeight - thumbRadiusPx
                    if (usableBottom <= usableTop) return valueRange.start
                    val clampedY = y.coerceIn(usableTop, usableBottom)
                    val fraction = 1f - ((clampedY - usableTop) / (usableBottom - usableTop))
                    return valueRange.start + fraction * rangeSpan
                }

                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isInteracting = true
                        val newVal = computeValueFromY(offset.y, size.height.toFloat())
                        onValueChange(newVal)
                    },
                    onDragEnd = {
                        isInteracting = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isInteracting = false
                        onValueChangeFinished?.invoke()
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val newVal = computeValueFromY(change.position.y, size.height.toFloat())
                        onValueChange(newVal)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxHeight().width(touchTargetWidth)) {
            val maxDiameterPx = 22.dp.toPx()
            val thumbRadiusPx = maxDiameterPx / 2f
            val usableTop = thumbRadiusPx
            val usableBottom = size.height - thumbRadiusPx
            val trackXPx = size.width / 2f
            val trackStrokePx = trackWidth.toPx()
            val rangeSpan = valueRange.endInclusive - valueRange.start

            // 1. 全程底轨 (Inactive track)
            drawLine(
                color = effectiveInactiveColor,
                start = Offset(trackXPx, usableTop),
                end = Offset(trackXPx, usableBottom),
                strokeWidth = trackStrokePx,
                cap = StrokeCap.Round,
            )

            // 2. 中心 0dB 基准刻度线
            val zeroFraction = if (rangeSpan > 0f) {
                (0f - valueRange.start) / rangeSpan
            } else {
                0.5f
            }
            val zeroY = usableBottom - zeroFraction * (usableBottom - usableTop)
            val tickHalfWidth = 5.dp.toPx()
            drawLine(
                color = effectiveInactiveColor.copy(alpha = 0.8f),
                start = Offset(trackXPx - tickHalfWidth, zeroY),
                end = Offset(trackXPx + tickHalfWidth, zeroY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // 3. 活动高亮填充 (从 0dB 基准线向当前值延伸)
            val clampedVal = value.coerceIn(valueRange)
            val currentFraction = if (rangeSpan > 0f) {
                (clampedVal - valueRange.start) / rangeSpan
            } else {
                0.5f
            }
            val currentThumbY = usableBottom - currentFraction * (usableBottom - usableTop)

            if (clampedVal != 0f) {
                drawLine(
                    color = effectiveActiveColor,
                    start = Offset(trackXPx, zeroY),
                    end = Offset(trackXPx, currentThumbY),
                    strokeWidth = trackStrokePx,
                    cap = StrokeCap.Round,
                )
            }

            // 4. 细圆环 Thumb (外径 animatedThumbDiameter, 环宽 2dp, 内芯 thumbInnerColor)
            val thumbDiameterPx = animatedThumbDiameter.toPx()
            val currentRadiusPx = thumbDiameterPx / 2f
            val ringStrokePx = 2.dp.toPx()

            // 内部填充（镂空感透出表面底色）
            drawCircle(
                color = thumbInnerColor,
                radius = currentRadiusPx,
                center = Offset(trackXPx, currentThumbY),
            )
            // 外圈高亮细环
            drawCircle(
                color = effectiveThumbColor,
                radius = currentRadiusPx - (ringStrokePx / 2f),
                center = Offset(trackXPx, currentThumbY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringStrokePx),
            )
        }
    }
}

/**
 * 水平细轨圆环滑块，专门用于低音增强与环绕声场。
 *
 * 从 0.0dB 起始向右填充，采用 2.5dp 细线轨道与镂空细圆环。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizontalThinRingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..10f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    activeTrackColor: Color = MusicTheme.colors.primary,
    inactiveTrackColor: Color = MusicTheme.colors.outlineVariant,
    thumbColor: Color = MusicTheme.colors.primary,
    thumbInnerColor: Color = MusicTheme.colors.surfaceContainerHigh,
    trackHeight: Dp = 2.5.dp,
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.minimumTouchTarget),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        thumb = {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(thumbInnerColor)
                    .border(
                        width = 2.dp,
                        color = if (enabled) sliderColors.thumbColor else sliderColors.disabledThumbColor,
                        shape = CircleShape,
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(inactiveColor),
            ) {
                val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val fraction = if (span > 0f) {
                    ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(activeColor),
                )
            }
        },
    )
}
