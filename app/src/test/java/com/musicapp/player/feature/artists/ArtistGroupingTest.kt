package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistGroupingTest {
    @Test
    fun `collaboration label is split into separate artist summaries across common delimiters`() {
        val tracks =
            listOf(
                track(1, "周杰伦", AlbumId("external", 1)),
                track(2, "周杰伦/王力宏", AlbumId("external", 2)),
                track(3, "王力宏 & 林俊杰", AlbumId("external", 3)),
                track(4, "陶喆、周杰伦, 陶喆; 方大同", AlbumId("external", 4)),
            )

        val grouped = ArtistGrouping.group(tracks)
        val byName = grouped.associateBy { it.displayName }

        assertEquals(5, grouped.size)
        assertTrue(byName.containsKey("周杰伦"))
        assertTrue(byName.containsKey("王力宏"))
        assertTrue(byName.containsKey("林俊杰"))
        assertTrue(byName.containsKey("陶喆"))
        assertTrue(byName.containsKey("方大同"))

        val jay = byName.getValue("周杰伦")
        assertEquals(3, jay.trackCount)
        assertEquals(listOf(1L, 2L, 4L), jay.artworkCandidates.map { it.id.mediaStoreId })

        val leehom = byName.getValue("王力宏")
        assertEquals(2, leehom.trackCount)
        assertEquals(listOf(2L, 3L), leehom.artworkCandidates.map { it.id.mediaStoreId })

        val jj = byName.getValue("林俊杰")
        assertEquals(1, jj.trackCount)
        assertEquals(listOf(3L), jj.artworkCandidates.map { it.id.mediaStoreId })

        val david = byName.getValue("陶喆")
        assertEquals(1, david.trackCount)
        assertEquals(listOf(4L), david.artworkCandidates.map { it.id.mediaStoreId })
    }

    @Test
    fun `artist summaries ignore casing and whitespace when grouping`() {
        val grouped =
            ArtistGrouping.group(
                listOf(
                    track(1, "Jay Chou", AlbumId("external", 1)),
                    track(2, "  jay chou  ", AlbumId("external", 2)),
                ),
            )

        assertEquals(1, grouped.size)
        val summary = grouped.single()
        assertEquals(ArtistId("jay chou"), summary.id)
        assertEquals("Jay Chou", summary.displayName)
        assertEquals(2, summary.trackCount)
    }

    @Test
    fun `splitArtistNames returns correct tokens`() {
        assertEquals(listOf("Alpha", "Beta"), ArtistGrouping.splitArtistNames("Alpha / Beta"))
        assertEquals(listOf("Alpha", "Beta"), ArtistGrouping.splitArtistNames("Alpha、Beta"))
        assertEquals(listOf("Alpha", "Beta"), ArtistGrouping.splitArtistNames("Alpha, Beta"))
        assertEquals(listOf("Alpha", "Beta"), ArtistGrouping.splitArtistNames("Alpha; Beta"))
        assertEquals(listOf("Alpha", "Beta"), ArtistGrouping.splitArtistNames("Alpha & Beta"))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), ArtistGrouping.splitArtistNames("Alpha / Beta & Gamma; ,"))
        assertEquals(emptyList<String>(), ArtistGrouping.splitArtistNames("   "))
        assertEquals(emptyList<String>(), ArtistGrouping.splitArtistNames(null))
    }

    @Test
    fun `artist summaries use fixed ascending name order`() {
        val grouped =
            ArtistGrouping.group(
                listOf(
                    track(1, "Zulu", AlbumId("external", 1)),
                    track(2, "alpha", AlbumId("external", 2)),
                    track(3, "Beta", AlbumId("external", 3)),
                    track(4, "方大同", AlbumId("external", 4)),
                    track(5, "!Special", AlbumId("external", 5)),
                ),
            )

        assertEquals(
            listOf("alpha", "Beta", "方大同", "Zulu", "!Special"),
            grouped.map(ArtistSummary::displayName),
        )
    }

    private fun track(value: Long, artistName: String, albumId: AlbumId) =
        Track(
            id = TrackId("external", value),
            title = "Track $value",
            artistName = artistName,
            artistMediaStoreId = value,
            albumTitle = "Album",
            albumId = albumId,
            durationMs = 1_000,
            dateAddedMs = value,
            dateModifiedMs = value,
            relativePath = "Music/",
            displayName = "$value.mp3",
        )
}
