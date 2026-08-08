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
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumsUiState(
    val albums: List<AlbumSummary> = emptyList(),
    val sort: AlbumSort = AlbumSort(),
    val artworkByAlbumId: Map<AlbumId, AlbumArtworkState> = emptyMap(),
)

data class AlbumDetailUiState(
    val albumId: AlbumId? = null,
    val title: String? = null,
    val tracks: List<Track> = emptyList(),
    val sort: CategoryTrackSort = CategoryTrackSort(),
)

data class AlbumArtworkState(
    val trackId: TrackId,
    val dateModifiedMs: Long,
    val artwork: ArtworkResult,
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val savedStateHandle: SavedStateHandle,
    private val artworkRepository: ArtworkRepository,
) : ViewModel() {
    private val sort = MutableStateFlow(restoreAlbumSort(savedStateHandle))
    private val artworkByAlbumId = MutableStateFlow<Map<AlbumId, AlbumArtworkState>>(emptyMap())
    private val albumsAndSort =
        combine(mediaLibraryRepository.observeTracks(), sort) { tracks, currentSort ->
            AlbumsUiState(
                albums = AlbumGrouping.sorted(AlbumGrouping.group(tracks), currentSort),
                sort = currentSort,
            )
        }

    val uiState: StateFlow<AlbumsUiState> =
        combine(albumsAndSort, artworkByAlbumId) { albumState, artwork ->
            val visibleAlbumIds = albumState.albums.mapTo(hashSetOf(), AlbumSummary::id)
            albumState.copy(artworkByAlbumId = artwork.filterKeys { it in visibleAlbumIds })
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AlbumsUiState(sort = sort.value),
        )

    fun selectSort(field: AlbumSortField) {
        sort.value = sort.value.next(field)
        savedStateHandle[ALBUM_SORT_FIELD_KEY] = sort.value.field.name
        savedStateHandle[ALBUM_SORT_DIRECTION_KEY] = sort.value.direction.name
    }

    fun requestArtwork(album: AlbumSummary) {
        val track = album.representativeTrack
        var shouldLoad = false
        artworkByAlbumId.update { cached ->
            val current = cached[album.id]
            if (current?.trackId == track.id && current.dateModifiedMs == track.dateModifiedMs) {
                cached
            } else {
                shouldLoad = true
                cached +
                    (
                        album.id to
                            AlbumArtworkState(
                                trackId = track.id,
                                dateModifiedMs = track.dateModifiedMs,
                                artwork = ArtworkResult.Placeholder,
                            )
                        )
            }
        }
        if (!shouldLoad) return

        viewModelScope.launch {
            val result =
                try {
                    artworkRepository.artwork(track, ALBUM_ARTWORK_TARGET_PX)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    ArtworkResult.Placeholder
                }
            artworkByAlbumId.update { cached ->
                val current = cached[album.id]
                if (current?.trackId == track.id && current.dateModifiedMs == track.dateModifiedMs) {
                    cached + (album.id to current.copy(artwork = result))
                } else {
                    cached
                }
            }
        }
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
