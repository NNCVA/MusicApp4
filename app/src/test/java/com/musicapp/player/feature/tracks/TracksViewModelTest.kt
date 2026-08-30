package com.musicapp.player.feature.tracks

import androidx.lifecycle.SavedStateHandle
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.sync.scanResultTitle
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.feature.tracks.batch.DefaultBatchTrackActionExecutor
import com.musicapp.player.fakes.FakeClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TracksViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val placeholderArtworkRepository =
        object : ArtworkRepository {
            override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult =
                ArtworkResult.Placeholder
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty library is marked loaded after its first emission`() = runTest(dispatcher) {
        val viewModel = subject()

        assertFalse(viewModel.uiState.value.isLibraryLoaded)
        collectState(viewModel)

        assertTrue(viewModel.uiState.value.tracks.isEmpty())
        assertTrue(viewModel.uiState.value.isLibraryLoaded)
    }

    @Test
    fun `persisted tracks are shown`() = runTest(dispatcher) {
        val cachedTracks = listOf(track(1, title = "Cached"))
        val viewModel = subject(tracks = cachedTracks)
        collectState(viewModel)

        assertEquals(cachedTracks, viewModel.uiState.value.tracks)
        assertTrue(viewModel.uiState.value.isLibraryLoaded)
    }

    @Test
    fun `cached tracks do not wait for playlists to load`() = runTest(dispatcher) {
        val cachedTracks = listOf(track(1, title = "Cached"))
        val viewModel = subject(
            tracks = cachedTracks,
            playlistRepository = NeverEmittingPlaylistRepository(),
        )
        collectState(viewModel)

        assertEquals(cachedTracks, viewModel.uiState.value.tracks)
        assertTrue(viewModel.uiState.value.isLibraryLoaded)
    }

    @Test
    fun `cached tracks stay visible`() = runTest(dispatcher) {
        val tracks = listOf(track(1, title = "Cached"))
        val viewModel = subject(tracks = tracks)
        collectState(viewModel)

        assertEquals(tracks, viewModel.uiState.value.tracks)
    }

    @Test
    fun `cached tracks stay visible while the library flow restarts`() = runTest(dispatcher) {
        val tracks = listOf(track(1, title = "Cached"))
        val repository = RestartingMediaLibraryRepository(tracks)
        val viewModel = subject(repository = repository)
        val firstCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
        viewModel.uiState.first { it.isLibraryLoaded }

        firstCollector.cancelAndJoin()
        testScheduler.advanceTimeBy(5_001)
        testScheduler.runCurrent()
        repository.firstCollectionStopped.await()

        val resumedStates = mutableListOf<TracksUiState>()
        val resumedCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect(resumedStates::add)
            }
        repository.secondCollectionStarted.await()
        testScheduler.runCurrent()

        assertTrue(resumedStates.isNotEmpty())
        assertTrue(resumedStates.all { it.isLibraryLoaded && it.tracks == tracks })
        resumedCollector.cancelAndJoin()
        testScheduler.advanceTimeBy(5_001)
        testScheduler.runCurrent()
        repository.secondCollectionStopped.await()
    }

    @Test
    fun `artwork completion keeps the sorted track list reference`() = runTest(dispatcher) {
        val track = track(1, title = "Artwork")
        val artwork =
            ArtworkResult.Embedded(
                ArtworkImage(width = 1, height = 1, argbPixels = intArrayOf(0xFF112233.toInt())),
            )
        val viewModel =
            subject(
                tracks = listOf(track),
                artworkRepository = ImmediateArtworkRepository(artwork),
            )
        collectState(viewModel)
        val sortedTracks = viewModel.uiState.value.tracks

        viewModel.requestArtwork(track)
        val updated = viewModel.uiState.first { it.artworkByTrackId[track.id]?.artwork === artwork }

        assertSame(sortedTracks, updated.tracks)
    }

    @Test
    fun `concurrent artwork requests load the same track once`() = runTest(dispatcher) {
        val track = track(1, title = "Artwork")
        val artworkRepository = SuspendingArtworkRepository()
        val viewModel = subject(tracks = listOf(track), artworkRepository = artworkRepository)
        collectState(viewModel)

        val first = launch { viewModel.requestArtwork(track) }
        val duplicate = launch { viewModel.requestArtwork(track) }
        testScheduler.runCurrent()

        assertEquals(1, artworkRepository.requestCount)
        artworkRepository.result.complete(ArtworkResult.Placeholder)
        testScheduler.advanceUntilIdle()
        first.join()
        duplicate.join()
        assertEquals(1, artworkRepository.requestCount)
    }

    @Test
    fun `cancelled artwork request can retry when the row returns`() = runTest(dispatcher) {
        val track = track(1, title = "Artwork")
        val artworkRepository = CancellableArtworkRepository()
        val viewModel = subject(tracks = listOf(track), artworkRepository = artworkRepository)
        collectState(viewModel)

        val first = launch { viewModel.requestArtwork(track) }
        testScheduler.runCurrent()
        assertEquals(1, artworkRepository.requestCount)
        first.cancelAndJoin()

        val retry = launch { viewModel.requestArtwork(track) }
        testScheduler.runCurrent()
        assertEquals(2, artworkRepository.requestCount)
        retry.cancelAndJoin()
    }

    @Test
    fun `all sort fields use defaults toggle and restore through saved state`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val tracks =
            listOf(
                track(1, title = "Charlie", artist = "Alpha", album = "Beta", dateAddedMs = 10, durationMs = 3_000),
                track(2, title = "Alpha", artist = "Charlie", album = "Alpha", dateAddedMs = 20, durationMs = 1_000),
                track(3, title = "Beta", artist = "Beta", album = "Charlie", dateAddedMs = 15, durationMs = 2_000),
            )
        val first = subject(tracks, savedState = savedState)
        collectState(first)
        assertEquals(listOf(2L, 3L, 1L), first.uiState.value.tracks.map { it.id.mediaStoreId })
        first.selectSort(TrackSortField.ARTIST)
        assertEquals(
            listOf(1L, 3L, 2L),
            first.awaitSort(TrackSort(TrackSortField.ARTIST, TrackSortDirection.ASCENDING))
                .tracks.map { it.id.mediaStoreId },
        )
        first.selectSort(TrackSortField.ALBUM)
        assertEquals(
            listOf(2L, 1L, 3L),
            first.awaitSort(TrackSort(TrackSortField.ALBUM, TrackSortDirection.ASCENDING))
                .tracks.map { it.id.mediaStoreId },
        )
        first.selectSort(TrackSortField.DATE_ADDED)
        assertEquals(
            listOf(2L, 3L, 1L),
            first.awaitSort(TrackSort(TrackSortField.DATE_ADDED, TrackSortDirection.DESCENDING))
                .tracks.map { it.id.mediaStoreId },
        )
        first.selectSort(TrackSortField.DURATION)
        assertEquals(
            listOf(2L, 3L, 1L),
            first.awaitSort(TrackSort(TrackSortField.DURATION, TrackSortDirection.ASCENDING))
                .tracks.map { it.id.mediaStoreId },
        )
        first.selectSort(TrackSortField.DURATION)
        assertEquals(
            listOf(1L, 3L, 2L),
            first.awaitSort(TrackSort(TrackSortField.DURATION, TrackSortDirection.DESCENDING))
                .tracks.map { it.id.mediaStoreId },
        )

        val restored = subject(tracks, savedState = savedState)
        collectState(restored)
        assertEquals(TrackSort(TrackSortField.DURATION, TrackSortDirection.DESCENDING), restored.uiState.value.sort)
        assertEquals(listOf(1L, 3L, 2L), restored.uiState.value.tracks.map { it.id.mediaStoreId })
    }

    @Test
    fun `hide selected updates repository without deleting the track`() = runTest(dispatcher) {
        val track = track(1, title = "Hidden")
        val repository = FakeMediaLibraryRepository(listOf(track))
        val viewModel = subject(repository = repository)
        collectState(viewModel)
        viewModel.toggleSelection(track.id)
        testScheduler.runCurrent()
        viewModel.hideSelected()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.tracks.isEmpty())
        assertEquals(track, repository.getTrack(track.id))
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `title sorting is deterministic when device locale is Turkish`() = runTest(dispatcher) {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val viewModel =
                subject(
                    tracks =
                        listOf(
                            track(1, title = "ı"),
                            track(2, title = "I"),
                        ),
                )
            collectState(viewModel)

            assertEquals(listOf(2L, 1L), viewModel.uiState.value.tracks.map { it.id.mediaStoreId })
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `selection removed from current results is ignored by back and hide`() = runTest(dispatcher) {
        val track = track(1, "Removed")
        val delegate = FakeMediaLibraryRepository(listOf(track))
        val repository = RecordingMediaLibraryRepository(delegate)
        val viewModel = subject(repository = repository)
        collectState(viewModel)
        viewModel.toggleSelection(track.id)
        testScheduler.runCurrent()
        delegate.setHidden(track.id, hidden = true, changedAtMs = 1)
        viewModel.uiState.first { it.isLibraryLoaded && it.tracks.isEmpty() }

        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
        assertFalse(viewModel.onBack())
        viewModel.hideSelected()
        testScheduler.advanceUntilIdle()
        assertTrue(repository.hiddenRequests.isEmpty())
    }

    @Test
    fun `scan result title falls back to display name then stable media id`() {
        val fromDisplayName =
            MediaAudioCandidate(
                volumeName = "external",
                mediaStoreId = 7,
                title = " ",
                displayName = "Song.mp3",
                mimeType = "audio/mpeg",
                durationMs = 1_000,
            )
        val fromId = fromDisplayName.copy(mediaStoreId = 8, displayName = "")

        assertEquals("Song", fromDisplayName.scanResultTitle())
        assertEquals("8", fromId.scanResultTitle())
    }

    @Test
    fun `multi selection supports current result select all and back clears first`() = runTest(dispatcher) {
        val tracks = listOf(track(1, "One"), track(2, "Two"))
        val viewModel = subject(tracks)
        collectState(viewModel)
        viewModel.toggleSelection(tracks.first().id)
        viewModel.selectAllCurrentResults()
        testScheduler.runCurrent()

        assertEquals(tracks.map(Track::id).toSet(), viewModel.uiState.value.selectedTrackIds)
        assertTrue(viewModel.onBack())
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertFalse(viewModel.onBack())
    }

    @Test
    fun `batch actions preserve user selection order and empty selection is not dispatched`() =
        runTest(dispatcher) {
            val tracks = listOf(track(1, "Charlie"), track(2, "Alpha"), track(3, "Beta"))
            val executor = RecordingBatchTrackActionExecutor()
            val viewModel = subject(tracks = tracks, batchActionExecutor = executor)
            collectState(viewModel)

            viewModel.toggleSelection(tracks[2].id)
            viewModel.toggleSelection(tracks[0].id)
            viewModel.toggleSelection(tracks[1].id)
            viewModel.addSelectedToQueue()
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(tracks[2].id, tracks[0].id, tracks[1].id),
                executor.requests.single().second,
            )
            assertFalse(viewModel.uiState.value.isSelectionMode)
            viewModel.addSelectedToQueue()
            testScheduler.advanceUntilIdle()
            assertEquals(1, executor.requests.size)
        }

    @Test
    fun `select all dispatches current sorted results and failure keeps selection`() =
        runTest(dispatcher) {
            val tracks = listOf(track(1, "Charlie"), track(2, "Alpha"), track(3, "Beta"))
            val executor =
                RecordingBatchTrackActionExecutor(
                    resultFactory = { action, ids -> BatchTrackActionResult.Failed(action, ids.size) },
                )
            val viewModel = subject(tracks = tracks, batchActionExecutor = executor)
            collectState(viewModel)

            viewModel.selectAllCurrentResults()
            viewModel.playSelectedNext()
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(tracks[1].id, tracks[2].id, tracks[0].id),
                executor.requests.single().second,
            )
            assertEquals(3, viewModel.uiState.value.selectedTrackIds.size)
            assertTrue(viewModel.uiState.value.batchResult is BatchTrackActionResult.Failed)
            viewModel.acknowledgeBatchResult()
            testScheduler.runCurrent()
            assertEquals(null, viewModel.uiState.value.batchResult)
        }

    @Test
    fun `running batch action rejects repeated queue and play next commands without result race`() =
        runTest(dispatcher) {
            val track = track(1, "Track")
            val executor = SuspendingBatchTrackActionExecutor()
            val viewModel = subject(tracks = listOf(track), batchActionExecutor = executor)
            collectState(viewModel)
            viewModel.toggleSelection(track.id)

            viewModel.addSelectedToQueue()
            viewModel.addSelectedToQueue()
            viewModel.playSelectedNext()
            testScheduler.runCurrent()

            assertEquals(
                listOf(BatchTrackAction.AddToQueue to listOf(track.id)),
                executor.requests,
            )
            assertTrue(viewModel.uiState.value.isBatchActionRunning)

            executor.release.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isBatchActionRunning)
            assertEquals(
                BatchTrackAction.AddToQueue,
                (viewModel.uiState.value.batchResult as BatchTrackActionResult.Completed).action,
            )
            assertEquals(1, executor.requests.size)
        }

    @Test
    fun `track click starts a list-repeat context in the visible sort order`() = runTest(dispatcher) {
        val playbackController = RecordingPlaybackControllerFacade()
        val first = track(1, "Alpha")
        val second = track(2, "Beta")
        val viewModel = subject(
            tracks = listOf(second, first),
            playbackController = playbackController,
        )
        collectState(viewModel)

        viewModel.playTrack(second.id)

        assertEquals(listOf(first.id, second.id), playbackController.context?.orderedTrackIds)
        assertEquals(second.id, playbackController.context?.selectedTrackId)
    }

    @Test
    fun `playAll starts playback from the first available track in order`() = runTest(dispatcher) {
        val playbackController = RecordingPlaybackControllerFacade()
        val first = track(1, "Alpha")
        val second = track(2, "Beta")
        val viewModel = subject(
            tracks = listOf(second, first),
            playbackController = playbackController,
        )
        collectState(viewModel)

        viewModel.playAll()

        assertEquals(listOf(first.id, second.id), playbackController.context?.orderedTrackIds)
        assertEquals(first.id, playbackController.context?.selectedTrackId)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.collectState(viewModel: TracksViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.isLibraryLoaded }
    }

    private suspend fun TracksViewModel.awaitSort(expected: TrackSort): TracksUiState =
        uiState.first { it.isLibraryLoaded && it.sort == expected }

    private fun subject(
        tracks: List<Track> = emptyList(),
        repository: MediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        savedState: SavedStateHandle = SavedStateHandle(),
        playbackController: PlaybackControllerFacade = RecordingPlaybackControllerFacade(),
        playlistRepository: PlaylistRepository = FakePlaylistRepository(),
        batchActionExecutor: BatchTrackActionExecutor? = null,
        artworkRepository: ArtworkRepository = placeholderArtworkRepository,
    ): TracksViewModel {
        val clock = FakeClock(123)
        return TracksViewModel(
            mediaLibraryRepository = repository,
            playlistRepository = playlistRepository,
            savedStateHandle = savedState,
            playbackController = playbackController,
            batchActionExecutor =
                batchActionExecutor
                    ?: DefaultBatchTrackActionExecutor(
                        playlistRepository = playlistRepository,
                        mediaLibraryRepository = repository,
                        playbackController = playbackController,
                        clock = clock,
                    ),
            artworkRepository = artworkRepository,
            computationDispatcher = dispatcher,
        )
    }

    private class RecordingPlaybackControllerFacade : PlaybackControllerFacade {
        override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())
        var context: PlaybackContext? = null

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun play(context: PlaybackContext) {
            this.context = context
        }

        override fun play() = Unit

        override fun pause() = Unit

        override fun skipToPrevious() = Unit

        override fun skipToNext() = Unit

        override fun seekTo(positionMs: Long) = Unit
    }

    private fun track(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String? = null,
        dateAddedMs: Long = id,
        durationMs: Long = id * 1_000,
    ): Track =
        Track(
            id = TrackId("external", id),
            title = title,
            artistName = artist,
            albumTitle = album,
            durationMs = durationMs,
            dateAddedMs = dateAddedMs,
            dateModifiedMs = dateAddedMs,
            relativePath = "Music/",
            displayName = "$title.mp3",
        )

}

private class ImmediateArtworkRepository(
    private val result: ArtworkResult,
) : ArtworkRepository {
    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult = result
}

private class SuspendingArtworkRepository : ArtworkRepository {
    var requestCount: Int = 0
        private set
    val result = CompletableDeferred<ArtworkResult>()

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requestCount += 1
        return result.await()
    }
}

private class CancellableArtworkRepository : ArtworkRepository {
    var requestCount: Int = 0
        private set

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requestCount += 1
        awaitCancellation()
    }
}

private class RestartingMediaLibraryRepository(
    private val tracks: List<Track>,
    delegate: MediaLibraryRepository = FakeMediaLibraryRepository(tracks),
) : MediaLibraryRepository by delegate {
    private val collectionCount = AtomicInteger()
    val firstCollectionStopped = CompletableDeferred<Unit>()
    val secondCollectionStarted = CompletableDeferred<Unit>()
    val secondCollectionStopped = CompletableDeferred<Unit>()

    override fun observeTracks(includeHidden: Boolean): Flow<List<Track>> =
        flow {
            when (collectionCount.incrementAndGet()) {
                1 -> {
                    try {
                        emit(tracks)
                        awaitCancellation()
                    } finally {
                        firstCollectionStopped.complete(Unit)
                    }
                }
                else -> {
                    try {
                        secondCollectionStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        secondCollectionStopped.complete(Unit)
                    }
                }
            }
        }
}

private class RecordingBatchTrackActionExecutor(
    private val resultFactory: (BatchTrackAction, List<TrackId>) -> BatchTrackActionResult =
        { action, ids ->
            BatchTrackActionResult.Completed(action, ids.size, ids.size, skippedCount = 0)
        },
) : BatchTrackActionExecutor {
    val requests = mutableListOf<Pair<BatchTrackAction, List<TrackId>>>()

    override suspend fun execute(
        action: BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): BatchTrackActionResult {
        requests += action to orderedTrackIds
        return resultFactory(action, orderedTrackIds)
    }
}

private class SuspendingBatchTrackActionExecutor : BatchTrackActionExecutor {
    val requests = mutableListOf<Pair<BatchTrackAction, List<TrackId>>>()
    val release = CompletableDeferred<Unit>()

    override suspend fun execute(
        action: BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): BatchTrackActionResult {
        requests += action to orderedTrackIds
        release.await()
        return BatchTrackActionResult.Completed(
            action = action,
            selectedCount = orderedTrackIds.size,
            affectedCount = orderedTrackIds.size,
            skippedCount = 0,
        )
    }
}

private class RecordingMediaLibraryRepository(
    private val delegate: MediaLibraryRepository,
) : MediaLibraryRepository by delegate {
    val hiddenRequests = mutableListOf<TrackId>()

    override suspend fun setHidden(trackId: TrackId, hidden: Boolean, changedAtMs: Long) {
        hiddenRequests += trackId
        delegate.setHidden(trackId, hidden, changedAtMs)
    }
}

private class NeverEmittingPlaylistRepository(
    delegate: PlaylistRepository = FakePlaylistRepository(),
) : PlaylistRepository by delegate {
    override fun observePlaylists(): Flow<List<Playlist>> = emptyFlow()
}
