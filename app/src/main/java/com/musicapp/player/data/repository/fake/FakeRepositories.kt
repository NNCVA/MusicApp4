package com.musicapp.player.data.repository.fake

import com.musicapp.player.core.domain.BatchAddResult
import com.musicapp.player.core.domain.PathRule
import com.musicapp.player.core.domain.PathRuleId
import com.musicapp.player.core.domain.PlayHistory
import com.musicapp.player.core.domain.PlaybackSnapshot
import com.musicapp.player.core.domain.Playlist
import com.musicapp.player.core.domain.PlaylistBatchRules
import com.musicapp.player.core.domain.PlaylistId
import com.musicapp.player.core.domain.PlaylistNameResult
import com.musicapp.player.core.domain.PlaylistNameRules
import com.musicapp.player.core.domain.Track
import com.musicapp.player.core.domain.TrackId
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeMediaLibraryRepository(initialTracks: List<Track> = emptyList()) : MediaLibraryRepository {
  private val mutex = Mutex()
  private val mutableTracks = MutableStateFlow(initialTracks.distinctBy(Track::id))
  private val mutableHiddenTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
  private val trackFlows = mutableMapOf<TrackId, MutableStateFlow<Track?>>()

  override val tracks: StateFlow<List<Track>> = mutableTracks.asStateFlow()
  override val hiddenTrackIds: StateFlow<Set<TrackId>> = mutableHiddenTrackIds.asStateFlow()

  override fun observeTrack(trackId: TrackId): StateFlow<Track?> =
    trackFlows.getOrPut(trackId) { MutableStateFlow(mutableTracks.value.firstOrNull { it.id == trackId }) }.asStateFlow()

  override suspend fun commitFullScan(generation: Long, tracks: List<Track>): RepositoryResult<Unit> = mutex.withLock {
    if (generation < 0L) {
      return@withLock RepositoryResult.Failure(RepositoryError.InvalidInput(InvalidInputReason.INVALID_SYNC_GENERATION))
    }
    val scanned = tracks.associateBy(Track::id)
    val committed = mutableTracks.value.map { cached ->
      scanned[cached.id] ?: cached.copy(isAvailable = false)
    }.associateBy(Track::id).toMutableMap()
    scanned.forEach { (id, track) -> committed[id] = track.copy(isAvailable = true) }
    mutableTracks.value = committed.values.toList()
    trackFlows.forEach { (id, flow) -> flow.value = committed[id] }
    RepositoryResult.Success(Unit)
  }

  override suspend fun upsertTracks(tracks: List<Track>): RepositoryResult<Unit> = mutex.withLock {
    val merged = mutableTracks.value.associateBy(Track::id).toMutableMap()
    tracks.forEach { merged[it.id] = it }
    mutableTracks.value = merged.values.toList()
    tracks.forEach { trackFlows[it.id]?.value = it }
    RepositoryResult.Success(Unit)
  }

  override suspend fun setTracksAvailable(trackIds: Set<TrackId>, isAvailable: Boolean): RepositoryResult<Unit> =
    mutex.withLock {
      val found = mutableTracks.value.mapTo(mutableSetOf(), Track::id)
      val missing = trackIds.firstOrNull { it !in found }
      if (missing != null) return@withLock RepositoryResult.Failure(trackNotFound(missing))
      mutableTracks.value = mutableTracks.value.map { track ->
        if (track.id in trackIds) track.copy(isAvailable = isAvailable).also { trackFlows[track.id]?.value = it } else track
      }
      RepositoryResult.Success(Unit)
    }

  override suspend fun setTracksHidden(trackIds: Set<TrackId>, hidden: Boolean): RepositoryResult<Unit> = mutex.withLock {
    val found = mutableTracks.value.mapTo(mutableSetOf(), Track::id)
    val missing = trackIds.firstOrNull { it !in found }
    if (missing != null) return@withLock RepositoryResult.Failure(trackNotFound(missing))
    mutableHiddenTrackIds.value =
      if (hidden) mutableHiddenTrackIds.value + trackIds else mutableHiddenTrackIds.value - trackIds
    RepositoryResult.Success(Unit)
  }

  private fun trackNotFound(trackId: TrackId) =
    RepositoryError.NotFound(Resource.TRACK, "${trackId.volumeName}:${trackId.mediaStoreId}")
}

