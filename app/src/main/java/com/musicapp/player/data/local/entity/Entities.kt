package com.musicapp.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tracks",
    primaryKeys = ["volume_name", "media_store_id"],
    indices = [
        Index("title"),
        Index("artist_media_store_id"),
        Index(value = ["album_volume_name", "album_media_store_id"]),
        Index("date_added_ms"),
        Index("duration_ms"),
        Index(value = ["volume_name", "relative_path"]),
    ],
)
data class TrackEntity(
    @ColumnInfo(name = "volume_name") val volumeName: String,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "artist_media_store_id") val artistMediaStoreId: Long?,
    @ColumnInfo(name = "album_title") val albumTitle: String?,
    @ColumnInfo(name = "album_volume_name") val albumVolumeName: String?,
    @ColumnInfo(name = "album_media_store_id") val albumMediaStoreId: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "date_added_ms") val dateAddedMs: Long,
    @ColumnInfo(name = "date_modified_ms") val dateModifiedMs: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "availability") val availability: String,
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["normalized_name"], unique = true)],
)
data class PlaylistEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "playlist_id") val playlistId: Long = 0,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "normalized_name", collate = ColumnInfo.NOCASE) val normalizedName: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_volume_name", "track_media_store_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlist_id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["volume_name", "media_store_id"],
            childColumns = ["track_volume_name", "track_media_store_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlist_id", "position"], unique = true),
        Index(value = ["track_volume_name", "track_media_store_id"]),
    ],
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_volume_name") val trackVolumeName: String,
    @ColumnInfo(name = "track_media_store_id") val trackMediaStoreId: Long,
    @ColumnInfo(name = "position") val position: Int,
)

@Entity(
    tableName = "play_history",
    primaryKeys = ["track_volume_name", "track_media_store_id"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["volume_name", "media_store_id"],
            childColumns = ["track_volume_name", "track_media_store_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("last_played_at_ms")],
)
data class PlayHistoryEntity(
    @ColumnInfo(name = "track_volume_name") val trackVolumeName: String,
    @ColumnInfo(name = "track_media_store_id") val trackMediaStoreId: Long,
    @ColumnInfo(name = "last_played_at_ms") val lastPlayedAtMs: Long,
    @ColumnInfo(name = "play_count") val playCount: Long,
)

@Entity(
    tableName = "hidden_tracks",
    primaryKeys = ["track_volume_name", "track_media_store_id"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["volume_name", "media_store_id"],
            childColumns = ["track_volume_name", "track_media_store_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HiddenTrackEntity(
    @ColumnInfo(name = "track_volume_name") val trackVolumeName: String,
    @ColumnInfo(name = "track_media_store_id") val trackMediaStoreId: Long,
    @ColumnInfo(name = "hidden_at_ms") val hiddenAtMs: Long,
)

@Entity(
    tableName = "path_rules",
    indices = [Index(value = ["volume_name", "directory", "kind"], unique = true)],
)
data class PathRuleEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "path_rule_id") val pathRuleId: Long = 0,
    @ColumnInfo(name = "volume_name") val volumeName: String,
    @ColumnInfo(name = "directory") val directory: String,
    @ColumnInfo(name = "kind") val kind: String,
)

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "snapshot_id") val snapshotId: Int = ACTIVE_SNAPSHOT_ID,
    @ColumnInfo(name = "original_queue_json") val originalQueueJson: String,
    @ColumnInfo(name = "stable_shuffle_sequence_json") val stableShuffleSequenceJson: String,
    @ColumnInfo(name = "current_queue_item_id") val currentQueueItemId: Long?,
    @ColumnInfo(name = "shuffle_round") val shuffleRound: Long,
    @ColumnInfo(name = "shuffle_cursor") val shuffleCursor: Int?,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "playback_mode") val playbackMode: String,
    @ColumnInfo(name = "playback_instance_json") val playbackInstanceJson: String?,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "playback_resumption_allowed") val playbackResumptionAllowed: Boolean,
) {
    companion object {
        const val ACTIVE_SNAPSHOT_ID = 1
    }
}
