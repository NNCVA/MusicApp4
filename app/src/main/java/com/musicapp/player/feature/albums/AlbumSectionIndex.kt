package com.musicapp.player.feature.albums

import com.ibm.icu.text.Transliterator

internal data class AlbumSection(
    val label: String,
    val albums: List<AlbumSummary>,
)

internal fun sectionLabelForAlbum(album: AlbumSummary, field: AlbumSortField): String? =
    when (field) {
        AlbumSortField.TITLE -> sectionLabel(album.title)
        AlbumSortField.ARTIST -> sectionLabel(album.artistName)
        AlbumSortField.TRACK_COUNT,
        AlbumSortField.DATE_ADDED,
        -> null
    }

internal fun groupAlbumsIntoSections(
    albums: List<AlbumSummary>,
    field: AlbumSortField,
): List<AlbumSection> {
    val albumsByLabel = linkedMapOf<String, MutableList<AlbumSummary>>()
    albums.forEach { album ->
        val label = sectionLabelForAlbum(album, field) ?: return@forEach
        albumsByLabel.getOrPut(label, ::mutableListOf) += album
    }
    return albumsByLabel.map { (label, sectionAlbums) ->
        AlbumSection(label = label, albums = sectionAlbums.toList())
    }
}

internal fun sectionIndexLabels(sections: List<AlbumSection>): List<String> =
    sections.map(AlbumSection::label).distinct()

internal fun sectionStartPositions(sections: List<AlbumSection>): Map<String, Int> {
    var itemPosition = 0
    return buildMap {
        sections.forEach { section ->
            if (!containsKey(section.label)) {
                put(section.label, itemPosition)
            }
            itemPosition += section.albums.size
        }
    }
}

internal fun sectionLabelAtPosition(
    sections: List<AlbumSection>,
    itemPosition: Int,
): String? {
    var sectionStart = 0
    var activeLabel: String? = null
    sections.forEach { section ->
        if (sectionStart > itemPosition) return@forEach
        activeLabel = section.label
        sectionStart += section.albums.size
    }
    return activeLabel
}

private fun sectionLabel(value: String?): String {
    val first = value.orEmpty().trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return when {
        first.isDigit() -> "0"
        first in 'A'..'Z' -> first.toString()
        else -> pinyinInitial(first)?.toString() ?: "#"
    }
}

private fun pinyinInitial(value: Char): Char? =
    HAN_TO_LATIN.transliterate(value.toString())
        .firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
        ?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }

private val HAN_TO_LATIN: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin")
}
