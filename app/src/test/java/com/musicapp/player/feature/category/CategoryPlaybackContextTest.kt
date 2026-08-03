package com.musicapp.player.feature.category

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryPlaybackContextTest {
    @Test
    fun `recursive category order becomes queue and skips unavailable tracks`() {
        val tracks =
            listOf(
                track(1),
                track(2, Availability.TEMPORARILY_UNAVAILABLE),
                track(3),
            )

        val context = CategoryPlaybackContextFactory.create(
            source = PlaybackContextSource.FOLDER,
            sourceId = "external|Music",
            tracks = tracks,
            selectedTrackId = tracks[2].id,
        )

        assertEquals(listOf(tracks[0].id, tracks[2].id), context?.orderedTrackIds)
        assertEquals(tracks[2].id, context?.selectedTrackId)
        assertEquals(PlaybackContextSource.FOLDER, context?.source)
    }

    @Test
    fun `play all selects first playable and empty playable set does not replace queue`() {
        val available = track(1)
        val unavailable = track(2, Availability.TEMPORARILY_UNAVAILABLE)

        assertEquals(
            available.id,
            CategoryPlaybackContextFactory.create(
                PlaybackContextSource.ALBUM,
                "external|10",
                listOf(available),
            )?.selectedTrackId,
        )
        assertNull(
            CategoryPlaybackContextFactory.create(
                PlaybackContextSource.ARTIST,
                "20",
                listOf(unavailable),
            ),
        )
    }

    private fun track(value: Long, availability: Availability = Availability.AVAILABLE) =
        Track(
            id = TrackId("external", value),
            title = "Track $value",
            artistName = "Artist",
            durationMs = 1_000,
            dateAddedMs = value,
            dateModifiedMs = value,
            relativePath = "Music",
            displayName = "$value.mp3",
            availability = availability,
        )
}
