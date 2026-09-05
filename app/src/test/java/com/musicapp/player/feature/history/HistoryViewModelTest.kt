package com.musicapp.player.feature.history

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.TrackMetadataRepository
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
import org.junit.Assert.assertNotNull
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
    fun `initial state loads history newest first and retains a recognizable missing entry which is selectable for deletion`() = runTest(dispatcher) {
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
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(missingId), viewModel.uiState.value.selectedTrackIds)
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
    fun `explicit selection mode supports 0 items and select all includes unavailable entries`() = runTest(dispatcher) {
        val available = track(1, "Available")
        val unavailable = track(2, "Unavailable", availability = Availability.TEMPORARILY_UNAVAILABLE)
        val repository = RecordingHistoryRepository(
            listOf(
                PlayHistory(available.id, 20, 1),
                PlayHistory(unavailable.id, 10, 1),
            ),
        )
        val viewModel = subject(repository, listOf(available, unavailable))
        collectState(viewModel)

        viewModel.enterSelectionMode()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(0, viewModel.uiState.value.selectedTrackIds.size)
        assertFalse(viewModel.uiState.value.hasPlayableSelection)

        viewModel.selectAllVisible()
        testScheduler.runCurrent()
        assertEquals(setOf(available.id, unavailable.id), viewModel.uiState.value.selectedTrackIds)
        assertTrue(viewModel.uiState.value.hasPlayableSelection)
        assertTrue(viewModel.uiState.value.isAllSelected)

        viewModel.toggleSelectAll()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
        assertTrue(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `selection and select all are scoped to current filter and query change clears selection`() = runTest(dispatcher) {
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
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.onBack())
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertFalse(viewModel.onBack())
    }

    @Test
    fun `temporary search and selection mode are mutually exclusive and onBack exits search first`() = runTest(dispatcher) {
        val first = track(1, "First")
        val second = track(2, "Second")
        val repository = RecordingHistoryRepository(
            listOf(PlayHistory(first.id, 20, 1), PlayHistory(second.id, 10, 1)),
        )
        val viewModel = subject(repository, listOf(first, second))
        collectState(viewModel)

        viewModel.openSearch()
        viewModel.setQuery("fir")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isSearchActive)
        assertEquals("fir", viewModel.uiState.value.query)

        // 长按或进入多选：关闭搜索并清空 query，进入多选
        viewModel.enterSelectionMode(second.id)
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isSearchActive)
        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(second.id), viewModel.uiState.value.selectedTrackIds)

        // 多选时返回退出多选
        assertTrue(viewModel.onBack())
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `single and batch delete requires confirmation and notifies user message`() = runTest(dispatcher) {
        val first = track(1, "First")
        val second = track(2, "Second")
        val repository = RecordingHistoryRepository(
            listOf(PlayHistory(first.id, 20, 1), PlayHistory(second.id, 10, 1)),
        )
        val viewModel = subject(repository, listOf(first, second))
        collectState(viewModel)

        // 单条删除
        viewModel.requestDeleteTrack(first.id)
        testScheduler.runCurrent()
        assertEquals(setOf(first.id), viewModel.uiState.value.deleteConfirmationTrackIds)

        viewModel.confirmDelete()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(setOf(first.id)), repository.deleteCalls)
        assertNull(viewModel.uiState.value.deleteConfirmationTrackIds)
        assertEquals(HistoryUserMessage.DeleteSuccess(1), viewModel.uiState.value.userMessage)
        viewModel.acknowledgeUserMessage()
        testScheduler.runCurrent()
        assertNull(viewModel.uiState.value.userMessage)

        // 批量删除
        viewModel.enterSelectionMode(second.id)
        testScheduler.runCurrent()
        viewModel.requestDeleteSelected()
        testScheduler.runCurrent()
        assertEquals(setOf(second.id), viewModel.uiState.value.deleteConfirmationTrackIds)

        viewModel.confirmDelete()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(setOf(first.id), setOf(second.id)), repository.deleteCalls)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(HistoryUserMessage.DeleteSuccess(1), viewModel.uiState.value.userMessage)
    }

    @Test
    fun `play all and track click plays current visible available history in newest first order`() = runTest(dispatcher) {
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

        // 播放全部
        viewModel.playAll()
        assertEquals(PlaybackContextSource.HISTORY, playbackController.contexts.single().source)
        assertEquals(listOf(newest.id, oldest.id), playbackController.contexts.single().orderedTrackIds)
        assertEquals(newest.id, playbackController.contexts.single().selectedTrackId)

        // 单曲点击
        viewModel.playTrack(oldest.id)
        assertEquals(2, playbackController.contexts.size)
        assertEquals(oldest.id, playbackController.contexts.last().selectedTrackId)

        // 不可用曲目与缺失项不触发播放
        viewModel.playTrack(unavailable.id)
        viewModel.playTrack(missingId)
        assertEquals(2, playbackController.contexts.size)
    }

    @Test
    fun `showTrackInfo loads metadata and dismissTrackInfo clears it`() = runTest(dispatcher) {
        val item = track(1, "Track with info")
        val metadataRepo = FakeTrackMetadataRepo()
        val viewModel = subject(
            historyRepository = RecordingHistoryRepository(listOf(PlayHistory(item.id, 10, 1))),
            tracks = listOf(item),
            trackMetadataRepository = metadataRepo,
        )
        collectState(viewModel)

        viewModel.showTrackInfo(item)
        testScheduler.advanceUntilIdle()
        assertEquals(item, viewModel.uiState.value.infoTrack)
        assertNotNull(viewModel.uiState.value.infoMetadata)
        assertFalse(viewModel.uiState.value.isInfoLoading)

        assertTrue(viewModel.onBack())
        testScheduler.runCurrent()
        assertNull(viewModel.uiState.value.infoTrack)
        assertNull(viewModel.uiState.value.infoMetadata)
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
        trackMetadataRepository: TrackMetadataRepository = FakeTrackMetadataRepo(),
    ) = HistoryViewModel(
        historyRepository = historyRepository,
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        playlistRepository = playlistRepository,
        playbackController = playbackController,
        batchTrackActionExecutor = batchTrackActionExecutor,
        trackMetadataRepository = trackMetadataRepository,
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

private class FakeTrackMetadataRepo : TrackMetadataRepository {
    override suspend fun read(track: Track): AdvancedTrackMetadata =
        AdvancedTrackMetadata(
            encoding = "flac",
            bitrateBps = 900_000L,
            sampleRateHz = 48_000,
            fileSizeBytes = 25_000_000L,
            isReadable = true,
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

private class RecordingHistoryRepository(
    initialHistory: List<PlayHistory> = emptyList(),
) : HistoryRepository {
    private val history = MutableStateFlow(initialHistory)
    var clearCalls: Int = 0
        private set
    val deleteCalls = mutableListOf<Set<TrackId>>()

    override fun observeHistory(): Flow<List<PlayHistory>> = history

    override suspend fun recordPlayback(trackId: TrackId, playedAtMs: Long) = Unit

    override suspend fun deleteHistory(trackIds: Set<TrackId>) {
        deleteCalls += trackIds
        history.value = history.value.filterNot { it.trackId in trackIds }
    }

    override suspend fun clearHistory() {
        clearCalls++
        history.value = emptyList()
    }
}
