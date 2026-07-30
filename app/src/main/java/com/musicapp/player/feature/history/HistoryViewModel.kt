package com.musicapp.player.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.HistoryRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryEntry(
    val history: PlayHistory,
    val track: Track?,
) {
    val trackId: TrackId
        get() = history.trackId

    val isActionable: Boolean
        get() = track != null
}

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val visibleEntries: List<HistoryEntry> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val query: String = "",
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val isLoading: Boolean = true,
    val clearConfirmationVisible: Boolean = false,
    val isClearing: Boolean = false,
    val batchResult: BatchTrackActionResult? = null,
    val isBatchActionRunning: Boolean = false,
) {
    val isSelectionMode: Boolean
        get() = selectedTrackIds.isNotEmpty()

    val selectedTrackIdsInVisibleOrder: List<TrackId>
        get() = visibleEntries.mapNotNull { entry ->
            entry.trackId.takeIf { entry.isActionable && it in selectedTrackIds }
        }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    mediaLibraryRepository: MediaLibraryRepository,
    playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackControllerFacade,
    private val batchTrackActionExecutor: BatchTrackActionExecutor,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val clearConfirmationRequested = MutableStateFlow(false)
    private val isClearing = MutableStateFlow(false)
    private val batchResult = MutableStateFlow<BatchTrackActionResult?>(null)
    private val isBatchActionRunning = MutableStateFlow(false)

    private val history =
        historyRepository.observeHistory()
            .onEach { items ->
                if (items.isEmpty()) {
                    clearConfirmationRequested.value = false
                    selectedTrackIds.value = emptySet()
                }
            }

    private val content =
        combine(
            history,
            mediaLibraryRepository.observeTracks(includeHidden = true),
            playlistRepository.observePlaylists(),
        ) { histories, tracks, playlists ->
            val trackById = tracks.associateBy(Track::id)
            HistoryContent(
                entries =
                    histories
                        .sortedWith(
                            compareByDescending<PlayHistory> { it.lastPlayedAtMs }
                                .thenByDescending { it.trackId.mediaStoreId }
                                .thenBy { it.trackId.volumeName },
                        )
                        .map { item -> HistoryEntry(item, trackById[item.trackId]) },
                playlists = playlists,
            )
        }

    private val controls =
        combine(
            query,
            selectedTrackIds,
            clearConfirmationRequested,
        ) { currentQuery, selected, confirmationRequested ->
            HistoryControls(currentQuery, selected, confirmationRequested)
        }

    val uiState: StateFlow<HistoryUiState> =
        combine(
            content,
            controls,
            isClearing,
            batchResult,
            isBatchActionRunning,
        ) { content, controls, clearing, currentBatchResult, batchActionRunning ->
            val entries = content.entries
            val currentQuery = controls.query
            val normalizedQuery = currentQuery.trim().lowercase(Locale.ROOT)
            val visibleEntries =
                if (normalizedQuery.isEmpty()) {
                    entries
                } else {
                    entries.filter { entry -> entry.matches(normalizedQuery) }
                }
            val actionableIds = entries.mapNotNullTo(mutableSetOf()) { entry ->
                entry.trackId.takeIf { entry.isActionable }
            }
            HistoryUiState(
                entries = entries,
                visibleEntries = visibleEntries,
                playlists = content.playlists,
                query = currentQuery,
                selectedTrackIds = controls.selectedTrackIds.intersect(actionableIds),
                isLoading = false,
                clearConfirmationVisible = controls.clearConfirmationRequested && entries.isNotEmpty() && !clearing,
                isClearing = clearing,
                batchResult = currentBatchResult,
                isBatchActionRunning = batchActionRunning,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryUiState(),
        )

    fun setQuery(value: String) {
        if (query.value == value) return
        query.value = value
        selectedTrackIds.value = emptySet()
    }

    fun toggleSelection(trackId: TrackId) {
        val selectableIds = uiState.value.visibleEntries
            .asSequence()
            .filter(HistoryEntry::isActionable)
            .map(HistoryEntry::trackId)
            .toSet()
        if (trackId !in selectableIds) return
        val effectiveSelection = selectedTrackIds.value.filterTo(linkedSetOf()) { it in selectableIds }
        selectedTrackIds.value = LinkedHashSet(effectiveSelection).apply {
            if (!remove(trackId)) add(trackId)
        }
    }

    fun selectAllVisible() {
        selectedTrackIds.value =
            uiState.value.visibleEntries
                .filter(HistoryEntry::isActionable)
                .mapTo(linkedSetOf(), HistoryEntry::trackId)
    }

    fun clearSelection() {
        selectedTrackIds.value = emptySet()
    }

    fun playTrack(trackId: TrackId) {
        val orderedTrackIds =
            uiState.value.visibleEntries.mapNotNull { entry ->
                entry.track?.takeIf { it.availability == Availability.AVAILABLE }?.id
            }
        if (trackId !in orderedTrackIds) return
        playbackController.play(
            PlaybackContext(
                source = PlaybackContextSource.HISTORY,
                orderedTrackIds = orderedTrackIds,
                selectedTrackId = trackId,
            ),
        )
    }

    fun onBack(): Boolean {
        if (currentVisibleSelectionInOrder().isNotEmpty()) {
            clearSelection()
            return true
        }
        if (uiState.value.clearConfirmationVisible) {
            cancelClearHistory()
            return true
        }
        return false
    }

    fun requestClearHistory() {
        if (uiState.value.entries.isNotEmpty() && !uiState.value.isClearing) {
            clearConfirmationRequested.value = true
        }
    }

    fun cancelClearHistory() {
        clearConfirmationRequested.value = false
    }

    fun confirmClearHistory() {
        if (!uiState.value.clearConfirmationVisible || isClearing.value) return
        clearConfirmationRequested.value = false
        isClearing.value = true
        viewModelScope.launch {
            try {
                historyRepository.clearHistory()
            } finally {
                isClearing.value = false
            }
        }
    }

    fun executeSelected(action: BatchTrackAction) {
        if (isBatchActionRunning.value) return
        val orderedTrackIds = currentVisibleSelectionInOrder()
        isBatchActionRunning.value = true
        viewModelScope.launch {
            try {
                val result = batchTrackActionExecutor.execute(action, orderedTrackIds)
                batchResult.value = result
                if (result is BatchTrackActionResult.Completed) clearSelection()
            } finally {
                isBatchActionRunning.value = false
            }
        }
    }

    fun acknowledgeBatchResult() {
        batchResult.value = null
    }

    private fun currentVisibleSelectionInOrder(): List<TrackId> {
        val selected = selectedTrackIds.value
        return uiState.value.visibleEntries.mapNotNull { entry ->
            entry.trackId.takeIf { entry.isActionable && it in selected }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private data class HistoryControls(
    val query: String,
    val selectedTrackIds: Set<TrackId>,
    val clearConfirmationRequested: Boolean,
)

private data class HistoryContent(
    val entries: List<HistoryEntry>,
    val playlists: List<Playlist>,
)

private fun HistoryEntry.matches(normalizedQuery: String): Boolean {
    val searchableValues =
        if (track == null) {
            listOf(trackId.volumeName, trackId.mediaStoreId.toString())
        } else {
            listOf(track.title, track.artistName, track.albumTitle.orEmpty(), track.displayName)
        }
    return searchableValues.any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
}
