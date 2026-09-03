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
    )

    private val selectedFolderId = MutableStateFlow<FolderId?>(null)
    private val folderSort = MutableStateFlow(FolderSort())
    private val trackSort = MutableStateFlow(CategoryTrackSort())
    private val infoTrack = MutableStateFlow<Track?>(null)
    private val infoMetadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val isInfoLoading = MutableStateFlow(false)

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

    val uiState: StateFlow<FolderDetailUiState> =
        combine(
            coreState,
            folderSort,
            trackSort,
            playlistRepository.observePlaylists().onStart { emit(emptyList()) }.catch { emit(emptyList()) },
            infoState,
        ) { (tracks, metadata, folderId), currentFolderSort, currentTrackSort, playlists, (currentInfoTrack, currentInfoMeta, loadingInfo) ->
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
                childFolders = FolderTree.sorted(
                    node?.children.orEmpty(),
                    FolderSort(field = FolderSortField.NAME, direction = CategorySortDirection.ASCENDING),
                ),
                directTracks = sortCategoryTracks(node?.directTracks.orEmpty(), currentTrackSort),
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
