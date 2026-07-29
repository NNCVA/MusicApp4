package com.musicapp.player.core.domain

import java.text.Normalizer
import java.util.Locale

data class Playlist(
  val id: PlaylistId,
  val name: String,
  val normalizedName: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
) {
  init {
    require(name.isNotBlank()) { "name must not be blank" }
    require(normalizedName.isNotBlank()) { "normalizedName must not be blank" }
    require(createdAtMs >= 0L) { "createdAtMs must be non-negative" }
    require(updatedAtMs >= createdAtMs) { "updatedAtMs must not precede createdAtMs" }
  }
}

sealed interface PlaylistNameResult {
  data class Valid(
    val displayName: String,
    val comparisonKey: String,
  ) : PlaylistNameResult

  data object Blank : PlaylistNameResult

  data class InvalidLength(val codePointCount: Int) : PlaylistNameResult
}

object PlaylistNameRules {
  const val MAX_CODE_POINTS = 50

  fun normalize(rawName: String): PlaylistNameResult {
    val normalized = Normalizer.normalize(rawName.trim(), Normalizer.Form.NFC)
    if (normalized.isEmpty()) return PlaylistNameResult.Blank
    val count = normalized.codePointCount(0, normalized.length)
    if (count !in 1..MAX_CODE_POINTS) return PlaylistNameResult.InvalidLength(count)
    return PlaylistNameResult.Valid(
      displayName = normalized,
      comparisonKey = normalized.lowercase(Locale.ROOT),
    )
  }

  fun conflicts(candidate: PlaylistNameResult.Valid, existingNames: Iterable<String>): Boolean =
    existingNames.any { existing ->
      val normalized = normalize(existing)
      normalized is PlaylistNameResult.Valid && normalized.comparisonKey == candidate.comparisonKey
    }
}

data class BatchAddResult(
  val addedCount: Int,
  val skippedCount: Int,
) {
  init {
    require(addedCount >= 0) { "addedCount must be non-negative" }
    require(skippedCount >= 0) { "skippedCount must be non-negative" }
  }
}

data class BatchAddSelection(
  val tracksToAdd: List<TrackId>,
  val result: BatchAddResult,
)

object PlaylistBatchRules {
  fun selectNewTracks(
    existingTrackIds: Collection<TrackId>,
    selectedTrackIds: Iterable<TrackId>,
  ): BatchAddSelection {
    val seen = existingTrackIds.toMutableSet()
    val additions = mutableListOf<TrackId>()
    var skipped = 0
    selectedTrackIds.forEach { trackId ->
      if (seen.add(trackId)) additions += trackId else skipped++
    }
    return BatchAddSelection(additions, BatchAddResult(additions.size, skipped))
  }
}
