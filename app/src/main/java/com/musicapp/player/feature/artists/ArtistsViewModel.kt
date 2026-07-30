package com.musicapp.player.feature.artists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.ArtistId
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ArtistsUiState(
    val artists: List<ArtistSummary> = emptyList(),
    val sort: ArtistSort = ArtistSort(),
)

data class ArtistDetailUiState(
    val artistId: ArtistId? = null,
    val displayName: String? = null,
    val tracks: List<Track> = emptyList(),
    val sort: CategoryTrackSort = CategoryTrackSort(field = CategoryTrackSortField.ALBUM),
)

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sort = MutableStateFlow(restoreArtistSort(savedStateHandle))

    val uiState: StateFlow<ArtistsUiState> =
        combine(mediaLibraryRepository.observeTracks(), sort) { tracks, currentSort ->
            ArtistsUiState(
                artists = ArtistGrouping.sorted(ArtistGrouping.group(tracks), currentSort),
                sort = currentSort,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ArtistsUiState(sort = sort.value))

    fun selectSort(field: ArtistSortField) {
        sort.value = sort.value.next(field)
        savedStateHandle[ARTIST_SORT_FIELD_KEY] = sort.value.field.name
        savedStateHandle[ARTIST_SORT_DIRECTION_KEY] = sort.value.direction.name
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
            val matching = artistId?.let { selected -> tracks.filter { it.artistId == selected } }.orEmpty()
            ArtistDetailUiState(
                artistId = artistId,
                displayName = matching.firstOrNull()?.artistName,
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
            sourceId = artistId.mediaStoreId.toString(),
            tracks = state.tracks,
            selectedTrackId = selectedTrackId,
        )?.let(playbackController::play)
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
private const val ARTIST_SORT_FIELD_KEY = "artists.sort.field"
private const val ARTIST_SORT_DIRECTION_KEY = "artists.sort.direction"

private fun restoreArtistSort(handle: SavedStateHandle): ArtistSort {
    val field = handle.get<String>(ARTIST_SORT_FIELD_KEY)?.let { stored ->
        ArtistSortField.entries.firstOrNull { it.name == stored }
    } ?: ArtistSortField.NAME
    val direction = handle.get<String>(ARTIST_SORT_DIRECTION_KEY)?.let { stored ->
        CategorySortDirection.entries.firstOrNull { it.name == stored }
    } ?: if (field == ArtistSortField.NAME) CategorySortDirection.ASCENDING else CategorySortDirection.DESCENDING
    return ArtistSort(field, direction)
}
