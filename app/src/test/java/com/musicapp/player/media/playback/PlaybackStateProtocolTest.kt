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
