package com.musicapp.player.feature.playlists

import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.classifySectionLabel
import com.musicapp.player.core.designsystem.component.resolveNearestPopulatedBucket
import com.musicapp.player.core.designsystem.component.sectionIndexLabelsForOrder
import com.musicapp.player.core.designsystem.component.sortedBySectionText
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.data.repository.PlaylistTrackChangeResult
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import java.util.Locale

enum class PlaylistTrackSortField {
    DEFAULT,
    TITLE,
    ARTIST,
    ALBUM,
    DURATION,
    FILE_SIZE;

    fun labelResId(): Int = when (this) {
        DEFAULT -> R.string.sort_default_order
        TITLE -> R.string.sort_title
        ARTIST -> R.string.sort_artist
        ALBUM -> R.string.sort_album
        DURATION -> R.string.sort_duration
        FILE_SIZE -> R.string.sort_file_size
    }
}

enum class PlaylistTrackSortDirection {
    ASCENDING,
    DESCENDING;

    fun labelResId(): Int = when (this) {
        ASCENDING -> R.string.sort_direction_ascending
        DESCENDING -> R.string.sort_direction_descending
    }
}

data class PlaylistTrackSort(
    val field: PlaylistTrackSortField = PlaylistTrackSortField.DEFAULT,
    val direction: PlaylistTrackSortDirection = PlaylistTrackSortDirection.ASCENDING,
) {
    companion object {
        val DEFAULT = PlaylistTrackSort(PlaylistTrackSortField.DEFAULT, PlaylistTrackSortDirection.ASCENDING)
    }
}

data class PlaylistSection(
    val label: String,
    val tracks: List<Track>,
)

data class PlaylistPlaybackPreparation(
    val context: PlaybackContext?,
    val playedCount: Int,
    val skippedCount: Int,
)

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val sortedTracks: List<Track> = emptyList(),
    val filteredTracks: List<Track> = emptyList(),
    val sections: List<PlaylistSection> = emptyList(),
    val sectionPositions: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val sort: PlaylistTrackSort = PlaylistTrackSort.DEFAULT,
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isLibraryLoaded: Boolean = false,
    val allPlaylists: List<Playlist> = emptyList(),
    val operationMessage: PlaylistOperationMessage? = null,
    val lastRemovalResult: PlaylistTrackChangeResult? = null,
    val batchResult: BatchTrackActionResult? = null,
    val isBatchActionRunning: Boolean = false,
    val playbackFeedback: PlaylistPlaybackPreparation? = null,
    val infoTrack: Track? = null,
    val infoMetadata: AdvancedTrackMetadata? = null,
    val isInfoLoading: Boolean = false,
) {
    val totalDurationMs: Long
        get() = tracks.sumOf { it.durationMs }

    val displayTracks: List<Track>
        get() = if (isSearching) filteredTracks else sortedTracks

    val selectedTrackIdsInOrder: List<TrackId>
        get() = displayTracks
            .map(Track::id)
            .filter(selectedTrackIds::contains)
}

object PlaylistPlaybackContextFactory {
    fun create(
        playlist: Playlist,
        tracks: List<Track>,
        selectedTrackId: TrackId? = null,
        shuffle: Boolean = false,
        customTrackOrder: List<TrackId>? = null,
    ): PlaybackContext? = prepare(playlist, tracks, selectedTrackId, shuffle, customTrackOrder).context

    fun prepare(
        playlist: Playlist,
        tracks: List<Track>,
        selectedTrackId: TrackId? = null,
        shuffle: Boolean = false,
        customTrackOrder: List<TrackId>? = null,
    ): PlaylistPlaybackPreparation {
        val tracksById = tracks.associateBy(Track::id)
        val baseTrackIds = customTrackOrder ?: playlist.trackIds
        val availableTrackIds = baseTrackIds.filter { trackId ->
            tracksById[trackId]?.availability == Availability.AVAILABLE
        }
        val targetOrder = if (shuffle) {
            val shuffled = availableTrackIds.shuffled()
            if (selectedTrackId != null && selectedTrackId in shuffled) {
                listOf(selectedTrackId) + (shuffled - selectedTrackId)
            } else {
                shuffled
            }
        } else {
            availableTrackIds
        }
        val skippedCount = (playlist.trackIds.size - availableTrackIds.size).coerceAtLeast(0)
        return PlaylistPlaybackPreparation(
            context =
                targetOrder.takeIf { it.isNotEmpty() }?.let {
                    PlaybackContext(
                        source = PlaybackContextSource.PLAYLIST,
                        sourceId = playlist.id.value.toString(),
                        orderedTrackIds = targetOrder,
                        selectedTrackId = selectedTrackId?.takeIf(targetOrder::contains) ?: targetOrder.first(),
                    )
                },
            playedCount = targetOrder.size,
            skippedCount = skippedCount,
        )
    }
}

fun Track.matchesPlaylistSearch(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return title.contains(trimmed, ignoreCase = true) ||
        artistName.contains(trimmed, ignoreCase = true) ||
        albumTitle?.contains(trimmed, ignoreCase = true) == true ||
        displayName.contains(trimmed, ignoreCase = true)
}

private val textTieBreaker = compareBy<Track>(
    { it.title.lowercase(Locale.ROOT) },
    { it.artistName.lowercase(Locale.ROOT) },
    { it.id.volumeName },
    { it.id.mediaStoreId },
)

