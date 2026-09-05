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
import com.musicapp.player.core.designsystem.component.VARIOUS_ARTISTS_SENTINEL
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    val artistName: String? = null,
    val representativeTrack: Track? = null,
    val tracks: List<AlbumTrackPresentation> = emptyList(),
    val rawTracks: List<Track> = emptyList(),
    val stats: AlbumStats = AlbumStats(0, 0L, null),
    val technicalSummary: AlbumTechnicalSummary = AlbumTechnicalSummary(null, null),
    val artists: List<AlbumArtistCredit> = emptyList(),
    val playlists: List<com.musicapp.player.core.domain.model.Playlist> = emptyList(),
    val currentPlayingTrackId: TrackId? = null,
    val isLoaded: Boolean = false,
    val isUnavailable: Boolean = false,
    val infoTrack: Track? = null,
    val infoMetadata: com.musicapp.player.core.metadata.AdvancedTrackMetadata? = null,
)

data class AlbumArtworkState(
    val trackId: TrackId = TrackId("", 0L),
    val dateModifiedMs: Long = 0L,
    val artwork: ArtworkResult = ArtworkResult.Placeholder,
)

@HiltViewModel
class AlbumsViewModel internal constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    @Inject
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

    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        savedStateHandle: SavedStateHandle,
        artworkRepository: ArtworkRepository,
        settingsRepository: SettingsRepository,
        computationDispatcher: CoroutineDispatcher,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        savedStateHandle = savedStateHandle,
        settingsRepository = settingsRepository,
        computationDispatcher = computationDispatcher,
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
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
    private val trackMetadataRepository: com.musicapp.player.core.metadata.TrackMetadataRepository,
    private val playlistRepository: com.musicapp.player.data.repository.PlaylistRepository,
    private val batchActionExecutor: com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor,
    private val playlistUseCase: com.musicapp.player.feature.playlists.PlaylistUseCase,
) : ViewModel() {
    private val selectedAlbumId = MutableStateFlow<AlbumId?>(null)
    private val metadataMap = MutableStateFlow<Map<TrackId, com.musicapp.player.core.metadata.AdvancedTrackMetadata?>>(emptyMap())
    private val infoTrack = MutableStateFlow<Track?>(null)
    private val infoMetadata = MutableStateFlow<com.musicapp.player.core.metadata.AdvancedTrackMetadata?>(null)
    private var metadataJob: Job? = null
    private var infoJob: Job? = null

    private val currentPlayingTrackId =
        playbackController.state
            .map { it.currentTrackId }
            .distinctUntilChanged()

    val uiState: StateFlow<AlbumDetailUiState> =
        combine(
            mediaLibraryRepository.observeTracks(),
            selectedAlbumId,
            currentPlayingTrackId,
            metadataMap,
            playlistRepository.observePlaylists(),
            infoTrack,
            infoMetadata,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val tracks = args[0] as List<Track>
            val albumId = args[1] as? AlbumId
            val playingId = args[2] as? TrackId
            @Suppress("UNCHECKED_CAST")
            val metaMap = args[3] as Map<TrackId, com.musicapp.player.core.metadata.AdvancedTrackMetadata?>
            @Suppress("UNCHECKED_CAST")
            val playlists = args[4] as List<com.musicapp.player.core.domain.model.Playlist>
            val iTrack = args[5] as? Track
            val iMeta = args[6] as? com.musicapp.player.core.metadata.AdvancedTrackMetadata

            if (albumId == null) {
                return@combine AlbumDetailUiState()
            }

            val matching = if (albumId == UNKNOWN_ALBUM_ID) {
                tracks.filter { it.albumId == null || it.albumTitle.isNullOrBlank() }
            } else {
                tracks.filter { it.albumId == albumId }
            }
            if (matching.isEmpty()) {
                return@combine AlbumDetailUiState(
                    albumId = albumId,
                    isLoaded = true,
                    isUnavailable = true,
                    playlists = playlists,
                )
            }

            val orderedPresentations = AlbumTrackOrdering.resolveOrder(matching, playingId)
            val orderedTracks = orderedPresentations.map { it.track }
            val representative = orderedTracks.firstOrNull()
            val albumTitle = if (albumId == UNKNOWN_ALBUM_ID) {
                UNKNOWN_ALBUM_SENTINEL
            } else {
                orderedTracks.firstNotNullOfOrNull(Track::albumTitle) ?: representative?.title
            }
            val artistName = if (albumId == UNKNOWN_ALBUM_ID) {
                val distinctArtists = matching.map { it.artistName }.distinct()
                if (distinctArtists.size == 1) distinctArtists.first() else VARIOUS_ARTISTS_SENTINEL
            } else {
                orderedTracks.firstOrNull()?.artistName
            }
            val stats = AlbumDetailAggregator.aggregateStats(matching)
            val techSummary = AlbumDetailAggregator.aggregateTechnicalSummary(matching, metaMap)
            val artists = AlbumDetailAggregator.aggregateArtists(orderedTracks)

            AlbumDetailUiState(
                albumId = albumId,
                title = albumTitle,
                artistName = artistName,
                representativeTrack = representative,
                tracks = orderedPresentations,
                rawTracks = orderedTracks,
                stats = stats,
                technicalSummary = techSummary,
                artists = artists,
                playlists = playlists,
                currentPlayingTrackId = playingId,
                isLoaded = true,
                isUnavailable = false,
                infoTrack = iTrack,
                infoMetadata = iMeta,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AlbumDetailUiState(),
        )

    fun open(albumId: AlbumId) {
        if (selectedAlbumId.value == albumId) return
        selectedAlbumId.value = albumId
        loadMetadataForAlbum(albumId)
    }

    private fun loadMetadataForAlbum(albumId: AlbumId) {
        metadataJob?.cancel()
        metadataMap.value = emptyMap()
        metadataJob = viewModelScope.launch(Dispatchers.IO) {
            val allTracks = mediaLibraryRepository.observeTracks().first()
            val tracks: List<Track> = if (albumId == UNKNOWN_ALBUM_ID) {
                allTracks.filter { it.albumId == null || it.albumTitle.isNullOrBlank() }
            } else {
                allTracks.filter { it.albumId == albumId }
            }
            val semaphore = Semaphore(2)
            coroutineScope {
                tracks.forEach { track ->
                    launch {
                        semaphore.withPermit {
                            try {
                                val meta = trackMetadataRepository.read(track)
                                metadataMap.update { current -> current + (track.id to meta) }
                            } catch (_: Exception) {
                                // Gracefully ignore as per PRD
                            }
                        }
                    }
                }
            }
        }
    }

    fun playTrack(trackId: TrackId) {
        val state = uiState.value
        val albumId = state.albumId ?: return
        CategoryPlaybackContextFactory.create(
            source = PlaybackContextSource.ALBUM,
            sourceId = "${albumId.volumeName}|${albumId.mediaStoreId}",
            tracks = state.rawTracks,
            selectedTrackId = trackId,
        )?.let(playbackController::play)
    }

    fun addToQueue(trackId: TrackId) {
        viewModelScope.launch {
            batchActionExecutor.execute(com.musicapp.player.feature.tracks.batch.BatchTrackAction.AddToQueue, listOf(trackId))
        }
    }

    fun playNext(trackId: TrackId) {
        viewModelScope.launch {
            batchActionExecutor.execute(com.musicapp.player.feature.tracks.batch.BatchTrackAction.PlayNext, listOf(trackId))
        }
    }

    fun hideTrack(trackId: TrackId) {
        viewModelScope.launch {
            batchActionExecutor.execute(com.musicapp.player.feature.tracks.batch.BatchTrackAction.Hide, listOf(trackId))
        }
    }

    fun addToPlaylist(trackId: TrackId, playlistId: com.musicapp.player.core.domain.model.PlaylistId) {
        viewModelScope.launch {
            batchActionExecutor.execute(com.musicapp.player.feature.tracks.batch.BatchTrackAction.AddToPlaylist(playlistId), listOf(trackId))
        }
    }

    fun showTrackInfo(track: Track) {
        infoTrack.value = track
        infoMetadata.value = null
        infoJob?.cancel()
        infoJob = viewModelScope.launch {
            try {
                infoMetadata.value = trackMetadataRepository.read(track)
            } catch (_: Exception) {
                // Keep null on error
            }
        }
    }

    fun dismissTrackInfo() {
        infoTrack.value = null
        infoMetadata.value = null
        infoJob?.cancel()
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistUseCase.create(name)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (_: Exception) {
            }
        }
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
