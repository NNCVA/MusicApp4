package com.musicapp.player.feature.player

import com.musicapp.player.R
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackConnectionState
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.core.playback.PlaybackFailure
import com.musicapp.player.core.playback.PlaybackFailureCode
import com.musicapp.player.core.playback.PlaybackStatus
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `controller and library state form player and queue UI`() = runTest(dispatcher) {
        val tracks = listOf(track(1), track(2))
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = tracks[1].id,
                playbackStatus = PlaybackStatus.BUFFERING,
                isBuffering = true,
                positionMs = 2_000,
                durationMs = 10_000,
                playbackMode = PlaybackMode.SHUFFLE,
                queue = PlaybackQueue(items(1, 2), ids(2, 1), id(2), 1, 0),
            ),
        )
        val viewModel = subject(controller, tracks)
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(PlayerLoadState.BUFFERING, viewModel.uiState.value.loadState)
        assertEquals("Track 2", viewModel.uiState.value.currentTrack?.title)
        assertEquals(listOf(id(2), id(1)), viewModel.uiState.value.queue.map { it.queueItemId })
        assertTrue(viewModel.uiState.value.queue.first().isCurrent)
        collection.cancel()
    }

    @Test
    fun `stable playback status drives immediate preparing buffering error and clear state`() = runTest(dispatcher) {
        val current = track(1)
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = current.id,
                playbackStatus = PlaybackStatus.PREPARING,
            ),
        )
        val viewModel = subject(controller, listOf(current))
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(PlayerLoadState.PREPARING, viewModel.uiState.value.loadState)

        controller.update {
            copy(playbackStatus = PlaybackStatus.BUFFERING, isBuffering = true)
        }
        advanceUntilIdle()
        assertEquals(PlayerLoadState.BUFFERING, viewModel.uiState.value.loadState)

        controller.update {
            copy(
                playbackStatus = PlaybackStatus.ERROR,
                playbackFailure = PlaybackFailure(PlaybackFailureCode.ACCESS_DENIED),
                isBuffering = false,
            )
        }
        advanceUntilIdle()
        assertEquals(PlayerLoadState.ERROR, viewModel.uiState.value.loadState)
        assertEquals(R.string.player_error_access_denied, viewModel.uiState.value.errorMessageRes)

        controller.update {
            copy(playbackStatus = PlaybackStatus.READY, playbackFailure = null)
        }
        advanceUntilIdle()
        assertEquals(PlayerLoadState.READY, viewModel.uiState.value.loadState)
        assertEquals(null, viewModel.uiState.value.errorMessageRes)
        collection.cancel()
    }

    @Test
    fun `every stable playback failure has a dedicated resource message`() {
        val expected = mapOf(
            PlaybackFailureCode.SOURCE_NOT_FOUND to R.string.player_error_source_not_found,
            PlaybackFailureCode.ACCESS_DENIED to R.string.player_error_access_denied,
            PlaybackFailureCode.UNSUPPORTED_FORMAT to R.string.player_error_unsupported_format,
            PlaybackFailureCode.DECODING_FAILED to R.string.player_error_decoding_failed,
            PlaybackFailureCode.AUDIO_OUTPUT_FAILED to R.string.player_error_audio_output_failed,
            PlaybackFailureCode.IO_ERROR to R.string.player_error_io,
            PlaybackFailureCode.UNKNOWN to R.string.player_error_unknown,
        )

        assertEquals(PlaybackFailureCode.entries.toSet(), expected.keys)
        expected.forEach { (failure, resource) -> assertEquals(resource, failure.messageRes()) }
    }

    @Test
    fun `player actions delegate seek mode transport and queue removal`() = runTest(dispatcher) {
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = track(1).id,
                durationMs = 8_000,
                playbackMode = PlaybackMode.LIST_REPEAT,
                queue = PlaybackQueue(items(1), currentItemId = id(1)),
            ),
        )
        val viewModel = subject(controller, listOf(track(1)))
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.togglePlayback()
        viewModel.seekToFraction(0.25f)
        assertEquals(2_000L, controller.seekPosition)
        viewModel.seekToPosition(3_500L)
        viewModel.cyclePlaybackMode()
        viewModel.skipPrevious()
        viewModel.skipNext()
        viewModel.jumpToQueueItem(id(1))
        viewModel.removeFromQueue(id(1))

        assertEquals(1, controller.playCalls)
        assertEquals(3_500L, controller.seekPosition)
        assertEquals(PlaybackMode.SINGLE_REPEAT, controller.mode)
        assertEquals(id(1), controller.removed)
        assertEquals(id(1), controller.jumped)
        assertEquals(1, controller.previousCalls)
        assertEquals(1, controller.nextCalls)
        collection.cancel()
    }

    @Test
    fun `rewind and fast forward use ten seconds and clamp to track bounds`() = runTest(dispatcher) {
        val current = track(1)
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = current.id,
                positionMs = 5_000,
                durationMs = 20_000,
            ),
        )
        val viewModel = subject(controller, listOf(current))
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.rewind()
        assertEquals(0L, controller.seekPosition)

        controller.update { copy(positionMs = 15_000) }
        advanceUntilIdle()
        viewModel.fastForward()
        assertEquals(20_000L, controller.seekPosition)

        controller.update { copy(positionMs = 12_000) }
        advanceUntilIdle()
        viewModel.rewind()
        assertEquals(2_000L, controller.seekPosition)
        collection.cancel()
    }

    @Test
    fun `relative seek is ignored until duration is known`() = runTest(dispatcher) {
        val current = track(1)
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = current.id,
                positionMs = 5_000,
                durationMs = null,
            ),
        )
        val viewModel = subject(controller, emptyList())
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.rewind()
        viewModel.fastForward()

        assertEquals(null, controller.seekPosition)
        collection.cancel()
    }

    @Test
    fun `track information loads metadata on demand`() = runTest(dispatcher) {
        val current = track(1)
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = current.id,
                queue = PlaybackQueue(items(1), currentItemId = id(1)),
            ),
        )
        val viewModel = subject(controller, listOf(current))
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.showTrackInfo()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTrackInfo)
        assertEquals("audio/flac", viewModel.uiState.value.metadata?.encoding)
        viewModel.dismissTrackInfo()
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.showTrackInfo)
        collection.cancel()
    }

    private fun subject(controller: RecordingController, tracks: List<Track>) = PlayerViewModel(
        playbackController = controller,
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        artworkRepository = object : ArtworkRepository {
            override suspend fun artwork(track: Track, targetPx: Int) = ArtworkResult.Placeholder
        },
        metadataRepository = object : TrackMetadataRepository {
            override suspend fun read(track: Track) =
                AdvancedTrackMetadata("audio/flac", 1_000, 48_000, track.sizeBytes, true)
        },
    )

    private fun track(value: Long) = Track(
        id = TrackId("external", value), title = "Track $value", artistName = "Artist",
        durationMs = 10_000, dateAddedMs = value, dateModifiedMs = value,
        relativePath = "Music/", displayName = "track$value.flac", sizeBytes = 1_024,
    )
    private fun items(vararg values: Long) = values.map { QueueItem(id(it), TrackId("external", it)) }
    private fun ids(vararg values: Long) = values.map(::id)
    private fun id(value: Long) = QueueItemId(value)
}

private class RecordingController(initial: PlaybackControllerState) : PlaybackControllerFacade {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<PlaybackControllerState> = mutableState
    var playCalls = 0
    var previousCalls = 0
    var nextCalls = 0
    var seekPosition: Long? = null
    var mode: PlaybackMode? = null
    var removed: QueueItemId? = null
    var jumped: QueueItemId? = null
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: com.musicapp.player.core.domain.model.PlaybackContext) = Unit
    override fun play() { playCalls++ }
    override fun pause() = Unit
    override fun skipToPrevious() { previousCalls++ }
    override fun skipToNext() { nextCalls++ }
    override fun seekTo(positionMs: Long) { seekPosition = positionMs }
    override fun setPlaybackMode(mode: PlaybackMode) { this.mode = mode }
    override fun jumpToQueueItem(queueItemId: QueueItemId) { jumped = queueItemId }
    override fun removeFromQueue(queueItemId: QueueItemId) { removed = queueItemId }
    fun update(transform: PlaybackControllerState.() -> PlaybackControllerState) {
        mutableState.value = mutableState.value.transform()
    }
}
