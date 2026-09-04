package com.musicapp.player.feature.albums

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTrackOrderingTest {
    private val defaultAlbumId = AlbumId("external", 100L)

    @Test
    fun `single disc tracks with valid track numbers order by trackNumber asc and format single number`() {
        val tracks = listOf(
            createTrack(id = 3, trackNumber = 3, title = "Track C"),
            createTrack(id = 1, trackNumber = 1, title = "Track A"),
            createTrack(id = 2, trackNumber = 2, title = "Track B"),
        )

        val presentations = AlbumTrackOrdering.resolveOrder(tracks)

        assertEquals(listOf(1L, 2L, 3L), presentations.map { it.track.id.mediaStoreId })
        assertEquals(listOf("1", "2", "3"), presentations.map { it.trackNumberText })
        assertTrue(presentations.none { it.hasConflict })
    }

    @Test
    fun `multi disc tracks order by disc asc then track asc and format disc-track`() {
        val tracks = listOf(
            createTrack(id = 4, discNumber = 2, trackNumber = 1, title = "Disc 2 Track 1"),
            createTrack(id = 1, discNumber = 1, trackNumber = 1, title = "Disc 1 Track 1"),
            createTrack(id = 3, discNumber = 1, trackNumber = 10, title = "Disc 1 Track 10"),
            createTrack(id = 2, discNumber = 1, trackNumber = 2, title = "Disc 1 Track 2"),
        )

        val presentations = AlbumTrackOrdering.resolveOrder(tracks)

        assertEquals(listOf(1L, 2L, 3L, 4L), presentations.map { it.track.id.mediaStoreId })
        assertEquals(listOf("1-01", "1-02", "1-10", "2-01"), presentations.map { it.trackNumberText })
    }

    @Test
    fun `when no track numbers exist, fallback to title asc with dash placeholder`() {
        val tracks = listOf(
            createTrack(id = 3, trackNumber = null, title = "Charlie"),
            createTrack(id = 1, trackNumber = null, title = "Alpha"),
            createTrack(id = 2, trackNumber = null, title = "Bravo"),
        )

        val presentations = AlbumTrackOrdering.resolveOrder(tracks)

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), presentations.map { it.track.title })
        assertEquals(
            listOf(
                ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
                ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
                ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
            ),
            presentations.map { it.trackNumberText },
        )
        assertTrue(presentations.none { it.hasConflict })
    }

    @Test
    fun `partial and duplicate track numbers place valid items first, duplicates and unnumbered at the end`() {
        val tracks = listOf(
            createTrack(id = 10, trackNumber = null, title = "Unnumbered B"),
            createTrack(id = 2, trackNumber = 2, title = "Valid Track 2"),
            createTrack(id = 3, trackNumber = 3, title = "Conflict 3 Alpha"),
            createTrack(id = 1, trackNumber = 1, title = "Valid Track 1"),
            createTrack(id = 4, trackNumber = 3, title = "Conflict 3 Beta"),
            createTrack(id = 9, trackNumber = null, title = "Unnumbered A"),
        )

        val presentations = AlbumTrackOrdering.resolveOrder(tracks)

        // Valid non-conflicting: track 1, track 2
        assertEquals(listOf(1L, 2L), presentations.take(2).map { it.track.id.mediaStoreId })
        assertEquals(listOf("1", "2"), presentations.take(2).map { it.trackNumberText })

        // Trailing items (conflicts + unnumbered) sorted by title:
        // Conflict 3 Alpha, Conflict 3 Beta, Unnumbered A, Unnumbered B
        val trailing = presentations.drop(2)
        assertEquals(listOf(3L, 4L, 9L, 10L), trailing.map { it.track.id.mediaStoreId })
        assertEquals(
            listOf(
                ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
                ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
                ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
                ALBUM_TRACK_NO_NUMBER_PLACEHOLDER,
            ),
            trailing.map { it.trackNumberText },
        )
        assertTrue(trailing[0].hasConflict)
        assertTrue(trailing[1].hasConflict)
        assertFalse(trailing[2].hasConflict)
        assertFalse(trailing[3].hasConflict)
    }

    @Test
    fun `current playing track is accurately marked`() {
        val tracks = listOf(
            createTrack(id = 1, trackNumber = 1, title = "Track 1"),
            createTrack(id = 2, trackNumber = 2, title = "Track 2"),
        )

        val presentations = AlbumTrackOrdering.resolveOrder(tracks, currentPlayingTrackId = TrackId("external", 2L))

        assertFalse(presentations[0].isCurrentPlaying)
        assertTrue(presentations[1].isCurrentPlaying)
    }

    private fun createTrack(
        id: Long,
        title: String,
        trackNumber: Int? = null,
        discNumber: Int? = null,
        availability: Availability = Availability.AVAILABLE,
    ) = Track(
        id = TrackId(defaultAlbumId.volumeName, id),
        title = title,
        artistName = "Artist",
        albumTitle = "Album",
        albumId = defaultAlbumId,
        durationMs = 180_000L,
        dateAddedMs = id,
        dateModifiedMs = id,
        relativePath = "Music/",
        displayName = "$id.mp3",
        availability = availability,
        trackNumber = trackNumber,
        discNumber = discNumber,
    )
}
