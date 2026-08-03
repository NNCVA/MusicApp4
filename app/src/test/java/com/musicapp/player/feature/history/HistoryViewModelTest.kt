package com.musicapp.player.feature.history

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.repository.HistoryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
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
    fun `initial state loads history newest first and retains a recognizable missing entry`() = runTest(dispatcher) {
        val newest = track(1, "Newest")
        val older = track(2, "Older")
        val missingId = TrackId("card", 99)
        val repository = RecordingHistoryRepository(
            listOf(
                PlayHistory(older.id, lastPlayedAtMs = 10, playCount = 1),
                PlayHistory(missingId, lastPlayedAtMs = 20, playCount = 3),
                PlayHistory(newest.id, lastPlayedAtMs = 30, playCount = 2),
            ),
        )
        val viewModel = subject(repository, listOf(older, newest))

        assertTrue(viewModel.uiState.value.isLoading)
        collectState(viewModel)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(newest.id, missingId, older.id), viewModel.uiState.value.entries.map(HistoryEntry::trackId))
        assertNull(viewModel.uiState.value.entries[1].track)
        viewModel.toggleSelection(missingId)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
    }

    @Test
    fun `empty history exposes an empty loaded state and never opens clear confirmation`() = runTest(dispatcher) {
        val repository = RecordingHistoryRepository()
        val viewModel = subject(repository)
        collectState(viewModel)

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        viewModel.requestClearHistory()
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.clearConfirmationVisible)
        assertEquals(0, repository.clearCalls)
    }

    @Test
    fun `selection and select all are scoped to the current filter in visible order`() = runTest(dispatcher) {
        val alpha = track(1, "Alpha", artist = "First")
        val beta = track(2, "Beta", artist = "Second")
        val alphabet = track(3, "Alphabet", artist = "Third")
        val repository = RecordingHistoryRepository(
            listOf(
                PlayHistory(alpha.id, 30, 1),
                PlayHistory(beta.id, 20, 1),
                PlayHistory(alphabet.id, 10, 1),
            ),
        )
        val viewModel = subject(repository, listOf(alpha, beta, alphabet))
        collectState(viewModel)

        viewModel.setQuery("alpha")
        testScheduler.runCurrent()
        viewModel.selectAllVisible()
        testScheduler.runCurrent()

        assertEquals(listOf(alpha.id, alphabet.id), viewModel.uiState.value.visibleEntries.map(HistoryEntry::trackId))
        assertEquals(setOf(alpha.id, alphabet.id), viewModel.uiState.value.selectedTrackIds)
        assertEquals(listOf(alpha.id, alphabet.id), viewModel.uiState.value.selectedTrackIdsInVisibleOrder)

        viewModel.setQuery("second")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
        viewModel.toggleSelection(beta.id)
        testScheduler.runCurrent()
        assertTrue(viewModel.onBack())
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertFalse(viewModel.onBack())
    }

    @Test
    fun `batch actions receive selected ids in current visible history order`() = runTest(dispatcher) {
        val first = track(1, "First")
        val second = track(2, "Second")
        val executor = RecordingBatchTrackActionExecutor()
        val repository = RecordingHistoryRepository(
            listOf(PlayHistory(second.id, 20, 1), PlayHistory(first.id, 30, 1)),
        )
        val viewModel = subject(repository, listOf(first, second), executor)
        collectState(viewModel)
        viewModel.toggleSelection(second.id)
        viewModel.toggleSelection(first.id)
        testScheduler.runCurrent()
        assertEquals(setOf(first.id, second.id), viewModel.uiState.value.selectedTrackIds)

        viewModel.executeSelected(BatchTrackAction.AddToQueue)
        testScheduler.advanceUntilIdle()

        assertEquals(BatchTrackAction.AddToQueue, executor.action)
        assertEquals(listOf(first.id, second.id), executor.trackIds)
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
        assertTrue(viewModel.uiState.value.batchResult is BatchTrackActionResult.Completed)
        viewModel.acknowledgeBatchResult()
        testScheduler.runCurrent()
        assertNull(viewModel.uiState.value.batchResult)
    }

    @Test
    fun `track click plays current visible available history in newest first order`() = runTest(dispatcher) {
        val newest = track(1, "Newest")
        val unavailable = track(2, "Unavailable", availability = Availability.TEMPORARILY_UNAVAILABLE)
        val oldest = track(3, "Oldest")
        val missingId = TrackId("card", 99)
        val playbackController = RecordingPlaybackControllerFacade()
        val repository = RecordingHistoryRepository(
            listOf(
                PlayHistory(oldest.id, 10, 1),
                PlayHistory(missingId, 20, 1),
                PlayHistory(unavailable.id, 30, 1),
                PlayHistory(newest.id, 40, 1),
            ),
        )
        val viewModel = subject(
            historyRepository = repository,
            tracks = listOf(oldest, unavailable, newest),
            playbackController = playbackController,
        )
        collectState(viewModel)

        viewModel.playTrack(oldest.id)

        assertEquals(PlaybackContextSource.HISTORY, playbackController.contexts.single().source)
        assertEquals(listOf(newest.id, oldest.id), playbackController.contexts.single().orderedTrackIds)
        assertEquals(oldest.id, playbackController.contexts.single().selectedTrackId)

        viewModel.playTrack(unavailable.id)
        viewModel.playTrack(missingId)
        assertEquals(1, playbackController.contexts.size)
    }

    @Test
    fun `playlist observation exposes choices and add to playlist keeps visible order`() = runTest(dispatcher) {
        val first = track(1, "First")
        val second = track(2, "Second")
        val playlist = Playlist(PlaylistId(7), "Road trip", "road trip", createdAtMs = 1)
        val executor = RecordingBatchTrackActionExecutor()
        val repository = RecordingHistoryRepository(
            listOf(PlayHistory(second.id, 10, 1), PlayHistory(first.id, 20, 1)),
        )
        val viewModel = subject(
            historyRepository = repository,
            tracks = listOf(first, second),
            batchTrackActionExecutor = executor,
            playlistRepository = FakePlaylistRepository(listOf(playlist)),
        )
        collectState(viewModel)
        viewModel.selectAllVisible()
        testScheduler.runCurrent()

        viewModel.executeSelected(BatchTrackAction.AddToPlaylist(playlist.id))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(playlist), viewModel.uiState.value.playlists)
        assertEquals(BatchTrackAction.AddToPlaylist(playlist.id), executor.action)
        assertEquals(listOf(first.id, second.id), executor.trackIds)
    }

    @Test
    fun `batch execution is mutually exclusive and result remains until acknowledged`() = runTest(dispatcher) {
        val item = track(1, "One")
        val executor = BlockingBatchTrackActionExecutor()
        val viewModel = subject(
            historyRepository = RecordingHistoryRepository(listOf(PlayHistory(item.id, 10, 1))),
            tracks = listOf(item),
            batchTrackActionExecutor = executor,
        )
        collectState(viewModel)
        viewModel.toggleSelection(item.id)
        testScheduler.runCurrent()

        viewModel.executeSelected(BatchTrackAction.AddToQueue)
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isBatchActionRunning)
        viewModel.executeSelected(BatchTrackAction.PlayNext)
        executor.release.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(BatchTrackAction.AddToQueue), executor.actions)
        assertFalse(viewModel.uiState.value.isBatchActionRunning)
        assertTrue(viewModel.uiState.value.batchResult is BatchTrackActionResult.Completed)
        viewModel.acknowledgeBatchResult()
        testScheduler.runCurrent()
        assertNull(viewModel.uiState.value.batchResult)
    }

    @Test
    fun `clear history requires confirmation while cancel and duplicate confirm are bounded`() = runTest(dispatcher) {
        val item = track(1, "One")
        val repository = RecordingHistoryRepository(listOf(PlayHistory(item.id, 10, 1)))
        val viewModel = subject(repository, listOf(item))
        collectState(viewModel)

        viewModel.confirmClearHistory()
        testScheduler.runCurrent()
        assertEquals(0, repository.clearCalls)

        viewModel.requestClearHistory()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.clearConfirmationVisible)
        viewModel.cancelClearHistory()
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.clearConfirmationVisible)
        assertEquals(0, repository.clearCalls)

        viewModel.requestClearHistory()
        testScheduler.runCurrent()
        viewModel.confirmClearHistory()
        viewModel.confirmClearHistory()
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.clearCalls)
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertFalse(viewModel.uiState.value.clearConfirmationVisible)
        assertFalse(viewModel.uiState.value.isClearing)
    }

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: HistoryViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.runCurrent()
    }

    private fun subject(
        historyRepository: HistoryRepository,
        tracks: List<Track> = emptyList(),
        batchTrackActionExecutor: BatchTrackActionExecutor = RecordingBatchTrackActionExecutor(),
        playlistRepository: PlaylistRepository = FakePlaylistRepository(),
        playbackController: PlaybackControllerFacade = RecordingPlaybackControllerFacade(),
    ) = HistoryViewModel(
        historyRepository = historyRepository,
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        playlistRepository = playlistRepository,
        playbackController = playbackController,
        batchTrackActionExecutor = batchTrackActionExecutor,
    )

    private fun track(
        id: Long,
        title: String,
        artist: String = "Artist",
        availability: Availability = Availability.AVAILABLE,
    ) =
        Track(
            id = TrackId("external", id),
            title = title,
            artistName = artist,
            durationMs = 1_000,
            dateAddedMs = id,
            dateModifiedMs = id,
            relativePath = "Music/",
            displayName = "$title.mp3",
            availability = availability,
        )
}

