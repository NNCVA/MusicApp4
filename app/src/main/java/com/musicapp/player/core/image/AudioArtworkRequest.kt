package com.musicapp.player.core.image

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId

/**
 * Sealed interface for audio artwork requests in Coil 3 pipeline.
 */
sealed interface AudioArtworkRequest {

    /**
     * Selects the artwork size and extraction path for the request.
     */
    val rendition: ArtworkRendition

    /**
     * Artwork request for a specific track.
     */
    data class TrackArtworkRequest(
        val trackId: TrackId,
        val dateModifiedMs: Long,
        override val rendition: ArtworkRendition = ArtworkRendition.FULL_SIZE,
    ) : AudioArtworkRequest {
        init {
            require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
        }
    }

    /**
     * Artwork request for an album.
     */
    data class AlbumArtworkRequest(
        val albumId: AlbumId,
        val representativeTrackId: TrackId? = null,
        val dateModifiedMs: Long = 0L,
        override val rendition: ArtworkRendition = ArtworkRendition.FULL_SIZE,
    ) : AudioArtworkRequest {
        init {
            require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
        }
    }

    /**
     * Artwork request for an artist.
     */
    data class ArtistArtworkRequest(
        val artistName: String,
        val representativeTrackId: TrackId? = null,
        val dateModifiedMs: Long = 0L,
        override val rendition: ArtworkRendition = ArtworkRendition.FULL_SIZE,
    ) : AudioArtworkRequest {
        init {
            require(artistName.isNotBlank()) { "artistName must not be blank" }
            require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
        }
    }

    /**
     * Artwork request for a playlist.
     */
    data class PlaylistArtworkRequest(
        val playlistId: PlaylistId,
        val representativeTrackId: TrackId? = null,
        val dateModifiedMs: Long = 0L,
        override val rendition: ArtworkRendition = ArtworkRendition.FULL_SIZE,
    ) : AudioArtworkRequest {
        init {
            require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
        }
    }

    companion object {
        fun from(
            track: Track,
            rendition: ArtworkRendition = ArtworkRendition.FULL_SIZE,
        ): TrackArtworkRequest =
            TrackArtworkRequest(track.id, track.dateModifiedMs, rendition)
    }
}

/**
 * Artwork size and extraction policy used by the image pipeline.
 */
enum class ArtworkRendition(
    val cacheKey: String,
) {
    LIST_THUMBNAIL("list_thumbnail"),
    GRID_THUMBNAIL("grid_thumbnail"),
    FULL_SIZE("full_size"),
}

/**
 * Convenient extension to convert a [Track] into an [AudioArtworkRequest.TrackArtworkRequest].
 */
fun Track.toArtworkRequest(
    rendition: ArtworkRendition = ArtworkRendition.FULL_SIZE,
): AudioArtworkRequest.TrackArtworkRequest =
    AudioArtworkRequest.TrackArtworkRequest(
        trackId = id,
        dateModifiedMs = dateModifiedMs,
        rendition = rendition,
    )
