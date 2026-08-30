package com.musicapp.player.feature.tracks

import com.musicapp.player.core.designsystem.component.SECTION_INDEX_ASCENDING_LABELS
import com.musicapp.player.core.designsystem.component.SECTION_INDEX_DESCENDING_LABELS
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSectionIndexTest {
    @Test
    fun `text sort fields use their own first letter`() {
        val track = track(title = "Title", artistName = "Artist", albumTitle = "Album")

        assertEquals("T", sectionLabelForTrack(track, TrackSortField.TITLE))
        assertEquals("A", sectionLabelForTrack(track, TrackSortField.ARTIST))
        assertEquals("A", sectionLabelForTrack(track, TrackSortField.ALBUM))
    }

    @Test
    fun `chinese initials use pinyin letters for their sections`() {
        val track = track(title = "中文歌曲", artistName = "周杰伦", albumTitle = "专辑")

        assertEquals("Z", sectionLabelForTrack(track, TrackSortField.TITLE))
        assertEquals("Z", sectionLabelForTrack(track, TrackSortField.ARTIST))
        assertEquals("Z", sectionLabelForTrack(track, TrackSortField.ALBUM))
    }

    @Test
    fun `non alphabetic sort fields do not create sections`() {
        val track = track(title = "Title")

        assertEquals(null, sectionLabelForTrack(track, TrackSortField.DATE_ADDED))
        assertEquals(null, sectionLabelForTrack(track, TrackSortField.DURATION))
        assertTrue(groupTracksIntoSections(listOf(track), TrackSortField.DATE_ADDED).isEmpty())
    }

    @Test
    fun `numeric initials use zero and special initials use hash section`() {
        val numericTitle = track(title = "123 title", albumTitle = null)
        val specialTitle = track(title = "!special title")

        assertEquals("0", sectionLabelForTrack(numericTitle, TrackSortField.TITLE))
        assertEquals("#", sectionLabelForTrack(specialTitle, TrackSortField.TITLE))
        assertEquals("#", sectionLabelForTrack(numericTitle, TrackSortField.ALBUM))
    }

    @Test
    fun `sections preserve fixed 28 bucket labels and calculate section positions`() {
        val tracks = listOf(
            track(1, "Alpha"),
            track(2, "Another"),
            track(3, "Bravo"),
            track(4, "123 title"),
        )

        val sections = groupTracksIntoSections(tracks, TrackSortField.TITLE)

        assertEquals(listOf("0", "A", "B"), sections.map(TrackSection::label))
        assertEquals(SECTION_INDEX_ASCENDING_LABELS, sectionIndexLabels(sections))
        assertEquals(listOf("123 title"), sections[0].tracks.map(Track::title))

        val positions = sectionStartPositions(sections)
        assertEquals(0, positions.getValue("0"))
        assertEquals(1, positions.getValue("A"))
        assertEquals(3, positions.getValue("B"))
        assertEquals(3, positions.getValue("C"))
        assertEquals(3, positions.getValue("#"))
    }

    @Test
    fun `descending order reverses sections and fixed bucket labels`() {
        val tracks = listOf(
            track(1, "Alpha"),
            track(2, "Bravo"),
            track(3, "123 title"),
            track(4, "!special"),
        )

        val sections = groupTracksIntoSections(tracks, TrackSortField.TITLE, TrackSortDirection.DESCENDING)

        assertEquals(listOf("#", "B", "A", "0"), sections.map(TrackSection::label))
        assertEquals(SECTION_INDEX_DESCENDING_LABELS, sectionIndexLabels(direction = TrackSortDirection.DESCENDING))
    }

    @Test
    fun `special sections are merged into one hash section at the bottom`() {
        val tracks = listOf(
            track(1, "Alpha"),
            track(2, "123 title"),
            track(3, "Bravo"),
            track(4, "!special title"),
            track(5, "@another special title"),
        )

        val sections = groupTracksIntoSections(tracks, TrackSortField.TITLE)

        assertEquals(listOf("0", "A", "B", "#"), sections.map(TrackSection::label))
        assertEquals(SECTION_INDEX_ASCENDING_LABELS, sectionIndexLabels(sections))
        assertEquals(
            listOf("!special title", "@another special title"),
            sections.last().tracks.map(Track::title),
        )
    }

    @Test
    fun `chinese tracks join the matching pinyin section`() {
        val sections = groupTracksIntoSections(
            listOf(
                track(1, "Zoo"),
                track(2, "中文标题"),
                track(3, "Alpha"),
            ),
            TrackSortField.TITLE,
        )

        assertEquals(listOf("A", "Z"), sections.map(TrackSection::label))
        assertEquals(listOf("Zoo", "中文标题"), sections[1].tracks.map(Track::title))
    }

    @Test
    fun `active section follows the hash section at the bottom`() {
        val sections = groupTracksIntoSections(
            listOf(
                track(1, "Alpha"),
                track(2, "123 title"),
                track(3, "Bravo"),
                track(4, "!special title"),
            ),
            TrackSortField.TITLE,
        )

        assertEquals("#", sectionLabelAtPosition(sections, 3))
    }

    private fun track(
        id: Long = 1,
        title: String,
        artistName: String = "Artist",
        albumTitle: String? = "Album",
    ): Track = Track(
        id = TrackId(volumeName = "external", mediaStoreId = id),
        title = title,
        artistName = artistName,
        albumTitle = albumTitle,
        durationMs = 1_000,
        dateAddedMs = 1,
        dateModifiedMs = 1,
        relativePath = "Music/$title",
        displayName = "$title.mp3",
    )
}
