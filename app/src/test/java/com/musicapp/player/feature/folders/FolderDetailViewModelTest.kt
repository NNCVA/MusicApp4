package com.musicapp.player.feature.folders

import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FolderDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `track sort selection updates detail UI for every visible field`() = runTest(dispatcher) {
        val tracks = listOf(
            track(1, title = "Bravo", artist = "Alpha", dateAddedMs = 100, durationMs = 1_000),
            track(2, title = "Alpha", artist = "Charlie", dateAddedMs = 300, durationMs = 3_000),
            track(3, title = "Charlie", artist = "Bravo", dateAddedMs = 200, durationMs = 2_000),
        )
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
            playbackController = NoOpPlaybackController(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        assertSort(viewModel, CategoryTrackSortField.TITLE, CategorySortDirection.ASCENDING, 2, 1, 3)

        viewModel.selectTrackSort(CategoryTrackSortField.ARTIST)
        advanceUntilIdle()
        assertSort(viewModel, CategoryTrackSortField.ARTIST, CategorySortDirection.ASCENDING, 1, 3, 2)

        viewModel.selectTrackSort(CategoryTrackSortField.DATE_ADDED)
        advanceUntilIdle()
        assertSort(viewModel, CategoryTrackSortField.DATE_ADDED, CategorySortDirection.DESCENDING, 2, 3, 1)

        viewModel.selectTrackSort(CategoryTrackSortField.DURATION)
        advanceUntilIdle()
        assertSort(viewModel, CategoryTrackSortField.DURATION, CategorySortDirection.ASCENDING, 1, 3, 2)
        collection.cancel()
    }

    @Test
    fun `volume root without direct tracks is browser only and uses friendly title`() = runTest(dispatcher) {
        val viewModel =
            FolderDetailViewModel(
                mediaLibraryRepository = FakeMediaLibraryRepository(
                    listOf(track(1, title = "Nested", artist = "Artist", dateAddedMs = 1, durationMs = 1_000).copy(relativePath = "Music/Live")),
                ),
                playbackController = NoOpPlaybackController(),
                volumeMetadataSource = FolderVolumeMetadataSource {
                    flowOf(
                        listOf(
                            FolderVolumeMetadata(
                                volumeName = "external",
                                displayName = "Internal storage",
                                rootPath = "/storage/emulated/0",
                                isPrimary = true,
                                usedBytes = 10,
                                totalBytes = 100,
                            ),
                        ),
                    )
                },
            )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", ""))
        advanceUntilIdle()

        assertEquals("Internal storage", viewModel.uiState.value.displayName)
        assertTrue(viewModel.uiState.value.isBrowserOnly)
        assertTrue(viewModel.uiState.value.isVolumeRoot)
        assertFalse(viewModel.uiState.value.isMusicFolder)
        assertEquals(listOf("Music"), viewModel.uiState.value.childFolders.map(FolderNode::displayName))
        collection.cancel()
    }

    @Test
    fun `volume root with direct track remains a playable music detail`() = runTest(dispatcher) {
        val viewModel =
            FolderDetailViewModel(
                mediaLibraryRepository = FakeMediaLibraryRepository(
                    listOf(
                        track(1, title = "Root", artist = "Artist", dateAddedMs = 1, durationMs = 1_000).copy(relativePath = ""),
                        track(2, title = "Nested", artist = "Artist", dateAddedMs = 2, durationMs = 1_000).copy(relativePath = "Music"),
                    ),
                ),
                playbackController = NoOpPlaybackController(),
            )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", ""))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isBrowserOnly)
        assertTrue(viewModel.uiState.value.isVolumeRoot)
        assertTrue(viewModel.uiState.value.isMusicFolder)
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.recursiveTracks.map { it.id.mediaStoreId }.toSet())
        collection.cancel()
    }

    @Test
    fun `metadata source failure preserves volume navigation fallback`() = runTest(dispatcher) {
        val viewModel =
            FolderDetailViewModel(
                mediaLibraryRepository = FakeMediaLibraryRepository(
                    listOf(track(1, title = "Nested", artist = "Artist", dateAddedMs = 1, durationMs = 1_000).copy(relativePath = "Music")),
                ),
                playbackController = NoOpPlaybackController(),
                volumeMetadataSource = FolderVolumeMetadataSource {
                    flow { error("metadata unavailable") }
                },
            )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", ""))
        advanceUntilIdle()

        assertEquals("external", viewModel.uiState.value.displayName)
        assertTrue(viewModel.uiState.value.isBrowserOnly)
        assertTrue(viewModel.uiState.value.volumeIsPrimary)
        collection.cancel()
    }

    @Test
    fun `subfolders are always sorted by name in ascending order`() = runTest(dispatcher) {
        val tracks = listOf(
            track(1, title = "Z", artist = "A", dateAddedMs = 1, durationMs = 1_000).copy(relativePath = "Root/Zeta"),
            track(2, title = "A", artist = "A", dateAddedMs = 2, durationMs = 1_000).copy(relativePath = "Root/Alpha"),
            track(3, title = "M", artist = "A", dateAddedMs = 3, durationMs = 1_000).copy(relativePath = "Root/Beta"),
        )
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
            playbackController = NoOpPlaybackController(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Root"))
        advanceUntilIdle()

        assertEquals(listOf("Alpha", "Beta", "Zeta"), viewModel.uiState.value.childFolders.map { it.displayName })
        collection.cancel()
    }

    @Test
    fun `single track actions delegate to playback controller`() = runTest(dispatcher) {
        val targetTrack = track(10, title = "Single", artist = "Artist", dateAddedMs = 1, durationMs = 1_000)
        val controller = RecordingDetailPlaybackController()
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(targetTrack)),
            playbackController = controller,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        viewModel.playTrackNext(targetTrack.id)
        assertEquals(listOf(targetTrack.id), controller.nextTracks)

        viewModel.addTrackToQueue(targetTrack.id)
        assertEquals(listOf(targetTrack.id), controller.enqueuedTracks)
        collection.cancel()
    }

    @Test
    fun `showTrackInfo and dismissTrackInfo update metadata state`() = runTest(dispatcher) {
        val targetTrack = track(10, title = "Single", artist = "Artist", dateAddedMs = 1, durationMs = 1_000)
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(targetTrack)),
            playbackController = NoOpPlaybackController(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        viewModel.showTrackInfo(targetTrack)
        advanceUntilIdle()

        assertEquals(targetTrack, viewModel.uiState.value.infoTrack)
        assertFalse(viewModel.uiState.value.isInfoLoading)
        assertEquals("FLAC", viewModel.uiState.value.infoMetadata?.encoding)

        viewModel.dismissTrackInfo()
        advanceUntilIdle()

        org.junit.Assert.assertNull(viewModel.uiState.value.infoTrack)
        org.junit.Assert.assertNull(viewModel.uiState.value.infoMetadata)
        collection.cancel()
    }

    @Test
    fun `startSelection activates selection mode and selects track`() = runTest(dispatcher) {
        val targetTrack = track(10, title = "Single", artist = "Artist", dateAddedMs = 1, durationMs = 1_000)
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(targetTrack)),
            playbackController = NoOpPlaybackController(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        viewModel.startSelection(targetTrack.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(targetTrack.id), viewModel.uiState.value.selectedTrackIds)
        collection.cancel()
    }

    @Test
    fun `toggleSelection toggles selection and exits when empty`() = runTest(dispatcher) {
        val t1 = track(1, title = "Track 1", artist = "Artist", dateAddedMs = 1, durationMs = 1_000)
        val t2 = track(2, title = "Track 2", artist = "Artist", dateAddedMs = 2, durationMs = 1_000)
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(t1, t2)),
            playbackController = NoOpPlaybackController(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        viewModel.startSelection(t1.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)

        // Add t2
        viewModel.toggleSelection(t2.id)
        advanceUntilIdle()
        assertEquals(setOf(t1.id, t2.id), viewModel.uiState.value.selectedTrackIds)
        assertTrue(viewModel.uiState.value.isSelectionMode)

        // Remove t1
        viewModel.toggleSelection(t1.id)
        advanceUntilIdle()
        assertEquals(setOf(t2.id), viewModel.uiState.value.selectedTrackIds)
        assertTrue(viewModel.uiState.value.isSelectionMode)

        // Remove t2 -> empty -> exits selection mode
        viewModel.toggleSelection(t2.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
        assertFalse(viewModel.uiState.value.isSelectionMode)
        collection.cancel()
    }

    @Test
    fun `selectAll and toggleSelectAll work on direct tracks only`() = runTest(dispatcher) {
        val t1 = track(1, title = "Direct 1", artist = "Artist", dateAddedMs = 1, durationMs = 1_000)
        val t2 = track(2, title = "Direct 2", artist = "Artist", dateAddedMs = 2, durationMs = 1_000)
        val t3 = track(3, title = "Subtrack", artist = "Artist", dateAddedMs = 3, durationMs = 1_000).copy(relativePath = "Music/Sub")
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(t1, t2, t3)),
            playbackController = NoOpPlaybackController(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        viewModel.selectAll()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(t1.id, t2.id), viewModel.uiState.value.selectedTrackIds)

        // toggleSelectAll when all are selected -> clear & exit
        viewModel.toggleSelectAll()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())

        // toggleSelectAll when none selected -> select all & enter
        viewModel.toggleSelectAll()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(t1.id, t2.id), viewModel.uiState.value.selectedTrackIds)
        collection.cancel()
    }

    @Test
    fun `clearSelection and exitSelection clear selection and exit mode`() = runTest(dispatcher) {
        val t1 = track(1, title = "Track 1", artist = "Artist", dateAddedMs = 1, durationMs = 1_000)
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(t1)),
            playbackController = NoOpPlaybackController(),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        viewModel.startSelection(t1.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.clearSelection()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())

        viewModel.startSelection(t1.id)
        advanceUntilIdle()
        viewModel.exitSelection()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
        collection.cancel()
    }

    @Test
    fun `addSelectedToQueue and addSelectedToPlaylist execute batch actions and exit selection`() = runTest(dispatcher) {
        val t1 = track(1, title = "Track 1", artist = "Artist", dateAddedMs = 1, durationMs = 1_000)
        val t2 = track(2, title = "Track 2", artist = "Artist", dateAddedMs = 2, durationMs = 1_000)
        val batchExecutor = RecordingBatchTrackActionExecutor()
        val viewModel = FolderDetailViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(t1, t2)),
            playbackController = NoOpPlaybackController(),
            batchActionExecutor = batchExecutor,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        viewModel.startSelection(t1.id)
        viewModel.toggleSelection(t2.id)
        advanceUntilIdle()

        viewModel.addSelectedToQueue()
        advanceUntilIdle()

        assertEquals(BatchTrackAction.AddToQueue, batchExecutor.lastAction)
        assertEquals(listOf(t1.id, t2.id), batchExecutor.lastTrackIds)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())
        assertTrue(viewModel.uiState.value.batchResult is BatchTrackActionResult.Completed)

        viewModel.acknowledgeBatchResult()
        advanceUntilIdle()
        org.junit.Assert.assertNull(viewModel.uiState.value.batchResult)

        // Now test add to playlist
        viewModel.startSelection(t1.id)
        advanceUntilIdle()
        val playlistId = PlaylistId(1L)
        viewModel.addSelectedToPlaylist(playlistId)
        advanceUntilIdle()

        assertEquals(BatchTrackAction.AddToPlaylist(playlistId), batchExecutor.lastAction)
        assertEquals(listOf(t1.id), batchExecutor.lastTrackIds)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        collection.cancel()
    }

    @Test
    fun `initial detail state is not loaded and becomes loaded after flow emits`() = runTest(dispatcher) {
        val viewModel =
            FolderDetailViewModel(
                mediaLibraryRepository = FakeMediaLibraryRepository(emptyList()),
                playbackController = NoOpPlaybackController(),
            )
        assertFalse(viewModel.uiState.value.isLoaded)

        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(FolderId("external", "Music"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoaded)
        assertTrue(viewModel.uiState.value.directTracks.isEmpty())
        assertTrue(viewModel.uiState.value.childFolders.isEmpty())
        collection.cancel()
    }

    private fun assertSort(
        viewModel: FolderDetailViewModel,
        field: CategoryTrackSortField,
        direction: CategorySortDirection,
        vararg expectedIds: Long,
    ) {
        assertEquals(field, viewModel.uiState.value.trackSort.field)
        assertEquals(direction, viewModel.uiState.value.trackSort.direction)
        assertEquals(expectedIds.toList(), viewModel.uiState.value.directTracks.map { it.id.mediaStoreId })
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        dateAddedMs: Long,
        durationMs: Long,
    ) = Track(
        id = TrackId("external", id),
        title = title,
        artistName = artist,
        durationMs = durationMs,
        dateAddedMs = dateAddedMs,
        dateModifiedMs = id,
        relativePath = "Music",
        displayName = "$id.mp3",
    )
}

private class RecordingBatchTrackActionExecutor : BatchTrackActionExecutor {
    var lastAction: BatchTrackAction? = null
    var lastTrackIds: List<TrackId>? = null

    override suspend fun execute(
        action: BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): BatchTrackActionResult {
        lastAction = action
        lastTrackIds = orderedTrackIds
        return BatchTrackActionResult.Completed(
            action = action,
            selectedCount = orderedTrackIds.size,
            affectedCount = orderedTrackIds.size,
            skippedCount = 0,
        )
    }
}

private class NoOpPlaybackController : PlaybackControllerFacade {
    override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: PlaybackContext) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

private class RecordingDetailPlaybackController : PlaybackControllerFacade {
    override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())
    var enqueuedTracks: List<TrackId>? = null
    var nextTracks: List<TrackId>? = null

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: PlaybackContext) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun addToQueue(trackIds: List<TrackId>) {
        enqueuedTracks = trackIds
    }
    override fun playNext(trackIds: List<TrackId>) {
        nextTracks = trackIds
    }
}
