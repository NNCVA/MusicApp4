package com.musicapp.player.data.repository.room

import android.database.sqlite.SQLiteConstraintException
import com.musicapp.player.core.domain.BatchAddResult
import com.musicapp.player.core.domain.PathRule
import com.musicapp.player.core.domain.PathRuleId
import com.musicapp.player.core.domain.PlayHistory
import com.musicapp.player.core.domain.PlaybackSnapshot
import com.musicapp.player.core.domain.Playlist
import com.musicapp.player.core.domain.PlaylistId
import com.musicapp.player.core.domain.PlaylistNameResult
import com.musicapp.player.core.domain.PlaylistNameRules
import com.musicapp.player.core.domain.Track
import com.musicapp.player.core.domain.TrackId
import com.musicapp.player.core.system.AppClock
import com.musicapp.player.data.local.dao.HiddenTrackDao
import com.musicapp.player.data.local.dao.PathRuleDao
import com.musicapp.player.data.local.dao.PlayHistoryDao
import com.musicapp.player.data.local.dao.PlaybackSnapshotDao
import com.musicapp.player.data.local.dao.PlaylistDao
import com.musicapp.player.data.local.dao.PlaylistTrackDao
import com.musicapp.player.data.local.dao.PlaylistTrackIdentity
import com.musicapp.player.data.local.dao.TrackDao
import com.musicapp.player.data.local.dao.TrackIdentity
import com.musicapp.player.data.local.entity.HiddenTrackEntity
import com.musicapp.player.data.repository.api.HistoryRepository
import com.musicapp.player.data.repository.api.InvalidInputReason
import com.musicapp.player.data.repository.api.MediaLibraryRepository
import com.musicapp.player.data.repository.api.PathRuleRepository
import com.musicapp.player.data.repository.api.PlaybackSnapshotRepository
import com.musicapp.player.data.repository.api.PlaylistRepository
import com.musicapp.player.data.repository.api.RepositoryError
import com.musicapp.player.data.repository.api.RepositoryResult
import com.musicapp.player.data.repository.api.Resource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RoomMediaLibraryRepository(
  private val trackDao: TrackDao,
  private val hiddenTrackDao: HiddenTrackDao,
  private val scope: CoroutineScope,
  private val clock: AppClock,
) : MediaLibraryRepository {
  override val tracks: StateFlow<List<Track>> =
    trackDao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())
  override val hiddenTrackIds: StateFlow<Set<TrackId>> =
    hiddenTrackDao.observeAll()
      .map { rows -> rows.mapTo(mutableSetOf()) { TrackId(it.trackVolumeName, it.trackMediaStoreId) } }
      .stateIn(scope, SharingStarted.Eagerly, emptySet())
  private val observedTracks = ConcurrentHashMap<TrackId, StateFlow<Track?>>()

  override fun observeTrack(trackId: TrackId): StateFlow<Track?> =
    observedTracks.getOrPut(trackId) {
      trackDao.observe(trackId.volumeName, trackId.mediaStoreId).map { it?.toDomainOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)
    }

  override suspend fun commitFullScan(generation: Long, tracks: List<Track>): RepositoryResult<Unit> =
    if (generation < 0L) {
      invalidInput(InvalidInputReason.INVALID_SYNC_GENERATION)
    } else repositoryCall {
      trackDao.commitFullScan(generation, tracks.map { it.toEntity(generation).copy(isAvailable = true) })
    }

  override suspend fun upsertTracks(tracks: List<Track>): RepositoryResult<Unit> = repositoryCall {
    trackDao.upsertPreservingGeneration(tracks.map { it.toEntity(generation = 0L) })
  }

  override suspend fun setTracksAvailable(
    trackIds: Set<TrackId>,
    isAvailable: Boolean,
  ): RepositoryResult<Unit> = repositoryCall {
    val success = trackDao.setAvailable(trackIds.map { TrackIdentity(it.volumeName, it.mediaStoreId) }, isAvailable)
    if (!success) {
      val missing = trackIds.first { trackDao.find(it.volumeName, it.mediaStoreId) == null }
      fail(trackNotFound(missing))
    }
  }

  override suspend fun setTracksHidden(trackIds: Set<TrackId>, hidden: Boolean): RepositoryResult<Unit> = repositoryCall {
    val missing = trackIds.firstOrNull { trackDao.find(it.volumeName, it.mediaStoreId) == null }
    if (missing != null) fail(trackNotFound(missing))
    val hiddenAt = clock.currentTimeMillis()
    hiddenTrackDao.setHidden(
      trackIds.map { HiddenTrackEntity(it.volumeName, it.mediaStoreId, hiddenAt) },
      hidden,
    )
  }
}

