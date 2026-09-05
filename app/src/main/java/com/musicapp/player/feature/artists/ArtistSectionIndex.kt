package com.musicapp.player.feature.artists

import com.musicapp.player.core.designsystem.component.SECTION_INDEX_ASCENDING_LABELS
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.classifySectionLabel
import com.musicapp.player.core.designsystem.component.resolveNearestPopulatedBucket
import com.musicapp.player.core.designsystem.component.sectionIndexLabelsForOrder
import com.musicapp.player.core.designsystem.component.sortedBySectionText
import com.musicapp.player.feature.category.CategorySortDirection

internal val ARTIST_SECTION_INDEX_LABELS: List<String> = SECTION_INDEX_ASCENDING_LABELS

internal data class ArtistSection(
    val label: String,
    val artists: List<ArtistSummary>,
)

internal fun artistSortDirectionToSectionOrder(direction: CategorySortDirection): SectionSortOrder =
    when (direction) {
        CategorySortDirection.ASCENDING -> SectionSortOrder.ASCENDING
        CategorySortDirection.DESCENDING -> SectionSortOrder.DESCENDING
    }

internal fun groupArtistsIntoSections(
    artists: List<ArtistSummary>,
    direction: CategorySortDirection = CategorySortDirection.ASCENDING,
): List<ArtistSection> {
    val artistsByLabel = linkedMapOf<String, MutableList<ArtistSummary>>()
    sortArtistsByIndexedName(artists, direction).forEach { artist ->
        artistsByLabel.getOrPut(sectionLabelForArtist(artist), ::mutableListOf) += artist
    }
    val order = artistSortDirectionToSectionOrder(direction)
    val orderedLabels = sectionIndexLabelsForOrder(order)
    return orderedLabels.mapNotNull { label ->
        artistsByLabel[label]?.let { section ->
            ArtistSection(label = label, artists = section.toList())
        }
    }
}

internal fun sortArtistsByIndexedName(
    artists: List<ArtistSummary>,
    direction: CategorySortDirection = CategorySortDirection.ASCENDING,
): List<ArtistSummary> =
    artists.sortedBySectionText(
        order = artistSortDirectionToSectionOrder(direction),
        textSelector = ArtistSummary::displayName,
        tieBreaker = compareBy<ArtistSummary> { it.displayName }.thenBy { it.id.name },
    )

internal fun sectionIndexLabels(direction: CategorySortDirection = CategorySortDirection.ASCENDING): List<String> =
    sectionIndexLabelsForOrder(artistSortDirectionToSectionOrder(direction))

internal fun sectionIndexLabels(
    @Suppress("UNUSED_PARAMETER") sections: List<ArtistSection>,
    direction: CategorySortDirection = CategorySortDirection.ASCENDING,
): List<String> = sectionIndexLabels(direction)

internal fun sectionStartPositions(
    sections: List<ArtistSection>,
    direction: CategorySortDirection = CategorySortDirection.ASCENDING,
    initialOffset: Int = 0,
): Map<String, Int> {
    val labels = sectionIndexLabels(direction)
    val starts = linkedMapOf<String, Int>()
    var itemPosition = initialOffset
    sections.forEach { section ->
        starts.putIfAbsent(section.label, itemPosition)
        itemPosition += section.artists.size
    }
    val lastPosition = (itemPosition - 1).coerceAtLeast(0)

    val populatedIndices = sections.mapNotNull { section ->
        val idx = labels.indexOf(section.label)
        if (idx >= 0) idx else null
    }.toSet()

    return buildMap {
        labels.forEachIndexed { bucketIndex, label ->
            val position = starts[label] ?: run {
                val nearestIndex = resolveNearestPopulatedBucket(
                    targetBucketIndex = bucketIndex,
                    populatedBucketIndices = populatedIndices,
                    dragDirection = 0,
                    bucketCount = labels.size,
                )
                val nearestLabel = labels.getOrNull(nearestIndex)
                nearestLabel?.let { starts[it] } ?: lastPosition
            }
            put(label, position.coerceIn(0, lastPosition))
        }
    }
}

internal fun sectionLabelAtPosition(
    sections: List<ArtistSection>,
    itemPosition: Int,
    initialOffset: Int = 0,
): String? {
    if (sections.isEmpty()) return null
    if (itemPosition < initialOffset) return sections.first().label
    val itemCount = sections.sumOf { it.artists.size }
    val safePosition = (itemPosition - initialOffset).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    var sectionStart = 0
    sections.forEach { section ->
        val sectionEnd = sectionStart + section.artists.size
        if (safePosition < sectionEnd) return section.label
        sectionStart = sectionEnd
    }
    return sections.last().label
}

internal fun sectionLabelForArtist(artist: ArtistSummary): String =
    artistSectionLabel(artist.displayName)

internal fun artistSectionLabel(value: String?): String =
    classifySectionLabel(value)
