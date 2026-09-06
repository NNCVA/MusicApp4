package com.musicapp.player.feature.search

import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.data.repository.FakeHistoryRepository
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.feature.tracks.TrackSortField
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.feature.tracks.batch.DefaultBatchTrackActionExecutor
import com.musicapp.player.navigation.SearchScopeType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    private val track1 = Track(
        id = TrackId("primary", 1L),
        title = "Beethoven Symphony",
        artistName = "Ludwig",
        albumTitle = "Classics",
        durationMs = 180_000,
        dateAddedMs = 1_000,
        dateModifiedMs = 1_000,
        relativePath = "Music/",
        displayName = "Beethoven Symphony.mp3",
        availability = Availability.AVAILABLE,
    )

    private val track2 = Track(
        id = TrackId("primary", 2L),
        title = "Moonlight Sonata",
        artistName = "Beethoven",
        albumTitle = "Piano Works",
        durationMs = 240_000,
        dateAddedMs = 2_000,
        dateModifiedMs = 2_000,
        relativePath = "Music/",
        displayName = "Moonlight Sonata.mp3",
        availability = Availability.AVAILABLE,
    )

    private val track3 = Track(
        id = TrackId("primary", 3L),
        title = "Clair de Lune",
        artistName = "Debussy",
        albumTitle = "Suite Bergamasque",
        durationMs = 300_000,
        dateAddedMs = 3_000,
        dateModifiedMs = 3_000,
        relativePath = "Music/",
        displayName = "Clair de Lune.mp3",
        availability = Availability.AVAILABLE,
    )

    private val allTracks = listOf(track1, track2, track3)

    private val fakePlaybackController = object : PlaybackControllerFacade {
        override val state: StateFlow<PlaybackControllerState> =
            MutableStateFlow(PlaybackControllerState())
        var lastContext: PlaybackContext? = null

        override fun play(context: PlaybackContext) {
            lastContext = context
        }

        override fun connect() {}
        override fun disconnect() {}
        override fun play() {}
        override fun pause() {}
        override fun skipToPrevious() {}
        override fun skipToNext() {}
        override fun seekTo(positionMs: Long) {}
    }

    private val fakeTrackMetadataRepository = object : TrackMetadataRepository {
        override suspend fun read(track: Track): AdvancedTrackMetadata =
            AdvancedTrackMetadata(
                encoding = "audio/mp3",
                bitrateBps = 320_000,
                sampleRateHz = 44_100,
                fileSizeBytes = track.sizeBytes,
                isReadable = true,
            )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        tracks: List<Track> = allTracks,
        playlistRepository: FakePlaylistRepository = FakePlaylistRepository(
            existingTrackIds = tracks.map(Track::id).toSet(),
        ),
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(
            existingTrackIds = tracks.map(Track::id).toSet(),
        ),
    ): SearchViewModel {
        val mediaRepo = FakeMediaLibraryRepository(tracks)
        val batchExecutor = DefaultBatchTrackActionExecutor(
            playlistRepository = playlistRepository,
            mediaLibraryRepository = mediaRepo,
            playbackController = fakePlaybackController,
            clock = Clock { 1000L },
        )
        return SearchViewModel(
            mediaLibraryRepository = mediaRepo,
            playlistRepository = playlistRepository,
            historyRepository = historyRepository,
            playbackController = fakePlaybackController,
            batchTrackActionExecutor = batchExecutor,
            trackMetadataRepository = fakeTrackMetadataRepository,
            computationDispatcher = dispatcher,
        )
    }

    @Test
    fun `initial search state has total candidates but empty filteredTracks`() = runTest(dispatcher) {
        val vm = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }

        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(3, state.totalCandidateCount)
        assertTrue(state.query.isEmpty())
        assertTrue(state.filteredTracks.isEmpty())
    }

    @Test
    fun `query filters tracks by title, artist, or album case-insensitively`() = runTest(dispatcher) {
        val vm = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Match by title ("moonlight")
        vm.onQueryChange("moonlight")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(track2), vm.uiState.value.filteredTracks)

        // Match by artist ("beethoven" matches both track1 as artist Ludwig/Beethoven title and track2 as artist Beethoven)
        vm.onQueryChange("beethoven")
        testScheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.filteredTracks.size)

        // Match by album ("bergamasque")
        vm.onQueryChange("bergamasque")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(track3), vm.uiState.value.filteredTracks)

        // Clear query -> empty filteredTracks
        vm.clearQuery()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.filteredTracks.isEmpty())
    }

    @Test
    fun `query with no matches returns empty filteredTracks`() = runTest(dispatcher) {
        val vm = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        vm.onQueryChange("xyz123no_match")
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.filteredTracks.isEmpty())
    }

    @Test
    fun `sorting updates filtered tracks order`() = runTest(dispatcher) {
        val vm = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        // Match all tracks by matching "e"
        vm.onQueryChange("e")
        testScheduler.advanceUntilIdle()
        assertEquals(3, vm.uiState.value.filteredTracks.size)

        // Sort by Artist
        vm.onSortSelected(TrackSortField.ARTIST)
        testScheduler.advanceUntilIdle()
        assertEquals(TrackSortField.ARTIST, vm.uiState.value.sort.field)
        val artists = vm.uiState.value.filteredTracks.map { it.artistName }
        assertEquals(listOf("Beethoven", "Debussy", "Ludwig"), artists)
    }

    @Test
    fun `selection mode and multi-select work correctly`() = runTest(dispatcher) {
        val vm = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        vm.onQueryChange("e")
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isSelectionMode)

        // Enter selection
        vm.enterSelection(track1.id)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isSelectionMode)
        assertEquals(setOf(track1.id), vm.uiState.value.selectedTrackIds)

        // Toggle selection for track2
        vm.toggleSelection(track2.id)
        testScheduler.advanceUntilIdle()
        assertEquals(setOf(track1.id, track2.id), vm.uiState.value.selectedTrackIds)

        // Select all
        vm.toggleSelectAll()
        testScheduler.advanceUntilIdle()
        assertEquals(3, vm.uiState.value.selectedTrackIds.size)

        // Clear selection
        vm.clearSelection()
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isSelectionMode)
        assertTrue(vm.uiState.value.selectedTrackIds.isEmpty())
    }

    @Test
    fun `playTrack and playAll use filteredTracks as playback context`() = runTest(dispatcher) {
        val vm = createViewModel()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        vm.onQueryChange("sonata")
        testScheduler.advanceUntilIdle()

        vm.playTrack(track2)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(track2.id), fakePlaybackController.lastContext?.orderedTrackIds)
        assertEquals(track2.id, fakePlaybackController.lastContext?.selectedTrackId)

        vm.onQueryChange("e")
        testScheduler.advanceUntilIdle()

        vm.playAll()
        testScheduler.advanceUntilIdle()

        assertEquals(3, fakePlaybackController.lastContext?.orderedTrackIds?.size)
        assertEquals(track1.id, fakePlaybackController.lastContext?.selectedTrackId)
    }

    @Test
    fun `playlist scope only searches within the playlist`() = runTest(dispatcher) {
        val playlistRepo = FakePlaylistRepository(existingTrackIds = setOf(track1.id, track2.id))
        val playlistId = playlistRepo.createPlaylist("Favorites", "favorites", 1000L)
        playlistRepo.addTracks(playlistId, listOf(track1.id), 1000L)

        val vm = createViewModel(playlistRepository = playlistRepo)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        vm.initScope(SearchScopeType.PLAYLIST, playlistId.value)
        testScheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.totalCandidateCount)

        // track2 is not in this playlist
        vm.onQueryChange("sonata")
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.filteredTracks.isEmpty())

        // track1 is in this playlist
        vm.onQueryChange("symphony")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(track1), vm.uiState.value.filteredTracks)
    }
}
