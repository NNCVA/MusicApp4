package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategorySortDirection
import java.util.Locale

enum class ArtistSortField { NAME, TRACK_COUNT, ALBUM_COUNT }

data class ArtistSort(
    val field: ArtistSortField = ArtistSortField.NAME,
    val direction: CategorySortDirection = CategorySortDirection.ASCENDING,
)

data class ArtistSummary(
    val id: ArtistId,
    val displayName: String,
    val trackCount: Int,
    val albumCount: Int,
)

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
                    albumCount = stableTracks.mapNotNull(Track::albumId).distinct().size,
                )
            }

    fun sorted(artists: List<ArtistSummary>, sort: ArtistSort): List<ArtistSummary> {
        val primary =
            when (sort.field) {
                ArtistSortField.NAME -> compareBy<ArtistSummary> { it.displayName.lowercase(Locale.ROOT) }
                ArtistSortField.TRACK_COUNT -> compareBy(ArtistSummary::trackCount)
                ArtistSortField.ALBUM_COUNT -> compareBy(ArtistSummary::albumCount)
            }
        val directed = if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed()
        return artists.sortedWith(
            directed.thenBy { it.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.id.mediaStoreId },
        )
    }

    private val trackIdentityComparator =
        compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })
}

internal fun ArtistSort.next(field: ArtistSortField): ArtistSort =
    if (this.field == field) {
        copy(
            direction =
                if (direction == CategorySortDirection.ASCENDING) {
                    CategorySortDirection.DESCENDING
                } else {
                    CategorySortDirection.ASCENDING
                },
        )
    } else {
        ArtistSort(
            field = field,
            direction =
                if (field == ArtistSortField.NAME) {
                    CategorySortDirection.ASCENDING
                } else {
                    CategorySortDirection.DESCENDING
                },
        )
    }
