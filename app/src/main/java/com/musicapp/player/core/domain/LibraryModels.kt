package com.musicapp.player.core.domain

data class Track(
  val id: TrackId,
  val contentUri: String,
  val title: String,
  val displayName: String,
  val extension: String,
  val artistId: ArtistId?,
  val artistName: String?,
  val albumId: AlbumId?,
  val albumName: String?,
  val durationMs: Long,
  val dateAddedMs: Long,
  val modifiedAtMs: Long,
  val relativePath: String,
  val mimeType: String?,
  val sizeBytes: Long,
  val isAvailable: Boolean,
) {
  init {
    require(contentUri.isNotBlank()) { "contentUri must not be blank" }
    require(title.isNotBlank()) { "title must not be blank" }
    require(displayName.isNotBlank()) { "displayName must not be blank" }
    require(extension.isNotBlank()) { "extension must not be blank" }
    require(durationMs > 0L) { "durationMs must be positive" }
    require(dateAddedMs >= 0L) { "dateAddedMs must be non-negative" }
    require(modifiedAtMs >= 0L) { "modifiedAtMs must be non-negative" }
    require(sizeBytes >= 0L) { "sizeBytes must be non-negative" }
  }
}

enum class PathRuleType {
  INCLUDE,
  EXCLUDE,
}

data class PathRule(
  val id: PathRuleId,
  val volumeName: String,
  val relativeDirectory: String,
  val type: PathRuleType,
) {
  init {
    require(volumeName.isNotBlank()) { "volumeName must not be blank" }
    require(relativeDirectory.isNotBlank()) { "relativeDirectory must not be blank" }
  }
}
