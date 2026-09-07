package com.musicapp.player.media.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.core.playback.PlaybackFailure
import com.musicapp.player.core.playback.PlaybackFailureCode
import com.musicapp.player.core.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStateProtocolTest {
    @Test
    fun `user command immediately publishes preparing and clears an earlier failure`() {
        val trackId = TrackId("external", 7)
        val state = PlaybackControllerState(
            playbackStatus = PlaybackStatus.ERROR,
            playbackFailure = PlaybackFailure(PlaybackFailureCode.UNKNOWN),
        )

        val preparing = state.preparingFor(trackId)

        assertEquals(trackId, preparing.currentTrackId)
        assertEquals(PlaybackStatus.PREPARING, preparing.playbackStatus)
        assertNull(preparing.playbackFailure)
        assertEquals(false, preparing.isPlaying)
        assertEquals(false, preparing.isBuffering)
    }

    @Test
    fun `user command with playWhenReady preserves playing intent during preparation`() {
        val trackId = TrackId("external", 7)
        val state = PlaybackControllerState(
            isPlaying = true,
            currentTrackId = TrackId("external", 1),
        )

        val preparing = state.preparingFor(trackId, playWhenReady = true)

        assertEquals(trackId, preparing.currentTrackId)
        assertEquals(PlaybackStatus.PREPARING, preparing.playbackStatus)
        assertEquals(true, preparing.isPlaying)
        assertEquals(false, preparing.isBuffering)
    }


    @Test
    fun `buffering remains preparing until the visibility threshold is reached`() {
        assertEquals(
            PlaybackStatus.PREPARING,
            resolve(playerState = Player.STATE_BUFFERING, bufferingVisible = false),
        )
        assertEquals(
            PlaybackStatus.BUFFERING,
            resolve(playerState = Player.STATE_BUFFERING, bufferingVisible = true),
        )
    }

    @Test
    fun `ready playing and paused are distinct observable states`() {
        assertEquals(
            PlaybackStatus.READY,
            resolve(playerState = Player.STATE_READY, isPlaying = false, playWhenReady = true),
        )
        assertEquals(
            PlaybackStatus.PLAYING,
            resolve(playerState = Player.STATE_READY, isPlaying = true, playWhenReady = true),
        )
        assertEquals(
            PlaybackStatus.PAUSED,
            resolve(playerState = Player.STATE_READY, isPlaying = false, playWhenReady = false),
        )
    }

    @Test
    fun `playback failure has priority without changing connection semantics`() {
        assertEquals(
            PlaybackStatus.ERROR,
            resolve(
                playerState = Player.STATE_READY,
                failure = PlaybackFailure(PlaybackFailureCode.SOURCE_NOT_FOUND),
            ),
        )
    }

    @Test
    fun `media3 failures map to stable application codes`() {
        assertEquals(
            PlaybackFailureCode.SOURCE_NOT_FOUND,
            Media3PlaybackFailureMapper.from(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND).code,
        )
        assertEquals(
            PlaybackFailureCode.ACCESS_DENIED,
            Media3PlaybackFailureMapper.from(PlaybackException.ERROR_CODE_IO_NO_PERMISSION).code,
        )
        assertEquals(
            PlaybackFailureCode.UNSUPPORTED_FORMAT,
            Media3PlaybackFailureMapper.from(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED).code,
        )
        assertEquals(
            PlaybackFailureCode.DECODING_FAILED,
            Media3PlaybackFailureMapper.from(PlaybackException.ERROR_CODE_DECODING_FAILED).code,
        )
        assertEquals(
            PlaybackFailureCode.UNKNOWN,
            Media3PlaybackFailureMapper.from(Int.MAX_VALUE).code,
        )
    }

    @Test
    fun `position refresh runs every two hundred milliseconds only while connected and playing`() {
        assertEquals(200L, PlaybackPositionRefreshPolicy.nextDelayMs(isConnected = true, isPlaying = true))
        assertNull(PlaybackPositionRefreshPolicy.nextDelayMs(isConnected = true, isPlaying = false))
        assertNull(PlaybackPositionRefreshPolicy.nextDelayMs(isConnected = false, isPlaying = true))
    }

    @Test
    fun `is playing resolver returns true when player reports playing`() {
        val result = Media3PlaybackIsPlayingResolver.resolve(
            isPlaying = true,
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            hasItem = true,
        )
        assertEquals(true, result)
    }

    @Test
    fun `is playing resolver maintains true during buffering when playWhenReady is true to prevent button flicker`() {
        // In ExoPlayer, player.isPlaying is false during STATE_BUFFERING even if playWhenReady is true.
        // The resolver must return true so the Pause button does not flicker to Play.
        val result = Media3PlaybackIsPlayingResolver.resolve(
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_BUFFERING,
            hasItem = true,
            hasFailure = false,
        )
        assertEquals(true, result)
    }

    @Test
    fun `is playing resolver returns false when playWhenReady is false`() {
        val result = Media3PlaybackIsPlayingResolver.resolve(
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            hasItem = true,
        )
        assertEquals(false, result)
    }

    @Test
    fun `is playing resolver returns false when playback is ended`() {
        val result = Media3PlaybackIsPlayingResolver.resolve(
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_ENDED,
            hasItem = true,
        )
        assertEquals(false, result)
    }

    @Test
    fun `is playing resolver returns false when playback is suppressed`() {
        val result = Media3PlaybackIsPlayingResolver.resolve(
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
            hasItem = true,
        )
        assertEquals(false, result)
    }

    @Test
    fun `is playing resolver returns false when there is a playback failure`() {
        val result = Media3PlaybackIsPlayingResolver.resolve(
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_BUFFERING,
            hasItem = true,
            hasFailure = true,
        )
        assertEquals(false, result)
    }

    @Test
    fun `is playing resolver returns false when there is no current item`() {
        val result = Media3PlaybackIsPlayingResolver.resolve(
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_BUFFERING,
            hasItem = false,
        )
        assertEquals(false, result)
    }

    private fun resolve(
        playerState: Int,
        isPlaying: Boolean = false,
        playWhenReady: Boolean = true,
        bufferingVisible: Boolean = false,
        failure: PlaybackFailure? = null,
    ) = Media3PlaybackStatusResolver.resolve(
        playerState = playerState,
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        hasCurrentItem = true,
        bufferingVisible = bufferingVisible,
        failure = failure,
    )
}
