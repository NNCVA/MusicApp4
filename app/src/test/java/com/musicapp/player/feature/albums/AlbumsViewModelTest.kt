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
    fun `grouped album keeps stable representative and artwork request is cached`() = runTest(dispatcher) {
        val tracks = listOf(
            track(id = 2, dateModifiedMs = 20),
            track(id = 1, dateModifiedMs = 10),
        )
        val artworkRepository = RecordingArtworkRepository()
        val viewModel = subject(tracks, artworkRepository = artworkRepository)
        val collection = collectState(viewModel)
        advanceUntilIdle()

        val album = viewModel.uiState.value.albums.single()
        assertEquals(1L, album.representativeTrack.id.mediaStoreId)

        viewModel.requestArtwork(album)
        viewModel.requestArtwork(album)
        advanceUntilIdle()

        assertEquals(1, artworkRepository.requests.size)
        assertEquals(512, artworkRepository.requests.single().targetPx)
        assertSame(ArtworkResult.Placeholder, viewModel.uiState.value.artworkByAlbumId.getValue(album.id).artwork)
        collection.cancel()
    }

    @Test
    fun `artwork failure is cached as placeholder`() = runTest(dispatcher) {
        val artworkRepository = RecordingArtworkRepository(failure = IllegalStateException("decode failed"))
        val viewModel = subject(artworkRepository = artworkRepository)
        val collection = collectState(viewModel)
        advanceUntilIdle()
        val album = viewModel.uiState.value.albums.single()

        viewModel.requestArtwork(album)
        advanceUntilIdle()
        viewModel.requestArtwork(album)
        advanceUntilIdle()

        assertEquals(1, artworkRepository.requests.size)
        assertSame(ArtworkResult.Placeholder, viewModel.uiState.value.artworkByAlbumId.getValue(album.id).artwork)
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

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: AlbumsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    private fun subject(
        tracks: List<Track> = listOf(track(id = 1, dateModifiedMs = 10)),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        artworkRepository: ArtworkRepository = RecordingArtworkRepository(),
        settingsRepository: com.musicapp.player.data.settings.SettingsRepository = com.musicapp.player.data.repository.FakeSettingsRepository(),
    ) = AlbumsViewModel(
        mediaLibraryRepository = FakeMediaLibraryRepository(tracks),
        savedStateHandle = savedStateHandle,
        artworkRepository = artworkRepository,
        settingsRepository = settingsRepository,
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
