package com.musicapp.player.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.musicapp.player.data.local.entity.HiddenTrackEntity
import com.musicapp.player.data.local.entity.MediaSyncStateEntity
import com.musicapp.player.data.local.entity.MediaVolumeSyncStateEntity
import com.musicapp.player.data.local.entity.PathRuleEntity
import com.musicapp.player.data.local.entity.PlayHistoryEntity
import com.musicapp.player.data.local.entity.PlaybackSnapshotEntity
import com.musicapp.player.data.local.entity.PlaylistEntity
import com.musicapp.player.data.local.entity.PlaylistTrackEntity
import com.musicapp.player.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE, volume_name, media_store_id")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT tracks.* FROM tracks
        WHERE NOT EXISTS (
            SELECT 1 FROM hidden_tracks
            WHERE track_volume_name = tracks.volume_name
              AND track_media_store_id = tracks.media_store_id
        )
        ORDER BY title COLLATE NOCASE, volume_name, media_store_id
        """,
    )
    fun observeVisible(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE volume_name = :volumeName AND media_store_id = :mediaStoreId")
    suspend fun get(volumeName: String, mediaStoreId: Long): TrackEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM tracks WHERE volume_name = :volumeName AND media_store_id = :mediaStoreId)")
    suspend fun exists(volumeName: String, mediaStoreId: Long): Boolean

    @Query("SELECT * FROM tracks WHERE volume_name = :volumeName")
    suspend fun getForVolume(volumeName: String): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE album_volume_name = :volumeName AND album_media_store_id = :albumMediaStoreId
          AND NOT EXISTS (
              SELECT 1 FROM hidden_tracks
              WHERE track_volume_name = tracks.volume_name
                AND track_media_store_id = tracks.media_store_id
          )
        ORDER BY title COLLATE NOCASE, media_store_id
        """,
    )
    fun observeAlbumTracks(volumeName: String, albumMediaStoreId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE artist_media_store_id = :artistMediaStoreId
          AND NOT EXISTS (
              SELECT 1 FROM hidden_tracks
              WHERE track_volume_name = tracks.volume_name
                AND track_media_store_id = tracks.media_store_id
          )
        ORDER BY album_title COLLATE NOCASE, title COLLATE NOCASE, volume_name, media_store_id
        """,
    )
    fun observeArtistTracks(artistMediaStoreId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE volume_name = :volumeName
          AND (relative_path = :directoryPath
            OR relative_path LIKE :escapedDescendantPrefix || '%' ESCAPE '\')
          AND NOT EXISTS (
              SELECT 1 FROM hidden_tracks
              WHERE track_volume_name = tracks.volume_name
                AND track_media_store_id = tracks.media_store_id
          )
        ORDER BY relative_path COLLATE NOCASE, title COLLATE NOCASE, media_store_id
        """,
    )
    fun observeFolderTracks(
        volumeName: String,
        directoryPath: String,
        escapedDescendantPrefix: String,
    ): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE volume_name = :volumeName
          AND NOT EXISTS (
              SELECT 1 FROM hidden_tracks
              WHERE track_volume_name = tracks.volume_name
                AND track_media_store_id = tracks.media_store_id
          )
        ORDER BY relative_path COLLATE NOCASE, title COLLATE NOCASE, media_store_id
        """,
    )
    fun observeRootFolderTracks(volumeName: String): Flow<List<TrackEntity>>

    @Upsert
    suspend fun upsert(entities: List<TrackEntity>)

    @Delete
    suspend fun delete(entities: List<TrackEntity>)

    @Query("UPDATE tracks SET availability = :availability WHERE volume_name = :volumeName")
    suspend fun updateAvailabilityForVolume(volumeName: String, availability: String)

    @Query("UPDATE tracks SET availability = :availability")
    suspend fun updateAllAvailability(availability: String)

    @Query("SELECT DISTINCT volume_name FROM tracks ORDER BY volume_name")
    suspend fun getKnownVolumeNames(): List<String>

    @Query(
        "SELECT * FROM tracks WHERE volume_name IN (:volumeNames) " +
            "AND last_seen_sync_generation != :generation",
    )
    suspend fun getNotSeenInGeneration(
        volumeNames: List<String>,
        generation: Long,
    ): List<TrackEntity>
}

@Dao
interface HiddenTrackDao {
    @Upsert
    suspend fun insert(entity: HiddenTrackEntity)

    @Query("DELETE FROM hidden_tracks WHERE track_volume_name = :volumeName AND track_media_store_id = :mediaStoreId")
    suspend fun delete(volumeName: String, mediaStoreId: Long)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM hidden_tracks WHERE track_volume_name = :volumeName " +
            "AND track_media_store_id = :mediaStoreId)",
    )
    suspend fun exists(volumeName: String, mediaStoreId: Long): Boolean
}

@Dao
interface MediaSyncStateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGlobalStateIfAbsent(entity: MediaSyncStateEntity)

    @Query("UPDATE media_sync_state SET last_generation = last_generation + 1 WHERE state_id = 1")
    suspend fun incrementGeneration()

    @Query("SELECT last_generation FROM media_sync_state WHERE state_id = 1")
    suspend fun getGenerationOrNull(): Long?

    @Query("SELECT * FROM media_volume_sync_state ORDER BY volume_name")
    suspend fun getVolumeStates(): List<MediaVolumeSyncStateEntity>

    @Upsert
    suspend fun upsertVolumeStates(entities: List<MediaVolumeSyncStateEntity>)
}

@Dao
interface PathRuleDao {
    @Query("SELECT * FROM path_rules ORDER BY path_rule_id")
    fun observeAll(): Flow<List<PathRuleEntity>>

    @Query("SELECT * FROM path_rules ORDER BY path_rule_id")
    suspend fun getAll(): List<PathRuleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PathRuleEntity): Long

    @Query("DELETE FROM path_rules")
    suspend fun deleteAll()

    @Query("DELETE FROM path_rules WHERE path_rule_id = :pathRuleId")
    suspend fun delete(pathRuleId: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY created_at_ms DESC, playlist_id DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlist_id = :playlistId")
    suspend fun get(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlaylistEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE playlist_id = :playlistId")
    suspend fun delete(playlistId: Long)
}

@Dao
interface PlaylistTrackDao {
    @Query("SELECT * FROM playlist_tracks ORDER BY playlist_id, position")
    fun observeAll(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    fun observeForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun getForPlaylist(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entities: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun deleteAllForPlaylist(playlistId: Long)
}

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY last_played_at_ms DESC, track_volume_name, track_media_store_id")
    fun observeAll(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE track_volume_name = :volumeName AND track_media_store_id = :mediaStoreId")
    suspend fun get(volumeName: String, mediaStoreId: Long): PlayHistoryEntity?

    @Upsert
    suspend fun upsert(entity: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun deleteAll()
}

@Dao
interface PlaybackSnapshotDao {
    @Query("SELECT * FROM playback_snapshot WHERE snapshot_id = 1")
    fun observe(): Flow<PlaybackSnapshotEntity?>

    @Query("SELECT * FROM playback_snapshot WHERE snapshot_id = 1")
    suspend fun get(): PlaybackSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: PlaybackSnapshotEntity)

    @Query("DELETE FROM playback_snapshot WHERE snapshot_id = 1")
    suspend fun delete()
}
