package com.musicapp.player.feature.albums

import com.musicapp.player.core.designsystem.component.VARIOUS_ARTISTS_SENTINEL
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.feature.category.CategorySortDirection
import org.junit.Assert.assertEquals
import org.junit.Test


class AlbumGroupingTest {
    @Test
    fun `same album title with different ids remains separate`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 10), "Shared title"),
                track(2, AlbumId("external", 11), "Shared title"),
                track(3, AlbumId("sdcard", 10), "Shared title"),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(3, grouped.size)
        assertEquals(tracks.mapNotNull(Track::albumId).toSet(), grouped.map(AlbumSummary::id).toSet())
        assertEquals(listOf(1L, 2L, 3L), grouped.map { it.representativeTrack.id.mediaStoreId })

        val reordered = AlbumGrouping.group(tracks.reversed()).associateBy(AlbumSummary::id)
        assertEquals(1L, reordered.getValue(AlbumId("external", 10)).representativeTrack.id.mediaStoreId)
    }

    @Test
    fun `album sort is independent and deterministic`() {
        val albums =
            AlbumGrouping.group(
                listOf(
                    track(1, AlbumId("external", 10), "Alpha"),
                    track(2, AlbumId("external", 10), "Alpha"),
                    track(3, AlbumId("external", 11), "Beta"),
                ),
            )

        val sorted = AlbumGrouping.sorted(
            albums,
            AlbumSort(AlbumSortField.TRACK_COUNT, CategorySortDirection.DESCENDING),
        )

        assertEquals(listOf(10L, 11L), sorted.map { it.id.mediaStoreId })
    }

    @Test
    fun `album sort orders by 28-bucket sections with digits, pinyin, and symbols`() {
        val albums =
            listOf(
                AlbumSummary(
                    id = AlbumId("external", 1),
                    title = "#Special Album",
                    artistName = "Artist",
                    trackCount = 1,
                    latestDateAddedMs = 1,
                    representativeTrack = track(1, AlbumId("external", 1), "#Special Album"),
                ),
                AlbumSummary(
                    id = AlbumId("external", 2),
                    title = "123 Album",
                    artistName = "Artist",
                    trackCount = 1,
                    latestDateAddedMs = 2,
                    representativeTrack = track(2, AlbumId("external", 2), "123 Album"),
                ),
                AlbumSummary(
                    id = AlbumId("external", 3),
                    title = "Alpha Album",
                    artistName = "Artist",
                    trackCount = 1,
                    latestDateAddedMs = 3,
                    representativeTrack = track(3, AlbumId("external", 3), "Alpha Album"),
                ),
                AlbumSummary(
                    id = AlbumId("external", 4),
                    title = "周杰伦专辑",
                    artistName = "Artist",
                    trackCount = 1,
                    latestDateAddedMs = 4,
                    representativeTrack = track(4, AlbumId("external", 4), "周杰伦专辑"),
                ),
            )

        val ascending = AlbumGrouping.sorted(albums, AlbumSort(AlbumSortField.TITLE, CategorySortDirection.ASCENDING))
        assertEquals(listOf(2L, 3L, 4L, 1L), ascending.map { it.id.mediaStoreId })

        val descending = AlbumGrouping.sorted(albums, AlbumSort(AlbumSortField.TITLE, CategorySortDirection.DESCENDING))
        assertEquals(listOf(1L, 4L, 3L, 2L), descending.map { it.id.mediaStoreId })
    }

    @Test
    fun `tracks without album metadata are grouped into UNKNOWN_ALBUM_ID`() {
        val tracks = listOf(
            track(1, null, null, artist = "Artist A"),
            track(2, null, "Orphan Title", artist = "Artist A"),
            track(3, AlbumId("external", 10), null, artist = "Artist A"),
            track(4, AlbumId("external", 11), "Known Album", artist = "Artist B"),
        )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(2, grouped.size)
        val unknownAlbum = grouped.first { it.id == UNKNOWN_ALBUM_ID }
        assertEquals(UNKNOWN_ALBUM_SENTINEL, unknownAlbum.title)
        assertEquals("Artist A", unknownAlbum.artistName)
        assertEquals(3, unknownAlbum.trackCount)
        assertEquals(1L, unknownAlbum.representativeTrack.id.mediaStoreId)

        val normalAlbum = grouped.first { it.id == AlbumId("external", 11) }
        assertEquals("Known Album", normalAlbum.title)
        assertEquals(1, normalAlbum.trackCount)
    }


    @Test
    fun `unknown album displays artist name when single artist, and various artists sentinel when multiple artists`() {
        val singleArtistTracks = listOf(
            track(1, null, null, artist = "Solo Singer"),
            track(2, null, null, artist = "Solo Singer"),
        )
        val singleGrouped = AlbumGrouping.group(singleArtistTracks)
        assertEquals("Solo Singer", singleGrouped.single().artistName)

        val multiArtistTracks = listOf(
            track(1, null, null, artist = "Singer A"),
            track(2, null, null, artist = "Singer B"),
        )
        val multiGrouped = AlbumGrouping.group(multiArtistTracks)
        assertEquals(VARIOUS_ARTISTS_SENTINEL, multiGrouped.single().artistName)
    }

    @Test
    fun `unknown album is always pinned to the top regardless of sort field and direction`() {
        val tracks = listOf(
            track(1, null, null, artist = "Artist Z"),
            track(2, AlbumId("external", 10), "Alpha Album", artist = "Artist A"),
            track(3, AlbumId("external", 11), "Beta Album", artist = "Artist B"),
        )
        val albums = AlbumGrouping.group(tracks)

        for (field in AlbumSortField.entries) {
            for (direction in CategorySortDirection.entries) {
                val sorted = AlbumGrouping.sorted(albums, AlbumSort(field, direction))
                assertEquals(
                    "Failed for field $field and direction $direction",
                    UNKNOWN_ALBUM_ID,
                    sorted.first().id,
                )
            }
        }
    }

    private fun track(
        value: Long,
        albumId: AlbumId?,
        albumTitle: String?,
        artist: String = "Artist",
    ) =
        Track(
            id = TrackId(albumId?.volumeName ?: "external", value),
            title = "Track $value",
            artistName = artist,
            albumTitle = albumTitle,
            albumId = albumId,
            durationMs = 1_000,
            dateAddedMs = value,
            dateModifiedMs = value,
            relativePath = "Music/",
            displayName = "$value.mp3",
        )
}
