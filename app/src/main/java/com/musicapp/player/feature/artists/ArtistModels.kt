package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId

import java.util.Locale

data class ArtistSummary(
    val id: ArtistId,
    val displayName: String,
    val trackCount: Int,
    val artworkCandidates: List<Track>,
)

/**
 * Splits tracks with multiple artists across common delimiters and groups them
 * into distinct artist summaries normalized by case-insensitive name.
 */
object ArtistGrouping {
    private val DELIMITER_REGEX = Regex("[/、,;&]+")

    fun splitArtistNames(artistName: String?): List<String> {
        if (artistName.isNullOrBlank()) return emptyList()
        return artistName.split(DELIMITER_REGEX)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf(artistName.trim()) }
    }

    fun group(tracks: List<Track>): List<ArtistSummary> {
        val tracksByNormalizedArtist = linkedMapOf<String, MutableList<Track>>()
        val displayNamesByNormalized = mutableMapOf<String, String>()

        for (track in tracks) {
            val names = splitArtistNames(track.artistName)
            for (name in names) {
                val normalized = name.lowercase(Locale.ROOT)
                tracksByNormalizedArtist.getOrPut(normalized) { mutableListOf() }.add(track)
                displayNamesByNormalized.putIfAbsent(normalized, name)
            }
        }

        return tracksByNormalizedArtist.map { (normalizedKey, artistTracks) ->
            val displayName = displayNamesByNormalized.getValue(normalizedKey)
            val stableTracks = artistTracks.distinctBy { it.id }.sortedWith(trackIdentityComparator)
            ArtistSummary(
                id = ArtistId(normalizedKey),
                displayName = displayName,
                trackCount = stableTracks.size,
                artworkCandidates = stableTracks,
            )
        }.let(::sortArtistsByIndexedName)
    }

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
