package com.musicapp.player.feature.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistsUiState(
    val artists: List<ArtistSummary> = emptyList(),
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
    val tracks: List<Track> = emptyList(),
    val sort: CategoryTrackSort = CategoryTrackSort(field = CategoryTrackSortField.ALBUM),
)

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
) : ViewModel() {
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        artworkRepository: ArtworkRepository,
    ) : this(mediaLibraryRepository)

    private val artists = mediaLibraryRepository.observeTracks().map(ArtistGrouping::group)

    val uiState: StateFlow<ArtistsUiState> =
        artists.map { ArtistsUiState(artists = it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                ArtistsUiState(),
            )

    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in M3.")
    fun requestArtwork(artist: ArtistSummary) {
        // No-op: artwork is loaded directly by Coil AsyncImage in Composable
    }
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
) : ViewModel() {
    private val selectedArtistId = MutableStateFlow<ArtistId?>(null)
    private val sort = MutableStateFlow(CategoryTrackSort(field = CategoryTrackSortField.ALBUM))

    val uiState: StateFlow<ArtistDetailUiState> =
        combine(mediaLibraryRepository.observeTracks(), selectedArtistId, sort) { tracks, artistId, currentSort ->
            val matching =
                artistId?.let { selected ->
                    tracks.filter { track ->
                        ArtistGrouping.splitArtistNames(track.artistName)
                            .any { it.equals(selected.name, ignoreCase = true) }
                    }
                }.orEmpty()
            val matchedDisplayName =
                artistId?.let { selected ->
                    tracks.asSequence()
                        .flatMap { ArtistGrouping.splitArtistNames(it.artistName).asSequence() }
                        .firstOrNull { it.equals(selected.name, ignoreCase = true) }
                        ?: selected.name
                }
            ArtistDetailUiState(
                artistId = artistId,
                displayName = matchedDisplayName,
                tracks = sortCategoryTracks(matching, currentSort),
                sort = currentSort,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArtistDetailUiState())

    fun open(artistId: ArtistId) {
        selectedArtistId.value = artistId
    }

    fun selectSort(field: CategoryTrackSortField) {
        sort.value = sort.value.next(field)
    }

    fun playAll() = play(selectedTrackId = null)

    fun playTrack(trackId: TrackId) = play(selectedTrackId = trackId)

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

private const val STOP_TIMEOUT_MS = 5_000L
private const val ARTIST_ARTWORK_TARGET_PX = 128
