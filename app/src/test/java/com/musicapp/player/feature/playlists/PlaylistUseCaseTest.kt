package com.musicapp.player.feature.playlists

import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.FakePlaylistRepository
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistUseCaseTest {
    @Test
    fun `name trim unicode normalization and locale stable uniqueness share one contract`() = runTest {
        val oldLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            val repository = FakePlaylistRepository()
            val useCase = PlaylistUseCase(repository, Clock { 10 })

            val playlistId = useCase.create("  CAFÉ  ")
            val playlist = repository.observePlaylist(playlistId).first()

            assertEquals("CAFÉ", playlist?.displayName)
            assertEquals("café", playlist?.normalizedName)
            assertEquals("i", PlaylistNameNormalizer.normalize("I").normalizedName)
            assertTrue(runCatching { useCase.create("Cafe\u0301") }.isFailure)
            assertTrue(runCatching { useCase.create("a".repeat(51)) }.isFailure)
        } finally {
            Locale.setDefault(oldLocale)
        }
    }

    @Test
    fun `batch add and remove preserve user order and report changed and skipped counts`() = runTest {
        val tracks = listOf(track(1), track(2), track(3))
        val repository = FakePlaylistRepository(existingTrackIds = tracks.map(Track::id).toSet())
        val useCase = PlaylistUseCase(repository, Clock { 20 })
        val playlistId = useCase.create("Road")

        val added = useCase.addTracks(
            playlistId,
            listOf(tracks[2].id, tracks[0].id, tracks[2].id, tracks[1].id),
        )
        val duplicate = useCase.addTracks(playlistId, listOf(tracks[1].id, tracks[0].id))
        val removed = useCase.removeTracks(playlistId, listOf(tracks[0].id, TrackId("external", 99)))

        assertEquals(3, added.changedCount)
        assertEquals(1, added.skippedCount)
        assertEquals(0, duplicate.changedCount)
        assertEquals(2, duplicate.skippedCount)
        assertEquals(1, removed.changedCount)
        assertEquals(1, removed.skippedCount)
        assertEquals(
            listOf(tracks[2].id, tracks[1].id),
            repository.observePlaylist(playlistId).first()?.trackIds,
        )
    }

    @Test
    fun `playlist playback keeps joined position and empty playable set does not create context`() {
        val first = track(1)
        val unavailable = track(2, Availability.TEMPORARILY_UNAVAILABLE)
        val last = track(3)
        val missing = TrackId("external", 4)
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Order",
            normalizedName = "order",
            trackIds = listOf(last.id, unavailable.id, missing, first.id),
            createdAtMs = 0,
        )

        val preparation = PlaylistPlaybackContextFactory.prepare(playlist, listOf(first, unavailable, last))
        val context = preparation.context

        assertEquals(listOf(last.id, first.id), context?.orderedTrackIds)
        assertEquals(last.id, context?.selectedTrackId)
        assertEquals(2, preparation.playedCount)
        assertEquals(2, preparation.skippedCount)
        assertNull(
            PlaylistPlaybackContextFactory.create(
                playlist.copy(trackIds = listOf(unavailable.id)),
                listOf(unavailable),
            ),
        )
        assertNull(PlaylistPlaybackContextFactory.create(playlist.copy(trackIds = emptyList()), emptyList()))
    }

    private fun track(value: Long, availability: Availability = Availability.AVAILABLE) = Track(
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