class FakePlaylistRepository(
  initialPlaylists: List<Playlist> = emptyList(),
  private val newId: () -> String = { UUID.randomUUID().toString() },
) : PlaylistRepository {
  private val mutex = Mutex()
  private val mutablePlaylists = MutableStateFlow(initialPlaylists.sortedByDescending(Playlist::createdAtMs))
  private val trackIdsByPlaylist = initialPlaylists.associate { it.id to MutableStateFlow<List<TrackId>>(emptyList()) }.toMutableMap()

  override val playlists: StateFlow<List<Playlist>> = mutablePlaylists.asStateFlow()

  override fun observeTrackIds(playlistId: PlaylistId): StateFlow<List<TrackId>> =
    trackIdsByPlaylist.getOrPut(playlistId) { MutableStateFlow(emptyList()) }.asStateFlow()

  override suspend fun create(name: String, createdAtMs: Long): RepositoryResult<Playlist> = mutex.withLock {
    if (createdAtMs < 0L) return@withLock invalidTime()
    val valid = validateName(name) ?: return@withLock invalidName(name)
    if (PlaylistNameRules.conflicts(valid, mutablePlaylists.value.map(Playlist::name))) {
      return@withLock RepositoryResult.Failure(RepositoryError.AlreadyExists(Resource.PLAYLIST, valid.comparisonKey))
    }
    val playlistId = runCatching { PlaylistId(newId()) }.getOrNull()
      ?: return@withLock RepositoryResult.Failure(RepositoryError.PersistenceUnavailable)
    val playlist = Playlist(playlistId, valid.displayName, valid.comparisonKey, createdAtMs, createdAtMs)
    mutablePlaylists.value = (mutablePlaylists.value + playlist).sortedByDescending(Playlist::createdAtMs)
    trackIdsByPlaylist[playlist.id] = MutableStateFlow(emptyList())
    RepositoryResult.Success(playlist)
  }

  override suspend fun rename(
    playlistId: PlaylistId,
    name: String,
    updatedAtMs: Long,
  ): RepositoryResult<Playlist> = mutex.withLock {
    if (updatedAtMs < 0L) return@withLock invalidTime()
    val current = mutablePlaylists.value.firstOrNull { it.id == playlistId }
      ?: return@withLock RepositoryResult.Failure(playlistNotFound(playlistId))
    if (updatedAtMs < current.createdAtMs) return@withLock invalidTime()
    val valid = validateName(name) ?: return@withLock invalidName(name)
    val otherNames = mutablePlaylists.value.filterNot { it.id == playlistId }.map(Playlist::name)
    if (PlaylistNameRules.conflicts(valid, otherNames)) {
      return@withLock RepositoryResult.Failure(RepositoryError.AlreadyExists(Resource.PLAYLIST, valid.comparisonKey))
    }
    val renamed = current.copy(name = valid.displayName, normalizedName = valid.comparisonKey, updatedAtMs = updatedAtMs)
    mutablePlaylists.value = mutablePlaylists.value.map { if (it.id == playlistId) renamed else it }
    RepositoryResult.Success(renamed)
  }

  override suspend fun delete(playlistId: PlaylistId): RepositoryResult<Unit> = mutex.withLock {
    if (mutablePlaylists.value.none { it.id == playlistId }) {
      return@withLock RepositoryResult.Failure(playlistNotFound(playlistId))
    }
    mutablePlaylists.value = mutablePlaylists.value.filterNot { it.id == playlistId }
    trackIdsByPlaylist.remove(playlistId)?.value = emptyList()
    RepositoryResult.Success(Unit)
  }

  override suspend fun deleteAll(): RepositoryResult<Unit> = mutex.withLock {
    mutablePlaylists.value = emptyList()
    trackIdsByPlaylist.values.forEach { it.value = emptyList() }
    trackIdsByPlaylist.clear()
    RepositoryResult.Success(Unit)
  }

  override suspend fun addTracks(
    playlistId: PlaylistId,
    trackIds: List<TrackId>,
  ): RepositoryResult<BatchAddResult> = mutex.withLock {
    if (mutablePlaylists.value.none { it.id == playlistId }) {
      return@withLock RepositoryResult.Failure(playlistNotFound(playlistId))
    }
    val flow = trackIdsByPlaylist.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
    val selection = PlaylistBatchRules.selectNewTracks(flow.value, trackIds)
    flow.value = flow.value + selection.tracksToAdd
    RepositoryResult.Success(selection.result)
  }

  override suspend fun removeTracks(
    playlistId: PlaylistId,
    trackIds: Set<TrackId>,
  ): RepositoryResult<Int> = mutex.withLock {
    if (mutablePlaylists.value.none { it.id == playlistId }) {
      return@withLock RepositoryResult.Failure(playlistNotFound(playlistId))
    }
    val flow = trackIdsByPlaylist.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
    val before = flow.value.size
    flow.value = flow.value.filterNot { it in trackIds }
    RepositoryResult.Success(before - flow.value.size)
  }

  private fun validateName(name: String): PlaylistNameResult.Valid? =
    PlaylistNameRules.normalize(name) as? PlaylistNameResult.Valid

  private fun invalidName(name: String): RepositoryResult.Failure {
    val reason = when (PlaylistNameRules.normalize(name)) {
      PlaylistNameResult.Blank -> InvalidInputReason.BLANK_PLAYLIST_NAME
      is PlaylistNameResult.InvalidLength -> InvalidInputReason.PLAYLIST_NAME_TOO_LONG
      is PlaylistNameResult.Valid -> InvalidInputReason.INVALID_PLAYLIST_NAME
    }
    return RepositoryResult.Failure(RepositoryError.InvalidInput(reason))
  }

  private fun playlistNotFound(id: PlaylistId) = RepositoryError.NotFound(Resource.PLAYLIST, id.value)

  private fun invalidTime() = RepositoryResult.Failure(RepositoryError.InvalidInput(InvalidInputReason.INVALID_TIME))
}

