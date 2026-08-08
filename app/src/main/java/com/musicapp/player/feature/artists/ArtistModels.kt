package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId

data class ArtistSummary(
    val id: ArtistId,
    val displayName: String,
    val trackCount: Int,
    val artworkCandidates: List<Track>,
)

/**
 * Groups tracks by the MediaStore artist id while preserving the complete
 * MediaStore artist label (including collaboration labels).
 */
object ArtistGrouping {
    fun group(tracks: List<Track>): List<ArtistSummary> =
        tracks.asSequence()
            .filter { it.artistId != null }
            .groupBy { checkNotNull(it.artistId) }
            .map { (id, artistTracks) ->
                val stableTracks = artistTracks.sortedWith(trackIdentityComparator)
                ArtistSummary(
                    id = id,
                    displayName = stableTracks.first().artistName,
                    trackCount = stableTracks.size,
                    artworkCandidates = stableTracks,
                )
            }
            .let(::sortArtistsByIndexedName)

    private val trackIdentityComparator =
        compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })

}

typealias ArtistArtworkSignature = List<ArtistArtworkCandidateSignature>

data class ArtistArtworkCandidateSignature(
    val trackId: TrackId,
    val dateModifiedMs: Long,
) {
    init {
        require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
    }
}

internal fun ArtistSummary.artworkSignature(): ArtistArtworkSignature =
    artworkCandidates
        .sortedWith(trackIdentityComparator)
        .map { track ->
            ArtistArtworkCandidateSignature(
                trackId = track.id,
                dateModifiedMs = track.dateModifiedMs,
            )
        }

internal fun List<Track>.artworkSignature(): ArtistArtworkSignature =
    sortedWith(trackIdentityComparator)
        .map { track ->
            ArtistArtworkCandidateSignature(
                trackId = track.id,
                dateModifiedMs = track.dateModifiedMs,
            )
        }

internal fun ArtistSummary.sortedArtworkCandidates(): List<Track> =
    artworkCandidates.sortedWith(trackIdentityComparator)

private val trackIdentityComparator =
    compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })
