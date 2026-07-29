package com.musicapp.player.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "playlists",
  indices = [Index(value = ["nameComparisonKey"], unique = true), Index(value = ["createdAtEpochMillis"])],
)
data class PlaylistEntity(
  @androidx.room.PrimaryKey val playlistId: String,
  val displayName: String,
  val nameComparisonKey: String,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
)

@Entity(
  tableName = "playlist_tracks",
  primaryKeys = ["playlistId", "trackVolumeName", "trackMediaStoreId"],
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["playlistId"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["playlistId"]),
    Index(value = ["playlistId", "position"], unique = true),
    Index(value = ["trackVolumeName", "trackMediaStoreId"]),
  ],
)
data class PlaylistTrackEntity(
  val playlistId: String,
  val trackVolumeName: String,
  val trackMediaStoreId: Long,
  val position: Int,
  val addedAtEpochMillis: Long,
)

@Entity(
  tableName = "play_history",
  primaryKeys = ["trackVolumeName", "trackMediaStoreId"],
  indices = [Index(value = ["lastPlayedAtEpochMillis"])],
)
data class PlayHistoryEntity(
  val trackVolumeName: String,
  val trackMediaStoreId: Long,
  val lastPlayedAtEpochMillis: Long,
  val playCount: Long,
)

@Entity(
  tableName = "hidden_tracks",
  primaryKeys = ["trackVolumeName", "trackMediaStoreId"],
)
data class HiddenTrackEntity(
  val trackVolumeName: String,
  val trackMediaStoreId: Long,
  val hiddenAtEpochMillis: Long,
)

@Entity(
  tableName = "path_rules",
  indices = [
    Index(value = ["ruleType"]),
    Index(value = ["volumeName", "relativePath", "ruleType"], unique = true),
  ],
)
data class PathRuleEntity(
  @androidx.room.PrimaryKey val pathRuleId: String,
  val volumeName: String,
  val relativePath: String,
  val ruleType: String,
  val createdAtEpochMillis: Long,
)

@Entity(tableName = "playback_snapshot")
data class PlaybackSnapshotEntity(
  @androidx.room.PrimaryKey val snapshotId: Int = ACTIVE_SNAPSHOT_ID,
  val formatVersion: Int,
  val originalQueueJson: String,
  val shuffledQueueJson: String,
  val playMode: String,
  val currentTrackVolumeName: String?,
  val currentTrackMediaStoreId: Long?,
  val currentQueueIndex: Int?,
  val positionMs: Long,
  val playbackInstanceId: String?,
  val accumulatedPlayedMs: Long,
  val historyRecordedForInstance: Boolean,
) {
  companion object {
    const val ACTIVE_SNAPSHOT_ID = 1
    const val CURRENT_FORMAT_VERSION = 1
  }
}
