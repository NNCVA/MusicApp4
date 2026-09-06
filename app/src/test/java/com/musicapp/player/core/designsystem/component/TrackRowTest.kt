package com.musicapp.player.core.designsystem.component

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.feature.albums.UNKNOWN_ALBUM_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRowTest {

    @Test
    fun subtitleFormattingCombinesArtistAndAlbum() {
        val result = formatTrackSubtitle("Jay Chou", "Fantasy")
        assertEquals("Jay Chou · Fantasy", result)
    }

    @Test
    fun subtitleFormattingOmitsEmptyOrBlankAlbum() {
        assertEquals("Jay Chou", formatTrackSubtitle("Jay Chou", null))
        assertEquals("Jay Chou", formatTrackSubtitle("Jay Chou", ""))
        assertEquals("Jay Chou", formatTrackSubtitle("Jay Chou", "   "))
    }

    @Test
    fun unknownArtistSentinelMatchesExpectedValue() {
        assertEquals("<unknown>", UNKNOWN_ARTIST_SENTINEL)
    }

    @Test
    fun isUnknownArtistIdentifiesNullSentinelAndBlank() {
        assertTrue(isUnknownArtist(null))
        assertTrue(isUnknownArtist("<unknown>"))
        assertTrue(isUnknownArtist(""))
        assertTrue(isUnknownArtist("   "))
        assertFalse(isUnknownArtist("Jay Chou"))
    }

    @Test
    fun isUnknownAlbumIdentifiesNullBlankAndSentinelId() {
        assertTrue(isUnknownAlbum(null, AlbumId("external", 1)))
        assertTrue(isUnknownAlbum("", AlbumId("external", 1)))
        assertTrue(isUnknownAlbum("   ", AlbumId("external", 1)))
        assertTrue(isUnknownAlbum("Fantasy", null))
        assertTrue(isUnknownAlbum("Fantasy", UNKNOWN_ALBUM_ID))
        assertFalse(isUnknownAlbum("Fantasy", AlbumId("external", 1)))
    }
}
