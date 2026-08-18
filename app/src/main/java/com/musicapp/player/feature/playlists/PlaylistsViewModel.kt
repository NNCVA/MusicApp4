package com.musicapp.player.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.Playlist
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.repository.PlaylistRepository
import com.musicapp.player.data.repository.PlaylistTrackChangeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
)

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val selectedTrackIds: Set<TrackId> = emptySet(),
    val operationMessage: PlaylistOperationMessage? = null,
    val lastRemovalResult: PlaylistTrackChangeResult? = null,
    val playbackFeedback: PlaylistPlaybackPreparation? = null,
)

val PlaylistDetailUiState.isSelectionMode: Boolean
    get() = selectedTrackIds.isNotEmpty()

val PlaylistDetailUiState.selectedTrackIdsInOrder: List<TrackId>
    get() = tracks.map(Track::id).filter(selectedTrackIds::contains)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    repository: PlaylistRepository,
    private val useCase: PlaylistUseCase,
) : ViewModel() {
    private val operationMessage = MutableStateFlow<PlaylistOperationMessage?>(null)

    val uiState: StateFlow<PlaylistsUiState> =
        combine(repository.observePlaylists(), operationMessage, ::PlaylistsUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistsUiState())

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
class PlaylistDetailViewModel @Inject constructor(
    playlistRepository: PlaylistRepository,
    mediaLibraryRepository: MediaLibraryRepository,
    private val useCase: PlaylistUseCase,
    private val playbackController: PlaybackControllerFacade,
) : ViewModel() {
    private val selectedPlaylistId = MutableStateFlow<PlaylistId?>(null)
    private val selectedTrackIds = MutableStateFlow<Set<TrackId>>(emptySet())
    private val operationMessage = MutableStateFlow<PlaylistOperationMessage?>(null)
    private val lastRemovalResult = MutableStateFlow<PlaylistTrackChangeResult?>(null)
    private val playbackFeedback = MutableStateFlow<PlaylistPlaybackPreparation?>(null)

    val uiState: StateFlow<PlaylistDetailUiState> =
        combine(
            playlistRepository.observePlaylists(),
            mediaLibraryRepository.observeTracks(),
            selectedPlaylistId,
            combine(
                selectedTrackIds,
                operationMessage,
                lastRemovalResult,
                playbackFeedback,
                ::PlaylistDetailControls,
            ),
        ) { playlists, tracks, playlistId, controls ->
            val playlist = playlists.firstOrNull { it.id == playlistId }
            val tracksById = tracks.associateBy(Track::id)
            val orderedTracks = playlist?.trackIds.orEmpty().mapNotNull(tracksById::get)
            PlaylistDetailUiState(
                playlist = playlist,
                tracks = orderedTracks,
                selectedTrackIds = controls.selectedTrackIds.intersect(orderedTracks.mapTo(mutableSetOf(), Track::id)),
                operationMessage = controls.operationMessage,
                lastRemovalResult = controls.lastRemovalResult,
                playbackFeedback = controls.playbackFeedback,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistDetailUiState())

    fun open(playlistId: PlaylistId) {
        if (selectedPlaylistId.value != playlistId) clearSelection()
        selectedPlaylistId.value = playlistId
    }

    fun playAll() = play(selectedTrackId = null, reportResult = true)

    fun playTrack(trackId: TrackId) = play(trackId, reportResult = false)

    fun toggleSelection(trackId: TrackId) {
        if (uiState.value.tracks.none { it.id == trackId }) return
        val currentSelection = selectedTrackIds.value
        selectedTrackIds.value =
            if (trackId in currentSelection) {
                currentSelection - trackId
            } else {
                currentSelection + trackId
            }
    }

    fun selectAll() {
        selectedTrackIds.value = uiState.value.tracks.mapTo(linkedSetOf(), Track::id)
    }

    fun clearSelection() {
        selectedTrackIds.value = emptySet()
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

    fun clearMessage() {
        operationMessage.value = null
    }

    fun acknowledgePlaybackFeedback() {
        playbackFeedback.value = null
    }

    private fun play(selectedTrackId: TrackId?, reportResult: Boolean) {
        val state = uiState.value
        val playlist = state.playlist ?: return
        val preparation = PlaylistPlaybackContextFactory.prepare(playlist, state.tracks, selectedTrackId)
        if (reportResult) playbackFeedback.value = preparation
        preparation.context?.let(playbackController::play)
    }
}

private const val STOP_TIMEOUT_MS = 5_000L

private data class PlaylistDetailControls(
    val selectedTrackIds: Set<TrackId>,
    val operationMessage: PlaylistOperationMessage?,
    val lastRemovalResult: PlaylistTrackChangeResult?,
    val playbackFeedback: PlaylistPlaybackPreparation?,
)