class FakeHistoryRepository(initialHistory: List<PlayHistory> = emptyList()) : HistoryRepository {
  private val mutex = Mutex()
  private val mutableHistory = MutableStateFlow(initialHistory.sortedByDescending(PlayHistory::lastPlayedAtMs))
  override val history: StateFlow<List<PlayHistory>> = mutableHistory.asStateFlow()

  override suspend fun recordQualifiedPlay(trackId: TrackId, playedAtMs: Long): RepositoryResult<PlayHistory> = mutex.withLock {
    if (playedAtMs < 0L) {
      return@withLock RepositoryResult.Failure(RepositoryError.InvalidInput(InvalidInputReason.INVALID_TIME))
    }
    val prior = mutableHistory.value.firstOrNull { it.trackId == trackId }
    val updated = PlayHistory(trackId, playedAtMs, (prior?.playCount ?: 0L) + 1L)
    mutableHistory.value = (mutableHistory.value.filterNot { it.trackId == trackId } + updated)
      .sortedByDescending(PlayHistory::lastPlayedAtMs)
    RepositoryResult.Success(updated)
  }

  override suspend fun clear(): RepositoryResult<Unit> = mutex.withLock {
    mutableHistory.value = emptyList()
    RepositoryResult.Success(Unit)
  }
}

class FakePlaybackSnapshotRepository(initialSnapshot: PlaybackSnapshot? = null) : PlaybackSnapshotRepository {
  private val mutex = Mutex()
  private val mutableSnapshot = MutableStateFlow(initialSnapshot)
  override val snapshot: StateFlow<PlaybackSnapshot?> = mutableSnapshot.asStateFlow()

  override suspend fun save(snapshot: PlaybackSnapshot): RepositoryResult<Unit> = mutex.withLock {
    mutableSnapshot.value = snapshot
    RepositoryResult.Success(Unit)
  }

  override suspend fun clear(): RepositoryResult<Unit> = mutex.withLock {
    mutableSnapshot.value = null
    RepositoryResult.Success(Unit)
  }
}

class FakePathRuleRepository(initialRules: List<PathRule> = emptyList()) : PathRuleRepository {
  private val mutex = Mutex()
  private val mutableRules = MutableStateFlow(initialRules.distinctBy(PathRule::id))
  override val rules: StateFlow<List<PathRule>> = mutableRules.asStateFlow()

  override suspend fun upsert(rule: PathRule): RepositoryResult<Unit> = mutex.withLock {
    mutableRules.value = mutableRules.value.filterNot { it.id == rule.id } + rule
    RepositoryResult.Success(Unit)
  }

  override suspend fun remove(ruleId: PathRuleId): RepositoryResult<Unit> = mutex.withLock {
    if (mutableRules.value.none { it.id == ruleId }) {
      return@withLock RepositoryResult.Failure(RepositoryError.NotFound(Resource.PATH_RULE, ruleId.value))
    }
    mutableRules.value = mutableRules.value.filterNot { it.id == ruleId }
    RepositoryResult.Success(Unit)
  }
}
