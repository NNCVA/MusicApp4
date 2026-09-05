package com.musicapp.player.feature.artists

import androidx.lifecycle.SavedStateHandle
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.data.repository.FakeMediaLibraryRepository
import com.musicapp.player.feature.category.CategorySortDirection
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistsViewModelTest {
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
    fun `artist grouping creates distinct artists with track counts`() = runTest(dispatcher) {
        val first = track(1, dateModifiedMs = 10, artistName = "Jay Chou")
        val second = track(2, dateModifiedMs = 20, artistName = "Jay Chou")
        val third = track(3, dateModifiedMs = 30, artistName = "Eason Chan")
        val viewModel = ArtistsViewModel(FakeMediaLibraryRepository(listOf(first, second, third)), computationDispatcher = dispatcher)
        collectState(viewModel)
        advanceUntilIdle()

        val artists = viewModel.uiState.value.artists
        assertEquals(2, artists.size)
        val jay = artists.first { it.id == ArtistId("jay chou") }
        assertEquals("Jay Chou", jay.displayName)
        assertEquals(2, jay.trackCount)
        val eason = artists.first { it.id == ArtistId("eason chan") }
        assertEquals("Eason Chan", eason.displayName)
        assertEquals(1, eason.trackCount)
    }

    @Test
    fun `artists uiState is immediately loaded and exposes stable summaries`() = runTest(dispatcher) {
        val track = track(1, dateModifiedMs = 10, artistName = "Solo Artist")
        val viewModel = ArtistsViewModel(FakeMediaLibraryRepository(listOf(track)), computationDispatcher = dispatcher)
        collectState(viewModel)
        advanceUntilIdle()

        val artist = viewModel.uiState.value.artists.single()
        assertEquals(ArtistId("solo artist"), artist.id)
        assertEquals("Solo Artist", artist.displayName)
        assertEquals(1, artist.trackCount)
    }

    @Test
    fun `artists default sort is name ascending`() = runTest(dispatcher) {
        val t1 = track(1, dateModifiedMs = 10, artistName = "Zulu")
        val t2 = track(2, dateModifiedMs = 20, artistName = "Alpha")
        val t3 = track(3, dateModifiedMs = 30, artistName = "Beta")
        val viewModel = ArtistsViewModel(FakeMediaLibraryRepository(listOf(t1, t2, t3)), computationDispatcher = dispatcher)
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals(ArtistSort(ArtistSortField.NAME, CategorySortDirection.ASCENDING), viewModel.uiState.value.sort)
        assertEquals(listOf("Alpha", "Beta", "Zulu"), viewModel.uiState.value.artists.map { it.displayName })
    }

    @Test
    fun `selectSort with TRACK_COUNT sorts by track count descending with name ascending tie breaker`() = runTest(dispatcher) {
        val a1 = track(1, dateModifiedMs = 10, artistName = "Zulu")
        val a2 = track(2, dateModifiedMs = 20, artistName = "Zulu")
        val b1 = track(3, dateModifiedMs = 30, artistName = "Alpha")
        val c1 = track(4, dateModifiedMs = 40, artistName = "Beta")
        val c2 = track(5, dateModifiedMs = 50, artistName = "Beta")
        val viewModel = ArtistsViewModel(FakeMediaLibraryRepository(listOf(a1, a2, b1, c1, c2)), computationDispatcher = dispatcher)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.selectSort(ArtistSortField.TRACK_COUNT)
        advanceUntilIdle()

        assertEquals(ArtistSort(ArtistSortField.TRACK_COUNT, CategorySortDirection.DESCENDING), viewModel.uiState.value.sort)
        assertEquals(listOf("Beta", "Zulu", "Alpha"), viewModel.uiState.value.artists.map { it.displayName })
    }

    @Test
    fun `selectSort with TRACK_COUNT twice toggles to ascending`() = runTest(dispatcher) {
        val a1 = track(1, dateModifiedMs = 10, artistName = "Zulu")
        val a2 = track(2, dateModifiedMs = 20, artistName = "Zulu")
        val b1 = track(3, dateModifiedMs = 30, artistName = "Alpha")
        val viewModel = ArtistsViewModel(FakeMediaLibraryRepository(listOf(a1, a2, b1)), computationDispatcher = dispatcher)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.selectSort(ArtistSortField.TRACK_COUNT)
        advanceUntilIdle()
        viewModel.selectSort(ArtistSortField.TRACK_COUNT)
        advanceUntilIdle()

        assertEquals(ArtistSort(ArtistSortField.TRACK_COUNT, CategorySortDirection.ASCENDING), viewModel.uiState.value.sort)
        assertEquals(listOf("Alpha", "Zulu"), viewModel.uiState.value.artists.map { it.displayName })
    }

    @Test
    fun `selectSort with NAME toggles between ascending and descending`() = runTest(dispatcher) {
        val t1 = track(1, dateModifiedMs = 10, artistName = "Alpha")
        val t2 = track(2, dateModifiedMs = 20, artistName = "Beta")
        val viewModel = ArtistsViewModel(FakeMediaLibraryRepository(listOf(t1, t2)), computationDispatcher = dispatcher)
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.selectSort(ArtistSortField.NAME)
        advanceUntilIdle()

        assertEquals(ArtistSort(ArtistSortField.NAME, CategorySortDirection.DESCENDING), viewModel.uiState.value.sort)
        assertEquals(listOf("Beta", "Alpha"), viewModel.uiState.value.artists.map { it.displayName })
    }

    @Test
    fun `artists sort restored from savedStateHandle correctly`() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                "artists.sort.field" to ArtistSortField.TRACK_COUNT.name,
                "artists.sort.direction" to CategorySortDirection.ASCENDING.name,
            )
        )
        val t1 = track(1, dateModifiedMs = 10, artistName = "Zulu")
        val t2 = track(2, dateModifiedMs = 20, artistName = "Zulu")
        val t3 = track(3, dateModifiedMs = 30, artistName = "Alpha")
        val viewModel = ArtistsViewModel(
            mediaLibraryRepository = FakeMediaLibraryRepository(listOf(t1, t2, t3)),
            savedStateHandle = handle,
            computationDispatcher = dispatcher,
        )
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals(ArtistSort(ArtistSortField.TRACK_COUNT, CategorySortDirection.ASCENDING), viewModel.uiState.value.sort)
        assertEquals(listOf("Alpha", "Zulu"), viewModel.uiState.value.artists.map { it.displayName })
    }

    @Test
    fun `artist detail matches tracks for individual artists and preserves display name`() =
        runTest(dispatcher) {
            val t1 = track(1, dateModifiedMs = 10, artistName = "周杰伦")
            val t2 = track(2, dateModifiedMs = 20, artistName = "周杰伦/王力宏")
            val t3 = track(3, dateModifiedMs = 30, artistName = "王力宏")
            val fakeRepo = FakeMediaLibraryRepository(listOf(t1, t2, t3))
            val detailVm = ArtistDetailViewModel(
                mediaLibraryRepository = fakeRepo,
                playbackController = NoOpPlaybackController(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detailVm.uiState.collect {} }
            detailVm.open(ArtistId("周杰伦"))
            advanceUntilIdle()

            val state = detailVm.uiState.value
            assertEquals("周杰伦", state.displayName)
            assertEquals(listOf(t1.id, t2.id), state.tracks.map { it.id })

            detailVm.open(ArtistId("王力宏"))
            advanceUntilIdle()
            val leehomState = detailVm.uiState.value
            assertEquals("王力宏", leehomState.displayName)
            assertEquals(listOf(t2.id, t3.id), leehomState.tracks.map { it.id })
        }

    @Test
    fun `artist detail preserves individual name casing and includes collaborative tracks`() =
        runTest(dispatcher) {
            val track = track(1, dateModifiedMs = 10, artistName = "AC/DC feat. Queen")
            val fakeRepo = FakeMediaLibraryRepository(listOf(track))
            val detailVm = ArtistDetailViewModel(
                mediaLibraryRepository = fakeRepo,
                playbackController = NoOpPlaybackController(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { detailVm.uiState.collect {} }
            detailVm.open(ArtistId("queen"))
            advanceUntilIdle()

            val state = detailVm.uiState.value
            assertEquals("Queen", state.displayName)
            assertEquals(listOf(track.id), state.tracks.map { it.id })
        }

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: ArtistsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        testScheduler.runCurrent()
    }

    private fun subject(
        tracks: List<Track>,
        artworkRepository: ArtworkRepository,
    ) = ArtistsViewModel(
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        computationDispatcher = dispatcher,
    )

    private fun artist(track: Track) =
        ArtistSummary(
            id = ArtistId(track.artistName.lowercase()),
            displayName = track.artistName,
            trackCount = 1,
            artworkCandidates = listOf(track),
        )

    private fun track(id: Long, dateModifiedMs: Long, artistName: String = "Artist") =
        Track(
            id = TrackId("external", id),
            title = "Track $id",
            artistName = artistName,
            artistMediaStoreId = id,
            durationMs = 1_000,
            dateAddedMs = id,
            dateModifiedMs = dateModifiedMs,
            relativePath = "Music/",
            displayName = "$id.mp3",
        )

    private fun image(color: Int = 0xFF0000FF.toInt()) =
        ArtworkImage(width = 1, height = 1, argbPixels = intArrayOf(color))
}

private class NoOpPlaybackController : com.musicapp.player.core.playback.PlaybackControllerFacade {
    override val state: kotlinx.coroutines.flow.StateFlow<com.musicapp.player.core.playback.PlaybackControllerState> =
        kotlinx.coroutines.flow.MutableStateFlow(com.musicapp.player.core.playback.PlaybackControllerState())

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun play(context: com.musicapp.player.core.domain.model.PlaybackContext) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun skipToPrevious() = Unit
    override fun skipToNext() = Unit
    override fun seekTo(positionMs: Long) = Unit
}

private data class ArtworkRequest(val trackId: TrackId, val targetPx: Int)

private class RecordingArtworkRepository(
    private val outcomes: Map<TrackId, Any> = emptyMap(),
) : ArtworkRepository {
    val requests = mutableListOf<ArtworkRequest>()

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requests += ArtworkRequest(track.id, targetPx)
        return when (val outcome = outcomes[track.id]) {
            is Throwable -> throw outcome
            is ArtworkResult -> outcome
            else -> ArtworkResult.Placeholder
        }
    }
}

private class SuspendingArtworkRepository(
    private val suspendedTrackId: TrackId,
    private val result: CompletableDeferred<ArtworkResult>,
) : ArtworkRepository {
    val requests = mutableListOf<ArtworkRequest>()

    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        requests += ArtworkRequest(track.id, targetPx)
        return if (track.id == suspendedTrackId) {
            result.await()
        } else {
            ArtworkResult.Embedded(ArtworkImage(1, 1, intArrayOf(0xFF0000FF.toInt())))
        }
    }
}
