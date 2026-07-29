package com.musicapp.player.data.repository.api

import com.musicapp.player.core.domain.BatchAddResult
import com.musicapp.player.core.domain.PathRule
import com.musicapp.player.core.domain.PathRuleId
import com.musicapp.player.core.domain.PlayHistory
import com.musicapp.player.core.domain.PlaybackSnapshot
import com.musicapp.player.core.domain.Playlist
import com.musicapp.player.core.domain.PlaylistId
import com.musicapp.player.core.domain.Track
import com.musicapp.player.core.domain.TrackId
import kotlinx.coroutines.flow.StateFlow

interface MediaLibraryRepository {
  val tracks: StateFlow<List<Track>>
  val hiddenTrackIds: StateFlow<Set<TrackId>>

  fun observeTrack(trackId: TrackId): StateFlow<Track?>

  /** Atomically upserts this complete scan and marks every unseen cached track unavailable. */
  suspend fun commitFullScan(generation: Long, tracks: List<Track>): RepositoryResult<Unit>

  suspend fun upsertTracks(tracks: List<Track>): RepositoryResult<Unit>

  suspend fun setTracksAvailable(trackIds: Set<TrackId>, isAvailable: Boolean): RepositoryResult<Unit>

  suspend fun setTracksHidden(trackIds: Set<TrackId>, hidden: Boolean): RepositoryResult<Unit>
}

interface PlaylistRepository {
  val playlists: StateFlow<List<Playlist>>

  fun observeTrackIds(playlistId: PlaylistId): StateFlow<List<TrackId>>

  suspend fun create(name: String, createdAtMs: Long): RepositoryResult<Playlist>

  suspend fun rename(playlistId: PlaylistId, name: String, updatedAtMs: Long): RepositoryResult<Playlist>

  suspend fun delete(playlistId: PlaylistId): RepositoryResult<Unit>

  suspend fun deleteAll(): RepositoryResult<Unit>

  suspend fun addTracks(playlistId: PlaylistId, trackIds: List<TrackId>): RepositoryResult<BatchAddResult>

  suspend fun removeTracks(playlistId: PlaylistId, trackIds: Set<TrackId>): RepositoryResult<Int>
}

interface HistoryRepository {
  val history: StateFlow<List<PlayHistory>>

  suspend fun recordQualifiedPlay(trackId: TrackId, playedAtMs: Long): RepositoryResult<PlayHistory>

  suspend fun clear(): RepositoryResult<Unit>
}

interface PlaybackSnapshotRepository {
  val snapshot: StateFlow<PlaybackSnapshot?>

  suspend fun save(snapshot: PlaybackSnapshot): RepositoryResult<Unit>

  suspend fun clear(): RepositoryResult<Unit>
}

interface PathRuleRepository {
  val rules: StateFlow<List<PathRule>>

  suspend fun upsert(rule: PathRule): RepositoryResult<Unit>

  suspend fun remove(ruleId: PathRuleId): RepositoryResult<Unit>
}
