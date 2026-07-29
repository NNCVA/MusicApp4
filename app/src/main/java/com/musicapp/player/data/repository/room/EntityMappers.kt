package com.musicapp.player.data.repository.room

import com.musicapp.player.core.domain.AlbumId
import com.musicapp.player.core.domain.ArtistId
import com.musicapp.player.core.domain.PathRule
import com.musicapp.player.core.domain.PathRuleId
import com.musicapp.player.core.domain.PathRuleType
import com.musicapp.player.core.domain.PlayHistory
import com.musicapp.player.core.domain.PlayInstanceId
import com.musicapp.player.core.domain.PlayInstanceProgress
import com.musicapp.player.core.domain.PlaybackMode
import com.musicapp.player.core.domain.PlaybackQueueJsonCodec
import com.musicapp.player.core.domain.PlaybackSnapshot
import com.musicapp.player.core.domain.Playlist
import com.musicapp.player.core.domain.PlaylistId
import com.musicapp.player.core.domain.QueueCodecResult
import com.musicapp.player.core.domain.Track
import com.musicapp.player.core.domain.TrackId
import com.musicapp.player.data.local.entity.PathRuleEntity
import com.musicapp.player.data.local.entity.PlayHistoryEntity
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import com.musicapp.player.data.local.entity.PlaylistEntity
import com.musicapp.player.data.local.entity.TrackEntity

internal fun TrackEntity.toDomainOrNull(): Track? = runCatching {
  Track(
    id = TrackId(volumeName, mediaStoreId),
    contentUri = contentUri,
    title = title,
    displayName = displayName,
    extension = extension,
    artistId = artistId?.let(::ArtistId),
    artistName = artistName,
    albumId = albumId?.let { AlbumId(volumeName, it) },
    albumName = albumName,
    durationMs = durationMillis,
    dateAddedMs = Math.multiplyExact(dateAddedEpochSeconds, MILLIS_PER_SECOND),
    modifiedAtMs = Math.multiplyExact(dateModifiedEpochSeconds, MILLIS_PER_SECOND),
    relativePath = relativePath.orEmpty(),
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    isAvailable = isAvailable,
  )
}.getOrNull()

internal fun Track.toEntity(generation: Long): TrackEntity =
  TrackEntity(
    volumeName = id.volumeName,
    mediaStoreId = id.mediaStoreId,
    contentUri = contentUri,
    displayName = displayName,
    title = title,
    artistId = artistId?.mediaStoreArtistId,
    artistName = artistName,
    albumId = albumId?.mediaStoreAlbumId,
    albumName = albumName,
    dateAddedEpochSeconds = dateAddedMs / MILLIS_PER_SECOND,
    durationMillis = durationMs,
    relativePath = relativePath,
    mimeType = mimeType,
    extension = extension,
    sizeBytes = sizeBytes,
    dateModifiedEpochSeconds = modifiedAtMs / MILLIS_PER_SECOND,
    isAvailable = isAvailable,
    lastSeenGeneration = generation,
  )

internal fun PlaylistEntity.toDomainOrNull(): Playlist? = runCatching {
  Playlist(
    id = PlaylistId(playlistId),
    name = displayName,
    normalizedName = nameComparisonKey,
    createdAtMs = createdAtEpochMillis,
    updatedAtMs = updatedAtEpochMillis,
  )
}.getOrNull()

internal fun Playlist.toEntity(): PlaylistEntity =
  PlaylistEntity(
    playlistId = id.value,
    displayName = name,
    nameComparisonKey = normalizedName,
    createdAtEpochMillis = createdAtMs,
    updatedAtEpochMillis = updatedAtMs,
  )

internal fun PlayHistoryEntity.toDomainOrNull(): PlayHistory? = runCatching {
  PlayHistory(
    trackId = TrackId(trackVolumeName, trackMediaStoreId),
    lastPlayedAtMs = lastPlayedAtEpochMillis,
    playCount = playCount,
  )
}.getOrNull()

internal fun PlayHistory.toEntity(): PlayHistoryEntity =
  PlayHistoryEntity(
    trackVolumeName = trackId.volumeName,
    trackMediaStoreId = trackId.mediaStoreId,
    lastPlayedAtEpochMillis = lastPlayedAtMs,
    playCount = playCount,
  )

internal fun PathRuleEntity.toDomainOrNull(): PathRule? = runCatching {
  PathRule(
    id = PathRuleId(pathRuleId),
    volumeName = volumeName,
    relativeDirectory = relativePath,
    type = PathRuleType.valueOf(ruleType),
  )
}.getOrNull()

internal fun PathRule.toEntity(createdAtEpochMillis: Long): PathRuleEntity =
  PathRuleEntity(
    pathRuleId = id.value,
    volumeName = volumeName,
    relativePath = relativeDirectory,
    ruleType = type.name,
    createdAtEpochMillis = createdAtEpochMillis,
  )

internal fun PlaybackSnapshot.toEntity(): PlaybackSnapshotEntity =
  PlaybackSnapshotEntity(
    formatVersion = PlaybackQueueJsonCodec.FORMAT_VERSION,
    originalQueueJson = PlaybackQueueJsonCodec.encode(queues.originalQueue),
    shuffledQueueJson = PlaybackQueueJsonCodec.encode(queues.stableRandomQueue),
    playMode = mode.name,
    currentTrackVolumeName = currentTrackId?.volumeName,
    currentTrackMediaStoreId = currentTrackId?.mediaStoreId,
    currentQueueIndex = currentQueueIndex,
    positionMs = positionMs,
    playbackInstanceId = playInstance?.id?.value,
    accumulatedPlayedMs = playInstance?.accumulatedPlayedMs ?: 0L,
    historyRecordedForInstance = playInstance?.historyRecorded ?: false,
  )

internal fun PlaybackSnapshotEntity.toDomainOrNull(): PlaybackSnapshot? {
  if (snapshotId != PlaybackSnapshotEntity.ACTIVE_SNAPSHOT_ID) return null
  if (formatVersion != PlaybackQueueJsonCodec.FORMAT_VERSION) return null
  val queues =
    when (val decoded = PlaybackQueueJsonCodec.decodeBoth(originalQueueJson, shuffledQueueJson)) {
      is QueueCodecResult.Success -> decoded.queues
      is QueueCodecResult.Corrupt -> return null
    }
  return runCatching {
    val currentTrackId =
      when {
        currentTrackVolumeName == null && currentTrackMediaStoreId == null -> null
        currentTrackVolumeName != null && currentTrackMediaStoreId != null ->
          TrackId(currentTrackVolumeName, currentTrackMediaStoreId)
        else -> error("Partial current TrackId")
      }
    val playInstance =
      playbackInstanceId?.let {
        PlayInstanceProgress(
          id = PlayInstanceId(it),
          accumulatedPlayedMs = accumulatedPlayedMs,
          historyRecorded = historyRecordedForInstance,
        )
      }
    PlaybackSnapshot(
      queues = queues,
      currentTrackId = currentTrackId,
      currentQueueIndex = currentQueueIndex,
      mode = PlaybackMode.valueOf(playMode),
      positionMs = positionMs,
      playInstance = playInstance,
    )
  }.getOrNull()
}

private const val MILLIS_PER_SECOND = 1_000L
