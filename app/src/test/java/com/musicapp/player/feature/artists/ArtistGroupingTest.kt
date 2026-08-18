package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistGroupingTest {
    @Test
    fun `collaboration label remains one artist group and is never split`() {
        val tracks =
            listOf(
                track(1, ArtistId(20), "Alpha & Beta", AlbumId("external", 1)),
                track(2, ArtistId(20), "Alpha & Beta", AlbumId("external", 2)),
            )

        val grouped = ArtistGrouping.group(tracks)

        assertEquals(1, grouped.size)
        assertEquals("Alpha & Beta", grouped.single().displayName)
        assertEquals(listOf(1L, 2L), grouped.single().artworkCandidates.map { it.id.mediaStoreId })
    }

    @Test
    fun `same artist label with different ids remains separate`() {
        val grouped =
            ArtistGrouping.group(
                listOf(
                    track(1, ArtistId(20), "Shared", AlbumId("external", 1)),
                    track(2, ArtistId(21), "Shared", AlbumId("external", 1)),
                ),
            )

        assertEquals(setOf(ArtistId(20), ArtistId(21)), grouped.map(ArtistSummary::id).toSet())
    }

    @Test
    fun `artist summaries use fixed ascending name order`() {
        val grouped =
            ArtistGrouping.group(
                listOf(
                    track(1, ArtistId(22), "Zulu", AlbumId("external", 1)),
                    track(2, ArtistId(20), "alpha", AlbumId("external", 2)),
                    track(3, ArtistId(21), "Beta", AlbumId("external", 3)),
                    track(4, ArtistId(23), "方大同", AlbumId("external", 4)),
                    track(5, ArtistId(24), "!Special", AlbumId("external", 5)),
                ),
            )

        assertEquals(
            listOf("alpha", "Beta", "方大同", "Zulu", "!Special"),
            grouped.map(ArtistSummary::displayName),
        )
    }

    private fun track(value: Long, artistId: ArtistId, artistName: String, albumId: AlbumId) =
        Track(
            id = TrackId("external", value),
            title = "Track $value",
            artistName = artistName,
            artistId = artistId,
            albumTitle = "Album",
            albumId = albumId,
            durationMs = 1_000,
            dateAddedMs = value,
            dateModifiedMs = value,
            relativePath = "Music/",
            displayName = "$value.mp3",
        )
}
