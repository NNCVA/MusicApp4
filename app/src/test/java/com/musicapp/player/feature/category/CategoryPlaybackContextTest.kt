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

    @Test
    fun `sortCategoryTracks orders by 28-bucket sections with digits, pinyin, and symbols`() {
        val symbolTrack = track(1).copy(title = "#Symbol")
        val numberTrack = track(2).copy(title = "123 Number")
        val englishTrack = track(3).copy(title = "Apple")
        val chineseBTrack = track(4).copy(title = "北京欢迎你")
        val chineseZTrack = track(5).copy(title = "周杰伦")
        val allTracks = listOf(symbolTrack, numberTrack, englishTrack, chineseBTrack, chineseZTrack)

        val ascending = sortCategoryTracks(
            allTracks,
            CategoryTrackSort(CategoryTrackSortField.TITLE, CategorySortDirection.ASCENDING),
        )
        assertEquals(
            listOf(numberTrack.id, englishTrack.id, chineseBTrack.id, chineseZTrack.id, symbolTrack.id),
            ascending.map { it.id },
        )

        val descending = sortCategoryTracks(
            allTracks,
            CategoryTrackSort(CategoryTrackSortField.TITLE, CategorySortDirection.DESCENDING),
        )
        assertEquals(
            listOf(symbolTrack.id, chineseZTrack.id, chineseBTrack.id, englishTrack.id, numberTrack.id),
            descending.map { it.id },
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
