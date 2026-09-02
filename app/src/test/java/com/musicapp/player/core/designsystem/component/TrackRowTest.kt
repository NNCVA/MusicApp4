package com.musicapp.player.core.designsystem.component

import org.junit.Assert.assertEquals
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
}
