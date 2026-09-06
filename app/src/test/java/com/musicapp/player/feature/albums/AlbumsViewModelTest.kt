package com.musicapp.player.feature.albums

import androidx.lifecycle.SavedStateHandle
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {
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
    fun `grouped album keeps stable representative and provides clean decoupled state`() = runTest(dispatcher) {
        val tracks = listOf(
            track(id = 2, dateModifiedMs = 20),
            track(id = 1, dateModifiedMs = 10),
        )
        val viewModel = subject(tracks)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        val album = viewModel.uiState.value.albums.single()
        assertEquals(1L, album.representativeTrack.id.mediaStoreId)
        assertEquals("Album", album.title)
        assertEquals(2, album.trackCount)
        collection.cancel()
    }

    @Test
    fun `album sort updates and reflects in uiState`() = runTest(dispatcher) {
        val tracks = listOf(
            track(id = 1, dateModifiedMs = 10),
        )
        val viewModel = subject(tracks)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        assertEquals(AlbumSortField.TITLE, viewModel.uiState.value.sort.field)
        viewModel.selectSort(AlbumSortField.ARTIST)
        advanceUntilIdle()
        assertEquals(AlbumSortField.ARTIST, viewModel.uiState.value.sort.field)

        viewModel.selectSort(AlbumSortField.RELEASE_YEAR)
        advanceUntilIdle()
        assertEquals(AlbumSortField.RELEASE_YEAR, viewModel.uiState.value.sort.field)
        assertEquals(com.musicapp.player.feature.category.CategorySortDirection.DESCENDING, viewModel.uiState.value.sort.direction)

        viewModel.selectSort(AlbumSortField.RELEASE_YEAR)
        advanceUntilIdle()
        assertEquals(AlbumSortField.RELEASE_YEAR, viewModel.uiState.value.sort.field)
        assertEquals(com.musicapp.player.feature.category.CategorySortDirection.ASCENDING, viewModel.uiState.value.sort.direction)

        collection.cancel()
    }

    @Test
    fun `selectSort updates state and persists to sortPreferencesRepository`() = runTest(dispatcher) {
        val sortRepo = com.musicapp.player.fakes.FakeSortPreferencesRepository()
        val viewModel = subject(sortPreferencesRepository = sortRepo)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        viewModel.selectSort(AlbumSortField.TRACK_COUNT)
        advanceUntilIdle()
        assertEquals(AlbumSortField.TRACK_COUNT, viewModel.uiState.value.sort.field)
        assertEquals(AlbumSortField.TRACK_COUNT, sortRepo.albumSort.value.field)

        collection.cancel()
    }

    @Test
    fun `sortPreferencesRepository initial albumSort is observed`() = runTest(dispatcher) {
        val initialSort = AlbumSort(field = AlbumSortField.RELEASE_YEAR, direction = com.musicapp.player.feature.category.CategorySortDirection.DESCENDING)
        val sortRepo = com.musicapp.player.fakes.FakeSortPreferencesRepository(initialAlbumSort = initialSort)
        val viewModel = subject(sortPreferencesRepository = sortRepo)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        assertEquals(initialSort, viewModel.uiState.value.sort)
        collection.cancel()
    }

    @Test
    fun `selectColumnCount updates state and persists to settings repository`() = runTest(dispatcher) {
        val settingsRepository = com.musicapp.player.data.repository.FakeSettingsRepository()
        val viewModel = subject(settingsRepository = settingsRepository)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.columnCount)

        viewModel.selectColumnCount(3)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.columnCount)
        assertEquals(3, settingsRepository.settings.value.albumGridColumns)

        viewModel.selectColumnCount(4)
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.columnCount)
        assertEquals(4, settingsRepository.settings.value.albumGridColumns)

        // Invalid column count should be ignored
        viewModel.selectColumnCount(1)
        viewModel.selectColumnCount(5)
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.columnCount)

        collection.cancel()
    }

    @Test
    fun `settingsRepository initial albumGridColumns is observed`() = runTest(dispatcher) {
        val settingsRepository = com.musicapp.player.data.repository.FakeSettingsRepository(
            initialSettings = com.musicapp.player.core.domain.model.AppSettings(albumGridColumns = 4),
        )
        val viewModel = subject(settingsRepository = settingsRepository)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.columnCount)
        collection.cancel()
    }

    @Test
    fun `tracks with same title and compatible artist but different albumIds merge into single summary with combined trackCount`() = runTest(dispatcher) {
        val tracks = listOf(
            track(id = 1, dateModifiedMs = 10).copy(
                albumTitle = "跨时代",
                albumId = AlbumId("external", 10),
                artistName = "周杰伦",
            ),
            track(id = 2, dateModifiedMs = 20).copy(
                albumTitle = "跨时代",
                albumId = AlbumId("external", 11),
                artistName = "周杰伦",
            ),
        )
        val viewModel = subject(tracks)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        val albums = viewModel.uiState.value.albums
        assertEquals(1, albums.size)
        val album = albums.single()
        assertEquals("跨时代", album.title)
        assertEquals(2, album.trackCount)
        assertEquals(setOf(AlbumId("external", 10), AlbumId("external", 11)), album.memberAlbumIds)

        collection.cancel()
    }

    @Test
    fun `album detail view model resolves unified tracks across memberAlbumIds and supports playback`() = runTest(dispatcher) {
        val tracks = listOf(
            track(id = 1, dateModifiedMs = 10).copy(
                albumTitle = "魔杰座",
                albumId = AlbumId("external", 20),
                artistName = "周杰伦",
                trackNumber = 1,
            ),
            track(id = 2, dateModifiedMs = 20).copy(
                albumTitle = "魔杰座",
                albumId = AlbumId("external", 21),
                artistName = "周杰伦 / 梁心颐",
                trackNumber = 2,
            ),
        )
        val playbackController = RecordingPlaybackController()
        val mediaRepo = FakeMediaLibraryRepository(tracks)
        val playlistRepo = com.musicapp.player.data.repository.FakePlaylistRepository()
        val clock = com.musicapp.player.fakes.FakeClock(1000L)
        val playlistUseCase = com.musicapp.player.feature.playlists.PlaylistUseCase(playlistRepo, clock)

        val detailViewModel = AlbumDetailViewModel(
            mediaLibraryRepository = mediaRepo,
            playbackController = playbackController,
            trackMetadataRepository = FakeTrackMetadataRepo(),
            playlistRepository = playlistRepo,
            batchActionExecutor = NoOpBatchExecutor,
            playlistUseCase = playlistUseCase,
            computationDispatcher = dispatcher,
        )

        val detailCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            detailViewModel.uiState.collect {}
        }

        detailViewModel.open(AlbumId("external", 20))
        advanceUntilIdle()

        val state = detailViewModel.uiState.value
        assertEquals("魔杰座", state.title)
        assertEquals("周杰伦", state.artistName)
        assertEquals(2, state.tracks.size)
        assertEquals(2, state.stats.trackCount)

        detailViewModel.playTrack(TrackId("external", 1))
        advanceUntilIdle()

        assertEquals(1, playbackController.playCalls)
        val playedTrackIds = playbackController.lastPlayedContext?.orderedTrackIds
        assertEquals(2, playedTrackIds?.size)

        detailCollection.cancel()
    }

    @Test
    fun `album detail keeps resolving group after representative track is removed`() = runTest(dispatcher) {
        val tracks = listOf(
            track(id = 1, dateModifiedMs = 10).copy(
                albumTitle = "跨时代",
                albumId = AlbumId("external", 31),
                artistName = "周杰伦",
            ),
            track(id = 2, dateModifiedMs = 20).copy(
                albumTitle = "跨时代",
                albumId = AlbumId("external", 30),
                artistName = "周杰伦 / 梁心颐",
            ),
        )
        val mediaRepo = FakeMediaLibraryRepository(tracks)
        val playlistRepo = com.musicapp.player.data.repository.FakePlaylistRepository()
        val detailViewModel = AlbumDetailViewModel(
            mediaLibraryRepository = mediaRepo,
            playbackController = RecordingPlaybackController(),
            trackMetadataRepository = FakeTrackMetadataRepo(),
            playlistRepository = playlistRepo,
            batchActionExecutor = NoOpBatchExecutor,
            playlistUseCase = com.musicapp.player.feature.playlists.PlaylistUseCase(
                playlistRepo,
                com.musicapp.player.fakes.FakeClock(1000L),
            ),
            computationDispatcher = dispatcher,
        )
        val detailCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            detailViewModel.uiState.collect {}
        }
        val summary = AlbumGrouping.group(tracks).single()

        detailViewModel.open(summary.id, summary.groupKey)
        advanceUntilIdle()
        mediaRepo.replaceTracksForVolume("external", listOf(tracks[1]))
        advanceUntilIdle()

        val state = detailViewModel.uiState.value
        assertEquals(false, state.isUnavailable)
        assertEquals(1, state.tracks.size)
        assertEquals("跨时代", state.title)
        detailCollection.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: AlbumsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    private fun subject(
        tracks: List<Track> = listOf(track(id = 1, dateModifiedMs = 10)),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        artworkRepository: ArtworkRepository = RecordingArtworkRepository(),
        settingsRepository: com.musicapp.player.data.settings.SettingsRepository = com.musicapp.player.data.repository.FakeSettingsRepository(),
        sortPreferencesRepository: com.musicapp.player.data.sort.SortPreferencesRepository = com.musicapp.player.fakes.FakeSortPreferencesRepository(),
    ) = AlbumsViewModel(
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        savedStateHandle = savedStateHandle,
        settingsRepository = settingsRepository,
        sortPreferencesRepository = sortPreferencesRepository,
        computationDispatcher = dispatcher,
    )

    private fun track(id: Long, dateModifiedMs: Long) =
        Track(
            id = TrackId("external", id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            albumId = AlbumId("external", 10),
            durationMs = 1_000,
            dateAddedMs = id,
            dateModifiedMs = dateModifiedMs,
            relativePath = "Music/",
            displayName = "$id.mp3",
        )
}

