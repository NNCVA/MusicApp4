package com.musicapp.player.data.repository

import androidx.room.withTransaction
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.local.MusicDatabase
import com.musicapp.player.data.local.PlaybackSnapshotCodec
import com.musicapp.player.data.local.entity.HiddenTrackEntity
import com.musicapp.player.data.local.entity.PathRuleEntity
import com.musicapp.player.data.local.entity.PlayHistoryEntity
import com.musicapp.player.data.local.entity.PlaylistEntity
import com.musicapp.player.data.local.entity.PlaylistTrackEntity
import com.musicapp.player.data.local.toDomain
import com.musicapp.player.data.local.toEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class RoomMediaLibraryRepository @Inject constructor(
    private val database: MusicDatabase,
) : MediaLibraryRepository {
    private val trackDao = database.trackDao()
    private val hiddenTrackDao = database.hiddenTrackDao()
    private val pathRuleDao = database.pathRuleDao()
    private val snapshotDao = database.playbackSnapshotDao()

    override fun observeTracks(includeHidden: Boolean): Flow<List<Track>> =
        (if (includeHidden) trackDao.observeAll() else trackDao.observeVisible())
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeAlbumTracks(albumId: AlbumId): Flow<List<Track>> =
        trackDao.observeAlbumTracks(albumId.volumeName, albumId.mediaStoreId)
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeArtistTracks(artistId: ArtistId): Flow<List<Track>> =
        trackDao.observeArtistTracks(artistId.mediaStoreId)
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeFolderTracks(volumeName: String, directoryPrefix: String): Flow<List<Track>> =
        trackDao.observeFolderTracks(volumeName.requireNonBlank("volumeName"), directoryPrefix.escapeSqlLike())
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTrack(trackId: TrackId): Track? =
        trackDao.get(trackId.volumeName, trackId.mediaStoreId)?.toDomain()

    override suspend fun mergeTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        require(tracks.map(Track::id).distinct().size == tracks.size) {
            "tracks must have unique TrackIds"
        }
        database.withTransaction { trackDao.upsert(tracks.map(Track::toEntity)) }
    }

    override suspend fun replaceTracksForVolume(volumeName: String, tracks: List<Track>) {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        if (tracks.isEmpty()) return
        require(tracks.all { it.id.volumeName == volumeName }) {
            "every track must belong to volumeName"
        }
        require(tracks.map(Track::id).distinct().size == tracks.size) {
            "tracks must have unique TrackIds"
        }
        database.withTransaction {
            val oldTracks = trackDao.getForVolume(volumeName)
            val incomingIds = tracks.map(Track::id).toSet()
            val removedIds = oldTracks.map { TrackId(it.volumeName, it.mediaStoreId) }
                .filterNot(incomingIds::contains)
                .toSet()
            trackDao.upsert(tracks.map(Track::toEntity))
            if (removedIds.isNotEmpty()) {
                trackDao.delete(oldTracks.filter { TrackId(it.volumeName, it.mediaStoreId) in removedIds })
                snapshotDao.get()?.let(PlaybackSnapshotCodec::decode)?.let { snapshot ->
                    snapshotDao.upsert(PlaybackSnapshotCodec.encode(snapshot.withoutTracks(removedIds)))
                }
            }
        }
    }

    override suspend fun setVolumeAvailability(volumeName: String, availability: Availability) {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        trackDao.updateAvailabilityForVolume(volumeName, availability.name)
    }

    override suspend fun setHidden(trackId: TrackId, hidden: Boolean, changedAtMs: Long) {
        require(changedAtMs >= 0) { "changedAtMs must not be negative" }
        database.withTransaction {
            require(trackDao.exists(trackId.volumeName, trackId.mediaStoreId)) { "track does not exist" }
            if (hidden) {
                hiddenTrackDao.insert(
                    HiddenTrackEntity(trackId.volumeName, trackId.mediaStoreId, changedAtMs),
                )
            } else {
                hiddenTrackDao.delete(trackId.volumeName, trackId.mediaStoreId)
            }
        }
    }

    override fun observePathRules(): Flow<List<PathRule>> =
        pathRuleDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addPathRule(
        volumeName: String,
        directory: String,
        kind: PathRuleKind,
    ): PathRule = database.withTransaction {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        val id = pathRuleDao.insert(
            PathRuleEntity(volumeName = volumeName, directory = directory, kind = kind.name),
        )
        checkNotNull(pathRuleDao.getAll().firstOrNull { it.pathRuleId == id }).toDomain()
    }

    override suspend fun replacePathRules(rules: List<PathRule>) {
        if (rules.isEmpty()) return
        require(rules.map(PathRule::id).distinct().size == rules.size) {
            "rules must have unique ids"
        }
        database.withTransaction {
            pathRuleDao.deleteAll()
            rules.forEach { rule ->
                pathRuleDao.insert(
                    PathRuleEntity(
                        pathRuleId = rule.id.value,
                        volumeName = rule.volumeName,
                        directory = rule.directory,
                        kind = rule.kind.name,
                    ),
                )
            }
        }
    }

    override suspend fun removePathRule(ruleId: PathRuleId) = pathRuleDao.delete(ruleId.value)

    override suspend fun clearPathRules() = pathRuleDao.deleteAll()
}

