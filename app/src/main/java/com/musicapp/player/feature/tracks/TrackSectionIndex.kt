package com.musicapp.player.feature.tracks

import com.musicapp.player.core.designsystem.component.SECTION_INDEX_BUCKET_COUNT
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.classifySectionLabel
import com.musicapp.player.core.designsystem.component.resolveNearestPopulatedBucket
import com.musicapp.player.core.designsystem.component.sectionIndexLabelsForOrder
import com.musicapp.player.core.domain.model.Track

data class TrackSection(
    val label: String,
    val tracks: List<Track>,
)

internal fun sectionLabelForTrack(track: Track, field: TrackSortField): String? =
    when (field) {
        TrackSortField.TITLE -> classifySectionLabel(track.title)
        TrackSortField.ARTIST -> classifySectionLabel(track.artistName)
        TrackSortField.ALBUM -> classifySectionLabel(track.albumTitle)
        TrackSortField.DATE_ADDED,
        TrackSortField.DURATION,
        -> null
    }

internal fun trackSortDirectionToSectionOrder(direction: TrackSortDirection): SectionSortOrder =
    when (direction) {
        TrackSortDirection.ASCENDING -> SectionSortOrder.ASCENDING
        TrackSortDirection.DESCENDING -> SectionSortOrder.DESCENDING
    }

internal fun groupTracksIntoSections(
    tracks: List<Track>,
    field: TrackSortField,
    direction: TrackSortDirection = TrackSortDirection.ASCENDING,
): List<TrackSection> {
    val tracksByLabel = linkedMapOf<String, MutableList<Track>>()
    tracks.forEach { track ->
        val label = sectionLabelForTrack(track, field) ?: return@forEach
        tracksByLabel.getOrPut(label, ::mutableListOf) += track
    }
    val order = trackSortDirectionToSectionOrder(direction)
    val orderedLabels = sectionIndexLabelsForOrder(order)
    return orderedLabels.mapNotNull { label ->
        tracksByLabel[label]?.let { sectionTracks ->
            TrackSection(label = label, tracks = sectionTracks.toList())
        }
    }
}

internal fun sectionIndexLabels(direction: TrackSortDirection = TrackSortDirection.ASCENDING): List<String> =
    sectionIndexLabelsForOrder(trackSortDirectionToSectionOrder(direction))

internal fun sectionIndexLabels(
    @Suppress("UNUSED_PARAMETER") sections: List<TrackSection>,
    direction: TrackSortDirection = TrackSortDirection.ASCENDING,
): List<String> = sectionIndexLabels(direction)

internal fun sectionStartPositions(
    sections: List<TrackSection>,
    direction: TrackSortDirection = TrackSortDirection.ASCENDING,
    leadingItemCount: Int = 0,
): Map<String, Int> {
    val labels = sectionIndexLabels(direction)
    val starts = linkedMapOf<String, Int>()
    var itemPosition = leadingItemCount.coerceAtLeast(0)
    sections.forEach { section ->
        starts.putIfAbsent(section.label, itemPosition)
        itemPosition += section.tracks.size
    }
    val itemCount = itemPosition
    val lastPosition = (itemCount - 1).coerceAtLeast(0)

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

internal fun sectionLabelAtPosition(
    sections: List<TrackSection>,
    itemPosition: Int,
    leadingItemCount: Int = 0,
): String? {
    val nonEmpty = sections.filter { it.tracks.isNotEmpty() }
    if (nonEmpty.isEmpty()) return null
    val safeLeading = leadingItemCount.coerceAtLeast(0)
    if (itemPosition < safeLeading) return nonEmpty.first().label
    val trackPosition = itemPosition - safeLeading
    val totalTracks = nonEmpty.sumOf { it.tracks.size }
    val safePosition = trackPosition.coerceIn(0, (totalTracks - 1).coerceAtLeast(0))
    var sectionStart = 0
    nonEmpty.forEach { section ->
        val sectionEnd = sectionStart + section.tracks.size
        if (safePosition < sectionEnd) return section.label
        sectionStart = sectionEnd
    }
    return nonEmpty.last().label
}
