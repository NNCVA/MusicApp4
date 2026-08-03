package com.musicapp.player.core.domain.policy

import kotlin.math.min

object PlayHistoryThreshold {
    private const val MAX_THRESHOLD_MILLIS = 30_000L

    fun thresholdMillis(durationMillis: Long): Long {
        require(durationMillis > 0) { "durationMillis must be greater than zero" }
        val halfDurationRoundedUp = durationMillis / 2 + durationMillis % 2
        return min(MAX_THRESHOLD_MILLIS, halfDurationRoundedUp)
    }

    fun isReached(durationMillis: Long, actualPlaybackMillis: Long): Boolean =
        actualPlaybackMillis >= thresholdMillis(durationMillis)
}
