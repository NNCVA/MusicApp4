package com.musicapp.player.feature.tracks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrackSortField {
    TITLE,
    ARTIST,
    ALBUM,
    DATE_ADDED,
    DURATION,
}

enum class TrackSortDirection {
    ASCENDING,
    DESCENDING,
}

data class TrackSort(
    val field: TrackSortField,
    val direction: TrackSortDirection,
) {
    companion object {
        fun defaultFor(field: TrackSortField): TrackSort =
            TrackSort(
                field = field,
                direction =
                    when (field) {
                        TrackSortField.DATE_ADDED -> TrackSortDirection.DESCENDING
                        else -> TrackSortDirection.ASCENDING
                    },
            )
    }
}

data class TracksUiState(
    val tracks: List<Track> = emptyList(),
    val isLibraryLoaded: Boolean = false,
    val artworkByTrackId: Map<TrackId, TrackArtworkState> = emptyMap(),
    val playlists: List<Playlist> = emptyList(),
    val sort: TrackSort = TrackSort.defaultFor(TrackSortField.TITLE),
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val batchResult: BatchTrackActionResult? = null,
    val isBatchActionRunning: Boolean = false,
) {
    val isSelectionMode: Boolean
        get() = selectedTrackIds.isNotEmpty()
}

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    playlistRepository: PlaylistRepository,
    private val savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackControllerFacade,
    private val batchActionExecutor: BatchTrackActionExecutor,
    private val artworkRepository: ArtworkRepository,
) : ViewModel() {
    private val sort = MutableStateFlow(restoreSort(savedStateHandle))
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val batchResult = MutableStateFlow<BatchTrackActionResult?>(null)
    private val isBatchActionRunning = MutableStateFlow(false)
    private val artworkByTrackId = MutableStateFlow<Map<TrackId, TrackArtworkState>>(emptyMap())

    private val libraryState =
        mediaLibraryRepository.observeTracks()
            .map { tracks -> TracksLibraryState(tracks = tracks, isLoaded = true) }
            .onStart { emit(TracksLibraryState()) }

    private val playlists =
        playlistRepository.observePlaylists()
            .onStart { emit(emptyList()) }

    private val presentationState =
        combine(
            sort,
            selectedTrackIds,
            batchResult,
            isBatchActionRunning,
        ) { currentSort, selected, result, isRunning ->
            TracksPresentationState(currentSort, selected, result, isRunning)
        }

    val uiState: StateFlow<TracksUiState> =
        combine(
            libraryState,
            playlists,
            presentationState,
            artworkByTrackId,
        ) { library, playlists, presentation, artwork ->
            val tracks = library.tracks
            val sortedTracks = tracks.sortedWith(presentation.sort.comparator())
            val visibleTrackIds = tracks.mapTo(hashSetOf(), Track::id)
            TracksUiState(
                tracks = sortedTracks,
                isLibraryLoaded = library.isLoaded,
                artworkByTrackId = artwork.filterKeys { it in visibleTrackIds },
                playlists = playlists,
                sort = presentation.sort,
                selectedTrackIds =
                    presentation.selectedTrackIds.filterTo(linkedSetOf()) { it in visibleTrackIds },
                batchResult = presentation.batchResult,
                isBatchActionRunning = presentation.isBatchActionRunning,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TracksUiState(sort = sort.value),
        )

    fun selectSort(field: TrackSortField) {
        val current = sort.value
        val updated =
            if (current.field == field) {
                current.copy(
                    direction =
                        if (current.direction == TrackSortDirection.ASCENDING) {
                            TrackSortDirection.DESCENDING
                        } else {
                            TrackSortDirection.ASCENDING
                        },
                )
            } else {
                TrackSort.defaultFor(field)
            }
        sort.value = updated
        savedStateHandle[SORT_FIELD_KEY] = updated.field.name
        savedStateHandle[SORT_DIRECTION_KEY] = updated.direction.name
    }

    fun toggleSelection(trackId: TrackId) {
        val effectiveSelection = currentVisibleSelection()
        selectedTrackIds.value = LinkedHashSet(effectiveSelection).apply {
            if (!remove(trackId)) add(trackId)
        }
    }

    fun selectAllCurrentResults() {
        selectTracks(uiState.value.tracks.map(Track::id))
    }

    fun selectTracks(trackIds: Collection<TrackId>) {
        val visibleTrackIds = uiState.value.tracks.mapTo(hashSetOf(), Track::id)
        selectedTrackIds.value = trackIds.filterTo(linkedSetOf()) { it in visibleTrackIds }
    }

    fun clearSelection() {
        selectedTrackIds.value = emptySet()
    }

    fun onBack(): Boolean {
        if (currentVisibleSelection().isEmpty()) return false
        clearSelection()
        return true
    }

    fun playTrack(trackId: TrackId) {
        val orderedTrackIds =
            uiState.value.tracks
                .filter { it.availability == Availability.AVAILABLE }
                .map(Track::id)
        if (trackId !in orderedTrackIds) return
        playbackController.play(
            PlaybackContext(
                source = PlaybackContextSource.TRACKS,
                orderedTrackIds = orderedTrackIds,
                selectedTrackId = trackId,
            ),
        )
    }

    fun requestArtwork(track: Track) {
        var shouldLoad = false
        artworkByTrackId.update { cached ->
            val current = cached[track.id]
            if (current?.dateModifiedMs == track.dateModifiedMs) {
                cached
            } else {
                shouldLoad = true
                cached +
                    (
                        track.id to
                            TrackArtworkState(
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
                    artworkRepository.artwork(track, ARTWORK_TARGET_PX)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    ArtworkResult.Placeholder
                }
            artworkByTrackId.update { cached ->
                val current = cached[track.id]
                if (current?.dateModifiedMs == track.dateModifiedMs) {
                    cached + (track.id to current.copy(artwork = result))
                } else {
                    cached
                }
            }
        }
    }

    fun hideSelected() {
        executeBatch(BatchTrackAction.Hide)
    }

    fun addSelectedToPlaylist(playlistId: PlaylistId) {
        executeBatch(BatchTrackAction.AddToPlaylist(playlistId))
    }

    fun addSelectedToQueue() {
        executeBatch(BatchTrackAction.AddToQueue)
    }

    fun addTrackToQueue(trackId: TrackId) {
        executeBatch(BatchTrackAction.AddToQueue, listOf(trackId))
    }

    fun playTrackNext(trackId: TrackId) {
        executeBatch(BatchTrackAction.PlayNext, listOf(trackId))
    }

    fun hideTrack(trackId: TrackId) {
        executeBatch(BatchTrackAction.Hide, listOf(trackId))
    }

    fun addTrackToPlaylist(trackId: TrackId, playlistId: PlaylistId) {
        executeBatch(BatchTrackAction.AddToPlaylist(playlistId), listOf(trackId))
    }

    fun playSelectedNext() {
        executeBatch(BatchTrackAction.PlayNext)
    }

    fun acknowledgeBatchResult() {
        batchResult.value = null
    }

    private fun executeBatch(
        action: BatchTrackAction,
        requestedTrackIds: List<TrackId> = currentVisibleSelection().toList(),
    ) {
        if (isBatchActionRunning.value) return
        val visibleTrackIds = uiState.value.tracks.mapTo(hashSetOf(), Track::id)
        val orderedTrackIds = requestedTrackIds.filter { it in visibleTrackIds }.distinct()
        if (orderedTrackIds.isEmpty()) return
        isBatchActionRunning.value = true
        viewModelScope.launch {
            try {
                val result = batchActionExecutor.execute(action, orderedTrackIds)
                batchResult.value = result
                if (result is BatchTrackActionResult.Completed) {
                    val completedIds = orderedTrackIds.toHashSet()
                    selectedTrackIds.value =
                        selectedTrackIds.value
                            .filterNot(completedIds::contains)
                            .toCollection(linkedSetOf())
                }
            } finally {
                isBatchActionRunning.value = false
            }
        }
    }

    private fun currentVisibleSelection(): Set<TrackId> {
        val visibleTrackIds = uiState.value.tracks.mapTo(hashSetOf(), Track::id)
        return selectedTrackIds.value.filterTo(linkedSetOf()) { it in visibleTrackIds }
    }

    private companion object {
        const val ARTWORK_TARGET_PX = 128
        const val SORT_FIELD_KEY = "tracks.sort.field"
        const val SORT_DIRECTION_KEY = "tracks.sort.direction"
        const val STOP_TIMEOUT_MS = 5_000L

        fun restoreSort(handle: SavedStateHandle): TrackSort {
            val field = handle.get<String>(SORT_FIELD_KEY)?.let { value ->
                TrackSortField.entries.firstOrNull { it.name == value }
            } ?: TrackSortField.TITLE
            val direction = handle.get<String>(SORT_DIRECTION_KEY)?.let { value ->
                TrackSortDirection.entries.firstOrNull { it.name == value }
            } ?: TrackSort.defaultFor(field).direction
            return TrackSort(field, direction)
        }
    }
}

private data class TracksPresentationState(
    val sort: TrackSort,
    val selectedTrackIds: Set<TrackId>,
    val batchResult: BatchTrackActionResult?,
    val isBatchActionRunning: Boolean,
)

private data class TracksLibraryState(
    val tracks: List<Track> = emptyList(),
    val isLoaded: Boolean = false,
)

data class TrackArtworkState(
    val dateModifiedMs: Long,
    val artwork: ArtworkResult,
)

private fun TrackSort.comparator(): Comparator<Track> {
    val textTieBreaker =
        compareBy<Track>(
            { it.title.lowercase(Locale.ROOT) },
            { it.id.volumeName.lowercase(Locale.ROOT) },
            { it.id.mediaStoreId },
        )
    val primary =
        when (field) {
            TrackSortField.TITLE -> compareBy<Track> { it.title.lowercase(Locale.ROOT) }
            TrackSortField.ARTIST -> compareBy<Track> { it.artistName.lowercase(Locale.ROOT) }
            TrackSortField.ALBUM -> compareBy<Track> { it.albumTitle.orEmpty().lowercase(Locale.ROOT) }
            TrackSortField.DATE_ADDED -> compareBy<Track> { it.dateAddedMs }
            TrackSortField.DURATION -> compareBy<Track> { it.durationMs }
        }
    return (if (direction == TrackSortDirection.ASCENDING) primary else primary.reversed())
        .then(textTieBreaker)
}
