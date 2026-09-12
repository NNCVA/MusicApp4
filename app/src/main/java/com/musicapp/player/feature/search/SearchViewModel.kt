package com.musicapp.player.feature.search

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
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.HistoryRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.feature.tracks.TrackSection
import com.musicapp.player.feature.tracks.TrackSort
import com.musicapp.player.feature.tracks.TrackSortField
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.feature.tracks.comparator
import com.musicapp.player.feature.tracks.groupTracksIntoSections
import com.musicapp.player.feature.tracks.sectionStartPositions
import com.musicapp.player.navigation.SearchScopeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val scopeType: SearchScopeType = SearchScopeType.ALL_TRACKS,
    val playlistId: Long? = null,
    val isLoaded: Boolean = false,
    val totalCandidateCount: Int = 0,
    val query: String = "",
    val filteredTracks: List<Track> = emptyList(),
    val sort: TrackSort = TrackSort.DEFAULT,
    val sections: List<TrackSection> = emptyList(),
    val sectionPositions: Map<String, Int> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val infoTrack: Track? = null,
    val infoMetadata: AdvancedTrackMetadata? = null,
    val batchResult: BatchTrackActionResult? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val historyRepository: HistoryRepository,
    private val playbackController: PlaybackControllerFacade,
    private val batchTrackActionExecutor: BatchTrackActionExecutor,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val computationDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playlistRepository: PlaylistRepository,
        historyRepository: HistoryRepository,
        playbackController: PlaybackControllerFacade,
        batchTrackActionExecutor: BatchTrackActionExecutor,
        trackMetadataRepository: TrackMetadataRepository,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playlistRepository = playlistRepository,
        historyRepository = historyRepository,
        playbackController = playbackController,
        batchTrackActionExecutor = batchTrackActionExecutor,
        trackMetadataRepository = trackMetadataRepository,
        computationDispatcher = Dispatchers.Default,
    )

    private val scopeConfig = MutableStateFlow<Pair<SearchScopeType, Long?>>(SearchScopeType.ALL_TRACKS to null)
    private val queryState = MutableStateFlow("")
    private val sortState = MutableStateFlow(TrackSort.DEFAULT)
    private val isSelectionModeState = MutableStateFlow(false)
    private val selectedTrackIdsState = MutableStateFlow<Set<TrackId>>(emptySet())
    private val infoTrackState = MutableStateFlow<Track?>(null)
    private val infoMetadataState = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val batchResultState = MutableStateFlow<BatchTrackActionResult?>(null)
    private var batchJob: Job? = null

    fun initScope(scopeType: SearchScopeType, playlistId: Long? = null) {
        val newScope = scopeType to playlistId
        if (scopeConfig.value != newScope) {
            scopeConfig.value = newScope
            resetSearch()
        }
    }

    private val candidatesFlow = scopeConfig.flatMapLatest { (scope, pId) ->
        when (scope) {
            SearchScopeType.ALL_TRACKS -> {
                mediaLibraryRepository.observeTracks(includeHidden = false)
            }
            SearchScopeType.PLAYLIST -> {
                if (pId == null) {
                    flowOf(emptyList())
                } else {
                    combine(
                        playlistRepository.observePlaylist(PlaylistId(pId)),
                        mediaLibraryRepository.observeTracks(includeHidden = false),
                    ) { playlist, allTracks ->
                        if (playlist == null) {
                            emptyList()
                        } else {
                            val trackMap = allTracks.associateBy { it.id }
                            playlist.trackIds.mapNotNull { trackMap[it] }
                        }
                    }
                }
            }
            SearchScopeType.HISTORY -> {
                combine(
                    historyRepository.observeHistory(),
                    mediaLibraryRepository.observeTracks(includeHidden = false),
                ) { histories, allTracks ->
                    val trackMap = allTracks.associateBy { it.id }
                    val seen = mutableSetOf<TrackId>()
                    histories.mapNotNull { history ->
                        if (seen.add(history.trackId)) {
                            trackMap[history.trackId]
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }.flowOn(computationDispatcher)

    val uiState: StateFlow<SearchUiState> = combine(
        scopeConfig,
        candidatesFlow,
        queryState,
        sortState,
        isSelectionModeState,
        selectedTrackIdsState,
        playlistRepository.observePlaylists().onStart { emit(emptyList()) },
        infoTrackState,
        infoMetadataState,
        batchResultState,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val scopePair = args[0] as Pair<SearchScopeType, Long?>
        val scopeType = scopePair.first
        val playlistId = scopePair.second
        @Suppress("UNCHECKED_CAST")
        val candidates = args[1] as List<Track>
        val query = args[2] as String
        val sort = args[3] as TrackSort
        val isSelection = args[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[5] as Set<TrackId>
        @Suppress("UNCHECKED_CAST")
        val playlists = args[6] as List<Playlist>
        val infoTrack = args[7] as Track?
        val infoMetadata = args[8] as AdvancedTrackMetadata?
        val batchResult = args[9] as BatchTrackActionResult?

        val trimmedQuery = query.trim()
        val filtered = if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            candidates.filter { track ->
                track.title.contains(trimmedQuery, ignoreCase = true) ||
                    track.artistName.contains(trimmedQuery, ignoreCase = true) ||
                    (track.albumTitle?.contains(trimmedQuery, ignoreCase = true) == true)
            }.sortedWith(sort.comparator())
        }

        val sections = if (filtered.isEmpty()) {
            emptyList()
        } else {
            groupTracksIntoSections(filtered, sort.field, sort.direction)
        }
        val sectionPositions = if (sections.isEmpty()) {
            emptyMap()
        } else {
            sectionStartPositions(sections, sort.direction)
        }

        val validSelected = selectedIds.filter { id -> filtered.any { it.id == id } }.toSet()

        SearchUiState(
            scopeType = scopeType,
            playlistId = playlistId,
            isLoaded = true,
            totalCandidateCount = candidates.size,
            query = query,
            filteredTracks = filtered,
            sort = sort,
            sections = sections,
            sectionPositions = sectionPositions,
            isSelectionMode = isSelection && validSelected.isNotEmpty(),
            selectedTrackIds = validSelected,
            playlists = playlists,
            infoTrack = infoTrack,
            infoMetadata = infoMetadata,
            batchResult = batchResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun onQueryChange(newQuery: String) {
        queryState.value = newQuery
    }

    fun clearQuery() {
        queryState.value = ""
    }

    fun resetSearch() {
        queryState.value = ""
        sortState.value = TrackSort.DEFAULT
        isSelectionModeState.value = false
        selectedTrackIdsState.value = emptySet()
        infoTrackState.value = null
        infoMetadataState.value = null
        batchResultState.value = null
        batchJob?.cancel()
        batchJob = null
    }

    fun onSortSelected(field: TrackSortField) {
        sortState.update { it.next(field) }
    }

    fun playTrack(track: Track) {
        val orderedTrackIds =
            uiState.value.filteredTracks
                .filter { it.availability == Availability.AVAILABLE }
                .map(Track::id)
        if (track.id !in orderedTrackIds) return
        val source = when (uiState.value.scopeType) {
            SearchScopeType.ALL_TRACKS -> PlaybackContextSource.TRACKS
            SearchScopeType.PLAYLIST -> PlaybackContextSource.PLAYLIST
            SearchScopeType.HISTORY -> PlaybackContextSource.HISTORY
        }
        val sourceId = if (uiState.value.scopeType == SearchScopeType.PLAYLIST) {
            uiState.value.playlistId?.toString()
        } else {
            null
        }
        playbackController.play(
            PlaybackContext(
                source = source,
                orderedTrackIds = orderedTrackIds,
                selectedTrackId = track.id,
                sourceId = sourceId,
            ),
        )
    }

    fun playAll() {
        val orderedTrackIds =
            uiState.value.filteredTracks
                .filter { it.availability == Availability.AVAILABLE }
                .map(Track::id)
        val firstTrackId = orderedTrackIds.firstOrNull() ?: return
        val source = when (uiState.value.scopeType) {
            SearchScopeType.ALL_TRACKS -> PlaybackContextSource.TRACKS
            SearchScopeType.PLAYLIST -> PlaybackContextSource.PLAYLIST
            SearchScopeType.HISTORY -> PlaybackContextSource.HISTORY
        }
        val sourceId = if (uiState.value.scopeType == SearchScopeType.PLAYLIST) {
            uiState.value.playlistId?.toString()
        } else {
            null
        }
        playbackController.play(
            PlaybackContext(
                source = source,
                orderedTrackIds = orderedTrackIds,
                selectedTrackId = firstTrackId,
                sourceId = sourceId,
            ),
        )
    }

    fun enterSelection(trackId: TrackId) {
        isSelectionModeState.value = true
        selectedTrackIdsState.update { it + trackId }
    }

    fun toggleSelection(trackId: TrackId) {
        selectedTrackIdsState.update { current ->
            if (trackId in current) {
                val next = current - trackId
                if (next.isEmpty()) {
                    isSelectionModeState.value = false
                }
                next
            } else {
                isSelectionModeState.value = true
                current + trackId
            }
        }
    }

    fun toggleSelectAll() {
        val allTrackIds = uiState.value.filteredTracks.map(Track::id).toSet()
        selectedTrackIdsState.update { current ->
            if (current.size >= allTrackIds.size && allTrackIds.isNotEmpty()) {
                emptySet()
            } else {
                allTrackIds
            }
        }
    }

    fun clearSelection() {
        selectedTrackIdsState.value = emptySet()
        isSelectionModeState.value = false
    }

    fun exitSelection() {
        clearSelection()
    }

    fun onBack(): Boolean {
        if (infoTrackState.value != null) {
            infoTrackState.value = null
            infoMetadataState.value = null
            return true
        }
        if (isSelectionModeState.value) {
            clearSelection()
            return true
        }
        return false
    }

    fun showTrackInfo(track: Track) {
        infoTrackState.value = track
        viewModelScope.launch {
            try {
                infoMetadataState.value = trackMetadataRepository.read(track)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                infoMetadataState.value = null
            }
        }
    }

    fun dismissTrackInfo() {
        infoTrackState.value = null
        infoMetadataState.value = null
    }

    fun onAddToQueue(trackId: TrackId) {
        executeBatch(BatchTrackAction.AddToQueue, listOf(trackId))
    }

    fun onPlayNext(trackId: TrackId) {
        executeBatch(BatchTrackAction.PlayNext, listOf(trackId))
    }

    fun onHideTrack(trackId: TrackId) {
        executeBatch(BatchTrackAction.Hide, listOf(trackId))
    }

    fun onAddToPlaylist(playlistId: PlaylistId, trackId: TrackId? = null) {
        val targets = trackId?.let { listOf(it) } ?: selectedTrackIdsState.value.toList()
        if (targets.isNotEmpty()) {
            executeBatch(BatchTrackAction.AddToPlaylist(playlistId), targets)
        }
    }

    fun onBatchAddToQueue() {
        val selected = selectedTrackIdsState.value.toList()
        if (selected.isNotEmpty()) {
            executeBatch(BatchTrackAction.AddToQueue, selected)
        }
    }

    fun acknowledgeBatchResult() {
        batchResultState.value = null
    }

    private fun executeBatch(
        action: BatchTrackAction,
        requestedTrackIds: List<TrackId>,
    ) {
        val visibleTrackIds = uiState.value.filteredTracks.mapTo(hashSetOf(), Track::id)
        val orderedTrackIds = requestedTrackIds.filter { it in visibleTrackIds }.distinct()
        if (orderedTrackIds.isEmpty()) return
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            val result = batchTrackActionExecutor.execute(action, orderedTrackIds)
            batchResultState.value = result
            if (result is BatchTrackActionResult.Completed) {
                val completedIds = orderedTrackIds.toHashSet()
                selectedTrackIdsState.value =
                    selectedTrackIdsState.value
                        .filterNot(completedIds::contains)
                        .toCollection(linkedSetOf())
                if (selectedTrackIdsState.value.isEmpty()) {
                    isSelectionModeState.value = false
                }
            }
        }
    }
}
