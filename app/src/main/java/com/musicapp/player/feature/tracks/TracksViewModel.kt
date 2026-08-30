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
import com.musicapp.player.core.designsystem.component.SectionSortOrder
import com.musicapp.player.core.designsystem.component.createSectionTextComparator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    val isSelectionMode: Boolean = false,
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val batchResult: BatchTrackActionResult? = null,
    val isBatchActionRunning: Boolean = false,
)

@HiltViewModel
class TracksViewModel internal constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    playlistRepository: PlaylistRepository,
    private val savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackControllerFacade,
    private val batchActionExecutor: BatchTrackActionExecutor,
    private val artworkRepository: ArtworkRepository,
    private val computationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playlistRepository: PlaylistRepository,
        savedStateHandle: SavedStateHandle,
        playbackController: PlaybackControllerFacade,
        batchActionExecutor: BatchTrackActionExecutor,
        artworkRepository: ArtworkRepository,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playlistRepository = playlistRepository,
        savedStateHandle = savedStateHandle,
        playbackController = playbackController,
        batchActionExecutor = batchActionExecutor,
        artworkRepository = artworkRepository,
        computationDispatcher = Dispatchers.Default,
    )

    private val sort = MutableStateFlow(restoreSort(savedStateHandle))
    private val isSelectionMode = MutableStateFlow(false)
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val batchResult = MutableStateFlow<BatchTrackActionResult?>(null)
    private val isBatchActionRunning = MutableStateFlow(false)
    private val artworkByTrackId = MutableStateFlow<Map<TrackId, TrackArtworkState>>(emptyMap())
    private val artworkRequestMutex = Mutex()
    private val activeArtworkRequests = mutableMapOf<TrackId, ActiveArtworkRequest>()

    private val libraryState =
        mediaLibraryRepository.observeTracks()
            .map { tracks -> TracksLibraryState(tracks = tracks, isLoaded = true) }

    private val playlists =
        playlistRepository.observePlaylists()
            .onStart { emit(emptyList()) }

    private val sortedTracksState =
        combine(libraryState, sort) { library, currentSort ->
            val sortedTracks = library.tracks.sortedWith(currentSort.comparator())
            SortedTracksState(
                tracks = sortedTracks,
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

    val uiState: StateFlow<TracksUiState> =
        combine(
            sortedTracksState,
            playlists,
            presentationState,
            artworkByTrackId,
        ) { sortedTracks, playlists, presentation, artwork ->
            val visibleSelection =
                presentation.selectedTrackIds.filterTo(linkedSetOf()) {
                    it in sortedTracks.visibleTrackIds
                }
            val isSelectionActive = presentation.isSelectionMode && sortedTracks.tracks.isNotEmpty()
            TracksUiState(
                tracks = sortedTracks.tracks,
                isLibraryLoaded = sortedTracks.isLibraryLoaded,
                artworkByTrackId = artwork.filterKeys { it in sortedTracks.visibleTrackIds },
                playlists = playlists,
                sort = sortedTracks.sort,
                isSelectionMode = isSelectionActive,
                selectedTrackIds = visibleSelection,
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

    suspend fun requestArtwork(track: Track) {
        while (true) {
            when (val claim = claimArtworkRequest(track)) {
                ArtworkRequestClaim.Cached -> return
                is ArtworkRequestClaim.Await -> {
                    if (claim.completion.await() == ArtworkRequestOutcome.FINISHED) return
                }
                is ArtworkRequestClaim.Load -> {
                    try {
                        val result =
                            try {
                                artworkRepository.artwork(track, ARTWORK_TARGET_PX)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Throwable) {
                                ArtworkResult.Placeholder
                            }
                        finishArtworkRequest(claim.request, result)
                        return
                    } catch (cancellation: CancellationException) {
                        cancelArtworkRequest(claim.request)
                        throw cancellation
                    }
                }
            }
        }
    }

    private suspend fun claimArtworkRequest(track: Track): ArtworkRequestClaim =
        artworkRequestMutex.withLock {
            activeArtworkRequests[track.id]
                ?.takeIf { it.dateModifiedMs == track.dateModifiedMs }
                ?.let { return@withLock ArtworkRequestClaim.Await(it.completion) }
            if (artworkByTrackId.value[track.id]?.dateModifiedMs == track.dateModifiedMs) {
                return@withLock ArtworkRequestClaim.Cached
            }

            val loadingState =
                TrackArtworkState(
                    dateModifiedMs = track.dateModifiedMs,
                    artwork = ArtworkResult.Placeholder,
                )
            val request =
                ActiveArtworkRequest(
                    trackId = track.id,
                    dateModifiedMs = track.dateModifiedMs,
                    loadingState = loadingState,
                )
            activeArtworkRequests[track.id] = request
            artworkByTrackId.value = artworkByTrackId.value + (track.id to loadingState)
            ArtworkRequestClaim.Load(request)
        }

    private suspend fun finishArtworkRequest(
        request: ActiveArtworkRequest,
        result: ArtworkResult,
    ) {
        withContext(NonCancellable) {
            artworkRequestMutex.withLock {
                if (activeArtworkRequests[request.trackId] === request) {
                    activeArtworkRequests.remove(request.trackId)
                    artworkByTrackId.update { cached ->
                        if (cached[request.trackId] === request.loadingState) {
                            cached + (request.trackId to request.loadingState.copy(artwork = result))
                        } else {
                            cached
                        }
                    }
                }
                request.completion.complete(ArtworkRequestOutcome.FINISHED)
            }
        }
    }

    private suspend fun cancelArtworkRequest(request: ActiveArtworkRequest) {
        withContext(NonCancellable) {
            artworkRequestMutex.withLock {
                val ownsRequestSlot = activeArtworkRequests[request.trackId] === request
                if (ownsRequestSlot) {
                    activeArtworkRequests.remove(request.trackId)
                    artworkByTrackId.update { cached ->
                        if (cached[request.trackId] === request.loadingState) {
                            cached - request.trackId
                        } else {
                            cached
                        }
                    }
                }
                request.completion.complete(
                    if (ownsRequestSlot) ArtworkRequestOutcome.RETRY else ArtworkRequestOutcome.FINISHED,
                )
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
    val isSelectionMode: Boolean,
    val selectedTrackIds: Set<TrackId>,
    val batchResult: BatchTrackActionResult?,
    val isBatchActionRunning: Boolean,
)

private data class TracksLibraryState(
    val tracks: List<Track> = emptyList(),
    val isLoaded: Boolean = false,
)

private data class SortedTracksState(
    val tracks: List<Track>,
    val visibleTrackIds: Set<TrackId>,
    val isLibraryLoaded: Boolean,
    val sort: TrackSort,
)

private data class ActiveArtworkRequest(
    val trackId: TrackId,
    val dateModifiedMs: Long,
    val loadingState: TrackArtworkState,
    val completion: CompletableDeferred<ArtworkRequestOutcome> = CompletableDeferred(),
)

private sealed interface ArtworkRequestClaim {
    data object Cached : ArtworkRequestClaim

    data class Await(val completion: CompletableDeferred<ArtworkRequestOutcome>) : ArtworkRequestClaim

    data class Load(val request: ActiveArtworkRequest) : ArtworkRequestClaim
}

private enum class ArtworkRequestOutcome {
    FINISHED,
    RETRY,
}

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
