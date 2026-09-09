package com.musicapp.player.feature.player

import com.musicapp.player.R
import com.musicapp.player.core.common.time.Clock
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
import com.musicapp.player.core.playback.timer.SleepTimerStatus
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakeSettingsRepository
import com.musicapp.player.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertSame
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
        assertEquals(tracks[1].id, viewModel.uiState.value.artworkTrackId)
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
        val clock = MutableClock()
        val viewModel = subject(controller, listOf(track(1)), clock)
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.togglePlayback()
        viewModel.seekToFraction(0.25f)
        assertEquals(2_000L, controller.seekPosition)
        viewModel.seekToPosition(3_500L)
        viewModel.cyclePlaybackMode()
        viewModel.skipPrevious()
        clock.currentTime += PlayerViewModel.SKIP_DEBOUNCE_WINDOW_MS
        viewModel.skipNext()
        viewModel.jumpToQueueItem(id(1))
        viewModel.removeFromQueue(id(1))
        viewModel.clearQueue()

        assertEquals(1, controller.playCalls)
        assertEquals(3_500L, controller.seekPosition)
        assertEquals(PlaybackMode.SINGLE_REPEAT, controller.mode)
        assertEquals(id(1), controller.removed)
        assertEquals(id(1), controller.jumped)
        assertEquals(1, controller.clearQueueCalls)
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

    @Test
    fun `expandPlayer emits to expandRequests flow`() = runTest(dispatcher) {
        val viewModel = subject(RecordingController(PlaybackControllerState()), emptyList())
        var expandEmitted = false
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.expandRequests.collect {
                expandEmitted = true
            }
        }
        viewModel.expandPlayer()

        assertTrue(expandEmitted)
        collection.cancel()
    }

    @Test
    fun `togglePlayback suppresses repeated clicks within 300ms window`() = runTest(dispatcher) {
        val clock = MutableClock(10_000L)
        val controller = RecordingController(PlaybackControllerState(isPlaying = false))
        val viewModel = subject(controller, emptyList(), clock)

        // First click executes immediately
        viewModel.togglePlayback()
        assertEquals(1, controller.playCalls)

        // Second click within 300ms is throttled
        clock.currentTime = 10_150L
        viewModel.togglePlayback()
        assertEquals(1, controller.playCalls)

        // Third click at 299ms is still throttled
        clock.currentTime = 10_299L
        viewModel.togglePlayback()
        assertEquals(1, controller.playCalls)

        // Fourth click at exactly 300ms window expiry executes
        clock.currentTime = 10_300L
        viewModel.togglePlayback()
        assertEquals(2, controller.playCalls)
    }

    @Test
    fun `skipNext suppresses rapid clicks within a shared 500ms window without extending it`() = runTest(dispatcher) {
        val clock = MutableClock(10_000L)
        val controller = RecordingController(PlaybackControllerState())
        val viewModel = subject(controller, emptyList(), clock)

        // First click executes immediately
        viewModel.skipNext()
        assertEquals(1, controller.nextCalls)

        // Continuous click at +200ms (<500ms) is ignored and does not extend the accepted window.
        clock.currentTime = 10_200L
        viewModel.skipNext()
        assertEquals(1, controller.nextCalls)

        // The accepted window expires exactly 500ms after the first click.
        clock.currentTime = 10_500L
        viewModel.skipNext()
        assertEquals(2, controller.nextCalls)

        // A click 200ms after the second accepted click is ignored.
        clock.currentTime = 10_700L
        viewModel.skipNext()
        assertEquals(2, controller.nextCalls)

        // The next click is accepted after another complete 500ms window.
        clock.currentTime = 11_000L
        viewModel.skipNext()
        assertEquals(3, controller.nextCalls)
    }

    @Test
    fun `skipPrevious suppresses rapid clicks within a shared 500ms window without extending it`() = runTest(dispatcher) {
        val clock = MutableClock(10_000L)
        val controller = RecordingController(PlaybackControllerState())
        val viewModel = subject(controller, emptyList(), clock)

        // First click executes immediately
        viewModel.skipPrevious()
        assertEquals(1, controller.previousCalls)

        // Continuous clicks at intervals < 500ms are ignored without extending the window.
        clock.currentTime = 10_300L
        viewModel.skipPrevious()
        assertEquals(1, controller.previousCalls)

        clock.currentTime = 10_499L
        viewModel.skipPrevious()
        assertEquals(1, controller.previousCalls)

        // Exactly 500ms after the accepted click, the action is available again.
        clock.currentTime = 10_500L
        viewModel.skipPrevious()
        assertEquals(2, controller.previousCalls)

        clock.currentTime = 10_900L
        viewModel.skipPrevious()
        assertEquals(2, controller.previousCalls)
    }

    @Test
    fun `skip directions share a debounce window while playback toggle remains independent`() = runTest(dispatcher) {
        val clock = MutableClock(10_000L)
        val controller = RecordingController(PlaybackControllerState(isPlaying = false))
        val viewModel = subject(controller, emptyList(), clock)

        viewModel.togglePlayback()
        assertEquals(1, controller.playCalls)

        // Immediately (50ms later) invoke skipNext, must not be blocked by togglePlayback
        clock.currentTime = 10_050L
        viewModel.skipNext()
        assertEquals(1, controller.nextCalls)

        // A different skip direction in the same 500ms window is also suppressed.
        clock.currentTime = 10_100L
        viewModel.skipPrevious()
        assertEquals(0, controller.previousCalls)

        // The first direction remains suppressed in the same window.
        clock.currentTime = 10_400L
        viewModel.skipNext()
        assertEquals(1, controller.nextCalls)

        // Once the shared window expires, the other direction is accepted.
        clock.currentTime = 10_550L
        viewModel.skipPrevious()
        assertEquals(1, controller.previousCalls)
    }

    @Test
    fun `progress reuses library index and queue rows and does not emit shell changes`() = runTest(dispatcher) {
        val library = List(1_000) { track(it.toLong() + 1) }
        var reads = 0
        val counted = object : AbstractList<Track>() {
            override val size get() = library.size
            override fun get(index: Int): Track { reads++; return library[index] }
        }
        val source = MutableStateFlow<List<Track>>(counted)
        val repository = object : MediaLibraryRepository by FakeMediaLibraryRepository() {
            override fun observeTracks(includeHidden: Boolean) = source
        }
        val controller = RecordingController(PlaybackControllerState(
            currentTrackId = library.first().id,
            queue = PlaybackQueue(items(1, 2, 3), currentItemId = id(1)),
        ))
        val viewModel = subject(controller, library, repository = repository)
        val shells = mutableListOf<PlayerShellState>()
        val collection = backgroundScope.launch { viewModel.shellState.collect { shells += it } }
        advanceUntilIdle()
        val rows = viewModel.uiState.value.queue
        val shellCount = shells.size
        reads = 0
        repeat(20) { tick ->
            controller.update { copy(positionMs = (tick + 1) * 100L) }
            advanceUntilIdle()
            assertSame(rows, viewModel.uiState.value.queue)
        }
        assertEquals(0, reads)
        assertEquals(shellCount, shells.size)
        assertEquals(2_000L, viewModel.uiState.value.positionMs)
        collection.cancel()
    }

    @Test
    fun `queue and same-id metadata changes refresh projections and artwork`() = runTest(dispatcher) {
        val first = track(1)
        val second = track(2)
        val source = MutableStateFlow(listOf(first, second))
        val repository = object : MediaLibraryRepository by FakeMediaLibraryRepository() {
            override fun observeTracks(includeHidden: Boolean) = source
        }
        val artworkRequests = mutableListOf<Track>()
        val controller = RecordingController(PlaybackControllerState(
            currentTrackId = first.id,
            queue = PlaybackQueue(items(1, 2), currentItemId = id(1)),
        ))
        val viewModel = subject(controller, source.value, repository = repository,
            artworkRepository = object : ArtworkRepository {
                override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
                    artworkRequests += track
                    return ArtworkResult.Placeholder
                }
            },
        )
        advanceUntilIdle()
        controller.update { copy(positionMs = 500) }
        advanceUntilIdle()
        assertEquals(listOf(first), artworkRequests)

        val renamed = first.copy(title = "Updated", dateModifiedMs = 99)
        source.value = listOf(renamed, second)
        advanceUntilIdle()
        assertEquals(renamed, viewModel.uiState.value.currentTrack)
        assertEquals(renamed, viewModel.uiState.value.queue.first().track)
        assertEquals(listOf(first, renamed), artworkRequests)

        controller.update { copy(currentTrackId = second.id,
            queue = PlaybackQueue(items(2, 1), currentItemId = id(2))) }
        advanceUntilIdle()
        assertEquals(listOf(id(2), id(1)), viewModel.uiState.value.queue.map { it.queueItemId })
        assertTrue(viewModel.uiState.value.queue.first().isCurrent)
        assertEquals(second.id, viewModel.shellState.value.currentTrackId)
        source.value = listOf(renamed)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.queue.first().track)
        assertEquals(PlayerShellState(), viewModel.shellState.value)
    }

    @Test
    fun `sleep timer visibility and commands reflect in UI and controller`() = runTest(dispatcher) {
        val tracks = listOf(track(1))
        val controller = RecordingController(
            PlaybackControllerState(
                currentTrackId = tracks[0].id,
            ),
        )
        val settingsRepo = FakeSettingsRepository()
        val viewModel = subject(controller, tracks, settingsRepository = settingsRepo)
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showSleepTimer)
        assertEquals(null, viewModel.uiState.value.sleepTimer)

        viewModel.showSleepTimer()
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.showSleepTimer)

        viewModel.dismissSleepTimer()
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.showSleepTimer)

        viewModel.startSleepTimer(30, true)
        advanceUntilIdle()
        assertEquals(1, controller.startSleepTimerCalls)
        assertEquals(30, controller.lastSleepTimerDuration)
        assertEquals(true, controller.lastSleepTimerExtend)
        assertEquals(30, settingsRepo.settings.value.sleepTimerDurationMinutes)
        assertEquals(true, settingsRepo.settings.value.sleepTimerExtendToEndOfTrack)

        val timerStatus = SleepTimerStatus(
            remainingMs = 1800_000L,
            totalDurationMs = 1800_000L,
            extendToEndOfTrack = true,
        )
        controller.update { copy(sleepTimer = timerStatus) }
        advanceUntilIdle()
        assertEquals(timerStatus, viewModel.uiState.value.sleepTimer)

        viewModel.stopSleepTimer()
        advanceUntilIdle()
        assertEquals(1, controller.stopSleepTimerCalls)

        collection.cancel()
    }

    @Test
    fun `skipNext updates slideDirection to FORWARD`() = runTest(dispatcher) {
        val tracks = listOf(track(1), track(2), track(3))
        val queue = PlaybackQueue(originalQueue = items(1, 2, 3), currentItemId = id(1))
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = tracks[0].id,
                queue = queue,
                canSkipNext = true,
            ),
        )
        val viewModel = subject(controller, tracks)
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        assertEquals(TrackSlideDirection.NONE, viewModel.uiState.value.slideDirection)

        viewModel.skipNext()
        controller.update { copy(currentTrackId = tracks[1].id, queue = queue.copy(currentItemId = id(2))) }
        advanceUntilIdle()

        assertEquals(TrackSlideDirection.FORWARD, viewModel.uiState.value.slideDirection)
        collection.cancel()
    }

    @Test
    fun `skipPrevious updates slideDirection to BACKWARD`() = runTest(dispatcher) {
        val tracks = listOf(track(1), track(2), track(3))
        val queue = PlaybackQueue(originalQueue = items(1, 2, 3), currentItemId = id(2))
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = tracks[1].id,
                queue = queue,
                canSkipPrevious = true,
            ),
        )
        val viewModel = subject(controller, tracks)
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.skipPrevious()
        controller.update { copy(currentTrackId = tracks[0].id, queue = queue.copy(currentItemId = id(1))) }
        advanceUntilIdle()

        assertEquals(TrackSlideDirection.BACKWARD, viewModel.uiState.value.slideDirection)
        collection.cancel()
    }

    @Test
    fun `jumpToQueueItem forward updates slideDirection to FORWARD and backward to BACKWARD`() = runTest(dispatcher) {
        val tracks = listOf(track(1), track(2), track(3))
        val queue = PlaybackQueue(originalQueue = items(1, 2, 3), currentItemId = id(1))
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = tracks[0].id,
                queue = queue,
            ),
        )
        val viewModel = subject(controller, tracks)
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // Jump forward from item 1 to item 3
        viewModel.jumpToQueueItem(id(3))
        controller.update { copy(currentTrackId = tracks[2].id, queue = queue.copy(currentItemId = id(3))) }
        advanceUntilIdle()
        assertEquals(TrackSlideDirection.FORWARD, viewModel.uiState.value.slideDirection)

        // Jump backward from item 3 to item 2
        viewModel.jumpToQueueItem(id(2))
        controller.update { copy(currentTrackId = tracks[1].id, queue = queue.copy(currentItemId = id(2))) }
        advanceUntilIdle()
        assertEquals(TrackSlideDirection.BACKWARD, viewModel.uiState.value.slideDirection)

        collection.cancel()
    }

    @Test
    fun `natural playback advance in queue updates slideDirection based on position`() = runTest(dispatcher) {
        val tracks = listOf(track(1), track(2))
        val queue = PlaybackQueue(originalQueue = items(1, 2), currentItemId = id(1))
        val controller = RecordingController(
            PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTED,
                currentTrackId = tracks[0].id,
                queue = queue,
            ),
        )
        val viewModel = subject(controller, tracks)
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        assertEquals(TrackSlideDirection.NONE, viewModel.uiState.value.slideDirection)

        // Auto-advance to track 2 without clicking buttons
        controller.update { copy(currentTrackId = tracks[1].id, queue = queue.copy(currentItemId = id(2))) }
        advanceUntilIdle()
        assertEquals(TrackSlideDirection.FORWARD, viewModel.uiState.value.slideDirection)

        // Same track replay does not change trackId
        controller.update { copy(positionMs = 0) }
        advanceUntilIdle()
        assertEquals(tracks[1].id, viewModel.uiState.value.currentTrack?.id)

        collection.cancel()
    }

    private fun subject(
        controller: RecordingController,
        tracks: List<Track>,
        clock: Clock = Clock { 10_000L },
        repository: MediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        artworkRepository: ArtworkRepository = object : ArtworkRepository {
            override suspend fun artwork(track: Track, targetPx: Int) = ArtworkResult.Placeholder
        },
        settingsRepository: SettingsRepository = FakeSettingsRepository(),
    ) = PlayerViewModel(
        playbackController = controller,
        mediaLibraryRepository = repository,
        artworkRepository = artworkRepository,
        metadataRepository = object : TrackMetadataRepository {
            override suspend fun read(track: Track) =
                AdvancedTrackMetadata("audio/flac", 1_000, 48_000, track.sizeBytes, true)
        },
        settingsRepository = settingsRepository,
        clock = clock,
        computationDispatcher = dispatcher,
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

private class MutableClock(var currentTime: Long = 10_000L) : Clock {
    override fun currentTimeMillis(): Long = currentTime
}

private class RecordingController(initial: PlaybackControllerState) : PlaybackControllerFacade {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<PlaybackControllerState> = mutableState
    var playCalls = 0
    var pauseCalls = 0
    var previousCalls = 0
    var nextCalls = 0
    var seekPosition: Long? = null
    var mode: PlaybackMode? = null
    var removed: QueueItemId? = null
    var jumped: QueueItemId? = null
    var clearQueueCalls = 0
    var startSleepTimerCalls = 0
    var stopSleepTimerCalls = 0
    var lastSleepTimerDuration: Int? = null
    var lastSleepTimerExtend: Boolean? = null
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: com.musicapp.player.core.domain.model.PlaybackContext) = Unit
    override fun play() { playCalls++ }
    override fun pause() { pauseCalls++ }
    override fun skipToPrevious() { previousCalls++ }
    override fun skipToNext() { nextCalls++ }
    override fun seekTo(positionMs: Long) { seekPosition = positionMs }
    override fun setPlaybackMode(mode: PlaybackMode) { this.mode = mode }
    override fun jumpToQueueItem(queueItemId: QueueItemId) { jumped = queueItemId }
    override fun removeFromQueue(queueItemId: QueueItemId) { removed = queueItemId }
    override fun clearQueue() { clearQueueCalls++ }
    override fun startSleepTimer(durationMinutes: Int, extendToEndOfTrack: Boolean) {
        startSleepTimerCalls++
        lastSleepTimerDuration = durationMinutes
        lastSleepTimerExtend = extendToEndOfTrack
    }
    override fun stopSleepTimer() {
        stopSleepTimerCalls++
    }
    fun update(transform: PlaybackControllerState.() -> PlaybackControllerState) {
        mutableState.value = mutableState.value.transform()
    }
}
