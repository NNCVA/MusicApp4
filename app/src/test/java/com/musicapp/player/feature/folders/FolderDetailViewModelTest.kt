package com.musicapp.player.feature.folders

import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackSortField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
