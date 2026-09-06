package com.musicapp.player.feature.folders

import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state exposes music volumes and recursive direct-folder shortcuts with safe metadata fallback`() = runTest(dispatcher) {
        val tracks =
            listOf(
                track("external", 1, "Music/Live/Set", "Zulu"),
                track("external", 2, "Music/Live", "Alpha"),
                track("sdcard", 3, "Music/Live", "Card"),
            )
        val viewModel = createViewModel(
            tracks = tracks,
            volumeMetadataSource = FolderVolumeMetadataSource {
                flowOf(
                    listOf(
                        FolderVolumeMetadata(
                            volumeName = "external",
                            displayName = "Internal storage",
                            rootPath = "/storage/emulated/0",
                            isPrimary = true,
                            usedBytes = 80,
                            totalBytes = 100,
                        ),
                    ),
                )
            },
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("external", "sdcard"), state.volumes.map { it.id.volumeName })
        assertEquals("Internal storage", state.volumes.first().displayName)
        assertEquals("/storage/emulated/0", state.volumes.first().rootPath)
        assertEquals(80L, state.volumes.first().usedBytes)
        assertEquals(100L, state.volumes.first().totalBytes)
        assertNull(state.volumes[1].displayName)
        assertNull(state.volumes[1].rootPath)
        assertEquals(
            listOf("Live", "Live", "Set"),
            state.musicFolders.map(FolderNode::displayName),
        )
        assertTrue(state.musicFolders.none { it.id.relativePath.isEmpty() })
        assertEquals(setOf(1L, 2L), state.musicFolders.first { it.id.volumeName == "external" && it.id.relativePath == "Music/Live" }.recursiveTracks.map { it.id.mediaStoreId }.toSet())
        collection.cancel()
    }

    @Test
    fun `playFolder uses recursive available tracks and stable folder source id`() = runTest(dispatcher) {
        val controller = RecordingPlaybackController()
        val viewModel = createViewModel(
            tracks = listOf(
                track("external", 1, "Music", "A"),
                track("external", 2, "Music/Live", "B"),
                track("external", 3, "Music/Live", "Unavailable", Availability.TEMPORARILY_UNAVAILABLE),
            ),
            playbackController = controller,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.playFolder(FolderId("external", "Music"))

        assertEquals("external|Music", controller.context?.sourceId)
        assertEquals(listOf(1L, 2L), controller.context?.orderedTrackIds?.map { it.mediaStoreId })
        collection.cancel()
    }

    @Test
    fun `primary media volume stays primary when platform metadata is unavailable`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            tracks = listOf(
                track("external_primary", 1, "Music", "Primary"),
                track("1234-5678", 2, "Music", "Secondary"),
            ),
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals("external_primary", viewModel.uiState.value.volumes.first().id.volumeName)
        assertTrue(viewModel.uiState.value.volumes.first().isPrimary)
        assertFalse(viewModel.uiState.value.volumes.last().isPrimary)
        collection.cancel()
    }

    @Test
    fun `metadata source failure keeps folder state available`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            tracks = listOf(track("external", 1, "Music", "Primary")),
            volumeMetadataSource = FolderVolumeMetadataSource {
                flow { error("metadata unavailable") }
            },
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("external"), viewModel.uiState.value.volumes.map { it.id.volumeName })
        assertTrue(viewModel.uiState.value.volumes.single().isPrimary)
        assertNull(viewModel.uiState.value.volumes.single().rootPath)
        collection.cancel()
    }

    @Test
    fun `initial state is not loaded and becomes loaded after flow emits`() = runTest(dispatcher) {
        val viewModel = createViewModel(tracks = emptyList())
        assertFalse(viewModel.uiState.value.isLoaded)

        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoaded)
        assertTrue(viewModel.uiState.value.volumes.isEmpty())
        assertTrue(viewModel.uiState.value.musicFolders.isEmpty())
        collection.cancel()
    }

    @Test
    fun `folderSort from sortPreferencesRepository orders musicFolders`() = runTest(dispatcher) {
        val tracks = listOf(
            track("external", 1, "Music/A", "A1"),
            track("external", 2, "Music/B", "B1"),
            track("external", 3, "Music/B", "B2"),
        )
        val sortRepo = com.musicapp.player.fakes.FakeSortPreferencesRepository(
            initialFolderSort = FolderSort(FolderSortField.TRACK_COUNT, com.musicapp.player.feature.category.CategorySortDirection.DESCENDING),
        )
        val viewModel = createViewModel(
            tracks = tracks,
            sortPreferencesRepository = sortRepo,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("B", "A"), viewModel.uiState.value.musicFolders.map(FolderNode::displayName))
        collection.cancel()
    }

    private fun createViewModel(
        tracks: List<Track>,
        volumeMetadataSource: FolderVolumeMetadataSource = FolderVolumeMetadataSource { flowOf(emptyList()) },
        playbackController: PlaybackControllerFacade = RecordingPlaybackController(),
        sortPreferencesRepository: com.musicapp.player.data.sort.SortPreferencesRepository = com.musicapp.player.fakes.FakeSortPreferencesRepository(),
    ) = FoldersViewModel(
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        volumeMetadataSource = volumeMetadataSource,
        playbackController = playbackController,
        sortPreferencesRepository = sortPreferencesRepository,
    )

    private fun track(
        volume: String,
        id: Long,
        path: String,
        title: String,
        availability: Availability = Availability.AVAILABLE,
    ) = Track(
        id = TrackId(volume, id),
        title = title,
        artistName = "Artist",
        durationMs = 1_000,
        dateAddedMs = id,
        dateModifiedMs = id,
        relativePath = path,
        displayName = "$id.mp3",
        availability = availability,
    )
}

private class RecordingPlaybackController : PlaybackControllerFacade {
    override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())
    var context: PlaybackContext? = null
        private set

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
