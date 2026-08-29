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
    val artworkByArtistId: Map<ArtistId, ArtistArtworkState> = emptyMap(),
)

data class ArtistArtworkState(
    val signature: ArtistArtworkSignature,
    val artwork: ArtworkResult,
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
    private val artworkRepository: ArtworkRepository,
) : ViewModel() {
    private val artworkByArtistId = MutableStateFlow<Map<ArtistId, ArtistArtworkState>>(emptyMap())
    private val artworkRequests = mutableMapOf<ArtistId, ArtworkRequest>()
    private val artists = mediaLibraryRepository.observeTracks().map(ArtistGrouping::group)

    val uiState: StateFlow<ArtistsUiState> =
        combine(artists, artworkByArtistId) { currentArtists, artwork ->
            val visibleArtistIds = currentArtists.mapTo(hashSetOf(), ArtistSummary::id)
            ArtistsUiState(
                artists = currentArtists,
                artworkByArtistId = artwork.filterKeys { it in visibleArtistIds },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ArtistsUiState(),
        )

    fun requestArtwork(artist: ArtistSummary) {
        val candidates = artist.sortedArtworkCandidates()
        val signature = candidates.artworkSignature()
        val current = artworkByArtistId.value[artist.id]
        if (current?.signature == signature) return

        artworkRequests.remove(artist.id)?.job?.cancel()
        val request = ArtworkRequest()
        artworkRequests[artist.id] = request
        artworkByArtistId.update { cached ->
            cached +
                (
                    artist.id to
                        ArtistArtworkState(
                            signature = signature,
                            artwork = ArtworkResult.Placeholder,
                        )
                    )
        }

        if (candidates.isEmpty()) {
            artworkRequests.remove(artist.id)
            return
        }

        request.job = viewModelScope.launch {
            try {
                val artwork = loadFirstEmbedded(candidates)
                artworkByArtistId.update { cached ->
                    val state = cached[artist.id]
                    if (state?.signature == signature) {
                        cached + (artist.id to state.copy(artwork = artwork))
                    } else {
                        cached
                    }
                }
            } finally {
                if (artworkRequests[artist.id] === request) {
                    artworkRequests.remove(artist.id)
                }
            }
        }
    }

    private suspend fun loadFirstEmbedded(candidates: List<Track>): ArtworkResult {
        candidates.forEach { candidate ->
            currentCoroutineContext().ensureActive()
            val result =
                try {
                    artworkRepository.artwork(candidate, ARTIST_ARTWORK_TARGET_PX)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    ArtworkResult.Placeholder
                }
            if (result is ArtworkResult.Embedded) return result
        }
        return ArtworkResult.Placeholder
    }

    private class ArtworkRequest(var job: Job? = null)
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
