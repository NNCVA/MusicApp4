package com.musicapp.player.core.playback

import com.musicapp.player.core.domain.model.PlaybackContext
import com.musicapp.player.core.domain.model.TrackId
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE,
}

data class PlaybackControllerState(
    val connectionState: PlaybackConnectionState = PlaybackConnectionState.DISCONNECTED,
    val currentTrackId: TrackId? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
) {
    init {
        require(positionMs >= 0) { "positionMs must not be negative" }
        require(durationMs == null || durationMs >= 0) { "durationMs must be null or non-negative" }
    }
}

/** Platform-free command and state boundary consumed by Activity and ViewModel code. */
interface PlaybackControllerFacade {
    val state: StateFlow<PlaybackControllerState>

    /** Connects a started application UI. Every call must be paired with [disconnect]. */
    fun connect()

    /** Releases one started UI; the final release unbinds while Service playback continues. */
    fun disconnect()

    /** Replaces the runtime queue with [context] and starts its selected track. */
    fun play(context: PlaybackContext)

    fun play()

    fun pause()

    fun skipToPrevious()

    fun skipToNext()

    fun seekTo(positionMs: Long)
}
