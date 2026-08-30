package com.musicapp.player.feature.artists

import com.ibm.icu.text.Transliterator
import com.musicapp.player.core.designsystem.component.SECTION_INDEX_ASCENDING_LABELS
import com.musicapp.player.core.designsystem.component.SECTION_INDEX_DIGIT_LABEL
import com.musicapp.player.core.designsystem.component.SECTION_INDEX_OTHER_LABEL
import com.musicapp.player.core.designsystem.component.classifySectionLabel
import com.musicapp.player.core.designsystem.component.resolveNearestPopulatedBucket
import java.util.Locale

internal val ARTIST_SECTION_INDEX_LABELS: List<String> = SECTION_INDEX_ASCENDING_LABELS

internal data class ArtistSection(
    val label: String,
    val artists: List<ArtistSummary>,
)

internal fun groupArtistsIntoSections(artists: List<ArtistSummary>): List<ArtistSection> {
    val artistsByLabel = linkedMapOf<String, MutableList<ArtistSummary>>()
    sortArtistsByIndexedName(artists).forEach { artist ->
        artistsByLabel.getOrPut(sectionLabelForArtist(artist), ::mutableListOf) += artist
    }
    return ARTIST_SECTION_INDEX_LABELS.mapNotNull { label ->
        artistsByLabel[label]?.let { section ->
            ArtistSection(label = label, artists = section.toList())
        }
    }
}

internal fun sortArtistsByIndexedName(artists: List<ArtistSummary>): List<ArtistSummary> =
    artists.sortedWith(artistNameComparator)

internal fun sectionIndexLabels(): List<String> = ARTIST_SECTION_INDEX_LABELS

internal fun sectionIndexLabels(@Suppress("UNUSED_PARAMETER") sections: List<ArtistSection>): List<String> =
    ARTIST_SECTION_INDEX_LABELS

internal fun sectionStartPositions(sections: List<ArtistSection>): Map<String, Int> {
    val ordered = orderedSections(sections)
    val starts = linkedMapOf<String, Int>()
    var itemPosition = 0
    ordered.forEach { section ->
        starts.putIfAbsent(section.label, itemPosition)
        itemPosition += section.artists.size
    }
    val itemCount = itemPosition
    val lastPosition = (itemCount - 1).coerceAtLeast(0)

    val populatedIndices = ordered.mapNotNull { section ->
        val idx = ARTIST_SECTION_INDEX_LABELS.indexOf(section.label)
        if (idx >= 0) idx else null
    }.toSet()

    return buildMap {
        ARTIST_SECTION_INDEX_LABELS.forEachIndexed { bucketIndex, label ->
            val position = starts[label] ?: run {
                val nearestIndex = resolveNearestPopulatedBucket(
                    targetBucketIndex = bucketIndex,
                    populatedBucketIndices = populatedIndices,
                    dragDirection = 0,
                    bucketCount = ARTIST_SECTION_INDEX_LABELS.size,
                )
                val nearestLabel = ARTIST_SECTION_INDEX_LABELS.getOrNull(nearestIndex)
                nearestLabel?.let { starts[it] } ?: lastPosition
            }
            put(label, position.coerceIn(0, lastPosition))
        }
    }
}

internal fun sectionLabelAtPosition(
    sections: List<ArtistSection>,
    itemPosition: Int,
): String? {
    val ordered = orderedSections(sections).filter { it.artists.isNotEmpty() }
    if (ordered.isEmpty()) return null
    val itemCount = ordered.sumOf { it.artists.size }
    val safePosition = itemPosition.coerceIn(0, itemCount - 1)
    var sectionStart = 0
    ordered.forEach { section ->
        val sectionEnd = sectionStart + section.artists.size
        if (safePosition < sectionEnd) return section.label
        sectionStart = sectionEnd
    }
    return ordered.last().label
}

internal fun sectionLabelForArtist(artist: ArtistSummary): String =
    artistSectionLabel(artist.displayName)

internal fun artistSectionLabel(value: String?): String =
    classifySectionLabel(value)

private val artistNameComparator =
    compareBy<ArtistSummary>(
        { sectionOrder(sectionLabelForArtist(it)) },
        { artistSortKey(it.displayName) },
        { it.displayName.lowercase(Locale.ROOT) },
        { it.displayName },
    )
        .thenBy { it.id.name }

private fun artistSortKey(value: String): String =
    HAN_TO_LATIN.transliterate(value.trim()).lowercase(Locale.ROOT)

private fun orderedSections(sections: List<ArtistSection>): List<ArtistSection> =
    sections.sortedWith(compareBy { sectionOrder(it.label) })

private fun sectionOrder(label: String): Int =
    ARTIST_SECTION_INDEX_LABELS.indexOf(label).takeIf { it >= 0 } ?: ARTIST_SECTION_INDEX_LABELS.lastIndex

private val HAN_TO_LATIN: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin")
}
