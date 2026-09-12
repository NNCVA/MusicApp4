package com.musicapp.player.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicapp.player.core.domain.model.EqualizerBand
import com.musicapp.player.theme.MusicAlpha
import com.musicapp.player.theme.MusicTheme
import kotlin.math.roundToInt

/**
 * 垂直频段调音台组件。
 *
 * 横轴为频段中心频率，纵轴为分贝增益（-10dB 到 +10dB）。
 * 包含左侧刻度标记（+10dB, +5dB, 0dB, -5dB, -10dB）、水平辅助线、顶部实时分贝读数与底部频率标签。
 */
@Composable
fun VerticalEqualizerConsole(
    bands: List<EqualizerBand>,
    enabled: Boolean,
    onBandLevelChange: (bandIndex: Int, levelMb: Int) -> Unit,
    modifier: Modifier = Modifier,
    sliderHeight: Dp = 190.dp,
    valueRange: ClosedFloatingPointRange<Float> = -10f..10f,
) {
    val dimensions = MusicTheme.dimensions
    val primaryColor = MusicTheme.colors.primary
    val outlineVariant = MusicTheme.colors.outlineVariant
    val onSurfaceVariant = MusicTheme.colors.onSurfaceVariant
    val currentAlpha = if (enabled) 1f else MusicAlpha.Disabled

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimensions.spaceSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧刻度数值列 (+10dB, +5dB, 0dB, -5dB, -10dB)
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .padding(top = 24.dp, bottom = 24.dp) // 预留顶部与底部文字高度
                        .height(sliderHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "+10",
                        style = MusicTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = currentAlpha * 0.7f),
                    )
                    Text(
                        text = "+5",
                        style = MusicTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = currentAlpha * 0.7f),
                    )
                    Text(
                        text = "0",
                        style = MusicTheme.typography.labelSmall,
                        color = if (enabled) primaryColor else onSurfaceVariant.copy(alpha = currentAlpha),
                    )
                    Text(
                        text = "-5",
                        style = MusicTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = currentAlpha * 0.7f),
                    )
                    Text(
                        text = "-10",
                        style = MusicTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = currentAlpha * 0.7f),
                    )
                }

                Spacer(modifier = Modifier.width(dimensions.spaceExtraSmall))

                // 右侧频段垂直调音台容器（含贯穿水平参考线）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // 背景水平参考辅助线
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 24.dp)
                            .height(sliderHeight),
                    ) {
                        val maxDiameterPx = 22.dp.toPx()
                        val thumbRadiusPx = maxDiameterPx / 2f
                        val usableTop = thumbRadiusPx
                        val usableBottom = size.height - thumbRadiusPx
                        val totalSpan = usableBottom - usableTop

                        val guideColor = outlineVariant.copy(alpha = currentAlpha * 0.4f)
                        val centerLineColor = primaryColor.copy(alpha = currentAlpha * 0.5f)
                        val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                        // +10 dB line
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, usableTop),
                            end = Offset(size.width, usableTop),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashPathEffect,
                        )
                        // +5 dB line
                        val plus5Y = usableTop + totalSpan * 0.25f
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, plus5Y),
                            end = Offset(size.width, plus5Y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashPathEffect,
                        )
                        // 0 dB center reference line (高亮实线)
                        val zeroY = usableTop + totalSpan * 0.5f
                        drawLine(
                            color = centerLineColor,
                            start = Offset(0f, zeroY),
                            end = Offset(size.width, zeroY),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        // -5 dB line
                        val minus5Y = usableTop + totalSpan * 0.75f
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, minus5Y),
                            end = Offset(size.width, minus5Y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashPathEffect,
                        )
                        // -10 dB line
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, usableBottom),
                            end = Offset(size.width, usableBottom),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashPathEffect,
                        )
                    }

                    // 频段滑块列 (一列一个频段)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        bands.forEach { band ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 顶部当前分贝读数
                                Text(
                                    text = formatBandDb(band.levelMb),
                                    style = MusicTheme.typography.labelSmall,
                                    color = if (enabled) {
                                        if (band.levelMb != 0) primaryColor else onSurfaceVariant
                                    } else {
                                        onSurfaceVariant.copy(alpha = currentAlpha)
                                    },
                                    maxLines = 1,
                                    modifier = Modifier.height(24.dp),
                                )

                                // 中间垂直滑块
                                Box(
                                    modifier = Modifier.height(sliderHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val currentDb = (band.levelMb / 100.0f).coerceIn(valueRange)
                                    VerticalThinRingSlider(
                                        value = currentDb,
                                        onValueChange = { newDb ->
                                            val newLevelMb = (newDb * 100f).roundToInt()
                                            onBandLevelChange(band.index, newLevelMb)
                                        },
                                        enabled = enabled,
                                        valueRange = valueRange,
                                    )
                                }

                                // 底部频率标签
                                Text(
                                    text = formatCenterFrequency(band.centerFreqHz),
                                    style = MusicTheme.typography.labelMedium,
                                    color = if (enabled) {
                                        MusicTheme.colors.onSurface
                                    } else {
                                        onSurfaceVariant.copy(alpha = currentAlpha)
                                    },
                                    maxLines = 1,
                                    modifier = Modifier.height(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatCenterFrequency(freqHz: Int): String {
    return if (freqHz >= 1000) {
        val kHz = freqHz / 1000.0
        if (freqHz % 1000 == 0) "${freqHz / 1000}k" else "%.1fk".format(kHz)
    } else {
        "$freqHz"
    }
}

internal fun formatBandDb(levelMb: Int): String {
    val db = levelMb / 100.0
    return if (db > 0.04) {
        "+%.1f".format(db)
    } else if (db < -0.04) {
        "%.1f".format(db)
    } else {
        "0.0"
    }
}
