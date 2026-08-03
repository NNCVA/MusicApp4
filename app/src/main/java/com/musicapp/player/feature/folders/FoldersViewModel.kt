package com.musicapp.player.feature.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.PlaybackContextSource
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.feature.category.CategoryPlaybackContextFactory
import com.musicapp.player.feature.category.CategorySortDirection
import com.musicapp.player.feature.category.CategoryTrackSort
import com.musicapp.player.feature.category.CategoryTrackSortField
import com.musicapp.player.feature.category.next
import com.musicapp.player.feature.category.sortCategoryTracks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FoldersUiState(
    val roots: List<FolderNode> = emptyList(),
    val sort: FolderSort = FolderSort(),
)

data class FolderDetailUiState(
    val folderId: FolderId? = null,
    val displayName: String? = null,
    val childFolders: List<FolderNode> = emptyList(),
    val directTracks: List<Track> = emptyList(),
    val recursiveTracks: List<Track> = emptyList(),
    val folderSort: FolderSort = FolderSort(),
    val trackSort: CategoryTrackSort = CategoryTrackSort(),
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sort = MutableStateFlow(restoreFolderSort(savedStateHandle))

    val uiState: StateFlow<FoldersUiState> =
        combine(mediaLibraryRepository.observeTracks(), sort) { tracks, currentSort ->
            FoldersUiState(
                roots = FolderTree.sorted(FolderTree.build(tracks), currentSort),
                sort = currentSort,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FoldersUiState(sort = sort.value))

    fun selectSort(field: FolderSortField) {
        sort.value = sort.value.next(field)
        savedStateHandle[FOLDER_SORT_FIELD_KEY] = sort.value.field.name
        savedStateHandle[FOLDER_SORT_DIRECTION_KEY] = sort.value.direction.name
    }
}

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    mediaLibraryRepository: MediaLibraryRepository,
    private val playbackController: PlaybackControllerFacade,
) : ViewModel() {
    private val selectedFolderId = MutableStateFlow<FolderId?>(null)
    private val folderSort = MutableStateFlow(FolderSort())
    private val trackSort = MutableStateFlow(CategoryTrackSort())

    val uiState: StateFlow<FolderDetailUiState> =
        combine(
            mediaLibraryRepository.observeTracks(),
            selectedFolderId,
            folderSort,
            trackSort,
        ) { tracks, folderId, currentFolderSort, currentTrackSort ->
            val node = folderId?.let { FolderTree.find(FolderTree.build(tracks), it) }
            FolderDetailUiState(
                folderId = folderId,
                displayName = node?.displayName,
                childFolders = FolderTree.sorted(node?.children.orEmpty(), currentFolderSort),
                directTracks = sortCategoryTracks(node?.directTracks.orEmpty(), currentTrackSort),
                recursiveTracks = sortCategoryTracks(node?.recursiveTracks.orEmpty(), currentTrackSort),
                folderSort = currentFolderSort,
                trackSort = currentTrackSort,
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
private const val FOLDER_SORT_FIELD_KEY = "folders.sort.field"
private const val FOLDER_SORT_DIRECTION_KEY = "folders.sort.direction"

private fun restoreFolderSort(handle: SavedStateHandle): FolderSort {
    val field = handle.get<String>(FOLDER_SORT_FIELD_KEY)?.let { stored ->
        FolderSortField.entries.firstOrNull { it.name == stored }
    } ?: FolderSortField.NAME
    val direction = handle.get<String>(FOLDER_SORT_DIRECTION_KEY)?.let { stored ->
        CategorySortDirection.entries.firstOrNull { it.name == stored }
    } ?: if (field == FolderSortField.NAME) CategorySortDirection.ASCENDING else CategorySortDirection.DESCENDING
    return FolderSort(field, direction)
}