@Singleton
class RoomPlaylistRepository @Inject constructor(
    private val database: MusicDatabase,
) : PlaylistRepository {
    private val playlistDao = database.playlistDao()
    private val playlistTrackDao = database.playlistTrackDao()
    private val trackDao = database.trackDao()

    override fun observePlaylists(): Flow<List<Playlist>> =
        combine(playlistDao.observeAll(), playlistTrackDao.observeAll()) { playlists, relations ->
            val relationsByPlaylist = relations.groupBy(PlaylistTrackEntity::playlistId)
            playlists.map { it.toDomain(relationsByPlaylist[it.playlistId].orEmpty()) }
        }.distinctUntilChanged()

    override fun observePlaylist(playlistId: PlaylistId): Flow<Playlist?> =
        observePlaylists().map { playlists -> playlists.firstOrNull { it.id == playlistId } }

    override suspend fun createPlaylist(
        displayName: String,
        normalizedName: String,
        createdAtMs: Long,
    ): PlaylistId {
        validatePlaylistNames(displayName, normalizedName)
        require(createdAtMs >= 0) { "createdAtMs must not be negative" }
        return PlaylistId(
            playlistDao.insert(
                PlaylistEntity(
                    displayName = displayName,
                    normalizedName = normalizedName,
                    createdAtMs = createdAtMs,
                    updatedAtMs = createdAtMs,
                ),
            ),
        )
    }

    override suspend fun renamePlaylist(
        playlistId: PlaylistId,
        displayName: String,
        normalizedName: String,
        updatedAtMs: Long,
    ) {
        validatePlaylistNames(displayName, normalizedName)
        database.withTransaction {
            val old = requireNotNull(playlistDao.get(playlistId.value)) { "playlist does not exist" }
            require(updatedAtMs >= old.createdAtMs) { "updatedAtMs must not precede creation" }
            playlistDao.update(
                old.copy(
                    displayName = displayName,
                    normalizedName = normalizedName,
                    updatedAtMs = updatedAtMs,
                ),
            )
        }
    }

    override suspend fun deletePlaylist(playlistId: PlaylistId) = playlistDao.delete(playlistId.value)

    override suspend fun addTracks(
        playlistId: PlaylistId,
        trackIds: List<TrackId>,
        updatedAtMs: Long,
    ) {
        if (trackIds.isEmpty()) return
        database.withTransaction {
            val playlist = requireNotNull(playlistDao.get(playlistId.value)) { "playlist does not exist" }
            requireTracksExist(trackIds)
            val existing = playlistTrackDao.getForPlaylist(playlistId.value)
            val existingIds = existing.map {
                TrackId(it.trackVolumeName, it.trackMediaStoreId)
            }.toSet()
            val additions = trackIds.distinct().filterNot(existingIds::contains)
            if (additions.isEmpty()) return@withTransaction
            val firstPosition = playlistTrackDao.maxPosition(playlistId.value) + 1
            playlistTrackDao.insert(
                additions.mapIndexed { index, trackId ->
                    PlaylistTrackEntity(
                        playlistId = playlistId.value,
                        trackVolumeName = trackId.volumeName,
                        trackMediaStoreId = trackId.mediaStoreId,
                        position = firstPosition + index,
                    )
                },
            )
            playlistDao.update(playlist.withUpdatedAt(updatedAtMs))
        }
    }

    override suspend fun replaceTracks(
        playlistId: PlaylistId,
        trackIds: List<TrackId>,
        updatedAtMs: Long,
    ) {
        if (trackIds.isEmpty()) return
        require(trackIds.distinct().size == trackIds.size) { "trackIds must be unique" }
        database.withTransaction {
            val playlist = requireNotNull(playlistDao.get(playlistId.value)) { "playlist does not exist" }
            requireTracksExist(trackIds)
            playlistTrackDao.deleteAllForPlaylist(playlistId.value)
            playlistTrackDao.insert(
                trackIds.mapIndexed { index, trackId ->
                    PlaylistTrackEntity(
                        playlistId = playlistId.value,
                        trackVolumeName = trackId.volumeName,
                        trackMediaStoreId = trackId.mediaStoreId,
                        position = index,
                    )
                },
            )
            playlistDao.update(playlist.withUpdatedAt(updatedAtMs))
        }
    }

    override suspend fun clearTracks(playlistId: PlaylistId, updatedAtMs: Long) {
        database.withTransaction {
            val playlist = requireNotNull(playlistDao.get(playlistId.value)) { "playlist does not exist" }
            playlistTrackDao.deleteAllForPlaylist(playlistId.value)
            playlistDao.update(playlist.withUpdatedAt(updatedAtMs))
        }
    }

    private fun PlaylistEntity.withUpdatedAt(updatedAtMs: Long): PlaylistEntity {
        require(updatedAtMs >= createdAtMs) { "updatedAtMs must not precede creation" }
        return copy(updatedAtMs = updatedAtMs)
    }

    private suspend fun requireTracksExist(trackIds: List<TrackId>) {
        require(trackIds.all { trackDao.exists(it.volumeName, it.mediaStoreId) }) {
            "every referenced track must exist"
        }
    }
}

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val database: MusicDatabase,
) : HistoryRepository {
    private val historyDao = database.playHistoryDao()
    private val trackDao = database.trackDao()

    override fun observeHistory(): Flow<List<PlayHistory>> =
        historyDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun recordPlayback(trackId: TrackId, playedAtMs: Long) {
        require(playedAtMs >= 0) { "playedAtMs must not be negative" }
        database.withTransaction {
            require(trackDao.exists(trackId.volumeName, trackId.mediaStoreId)) { "track does not exist" }
            val old = historyDao.get(trackId.volumeName, trackId.mediaStoreId)
            historyDao.upsert(
                PlayHistoryEntity(
                    trackVolumeName = trackId.volumeName,
                    trackMediaStoreId = trackId.mediaStoreId,
                    lastPlayedAtMs = playedAtMs,
                    playCount = (old?.playCount ?: 0) + 1,
                ),
            )
        }
    }

    override suspend fun clearHistory() = historyDao.deleteAll()
}

