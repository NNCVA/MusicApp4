package com.musicapp.player.feature.artists

import com.ibm.icu.text.Transliterator
import java.util.Locale

internal const val ARTIST_SECTION_DIGIT_LABEL = "0"
internal const val ARTIST_SECTION_OTHER_LABEL = "#"

/** The artist index is intentionally fixed, even when some labels have no artists. */
internal val ARTIST_SECTION_INDEX_LABELS: List<String> =
    listOf(ARTIST_SECTION_DIGIT_LABEL) + ('A'..'Z').map(Char::toString) + ARTIST_SECTION_OTHER_LABEL

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

/**
 * Returns a safe list position for every fixed label. Missing labels point at the
 * insertion point before the next populated section, or the nearest list boundary.
 */
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

    return buildMap {
        ARTIST_SECTION_INDEX_LABELS.forEach { label ->
            val position =
                starts[label]
                    ?: ordered
                        .firstOrNull { sectionOrder(it.label) > sectionOrder(label) }
                        ?.let { nextSection -> starts[nextSection.label] }
                    ?: lastPosition
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

internal fun artistSectionLabel(value: String?): String {
    val firstCodePoint = value.orEmpty().trim().firstCodePointOrNull() ?: return ARTIST_SECTION_OTHER_LABEL
    return when {
        Character.isDigit(firstCodePoint.codePoint) -> ARTIST_SECTION_DIGIT_LABEL
            firstCodePoint.codePoint in 'A'.code..'Z'.code ||
            firstCodePoint.codePoint in 'a'.code..'z'.code ->
            firstCodePoint.text.uppercase(Locale.ROOT)
        else -> pinyinInitial(firstCodePoint)?.toString() ?: ARTIST_SECTION_OTHER_LABEL
    }
}

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

private data class CodePointText(
    val text: String,
    val codePoint: Int,
)

private fun String.firstCodePointOrNull(): CodePointText? {
    if (isEmpty()) return null
    val codePoint = codePointAt(0)
    return CodePointText(String(Character.toChars(codePoint)), codePoint)
}

private fun pinyinInitial(value: CodePointText): Char? =
    HAN_TO_LATIN.transliterate(value.text)
        .firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }

private val HAN_TO_LATIN: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin")
}
