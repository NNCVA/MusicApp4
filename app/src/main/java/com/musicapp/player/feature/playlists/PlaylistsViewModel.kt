package com.musicapp.player.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.repository.PlaylistTrackChangeResult
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaylistOperationMessage {
    CREATED,
    RENAMED,
    DELETED,
    TRACKS_REMOVED,
    FAILED,
}

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val operationMessage: PlaylistOperationMessage? = null,
    val isLoaded: Boolean = false,
) {
    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in M3.")
    val artworkByPlaylistId: Map<Long, ArtworkResult> get() = emptyMap()
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    repository: PlaylistRepository,
    private val useCase: PlaylistUseCase,
) : ViewModel() {
    constructor(
        repository: PlaylistRepository,
        useCase: PlaylistUseCase,
        mediaLibraryRepository: MediaLibraryRepository,
        artworkRepository: ArtworkRepository,
    ) : this(repository, useCase)

    private val operationMessage = MutableStateFlow<PlaylistOperationMessage?>(null)
    private val playlists = repository.observePlaylists()

    val uiState: StateFlow<PlaylistsUiState> =
        combine(playlists, operationMessage) { currentPlaylists, message ->
            PlaylistsUiState(
                playlists = currentPlaylists,
                operationMessage = message,
                isLoaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistsUiState(isLoaded = false))

    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in M3.")
    fun requestArtwork(playlist: Playlist) {
        // No-op: artwork is loaded directly by Coil AsyncImage in Composable
    }

    fun create(rawName: String) = mutate(PlaylistOperationMessage.CREATED) { useCase.create(rawName) }

    fun rename(playlistId: PlaylistId, rawName: String) =
        mutate(PlaylistOperationMessage.RENAMED) { useCase.rename(playlistId, rawName) }

    fun delete(playlistId: PlaylistId) =
        mutate(PlaylistOperationMessage.DELETED) { useCase.delete(playlistId) }

    fun clearMessage() {
        operationMessage.value = null
    }

    private fun mutate(success: PlaylistOperationMessage, action: suspend () -> Unit) {
        viewModelScope.launch {
            operationMessage.value =
                try {
                    action()
                    success
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    PlaylistOperationMessage.FAILED
                }
        }
    }
}

@HiltViewModel
class PlaylistDetailViewModel internal constructor(
    private val playlistRepository: PlaylistRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val useCase: PlaylistUseCase,
    private val playbackController: PlaybackControllerFacade,
    private val batchActionExecutor: BatchTrackActionExecutor,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val computationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        playlistRepository: PlaylistRepository,
        mediaLibraryRepository: MediaLibraryRepository,
        useCase: PlaylistUseCase,
        playbackController: PlaybackControllerFacade,
        batchActionExecutor: BatchTrackActionExecutor,
        trackMetadataRepository: TrackMetadataRepository,
    ) : this(
        playlistRepository = playlistRepository,
        mediaLibraryRepository = mediaLibraryRepository,
        useCase = useCase,
        playbackController = playbackController,
        batchActionExecutor = batchActionExecutor,
        trackMetadataRepository = trackMetadataRepository,
        computationDispatcher = Dispatchers.Default,
    )

    constructor(
        playlistRepository: PlaylistRepository,
        mediaLibraryRepository: MediaLibraryRepository,
        useCase: PlaylistUseCase,
        playbackController: PlaybackControllerFacade,
    ) : this(
        playlistRepository = playlistRepository,
        mediaLibraryRepository = mediaLibraryRepository,
        useCase = useCase,
        playbackController = playbackController,
        batchActionExecutor = com.musicapp.player.feature.tracks.batch.DefaultBatchTrackActionExecutor(
            playlistRepository,
            mediaLibraryRepository,
            playbackController,
            com.musicapp.player.core.common.time.Clock { System.currentTimeMillis() },
        ),
        trackMetadataRepository = object : TrackMetadataRepository {
            override suspend fun read(track: Track): AdvancedTrackMetadata = AdvancedTrackMetadata(
                encoding = "FLAC",
                bitrateBps = 320_000L,
                sampleRateHz = 44100,
                fileSizeBytes = track.sizeBytes,
                isReadable = true,
            )
        },
        computationDispatcher = Dispatchers.Main.immediate,
    )

    private val selectedPlaylistId = MutableStateFlow<PlaylistId?>(null)
    private val sort = MutableStateFlow(PlaylistTrackSort.DEFAULT)
    private val searchQuery = MutableStateFlow("")
    private val isSearching = MutableStateFlow(false)
    private val isSelectionMode = MutableStateFlow(false)
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val operationMessage = MutableStateFlow<PlaylistOperationMessage?>(null)
    private val lastRemovalResult = MutableStateFlow<PlaylistTrackChangeResult?>(null)
    private val batchResult = MutableStateFlow<BatchTrackActionResult?>(null)
    private val isBatchActionRunning = MutableStateFlow(false)
    private val playbackFeedback = MutableStateFlow<PlaylistPlaybackPreparation?>(null)
    private val infoTrack = MutableStateFlow<Track?>(null)
    private val infoMetadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val isInfoLoading = MutableStateFlow(false)

    val uiState: StateFlow<PlaylistDetailUiState> =
        combine(
            playlistRepository.observePlaylists(),
            mediaLibraryRepository.observeTracks(),
            selectedPlaylistId,
            sort,
            searchQuery,
            isSearching,
            isSelectionMode,
            selectedTrackIds,
            operationMessage,
            lastRemovalResult,
            batchResult,
            isBatchActionRunning,
            playbackFeedback,
            infoTrack,
            infoMetadata,
            isInfoLoading,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val playlists = args[0] as List<Playlist>
            @Suppress("UNCHECKED_CAST")
            val tracks = args[1] as List<Track>
            val playlistId = args[2] as? PlaylistId
            val currentSort = args[3] as PlaylistTrackSort
            val query = args[4] as String
            val searching = args[5] as Boolean
            val selectionMode = args[6] as Boolean
            @Suppress("UNCHECKED_CAST")
            val currentSelection = args[7] as Set<TrackId>
            val opMessage = args[8] as? PlaylistOperationMessage
            val removalResult = args[9] as? PlaylistTrackChangeResult
            val bResult = args[10] as? BatchTrackActionResult
            val batchRunning = args[11] as Boolean
            val pbFeedback = args[12] as? PlaylistPlaybackPreparation
            val iTrack = args[13] as? Track
            val iMeta = args[14] as? AdvancedTrackMetadata
            val iLoading = args[15] as Boolean

            val playlist = playlists.firstOrNull { it.id == playlistId }
            val tracksById = tracks.associateBy(Track::id)
            val orderedTracks = playlist?.trackIds.orEmpty().mapNotNull(tracksById::get)
            val sortedTracks = sortPlaylistTracks(orderedTracks, currentSort)
            val filteredTracks = if (searching && query.isNotBlank()) {
                sortedTracks.filter { it.matchesPlaylistSearch(query) }
            } else {
                sortedTracks
            }
            val sections = if (searching && query.isNotBlank()) {
                groupPlaylistTracksIntoSections(filteredTracks, currentSort.field, currentSort.direction)
            } else {
                groupPlaylistTracksIntoSections(sortedTracks, currentSort.field, currentSort.direction)
            }
            val sectionPositions = playlistSectionStartPositions(sections, currentSort.direction)

            PlaylistDetailUiState(
                playlist = playlist,
                tracks = orderedTracks,
                sortedTracks = sortedTracks,
                filteredTracks = filteredTracks,
                sections = sections,
                sectionPositions = sectionPositions,
                searchQuery = query,
                isSearching = searching,
                sort = currentSort,
                selectedTrackIds = currentSelection.intersect(orderedTracks.mapTo(mutableSetOf(), Track::id)),
                isSelectionMode = selectionMode,
                isLibraryLoaded = true,
                allPlaylists = playlists,
                operationMessage = opMessage,
                lastRemovalResult = removalResult,
                batchResult = bResult,
                isBatchActionRunning = batchRunning,
                playbackFeedback = pbFeedback,
                infoTrack = iTrack,
                infoMetadata = iMeta,
                isInfoLoading = iLoading,
            )
        }
            .flowOn(computationDispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                PlaylistDetailUiState(),
            )

    fun open(playlistId: PlaylistId) {
        if (selectedPlaylistId.value != playlistId) {
            clearSelection()
            closeSearch()
        }
        selectedPlaylistId.value = playlistId
    }

    fun selectSort(field: PlaylistTrackSortField) {
        sort.update { current ->
            if (current.field == field) {
                val nextDirection =
                    if (current.direction == PlaylistTrackSortDirection.ASCENDING) {
                        PlaylistTrackSortDirection.DESCENDING
                    } else {
                        PlaylistTrackSortDirection.ASCENDING
                    }
                current.copy(direction = nextDirection)
            } else {
                PlaylistTrackSort(
                    field = field,
                    direction = PlaylistTrackSortDirection.ASCENDING,
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun openSearch() {
        isSearching.value = true
    }

    fun closeSearch() {
        isSearching.value = false
        searchQuery.value = ""
    }

    fun playAll() {
        val state = uiState.value
        val playlist = state.playlist ?: return
        val preparation = PlaylistPlaybackContextFactory.prepare(
            playlist = playlist,
            tracks = state.tracks,
            selectedTrackId = null,
            shuffle = false,
            customTrackOrder = state.displayTracks.map(Track::id),
        )
        playbackFeedback.value = preparation
        preparation.context?.let(playbackController::play)
    }

    fun shufflePlay() {
        val state = uiState.value
        val playlist = state.playlist ?: return
        val preparation = PlaylistPlaybackContextFactory.prepare(
            playlist = playlist,
            tracks = state.tracks,
            selectedTrackId = null,
            shuffle = true,
            customTrackOrder = state.displayTracks.map(Track::id),
        )
        playbackFeedback.value = preparation
        preparation.context?.let(playbackController::play)
    }

    fun playTrack(trackId: TrackId) {
        val state = uiState.value
        val playlist = state.playlist ?: return
        val preparation = PlaylistPlaybackContextFactory.prepare(
            playlist = playlist,
            tracks = state.tracks,
            selectedTrackId = trackId,
            shuffle = false,
            customTrackOrder = state.displayTracks.map(Track::id),
        )
        preparation.context?.let(playbackController::play)
    }

    fun toggleSelection(trackId: TrackId) {
        if (uiState.value.tracks.none { it.id == trackId }) return
        selectedTrackIds.update { current ->
            val updated = if (trackId in current) current - trackId else current + trackId
            if (updated.isEmpty()) {
                isSelectionMode.value = false
            }
            updated
        }
    }

    fun startSelection(trackId: TrackId) {
        isSelectionMode.value = true
        selectedTrackIds.value = setOf(trackId)
    }

    fun selectAll() {
        val visibleIds = uiState.value.displayTracks.mapTo(linkedSetOf(), Track::id)
        selectedTrackIds.value = visibleIds
        if (visibleIds.isNotEmpty()) {
            isSelectionMode.value = true
        }
    }

    fun selectTracks(trackIds: Collection<TrackId>) {
        val targetIds = uiState.value.tracks.map(Track::id).intersect(trackIds.toSet())
        selectedTrackIds.value = targetIds
        isSelectionMode.value = targetIds.isNotEmpty()
    }

    fun toggleSelectAll() {
        val visibleIds = uiState.value.displayTracks.map(Track::id).toSet()
        if (visibleIds.isNotEmpty() && selectedTrackIds.value.containsAll(visibleIds)) {
            selectedTrackIds.value = emptySet()
            isSelectionMode.value = false
        } else {
            selectedTrackIds.value = visibleIds
            isSelectionMode.value = visibleIds.isNotEmpty()
        }
    }

    fun clearSelection() {
        selectedTrackIds.value = emptySet()
        isSelectionMode.value = false
    }

    fun exitSelection() {
        clearSelection()
    }

    fun removeTrack(trackId: TrackId) {
        removeTracks(listOf(trackId))
    }

    fun removeSelected() {
        removeTracks(uiState.value.selectedTrackIdsInOrder)
    }

    private fun removeTracks(trackIds: List<TrackId>) {
        val playlistId = selectedPlaylistId.value ?: return
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            operationMessage.value =
                try {
                    lastRemovalResult.value = useCase.removeTracks(playlistId, trackIds)
                    clearSelection()
                    PlaylistOperationMessage.TRACKS_REMOVED
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    lastRemovalResult.value = null
                    PlaylistOperationMessage.FAILED
                }
        }
    }

    fun addTrackToQueue(trackId: TrackId) {
        executeBatchAction(listOf(trackId), BatchTrackAction.AddToQueue)
    }

    fun playTrackNext(trackId: TrackId) {
        executeBatchAction(listOf(trackId), BatchTrackAction.PlayNext)
    }

    fun addTrackToPlaylist(trackId: TrackId, targetPlaylistId: PlaylistId) {
        executeBatchAction(listOf(trackId), BatchTrackAction.AddToPlaylist(targetPlaylistId))
    }

    fun addSelectedToPlaylist(targetPlaylistId: PlaylistId) {
        executeBatchAction(uiState.value.selectedTrackIdsInOrder, BatchTrackAction.AddToPlaylist(targetPlaylistId))
    }

    fun addSelectedToQueue() {
        executeBatchAction(uiState.value.selectedTrackIdsInOrder, BatchTrackAction.AddToQueue)
    }

    private fun executeBatchAction(trackIds: List<TrackId>, action: BatchTrackAction) {
        if (trackIds.isEmpty() || isBatchActionRunning.value) return
        isBatchActionRunning.value = true
        viewModelScope.launch {
            try {
                val result = batchActionExecutor.execute(action, trackIds)
                batchResult.value = result
                if (result is BatchTrackActionResult.Completed && action !is BatchTrackAction.PlayNext && action !is BatchTrackAction.AddToQueue) {
                    clearSelection()
                } else if (result is BatchTrackActionResult.Completed) {
                    clearSelection()
                }
            } finally {
                isBatchActionRunning.value = false
            }
        }
    }

    fun showTrackInfo(track: Track) {
        infoTrack.value = track
        infoMetadata.value = null
        isInfoLoading.value = true
        viewModelScope.launch {
            val metadata = trackMetadataRepository.read(track)
            if (infoTrack.value?.id == track.id) {
                infoMetadata.value = metadata
                isInfoLoading.value = false
            }
        }
    }

    fun dismissTrackInfo() {
        infoTrack.value = null
        infoMetadata.value = null
        isInfoLoading.value = false
    }

    fun renamePlaylist(newName: String) {
        val playlistId = selectedPlaylistId.value ?: return
        viewModelScope.launch {
            operationMessage.value =
                try {
                    useCase.rename(playlistId, newName)
                    PlaylistOperationMessage.RENAMED
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    PlaylistOperationMessage.FAILED
                }
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit = {}) {
        val playlistId = selectedPlaylistId.value ?: return
        viewModelScope.launch {
            operationMessage.value =
                try {
                    useCase.delete(playlistId)
                    onDeleted()
                    PlaylistOperationMessage.DELETED
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    PlaylistOperationMessage.FAILED
                }
        }
    }

    fun clearMessage() {
        operationMessage.value = null
    }

    fun acknowledgeBatchResult() {
        batchResult.value = null
    }

    fun acknowledgePlaybackFeedback() {
        playbackFeedback.value = null
    }

    fun onBack(): Boolean {
        if (infoTrack.value != null) {
            dismissTrackInfo()
            return true
        }
        if (isSelectionMode.value) {
            clearSelection()
            return true
        }
        if (isSearching.value) {
            closeSearch()
            return true
        }
        return false
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
