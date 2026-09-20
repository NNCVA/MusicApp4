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
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.sort.SortPreferencesRepository
import com.musicapp.player.data.sync.LibrarySyncEvent
import com.musicapp.player.data.sync.LibrarySyncState
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.createSectionTextComparator
import com.musicapp.player.core.designsystem.component.sortedBySectionText
import com.musicapp.player.feature.playlists.PlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    val field: TrackSortField = TrackSortField.TITLE,
    val direction: TrackSortDirection = TrackSortDirection.ASCENDING,
) {
    fun next(field: TrackSortField): TrackSort =
        if (this.field == field) {
            val nextDirection =
                if (direction == TrackSortDirection.ASCENDING) {
                    TrackSortDirection.DESCENDING
                } else {
                    TrackSortDirection.ASCENDING
                }
            copy(direction = nextDirection)
        } else {
            defaultFor(field)
        }

    companion object {
        val DEFAULT = TrackSort(TrackSortField.TITLE, TrackSortDirection.ASCENDING)
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

sealed interface TracksRefreshResult {
    data class Added(val count: Int) : TracksRefreshResult
    data class Removed(val count: Int) : TracksRefreshResult
    data class AddedAndRemoved(val addedCount: Int, val removedCount: Int) : TracksRefreshResult
    data object UpToDate : TracksRefreshResult
    data object Failed : TracksRefreshResult
}

data class TracksUiState(
    val tracks: List<Track> = emptyList(),
    val sections: List<TrackSection> = emptyList(),
    val sectionPositions: Map<String, Int> = emptyMap(),
    val isLibraryLoaded: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val sort: TrackSort = TrackSort.defaultFor(TrackSortField.TITLE),
    val isSelectionMode: Boolean = false,
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val batchResult: BatchTrackActionResult? = null,
    val isBatchActionRunning: Boolean = false,
    val infoTrack: Track? = null,
    val infoMetadata: AdvancedTrackMetadata? = null,
    val isInfoLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshResult: TracksRefreshResult? = null,
) {
    @Deprecated("Artwork state decoupled from ViewModel StateFlow in M2 (R3). Replaced by Coil AsyncImage in M3.")
    val artworkByTrackId: Map<TrackId, TrackArtworkState> get() = emptyMap()
}

@HiltViewModel
class TracksViewModel internal constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    playlistRepository: PlaylistRepository,
    private val playlistUseCase: PlaylistUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackControllerFacade,
    private val batchActionExecutor: BatchTrackActionExecutor,
    private val artworkRepository: ArtworkRepository,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val sortPreferencesRepository: SortPreferencesRepository,
    private val computationDispatcher: CoroutineDispatcher,
    private val tracksSyncController: TracksSyncController? = null,
) : ViewModel() {
    @Inject
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playlistRepository: PlaylistRepository,
        playlistUseCase: PlaylistUseCase,
        savedStateHandle: SavedStateHandle,
        playbackController: PlaybackControllerFacade,
        batchActionExecutor: BatchTrackActionExecutor,
        artworkRepository: ArtworkRepository,
        trackMetadataRepository: TrackMetadataRepository,
        sortPreferencesRepository: SortPreferencesRepository,
        tracksSyncController: TracksSyncController,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playlistRepository = playlistRepository,
        playlistUseCase = playlistUseCase,
        savedStateHandle = savedStateHandle,
        playbackController = playbackController,
        batchActionExecutor = batchActionExecutor,
        artworkRepository = artworkRepository,
        trackMetadataRepository = trackMetadataRepository,
        sortPreferencesRepository = sortPreferencesRepository,
        computationDispatcher = Dispatchers.Default,
        tracksSyncController = tracksSyncController,
    )

    internal constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playlistRepository: PlaylistRepository,
        savedStateHandle: SavedStateHandle,
        playbackController: PlaybackControllerFacade,
        batchActionExecutor: BatchTrackActionExecutor,
        artworkRepository: ArtworkRepository,
        trackMetadataRepository: TrackMetadataRepository,
        sortPreferencesRepository: SortPreferencesRepository,
        computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
        tracksSyncController: TracksSyncController? = null,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playlistRepository = playlistRepository,
        playlistUseCase = PlaylistUseCase(playlistRepository, com.musicapp.player.core.common.time.Clock { System.currentTimeMillis() }),
        savedStateHandle = savedStateHandle,
        playbackController = playbackController,
        batchActionExecutor = batchActionExecutor,
        artworkRepository = artworkRepository,
        trackMetadataRepository = trackMetadataRepository,
        sortPreferencesRepository = sortPreferencesRepository,
        computationDispatcher = computationDispatcher,
        tracksSyncController = tracksSyncController,
    )

    private val isSelectionMode = MutableStateFlow(false)
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val batchResult = MutableStateFlow<BatchTrackActionResult?>(null)
    private val isBatchActionRunning = MutableStateFlow(false)
    private val infoTrack = MutableStateFlow<Track?>(null)
    private val infoMetadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val isInfoLoading = MutableStateFlow(false)
    private var infoJob: Job? = null

    private val _isInitialDataReady = MutableStateFlow(false)
    val isInitialDataReady: StateFlow<Boolean> = _isInitialDataReady

    private val libraryState =
        mediaLibraryRepository.observeTracks()
            .map { tracks -> TracksLibraryState(tracks = tracks, isLoaded = true) }

    private val playlists =
        playlistRepository.observePlaylists()
            .onStart { emit(emptyList()) }

    private val sortedTracksState =
        combine(libraryState, sortPreferencesRepository.trackSort) { library, currentSort ->
            val sortedTracks = library.tracks.sortedWithTrackSort(currentSort)
            val sections = groupTracksIntoSections(sortedTracks, currentSort.field, currentSort.direction)
            val sectionPositions = sectionStartPositions(sections, currentSort.direction)
            SortedTracksState(
                tracks = sortedTracks,
                sections = sections,
                sectionPositions = sectionPositions,
                visibleTrackIds = sortedTracks.mapTo(hashSetOf(), Track::id),
                isLibraryLoaded = library.isLoaded,
                sort = currentSort,
            )
        }.flowOn(computationDispatcher)

    private val presentationState =
        combine(
            isSelectionMode,
            selectedTrackIds,
            batchResult,
            isBatchActionRunning,
        ) { selectionMode, selected, result, isRunning ->
            TracksPresentationState(selectionMode, selected, result, isRunning)
        }

    private val infoState =
        combine(infoTrack, infoMetadata, isInfoLoading) { track, metadata, loading ->
            TracksInfoState(track, metadata, loading)
        }

    private val refreshResult = MutableStateFlow<TracksRefreshResult?>(null)
    private val isManualRefreshing = MutableStateFlow(false)

    private val isRefreshingFlow: Flow<Boolean> =
        combine(
            tracksSyncController?.state ?: MutableStateFlow(LibrarySyncState.Idle(false)),
            isManualRefreshing,
        ) { syncState, manualRefreshing ->
            manualRefreshing || syncState is LibrarySyncState.Syncing
        }

    private val refreshState =
        combine(isRefreshingFlow, refreshResult) { isRefreshing, result ->
            isRefreshing to result
        }

    val uiState: StateFlow<TracksUiState> =
        combine(
            sortedTracksState,
            playlists,
            presentationState,
            infoState,
            refreshState,
        ) { sortedTracks, playlists, presentation, info, (isRefreshing, refreshResult) ->
            val visibleSelection =
                presentation.selectedTrackIds.filterTo(linkedSetOf()) {
                    it in sortedTracks.visibleTrackIds
                }
            val isSelectionActive = presentation.isSelectionMode && sortedTracks.tracks.isNotEmpty()
            if (sortedTracks.isLibraryLoaded) {
                _isInitialDataReady.value = true
            }
            TracksUiState(
                tracks = sortedTracks.tracks,
                sections = sortedTracks.sections,
                sectionPositions = sortedTracks.sectionPositions,
                isLibraryLoaded = sortedTracks.isLibraryLoaded,
                playlists = playlists,
                sort = sortedTracks.sort,
                isSelectionMode = isSelectionActive,
                selectedTrackIds = visibleSelection,
                batchResult = presentation.batchResult,
                isBatchActionRunning = presentation.isBatchActionRunning,
                infoTrack = info.track,
                infoMetadata = info.metadata,
                isInfoLoading = info.isLoading,
                isRefreshing = isRefreshing,
                refreshResult = refreshResult,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TracksUiState(sort = sortPreferencesRepository.trackSort.value, isLibraryLoaded = false),
        )

    fun selectSort(field: TrackSortField) {
        val updated = uiState.value.sort.next(field)
        viewModelScope.launch {
            sortPreferencesRepository.setTrackSort(updated)
        }
    }

    fun startSelection(trackId: TrackId) {
        isSelectionMode.value = true
        val visibleTrackIds = uiState.value.tracks.mapTo(hashSetOf(), Track::id)
        if (trackId in visibleTrackIds) {
            selectedTrackIds.value = setOf(trackId)
        }
    }

    fun toggleSelection(trackId: TrackId) {
        if (!isSelectionMode.value) {
            isSelectionMode.value = true
        }
        val effectiveSelection = currentVisibleSelection()
        selectedTrackIds.value = LinkedHashSet(effectiveSelection).apply {
            if (!remove(trackId)) add(trackId)
        }
    }

    fun toggleSelectAll(targetTrackIds: Collection<TrackId>? = null) {
        isSelectionMode.value = true
        val targetIds = (targetTrackIds ?: uiState.value.tracks.map(Track::id)).toSet()
        val visibleIds = uiState.value.tracks.mapTo(hashSetOf(), Track::id)
        val candidateIds = targetIds.filter { it in visibleIds }.toSet()
        val effectiveSelection = currentVisibleSelection()
        if (candidateIds.isNotEmpty() && effectiveSelection.containsAll(candidateIds)) {
            clearSelection()
        } else {
            selectTracks(candidateIds)
        }
    }

    fun selectAllCurrentResults() {
        isSelectionMode.value = true
        selectTracks(uiState.value.tracks.map(Track::id))
    }

    fun selectTracks(trackIds: Collection<TrackId>) {
        isSelectionMode.value = true
        val visibleTrackIds = uiState.value.tracks.mapTo(hashSetOf(), Track::id)
        selectedTrackIds.value = trackIds.filterTo(linkedSetOf()) { it in visibleTrackIds }
    }

    fun clearSelection() {
        selectedTrackIds.value = emptySet()
    }

    fun exitSelection() {
        isSelectionMode.value = false
        selectedTrackIds.value = emptySet()
    }

    fun onBack(): Boolean {
        if (uiState.value.infoTrack != null) {
            dismissTrackInfo()
            return true
        }
        if (!uiState.value.isSelectionMode) return false
        exitSelection()
        return true
    }

    fun playAll() {
        val orderedTrackIds =
            uiState.value.tracks
                .filter { it.availability == Availability.AVAILABLE }
                .map(Track::id)
        val firstTrackId = orderedTrackIds.firstOrNull() ?: return
        playbackController.play(
            PlaybackContext(
                source = PlaybackContextSource.TRACKS,
                orderedTrackIds = orderedTrackIds,
                selectedTrackId = firstTrackId,
            ),
        )
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

    @Deprecated("Decoupled in M2 (R3). Replaced by Coil AsyncImage in M3.")
    suspend fun requestArtwork(track: Track) {
        // No-op: artwork is loaded directly by Coil AsyncImage in Composable
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

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistUseCase.create(name)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Failure handled gracefully
            }
        }
    }

    fun playSelectedNext() {
        executeBatch(BatchTrackAction.PlayNext)
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

    fun acknowledgeBatchResult() {
        batchResult.value = null
    }

    fun refreshTracks() {
        if (uiState.value.isRefreshing || uiState.value.isSelectionMode) return
        val controller = tracksSyncController ?: return
        viewModelScope.launch {
            isManualRefreshing.value = true
            val startTime = System.currentTimeMillis()
            try {
                val event = controller.requestFullSync()
                val elapsed = System.currentTimeMillis() - startTime
                val remainingDelay = (MIN_REFRESH_DURATION_MS - elapsed).coerceAtLeast(0L)
                if (remainingDelay > 0L) {
                    delay(remainingDelay)
                }
                when (event) {
                    is LibrarySyncEvent.Completed -> {
                        val added = event.result.addedTrackCount
                        val removed = event.result.removedTrackCount
                        refreshResult.value = when {
                            added > 0 && removed > 0 -> TracksRefreshResult.AddedAndRemoved(added, removed)
                            added > 0 -> TracksRefreshResult.Added(added)
                            removed > 0 -> TracksRefreshResult.Removed(removed)
                            else -> TracksRefreshResult.UpToDate
                        }
                    }
                    is LibrarySyncEvent.Failed -> {
                        refreshResult.value = TracksRefreshResult.Failed
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                refreshResult.value = TracksRefreshResult.Failed
            } finally {
                isManualRefreshing.value = false
            }
        }
    }

    fun acknowledgeRefreshResult() {
        refreshResult.value = null
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
                    if (selectedTrackIds.value.isEmpty()) {
                        isSelectionMode.value = false
                    }
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
        const val MIN_REFRESH_DURATION_MS = 800L
        const val ARTWORK_TARGET_PX = 128
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private data class TracksPresentationState(
    val isSelectionMode: Boolean,
    val selectedTrackIds: Set<TrackId>,
    val batchResult: BatchTrackActionResult?,
    val isBatchActionRunning: Boolean,
)

private data class TracksInfoState(
    val track: Track? = null,
    val metadata: AdvancedTrackMetadata? = null,
    val isLoading: Boolean = false,
)

private data class TracksLibraryState(
    val tracks: List<Track> = emptyList(),
    val isLoaded: Boolean = false,
)

private data class SortedTracksState(
    val tracks: List<Track>,
    val sections: List<TrackSection> = emptyList(),
    val sectionPositions: Map<String, Int> = emptyMap(),
    val visibleTrackIds: Set<TrackId>,
    val isLibraryLoaded: Boolean,
    val sort: TrackSort,
)

data class TrackArtworkState(
    val dateModifiedMs: Long = 0L,
    val artwork: ArtworkResult = ArtworkResult.Placeholder,
)

private fun List<Track>.sortedWithTrackSort(sort: TrackSort): List<Track> {
    val textTieBreaker =
        compareBy<Track>(
            { it.title.lowercase(Locale.ROOT) },
            { it.id.volumeName.lowercase(Locale.ROOT) },
            { it.id.mediaStoreId },
        )
    val sectionOrder =
        when (sort.direction) {
            TrackSortDirection.ASCENDING -> SectionSortOrder.ASCENDING
            TrackSortDirection.DESCENDING -> SectionSortOrder.DESCENDING
        }
    return when (sort.field) {
        TrackSortField.TITLE -> sortedBySectionText(sectionOrder, Track::title, textTieBreaker)
        TrackSortField.ARTIST -> sortedBySectionText(sectionOrder, Track::artistName, textTieBreaker)
        TrackSortField.ALBUM -> sortedBySectionText(sectionOrder, { it.albumTitle.orEmpty() }, textTieBreaker)
        TrackSortField.DATE_ADDED -> {
            val primary = compareBy<Track> { it.dateAddedMs }
            sortedWith((if (sort.direction == TrackSortDirection.ASCENDING) primary else primary.reversed()).then(textTieBreaker))
        }
        TrackSortField.DURATION -> {
            val primary = compareBy<Track> { it.durationMs }
            sortedWith((if (sort.direction == TrackSortDirection.ASCENDING) primary else primary.reversed()).then(textTieBreaker))
        }
    }
}

internal fun TrackSort.comparator(): Comparator<Track> {
    val textTieBreaker =
        compareBy<Track>(
            { it.title.lowercase(Locale.ROOT) },
            { it.id.volumeName.lowercase(Locale.ROOT) },
            { it.id.mediaStoreId },
        )
    val sectionOrder =
        when (direction) {
            TrackSortDirection.ASCENDING -> SectionSortOrder.ASCENDING
            TrackSortDirection.DESCENDING -> SectionSortOrder.DESCENDING
        }
    return when (field) {
        TrackSortField.TITLE -> createSectionTextComparator(sectionOrder, Track::title, textTieBreaker)
        TrackSortField.ARTIST -> createSectionTextComparator(sectionOrder, Track::artistName, textTieBreaker)
        TrackSortField.ALBUM -> createSectionTextComparator(sectionOrder, { it.albumTitle.orEmpty() }, textTieBreaker)
        TrackSortField.DATE_ADDED -> {
            val primary = compareBy<Track> { it.dateAddedMs }
            (if (direction == TrackSortDirection.ASCENDING) primary else primary.reversed())
                .then(textTieBreaker)
        }
        TrackSortField.DURATION -> {
            val primary = compareBy<Track> { it.durationMs }
            (if (direction == TrackSortDirection.ASCENDING) primary else primary.reversed())
                .then(textTieBreaker)
        }
    }
}
