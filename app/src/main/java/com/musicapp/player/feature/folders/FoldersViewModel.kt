package com.musicapp.player.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.FakePlaylistRepository
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import com.musicapp.player.feature.playlists.PlaylistUseCase
import com.musicapp.player.feature.tracks.batch.BatchTrackAction
import com.musicapp.player.feature.tracks.batch.BatchTrackActionExecutor
import com.musicapp.player.feature.tracks.batch.BatchTrackActionResult
import com.musicapp.player.feature.tracks.batch.DefaultBatchTrackActionExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoldersUiState(
    val volumes: List<FolderVolumeItem> = emptyList(),
    val musicFolders: List<FolderNode> = emptyList(),
)

data class FolderDetailUiState(
    val folderId: FolderId? = null,
    val displayName: String? = null,
    val childFolders: List<FolderNode> = emptyList(),
    val directTracks: List<Track> = emptyList(),
    val recursiveTracks: List<Track> = emptyList(),
    val folderSort: FolderSort = FolderSort(),
    val trackSort: CategoryTrackSort = CategoryTrackSort(),
    val isBrowserOnly: Boolean = false,
    val isVolumeRoot: Boolean = false,
    val isMusicFolder: Boolean = false,
    val volumeIsPrimary: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val infoTrack: Track? = null,
    val infoMetadata: AdvancedTrackMetadata? = null,
    val isInfoLoading: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val batchResult: BatchTrackActionResult? = null,
    val isBatchActionRunning: Boolean = false,
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val volumeMetadataSource: FolderVolumeMetadataSource,
    private val playbackController: PlaybackControllerFacade,
) : ViewModel() {
    val uiState: StateFlow<FoldersUiState> =
        combine(
            mediaLibraryRepository.observeTracks(),
            volumeMetadataSource.observe().catch { emit(emptyList()) },
        ) { tracks, metadata ->
            val roots = FolderTree.build(tracks)
            val metadataByVolume = metadata.associateBy(FolderVolumeMetadata::volumeName)
            val volumeItems =
                roots.map { root -> metadataByVolume[root.id.volumeName].toVolumeItem(root) }
                    .sortedWith(
                        compareByDescending<FolderVolumeItem> { it.isPrimary }
                            .thenBy {
                                (it.displayName ?: it.folder.displayName).lowercase(Locale.ROOT)
                            }
                            .thenBy { it.displayName ?: it.folder.displayName }
                            .thenBy { it.folder.id.sourceId },
                    )
            FoldersUiState(
                volumes = volumeItems,
                musicFolders = FolderTree.sorted(FolderTree.musicFolders(roots), FolderSort()),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            FoldersUiState(),
        )

    /** Starts playback for a volume root or any folder shortcut using recursive tracks. */
    fun playFolder(folderId: FolderId) {
        val folder =
            FolderTree.find(uiState.value.volumes.map(FolderVolumeItem::folder), folderId)
                ?: return
        CategoryPlaybackContextFactory.create(
            source = PlaybackContextSource.FOLDER,
            sourceId = folder.id.sourceId,
            tracks = folder.recursiveTracks,
        )?.let(playbackController::play)
    }
}

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
    private val volumeMetadataSource: FolderVolumeMetadataSource,
    private val playlistRepository: PlaylistRepository,
    private val playlistUseCase: PlaylistUseCase,
    private val trackMetadataRepository: TrackMetadataRepository,
    private val batchActionExecutor: BatchTrackActionExecutor,
) : ViewModel() {
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playbackController: PlaybackControllerFacade,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playbackController = playbackController,
        volumeMetadataSource = EmptyFolderVolumeMetadataSource,
        playlistRepository = FakePlaylistRepository(),
        playlistUseCase = PlaylistUseCase(FakePlaylistRepository(), com.musicapp.player.core.common.time.Clock { System.currentTimeMillis() }),
        trackMetadataRepository = DefaultFolderTrackMetadataRepository,
        batchActionExecutor = DefaultBatchTrackActionExecutor(
            FakePlaylistRepository(),
            mediaLibraryRepository,
            playbackController,
            com.musicapp.player.core.common.time.Clock { System.currentTimeMillis() },
        ),
    )

    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playbackController: PlaybackControllerFacade,
        volumeMetadataSource: FolderVolumeMetadataSource,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playbackController = playbackController,
        volumeMetadataSource = volumeMetadataSource,
        playlistRepository = FakePlaylistRepository(),
        playlistUseCase = PlaylistUseCase(FakePlaylistRepository(), com.musicapp.player.core.common.time.Clock { System.currentTimeMillis() }),
        trackMetadataRepository = DefaultFolderTrackMetadataRepository,
        batchActionExecutor = DefaultBatchTrackActionExecutor(
            FakePlaylistRepository(),
            mediaLibraryRepository,
            playbackController,
            com.musicapp.player.core.common.time.Clock { System.currentTimeMillis() },
        ),
    )

    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playbackController: PlaybackControllerFacade,
        batchActionExecutor: BatchTrackActionExecutor,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playbackController = playbackController,
        volumeMetadataSource = EmptyFolderVolumeMetadataSource,
        playlistRepository = FakePlaylistRepository(),
        playlistUseCase = PlaylistUseCase(FakePlaylistRepository(), com.musicapp.player.core.common.time.Clock { System.currentTimeMillis() }),
        trackMetadataRepository = DefaultFolderTrackMetadataRepository,
        batchActionExecutor = batchActionExecutor,
    )

    private val selectedFolderId = MutableStateFlow<FolderId?>(null)
    private val folderSort = MutableStateFlow(FolderSort())
    private val trackSort = MutableStateFlow(CategoryTrackSort())
    private val infoTrack = MutableStateFlow<Track?>(null)
    private val infoMetadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val isInfoLoading = MutableStateFlow(false)
    private val isSelectionMode = MutableStateFlow(false)
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val batchResult = MutableStateFlow<BatchTrackActionResult?>(null)
    private val isBatchActionRunning = MutableStateFlow(false)

    private val coreState =
        combine(
            mediaLibraryRepository.observeTracks(),
            volumeMetadataSource.observe().catch { emit(emptyList()) },
            selectedFolderId,
        ) { tracks, metadata, folderId ->
            Triple(tracks, metadata, folderId)
        }

    private val infoState =
        combine(infoTrack, infoMetadata, isInfoLoading) { track, metadata, loading ->
            Triple(track, metadata, loading)
        }

    private val selectionState =
        combine(isSelectionMode, selectedTrackIds, batchResult, isBatchActionRunning) { selectionMode, selectedIds, bResult, batchRunning ->
            SelectionState(selectionMode, selectedIds, bResult, batchRunning)
        }

    val uiState: StateFlow<FolderDetailUiState> =
        combine(
            coreState,
            folderSort,
            trackSort,
            playlistRepository.observePlaylists().onStart { emit(emptyList()) }.catch { emit(emptyList()) },
            combine(infoState, selectionState) { info, selection -> Pair(info, selection) },
        ) { (tracks, metadata, folderId), currentFolderSort, currentTrackSort, playlists, (info, selection) ->
            val (currentInfoTrack, currentInfoMeta, loadingInfo) = info
            val (selectionMode, currentSelectedIds, currentBatchResult, batchRunning) = selection
            val roots = FolderTree.build(tracks)
            val node = folderId?.let { FolderTree.find(roots, it) }
            val volumeMetadata = node?.let { metadata.associateBy(FolderVolumeMetadata::volumeName)[it.id.volumeName] }
            val isVolumeRoot = node?.isVolumeRoot == true
            val isMusicFolder = node?.hasDirectTracks == true
            val sortedDirectTracks = sortCategoryTracks(node?.directTracks.orEmpty(), currentTrackSort)
            val directTrackIds = sortedDirectTracks.mapTo(mutableSetOf(), Track::id)
            val validSelectedIds = currentSelectedIds.intersect(directTrackIds)

            FolderDetailUiState(
                folderId = folderId,
                displayName = when {
                    node == null -> null
                    isVolumeRoot -> volumeMetadata?.displayName ?: node.displayName
                    else -> node.displayName
                },
                childFolders = FolderTree.sorted(
                    node?.children.orEmpty(),
                    FolderSort(field = FolderSortField.NAME, direction = CategorySortDirection.ASCENDING),
                ),
                directTracks = sortedDirectTracks,
                recursiveTracks = sortCategoryTracks(node?.recursiveTracks.orEmpty(), currentTrackSort),
                folderSort = currentFolderSort,
                trackSort = currentTrackSort,
                isBrowserOnly = node != null && !isMusicFolder && node.children.isNotEmpty(),
                isVolumeRoot = isVolumeRoot,
                isMusicFolder = isMusicFolder,
                volumeIsPrimary = volumeMetadata?.isPrimary ?: node?.id?.volumeName.isPrimaryMediaVolumeName(),
                playlists = playlists,
                infoTrack = currentInfoTrack,
                infoMetadata = currentInfoMeta,
                isInfoLoading = loadingInfo,
                isSelectionMode = selectionMode,
                selectedTrackIds = validSelectedIds,
                batchResult = currentBatchResult,
                isBatchActionRunning = batchRunning,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FolderDetailUiState())

    fun open(folderId: FolderId) {
        selectedFolderId.value = folderId
    }

    fun selectFolderSort(field: FolderSortField) {
        folderSort.value = folderSort.value.next(field)
    }

    fun selectTrackSort(field: CategoryTrackSortField) {
        trackSort.value = trackSort.value.next(field)
    }

    fun playAll() = play(selectedTrackId = null)

    fun playTrack(trackId: TrackId) = play(selectedTrackId = trackId)

    fun playTrackNext(trackId: TrackId) {
        playbackController.playNext(listOf(trackId))
    }

    fun addTrackToQueue(trackId: TrackId) {
        playbackController.addToQueue(listOf(trackId))
    }

    fun addTrackToPlaylist(trackId: TrackId, playlistId: PlaylistId) {
        viewModelScope.launch {
            try {
                playlistUseCase.addTracks(playlistId, listOf(trackId))
            } catch (_: Exception) {
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                playlistUseCase.create(name)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
            }
        }
    }

    fun startSelection(trackId: TrackId) {
        isSelectionMode.value = true
        selectedTrackIds.value = setOf(trackId)
    }

    fun toggleSelection(trackId: TrackId) {
        selectedTrackIds.update { current ->
            val updated = if (trackId in current) current - trackId else current + trackId
            if (updated.isEmpty()) {
                isSelectionMode.value = false
            }
            updated
        }
    }

    fun selectAll() {
        val visibleIds = uiState.value.directTracks.mapTo(linkedSetOf(), Track::id)
        selectedTrackIds.value = visibleIds
        if (visibleIds.isNotEmpty()) {
            isSelectionMode.value = true
        }
    }

    fun selectTracks(trackIds: Collection<TrackId>) {
        val targetIds = uiState.value.directTracks.map(Track::id).intersect(trackIds.toSet())
        selectedTrackIds.value = targetIds
        isSelectionMode.value = targetIds.isNotEmpty()
    }

    fun toggleSelectAll() {
        val visibleIds = uiState.value.directTracks.map(Track::id).toSet()
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

    fun addSelectedToQueue() {
        executeBatchAction(selectedTracksInOrder(), BatchTrackAction.AddToQueue)
    }

    fun addSelectedToPlaylist(playlistId: PlaylistId) {
        executeBatchAction(selectedTracksInOrder(), BatchTrackAction.AddToPlaylist(playlistId))
    }

    fun acknowledgeBatchResult() {
        batchResult.value = null
    }

    private fun selectedTracksInOrder(): List<TrackId> {
        val selected = selectedTrackIds.value
        return uiState.value.directTracks.map(Track::id).filter { it in selected }
    }

    private fun executeBatchAction(trackIds: List<TrackId>, action: BatchTrackAction) {
        if (trackIds.isEmpty() || isBatchActionRunning.value) return
        isBatchActionRunning.value = true
        viewModelScope.launch {
            try {
                val result = batchActionExecutor.execute(action, trackIds)
                batchResult.value = result
                if (result is BatchTrackActionResult.Completed) {
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
            val loaded = trackMetadataRepository.read(track)
            if (infoTrack.value?.id == track.id) {
                infoMetadata.value = loaded
                isInfoLoading.value = false
            }
        }
    }

    fun dismissTrackInfo() {
        infoTrack.value = null
        infoMetadata.value = null
        isInfoLoading.value = false
    }

    private fun play(selectedTrackId: TrackId?) {
        val state = uiState.value
        val folderId = state.folderId ?: return
        CategoryPlaybackContextFactory.create(
            source = PlaybackContextSource.FOLDER,
            sourceId = folderId.sourceId,
            tracks = state.recursiveTracks,
            selectedTrackId = selectedTrackId,
        )?.let(playbackController::play)
    }
}

private data class SelectionState(
    val isSelectionMode: Boolean,
    val selectedTrackIds: Set<TrackId>,
    val batchResult: BatchTrackActionResult?,
    val isBatchActionRunning: Boolean,
)

private object DefaultFolderTrackMetadataRepository : TrackMetadataRepository {
    override suspend fun read(track: Track): AdvancedTrackMetadata =
        AdvancedTrackMetadata(
            encoding = "FLAC",
            bitrateBps = 320_000L,
            sampleRateHz = 44100,
            fileSizeBytes = track.sizeBytes,
            isReadable = true,
        )
}

private const val STOP_TIMEOUT_MS = 5_000L
private fun FolderVolumeMetadata?.toVolumeItem(folder: FolderNode): FolderVolumeItem =
    FolderVolumeItem(
        folder = folder,
        displayName = this?.displayName,
        rootPath = this?.rootPath,
        isPrimary = this?.isPrimary ?: folder.id.volumeName.isPrimaryMediaVolumeName(),
        usedBytes = this?.usedBytes,
        totalBytes = this?.totalBytes,
    )

private fun String?.isPrimaryMediaVolumeName(): Boolean =
    this == "external" || this == "external_primary"
