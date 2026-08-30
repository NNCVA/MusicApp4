package com.musicapp.player.feature.albums

import com.musicapp.player.core.designsystem.component.SECTION_INDEX_ASCENDING_LABELS
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.category.CategorySortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumSectionIndexTest {
    @Test
    fun `index follows 28 bucket ordering and hides unsupported sort fields`() {
        val albums = listOf(
            album(1, "Alpha", "Zed"),
            album(2, "Beta", "Alpha"),
            album(3, "中文专辑", "Artist"),
            album(4, "123 album", "Artist"),
            album(5, "!Special", "Artist"),
        )

        val titleSections = groupAlbumsIntoSections(albums, AlbumSortField.TITLE)

        assertEquals(listOf("0", "A", "B", "Z", "#"), titleSections.map(AlbumSection::label))
        assertEquals(SECTION_INDEX_ASCENDING_LABELS, sectionIndexLabels(titleSections))

        val positions = sectionStartPositions(titleSections)
        assertEquals(0, positions.getValue("0"))
        assertEquals(1, positions.getValue("A"))
        assertEquals(2, positions.getValue("B"))
        assertEquals(3, positions.getValue("Z"))
        assertEquals(4, positions.getValue("#"))

        val artistSections = groupAlbumsIntoSections(albums, AlbumSortField.ARTIST)
        assertEquals(listOf("A", "Z"), artistSections.map(AlbumSection::label))
        assertTrue(groupAlbumsIntoSections(albums, AlbumSortField.TRACK_COUNT).isEmpty())
        assertTrue(groupAlbumsIntoSections(albums, AlbumSortField.DATE_ADDED).isEmpty())
    }

    @Test
    fun `descending order flips album section ordering`() {
        val albums = listOf(
            album(1, "Alpha", "Zed"),
            album(2, "Beta", "Alpha"),
            album(3, "123 album", "Artist"),
        )

        val titleSections = groupAlbumsIntoSections(albums, AlbumSortField.TITLE, CategorySortDirection.DESCENDING)
        assertEquals(listOf("B", "A", "0"), titleSections.map(AlbumSection::label))
    }

    private fun album(id: Long, title: String, artistName: String): AlbumSummary =
        AlbumSummary(
            id = AlbumId(volumeName = "external", mediaStoreId = id),
            title = title,
            artistName = artistName,
            trackCount = 1,
            latestDateAddedMs = id,
            representativeTrack =
                Track(
                    id = TrackId("external", id),
                    title = title,
                    artistName = artistName,
                    albumTitle = title,
                    albumId = AlbumId("external", id),
                    durationMs = 1_000,
                    dateAddedMs = id,
                    dateModifiedMs = id,
                    relativePath = "Music/",
                    displayName = "$title.mp3",
                ),
        )
}