fun sortPlaylistTracks(
    tracks: List<Track>,
    sort: PlaylistTrackSort,
): List<Track> {
    if (tracks.isEmpty()) return emptyList()
    return when (sort.field) {
        PlaylistTrackSortField.DEFAULT -> {
            if (sort.direction == PlaylistTrackSortDirection.DESCENDING) tracks.reversed() else tracks
        }
        PlaylistTrackSortField.TITLE -> {
            val order = if (sort.direction == PlaylistTrackSortDirection.ASCENDING) SectionSortOrder.ASCENDING else SectionSortOrder.DESCENDING
            tracks.sortedBySectionText(order, Track::title, textTieBreaker)
        }
        PlaylistTrackSortField.ARTIST -> {
            val order = if (sort.direction == PlaylistTrackSortDirection.ASCENDING) SectionSortOrder.ASCENDING else SectionSortOrder.DESCENDING
            tracks.sortedBySectionText(order, Track::artistName, textTieBreaker)
        }
        PlaylistTrackSortField.ALBUM -> {
            val order = if (sort.direction == PlaylistTrackSortDirection.ASCENDING) SectionSortOrder.ASCENDING else SectionSortOrder.DESCENDING
            tracks.sortedBySectionText(order, { it.albumTitle.orEmpty() }, textTieBreaker)
        }
        PlaylistTrackSortField.DURATION -> {
            val primary = compareBy<Track> { it.durationMs }
            tracks.sortedWith((if (sort.direction == PlaylistTrackSortDirection.ASCENDING) primary else primary.reversed()).then(textTieBreaker))
        }
        PlaylistTrackSortField.FILE_SIZE -> {
            val primary = compareBy<Track> { it.sizeBytes }
            tracks.sortedWith((if (sort.direction == PlaylistTrackSortDirection.ASCENDING) primary else primary.reversed()).then(textTieBreaker))
        }
    }
}

fun playlistSectionLabelForTrack(track: Track, field: PlaylistTrackSortField): String? =
    when (field) {
        PlaylistTrackSortField.TITLE -> classifySectionLabel(track.title)
        PlaylistTrackSortField.ARTIST -> classifySectionLabel(track.artistName)
        PlaylistTrackSortField.ALBUM -> classifySectionLabel(track.albumTitle)
        PlaylistTrackSortField.DEFAULT,
        PlaylistTrackSortField.DURATION,
        PlaylistTrackSortField.FILE_SIZE -> null
    }

fun groupPlaylistTracksIntoSections(
    tracks: List<Track>,
    field: PlaylistTrackSortField,
    direction: PlaylistTrackSortDirection = PlaylistTrackSortDirection.ASCENDING,
): List<PlaylistSection> {
    if (field !in listOf(PlaylistTrackSortField.TITLE, PlaylistTrackSortField.ARTIST, PlaylistTrackSortField.ALBUM)) {
        return emptyList()
    }
    val tracksByLabel = linkedMapOf<String, MutableList<Track>>()
    tracks.forEach { track ->
        val label = playlistSectionLabelForTrack(track, field) ?: return@forEach
        tracksByLabel.getOrPut(label, ::mutableListOf) += track
    }
    val order = if (direction == PlaylistTrackSortDirection.ASCENDING) SectionSortOrder.ASCENDING else SectionSortOrder.DESCENDING
    val orderedLabels = sectionIndexLabelsForOrder(order)
    return orderedLabels.mapNotNull { label ->
        tracksByLabel[label]?.let { sectionTracks ->
            PlaylistSection(label = label, tracks = sectionTracks.toList())
        }
    }
}

fun playlistSectionStartPositions(
    sections: List<PlaylistSection>,
    direction: PlaylistTrackSortDirection = PlaylistTrackSortDirection.ASCENDING,
): Map<String, Int> {
    val order = if (direction == PlaylistTrackSortDirection.ASCENDING) SectionSortOrder.ASCENDING else SectionSortOrder.DESCENDING
    val labels = sectionIndexLabelsForOrder(order)
    val starts = linkedMapOf<String, Int>()
    var itemPosition = 0
    sections.forEach { section ->
        starts.putIfAbsent(section.label, itemPosition)
        itemPosition += section.tracks.size
    }
    val lastPosition = (itemPosition - 1).coerceAtLeast(0)

    val populatedIndices = sections.mapNotNull { section ->
        val idx = labels.indexOf(section.label)
        if (idx >= 0) idx else null
    }.toSet()

    return buildMap {
        labels.forEachIndexed { bucketIndex, label ->
            val position = starts[label] ?: run {
                val nearestBucketIndex = resolveNearestPopulatedBucket(
                    targetBucketIndex = bucketIndex,
                    populatedBucketIndices = populatedIndices,
                    dragDirection = 0,
                    bucketCount = labels.size,
                )
                val nearestLabel = labels.getOrNull(nearestBucketIndex)
                nearestLabel?.let { starts[it] } ?: lastPosition
            }
            put(label, position.coerceIn(0, lastPosition))
        }
    }
}

fun playlistSectionLabelAtPosition(
    sections: List<PlaylistSection>,
    itemPosition: Int,
): String? {
    if (sections.isEmpty()) return null
    val totalTracks = sections.sumOf { it.tracks.size }
    val safePosition = itemPosition.coerceIn(0, (totalTracks - 1).coerceAtLeast(0))
    var sectionStart = 0
    sections.forEach { section ->
        val sectionEnd = sectionStart + section.tracks.size
        if (safePosition < sectionEnd) return section.label
        sectionStart = sectionEnd
    }
    return sections.last().label
}
