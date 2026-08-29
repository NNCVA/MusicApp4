package com.musicapp.player.feature.artists

import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistsViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `artwork candidates are attempted in track identity order and stop at first embedded`() =
        runTest(dispatcher) {
            val first = track(1, dateModifiedMs = 10)
            val second = track(2, dateModifiedMs = 20)
            val third = track(3, dateModifiedMs = 30)
            val repository = RecordingArtworkRepository(
                outcomes = mapOf(
                    first.id to IllegalStateException("unreadable"),
                    second.id to ArtworkResult.Embedded(image()),
                    third.id to ArtworkResult.Embedded(image(0xFF00FF00.toInt())),
                ),
            )
            val viewModel = subject(listOf(first, second, third), repository)
            collectState(viewModel)
            advanceUntilIdle()

            val artist =
                ArtistSummary(
                    id = ArtistId("artist"),
                    displayName = "Artist",
                    trackCount = 3,
                    artworkCandidates = listOf(third, first, second),
                )
            viewModel.requestArtwork(artist)
            viewModel.requestArtwork(artist)
            advanceUntilIdle()

            assertEquals(listOf(first.id, second.id), repository.requests.map { it.trackId })
            assertTrue(repository.requests.all { it.targetPx == 128 })
            assertTrue(viewModel.uiState.value.artworkByArtistId.getValue(artist.id).artwork is ArtworkResult.Embedded)
            assertTrue(viewModel.uiState.value.artworkByArtistId.getValue(artist.id).matches(artist))
        }

    @Test
    fun `a changed candidate signature prevents an older request from replacing newer artwork`() =
        runTest(dispatcher) {
            val oldTrack = track(1, dateModifiedMs = 10)
            val newTrack = track(2, dateModifiedMs = 20)
            val oldResult = CompletableDeferred<ArtworkResult>()
            val repository = SuspendingArtworkRepository(oldTrack.id, oldResult)
            val viewModel = subject(listOf(oldTrack), repository)
            collectState(viewModel)
            advanceUntilIdle()

            val oldArtist = artist(oldTrack)
            val newArtist = artist(newTrack)
            viewModel.requestArtwork(oldArtist)
            testScheduler.runCurrent()
            viewModel.requestArtwork(newArtist)
            advanceUntilIdle()

            val state = viewModel.uiState.value.artworkByArtistId.getValue(newArtist.id)
            assertTrue(state.matches(newArtist))
            assertTrue(state.artwork is ArtworkResult.Embedded)
            assertEquals(listOf(oldTrack.id, newTrack.id), repository.requests.map { it.trackId })
        }

    @Test
    fun `all missing candidates leave a placeholder and duplicate requests are cached`() =
        runTest(dispatcher) {
            val track = track(1, dateModifiedMs = 10)
            val repository = RecordingArtworkRepository(
                outcomes = mapOf(track.id to ArtworkResult.Placeholder),
            )
            val viewModel = subject(listOf(track), repository)
            collectState(viewModel)
            advanceUntilIdle()
            val artist = artist(track)

            viewModel.requestArtwork(artist)
            advanceUntilIdle()
            viewModel.requestArtwork(artist)
            advanceUntilIdle()

            assertEquals(1, repository.requests.size)
            assertSame(ArtworkResult.Placeholder, viewModel.uiState.value.artworkByArtistId.getValue(artist.id).artwork)
        }

    @Test
    fun `artist detail filters tracks including collaboration songs and matches display name`() =
        runTest(dispatcher) {
            val t1 = track(1, dateModifiedMs = 10, artistName = "周杰伦")
            val t2 = track(2, dateModifiedMs = 20, artistName = "周杰伦/王力宏")
            val t3 = track(3, dateModifiedMs = 30, artistName = "王力宏")
            val fakeRepo = FakeMediaLibraryRepository(listOf(t1, t2, t3))
            val detailVm = ArtistDetailViewModel(
                mediaLibraryRepository = fakeRepo,
                playbackController = NoOpPlaybackController(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detailVm.uiState.collect {} }
            detailVm.open(ArtistId("周杰伦"))
            advanceUntilIdle()

            val state = detailVm.uiState.value
            assertEquals("周杰伦", state.displayName)
            assertEquals(listOf(t1.id, t2.id), state.tracks.map { it.id })

            detailVm.open(ArtistId("王力宏"))
            advanceUntilIdle()
            val leehomState = detailVm.uiState.value
            assertEquals("王力宏", leehomState.displayName)
            assertEquals(listOf(t2.id, t3.id), leehomState.tracks.map { it.id })
        }

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: ArtistsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.runCurrent()
    }

    private fun subject(
        tracks: List<Track>,
        artworkRepository: ArtworkRepository,
    ) = ArtistsViewModel(
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        artworkRepository = artworkRepository,
    )

    private fun artist(track: Track) =
        ArtistSummary(
            id = ArtistId(track.artistName.lowercase()),
            displayName = track.artistName,
            trackCount = 1,
            artworkCandidates = listOf(track),
        )

    private fun track(id: Long, dateModifiedMs: Long, artistName: String = "Artist") =
        Track(
            id = TrackId("external", id),
            title = "Track $id",
            artistName = artistName,
            artistMediaStoreId = id,
            durationMs = 1_000,
            dateAddedMs = id,
            dateModifiedMs = dateModifiedMs,
            relativePath = "Music/",
            displayName = "$id.mp3",
        )

    private fun image(color: Int = 0xFF0000FF.toInt()) =
        ArtworkImage(width = 1, height = 1, argbPixels = intArrayOf(color))
}

private class NoOpPlaybackController : com.musicapp.player.core.playback.PlaybackControllerFacade {
    override val state: kotlinx.coroutines.flow.StateFlow<com.musicapp.player.core.playback.PlaybackControllerState> =
        kotlinx.coroutines.flow.MutableStateFlow(com.musicapp.player.core.playback.PlaybackControllerState())

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: com.musicapp.player.core.domain.model.PlaybackContext) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

private data class ArtworkRequest(val trackId: TrackId, val targetPx: Int)

private class RecordingArtworkRepository(
    private val outcomes: Map<TrackId, Any> = emptyMap(),
) : ArtworkRepository {
    val requests = mutableListOf<ArtworkRequest>()

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requests += ArtworkRequest(track.id, targetPx)
        return when (val outcome = outcomes[track.id]) {
            is Throwable -> throw outcome
            is ArtworkResult -> outcome
            else -> ArtworkResult.Placeholder
        }
    }
}

private class SuspendingArtworkRepository(
    private val suspendedTrackId: TrackId,
    private val result: CompletableDeferred<ArtworkResult>,
) : ArtworkRepository {
    val requests = mutableListOf<ArtworkRequest>()

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requests += ArtworkRequest(track.id, targetPx)
        return if (track.id == suspendedTrackId) {
            result.await()
        } else {
            ArtworkResult.Embedded(ArtworkImage(1, 1, intArrayOf(0xFF0000FF.toInt())))
        }
    }
}
