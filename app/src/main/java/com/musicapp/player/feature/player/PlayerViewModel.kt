package com.musicapp.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.musicapp.player.R
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackFailureCode
import com.musicapp.player.core.playback.PlaybackStatus
import com.musicapp.player.data.repository.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlayerLoadState { EMPTY, PREPARING, READY, BUFFERING, ERROR }

data class PlayerQueueRow(
    val queueItemId: QueueItemId,
    val track: Track?,
    val isCurrent: Boolean,
)

data class PlayerUiState(
    val loadState: PlayerLoadState = PlayerLoadState.EMPTY,
    @param:StringRes val errorMessageRes: Int? = null,
    val currentTrack: Track? = null,
    val artwork: ArtworkResult = ArtworkResult.Placeholder,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.DEFAULT,
    val queue: List<PlayerQueueRow> = emptyList(),
    val showTrackInfo: Boolean = false,
    val metadata: AdvancedTrackMetadata? = null,
    val metadataLoading: Boolean = false,
    val fullPlayerPage: FullPlayerPage = FullPlayerPage.ARTWORK,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackControllerFacade,
    mediaLibraryRepository: MediaLibraryRepository,
    private val artworkRepository: ArtworkRepository,
    private val metadataRepository: TrackMetadataRepository,
) : ViewModel() {
    private val tracks = mediaLibraryRepository.observeTracks(includeHidden = true)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val artwork = MutableStateFlow<ArtworkResult>(ArtworkResult.Placeholder)
    private val showTrackInfo = MutableStateFlow(false)
    private val metadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val metadataLoading = MutableStateFlow(false)
    private val fullPlayerPage = MutableStateFlow(FullPlayerPage.ARTWORK)
    private val infoState = combine(showTrackInfo, metadata, metadataLoading, fullPlayerPage, ::PlayerInfoState)
    private var metadataJob: Job? = null

    val uiState = combine(
        playbackController.state,
        tracks,
        artwork,
        infoState,
    ) { playback, library, currentArtwork, info ->
        val byId = library.associateBy(Track::id)
        val currentTrack = playback.currentTrackId?.let(byId::get)
        PlayerUiState(
            loadState = playback.playbackStatus.toPlayerLoadState(),
            errorMessageRes = playback.playbackFailure?.code?.messageRes(),
            currentTrack = currentTrack,
            artwork = currentArtwork,
            isPlaying = playback.isPlaying,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs ?: currentTrack?.durationMs ?: 0,
            canSkipPrevious = playback.canSkipPrevious,
            canSkipNext = playback.canSkipNext,
            playbackMode = playback.playbackMode,
            queue = playback.queue.playbackOrder.map { item ->
                PlayerQueueRow(item.id, byId[item.trackId], item.id == playback.queue.currentItemId)
            },
            showTrackInfo = info.visible,
            metadata = info.metadata,
            metadataLoading = info.loading,
            fullPlayerPage = info.page,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    init {
        viewModelScope.launch {
            combine(playbackController.state.map { it.currentTrackId }, tracks) { id, library ->
                id?.let { current -> library.firstOrNull { it.id == current } }
            }.distinctUntilChanged().collectLatest { track ->
                artwork.value = ArtworkResult.Placeholder
                metadataJob?.cancel()
                metadata.value = null
                metadataLoading.value = false
                showTrackInfo.value = false
                if (track != null) artwork.value = artworkRepository.artwork(track, ARTWORK_TARGET_PX)
            }
        }
    }

    fun togglePlayback() {
        if (uiState.value.isPlaying) playbackController.pause() else playbackController.play()
    }

    fun skipPrevious() = playbackController.skipToPrevious()
    fun skipNext() = playbackController.skipToNext()

    fun seekToFraction(fraction: Float) {
        val duration = uiState.value.durationMs
        if (duration <= 0) return
        playbackController.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekToPosition(positionMs: Long) = playbackController.seekTo(positionMs.coerceAtLeast(0))

    fun rewind() = seekBy(-SEEK_INTERVAL_MS)

    fun fastForward() = seekBy(SEEK_INTERVAL_MS)

    fun cyclePlaybackMode() = playbackController.setPlaybackMode(uiState.value.playbackMode.nextMode())
    fun jumpToQueueItem(queueItemId: QueueItemId) = playbackController.jumpToQueueItem(queueItemId)
    fun removeFromQueue(queueItemId: QueueItemId) = playbackController.removeFromQueue(queueItemId)

    fun showTrackInfo() {
        val track = uiState.value.currentTrack ?: return
        showTrackInfo.value = true
        metadataLoading.value = true
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            val loaded = metadataRepository.read(track)
            if (uiState.value.currentTrack?.id == track.id) {
                metadata.value = loaded
                metadataLoading.value = false
            }
        }
    }

    fun dismissTrackInfo() {
        showTrackInfo.value = false
    }

    fun selectFullPlayerPage(page: FullPlayerPage) {
        fullPlayerPage.value = page
    }

    private fun seekBy(deltaMs: Long) {
        val state = uiState.value
        if (state.durationMs <= 0) return
        val currentPositionMs = state.positionMs.coerceIn(0, state.durationMs)
        val targetPositionMs =
            if (deltaMs < 0) {
                (currentPositionMs + deltaMs).coerceAtLeast(0)
            } else if (currentPositionMs > state.durationMs - deltaMs) {
                state.durationMs
            } else {
                currentPositionMs + deltaMs
            }
        playbackController.seekTo(targetPositionMs)
    }

    private companion object {
        const val ARTWORK_TARGET_PX = 1_024
        const val SEEK_INTERVAL_MS = 10_000L
    }
}

private fun PlaybackStatus.toPlayerLoadState(): PlayerLoadState =
    when (this) {
        PlaybackStatus.IDLE -> PlayerLoadState.EMPTY
        PlaybackStatus.PREPARING -> PlayerLoadState.PREPARING
        PlaybackStatus.BUFFERING -> PlayerLoadState.BUFFERING
        PlaybackStatus.READY,
        PlaybackStatus.PLAYING,
        PlaybackStatus.PAUSED,
        -> PlayerLoadState.READY
        PlaybackStatus.ERROR -> PlayerLoadState.ERROR
    }

@StringRes
internal fun PlaybackFailureCode.messageRes(): Int =
    when (this) {
        PlaybackFailureCode.SOURCE_NOT_FOUND -> R.string.player_error_source_not_found
        PlaybackFailureCode.ACCESS_DENIED -> R.string.player_error_access_denied
        PlaybackFailureCode.UNSUPPORTED_FORMAT -> R.string.player_error_unsupported_format
        PlaybackFailureCode.DECODING_FAILED -> R.string.player_error_decoding_failed
        PlaybackFailureCode.AUDIO_OUTPUT_FAILED -> R.string.player_error_audio_output_failed
        PlaybackFailureCode.IO_ERROR -> R.string.player_error_io
        PlaybackFailureCode.UNKNOWN -> R.string.player_error_unknown
    }

private data class PlayerInfoState(
    val visible: Boolean,
    val metadata: AdvancedTrackMetadata?,
    val loading: Boolean,
    val page: FullPlayerPage,
)