private class RecordingPlaybackControllerFacade : PlaybackControllerFacade {
    override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())
    val contexts = mutableListOf<PlaybackContext>()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: PlaybackContext) {
        contexts += context
    }
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

private class RecordingBatchTrackActionExecutor : BatchTrackActionExecutor {
    var action: BatchTrackAction? = null
        private set
    var trackIds: List<TrackId> = emptyList()
        private set

    override suspend fun execute(
        action: BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): BatchTrackActionResult {
        this.action = action
        trackIds = orderedTrackIds
        if (orderedTrackIds.isEmpty()) return BatchTrackActionResult.EmptySelection
        return BatchTrackActionResult.Completed(
            action = action,
            selectedCount = orderedTrackIds.size,
            affectedCount = orderedTrackIds.size,
            skippedCount = 0,
        )
    }
}

private class BlockingBatchTrackActionExecutor : BatchTrackActionExecutor {
    val actions = mutableListOf<BatchTrackAction>()
    val release = CompletableDeferred<Unit>()

    override suspend fun execute(
        action: BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): BatchTrackActionResult {
        actions += action
        release.await()
        return BatchTrackActionResult.Completed(
            action = action,
            selectedCount = orderedTrackIds.size,
            affectedCount = orderedTrackIds.size,
            skippedCount = 0,
        )
    }
}

private class RecordingHistoryRepository(
    initialHistory: List<PlayHistory> = emptyList(),
) : HistoryRepository {
    private val history = MutableStateFlow(initialHistory)
    var clearCalls: Int = 0
        private set

    override fun observeHistory(): Flow<List<PlayHistory>> = history

    override suspend fun recordPlayback(trackId: TrackId, playedAtMs: Long) = Unit

    override suspend fun clearHistory() {
        clearCalls++
        history.value = emptyList()
    }
}
