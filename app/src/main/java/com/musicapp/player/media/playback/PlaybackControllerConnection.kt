package com.musicapp.player.media.playback

import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.playback.PlaybackControllerState
import kotlinx.coroutines.flow.StateFlow

internal interface PlaybackControllerConnection {
    val state: StateFlow<PlaybackControllerState>

    fun connect()

    fun disconnect()

    fun replaceQueue(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean,
    )

    fun play()

    fun pause()

    fun skipToPrevious()

    fun skipToNext()

    fun seekTo(positionMs: Long)
}
