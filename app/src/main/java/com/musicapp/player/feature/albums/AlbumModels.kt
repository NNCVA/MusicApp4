package com.musicapp.player.feature.albums

import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.sortedBySectionText
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
        val textTieBreaker =
            compareBy<AlbumSummary>(
                { it.title.lowercase(Locale.ROOT) },
                { it.id.volumeName.lowercase(Locale.ROOT) },
                { it.id.mediaStoreId },
            )
        val sectionOrder =
            when (sort.direction) {
                CategorySortDirection.ASCENDING -> SectionSortOrder.ASCENDING
                CategorySortDirection.DESCENDING -> SectionSortOrder.DESCENDING
            }
        return when (sort.field) {
            AlbumSortField.TITLE ->
                albums.sortedBySectionText(
                    order = sectionOrder,
                    textSelector = AlbumSummary::title,
                    tieBreaker = textTieBreaker,
                )
            AlbumSortField.ARTIST ->
                albums.sortedBySectionText(
                    order = sectionOrder,
                    textSelector = AlbumSummary::artistName,
                    tieBreaker = textTieBreaker,
                )
            AlbumSortField.TRACK_COUNT -> {
                val primary = compareBy(AlbumSummary::trackCount)
                albums.sortedWith(
                    (if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed())
                        .then(textTieBreaker),
                )
            }
            AlbumSortField.DATE_ADDED -> {
                val primary = compareBy(AlbumSummary::latestDateAddedMs)
                albums.sortedWith(
                    (if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed())
                        .then(textTieBreaker),
                )
            }
        }
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
