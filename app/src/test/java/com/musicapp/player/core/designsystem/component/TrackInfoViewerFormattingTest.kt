package com.musicapp.player.core.designsystem.component

import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackInfoViewerFormattingTest {
    @Test
    fun trackPathAddsSeparatorWhenRelativePathDoesNotHaveOne() {
        assertEquals(
            "Music/Test Song.mp3",
            trackPath(testTrack(relativePath = "Music")),
        )
    }

    @Test
    fun trackFormatPrefersDisplayNameExtensionAndFallsBackToMimeType() {
        assertEquals("MP3", trackFormat(testTrack(displayName = "Test Song.mp3", mimeType = "audio/flac")))
        assertEquals("FLAC", trackFormat(testTrack(displayName = "Test Song", mimeType = "audio/flac")))
    }

    private fun testTrack(
        relativePath: String = "Music/",
        displayName: String = "Test Song.mp3",
        mimeType: String? = "audio/mpeg",
    ) = Track(
        id = TrackId("external_primary", 1L),
        title = "Test Song",
        artistName = "Test Artist",
        albumTitle = "Test Album",
        durationMs = 120_000L,
        dateAddedMs = 1L,
        dateModifiedMs = 2L,
        relativePath = relativePath,
        displayName = displayName,
        mimeType = mimeType,
    )
}
