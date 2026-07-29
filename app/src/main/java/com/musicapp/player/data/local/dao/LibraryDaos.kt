package com.musicapp.player.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.musicapp.player.data.local.entity.HiddenTrackEntity
import com.musicapp.player.data.local.entity.PathRuleEntity
import com.musicapp.player.data.local.entity.PlayHistoryEntity
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import com.musicapp.player.data.local.entity.PlaylistEntity
import com.musicapp.player.data.local.entity.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenTrackDao {
  @Query("SELECT * FROM hidden_tracks") fun observeAll(): Flow<List<HiddenTrackEntity>>
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entity: HiddenTrackEntity): Long
  @Query("DELETE FROM hidden_tracks WHERE trackVolumeName = :volumeName AND trackMediaStoreId = :mediaStoreId")
  suspend fun delete(volumeName: String, mediaStoreId: Long): Int
  @Query("SELECT EXISTS(SELECT 1 FROM hidden_tracks WHERE trackVolumeName = :volumeName AND trackMediaStoreId = :mediaStoreId)")
  suspend fun contains(volumeName: String, mediaStoreId: Long): Boolean

  @Transaction
  suspend fun setHidden(entities: Collection<HiddenTrackEntity>, hidden: Boolean) {
    entities.forEach { entity ->
      if (hidden) insert(entity) else delete(entity.trackVolumeName, entity.trackMediaStoreId)
    }
  }
}

@Dao
interface PathRuleDao {
  @Query("SELECT * FROM path_rules ORDER BY ruleType, volumeName, relativePath") fun observeAll(): Flow<List<PathRuleEntity>>
  @Upsert suspend fun upsert(entity: PathRuleEntity)
  @Delete suspend fun delete(entity: PathRuleEntity): Int
  @Query("DELETE FROM path_rules WHERE pathRuleId = :pathRuleId") suspend fun delete(pathRuleId: String): Int
  @Query("DELETE FROM path_rules") suspend fun deleteAll(): Int
}

@Dao
interface PlayHistoryDao {
  @Query("SELECT * FROM play_history ORDER BY lastPlayedAtEpochMillis DESC") fun observeAll(): Flow<List<PlayHistoryEntity>>
  @Query("SELECT * FROM play_history WHERE trackVolumeName = :volumeName AND trackMediaStoreId = :mediaStoreId")
  suspend fun find(volumeName: String, mediaStoreId: Long): PlayHistoryEntity?
  @Upsert suspend fun upsert(entity: PlayHistoryEntity)
  @Query("DELETE FROM play_history") suspend fun deleteAll(): Int

  @Transaction
  suspend fun record(track: PlaylistTrackIdentity, playedAtEpochMillis: Long): PlayHistoryEntity {
    val prior = find(track.volumeName, track.mediaStoreId)
    val updated =
      PlayHistoryEntity(
        trackVolumeName = track.volumeName,
        trackMediaStoreId = track.mediaStoreId,
        lastPlayedAtEpochMillis = playedAtEpochMillis,
        playCount = (prior?.playCount ?: 0L) + 1L,
      )
    upsert(updated)
    return updated
  }
}

@Dao
interface PlaybackSnapshotDao {
  @Query("SELECT * FROM playback_snapshot WHERE snapshotId = 1") fun observeActive(): Flow<PlaybackSnapshotEntity?>
  @Query("SELECT * FROM playback_snapshot WHERE snapshotId = 1") suspend fun getActive(): PlaybackSnapshotEntity?
  @Upsert suspend fun save(entity: PlaybackSnapshotEntity)
  @Query("DELETE FROM playback_snapshot WHERE snapshotId = 1") suspend fun clear(): Int
}

@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY createdAtEpochMillis DESC") fun observeAll(): Flow<List<PlaylistEntity>>
  @Query("SELECT * FROM playlists WHERE playlistId = :playlistId") suspend fun find(playlistId: String): PlaylistEntity?
  @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE nameComparisonKey = :nameComparisonKey AND playlistId != :excludedPlaylistId)")
  suspend fun nameExists(nameComparisonKey: String, excludedPlaylistId: String = ""): Boolean
  @Insert suspend fun insert(entity: PlaylistEntity)
  @Update suspend fun update(entity: PlaylistEntity): Int
  @Query("DELETE FROM playlists WHERE playlistId = :playlistId") suspend fun delete(playlistId: String): Int
  @Query("DELETE FROM playlists") suspend fun deleteAll(): Int
}

@Dao
interface PlaylistTrackDao {
  @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
  fun observeForPlaylist(playlistId: String): Flow<List<PlaylistTrackEntity>>
  @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
  suspend fun getForPlaylist(playlistId: String): List<PlaylistTrackEntity>
  @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
  suspend fun maxPosition(playlistId: String): Int
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entities: List<PlaylistTrackEntity>): List<Long>
  @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackVolumeName = :volumeName AND trackMediaStoreId = :mediaStoreId")
  suspend fun delete(playlistId: String, volumeName: String, mediaStoreId: Long): Int
  @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId") suspend fun deleteAllFromPlaylist(playlistId: String): Int

  @Transaction
  suspend fun delete(playlistId: String, tracks: Collection<PlaylistTrackIdentity>): Int =
    tracks.sumOf { delete(playlistId, it.volumeName, it.mediaStoreId) }

  @Transaction
  suspend fun appendDistinct(
    playlistId: String,
    tracks: List<PlaylistTrackIdentity>,
    addedAtEpochMillis: Long,
  ): PlaylistInsertResult {
    var nextPosition = maxPosition(playlistId) + 1
    var addedCount = 0
    tracks.distinct().forEach { track ->
      val inserted =
        insert(
          listOf(
            PlaylistTrackEntity(
              playlistId = playlistId,
              trackVolumeName = track.volumeName,
              trackMediaStoreId = track.mediaStoreId,
              position = nextPosition,
              addedAtEpochMillis = addedAtEpochMillis,
            ),
          ),
        ).single()
      if (inserted != -1L) {
        addedCount += 1
        nextPosition += 1
      }
    }
    return PlaylistInsertResult(addedCount = addedCount, skippedCount = tracks.size - addedCount)
  }
}

data class PlaylistTrackIdentity(val volumeName: String, val mediaStoreId: Long)
data class PlaylistInsertResult(val addedCount: Int, val skippedCount: Int)
