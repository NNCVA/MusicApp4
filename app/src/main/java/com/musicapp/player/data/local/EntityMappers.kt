package com.musicapp.player.data.local

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.local.entity.PathRuleEntity
import com.musicapp.player.data.local.entity.PlayHistoryEntity
import com.musicapp.player.data.local.entity.PlaylistEntity
import com.musicapp.player.data.local.entity.PlaylistTrackEntity
import com.musicapp.player.data.local.entity.TrackEntity

internal fun Track.toEntity() = TrackEntity(
    volumeName = id.volumeName,
    mediaStoreId = id.mediaStoreId,
    title = title,
    artistName = artistName,
    artistMediaStoreId = artistId?.mediaStoreId,
    albumTitle = albumTitle,
    albumVolumeName = albumId?.volumeName,
    albumMediaStoreId = albumId?.mediaStoreId,
    durationMs = durationMs,
    dateAddedMs = dateAddedMs,
    dateModifiedMs = dateModifiedMs,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    availability = availability.name,
    lastSeenSyncGeneration = 0,
)

internal fun TrackEntity.toDomain() = Track(
    id = TrackId(volumeName, mediaStoreId),
    title = title,
    artistName = artistName,
    artistId = artistMediaStoreId?.let(::ArtistId),
    albumTitle = albumTitle,
    albumId = albumMediaStoreId?.let { AlbumId(checkNotNull(albumVolumeName), it) },
    durationMs = durationMs,
    dateAddedMs = dateAddedMs,
    dateModifiedMs = dateModifiedMs,
    relativePath = relativePath,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    availability = Availability.valueOf(availability),
)

internal fun PathRuleEntity.toDomain() = PathRule(
    id = PathRuleId(pathRuleId),
    volumeName = volumeName,
    directory = directory,
    kind = PathRuleKind.valueOf(kind),
)

internal fun PlayHistoryEntity.toDomain() = PlayHistory(
    trackId = TrackId(trackVolumeName, trackMediaStoreId),
    lastPlayedAtMs = lastPlayedAtMs,
    playCount = playCount,
)

internal fun PlaylistEntity.toDomain(relations: List<PlaylistTrackEntity>) = Playlist(
    id = PlaylistId(playlistId),
    displayName = displayName,
    normalizedName = normalizedName,
    trackIds = relations.sortedBy(PlaylistTrackEntity::position).map {
        TrackId(it.trackVolumeName, it.trackMediaStoreId)
    },
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)
