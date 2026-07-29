package com.musicapp.player.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
  tableName = "tracks",
  primaryKeys = ["volumeName", "mediaStoreId"],
  indices = [
    Index(value = ["title"]),
    Index(value = ["artistId"]),
    Index(value = ["albumId"]),
    Index(value = ["volumeName", "albumId"]),
    Index(value = ["dateAddedEpochSeconds"]),
    Index(value = ["durationMillis"]),
    Index(value = ["relativePath"]),
    Index(value = ["volumeName", "relativePath"]),
    Index(value = ["isAvailable"]),
    Index(value = ["lastSeenGeneration"]),
  ],
)
data class TrackEntity(
  val volumeName: String,
  val mediaStoreId: Long,
  val contentUri: String,
  val displayName: String,
  val title: String,
  val artistId: Long?,
  val artistName: String?,
  val albumId: Long?,
  val albumName: String?,
  val dateAddedEpochSeconds: Long,
  val durationMillis: Long,
  val relativePath: String?,
  val mimeType: String?,
  val extension: String,
  val sizeBytes: Long,
  val dateModifiedEpochSeconds: Long,
  val isAvailable: Boolean,
  val lastSeenGeneration: Long,
)
