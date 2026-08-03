package com.musicapp.player.feature.albums

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

    private fun track(value: Long, albumId: AlbumId, albumTitle: String) =
        Track(
            id = TrackId(albumId.volumeName, value),
            title = "Track $value",
            artistName = "Artist",
            albumTitle = albumTitle,
            albumId = albumId,
            durationMs = 1_000,
            dateAddedMs = value,
            dateModifiedMs = value,
            relativePath = "Music/",
            displayName = "$value.mp3",
        )
}
