package com.musicapp.player.feature.tracks

import androidx.lifecycle.SavedStateHandle
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.media.MediaAudioCandidate
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.data.sync.MediaLibraryScanSummary
import com.musicapp.player.data.sync.MediaLibrarySyncFeedback
import com.musicapp.player.data.sync.MediaLibrarySyncFailure
import com.musicapp.player.data.sync.MediaLibrarySyncTrigger
import com.musicapp.player.data.sync.PendingLibrarySyncFeedback
import com.musicapp.player.data.sync.SyncReport
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.feature.tracks.batch.DefaultBatchTrackActionExecutor
import com.musicapp.player.fakes.FakeClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TracksViewModelTest {
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
    fun `no cache starts in full screen loading state`() = runTest(dispatcher) {
        val viewModel = subject(syncState = LibrarySyncState.Idle(false))
        collectState(viewModel)

        assertTrue(viewModel.uiState.value.isInitialLoading)
        assertFalse(viewModel.uiState.value.fullScreenFailure)
    }

    @Test
    fun `successful empty cache shows zero tracks without loading`() = runTest(dispatcher) {
        val viewModel = subject(syncState = LibrarySyncState.Idle(true))
        collectState(viewModel)

        assertFalse(viewModel.uiState.value.isInitialLoading)
        assertTrue(viewModel.uiState.value.tracks.isEmpty())
    }

    @Test
    fun `cached failure preserves tracks and exposes local error`() = runTest(dispatcher) {
        val track = track(1, title = "Cached")
        val viewModel =
            subject(
                tracks = listOf(track),
                syncState =
                    LibrarySyncState.Failed(
                        hasSuccessfulScan = true,
                        trigger = MediaLibrarySyncTrigger.CONTENT_CHANGE,
                        failure = MediaLibrarySyncFailure.QUERY_FAILED,
                    ),
            )
        collectState(viewModel)

        assertTrue(viewModel.uiState.value.cachedFailure)
        assertEquals(listOf(track), viewModel.uiState.value.tracks)
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
        val first = subject(tracks, LibrarySyncState.Idle(true), savedState = savedState)
        collectState(first)
        assertEquals(listOf(2L, 3L, 1L), first.uiState.value.tracks.map { it.id.mediaStoreId })
        first.selectSort(TrackSortField.ARTIST)
        testScheduler.runCurrent()
        assertEquals(listOf(1L, 3L, 2L), first.uiState.value.tracks.map { it.id.mediaStoreId })
        first.selectSort(TrackSortField.ALBUM)
        testScheduler.runCurrent()
        assertEquals(listOf(2L, 1L, 3L), first.uiState.value.tracks.map { it.id.mediaStoreId })
        first.selectSort(TrackSortField.DATE_ADDED)
        testScheduler.runCurrent()
        assertEquals(listOf(2L, 3L, 1L), first.uiState.value.tracks.map { it.id.mediaStoreId })
        first.selectSort(TrackSortField.DURATION)
        testScheduler.runCurrent()
        assertEquals(listOf(2L, 3L, 1L), first.uiState.value.tracks.map { it.id.mediaStoreId })
        first.selectSort(TrackSortField.DURATION)
        testScheduler.runCurrent()
        assertEquals(listOf(1L, 3L, 2L), first.uiState.value.tracks.map { it.id.mediaStoreId })

        val restored = subject(tracks, LibrarySyncState.Idle(true), savedState = savedState)
        collectState(restored)
        testScheduler.runCurrent()
        assertEquals(TrackSort(TrackSortField.DURATION, TrackSortDirection.DESCENDING), restored.uiState.value.sort)
        assertEquals(listOf(1L, 3L, 2L), restored.uiState.value.tracks.map { it.id.mediaStoreId })
    }

    @Test
    fun `hide selected updates repository without deleting the track`() = runTest(dispatcher) {
        val track = track(1, title = "Hidden")
        val repository = FakeMediaLibraryRepository(listOf(track))
        val viewModel = subject(repository = repository, syncState = LibrarySyncState.Idle(true))
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
        testScheduler.runCurrent()

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
        val viewModel = subject(tracks, LibrarySyncState.Idle(true))
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
    fun `feedback acknowledgement delegates event id and clears pending state`() = runTest(dispatcher) {
        val feedback = completedFeedback(eventId = 42)
        val controller = FakeTracksSyncController(LibrarySyncState.Idle(true, feedback))
        val viewModel = subject(syncController = controller)
        collectState(viewModel)

        viewModel.acknowledgeFeedback(42)
        testScheduler.runCurrent()

        assertEquals(listOf(42L), controller.acknowledgedIds)
        assertEquals(null, viewModel.uiState.value.pendingFeedback)
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

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: TracksViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.runCurrent()
    }

    private fun subject(
        tracks: List<Track> = emptyList(),
        syncState: LibrarySyncState = LibrarySyncState.Idle(true),
        repository: MediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        syncController: FakeTracksSyncController = FakeTracksSyncController(syncState),
        savedState: SavedStateHandle = SavedStateHandle(),
        playbackController: PlaybackControllerFacade = RecordingPlaybackControllerFacade(),
        playlistRepository: PlaylistRepository = FakePlaylistRepository(),
        batchActionExecutor: BatchTrackActionExecutor? = null,
    ): TracksViewModel {
        val clock = FakeClock(123)
        return TracksViewModel(
            mediaLibraryRepository = repository,
            playlistRepository = playlistRepository,
            syncCoordinator = syncController,
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

    private fun completedFeedback(eventId: Long): PendingLibrarySyncFeedback {
        val candidate =
            MediaAudioCandidate(
                volumeName = "external",
                mediaStoreId = 1,
                title = "Track",
                displayName = "Track.mp3",
                mimeType = "audio/mpeg",
                durationMs = 1_000,
            )
        val report =
            SyncReport(
                generation = 1,
                upsertedTrackCount = 1,
                removedTrackCount = 0,
                temporarilyUnavailableVolumeNames = emptySet(),
                scanSummary = MediaLibraryScanSummary(1, listOf(candidate), emptyList()),
            )
        return PendingLibrarySyncFeedback(
            eventId,
            LibrarySyncEvent.Completed(
                trigger = MediaLibrarySyncTrigger.MANUAL,
                feedback = MediaLibrarySyncFeedback.RESULT_DIALOG,
                result = report,
            ),
        )
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

private class FakeTracksSyncController(initialState: LibrarySyncState) : TracksSyncController {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<LibrarySyncState> = mutableState
    val acknowledgedIds = mutableListOf<Long>()
    var manualSyncRequests: Int = 0

    override fun requestManualSync() {
        manualSyncRequests++
    }

    override fun acknowledgeFeedback(eventId: Long) {
        acknowledgedIds += eventId
        mutableState.value =
            when (val current = mutableState.value) {
                is LibrarySyncState.Idle -> current.copy(pendingFeedback = null)
                is LibrarySyncState.Syncing -> current.copy(pendingFeedback = null)
                is LibrarySyncState.Failed -> current.copy(pendingFeedback = null)
            }
    }
}
