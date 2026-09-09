package com.musicapp.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.musicapp.player.R
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.core.common.time.SystemClock
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.designsystem.motion.PlayerMotionTokens
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.metadata.TrackMetadataRepository
import com.musicapp.player.core.playback.PlaybackControllerFacade
import com.musicapp.player.core.playback.PlaybackEvent
import com.musicapp.player.core.playback.PlaybackFailureCode
import com.musicapp.player.core.playback.PlaybackStatus
import com.musicapp.player.core.playback.timer.SleepTimerStatus
import com.musicapp.player.data.repository.MediaLibraryRepository
import com.musicapp.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlayerLoadState { EMPTY, PREPARING, READY, BUFFERING, ERROR }

data class PlayerQueueRow(
    val queueItemId: QueueItemId,
    val track: Track?,
    val isCurrent: Boolean,
)

private data class LoadedArtwork(
    val result: ArtworkResult = ArtworkResult.Placeholder,
    val trackId: TrackId? = null,
)

data class PlayerUiState(
    val loadState: PlayerLoadState = PlayerLoadState.EMPTY,
    @param:StringRes val errorMessageRes: Int? = null,
    val currentTrack: Track? = null,
    val artwork: ArtworkResult = ArtworkResult.Placeholder,
    val artworkTrackId: TrackId? = null,
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
    val sleepTimer: SleepTimerStatus? = null,
    val showSleepTimer: Boolean = false,
    val savedSleepTimerDurationMinutes: Int = AppSettings.DEFAULT_SLEEP_TIMER_DURATION_MINUTES,
    val savedSleepTimerExtendToEndOfTrack: Boolean = false,
    val slideDirection: TrackSlideDirection = TrackSlideDirection.NONE,
)

data class PlayerShellState(
    val currentTrackId: TrackId? = null,
) {
    val isPlayerVisible: Boolean get() = currentTrackId != null
}

