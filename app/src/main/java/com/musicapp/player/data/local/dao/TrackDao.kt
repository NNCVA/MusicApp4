package com.musicapp.player.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.musicapp.player.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
  @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE, volumeName, mediaStoreId")
  fun observeAll(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM tracks WHERE isAvailable = 1 ORDER BY title COLLATE NOCASE, volumeName, mediaStoreId")
  fun observeAvailable(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM tracks WHERE volumeName = :volumeName AND mediaStoreId = :mediaStoreId")
  fun observe(volumeName: String, mediaStoreId: Long): Flow<TrackEntity?>

  @Query("SELECT * FROM tracks WHERE volumeName = :volumeName AND mediaStoreId = :mediaStoreId")
  suspend fun find(volumeName: String, mediaStoreId: Long): TrackEntity?

  @Query("SELECT COUNT(*) FROM tracks")
  suspend fun count(): Int

  @Upsert
  suspend fun upsert(tracks: List<TrackEntity>)

  @Query("UPDATE tracks SET isAvailable = 0 WHERE lastSeenGeneration != :generation")
  suspend fun markNotSeenUnavailable(generation: Long): Int

  @Query("UPDATE tracks SET isAvailable = 0 WHERE volumeName = :volumeName")
  suspend fun markVolumeUnavailable(volumeName: String): Int

  @Query("UPDATE tracks SET isAvailable = :isAvailable WHERE volumeName = :volumeName AND mediaStoreId = :mediaStoreId")
  suspend fun setAvailable(volumeName: String, mediaStoreId: Long, isAvailable: Boolean): Int

  @Query("DELETE FROM tracks")
  suspend fun deleteAll(): Int

  @Transaction
  suspend fun commitFullScan(generation: Long, tracks: List<TrackEntity>) {
    upsert(tracks)
    markNotSeenUnavailable(generation)
  }

  @Transaction
  suspend fun setAvailable(trackIds: Collection<TrackIdentity>, isAvailable: Boolean): Boolean {
    if (trackIds.any { find(it.volumeName, it.mediaStoreId) == null }) return false
    trackIds.forEach { id ->
      setAvailable(id.volumeName, id.mediaStoreId, isAvailable)
    }
    return true
  }

  @Transaction
  suspend fun upsertPreservingGeneration(tracks: List<TrackEntity>) {
    val preserved = tracks.map { track ->
      val generation = find(track.volumeName, track.mediaStoreId)?.lastSeenGeneration ?: track.lastSeenGeneration
      track.copy(lastSeenGeneration = generation)
    }
    upsert(preserved)
  }
}

data class TrackIdentity(val volumeName: String, val mediaStoreId: Long)
