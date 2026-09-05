package com.musicapp.player.feature.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import com.musicapp.player.feature.playlists.PlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArtistsUiState(
    val artists: List<ArtistSummary> = emptyList(),
    val isLoaded: Boolean = false,
) {
    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in M3.")
    val artworkByArtistId: Map<ArtistId, ArtistArtworkState> get() = emptyMap()
}

data class ArtistArtworkState(
    val signature: ArtistArtworkSignature = emptyList(),
    val artwork: ArtworkResult = ArtworkResult.Placeholder,
) {
    fun matches(artist: ArtistSummary): Boolean = signature == artist.artworkSignature()
}

data class ArtistDetailUiState(
    val artistId: ArtistId? = null,
    val displayName: String? = null,
    val representativeTrack: Track? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<ArtistAlbumSummary> = emptyList(),
    val sort: CategoryTrackSort = CategoryTrackSort(field = CategoryTrackSortField.ALBUM),
    val playlists: List<Playlist> = emptyList(),
    val isLoaded: Boolean = false,
    val isUnavailable: Boolean = false,
    val infoTrack: Track? = null,
    val infoMetadata: AdvancedTrackMetadata? = null,
    val isInfoLoading: Boolean = false,
)

@HiltViewModel
class ArtistsViewModel internal constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val computationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
    ) : this(mediaLibraryRepository, Dispatchers.Default)

    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        artworkRepository: ArtworkRepository,
    ) : this(mediaLibraryRepository, Dispatchers.Default)

    private val artists =
        mediaLibraryRepository.observeTracks()
            .map(ArtistGrouping::group)
            .flowOn(computationDispatcher)

    val uiState: StateFlow<ArtistsUiState> =
        artists.map { ArtistsUiState(artists = it, isLoaded = true) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                ArtistsUiState(isLoaded = false),
            )

    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in Composable")
    fun requestArtwork(artist: ArtistSummary) {
        // No-op: artwork is loaded directly by Coil AsyncImage in Composable
    }
}

