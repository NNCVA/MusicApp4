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
     * Artwork request for a specific track.
     */
    data class TrackArtworkRequest(
        val trackId: TrackId,
        val dateModifiedMs: Long,
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
    ) : AudioArtworkRequest {
        init {
            require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
        }
    }

    companion object {
        fun from(track: Track): TrackArtworkRequest =
            TrackArtworkRequest(track.id, track.dateModifiedMs)
    }
}

/**
 * Convenient extension to convert a [Track] into an [AudioArtworkRequest.TrackArtworkRequest].
 */
fun Track.toArtworkRequest(): AudioArtworkRequest.TrackArtworkRequest =
    AudioArtworkRequest.TrackArtworkRequest(trackId = id, dateModifiedMs = dateModifiedMs)
