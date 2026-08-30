package com.musicapp.player.feature.albums

import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.classifySectionLabel
import com.musicapp.player.core.designsystem.component.resolveNearestPopulatedBucket
import com.musicapp.player.core.designsystem.component.sectionIndexLabelsForOrder
import com.musicapp.player.feature.category.CategorySortDirection

internal data class AlbumSection(
    val label: String,
    val albums: List<AlbumSummary>,
)

internal fun sectionLabelForAlbum(album: AlbumSummary, field: AlbumSortField): String? =
    when (field) {
        AlbumSortField.TITLE -> classifySectionLabel(album.title)
        AlbumSortField.ARTIST -> classifySectionLabel(album.artistName)
        AlbumSortField.TRACK_COUNT,
        AlbumSortField.DATE_ADDED,
        -> null
    }

internal fun albumSortDirectionToSectionOrder(direction: CategorySortDirection): SectionSortOrder =
    when (direction) {
        CategorySortDirection.ASCENDING -> SectionSortOrder.ASCENDING
        CategorySortDirection.DESCENDING -> SectionSortOrder.DESCENDING
    }

internal fun groupAlbumsIntoSections(
    albums: List<AlbumSummary>,
    field: AlbumSortField,
    direction: CategorySortDirection = CategorySortDirection.ASCENDING,
): List<AlbumSection> {
    val albumsByLabel = linkedMapOf<String, MutableList<AlbumSummary>>()
    albums.forEach { album ->
        val label = sectionLabelForAlbum(album, field) ?: return@forEach
        albumsByLabel.getOrPut(label, ::mutableListOf) += album
    }
    val order = albumSortDirectionToSectionOrder(direction)
    val orderedLabels = sectionIndexLabelsForOrder(order)
    return orderedLabels.mapNotNull { label ->
        albumsByLabel[label]?.let { sectionAlbums ->
            AlbumSection(label = label, albums = sectionAlbums.toList())
        }
    }
}

internal fun sectionIndexLabels(direction: CategorySortDirection = CategorySortDirection.ASCENDING): List<String> =
    sectionIndexLabelsForOrder(albumSortDirectionToSectionOrder(direction))

internal fun sectionIndexLabels(
    @Suppress("UNUSED_PARAMETER") sections: List<AlbumSection>,
    direction: CategorySortDirection = CategorySortDirection.ASCENDING,
): List<String> = sectionIndexLabels(direction)

internal fun sectionStartPositions(
    sections: List<AlbumSection>,
    direction: CategorySortDirection = CategorySortDirection.ASCENDING,
): Map<String, Int> {
    val labels = sectionIndexLabels(direction)
    val starts = linkedMapOf<String, Int>()
    var itemPosition = 0
    sections.forEach { section ->
        starts.putIfAbsent(section.label, itemPosition)
        itemPosition += section.albums.size
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

internal fun sectionLabelAtPosition(
    sections: List<AlbumSection>,
    itemPosition: Int,
): String? {
    if (sections.isEmpty()) return null
    val totalAlbums = sections.sumOf { it.albums.size }
    val safePosition = itemPosition.coerceIn(0, (totalAlbums - 1).coerceAtLeast(0))
    var sectionStart = 0
    sections.forEach { section ->
        val sectionEnd = sectionStart + section.albums.size
        if (safePosition < sectionEnd) return section.label
        sectionStart = sectionEnd
    }
    return sections.last().label
}
