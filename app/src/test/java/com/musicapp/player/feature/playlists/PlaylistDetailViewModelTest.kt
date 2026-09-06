package com.musicapp.player.feature.playlists

import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Availability
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
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.feature.tracks.batch.DefaultBatchTrackActionExecutor
import com.musicapp.player.fakes.FakeSortPreferencesRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `view model initializes with ordered tracks and supports sorting`() = runTest(dispatcher) {
        val trackA = track(1, "Alpha", "Artist 2", "Album B", durationMs = 120_000, sizeBytes = 10_000_000)
        val trackB = track(2, "Beta", "Artist 1", "Album A", durationMs = 180_000, sizeBytes = 20_000_000)
        val trackC = track(3, "Charlie", "Artist 3", "Album C", durationMs = 60_000, sizeBytes = 5_000_000)

        // Initial playlist order: C, A, B
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Favorites",
            normalizedName = "favorites",
            trackIds = listOf(trackC.id, trackA.id, trackB.id),
            createdAtMs = 1000L,
        )
        val playlistRepo = FakePlaylistRepository(initialPlaylists = listOf(playlist))
        val mediaRepo = FakeMediaLibraryRepository(listOf(trackA, trackB, trackC))
        val playbackController = DetailRecordingPlaybackController()
        val executor = DefaultBatchTrackActionExecutor(playlistRepo, mediaRepo, playbackController, Clock { 10 })
        val metadataRepo = FakeTrackMetadataRepository()

        val viewModel = PlaylistDetailViewModel(
            playlistRepository = playlistRepo,
            mediaLibraryRepository = mediaRepo,
            useCase = PlaylistUseCase(playlistRepo, Clock { 10 }),
            playbackController = playbackController,
            batchActionExecutor = executor,
            trackMetadataRepository = metadataRepo,
            computationDispatcher = dispatcher,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        // 1. Default addition order
        assertEquals(listOf(trackC.id, trackA.id, trackB.id), viewModel.uiState.value.tracks.map(Track::id))
        assertEquals(listOf(trackC.id, trackA.id, trackB.id), viewModel.uiState.value.displayTracks.map(Track::id))

        // 2. Sort by TITLE ascending -> Alpha, Beta, Charlie
        viewModel.selectSort(PlaylistTrackSortField.TITLE)
        advanceUntilIdle()
        assertEquals(listOf(trackA.id, trackB.id, trackC.id), viewModel.uiState.value.displayTracks.map(Track::id))
        assertTrue(viewModel.uiState.value.sections.isNotEmpty())

        // 3. Sort by TITLE descending -> Charlie, Beta, Alpha
        viewModel.selectSort(PlaylistTrackSortField.TITLE)
        advanceUntilIdle()
        assertEquals(listOf(trackC.id, trackB.id, trackA.id), viewModel.uiState.value.displayTracks.map(Track::id))

        // 4. Sort by DURATION ascending -> Charlie (60s), Alpha (120s), Beta (180s)
        viewModel.selectSort(PlaylistTrackSortField.DURATION)
        advanceUntilIdle()
        assertEquals(listOf(trackC.id, trackA.id, trackB.id), viewModel.uiState.value.displayTracks.map(Track::id))

        // 5. Restore DEFAULT order
        viewModel.selectSort(PlaylistTrackSortField.DEFAULT)
        advanceUntilIdle()
        assertEquals(listOf(trackC.id, trackA.id, trackB.id), viewModel.uiState.value.displayTracks.map(Track::id))

        collection.cancel()
    }

    @Test
    fun `view model persists sort preference changes`() = runTest(dispatcher) {
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Favorites",
            normalizedName = "favorites",
            trackIds = emptyList(),
            createdAtMs = 1000L,
        )
        val playlistRepo = FakePlaylistRepository(initialPlaylists = listOf(playlist))
        val mediaRepo = FakeMediaLibraryRepository(emptyList())
        val sortPreferencesRepo = FakeSortPreferencesRepository()
        val viewModel = PlaylistDetailViewModel(
            playlistRepository = playlistRepo,
            mediaLibraryRepository = mediaRepo,
            useCase = PlaylistUseCase(playlistRepo, Clock { 10 }),
            playbackController = DetailRecordingPlaybackController(),
            sortPreferencesRepository = sortPreferencesRepo,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        viewModel.selectSort(PlaylistTrackSortField.ARTIST)
        advanceUntilIdle()

        assertEquals(
            PlaylistTrackSort(field = PlaylistTrackSortField.ARTIST, direction = PlaylistTrackSortDirection.ASCENDING),
            sortPreferencesRepo.playlistTrackSort.value,
        )
        assertEquals(
            PlaylistTrackSort(field = PlaylistTrackSortField.ARTIST, direction = PlaylistTrackSortDirection.ASCENDING),
            viewModel.uiState.value.sort,
        )

        collection.cancel()
    }

    @Test
    fun `search filters display tracks and updates sections`() = runTest(dispatcher) {
        val track1 = track(1, "Viva La Vida", "Coldplay")
        val track2 = track(2, "Yellow", "Coldplay")
        val track3 = track(3, "Shape of You", "Ed Sheeran")

        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Pop",
            normalizedName = "pop",
            trackIds = listOf(track1.id, track2.id, track3.id),
            createdAtMs = 0L,
        )
        val playlistRepo = FakePlaylistRepository(initialPlaylists = listOf(playlist))
        val mediaRepo = FakeMediaLibraryRepository(listOf(track1, track2, track3))
        val playbackController = DetailRecordingPlaybackController()
        val executor = DefaultBatchTrackActionExecutor(playlistRepo, mediaRepo, playbackController, Clock { 10 })

        val viewModel = PlaylistDetailViewModel(
            playlistRepository = playlistRepo,
            mediaLibraryRepository = mediaRepo,
            useCase = PlaylistUseCase(playlistRepo, Clock { 10 }),
            playbackController = playbackController,
            batchActionExecutor = executor,
            trackMetadataRepository = FakeTrackMetadataRepository(),
            computationDispatcher = dispatcher,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        viewModel.openSearch()
        viewModel.setSearchQuery("Coldplay")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSearching)
        assertEquals(2, viewModel.uiState.value.displayTracks.size)
        assertEquals(listOf(track1.id, track2.id), viewModel.uiState.value.displayTracks.map(Track::id))

        viewModel.closeSearch()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(3, viewModel.uiState.value.displayTracks.size)

        collection.cancel()
    }

    @Test
    fun `selection mode handles select all toggle and batch remove`() = runTest(dispatcher) {
        val track1 = track(1, "Track 1")
        val track2 = track(2, "Track 2")
        val track3 = track(3, "Track 3")

        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "My List",
            normalizedName = "my list",
            trackIds = listOf(track1.id, track2.id, track3.id),
            createdAtMs = 0L,
        )
        val playlistRepo = FakePlaylistRepository(
            initialPlaylists = listOf(playlist),
            existingTrackIds = setOf(track1.id, track2.id, track3.id),
        )
        val mediaRepo = FakeMediaLibraryRepository(listOf(track1, track2, track3))
        val playbackController = DetailRecordingPlaybackController()
        val executor = DefaultBatchTrackActionExecutor(playlistRepo, mediaRepo, playbackController, Clock { 10 })

        val viewModel = PlaylistDetailViewModel(
            playlistRepository = playlistRepo,
            mediaLibraryRepository = mediaRepo,
            useCase = PlaylistUseCase(playlistRepo, Clock { 10 }),
            playbackController = playbackController,
            batchActionExecutor = executor,
            trackMetadataRepository = FakeTrackMetadataRepository(),
            computationDispatcher = dispatcher,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        // Long click -> start selection
        viewModel.startSelection(track1.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(track1.id), viewModel.uiState.value.selectedTrackIds)

        // Toggle track 2
        viewModel.toggleSelection(track2.id)
        advanceUntilIdle()
        assertEquals(setOf(track1.id, track2.id), viewModel.uiState.value.selectedTrackIds)

        // Select all
        viewModel.selectAll()
        advanceUntilIdle()
        assertEquals(setOf(track1.id, track2.id, track3.id), viewModel.uiState.value.selectedTrackIds)

        // Remove selected
        viewModel.removeSelected()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(emptySet<TrackId>(), viewModel.uiState.value.selectedTrackIds)
        assertEquals(0, viewModel.uiState.value.tracks.size)

        collection.cancel()
    }

    @Test
    fun `clearSelection exits selection mode and clears selected tracks`() = runTest(dispatcher) {
        val track1 = track(1, "Track 1")
        val track2 = track(2, "Track 2")
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "My List",
            normalizedName = "my list",
            trackIds = listOf(track1.id, track2.id),
            createdAtMs = 0L,
        )
        val playlistRepo = FakePlaylistRepository(
            initialPlaylists = listOf(playlist),
            existingTrackIds = setOf(track1.id, track2.id),
        )
        val mediaRepo = FakeMediaLibraryRepository(listOf(track1, track2))
        val playbackController = DetailRecordingPlaybackController()
        val executor = DefaultBatchTrackActionExecutor(playlistRepo, mediaRepo, playbackController, Clock { 10 })

        val viewModel = PlaylistDetailViewModel(
            playlistRepository = playlistRepo,
            mediaLibraryRepository = mediaRepo,
            useCase = PlaylistUseCase(playlistRepo, Clock { 10 }),
            playbackController = playbackController,
            batchActionExecutor = executor,
            trackMetadataRepository = FakeTrackMetadataRepository(),
            computationDispatcher = dispatcher,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        viewModel.startSelection(track1.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(track1.id), viewModel.uiState.value.selectedTrackIds)

        viewModel.clearSelection()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedTrackIds.isEmpty())

        collection.cancel()
    }

    @Test
    fun `shuffle play prepares and triggers playback`() = runTest(dispatcher) {
        val track1 = track(1, "Track 1")
        val track2 = track(2, "Track 2")
        val track3 = track(3, "Track 3")

        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Shuffle List",
            normalizedName = "shuffle list",
            trackIds = listOf(track1.id, track2.id, track3.id),
            createdAtMs = 0L,
        )
        val playlistRepo = FakePlaylistRepository(initialPlaylists = listOf(playlist))
        val mediaRepo = FakeMediaLibraryRepository(listOf(track1, track2, track3))
        val playbackController = DetailRecordingPlaybackController()
        val executor = DefaultBatchTrackActionExecutor(playlistRepo, mediaRepo, playbackController, Clock { 10 })

        val viewModel = PlaylistDetailViewModel(
            playlistRepository = playlistRepo,
            mediaLibraryRepository = mediaRepo,
            useCase = PlaylistUseCase(playlistRepo, Clock { 10 }),
            playbackController = playbackController,
            batchActionExecutor = executor,
            trackMetadataRepository = FakeTrackMetadataRepository(),
            computationDispatcher = dispatcher,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        viewModel.shufflePlay()
        advanceUntilIdle()

        assertNotNull(playbackController.playedContext)
        assertEquals(3, playbackController.playedContext?.orderedTrackIds?.size)
        assertEquals(1, playbackController.playCalls)

        collection.cancel()
    }

    @Test
    fun `track info viewing and back handling`() = runTest(dispatcher) {
        val track1 = track(1, "Track 1")
        val playlist = Playlist(
            id = PlaylistId(1),
            displayName = "Info List",
            normalizedName = "info list",
            trackIds = listOf(track1.id),
            createdAtMs = 0L,
        )
        val playlistRepo = FakePlaylistRepository(initialPlaylists = listOf(playlist))
        val mediaRepo = FakeMediaLibraryRepository(listOf(track1))
        val playbackController = DetailRecordingPlaybackController()
        val executor = DefaultBatchTrackActionExecutor(playlistRepo, mediaRepo, playbackController, Clock { 10 })
        val metadataRepo = FakeTrackMetadataRepository()

        val viewModel = PlaylistDetailViewModel(
            playlistRepository = playlistRepo,
            mediaLibraryRepository = mediaRepo,
            useCase = PlaylistUseCase(playlistRepo, Clock { 10 }),
            playbackController = playbackController,
            batchActionExecutor = executor,
            trackMetadataRepository = metadataRepo,
            computationDispatcher = dispatcher,
        )
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.open(playlist.id)
        advanceUntilIdle()

        viewModel.showTrackInfo(track1)
        advanceUntilIdle()
        assertEquals(track1, viewModel.uiState.value.infoTrack)
        assertNotNull(viewModel.uiState.value.infoMetadata)
        assertFalse(viewModel.uiState.value.isInfoLoading)

        // onBack dismisses infoTrack
        assertTrue(viewModel.onBack())
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.infoTrack)

        // onBack when idle returns false
        assertFalse(viewModel.onBack())

        collection.cancel()
    }

    private fun track(
        value: Long,
        title: String = "Track $value",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 180_000L,
        sizeBytes: Long = 10_000_000L,
        availability: Availability = Availability.AVAILABLE,
    ) = Track(
        id = TrackId("primary", value),
        title = title,
        artistName = artist,
        albumTitle = album,
        durationMs = durationMs,
        dateAddedMs = value,
        dateModifiedMs = value,
        relativePath = "Music",
        displayName = "$title.mp3",
        sizeBytes = sizeBytes,
        availability = availability,
    )
}

private class FakeTrackMetadataRepository : TrackMetadataRepository {
    override suspend fun read(track: Track): AdvancedTrackMetadata =
        AdvancedTrackMetadata(
            encoding = "MP3",
            bitrateBps = 320_000L,
            sampleRateHz = 44100,
            fileSizeBytes = track.sizeBytes,
            isReadable = true,
        )
}

private class DetailRecordingPlaybackController : PlaybackControllerFacade {
    override val state: StateFlow<PlaybackControllerState> = MutableStateFlow(PlaybackControllerState())
    var playedContext: PlaybackContext? = null
    var playCalls: Int = 0

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: PlaybackContext) {
        playedContext = context
        playCalls += 1
    }
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
}
