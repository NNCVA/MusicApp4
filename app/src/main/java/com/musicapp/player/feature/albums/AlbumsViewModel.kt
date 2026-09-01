package com.musicapp.player.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DEFAULT_ALBUM_GRID_COLUMNS = 2

data class AlbumsUiState(
    val albums: List<AlbumSummary> = emptyList(),
    val sort: AlbumSort = AlbumSort(),
    val columnCount: Int = DEFAULT_ALBUM_GRID_COLUMNS,
    val isLoaded: Boolean = false,
) {
    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in M3.")
    val artworkByAlbumId: Map<AlbumId, AlbumArtworkState> get() = emptyMap()
}

data class AlbumDetailUiState(
    val albumId: AlbumId? = null,
    val title: String? = null,
    val tracks: List<Track> = emptyList(),
    val sort: CategoryTrackSort = CategoryTrackSort(),
)

data class AlbumArtworkState(
    val trackId: TrackId = TrackId("", 0L),
    val dateModifiedMs: Long = 0L,
    val artwork: ArtworkResult = ArtworkResult.Placeholder,
)

@HiltViewModel
class AlbumsViewModel internal constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val computationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        savedStateHandle: SavedStateHandle,
        settingsRepository: SettingsRepository,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        savedStateHandle = savedStateHandle,
        settingsRepository = settingsRepository,
        computationDispatcher = Dispatchers.Default,
    )

    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        savedStateHandle: SavedStateHandle,
        artworkRepository: ArtworkRepository,
        settingsRepository: SettingsRepository,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        savedStateHandle = savedStateHandle,
        settingsRepository = settingsRepository,
        computationDispatcher = Dispatchers.Default,
    )

    private val sort = MutableStateFlow(restoreAlbumSort(savedStateHandle))

    val uiState: StateFlow<AlbumsUiState> =
        combine(mediaLibraryRepository.observeTracks(), sort, settingsRepository.settings) { tracks, currentSort, settings ->
            AlbumsUiState(
                albums = AlbumGrouping.sorted(AlbumGrouping.group(tracks), currentSort),
                sort = currentSort,
                columnCount = settings.albumGridColumns,
                isLoaded = true,
            )
        }
        .flowOn(computationDispatcher)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AlbumsUiState(sort = sort.value, columnCount = settingsRepository.settings.value.albumGridColumns, isLoaded = false),
        )

    fun selectSort(field: AlbumSortField) {
        sort.value = sort.value.next(field)
        savedStateHandle[ALBUM_SORT_FIELD_KEY] = sort.value.field.name
        savedStateHandle[ALBUM_SORT_DIRECTION_KEY] = sort.value.direction.name
    }

    fun selectColumnCount(columns: Int) {
        if (columns in 2..4) {
            viewModelScope.launch {
                settingsRepository.setAlbumGridColumns(columns)
            }
        }
    }

    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in M3.")
    fun requestArtwork(album: AlbumSummary) {
        // No-op: artwork is loaded directly by Coil AsyncImage in Composable
    }
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
) : ViewModel() {
    private val selectedAlbumId = MutableStateFlow<AlbumId?>(null)
    private val sort = MutableStateFlow(CategoryTrackSort())

    val uiState: StateFlow<AlbumDetailUiState> =
        combine(mediaLibraryRepository.observeTracks(), selectedAlbumId, sort) { tracks, albumId, currentSort ->
            val matching = albumId?.let { selected -> tracks.filter { it.albumId == selected } }.orEmpty()
            AlbumDetailUiState(
                albumId = albumId,
                title = matching.firstNotNullOfOrNull(Track::albumTitle),
                tracks = sortCategoryTracks(matching, currentSort),
                sort = currentSort,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AlbumDetailUiState())

    fun open(albumId: AlbumId) {
        selectedAlbumId.value = albumId
    }

    fun selectSort(field: CategoryTrackSortField) {
        sort.value = sort.value.next(field)
    }

    fun playAll() = play(selectedTrackId = null)

    fun playTrack(trackId: TrackId) = play(selectedTrackId = trackId)

    private fun play(selectedTrackId: TrackId?) {
        val state = uiState.value
        val albumId = state.albumId ?: return
        CategoryPlaybackContextFactory.create(
            source = PlaybackContextSource.ALBUM,
            sourceId = "${albumId.volumeName}|${albumId.mediaStoreId}",
            tracks = state.tracks,
            selectedTrackId = selectedTrackId,
        )?.let(playbackController::play)
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
private const val ALBUM_ARTWORK_TARGET_PX = 512
private const val ALBUM_SORT_FIELD_KEY = "albums.sort.field"
private const val ALBUM_SORT_DIRECTION_KEY = "albums.sort.direction"

private fun restoreAlbumSort(handle: SavedStateHandle): AlbumSort {
    val field = handle.get<String>(ALBUM_SORT_FIELD_KEY)?.let { stored ->
        AlbumSortField.entries.firstOrNull { it.name == stored }
    } ?: AlbumSortField.TITLE
    val direction = handle.get<String>(ALBUM_SORT_DIRECTION_KEY)?.let { stored ->
        com.musicapp.player.feature.category.CategorySortDirection.entries.firstOrNull { it.name == stored }
    } ?: AlbumSort().next(field).let { if (field == AlbumSortField.TITLE) AlbumSort().direction else it.direction }
    return AlbumSort(field, direction)
}
