package com.musicapp.player.core.domain

data class PlayHistory(
  val trackId: TrackId,
  val lastPlayedAtMs: Long,
  val playCount: Long,
) {
  init {
    require(lastPlayedAtMs >= 0L) { "lastPlayedAtMs must be non-negative" }
    require(playCount > 0L) { "playCount must be positive" }
  }
}

data class PlayInstanceProgress(
  val id: PlayInstanceId,
  val accumulatedPlayedMs: Long,
  val historyRecorded: Boolean,
) {
  init {
    require(accumulatedPlayedMs >= 0L) { "accumulatedPlayedMs must be non-negative" }
  }
}

object PlaybackHistoryRules {
  const val MAX_THRESHOLD_MS = 30_000L

  fun thresholdMs(trackDurationMs: Long): Long {
    require(trackDurationMs > 0L) { "trackDurationMs must be positive" }
    return minOf(MAX_THRESHOLD_MS, trackDurationMs / 2L + trackDurationMs % 2L)
  }

  fun shouldRecord(trackDurationMs: Long, progress: PlayInstanceProgress): Boolean =
    !progress.historyRecorded && progress.accumulatedPlayedMs >= thresholdMs(trackDurationMs)
}
