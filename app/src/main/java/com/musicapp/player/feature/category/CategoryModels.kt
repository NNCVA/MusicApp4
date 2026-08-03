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
    val primary =
        when (sort.field) {
            CategoryTrackSortField.TITLE -> compareBy<Track> { it.title.lowercase(Locale.ROOT) }
            CategoryTrackSortField.ARTIST -> compareBy<Track> { it.artistName.lowercase(Locale.ROOT) }
            CategoryTrackSortField.ALBUM -> compareBy<Track> { it.albumTitle.orEmpty().lowercase(Locale.ROOT) }
            CategoryTrackSortField.DATE_ADDED -> compareBy(Track::dateAddedMs)
            CategoryTrackSortField.DURATION -> compareBy(Track::durationMs)
        }
    val directed = if (sort.direction == CategorySortDirection.ASCENDING) primary else primary.reversed()
    return tracks.sortedWith(
        directed.thenBy { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.id.volumeName }
            .thenBy { it.id.mediaStoreId },
    )
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
