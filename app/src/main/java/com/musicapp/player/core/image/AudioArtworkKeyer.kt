package com.musicapp.player.core.image

import coil3.key.Keyer
import coil3.request.Options
import com.musicapp.player.core.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates unique cache keys for [AudioArtworkRequest] objects in the Coil 3 pipeline.
 */
@Singleton
class AudioArtworkKeyer @Inject constructor() : Keyer<AudioArtworkRequest> {

    override fun key(data: AudioArtworkRequest, options: Options): String =
        when (data) {
            is AudioArtworkRequest.TrackArtworkRequest ->
                "artwork:track:${data.trackId.volumeName}:${data.trackId.mediaStoreId}:${data.dateModifiedMs}"

            is AudioArtworkRequest.AlbumArtworkRequest -> {
                val repPart = data.representativeTrackId?.let { "${it.volumeName}:${it.mediaStoreId}" } ?: "none"
                "artwork:album:${data.albumId.volumeName}:${data.albumId.mediaStoreId}:$repPart:${data.dateModifiedMs}"
            }

            is AudioArtworkRequest.ArtistArtworkRequest -> {
                val repPart = data.representativeTrackId?.let { "${it.volumeName}:${it.mediaStoreId}" } ?: "none"
                "artwork:artist:${data.artistName}:$repPart:${data.dateModifiedMs}"
            }

            is AudioArtworkRequest.PlaylistArtworkRequest -> {
                val repPart = data.representativeTrackId?.let { "${it.volumeName}:${it.mediaStoreId}" } ?: "none"
                "artwork:playlist:${data.playlistId.value}:$repPart:${data.dateModifiedMs}"
            }
        }

    /**
     * Overload for direct [Track] instances.
     */
    fun key(track: Track, options: Options): String =
        "artwork:track:${track.id.volumeName}:${track.id.mediaStoreId}:${track.dateModifiedMs}"

    /**
     * Polymorphic key generation helper with safe fallback for unsupported data models.
     */
    fun keyFromAny(data: Any, options: Options): String? =
        when (data) {
            is AudioArtworkRequest -> key(data, options)
            is Track -> key(data, options)
            else -> null
        }
}

/**
 * Keyer registered in Coil to directly process [Track] instances passed to AsyncImage.
 */
@Singleton
class TrackArtworkKeyer @Inject constructor() : Keyer<Track> {
    override fun key(data: Track, options: Options): String =
        "artwork:track:${data.id.volumeName}:${data.id.mediaStoreId}:${data.dateModifiedMs}"
}
