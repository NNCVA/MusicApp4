package com.musicapp.player.feature.category

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import java.util.Locale

enum class CategorySortDirection { ASCENDING, DESCENDING }

enum class CategoryTrackSortField { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

data class CategoryTrackSort(
    val field: CategoryTrackSortField = CategoryTrackSortField.TITLE,
    val direction: CategorySortDirection = CategorySortDirection.ASCENDING,
)

fun sortCategoryTracks(tracks: List<Track>, sort: CategoryTrackSort): List<Track> {
    val textTieBreaker =
        compareBy<Track>(
            { it.title.lowercase(Locale.ROOT) },
            { it.id.volumeName.lowercase(Locale.ROOT) },
            { it.id.mediaStoreId },
        )
    val sectionOrder =
        when (sort.direction) {
            CategorySortDirection.ASCENDING -> com.musicapp.player.core.designsystem.component.SectionSortOrder.ASCENDING
            CategorySortDirection.DESCENDING -> com.musicapp.player.core.designsystem.component.SectionSortOrder.DESCENDING
        }
    val comparator =
        when (sort.field) {
            CategoryTrackSortField.TITLE ->
                com.musicapp.player.core.designsystem.component.createSectionTextComparator(
                    sectionOrder,
                    Track::title,
                    textTieBreaker,
                )
            CategoryTrackSortField.ARTIST ->
                com.musicapp.player.core.designsystem.component.createSectionTextComparator(
                    sectionOrder,
                    Track::artistName,
                    textTieBreaker,
                )
            CategoryTrackSortField.ALBUM ->
                com.musicapp.player.core.designsystem.component.createSectionTextComparator(
                    sectionOrder,
                    { it.albumTitle.orEmpty() },
                    textTieBreaker,
                )
            CategoryTrackSortField.DATE_ADDED -> {
                val primary = compareBy<Track> { it.dateAddedMs }
                (if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed())
                    .then(textTieBreaker)
            }
            CategoryTrackSortField.DURATION -> {
                val primary = compareBy<Track> { it.durationMs }
                (if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed())
                    .then(textTieBreaker)
            }
        }
    return tracks.sortedWith(comparator)
}

fun CategoryTrackSort.next(field: CategoryTrackSortField): CategoryTrackSort =
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
        CategoryTrackSort(
            field = field,
            direction =
                if (field == CategoryTrackSortField.DATE_ADDED) {
                    CategorySortDirection.DESCENDING
                } else {
                    CategorySortDirection.ASCENDING
                },
        )
    }

object CategoryPlaybackContextFactory {
    fun create(
        source: PlaybackContextSource,
        sourceId: String,
        tracks: List<Track>,
        selectedTrackId: TrackId? = null,
    ): PlaybackContext? {
        require(source in supportedSources) { "unsupported category playback source: $source" }
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(tracks.map(Track::id).distinct().size == tracks.size) {
            "category tracks must have unique TrackIds"
        }
        val orderedTrackIds = tracks
            .filter { it.availability == Availability.AVAILABLE }
            .map(Track::id)
        if (orderedTrackIds.isEmpty()) return null
        val selected = selectedTrackId?.takeIf(orderedTrackIds::contains) ?: orderedTrackIds.first()
        return PlaybackContext(
            source = source,
            orderedTrackIds = orderedTrackIds,
            selectedTrackId = selected,
            sourceId = sourceId,
        )
    }

    private val supportedSources =
        setOf(
            PlaybackContextSource.ALBUM,
            PlaybackContextSource.ARTIST,
            PlaybackContextSource.FOLDER,
        )
}
