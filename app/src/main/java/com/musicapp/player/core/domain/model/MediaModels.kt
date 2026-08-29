package com.musicapp.player.core.domain.model

data class TrackId(
    val volumeName: String,
    val mediaStoreId: Long,
) {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        require(mediaStoreId > 0) { "mediaStoreId must be positive" }
    }
}

data class AlbumId(
    val volumeName: String,
    val mediaStoreId: Long,
) {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
        require(mediaStoreId > 0) { "mediaStoreId must be positive" }
    }
}

@JvmInline
value class ArtistId(val name: String) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }
}

enum class Availability {
    AVAILABLE,
    TEMPORARILY_UNAVAILABLE,
}

data class Track(
    val id: TrackId,
    val title: String,
    val artistName: String,
    val artistMediaStoreId: Long? = null,
    val albumTitle: String? = null,
    val albumId: AlbumId? = null,
    val durationMs: Long,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val relativePath: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val availability: Availability = Availability.AVAILABLE,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(artistName.isNotBlank()) { "artistName must not be blank" }
        require(albumTitle == null || albumTitle.isNotBlank()) { "albumTitle must be null or non-blank" }
        require(durationMs > 0) { "durationMs must be positive" }
        require(dateAddedMs >= 0) { "dateAddedMs must not be negative" }
        require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(sizeBytes >= 0) { "sizeBytes must not be negative" }
        require(mimeType == null || mimeType.isNotBlank()) { "mimeType must be null or non-blank" }
        require(albumId == null || albumId.volumeName == id.volumeName) {
            "albumId and track id must belong to the same volume"
        }
    }
}
