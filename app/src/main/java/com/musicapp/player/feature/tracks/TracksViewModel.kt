package com.musicapp.player.feature.tracks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.data.sync.PendingLibrarySyncFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val syncState: LibrarySyncState = LibrarySyncState.Idle(hasSuccessfulScan = false),
    val sort: TrackSort = TrackSort.defaultFor(TrackSortField.TITLE),
    val selectedTrackIds: Set<TrackId> = emptySet(),
) {
    val isInitialLoading: Boolean
        get() = !syncState.hasSuccessfulScan && syncState !is LibrarySyncState.Failed

    val isRefreshing: Boolean
        get() = syncState is LibrarySyncState.Syncing && syncState.hasSuccessfulScan

    val fullScreenFailure: Boolean
        get() = syncState is LibrarySyncState.Failed && !syncState.hasSuccessfulScan

    val cachedFailure: Boolean
        get() = syncState is LibrarySyncState.Failed && syncState.hasSuccessfulScan

    val isSelectionMode: Boolean
        get() = selectedTrackIds.isNotEmpty()

    val pendingFeedback: PendingLibrarySyncFeedback?
        get() = syncState.pendingFeedback
}

@HiltViewModel
class TracksViewModel @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val syncCoordinator: TracksSyncController,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sort = MutableStateFlow(restoreSort(savedStateHandle))
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())

    val uiState: StateFlow<TracksUiState> =
        combine(
            mediaLibraryRepository.observeTracks(),
            syncCoordinator.state,
            sort,
            selectedTrackIds,
        ) { tracks, syncState, currentSort, selected ->
            TracksUiState(
                tracks = tracks.sortedWith(currentSort.comparator()),
                syncState = syncState,
                sort = currentSort,
                selectedTrackIds = selected.intersect(tracks.mapTo(mutableSetOf(), Track::id)),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TracksUiState(syncState = syncCoordinator.state.value, sort = sort.value),
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
        val effectiveSelection = uiState.value.selectedTrackIds
        selectedTrackIds.value =
            if (trackId in effectiveSelection) {
                effectiveSelection - trackId
            } else {
                effectiveSelection + trackId
            }
    }

    fun selectAllCurrentResults() {
        selectedTrackIds.value = uiState.value.tracks.mapTo(linkedSetOf(), Track::id)
    }

    fun clearSelection() {
        selectedTrackIds.value = emptySet()
    }

    fun onBack(): Boolean {
        if (uiState.value.selectedTrackIds.isEmpty()) return false
        clearSelection()
        return true
    }

    fun hideSelected() {
        val ids = uiState.value.selectedTrackIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val changedAtMs = clock.currentTimeMillis()
            ids.forEach { id ->
                mediaLibraryRepository.setHidden(id, hidden = true, changedAtMs = changedAtMs)
            }
            clearSelection()
        }
    }

    fun requestManualSync() {
        syncCoordinator.requestManualSync()
    }

    fun retrySync() {
        syncCoordinator.requestManualSync()
    }

    fun acknowledgeFeedback(eventId: Long) {
        syncCoordinator.acknowledgeFeedback(eventId)
    }

    private companion object {
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
