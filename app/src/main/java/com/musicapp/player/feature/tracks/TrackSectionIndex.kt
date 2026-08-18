package com.musicapp.player.feature.tracks

import com.musicapp.player.core.domain.model.Track
import com.ibm.icu.text.Transliterator

internal data class TrackSection(
    val label: String,
    val tracks: List<Track>,
)

internal fun sectionLabelForTrack(track: Track, field: TrackSortField): String? =
    when (field) {
        TrackSortField.TITLE -> sectionLabel(track.title)
        TrackSortField.ARTIST -> sectionLabel(track.artistName)
        TrackSortField.ALBUM -> sectionLabel(track.albumTitle)
        TrackSortField.DATE_ADDED,
        TrackSortField.DURATION,
        -> null
    }

internal fun groupTracksIntoSections(
    tracks: List<Track>,
    field: TrackSortField,
): List<TrackSection> {
    val tracksByLabel = linkedMapOf<String, MutableList<Track>>()
    tracks.forEach { track ->
        val label = sectionLabelForTrack(track, field) ?: return@forEach
        tracksByLabel.getOrPut(label, ::mutableListOf) += track
    }
    return tracksByLabel
        .toList()
        .sortedBy { sectionGroupOrder(it.first) }
        .map { (label, sectionTracks) ->
            TrackSection(label = label, tracks = sectionTracks.toList())
        }
}

internal fun sectionIndexLabels(sections: List<TrackSection>): List<String> =
    sections.map(TrackSection::label).distinct()

internal fun sectionStartPositions(sections: List<TrackSection>): Map<String, Int> {
    var itemPosition = 0
    return buildMap {
        sections.forEach { section ->
            if (!containsKey(section.label)) {
                put(section.label, itemPosition)
            }
            itemPosition += section.tracks.size
        }
    }
}

internal fun sectionLabelAtPosition(
    sections: List<TrackSection>,
    itemPosition: Int,
): String? {
    var sectionStart = 0
    var activeLabel: String? = null
    sections.forEach { section ->
        if (sectionStart > itemPosition) return@forEach
        activeLabel = section.label
        sectionStart += section.tracks.size
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

private fun sectionGroupOrder(label: String): Int =
    when {
        label == "0" -> 0
        label == "#" -> 27
        else -> label.single().code - 'A'.code + 1
    }
