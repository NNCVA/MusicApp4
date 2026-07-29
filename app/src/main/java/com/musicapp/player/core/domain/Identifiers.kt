package com.musicapp.player.core.domain

data class TrackId(
  val volumeName: String,
  val mediaStoreId: Long,
) {
  init {
    require(volumeName.isNotBlank()) { "volumeName must not be blank" }
    require(mediaStoreId >= 0L) { "mediaStoreId must be non-negative" }
  }
}

data class AlbumId(
  val volumeName: String,
  val mediaStoreAlbumId: Long,
) {
  init {
    require(volumeName.isNotBlank()) { "volumeName must not be blank" }
    require(mediaStoreAlbumId >= 0L) { "mediaStoreAlbumId must be non-negative" }
  }
}

@JvmInline
value class ArtistId(val mediaStoreArtistId: Long) {
  init {
    require(mediaStoreArtistId >= 0L) { "mediaStoreArtistId must be non-negative" }
  }
}

@JvmInline
value class PlaylistId(val value: String) {
  init {
    require(value.isNotBlank()) { "PlaylistId must not be blank" }
  }
}

@JvmInline
value class PlayInstanceId(val value: String) {
  init {
    require(value.isNotBlank()) { "PlayInstanceId must not be blank" }
  }
}

@JvmInline
value class PathRuleId(val value: String) {
  init {
    require(value.isNotBlank()) { "PathRuleId must not be blank" }
  }
}
