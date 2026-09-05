package com.musicapp.player.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.PlayHistory
import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.HistoryRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.feature.playlists.PlaylistUseCase
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
        get() = track != null && track.availability == Availability.AVAILABLE
}

sealed interface HistoryUserMessage {
    data class DeleteSuccess(val count: Int) : HistoryUserMessage
    data object DeleteFailed : HistoryUserMessage
}

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val visibleEntries: List<HistoryEntry> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val query: String = "",
    val isSearchActive: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val isLoading: Boolean = true,
    val clearConfirmationVisible: Boolean = false,
    val deleteConfirmationTrackIds: Set<TrackId>? = null,
    val isClearing: Boolean = false,
    val isDeleting: Boolean = false,
    val batchResult: BatchTrackActionResult? = null,
    val isBatchActionRunning: Boolean = false,
    val userMessage: HistoryUserMessage? = null,
    val infoTrack: Track? = null,
    val infoMetadata: AdvancedTrackMetadata? = null,
    val isInfoLoading: Boolean = false,
) {
    val selectedTrackIdsInVisibleOrder: List<TrackId>
        get() = visibleEntries.mapNotNull { entry ->
            entry.trackId.takeIf { it in selectedTrackIds }
        }

    val actionableSelectedTrackIdsInVisibleOrder: List<TrackId>
        get() = visibleEntries.mapNotNull { entry ->
            entry.trackId.takeIf { entry.isActionable && it in selectedTrackIds }
        }

    val hasPlayableSelection: Boolean
        get() = visibleEntries.any { it.isActionable && it.trackId in selectedTrackIds }

    val isAllSelected: Boolean
        get() = visibleEntries.isNotEmpty() && visibleEntries.all { it.trackId in selectedTrackIds }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    mediaLibraryRepository: MediaLibraryRepository,
    playlistRepository: PlaylistRepository,
    private val playlistUseCase: PlaylistUseCase,
    private val playbackController: PlaybackControllerFacade,
    private val batchTrackActionExecutor: BatchTrackActionExecutor,
    private val trackMetadataRepository: TrackMetadataRepository,
) : ViewModel() {

    internal constructor(
        historyRepository: HistoryRepository,
        mediaLibraryRepository: MediaLibraryRepository,
        playlistRepository: PlaylistRepository,
        playbackController: PlaybackControllerFacade,
        batchTrackActionExecutor: BatchTrackActionExecutor,
        trackMetadataRepository: TrackMetadataRepository = DefaultTrackMetadataRepository(),
    ) : this(
        historyRepository = historyRepository,
        mediaLibraryRepository = mediaLibraryRepository,
        playlistRepository = playlistRepository,
        playlistUseCase = PlaylistUseCase(playlistRepository, Clock { System.currentTimeMillis() }),
        playbackController = playbackController,
        batchTrackActionExecutor = batchTrackActionExecutor,
        trackMetadataRepository = trackMetadataRepository,
    )

    private val query = MutableStateFlow("")
    private val isSearchActive = MutableStateFlow(false)
    private val isSelectionMode = MutableStateFlow(false)
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val clearConfirmationRequested = MutableStateFlow(false)
    private val deleteConfirmationTrackIds = MutableStateFlow<Set<TrackId>?>(null)
    private val isClearing = MutableStateFlow(false)
    private val isDeleting = MutableStateFlow(false)
    private val batchResult = MutableStateFlow<BatchTrackActionResult?>(null)
    private val isBatchActionRunning = MutableStateFlow(false)
    private val userMessage = MutableStateFlow<HistoryUserMessage?>(null)
    private val infoTrack = MutableStateFlow<Track?>(null)
    private val infoMetadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val isInfoLoading = MutableStateFlow(false)
    private var infoJob: Job? = null

    private val history =
        historyRepository.observeHistory()
            .onEach { items ->
                if (items.isEmpty()) {
                    clearConfirmationRequested.value = false
                    deleteConfirmationTrackIds.value = null
                    selectedTrackIds.value = emptySet()
                    isSelectionMode.value = false
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

    private val presentationState =
        combine(
            query,
            isSearchActive,
            isSelectionMode,
            selectedTrackIds,
        ) { currentQuery, searchActive, selectionMode, selected ->
            HistoryPresentationState(currentQuery, searchActive, selectionMode, selected)
        }

    private val dialogsAndActionsState =
        combine(
            clearConfirmationRequested,
            deleteConfirmationTrackIds,
            isClearing,
            isDeleting,
            batchResult,
        ) { clearRequested, deleteRequestedIds, clearing, deleting, currentBatchResult ->
            HistoryDialogsAndActionsState(
                clearConfirmationRequested = clearRequested,
                deleteConfirmationTrackIds = deleteRequestedIds,
                isClearing = clearing,
                isDeleting = deleting,
                batchResult = currentBatchResult,
            )
        }

    private val infoState =
        combine(
            infoTrack,
            infoMetadata,
            isInfoLoading,
            isBatchActionRunning,
            userMessage,
        ) { track, metadata, loading, runningBatch, message ->
            HistoryInfoAndMessageState(track, metadata, loading, runningBatch, message)
        }

    val uiState: StateFlow<HistoryUiState> =
        combine(
            content,
            presentationState,
            dialogsAndActionsState,
            infoState,
        ) { content, presentation, actions, info ->
            val entries = content.entries
            val currentQuery = presentation.query
            val normalizedQuery = currentQuery.trim().lowercase(Locale.ROOT)
            val visibleEntries =
                if (normalizedQuery.isEmpty()) {
                    entries
                } else {
                    entries.filter { entry -> entry.matches(normalizedQuery) }
                }

            val validVisibleIds = visibleEntries.mapTo(hashSetOf()) { it.trackId }
            val currentSelected = presentation.selectedTrackIds.filterTo(linkedSetOf()) { it in validVisibleIds }

            HistoryUiState(
                entries = entries,
                visibleEntries = visibleEntries,
                playlists = content.playlists,
                query = currentQuery,
                isSearchActive = presentation.isSearchActive,
                isSelectionMode = presentation.isSelectionMode,
                selectedTrackIds = currentSelected,
                isLoading = false,
                clearConfirmationVisible = actions.clearConfirmationRequested && entries.isNotEmpty() && !actions.isClearing,
                deleteConfirmationTrackIds = actions.deleteConfirmationTrackIds,
                isClearing = actions.isClearing,
                isDeleting = actions.isDeleting,
                batchResult = actions.batchResult,
                isBatchActionRunning = info.isBatchActionRunning,
                userMessage = info.userMessage,
                infoTrack = info.track,
                infoMetadata = info.metadata,
                isInfoLoading = info.isInfoLoading,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryUiState(),
        )

    fun openSearch() {
        if (isSelectionMode.value) {
            exitSelectionMode()
        }
        isSearchActive.value = true
    }

    fun closeSearch() {
        isSearchActive.value = false
        query.value = ""
    }

    fun setQuery(value: String) {
        if (query.value == value) return
        query.value = value
        selectedTrackIds.value = emptySet()
    }

    fun enterSelectionMode(initialSelectedId: TrackId? = null) {
        if (isSearchActive.value) {
            closeSearch()
        }
        isSelectionMode.value = true
        if (initialSelectedId != null) {
            selectedTrackIds.value = linkedSetOf(initialSelectedId)
        }
    }

    fun exitSelectionMode() {
        isSelectionMode.value = false
        selectedTrackIds.value = emptySet()
    }

    fun clearSelection() {
        exitSelectionMode()
    }

    fun toggleSelection(trackId: TrackId) {
        if (!isSelectionMode.value) {
            enterSelectionMode(trackId)
            return
        }
        val current = LinkedHashSet(selectedTrackIds.value)
        if (!current.remove(trackId)) {
            current.add(trackId)
        }
        selectedTrackIds.value = current
    }

    fun selectAllVisible() {
        selectedTrackIds.value = uiState.value.visibleEntries.mapTo(linkedSetOf()) { it.trackId }
    }

    fun toggleSelectAll() {
        if (uiState.value.isAllSelected) {
            selectedTrackIds.value = emptySet()
        } else {
            selectAllVisible()
        }
    }

    fun playAll() {
        val playableTrackIds =
            uiState.value.visibleEntries.mapNotNull { entry ->
                entry.track?.takeIf { it.availability == Availability.AVAILABLE }?.id
            }
        if (playableTrackIds.isEmpty()) return
        playbackController.play(
            PlaybackContext(
                source = PlaybackContextSource.HISTORY,
                orderedTrackIds = playableTrackIds,
                selectedTrackId = playableTrackIds.first(),
            ),
        )
    }

    fun playTrack(trackId: TrackId) {
        if (isSelectionMode.value) {
            toggleSelection(trackId)
            return
        }
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

    fun playNext(trackId: TrackId) {
        viewModelScope.launch {
            batchTrackActionExecutor.execute(BatchTrackAction.PlayNext, listOf(trackId))
        }
    }

    fun requestDeleteTrack(trackId: TrackId) {
        if (!isDeleting.value) {
            deleteConfirmationTrackIds.value = setOf(trackId)
        }
    }

    fun requestDeleteSelected() {
        val selected = selectedTrackIds.value
        if (selected.isNotEmpty() && !isDeleting.value) {
            deleteConfirmationTrackIds.value = selected
        }
    }

    fun cancelDelete() {
        deleteConfirmationTrackIds.value = null
    }

    fun confirmDelete() {
        val idsToDelete = deleteConfirmationTrackIds.value ?: return
        if (isDeleting.value) return
        deleteConfirmationTrackIds.value = null
        isDeleting.value = true
        viewModelScope.launch {
            try {
                historyRepository.deleteHistory(idsToDelete)
                selectedTrackIds.value = selectedTrackIds.value - idsToDelete
                exitSelectionMode()
                userMessage.value = HistoryUserMessage.DeleteSuccess(idsToDelete.size)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                userMessage.value = HistoryUserMessage.DeleteFailed
            } finally {
                isDeleting.value = false
            }
        }
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
                exitSelectionMode()
            } finally {
                isClearing.value = false
            }
        }
    }

    fun executeSelected(action: BatchTrackAction) {
        if (isBatchActionRunning.value) return
        val orderedTrackIds = currentVisibleActionableSelectionInOrder()
        if (orderedTrackIds.isEmpty()) return
        isBatchActionRunning.value = true
        viewModelScope.launch {
            try {
                val result = batchTrackActionExecutor.execute(action, orderedTrackIds)
                batchResult.value = result
                if (result is BatchTrackActionResult.Completed) {
                    exitSelectionMode()
                }
            } finally {
                isBatchActionRunning.value = false
            }
        }
    }

    fun addSelectedToQueue() {
        executeSelected(BatchTrackAction.AddToQueue)
    }

    fun addSelectedToPlaylist(playlistId: PlaylistId) {
        executeSelected(BatchTrackAction.AddToPlaylist(playlistId))
    }

    fun addSingleTrackToPlaylist(trackId: TrackId, playlistId: PlaylistId) {
        viewModelScope.launch {
            batchTrackActionExecutor.execute(BatchTrackAction.AddToPlaylist(playlistId), listOf(trackId))
        }
    }

    fun acknowledgeBatchResult() {
        batchResult.value = null
    }

    fun acknowledgeUserMessage() {
        userMessage.value = null
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistUseCase.create(name)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Handled gracefully
            }
        }
    }

    fun showTrackInfo(track: Track) {
        infoTrack.value = track
        infoMetadata.value = null
        isInfoLoading.value = true
        infoJob?.cancel()
        infoJob = viewModelScope.launch {
            val loaded = trackMetadataRepository.read(track)
            if (infoTrack.value?.id == track.id) {
                infoMetadata.value = loaded
                isInfoLoading.value = false
            }
        }
    }

    fun dismissTrackInfo() {
        infoJob?.cancel()
        infoJob = null
        infoTrack.value = null
        infoMetadata.value = null
        isInfoLoading.value = false
    }

    fun onBack(): Boolean {
        if (infoTrack.value != null) {
            dismissTrackInfo()
            return true
        }
        if (deleteConfirmationTrackIds.value != null) {
            cancelDelete()
            return true
        }
        if (clearConfirmationRequested.value) {
            cancelClearHistory()
            return true
        }
        if (isSearchActive.value) {
            closeSearch()
            return true
        }
        if (isSelectionMode.value) {
            exitSelectionMode()
            return true
        }
        return false
    }

    private fun currentVisibleActionableSelectionInOrder(): List<TrackId> {
        val selected = selectedTrackIds.value
        return uiState.value.visibleEntries.mapNotNull { entry ->
            entry.trackId.takeIf { entry.isActionable && it in selected }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private data class HistoryPresentationState(
    val query: String,
    val isSearchActive: Boolean,
    val isSelectionMode: Boolean,
    val selectedTrackIds: Set<TrackId>,
)

private data class HistoryDialogsAndActionsState(
    val clearConfirmationRequested: Boolean,
    val deleteConfirmationTrackIds: Set<TrackId>?,
    val isClearing: Boolean,
    val isDeleting: Boolean,
    val batchResult: BatchTrackActionResult?,
)

private data class HistoryInfoAndMessageState(
    val track: Track?,
    val metadata: AdvancedTrackMetadata?,
    val isInfoLoading: Boolean,
    val isBatchActionRunning: Boolean,
    val userMessage: HistoryUserMessage?,
)

private data class HistoryContent(
    val entries: List<HistoryEntry>,
    val playlists: List<Playlist>,
)

private class DefaultTrackMetadataRepository : TrackMetadataRepository {
    override suspend fun read(track: Track): AdvancedTrackMetadata =
        AdvancedTrackMetadata(
            encoding = "mp3",
            bitrateBps = 320_000L,
            sampleRateHz = 44_100,
            fileSizeBytes = 10_000_000L,
            isReadable = true,
        )
}

private fun HistoryEntry.matches(normalizedQuery: String): Boolean {
    val searchableValues =
        if (track == null) {
            listOf(trackId.volumeName, trackId.mediaStoreId.toString())
        } else {
            listOf(track.title, track.artistName, track.albumTitle.orEmpty(), track.displayName)
        }
    return searchableValues.any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
}
