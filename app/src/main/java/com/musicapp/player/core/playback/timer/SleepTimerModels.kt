package com.musicapp.player.core.playback.timer

import java.util.Locale

data class SleepTimerStatus(
    val remainingMs: Long,
    val totalDurationMs: Long,
    val extendToEndOfTrack: Boolean,
    val isWaitingForTrackEnd: Boolean = false,
    val extendedElapsedMs: Long = 0L,
) {
    init {
        require(remainingMs >= 0) { "remainingMs must not be negative" }
        require(totalDurationMs >= 0) { "totalDurationMs must not be negative" }
        require(extendedElapsedMs >= 0) { "extendedElapsedMs must not be negative" }
    }

    fun formatRemainingTime(): String {
        return if (isWaitingForTrackEnd) {
            val totalSeconds = (extendedElapsedMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format(Locale.US, "+%02d:%02d", minutes, seconds)
        } else {
            val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

fun SleepTimerStatus?.formatRemainingTime(): String = this?.formatRemainingTime() ?: ""

