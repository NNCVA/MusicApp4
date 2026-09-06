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
    fun `same volume same title different ids are merged into single album`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 10), "跨时代", artist = "周杰伦"),
                track(2, AlbumId("external", 11), "跨时代", artist = "周杰伦"),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(1, grouped.size)
        val album = grouped.single()
        assertEquals("跨时代", album.title)
        assertEquals("周杰伦", album.artistName)
        assertEquals(2, album.trackCount)
        assertEquals(1L, album.representativeTrack.id.mediaStoreId)
        assertEquals(
            setOf(AlbumId("external", 10), AlbumId("external", 11)),
            album.memberAlbumIds,
        )

        val foundTracks = AlbumGrouping.findTracksForAlbum(tracks, AlbumId("external", 10))
        assertEquals(2, foundTracks.size)
        val foundTracksViaOtherId = AlbumGrouping.findTracksForAlbum(tracks, AlbumId("external", 11))
        assertEquals(2, foundTracksViaOtherId.size)
    }

    @Test
    fun `different volumes with same title remain separate`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 10), "跨时代", artist = "周杰伦"),
                track(2, AlbumId("sdcard", 10), "跨时代", artist = "周杰伦"),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(2, grouped.size)
        val externalAlbum = grouped.first { it.id.volumeName == "external" }
        val sdcardAlbum = grouped.first { it.id.volumeName == "sdcard" }
        assertEquals(1, externalAlbum.trackCount)
        assertEquals(1, sdcardAlbum.trackCount)
    }

    @Test
    fun `different artists with same title remain separate`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 10), "精选集", artist = "周杰伦"),
                track(2, AlbumId("external", 11), "精选集", artist = "陈奕迅"),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(2, grouped.size)
        val jayAlbum = grouped.first { it.artistName == "周杰伦" }
        val easonAlbum = grouped.first { it.artistName == "陈奕迅" }
        assertEquals(1, jayAlbum.trackCount)
        assertEquals(1, easonAlbum.trackCount)
    }

    @Test
    fun `compatible artists with delimiters and feats are merged with primary artist preserved`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 20), "魔杰座", artist = "周杰伦"),
                track(2, AlbumId("external", 21), "魔杰座", artist = "周杰伦 / 梁心颐"),
                track(3, AlbumId("external", 21), "魔杰座", artist = "周杰伦 feat. 浪花兄弟"),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(1, grouped.size)
        val album = grouped.single()
        assertEquals("魔杰座", album.title)
        assertEquals("周杰伦", album.artistName)
        assertEquals(3, album.trackCount)
        assertEquals(
            setOf(AlbumId("external", 20), AlbumId("external", 21)),
            album.memberAlbumIds,
        )
    }

    @Test
    fun `different release years with same title remain separate as version conflict`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 40), "跨时代", artist = "周杰伦").copy(releaseYear = 2010),
                track(2, AlbumId("external", 41), "跨时代", artist = "周杰伦").copy(releaseYear = 2024),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(2, grouped.size)
        assertEquals(listOf(1, 1), grouped.map { it.trackCount })
    }

    @Test
    fun `version modifier keywords like Live or Deluxe remain separate from original`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 50), "跨时代", artist = "周杰伦"),
                track(2, AlbumId("external", 51), "跨时代 (Live)", artist = "周杰伦"),
                track(3, AlbumId("external", 52), "跨时代 [Deluxe Edition]", artist = "周杰伦"),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(3, grouped.size)
        assertEquals(listOf(1, 1, 1), grouped.map { it.trackCount })
    }

    @Test
    fun `transitive artist relation does not merge independent artists`() {
        val tracks =
            listOf(
                // Group 1: Jay Chou solo
                track(1, AlbumId("external", 60), "魔杰座", artist = "周杰伦"),
                // Group 2: Jay Chou + Lara duet
                track(2, AlbumId("external", 61), "魔杰座", artist = "周杰伦 / 梁心颐"),
                // Group 3: Lara solo
                track(3, AlbumId("external", 62), "魔杰座", artist = "梁心颐"),
            )

        val grouped = AlbumGrouping.group(tracks)

        // Jay Chou solo and Jay+Lara share core artist "周杰伦" -> merged.
        // Lara solo does not contain core artist "周杰伦" -> separate!
        assertEquals(2, grouped.size)
        val jayAlbum = grouped.first { it.artistName == "周杰伦" }
        assertEquals(2, jayAlbum.trackCount)
        assertEquals(setOf(AlbumId("external", 60), AlbumId("external", 61)), jayAlbum.memberAlbumIds)

        val laraAlbum = grouped.first { it.artistName == "梁心颐" }
        assertEquals(1, laraAlbum.trackCount)
        assertEquals(setOf(AlbumId("external", 62)), laraAlbum.memberAlbumIds)
    }

    @Test
    fun `artist delimiters support backslash and fullwidth punctuation`() {
        val tracks =
            listOf(
                track(1, AlbumId("external", 70), "魔杰座", artist = "周杰伦\\梁心颐"),
                track(2, AlbumId("external", 71), "魔杰座", artist = "周杰伦，梁心颐"),
                track(3, AlbumId("external", 72), "魔杰座", artist = "周杰伦；梁心颐"),
            )

        val grouped = AlbumGrouping.group(tracks)

        assertEquals(1, grouped.size)
        val album = grouped.single()
        assertEquals(3, album.trackCount)
    }

    @Test
    fun `physical album summary uses normalized majority title`() {
        val tracks = listOf(
            track(1, AlbumId("external", 73), "Minor Title", artist = "周杰伦"),
            track(2, AlbumId("external", 73), "Canonical Title", artist = "周杰伦"),
            track(3, AlbumId("external", 73), "canonical title", artist = "周杰伦"),
        )

        val album = AlbumGrouping.group(tracks).single()

        assertEquals("Canonical Title", album.title)
        assertEquals("canonical title", album.groupKey.normalizedTitle)
        assertEquals(album.groupKey.encode(), album.key)
        assertEquals(album.groupKey, AlbumGroupKey.decode(album.key))
    }

    @Test
    fun `group key remains stable when representative physical album is removed`() {
        val allTracks = listOf(
            track(1, AlbumId("external", 74), "跨时代", artist = "周杰伦"),
            track(2, AlbumId("external", 75), "跨时代", artist = "周杰伦"),
        )

        val allKey = AlbumGrouping.group(allTracks).single().key
        val remainingKey = AlbumGrouping.group(allTracks.drop(1)).single().key

        assertEquals(allKey, remainingKey)
    }

    @Test
    fun `same album input order produces the same artist cluster assignment`() {
        val tracks = listOf(
            track(1, AlbumId("external", 76), "魔杰座", artist = "周杰伦"),
            track(2, AlbumId("external", 77), "魔杰座", artist = "梁心颐"),
            track(3, AlbumId("external", 78), "魔杰座", artist = "周杰伦 / 梁心颐"),
        )

        val forward = AlbumGrouping.group(tracks).map { it.trackIds }
        val reversed = AlbumGrouping.group(tracks.reversed()).map { it.trackIds }

        assertEquals(forward, reversed)
    }

    @Test
    fun `findTracksForAlbum does not leak orphan tracks from same physical album id into normal album detail`() {
        val normalTrack = track(1, AlbumId("external", 80), "Known Album", artist = "Artist")
        val orphanTrack = track(2, AlbumId("external", 80), null, artist = "Artist")
        val tracks = listOf(normalTrack, orphanTrack)

        val grouped = AlbumGrouping.group(tracks)
        assertEquals(2, grouped.size) // 1 normal album, 1 unknown album

        val normalTracks = AlbumGrouping.findTracksForAlbum(tracks, AlbumId("external", 80))
        assertEquals(listOf(1L), normalTracks.map { it.id.mediaStoreId })

        val unknownTracks = AlbumGrouping.findTracksForAlbum(tracks, UNKNOWN_ALBUM_ID)
        assertEquals(listOf(2L), unknownTracks.map { it.id.mediaStoreId })
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

    @Test
    fun `album grouping computes earliest release year for album summary`() {
        val tracks = listOf(
            track(1, AlbumId("external", 1), "Album A", releaseYear = 2023),
            track(2, AlbumId("external", 1), "Album A", releaseYear = 2019),
            track(3, AlbumId("external", 1), "Album A", releaseYear = 2021),
            track(4, AlbumId("external", 2), "Album B", releaseYear = null),
        )
        val albums = AlbumGrouping.group(tracks)
        val albumA = albums.first { it.id == AlbumId("external", 1) }
        val albumB = albums.first { it.id == AlbumId("external", 2) }

        assertEquals(2019, albumA.releaseYear)
        assertEquals(null, albumB.releaseYear)
    }

    @Test
    fun `album sort by RELEASE_YEAR orders descending with nulls placed at the end`() {
        val albums = listOf(
            AlbumSummary(
                id = AlbumId("external", 1),
                title = "Album 2018",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 1,
                representativeTrack = track(1, AlbumId("external", 1), "Album 2018", releaseYear = 2018),
                releaseYear = 2018,
            ),
            AlbumSummary(
                id = AlbumId("external", 2),
                title = "Album No Year",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 2,
                representativeTrack = track(2, AlbumId("external", 2), "Album No Year", releaseYear = null),
                releaseYear = null,
            ),
            AlbumSummary(
                id = AlbumId("external", 3),
                title = "Album 2024",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 3,
                representativeTrack = track(3, AlbumId("external", 3), "Album 2024", releaseYear = 2024),
                releaseYear = 2024,
            ),
            AlbumSummary(
                id = AlbumId("external", 4),
                title = "Album 1999",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 4,
                representativeTrack = track(4, AlbumId("external", 4), "Album 1999", releaseYear = 1999),
                releaseYear = 1999,
            ),
        )

        val descending = AlbumGrouping.sorted(
            albums,
            AlbumSort(AlbumSortField.RELEASE_YEAR, CategorySortDirection.DESCENDING),
        )
        assertEquals(listOf(3L, 1L, 4L, 2L), descending.map { it.id.mediaStoreId })

        val ascending = AlbumGrouping.sorted(
            albums,
            AlbumSort(AlbumSortField.RELEASE_YEAR, CategorySortDirection.ASCENDING),
        )
        assertEquals(listOf(4L, 1L, 3L, 2L), ascending.map { it.id.mediaStoreId })
    }

    @Test
    fun `album sort by RELEASE_YEAR breaks ties by title tie breaker`() {
        val albums = listOf(
            AlbumSummary(
                id = AlbumId("external", 1),
                title = "Beta Album",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 1,
                representativeTrack = track(1, AlbumId("external", 1), "Beta Album", releaseYear = 2020),
                releaseYear = 2020,
            ),
            AlbumSummary(
                id = AlbumId("external", 2),
                title = "Alpha Album",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 2,
                representativeTrack = track(2, AlbumId("external", 2), "Alpha Album", releaseYear = 2020),
                releaseYear = 2020,
            ),
        )

        val sorted = AlbumGrouping.sorted(
            albums,
            AlbumSort(AlbumSortField.RELEASE_YEAR, CategorySortDirection.DESCENDING),
        )
        assertEquals(listOf(2L, 1L), sorted.map { it.id.mediaStoreId })
    }

    @Test
    fun `album sort next toggles direction and defaults to descending for RELEASE_YEAR`() {
        val initial = AlbumSort().next(AlbumSortField.RELEASE_YEAR)
        assertEquals(AlbumSortField.RELEASE_YEAR, initial.field)
        assertEquals(CategorySortDirection.DESCENDING, initial.direction)

        val toggled = initial.next(AlbumSortField.RELEASE_YEAR)
        assertEquals(AlbumSortField.RELEASE_YEAR, toggled.field)
        assertEquals(CategorySortDirection.ASCENDING, toggled.direction)
    }

    @Test
    fun `groupAlbumsByYear groups albums by year and puts un-yeared albums in null bucket at end`() {
        val albums = listOf(
            AlbumSummary(
                id = AlbumId("external", 1),
                title = "Album 2023",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 1,
                representativeTrack = track(1, AlbumId("external", 1), "Album 2023", releaseYear = 2023),
                releaseYear = 2023,
            ),
            AlbumSummary(
                id = AlbumId("external", 2),
                title = "Album 2002 A",
                artistName = "Artist",
                trackCount = 2,
                latestDateAddedMs = 2,
                representativeTrack = track(2, AlbumId("external", 2), "Album 2002 A", releaseYear = 2002),
                releaseYear = 2002,
            ),
            AlbumSummary(
                id = AlbumId("external", 3),
                title = "Album 2002 B",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 3,
                representativeTrack = track(3, AlbumId("external", 3), "Album 2002 B", releaseYear = 2002),
                releaseYear = 2002,
            ),
            AlbumSummary(
                id = AlbumId("external", 4),
                title = "Album No Year",
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 4,
                representativeTrack = track(4, AlbumId("external", 4), "Album No Year", releaseYear = null),
                releaseYear = null,
            ),
            AlbumSummary(
                id = UNKNOWN_ALBUM_ID,
                title = UNKNOWN_ALBUM_SENTINEL,
                artistName = "Artist",
                trackCount = 1,
                latestDateAddedMs = 5,
                representativeTrack = track(5, null, null, releaseYear = null),
                releaseYear = null,
            ),
        )

        val groups = groupAlbumsByYear(albums)
        assertEquals(3, groups.size)

        assertEquals(2023, groups[0].year)
        assertEquals(listOf(1L), groups[0].albums.map { it.id.mediaStoreId })

        assertEquals(2002, groups[1].year)
        assertEquals(listOf(2L, 3L), groups[1].albums.map { it.id.mediaStoreId })

        org.junit.Assert.assertNull(groups[2].year)
        assertEquals(listOf(4L, UNKNOWN_ALBUM_ID.mediaStoreId), groups[2].albums.map { it.id.mediaStoreId })
    }

    private fun track(
        value: Long,
        albumId: AlbumId?,
        albumTitle: String?,
        artist: String = "Artist",
        releaseYear: Int? = null,
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
            releaseYear = releaseYear,
        )
}
