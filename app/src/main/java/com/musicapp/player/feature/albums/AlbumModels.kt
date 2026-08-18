package com.musicapp.player.feature.albums

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.feature.category.CategorySortDirection
import java.util.Locale

enum class AlbumSortField { TITLE, ARTIST, TRACK_COUNT, DATE_ADDED }

data class AlbumSort(
    val field: AlbumSortField = AlbumSortField.TITLE,
    val direction: CategorySortDirection = CategorySortDirection.ASCENDING,
)

data class AlbumSummary(
    val id: AlbumId,
    val title: String,
    val artistName: String,
    val trackCount: Int,
    val latestDateAddedMs: Long,
    val representativeTrack: Track,
)

object AlbumGrouping {
    fun group(tracks: List<Track>): List<AlbumSummary> =
        tracks.asSequence()
            .filter { it.albumId != null }
            .groupBy { checkNotNull(it.albumId) }
            .map { (id, albumTracks) ->
                val stableTracks = albumTracks.sortedWith(trackIdentityComparator)
                AlbumSummary(
                    id = id,
                    title = stableTracks.firstNotNullOfOrNull(Track::albumTitle) ?: stableTracks.first().title,
                    artistName = stableTracks.first().artistName,
                    trackCount = stableTracks.size,
                    latestDateAddedMs = stableTracks.maxOf(Track::dateAddedMs),
                    representativeTrack = stableTracks.first(),
                )
            }

    fun sorted(albums: List<AlbumSummary>, sort: AlbumSort): List<AlbumSummary> {
        val primary =
            when (sort.field) {
                AlbumSortField.TITLE -> compareBy<AlbumSummary> { it.title.lowercase(Locale.ROOT) }
                AlbumSortField.ARTIST -> compareBy<AlbumSummary> { it.artistName.lowercase(Locale.ROOT) }
                AlbumSortField.TRACK_COUNT -> compareBy(AlbumSummary::trackCount)
                AlbumSortField.DATE_ADDED -> compareBy(AlbumSummary::latestDateAddedMs)
            }
        val directed = if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed()
        return albums.sortedWith(
            directed.thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id.volumeName }
                .thenBy { it.id.mediaStoreId },
        )
    }

    private val trackIdentityComparator =
        compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })
}

internal fun AlbumSort.next(field: AlbumSortField): AlbumSort =
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
        AlbumSort(
            field = field,
            direction =
                if (field == AlbumSortField.DATE_ADDED || field == AlbumSortField.TRACK_COUNT) {
                    CategorySortDirection.DESCENDING
                } else {
                    CategorySortDirection.ASCENDING
                },
        )
    }
