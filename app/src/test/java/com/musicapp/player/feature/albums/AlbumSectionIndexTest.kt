package com.musicapp.player.feature.albums

import com.musicapp.player.core.domain.model.AlbumId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumSectionIndexTest {
    @Test
    fun `index follows current album order and hides unsupported sort fields`() {
        val albums = listOf(
            album(1, "Alpha", "Zed"),
            album(2, "Beta", "Alpha"),
            album(3, "中文专辑", "Artist"),
            album(4, "123 album", "Artist"),
            album(5, "!Special", "Artist"),
        )

        val titleSections = groupAlbumsIntoSections(albums, AlbumSortField.TITLE)

        assertEquals(listOf("A", "B", "Z", "0", "#"), titleSections.map(AlbumSection::label))
        assertEquals(
            mapOf("A" to 0, "B" to 1, "Z" to 2, "0" to 3, "#" to 4),
            sectionStartPositions(titleSections),
        )
        assertEquals("Z", sectionLabelAtPosition(titleSections, 2))

        val artistSections = groupAlbumsIntoSections(albums, AlbumSortField.ARTIST)
        assertEquals(listOf("Z", "A"), artistSections.map(AlbumSection::label))
        assertTrue(groupAlbumsIntoSections(albums, AlbumSortField.TRACK_COUNT).isEmpty())
        assertTrue(groupAlbumsIntoSections(albums, AlbumSortField.DATE_ADDED).isEmpty())
    }

    private fun album(id: Long, title: String, artistName: String): AlbumSummary =
        AlbumSummary(
            id = AlbumId(volumeName = "external", mediaStoreId = id),
            title = title,
            artistName = artistName,
            trackCount = 1,
            latestDateAddedMs = id,
        )
}