private class RecordingPlaybackController : com.musicapp.player.core.playback.PlaybackControllerFacade {
    override val state: kotlinx.coroutines.flow.StateFlow<com.musicapp.player.core.playback.PlaybackControllerState> =
        kotlinx.coroutines.flow.MutableStateFlow(com.musicapp.player.core.playback.PlaybackControllerState())
    var lastPlayedContext: com.musicapp.player.core.domain.model.PlaybackContext? = null
    var playCalls: Int = 0

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: com.musicapp.player.core.domain.model.PlaybackContext) {
        lastPlayedContext = context
        playCalls += 1
    }
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

private class FakeTrackMetadataRepo : com.musicapp.player.core.metadata.TrackMetadataRepository {
    override suspend fun read(track: Track): com.musicapp.player.core.metadata.AdvancedTrackMetadata =
        com.musicapp.player.core.metadata.AdvancedTrackMetadata(
            encoding = "MP3",
            bitrateBps = 320_000L,
            sampleRateHz = 44100,
            fileSizeBytes = track.sizeBytes,
            isReadable = true,
        )
}

private object NoOpBatchExecutor : com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor {
    override suspend fun execute(
        action: com.musicapp.player.feature.tracks.batch.BatchTrackAction,
        orderedTrackIds: List<TrackId>,
    ): com.musicapp.player.feature.tracks.batch.BatchTrackActionResult =
        if (orderedTrackIds.isEmpty()) {
            com.musicapp.player.feature.tracks.batch.BatchTrackActionResult.EmptySelection
        } else {
            com.musicapp.player.feature.tracks.batch.BatchTrackActionResult.Completed(
                action = action,
                selectedCount = orderedTrackIds.size,
                affectedCount = orderedTrackIds.size,
                skippedCount = 0,
            )
        }
}

private data class ArtworkRequest(val track: Track, val targetPx: Int)

private class RecordingArtworkRepository(
    private val failure: Throwable? = null,
) : ArtworkRepository {
    val requests = mutableListOf<ArtworkRequest>()

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requests += ArtworkRequest(track, targetPx)
        failure?.let { throw it }
        return ArtworkResult.Placeholder
    }
}
