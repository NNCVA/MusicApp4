package com.musicapp.player.feature.playlists

import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTransferUseCaseTest {
    @Test
    fun `export preserves playlist order and reports missing tracks`() = runTest {
        val first = track(1, "One.mp3")
        val second = track(2, "Two.mp3")
        val missing = TrackId("external", 99)
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Road",
            normalizedName = "road",
            trackIds = listOf(second.id, missing, first.id),
            createdAtMs = 0,
        )
        val useCase = PlaylistTransferUseCase(
            playlistRepository = FakePlaylistRepository(initialPlaylists = listOf(playlist)),
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(first, second)),
            clock = Clock { 10 },
        )

        val result = useCase.export(playlist.id)

        assertEquals(2, result.exportedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(
            listOf(
                PlaylistTextCodec.HEADER,
                "# name=Road",
                PlaylistTextCodec.pathFor(second),
                PlaylistTextCodec.pathFor(first),
            ),
            result.content.trimEnd().lines(),
        )
    }

    @Test
    fun `import deduplicates paths skips other volumes and suffixes duplicate names`() = runTest {
        val first = track(1, "One.mp3")
        val second = track(2, "Two.mp3")
        val repository = FakePlaylistRepository(
            initialPlaylists = listOf(
                Playlist(PlaylistId(1), "Road", "road", createdAtMs = 1),
            ),
            existingTrackIds = setOf(first.id, second.id),
        )
        val useCase = PlaylistTransferUseCase(
            playlistRepository = repository,
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(first, second)),
            clock = Clock { 20 },
        )
        val content = PlaylistTextCodec.encode(
            "Road",
            listOf(
                PlaylistTextCodec.pathFor(first),
                PlaylistTextCodec.pathFor(first),
                "other/Music/One.mp3",
                PlaylistTextCodec.pathFor(second),
            ),
        )

        val result = useCase.import(content)
        val imported = repository.observePlaylist(result.playlistId).first()

        assertEquals("Road (2)", result.playlistName)
        assertEquals(2, result.matchedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(listOf(first.id, second.id), imported?.trackIds)
    }

    @Test
    fun `valid empty document creates an empty playlist`() = runTest {
        val repository = FakePlaylistRepository()
        val useCase = PlaylistTransferUseCase(
            playlistRepository = repository,
            mediaLibraryRepository = FakeMediaLibraryRepository(),
            clock = Clock { 30 },
        )

        val result = useCase.import(PlaylistTextCodec.encode("Empty", emptyList()))

        assertEquals("Empty", result.playlistName)
        assertEquals(emptyList<TrackId>(), repository.observePlaylist(result.playlistId).first()?.trackIds)
    }

    @Test
    fun `invalid document does not mutate repository`() = runTest {
        val repository = FakePlaylistRepository()
        val useCase = PlaylistTransferUseCase(
            playlistRepository = repository,
            mediaLibraryRepository = FakeMediaLibraryRepository(),
            clock = Clock { 30 },
        )

        assertTrue(runCatching { useCase.import("invalid") }.isFailure)
        assertEquals(emptyList<Playlist>(), repository.observePlaylists().first())
    }

    private fun track(id: Long, displayName: String) = Track(
        id = TrackId("external", id),
        title = displayName.substringBeforeLast('.'),
        artistName = "Artist",
        durationMs = 1_000,
        dateAddedMs = id,
        dateModifiedMs = id,
        relativePath = "Music",
        displayName = displayName,
    )
}
