package com.musicapp.player.feature.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
) : ViewModel() {
    constructor(
        mediaLibraryRepository: MediaLibraryRepository,
        playbackController: PlaybackControllerFacade,
    ) : this(
        mediaLibraryRepository = mediaLibraryRepository,
        playbackController = playbackController,
        volumeMetadataSource = EmptyFolderVolumeMetadataSource,
    )

    private val selectedFolderId = MutableStateFlow<FolderId?>(null)
    private val folderSort = MutableStateFlow(FolderSort())
    private val trackSort = MutableStateFlow(CategoryTrackSort())

    val uiState: StateFlow<FolderDetailUiState> =
        combine(
            mediaLibraryRepository.observeTracks(),
            volumeMetadataSource.observe().catch { emit(emptyList()) },
            selectedFolderId,
            folderSort,
            trackSort,
        ) { tracks, metadata, folderId, currentFolderSort, currentTrackSort ->
            val roots = FolderTree.build(tracks)
            val node = folderId?.let { FolderTree.find(roots, it) }
            val volumeMetadata = node?.let { metadata.associateBy(FolderVolumeMetadata::volumeName)[it.id.volumeName] }
            val isVolumeRoot = node?.isVolumeRoot == true
            val isMusicFolder = node?.hasDirectTracks == true
            FolderDetailUiState(
                folderId = folderId,
                displayName = when {
                    node == null -> null
                    isVolumeRoot -> volumeMetadata?.displayName ?: node.displayName
                    else -> node.displayName
                },
                childFolders = FolderTree.sorted(node?.children.orEmpty(), currentFolderSort),
                directTracks = sortCategoryTracks(node?.directTracks.orEmpty(), currentTrackSort),
                recursiveTracks = sortCategoryTracks(node?.recursiveTracks.orEmpty(), currentTrackSort),
                folderSort = currentFolderSort,
                trackSort = currentTrackSort,
                isBrowserOnly = node != null && !isMusicFolder && node.children.isNotEmpty(),
                isVolumeRoot = isVolumeRoot,
                isMusicFolder = isMusicFolder,
                volumeIsPrimary = volumeMetadata?.isPrimary ?: node?.id?.volumeName.isPrimaryMediaVolumeName(),
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
