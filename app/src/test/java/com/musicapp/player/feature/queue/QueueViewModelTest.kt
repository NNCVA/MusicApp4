package com.musicapp.player.feature.queue

import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackConnectionState
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
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
    fun `queue follows controller playback order and marks current item`() = runTest(dispatcher) {
        val firstTrack = track(1, "First")
        val secondTrack = track(2, "Second")
        val firstItem = QueueItem(QueueItemId(10), firstTrack.id)
        val secondItem = QueueItem(QueueItemId(11), secondTrack.id)
        val controller = RecordingPlaybackController(
            playbackState(
                original = listOf(firstItem, secondItem),
                playbackOrder = listOf(secondItem.id, firstItem.id),
                current = secondItem.id,
            ),
        )
        val viewModel = QueueViewModel(
            FakeMediaLibraryRepository(listOf(firstTrack, secondTrack)),
            controller,
        )
        collectState(viewModel)

        assertEquals(listOf(secondItem.id, firstItem.id), viewModel.uiState.value.items.map { it.queueItemId })
        assertEquals(secondItem.id, viewModel.uiState.value.currentQueueItemId)
        assertEquals(secondItem.id, viewModel.uiState.value.currentItem?.queueItemId)
        assertTrue(viewModel.uiState.value.items.first().isCurrent)
        assertFalse(viewModel.uiState.value.items.last().isCurrent)
        assertSame(secondTrack, viewModel.uiState.value.items.first().track)
    }

    @Test
    fun `missing metadata keeps queue identity visible`() = runTest(dispatcher) {
        val missingTrackId = TrackId("external", 99)
        val missingItem = QueueItem(QueueItemId(20), missingTrackId)
        val controller = RecordingPlaybackController(
            playbackState(original = listOf(missingItem), current = missingItem.id),
        )
        val viewModel = QueueViewModel(FakeMediaLibraryRepository(), controller)
        collectState(viewModel)

        val item = viewModel.uiState.value.items.single()
        assertEquals(missingItem.id, item.queueItemId)
        assertEquals(missingTrackId, item.trackId)
        assertNull(item.track)
        assertFalse(item.hasMetadata)
        assertTrue(item.isCurrent)
    }

    @Test
    fun `controller updates replace current queue projection`() = runTest(dispatcher) {
        val firstTrack = track(1, "First")
        val secondTrack = track(2, "Second")
        val firstItem = QueueItem(QueueItemId(30), firstTrack.id)
        val secondItem = QueueItem(QueueItemId(31), secondTrack.id)
        val controller = RecordingPlaybackController(
            playbackState(original = listOf(firstItem), current = firstItem.id),
        )
        val viewModel = QueueViewModel(
            FakeMediaLibraryRepository(listOf(firstTrack, secondTrack)),
            controller,
        )
        collectState(viewModel)

        controller.mutableState.value = playbackState(
            original = listOf(firstItem, secondItem),
            current = secondItem.id,
        )
        testScheduler.runCurrent()

        assertEquals(listOf(firstItem.id, secondItem.id), viewModel.uiState.value.items.map { it.queueItemId })
        assertEquals(secondItem.id, viewModel.uiState.value.currentItem?.queueItemId)
        assertEquals(PlaybackConnectionState.CONNECTED, viewModel.uiState.value.connectionState)
    }

    @Test
    fun `click and remove submit queue item identity while stale identity is ignored`() = runTest(dispatcher) {
        val currentTrack = track(1, "Current")
        val currentItem = QueueItem(QueueItemId(40), currentTrack.id)
        val staleItemId = QueueItemId(41)
        val controller = RecordingPlaybackController(
            playbackState(original = listOf(currentItem), current = currentItem.id),
        )
        val viewModel = QueueViewModel(FakeMediaLibraryRepository(listOf(currentTrack)), controller)
        collectState(viewModel)

        viewModel.jumpToQueueItem(currentItem.id)
        viewModel.removeFromQueue(currentItem.id)
        viewModel.jumpToQueueItem(staleItemId)
        viewModel.removeFromQueue(staleItemId)

        assertEquals(listOf(currentItem.id), controller.jumpedItems)
        assertEquals(listOf(currentItem.id), controller.removedItems)
    }

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: QueueViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.runCurrent()
    }

    private fun playbackState(
        original: List<QueueItem>,
        playbackOrder: List<QueueItemId> = emptyList(),
        current: QueueItemId?,
    ): PlaybackControllerState =
        PlaybackControllerState(
            connectionState = PlaybackConnectionState.CONNECTED,
            currentTrackId = original.firstOrNull { it.id == current }?.trackId,
            queue = PlaybackQueue(
                originalQueue = original,
                stableShuffleSequence = playbackOrder,
                currentItemId = current,
                shuffleCursor = playbackOrder.indexOf(current).takeIf { it >= 0 },
            ),
        )

    private fun track(id: Long, title: String): Track =
        Track(
            id = TrackId("external", id),
            title = title,
            artistName = "Artist",
            durationMs = 60_000,
            dateAddedMs = id,
            dateModifiedMs = id,
            relativePath = "Music/",
            displayName = "$title.mp3",
        )

    private class RecordingPlaybackController(initialState: PlaybackControllerState) : PlaybackControllerFacade {
        val mutableState = MutableStateFlow(initialState)
        val jumpedItems = mutableListOf<QueueItemId>()
        val removedItems = mutableListOf<QueueItemId>()

        override val state: StateFlow<PlaybackControllerState> = mutableState

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun play(context: PlaybackContext) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun skipToPrevious() = Unit
        override fun skipToNext() = Unit
        override fun seekTo(positionMs: Long) = Unit

        override fun jumpToQueueItem(queueItemId: QueueItemId) {
            jumpedItems += queueItemId
        }

        override fun removeFromQueue(queueItemId: QueueItemId) {
            removedItems += queueItemId
        }
    }
}
