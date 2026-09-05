package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.ArtistId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistSectionIndexTest {
    @Test
    fun `index keeps fixed labels and classifies numbers ascii pinyin and other characters`() {
        val sections =
            groupArtistsIntoSections(
                listOf(
                    artist(1, "Beta"),
                    artist(2, "中文"),
                    artist(3, "123 songs"),
                    artist(4, "!Special"),
                    artist(5, "alpha"),
                    artist(6, "Zulu"),
                ),
            )

        assertEquals(listOf("0", "A", "B", "Z", "#"), sections.map(ArtistSection::label))
        assertEquals(ARTIST_SECTION_INDEX_LABELS, sectionIndexLabels(sections))
        assertEquals(
            listOf("123 songs", "alpha", "Beta", "中文", "Zulu", "!Special"),
            sections.flatMap(ArtistSection::artists).map(ArtistSummary::displayName),
        )
        assertEquals("Z", sectionLabelForArtist(artist(7, "中文")))
    }

    @Test
    fun `empty labels resolve to insertion positions and boundaries`() {
        val sections =
            listOf(
                ArtistSection("A", listOf(artist(1, "Alpha"), artist(2, "Another"))),
                ArtistSection("C", listOf(artist(3, "Charlie"))),
                ArtistSection("#", listOf(artist(4, "!Special"))),
            )

        val positions = sectionStartPositions(sections)

        assertEquals(0, positions.getValue("0"))
        assertEquals(0, positions.getValue("A"))
        assertEquals(2, positions.getValue("B"))
        assertEquals(2, positions.getValue("C"))
        assertEquals(3, positions.getValue("Z"))
        assertEquals(3, positions.getValue("#"))
        assertEquals("A", sectionLabelAtPosition(sections, -1))
        assertEquals("A", sectionLabelAtPosition(sections, 0))
        assertEquals("C", sectionLabelAtPosition(sections, 2))
        assertEquals("#", sectionLabelAtPosition(sections, 99))
    }

    @Test
    fun `empty sections never produce out of bounds positions`() {
        val positions = sectionStartPositions(emptyList())

        assertEquals(ARTIST_SECTION_INDEX_LABELS.associateWith { 0 }, positions)
        assertNull(sectionLabelAtPosition(emptyList(), 0))
    }

    @Test
    fun `initialOffset shifts start positions and preserves head label for pinned items`() {
        val sections = listOf(
            ArtistSection("A", listOf(artist(1, "Alpha"))),
            ArtistSection("B", listOf(artist(2, "Beta"))),
        )

        val positionsWithOffset = sectionStartPositions(sections, initialOffset = 1)
        assertEquals(1, positionsWithOffset.getValue("A"))
        assertEquals(2, positionsWithOffset.getValue("B"))

        // Pinned header position (0) falls before offset, resolves to first section label
        assertEquals("A", sectionLabelAtPosition(sections, itemPosition = 0, initialOffset = 1))
        assertEquals("A", sectionLabelAtPosition(sections, itemPosition = 1, initialOffset = 1))
        assertEquals("B", sectionLabelAtPosition(sections, itemPosition = 2, initialOffset = 1))
    }


    private fun artist(id: Long, name: String): ArtistSummary =
        ArtistSummary(
            id = ArtistId(name.lowercase()),
            displayName = name,
            trackCount = 1,
            artworkCandidates = emptyList(),
        )
}
