package com.musicapp.player.feature.playlists

import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTextCodecTest {
    @Test
    fun `encode and decode preserve name and ordered paths`() {
        val content = PlaylistTextCodec.encode(
            displayName = "Road Trip",
            paths = listOf("external/Music/One.mp3", "external/Music/Two.mp3"),
        )

        assertEquals(
            PlaylistTextDocument(
                displayName = "Road Trip",
                paths = listOf("external/Music/One.mp3", "external/Music/Two.mp3"),
            ),
            PlaylistTextCodec.decode(content),
        )
    }

    @Test
    fun `decode accepts utf8 bom crlf and ignores blank lines`() {
        val content = "\uFEFF${PlaylistTextCodec.HEADER}\r\n# name=旅行\r\n\r\nexternal/Music/一.mp3\r\n"

        assertEquals(
            listOf("external/Music/一.mp3"),
            PlaylistTextCodec.decode(content).paths,
        )
    }

    @Test
    fun `track path normalizes separators and repeated slashes`() {
        val track = Track(
            id = TrackId("external", 1),
            title = "One",
            artistName = "Artist",
            durationMs = 1_000,
            dateAddedMs = 0,
            dateModifiedMs = 0,
            relativePath = "Music\\Album/",
            displayName = "One.mp3",
        )

        assertEquals("external/Music/Album/One.mp3", PlaylistTextCodec.pathFor(track))
        assertEquals("Road_Trip.txt", PlaylistTextCodec.suggestedFileName("Road/Trip"))
    }

    @Test
    fun `invalid header and parent traversal are rejected`() {
        assertTrue(runCatching { PlaylistTextCodec.decode("not a playlist") }.isFailure)
        assertTrue(runCatching { PlaylistTextCodec.canonicalPath("external/Music/../Track.mp3") }.isFailure)
    }
}