@HiltViewModel
class ArtistDetailViewModel internal constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
    private val playlistRepository: PlaylistRepository,
    private val playlistUseCase: PlaylistUseCase,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val computationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playbackController: PlaybackControllerFacade,
        playlistRepository: PlaylistRepository,
        playlistUseCase: PlaylistUseCase,
        trackMetadataRepository: TrackMetadataRepository,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playbackController = playbackController,
        playlistRepository = playlistRepository,
        playlistUseCase = playlistUseCase,
        trackMetadataRepository = trackMetadataRepository,
        computationDispatcher = Dispatchers.Default,
    )

    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playbackController: PlaybackControllerFacade,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playbackController = playbackController,
        playlistRepository = FakePlaylistRepository(),
        playlistUseCase = PlaylistUseCase(
            FakePlaylistRepository(),
            Clock { System.currentTimeMillis() },
        ),
        trackMetadataRepository = DefaultArtistTrackMetadataRepository,
        computationDispatcher = Dispatchers.Unconfined,
    )

    private val selectedArtistId = MutableStateFlow<ArtistId?>(null)
    private val sort = MutableStateFlow(CategoryTrackSort(field = CategoryTrackSortField.ALBUM))
    private val infoTrack = MutableStateFlow<Track?>(null)
    private val infoMetadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val isInfoLoading = MutableStateFlow(false)
    private var infoJob: Job? = null

    private val playlists =
        playlistRepository.observePlaylists()
            .onStart { emit(emptyList()) }
            .catch { emit(emptyList()) }

    private val baseUiState = combine(
        mediaLibraryRepository.observeTracks(),
        selectedArtistId,
        sort,
        playlists,
    ) { tracks, artistId, currentSort, currentPlaylists ->
            val matching = artistId?.let { selected ->
                tracks.filter { track -> ArtistGrouping.matches(track.artistName, selected) }
            }.orEmpty()
            val targetKey = artistId?.let { ArtistGrouping.normalizedKey(it.name) }
            val displayName = matching.asSequence()
                .flatMap { ArtistGrouping.splitArtistNames(it.artistName).asSequence() }
                .firstOrNull { ArtistGrouping.normalizedKey(it) == targetKey }
                ?: artistId?.name
            val sortedTracks = sortCategoryTracks(matching, currentSort)
            ArtistDetailUiState(
                artistId = artistId,
                displayName = displayName,
                representativeTrack = matching.minWithOrNull(trackIdentityComparator),
                tracks = sortedTracks,
                albums = ArtistGrouping.groupAlbumsForArtist(tracks, matching),
                sort = currentSort,
                playlists = currentPlaylists,
                isLoaded = artistId != null,
                isUnavailable = artistId != null && tracks.isNotEmpty() && matching.isEmpty(),
            )
        }

    val uiState: StateFlow<ArtistDetailUiState> = combine(
        baseUiState,
        infoTrack,
        infoMetadata,
        isInfoLoading,
    ) { base, currentInfoTrack, currentInfoMetadata, currentInfoLoading ->
        base.copy(
            infoTrack = currentInfoTrack,
            infoMetadata = currentInfoMetadata,
            isInfoLoading = currentInfoLoading,
        )
    }
        .flowOn(computationDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArtistDetailUiState())

    fun open(artistId: ArtistId) {
        if (selectedArtistId.value != artistId) {
            selectedArtistId.value = artistId
        }
    }

    fun selectSort(field: CategoryTrackSortField) {
        sort.value = sort.value.next(field)
    }

    fun playAll() = play(selectedTrackId = null)

    fun playTrack(trackId: TrackId) = play(selectedTrackId = trackId)

    fun addToQueue(trackId: TrackId) {
        playbackController.addToQueue(listOf(trackId))
    }

    fun playNext(trackId: TrackId) {
        playbackController.playNext(listOf(trackId))
    }

    fun hideTrack(trackId: TrackId) {
        viewModelScope.launch {
            try {
                mediaLibraryRepository.setHidden(
                    trackIds = listOf(trackId),
                    hidden = true,
                    changedAtMs = System.currentTimeMillis(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Keep the page usable when the library changes underneath the menu.
            }
        }
    }

    fun addToPlaylist(trackId: TrackId, playlistId: com.musicapp.player.core.domain.model.PlaylistId) {
        viewModelScope.launch {
            try {
                playlistUseCase.addTracks(playlistId, listOf(trackId))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The shared menu has no result surface; the repository remains authoritative.
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistUseCase.create(name)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        }
    }

    fun showTrackInfo(track: Track) {
        infoJob?.cancel()
        infoTrack.value = track
        infoMetadata.value = null
        isInfoLoading.value = true
        infoJob = viewModelScope.launch {
            try {
                val metadata = trackMetadataRepository.read(track)
                if (infoTrack.value?.id == track.id) {
                    infoMetadata.value = metadata
                    isInfoLoading.value = false
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (infoTrack.value?.id == track.id) {
                    isInfoLoading.value = false
                }
            }
        }
    }

    fun dismissTrackInfo() {
        infoJob?.cancel()
        infoTrack.value = null
        infoMetadata.value = null
        isInfoLoading.value = false
    }

    private fun play(selectedTrackId: TrackId?) {
        val state = uiState.value
        val artistId = state.artistId ?: return
        CategoryPlaybackContextFactory.create(
            source = PlaybackContextSource.ARTIST,
            sourceId = artistId.name,
            tracks = state.tracks,
            selectedTrackId = selectedTrackId,
        )?.let(playbackController::play)
    }
}

private object DefaultArtistTrackMetadataRepository : TrackMetadataRepository {
    override suspend fun read(track: Track): AdvancedTrackMetadata =
        AdvancedTrackMetadata(
            encoding = null,
            bitrateBps = null,
            sampleRateHz = null,
            fileSizeBytes = track.sizeBytes,
            isReadable = true,
        )
}

private val trackIdentityComparator =
    compareBy<Track>({ it.id.volumeName }, { it.id.mediaStoreId })

private const val STOP_TIMEOUT_MS = 5_000L