class RoomPlaylistRepository(
  private val playlistDao: PlaylistDao,
  private val playlistTrackDao: PlaylistTrackDao,
  private val scope: CoroutineScope,
  private val clock: AppClock,
  private val newId: () -> String = { UUID.randomUUID().toString() },
) : PlaylistRepository {
  override val playlists: StateFlow<List<Playlist>> =
    playlistDao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())
  private val observedTrackIds = ConcurrentHashMap<PlaylistId, StateFlow<List<TrackId>>>()

  override fun observeTrackIds(playlistId: PlaylistId): StateFlow<List<TrackId>> =
    observedTrackIds.getOrPut(playlistId) {
      playlistTrackDao.observeForPlaylist(playlistId.value)
        .map { rows -> rows.map { TrackId(it.trackVolumeName, it.trackMediaStoreId) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

  override suspend fun create(name: String, createdAtMs: Long): RepositoryResult<Playlist> = repositoryCall {
    if (createdAtMs < 0L) fail(RepositoryError.InvalidInput(InvalidInputReason.INVALID_TIME))
    val valid = validatedName(name) ?: fail(invalidNameError(name))
    if (playlistDao.nameExists(valid.comparisonKey)) {
      fail(RepositoryError.AlreadyExists(Resource.PLAYLIST, valid.comparisonKey))
    }
    val playlist = Playlist(PlaylistId(newId()), valid.displayName, valid.comparisonKey, createdAtMs, createdAtMs)
    try {
      playlistDao.insert(playlist.toEntity())
    } catch (_: SQLiteConstraintException) {
      fail(RepositoryError.AlreadyExists(Resource.PLAYLIST, valid.comparisonKey))
    }
    playlist
  }

  override suspend fun rename(
    playlistId: PlaylistId,
    name: String,
    updatedAtMs: Long,
  ): RepositoryResult<Playlist> = repositoryCall {
    if (updatedAtMs < 0L) fail(RepositoryError.InvalidInput(InvalidInputReason.INVALID_TIME))
    val current = playlistDao.find(playlistId.value)?.toDomainOrNull() ?: fail(playlistNotFound(playlistId))
    if (updatedAtMs < current.createdAtMs) fail(RepositoryError.InvalidInput(InvalidInputReason.INVALID_TIME))
    val valid = validatedName(name) ?: fail(invalidNameError(name))
    if (playlistDao.nameExists(valid.comparisonKey, playlistId.value)) {
      fail(RepositoryError.AlreadyExists(Resource.PLAYLIST, valid.comparisonKey))
    }
    val renamed = current.copy(name = valid.displayName, normalizedName = valid.comparisonKey, updatedAtMs = updatedAtMs)
    try {
      playlistDao.update(renamed.toEntity())
    } catch (_: SQLiteConstraintException) {
      fail(RepositoryError.AlreadyExists(Resource.PLAYLIST, valid.comparisonKey))
    }
    renamed
  }

  override suspend fun delete(playlistId: PlaylistId): RepositoryResult<Unit> = repositoryCall {
    if (playlistDao.delete(playlistId.value) == 0) fail(playlistNotFound(playlistId))
  }

  override suspend fun deleteAll(): RepositoryResult<Unit> = repositoryCall { playlistDao.deleteAll(); Unit }

  override suspend fun addTracks(
    playlistId: PlaylistId,
    trackIds: List<TrackId>,
  ): RepositoryResult<BatchAddResult> = repositoryCall {
    if (playlistDao.find(playlistId.value) == null) fail(playlistNotFound(playlistId))
    val result =
      playlistTrackDao.appendDistinct(
        playlistId.value,
        trackIds.map { PlaylistTrackIdentity(it.volumeName, it.mediaStoreId) },
        clock.currentTimeMillis(),
      )
    BatchAddResult(result.addedCount, result.skippedCount)
  }

  override suspend fun removeTracks(
    playlistId: PlaylistId,
    trackIds: Set<TrackId>,
  ): RepositoryResult<Int> = repositoryCall {
    if (playlistDao.find(playlistId.value) == null) fail(playlistNotFound(playlistId))
    playlistTrackDao.delete(
      playlistId.value,
      trackIds.map { PlaylistTrackIdentity(it.volumeName, it.mediaStoreId) },
    )
  }

  private fun validatedName(name: String) = PlaylistNameRules.normalize(name) as? PlaylistNameResult.Valid

  private fun invalidNameError(name: String): RepositoryError.InvalidInput {
    val reason = when (PlaylistNameRules.normalize(name)) {
      PlaylistNameResult.Blank -> InvalidInputReason.BLANK_PLAYLIST_NAME
      is PlaylistNameResult.InvalidLength -> InvalidInputReason.PLAYLIST_NAME_TOO_LONG
      is PlaylistNameResult.Valid -> error("valid name")
    }
    return RepositoryError.InvalidInput(reason)
  }

  private fun playlistNotFound(id: PlaylistId) = RepositoryError.NotFound(Resource.PLAYLIST, id.value)
}

class RoomHistoryRepository(
  private val dao: PlayHistoryDao,
  scope: CoroutineScope,
) : HistoryRepository {
  override val history: StateFlow<List<PlayHistory>> =
    dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  override suspend fun recordQualifiedPlay(trackId: TrackId, playedAtMs: Long): RepositoryResult<PlayHistory> =
    repositoryCall {
      if (playedAtMs < 0L) fail(RepositoryError.InvalidInput(InvalidInputReason.INVALID_TIME))
      dao.record(PlaylistTrackIdentity(trackId.volumeName, trackId.mediaStoreId), playedAtMs).toDomainOrNull()
        ?: error("Room returned invalid history")
    }

  override suspend fun clear(): RepositoryResult<Unit> = repositoryCall { dao.deleteAll(); Unit }
}

class RoomPlaybackSnapshotRepository(
  private val dao: PlaybackSnapshotDao,
  scope: CoroutineScope,
) : PlaybackSnapshotRepository {
  override val snapshot: StateFlow<PlaybackSnapshot?> =
    dao.observeActive().map { it?.toDomainOrNull() }.stateIn(scope, SharingStarted.Eagerly, null)

  override suspend fun save(snapshot: PlaybackSnapshot): RepositoryResult<Unit> = repositoryCall { dao.save(snapshot.toEntity()) }
  override suspend fun clear(): RepositoryResult<Unit> = repositoryCall { dao.clear(); Unit }
}

class RoomPathRuleRepository(
  private val dao: PathRuleDao,
  scope: CoroutineScope,
  private val clock: AppClock,
) : PathRuleRepository {
  override val rules: StateFlow<List<PathRule>> =
    dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  override suspend fun upsert(rule: PathRule): RepositoryResult<Unit> =
    repositoryCall { dao.upsert(rule.toEntity(clock.currentTimeMillis())) }

  override suspend fun remove(ruleId: PathRuleId): RepositoryResult<Unit> = repositoryCall {
    if (dao.delete(ruleId.value) == 0) fail(RepositoryError.NotFound(Resource.PATH_RULE, ruleId.value))
  }
}

private suspend inline fun <T> repositoryCall(crossinline block: suspend () -> T): RepositoryResult<T> =
  try {
    RepositoryResult.Success(block())
  } catch (failure: RepositoryFailureException) {
    RepositoryResult.Failure(failure.error)
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (_: Exception) {
    RepositoryResult.Failure(RepositoryError.PersistenceUnavailable)
  }

private class RepositoryFailureException(val error: RepositoryError) : Exception()
private fun fail(error: RepositoryError): Nothing = throw RepositoryFailureException(error)
private fun trackNotFound(trackId: TrackId) =
  RepositoryError.NotFound(Resource.TRACK, "${trackId.volumeName}:${trackId.mediaStoreId}")

private fun <T> invalidInput(reason: InvalidInputReason): RepositoryResult<T> =
  RepositoryResult.Failure(RepositoryError.InvalidInput(reason))