@HiltViewModel
class PlayerViewModel(
    private val playbackController: PlaybackControllerFacade,
    mediaLibraryRepository: MediaLibraryRepository,
    private val artworkRepository: ArtworkRepository,
    private val metadataRepository: TrackMetadataRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = SystemClock(),
    private val computationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        playbackController: PlaybackControllerFacade,
        mediaLibraryRepository: MediaLibraryRepository,
        artworkRepository: ArtworkRepository,
        metadataRepository: TrackMetadataRepository,
        settingsRepository: SettingsRepository,
    ) : this(playbackController, mediaLibraryRepository, artworkRepository, metadataRepository,
        settingsRepository, SystemClock(), Dispatchers.Default)

    private val tracksById = mediaLibraryRepository.observeTracks(includeHidden = true)
        .distinctUntilChanged()
        .map { it.associateBy(Track::id) }
        .flowOn(computationDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    private val currentTrack = combine(
        playbackController.state.map { it.currentTrackId }.distinctUntilChanged(),
        tracksById,
    ) { id, byId -> id?.let(byId::get) }.distinctUntilChanged()
    private val queueRows = combine(
        playbackController.state.map { it.queue }.distinctUntilChanged(),
        tracksById,
    ) { queue, byId ->
        queue.playbackOrder.map { item ->
            PlayerQueueRow(item.id, byId[item.trackId], item.id == queue.currentItemId)
        }
    }.flowOn(computationDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Artwork and its ready track ID are published atomically to keep transitions in lockstep. */
    private val loadedArtwork = MutableStateFlow(LoadedArtwork())
    private val showTrackInfo = MutableStateFlow(false)
    private val metadata = MutableStateFlow<AdvancedTrackMetadata?>(null)
    private val metadataLoading = MutableStateFlow(false)
    private val fullPlayerPage = MutableStateFlow(FullPlayerPage.ARTWORK)
    private val infoState = combine(showTrackInfo, metadata, metadataLoading, fullPlayerPage, ::PlayerInfoState)
    private val showSleepTimer = MutableStateFlow(false)
    private val sleepTimerDialogState = combine(
        showSleepTimer,
        settingsRepository.settings,
    ) { show, settings ->
        Triple(show, settings.sleepTimerDurationMinutes, settings.sleepTimerExtendToEndOfTrack)
    }
    private val dialogsState = combine(
        infoState,
        sleepTimerDialogState,
    ) { info, timerDialog ->
        info to timerDialog
    }
    private var metadataJob: Job? = null
    private val _expandRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val expandRequests: SharedFlow<Unit> = _expandRequests.asSharedFlow()
    val events: Flow<PlaybackEvent> = playbackController.events

    fun expandPlayer() {
        _expandRequests.tryEmit(Unit)
    }

    private var pendingExplicitDirection: TrackSlideDirection? = null
    private var previousTrackId: TrackId? = null
    private var currentSlideDirection: TrackSlideDirection = TrackSlideDirection.NONE

    val shellState: StateFlow<PlayerShellState> = currentTrack
        .map { PlayerShellState(currentTrackId = it?.id) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerShellState())

    val uiState = combine(
        playbackController.state,
        combine(tracksById, queueRows) { byId, rows -> byId to rows },
        loadedArtwork,
        dialogsState,
    ) { playback, (byId, rows), loadedArtwork, (info, timerDialog) ->
        val currentTrack = playback.currentTrackId?.let(byId::get)
        val newTrackId = currentTrack?.id
        if (newTrackId != previousTrackId) {
            currentSlideDirection = when {
                previousTrackId == null -> TrackSlideDirection.NONE
                pendingExplicitDirection != null -> {
                    val dir = pendingExplicitDirection!!
                    pendingExplicitDirection = null
                    dir
                }
                else -> {
                    val oldIndex = rows.indexOfFirst { it.track?.id == previousTrackId }
                    val newIndex = rows.indexOfFirst { it.track?.id == newTrackId }
                    when {
                        oldIndex != -1 && newIndex != -1 -> {
                            if (newIndex > oldIndex) {
                                TrackSlideDirection.FORWARD
                            } else if (newIndex < oldIndex) {
                                if (oldIndex == rows.lastIndex && newIndex == 0) {
                                    TrackSlideDirection.FORWARD
                                } else if (oldIndex == 0 && newIndex == rows.lastIndex) {
                                    TrackSlideDirection.BACKWARD
                                } else {
                                    TrackSlideDirection.BACKWARD
                                }
                            } else {
                                TrackSlideDirection.NONE
                            }
                        }
                        else -> TrackSlideDirection.NONE
                    }
                }
            }
            previousTrackId = newTrackId
        } else {
            pendingExplicitDirection = null
        }
        PlayerUiState(
            loadState = playback.playbackStatus.toPlayerLoadState(),
            errorMessageRes = playback.playbackFailure?.code?.messageRes(),
            currentTrack = currentTrack,
            artwork = loadedArtwork.result,
            artworkTrackId = loadedArtwork.trackId,
            isPlaying = playback.isPlaying,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs ?: currentTrack?.durationMs ?: 0,
            canSkipPrevious = playback.canSkipPrevious,
            canSkipNext = playback.canSkipNext,
            playbackMode = playback.playbackMode,
            queue = rows,
            showTrackInfo = info.visible,
            metadata = info.metadata,
            metadataLoading = info.loading,
            fullPlayerPage = info.page,
            sleepTimer = playback.sleepTimer,
            showSleepTimer = timerDialog.first,
            savedSleepTimerDurationMinutes = timerDialog.second,
            savedSleepTimerExtendToEndOfTrack = timerDialog.third,
            slideDirection = currentSlideDirection,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    init {
        viewModelScope.launch {
            currentTrack.collectLatest { track ->
                metadataJob?.cancel()
                metadata.value = null
                metadataLoading.value = false
                showTrackInfo.value = false
                if (track != null) {
                    val result = artworkRepository.artwork(track, ARTWORK_TARGET_PX)
                    loadedArtwork.value = LoadedArtwork(result = result, trackId = track.id)
                    if (skipDebouncePending) {
                        // Start the perceptual debounce window when the target artwork is ready,
                        // so a slow local decode cannot let the next tap overtake the transition.
                        lastSkipClickTimeMs = clock.currentTimeMillis()
                        skipDebouncePending = false
                        pendingSkipOriginTrackId = null
                    }
                } else {
                    loadedArtwork.value = LoadedArtwork()
                }
            }
        }
    }

    private var lastTogglePlaybackTimeMs = -THROTTLE_WINDOW_MS
    private var lastSkipClickTimeMs = -PlayerMotionTokens.TRACK_CHANGE_DURATION_MS.toLong()
    private var skipDebouncePending = false
    private var pendingSkipOriginTrackId: TrackId? = null

    fun togglePlayback() {
        val now = clock.currentTimeMillis()
        if (now - lastTogglePlaybackTimeMs in 0 until THROTTLE_WINDOW_MS) return
        lastTogglePlaybackTimeMs = now
        if (uiState.value.isPlaying) playbackController.pause() else playbackController.play()
    }

    fun skipPrevious() {
        if (!acceptSkipClick()) return
        pendingExplicitDirection = TrackSlideDirection.BACKWARD
        playbackController.skipToPrevious()
    }

    fun skipNext() {
        if (!acceptSkipClick()) return
        pendingExplicitDirection = TrackSlideDirection.FORWARD
        playbackController.skipToNext()
    }

    private fun acceptSkipClick(): Boolean {
        val now = clock.currentTimeMillis()
        val elapsed = now - lastSkipClickTimeMs
        if (skipDebouncePending) {
            val currentTrackId = uiState.value.currentTrack?.id
            val targetHasChanged = currentTrackId != null && currentTrackId != pendingSkipOriginTrackId
            if (targetHasChanged || elapsed < PlayerMotionTokens.TRACK_CHANGE_DURATION_MS.toLong()) {
                return false
            }
            // A controller that could not advance the queue has no artwork-ready callback to
            // release the pending state; allow the normal fixed window to recover here.
            skipDebouncePending = false
            pendingSkipOriginTrackId = null
        }
        if (elapsed in 0 until PlayerMotionTokens.TRACK_CHANGE_DURATION_MS.toLong()) return false
        lastSkipClickTimeMs = now
        skipDebouncePending = true
        pendingSkipOriginTrackId = uiState.value.currentTrack?.id
        return true
    }

    fun seekToFraction(fraction: Float) {
        val duration = uiState.value.durationMs
        if (duration <= 0) return
        playbackController.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekToPosition(positionMs: Long) = playbackController.seekTo(positionMs.coerceAtLeast(0))

    fun rewind() = seekBy(-SEEK_INTERVAL_MS)

    fun fastForward() = seekBy(SEEK_INTERVAL_MS)

    fun cyclePlaybackMode() = playbackController.setPlaybackMode(uiState.value.playbackMode.nextMode())
    fun jumpToQueueItem(queueItemId: QueueItemId) {
        val currentQueue = uiState.value.queue
        val currentIndex = currentQueue.indexOfFirst { it.isCurrent }
        val targetIndex = currentQueue.indexOfFirst { it.queueItemId == queueItemId }
        pendingExplicitDirection = when {
            currentIndex != -1 && targetIndex != -1 && targetIndex > currentIndex -> TrackSlideDirection.FORWARD
            currentIndex != -1 && targetIndex != -1 && targetIndex < currentIndex -> TrackSlideDirection.BACKWARD
            else -> TrackSlideDirection.NONE
        }
        playbackController.jumpToQueueItem(queueItemId)
    }
    fun removeFromQueue(queueItemId: QueueItemId) = playbackController.removeFromQueue(queueItemId)
    fun clearQueue() = playbackController.clearQueue()

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

    fun showSleepTimer() {
        showSleepTimer.value = true
    }

    fun dismissSleepTimer() {
        showSleepTimer.value = false
    }

    fun startSleepTimer(durationMinutes: Int, extendToEndOfTrack: Boolean) {
        playbackController.startSleepTimer(durationMinutes, extendToEndOfTrack)
        viewModelScope.launch {
            settingsRepository.setSleepTimerPreferences(durationMinutes, extendToEndOfTrack)
        }
    }

    fun stopSleepTimer() {
        playbackController.stopSleepTimer()
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

    companion object {
        const val THROTTLE_WINDOW_MS = 300L
        val SKIP_DEBOUNCE_WINDOW_MS = PlayerMotionTokens.TRACK_CHANGE_DURATION_MS.toLong()
        private const val ARTWORK_TARGET_PX = 1_024
        private const val SEEK_INTERVAL_MS = 10_000L
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