@Singleton
class RoomPlaybackSnapshotRepository @Inject constructor(
    private val database: MusicDatabase,
) : PlaybackSnapshotRepository {
    private val snapshotDao = database.playbackSnapshotDao()

    override fun observeSnapshot(): Flow<PlaybackSnapshot?> =
        snapshotDao.observe().map { it?.let(PlaybackSnapshotCodec::decode) }

    override suspend fun getSnapshot(): PlaybackSnapshot? =
        snapshotDao.get()?.let(PlaybackSnapshotCodec::decode)

    override suspend fun saveSnapshot(snapshot: PlaybackSnapshot) =
        snapshotDao.upsert(PlaybackSnapshotCodec.encode(snapshot))

    override suspend fun clearSnapshot() = snapshotDao.delete()
}

private fun String.escapeSqlLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private fun String.requireNonBlank(name: String): String = apply {
    require(isNotBlank()) { "$name must not be blank" }
}

private fun validatePlaylistNames(displayName: String, normalizedName: String) {
    require(displayName.isNotBlank()) { "displayName must not be blank" }
    require(displayName == displayName.trim()) { "displayName must be trimmed" }
    require(normalizedName.isNotBlank()) { "normalizedName must not be blank" }
}

private fun PlaybackSnapshot.withoutTracks(removedTrackIds: Set<TrackId>): PlaybackSnapshot {
    val remainingQueue = queue.originalQueue.filterNot { it.trackId in removedTrackIds }
    if (remainingQueue.isEmpty()) {
        return copy(
            queue = PlaybackQueue(),
            positionMs = 0,
            playbackInstance = null,
        )
    }
    val remainingIds = remainingQueue.map { it.id }.toSet()
    val stableSequence = queue.stableShuffleSequence.filter(remainingIds::contains)
    val currentId = if (queue.currentItemId == null) {
        null
    } else {
        queue.currentItemId.takeIf(remainingIds::contains)
            ?: queue.playbackOrder.dropWhile { it.id != queue.currentItemId }
                .drop(1)
                .firstOrNull { it.id in remainingIds }
                ?.id
            ?: (if (stableSequence.isNotEmpty()) stableSequence.first() else remainingQueue.first().id)
    }
    val shuffleCursor = stableSequence.indexOf(currentId).takeIf { it >= 0 }
    return copy(
        queue = PlaybackQueue(
            originalQueue = remainingQueue,
            stableShuffleSequence = stableSequence,
            currentItemId = currentId,
            shuffleRound = queue.shuffleRound,
            shuffleCursor = shuffleCursor,
        ),
        positionMs = if (currentId == queue.currentItemId) positionMs else 0,
        playbackInstance = playbackInstance?.takeIf { it.queueItemId == currentId },
    )
}
