package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.albums.AlbumGrouping
import com.musicapp.player.feature.albums.UNKNOWN_ALBUM_SENTINEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistGroupingTest {
    @Test
    fun `artist labels remain complete identities`() {
        val tracks =
            listOf(
                track(1, "周杰伦", AlbumId("external", 1)),
                track(2, "周杰伦/王力宏", AlbumId("external", 2)),
                track(3, "王力宏 & 林俊杰", AlbumId("external", 3)),
                track(4, "陶喆、周杰伦, 陶喆; 方大同", AlbumId("external", 4)),
            )

        val grouped = ArtistGrouping.group(tracks)
        val byName = grouped.associateBy { it.displayName }

        assertEquals(4, grouped.size)
        assertEquals(setOf("周杰伦", "周杰伦/王力宏", "王力宏 & 林俊杰", "陶喆、周杰伦, 陶喆; 方大同"), byName.keys)
        assertEquals(1, byName.getValue("周杰伦/王力宏").trackCount)
        assertEquals(1, byName.getValue("王力宏 & 林俊杰").trackCount)
        assertEquals(1, byName.getValue("陶喆、周杰伦, 陶喆; 方大同").trackCount)
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
    fun `splitArtistNames trims without splitting complete labels`() {
        assertEquals(listOf("Alpha / Beta"), ArtistGrouping.splitArtistNames(" Alpha / Beta "))
        assertEquals(listOf("Alpha、Beta"), ArtistGrouping.splitArtistNames("Alpha、Beta"))
        assertEquals(listOf("Alpha, Beta"), ArtistGrouping.splitArtistNames("Alpha, Beta"))
        assertEquals(listOf("Alpha; Beta"), ArtistGrouping.splitArtistNames("Alpha; Beta"))
        assertEquals(listOf("Alpha & Beta"), ArtistGrouping.splitArtistNames("Alpha & Beta"))
        assertEquals(emptyList<String>(), ArtistGrouping.splitArtistNames("   "))
        assertEquals(emptyList<String>(), ArtistGrouping.splitArtistNames(null))
    }

    @Test
    fun `artist album summaries preserve album group key and intersect artist tracks`() {
        val artistTrack = track(1, "Artist", AlbumId("external", 10))
        val otherTrack = track(2, "Other", AlbumId("external", 10))
        val unknownTrack = track(3, "Artist", AlbumId("external", 11)).copy(albumId = null, albumTitle = null)

        val albums = ArtistGrouping.groupAlbumsForArtist(
            allTracks = listOf(artistTrack, otherTrack, unknownTrack),
            artistTracks = listOf(artistTrack, unknownTrack),
        )

        assertEquals(2, albums.size)
        assertEquals(UNKNOWN_ALBUM_SENTINEL, albums.first().title)
        assertEquals(1, albums.first().artistTrackCount)
        assertEquals(1, albums.last().artistTrackCount)
        assertEquals(
            AlbumGrouping.group(listOf(artistTrack, otherTrack)).first().groupKey,
            albums.last().groupKey,
        )
    }

    @Test
    fun `artist route key round trips complete unicode label`() {
        val label = "周杰伦 / 王力宏"
        val routeKey = ArtistRouteKey.encode(label)

        assertTrue(routeKey.startsWith("a_"))
        assertTrue('/' !in routeKey)
        assertTrue('+' !in routeKey)
        assertEquals(label, ArtistRouteKey.decode(routeKey))
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
