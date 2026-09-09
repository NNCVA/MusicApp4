package com.musicapp.player.feature.settings

import com.musicapp.player.core.domain.model.AppSettings
import kotlin.math.roundToInt

/**
 * Snaps the settings-page fade duration to the domain's 250 ms unit and clamps it to the
 * supported range. Keeping this conversion outside the composable makes click, drag and
 * accessibility updates use the same rule.
 */
internal fun snapFadeThroughDurationMs(rawValue: Float): Long {
    val minimum = AppSettings.MIN_FADE_THROUGH_DURATION_MS
    val maximum = AppSettings.MAX_FADE_THROUGH_DURATION_MS
    val step = AppSettings.FADE_THROUGH_STEP_MS
    val bounded = rawValue
        .takeUnless { it.isNaN() }
        ?.coerceIn(minimum.toFloat(), maximum.toFloat())
        ?: minimum.toFloat()
    val stepIndex = ((bounded - minimum.toFloat()) / step.toFloat()).roundToInt()
    return (minimum + stepIndex * step).coerceIn(minimum, maximum)
}

internal val fadeThroughSliderSteps: Int
    get() = (
        (AppSettings.MAX_FADE_THROUGH_DURATION_MS - AppSettings.MIN_FADE_THROUGH_DURATION_MS) /
            AppSettings.FADE_THROUGH_STEP_MS
        ).toInt() - 1
