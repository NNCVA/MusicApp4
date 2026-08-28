package com.musicapp.player.feature.playlists

import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `main view model creates renames and deletes through normalized use case`() = runTest(dispatcher) {
        val repository = FakePlaylistRepository()
        val viewModel = PlaylistsViewModel(
            repository = repository,
            useCase = PlaylistUseCase(repository, Clock { 10 }),
            mediaLibraryRepository = FakeMediaLibraryRepository(),
            artworkRepository = RecordingArtworkRepository(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.create("  Road  ")
        advanceUntilIdle()
        val playlist = viewModel.uiState.value.playlists.single()
        assertEquals("Road", playlist.displayName)
        assertEquals(PlaylistOperationMessage.CREATED, viewModel.uiState.value.operationMessage)

        viewModel.rename(playlist.id, "Travel")
        advanceUntilIdle()
        assertEquals("Travel", viewModel.uiState.value.playlists.single().displayName)
        assertEquals(PlaylistOperationMessage.RENAMED, viewModel.uiState.value.operationMessage)

        viewModel.delete(playlist.id)
        advanceUntilIdle()
        assertEquals(emptyList<Playlist>(), viewModel.uiState.value.playlists)
        assertEquals(PlaylistOperationMessage.DELETED, viewModel.uiState.value.operationMessage)
        collection.cancel()
    }

    @Test
    fun `main view model uses the last available track artwork and removes it after deletion`() =
        runTest(dispatcher) {
            val first = track(1)
            val lastAvailable = track(2)
            val newestUnavailable = track(3, Availability.TEMPORARILY_UNAVAILABLE)
            val playlist = Playlist(
                id = PlaylistId(1),
                displayName = "Cover",
                normalizedName = "cover",
                trackIds = listOf(first.id, lastAvailable.id, newestUnavailable.id),
                createdAtMs = 0,
            )
            val repository = FakePlaylistRepository(
                initialPlaylists = listOf(playlist),
                existingTrackIds = setOf(first.id, lastAvailable.id, newestUnavailable.id),
            )
            val artworkRepository = RecordingArtworkRepository(
                outcomes = mapOf(lastAvailable.id to embeddedArtwork(2)),
            )
            val viewModel = PlaylistsViewModel(
                repository = repository,
                useCase = PlaylistUseCase(repository, Clock { 10 }),
                mediaLibraryRepository = FakeMediaLibraryRepository(
                    listOf(first, lastAvailable, newestUnavailable),
                ),
                artworkRepository = artworkRepository,
            )
            val collection = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.requestArtwork(playlist)
            advanceUntilIdle()

            assertEquals(listOf(lastAvailable.id), artworkRepository.requests.map(ArtworkRequest::trackId))
            assertEquals(
                2,
                embeddedPixel(viewModel.uiState.value.artworkByPlaylistId.getValue(playlist.id.value)),
            )

            viewModel.delete(playlist.id)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.artworkByPlaylistId.containsKey(playlist.id.value))
            collection.cancel()
        }

    @Test
    fun `main view model uses placeholder for empty unavailable and unreadable artwork`() =
        runTest(dispatcher) {
            val unavailable = track(2, Availability.TEMPORARILY_UNAVAILABLE)
            val unreadable = track(3)
            val emptyPlaylist = Playlist(
                id = PlaylistId(1),
                displayName = "Empty",
                normalizedName = "empty",
                createdAtMs = 0,
            )
            val unavailablePlaylist = Playlist(
                id = PlaylistId(2),
                displayName = "Unavailable",
                normalizedName = "unavailable",
                trackIds = listOf(unavailable.id),
                createdAtMs = 0,
            )
            val unreadablePlaylist = Playlist(
                id = PlaylistId(3),
                displayName = "Unreadable",
                normalizedName = "unreadable",
                trackIds = listOf(unreadable.id),
                createdAtMs = 0,
            )
            val repository = FakePlaylistRepository(
                initialPlaylists = listOf(emptyPlaylist, unavailablePlaylist, unreadablePlaylist),
                existingTrackIds = setOf(unavailable.id, unreadable.id),
            )
            val artworkRepository = RecordingArtworkRepository(
                failure = IllegalStateException("decode failed"),
            )
            val viewModel = PlaylistsViewModel(
                repository = repository,
                useCase = PlaylistUseCase(repository, Clock { 10 }),
                mediaLibraryRepository = FakeMediaLibraryRepository(listOf(unavailable, unreadable)),
                artworkRepository = artworkRepository,
            )
            val collection = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.requestArtwork(emptyPlaylist)
            viewModel.requestArtwork(unavailablePlaylist)
            viewModel.requestArtwork(unreadablePlaylist)
            advanceUntilIdle()

            assertSame(
                ArtworkResult.Placeholder,
                viewModel.uiState.value.artworkByPlaylistId.getValue(emptyPlaylist.id.value),
            )
            assertSame(
                ArtworkResult.Placeholder,
                viewModel.uiState.value.artworkByPlaylistId.getValue(unavailablePlaylist.id.value),
            )
            assertSame(
                ArtworkResult.Placeholder,
                viewModel.uiState.value.artworkByPlaylistId.getValue(unreadablePlaylist.id.value),
            )
            assertEquals(listOf(unreadable.id), artworkRepository.requests.map(ArtworkRequest::trackId))
            collection.cancel()
        }

    @Test
    fun `main view model ignores artwork result for an older playlist request`() = runTest(dispatcher) {
        val first = track(1)
        val second = track(2)
        val original = Playlist(
            id = PlaylistId(1),
            displayName = "Changing",
            normalizedName = "changing",
            trackIds = listOf(first.id),
            createdAtMs = 0,
        )
        val updated = original.copy(trackIds = listOf(second.id), updatedAtMs = 1)
        val repository = FakePlaylistRepository(
            initialPlaylists = listOf(original),
            existingTrackIds = setOf(first.id, second.id),
        )
        val staleResult = CompletableDeferred<ArtworkResult>()
        val viewModel = PlaylistsViewModel(
            repository = repository,
            useCase = PlaylistUseCase(repository, Clock { 10 }),
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(first, second)),
            artworkRepository = SuspendingArtworkRepository(
                suspendedTrackId = first.id,
                suspendedResult = staleResult,
                immediateResult = embeddedArtwork(2),
            ),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.requestArtwork(original)
        runCurrent()
        viewModel.requestArtwork(updated)
        advanceUntilIdle()
        assertEquals(
            2,
            embeddedPixel(viewModel.uiState.value.artworkByPlaylistId.getValue(original.id.value)),
        )

        staleResult.complete(embeddedArtwork(1))
        advanceUntilIdle()
        assertEquals(
            2,
            embeddedPixel(viewModel.uiState.value.artworkByPlaylistId.getValue(original.id.value)),
        )
        collection.cancel()
    }

    @Test
    fun `detail view model preserves joined order removes tracks and never plays empty list`() = runTest(dispatcher) {
        val first = track(1)
        val second = track(2)
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Order",
            normalizedName = "order",
            trackIds = listOf(second.id, first.id),
            createdAtMs = 0,
        )
        val repository = FakePlaylistRepository(
            initialPlaylists = listOf(playlist),
            existingTrackIds = setOf(first.id, second.id),
        )
        val controller = RecordingPlaybackController()
        val viewModel = PlaylistDetailViewModel(
            playlistRepository = repository,
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(first, second)),
            useCase = PlaylistUseCase(repository, Clock { 10 }),
            playbackController = controller,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        assertEquals(listOf(second.id, first.id), viewModel.uiState.value.tracks.map(Track::id))
        viewModel.playAll()
        advanceUntilIdle()
        assertEquals(listOf(second.id, first.id), controller.playedContext?.orderedTrackIds)
        assertEquals(2, viewModel.uiState.value.playbackFeedback?.playedCount)
        assertEquals(0, viewModel.uiState.value.playbackFeedback?.skippedCount)

        viewModel.toggleSelection(first.id)
        viewModel.toggleSelection(second.id)
        advanceUntilIdle()
        assertEquals(listOf(second.id, first.id), viewModel.uiState.value.selectedTrackIdsInOrder)
        viewModel.removeSelected()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.lastRemovalResult?.changedCount)
        assertEquals(emptySet<TrackId>(), viewModel.uiState.value.selectedTrackIds)
        viewModel.playAll()
        advanceUntilIdle()
        assertEquals(1, controller.playCalls)
        assertEquals(0, viewModel.uiState.value.playbackFeedback?.playedCount)
        assertEquals(0, viewModel.uiState.value.playbackFeedback?.skippedCount)
        viewModel.acknowledgePlaybackFeedback()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.playbackFeedback)
        collection.cancel()
    }

    @Test
    fun `play all reports unavailable and missing tracks while queuing only playable tracks`() = runTest(dispatcher) {
        val playable = track(1)
        val unavailable = track(2, Availability.TEMPORARILY_UNAVAILABLE)
        val missing = TrackId("external", 3)
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Mixed",
            normalizedName = "mixed",
            trackIds = listOf(unavailable.id, missing, playable.id),
            createdAtMs = 0,
        )
        val repository = FakePlaylistRepository(initialPlaylists = listOf(playlist))
        val controller = RecordingPlaybackController()
        val viewModel = PlaylistDetailViewModel(
            playlistRepository = repository,
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(playable, unavailable)),
            useCase = PlaylistUseCase(repository, Clock { 10 }),
            playbackController = controller,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        viewModel.playAll()
        advanceUntilIdle()

        assertEquals(listOf(playable.id), controller.playedContext?.orderedTrackIds)
        assertEquals(1, viewModel.uiState.value.playbackFeedback?.playedCount)
        assertEquals(2, viewModel.uiState.value.playbackFeedback?.skippedCount)
        collection.cancel()
    }

    private fun track(
        value: Long,
        availability: Availability = Availability.AVAILABLE,
    ) = Track(
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

private data class ArtworkRequest(
    val trackId: TrackId,
    val targetPx: Int,
)

private class RecordingArtworkRepository(
    private val outcomes: Map<TrackId, Any> = emptyMap(),
    private val failure: Throwable? = null,
) : ArtworkRepository {
    val requests = mutableListOf<ArtworkRequest>()

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requests += ArtworkRequest(track.id, targetPx)
        failure?.let { throw it }
        return when (val outcome = outcomes[track.id]) {
            is Throwable -> throw outcome
            is ArtworkResult -> outcome
            else -> ArtworkResult.Placeholder
        }
    }
}

private class SuspendingArtworkRepository(
    private val suspendedTrackId: TrackId,
    private val suspendedResult: CompletableDeferred<ArtworkResult>,
    private val immediateResult: ArtworkResult,
) : ArtworkRepository {
    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult =
        if (track.id == suspendedTrackId) suspendedResult.await() else immediateResult
}

private fun embeddedArtwork(pixel: Int): ArtworkResult.Embedded =
    ArtworkResult.Embedded(ArtworkImage(width = 1, height = 1, argbPixels = intArrayOf(pixel)))

private fun embeddedPixel(result: ArtworkResult): Int =
    (result as ArtworkResult.Embedded).image.argbPixels.single()

private class RecordingPlaybackController : PlaybackControllerFacade {
    override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())
    var playedContext: PlaybackContext? = null
    var playCalls: Int = 0

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: PlaybackContext) {
        playedContext = context
        playCalls += 1
    }
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
}
