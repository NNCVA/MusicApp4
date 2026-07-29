package com.musicapp.player.data.sync

import androidx.room.withTransaction
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.data.local.MusicDatabase
import com.musicapp.player.data.local.PlaybackSnapshotCodec
import com.musicapp.player.data.local.entity.MediaSyncStateEntity
import com.musicapp.player.data.local.entity.MediaVolumeSyncStateEntity
import com.musicapp.player.data.local.entity.TrackEntity
import com.musicapp.player.data.local.withoutTracks
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class MediaLibrarySyncCoordinator @Inject constructor(
    private val database: MusicDatabase,
    private val clock: Clock,
) {
    private val trackDao = database.trackDao()
    private val snapshotDao = database.playbackSnapshotDao()
    private val syncStateDao = database.mediaSyncStateDao()

    suspend fun synchronize(
        mode: MediaLibrarySyncMode,
        source: MediaLibraryScanSource,
    ): MediaLibrarySyncResult {
        val scan = try {
            source.queryMountedAudio()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            val reason = if (failure.hasSecurityCause()) {
                MediaLibrarySyncFailure.PERMISSION_LOST
            } else {
                MediaLibrarySyncFailure.QUERY_FAILED
            }
            return retainCacheAfterFailure(reason)
        }
        return commit(mode, scan)
    }

    suspend fun commit(
        mode: MediaLibrarySyncMode,
        scan: CompleteMediaLibraryScan,
    ): MediaLibrarySyncResult = database.withTransaction {
        val generation = nextGeneration()
        val nowMs = clock.currentTimeMillis().also {
            require(it >= 0) { "clock must not return a negative timestamp" }
        }
        val previousStates = syncStateDao.getVolumeStates().associateBy { it.volumeName }
        val knownVolumes = trackDao.getKnownVolumeNames().toSet() + previousStates.keys
        val unavailableVolumes = knownVolumes - scan.mountedVolumeNames

        unavailableVolumes.forEach { volumeName ->
            trackDao.updateAvailabilityForVolume(
                volumeName,
                Availability.TEMPORARILY_UNAVAILABLE.name,
            )
        }

        val incoming = scan.candidates.map { candidate -> candidate.toEntity(generation) }
        if (incoming.isNotEmpty()) {
            trackDao.upsert(incoming)
        }

        val removed = if (
            mode == MediaLibrarySyncMode.FULL && scan.mountedVolumeNames.isNotEmpty()
        ) {
            trackDao.getNotSeenInGeneration(
                volumeNames = scan.mountedVolumeNames.sorted(),
                generation = generation,
            )
        } else {
            emptyList()
        }
        if (removed.isNotEmpty()) {
            val removedIds = removed.mapTo(mutableSetOf()) {
                TrackId(it.volumeName, it.mediaStoreId)
            }
            snapshotDao.get()?.let(PlaybackSnapshotCodec::decode)?.let { snapshot ->
                snapshotDao.upsert(PlaybackSnapshotCodec.encode(snapshot.withoutTracks(removedIds)))
            }
            trackDao.delete(removed)
        }

        val allVolumes = knownVolumes + scan.mountedVolumeNames
        if (allVolumes.isNotEmpty()) {
            syncStateDao.upsertVolumeStates(
                allVolumes.sorted().map { volumeName ->
                    val previous = previousStates[volumeName]
                    val mounted = volumeName in scan.mountedVolumeNames
                    MediaVolumeSyncStateEntity(
                        volumeName = volumeName,
                        availability = if (mounted) {
                            Availability.AVAILABLE.name
                        } else {
                            Availability.TEMPORARILY_UNAVAILABLE.name
                        },
                        lastSuccessfulGeneration = if (mounted) {
                            generation
                        } else {
                            previous?.lastSuccessfulGeneration
                        },
                        lastCompleteGeneration = if (
                            mounted && mode == MediaLibrarySyncMode.FULL
                        ) {
                            generation
                        } else {
                            previous?.lastCompleteGeneration
                        },
                        mediaStoreVersion = scan.volumeSignatures[volumeName]
                            ?: previous?.mediaStoreVersion,
                        updatedAtMs = nowMs,
                    )
                },
            )
        }

        MediaLibrarySyncResult(
            generation = generation,
            upsertedTrackCount = incoming.size,
            removedTrackCount = removed.size,
            temporarilyUnavailableVolumeNames = unavailableVolumes,
        )
    }

    suspend fun retainCacheAfterFailure(
        failure: MediaLibrarySyncFailure,
    ): MediaLibrarySyncResult = database.withTransaction {
        val generation = syncStateDao.getGenerationOrNull() ?: 0
        val nowMs = clock.currentTimeMillis().also {
            require(it >= 0) { "clock must not return a negative timestamp" }
        }
        val previousStates = syncStateDao.getVolumeStates().associateBy { it.volumeName }
        val knownVolumes = trackDao.getKnownVolumeNames().toSet() + previousStates.keys
        trackDao.updateAllAvailability(Availability.TEMPORARILY_UNAVAILABLE.name)
        if (knownVolumes.isNotEmpty()) {
            syncStateDao.upsertVolumeStates(
                knownVolumes.sorted().map { volumeName ->
                    val previous = previousStates[volumeName]
                    MediaVolumeSyncStateEntity(
                        volumeName = volumeName,
                        availability = Availability.TEMPORARILY_UNAVAILABLE.name,
                        lastSuccessfulGeneration = previous?.lastSuccessfulGeneration,
                        lastCompleteGeneration = previous?.lastCompleteGeneration,
                        mediaStoreVersion = previous?.mediaStoreVersion,
                        updatedAtMs = nowMs,
                    )
                },
            )
        }
        MediaLibrarySyncResult(
            generation = generation,
            upsertedTrackCount = 0,
            removedTrackCount = 0,
            temporarilyUnavailableVolumeNames = knownVolumes,
            failure = failure,
        )
    }

    private suspend fun nextGeneration(): Long {
        syncStateDao.insertGlobalStateIfAbsent(MediaSyncStateEntity())
        syncStateDao.incrementGeneration()
        return checkNotNull(syncStateDao.getGenerationOrNull())
    }
}

private fun MediaAudioCandidate.toEntity(generation: Long): TrackEntity {
    val stableIdentityText = mediaStoreId.toString()
    val normalizedDisplayName = displayName.trim().ifBlank { stableIdentityText }
    val fallbackTitle = normalizedDisplayName.substringBeforeLast('.').ifBlank { stableIdentityText }
    val normalizedAlbumId = albumId?.takeIf { it > 0 }
    return TrackEntity(
        volumeName = volumeName,
        mediaStoreId = mediaStoreId,
        title = title.normalizedOrNull() ?: fallbackTitle,
        artistName = artistName.normalizedOrNull() ?: UNKNOWN_ARTIST,
        artistMediaStoreId = artistId?.takeIf { it > 0 },
        albumTitle = albumTitle.normalizedOrNull(),
        albumVolumeName = normalizedAlbumId?.let { volumeName },
        albumMediaStoreId = normalizedAlbumId,
        durationMs = durationMs,
        dateAddedMs = dateAddedMs,
        dateModifiedMs = dateModifiedMs,
        relativePath = relativeDirectory,
        displayName = normalizedDisplayName,
        mimeType = mimeType.normalizedOrNull(),
        sizeBytes = sizeBytes,
        availability = Availability.AVAILABLE.name,
        lastSeenSyncGeneration = generation,
    )
}

private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.hasSecurityCause(): Boolean =
    generateSequence(this) { it.cause }.any { it is SecurityException }

private const val UNKNOWN_ARTIST = "<unknown>"
