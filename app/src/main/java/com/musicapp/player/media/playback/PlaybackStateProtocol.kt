package com.musicapp.player.media.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.core.playback.PlaybackFailure
import com.musicapp.player.core.playback.PlaybackFailureCode
import com.musicapp.player.core.playback.PlaybackStatus

internal fun PlaybackControllerState.preparingFor(
    trackId: TrackId? = currentTrackId,
): PlaybackControllerState = copy(
    currentTrackId = trackId,
    playbackStatus = PlaybackStatus.PREPARING,
    playbackFailure = null,
    isPlaying = false,
    isBuffering = false,
)

internal object Media3PlaybackStatusResolver {
    fun resolve(
        playerState: Int,
        isPlaying: Boolean,
        playWhenReady: Boolean,
        hasCurrentItem: Boolean,
        bufferingVisible: Boolean,
        failure: PlaybackFailure?,
    ): PlaybackStatus {
        if (failure != null) return PlaybackStatus.ERROR
        return when (playerState) {
            Player.STATE_BUFFERING -> when {
                bufferingVisible -> PlaybackStatus.BUFFERING
                playWhenReady -> PlaybackStatus.PREPARING
                else -> PlaybackStatus.PAUSED
            }

            Player.STATE_READY -> when {
                isPlaying -> PlaybackStatus.PLAYING
                playWhenReady -> PlaybackStatus.READY
                hasCurrentItem -> PlaybackStatus.PAUSED
                else -> PlaybackStatus.IDLE
            }

            Player.STATE_ENDED -> if (hasCurrentItem) PlaybackStatus.PAUSED else PlaybackStatus.IDLE
            Player.STATE_IDLE -> when {
                hasCurrentItem && playWhenReady -> PlaybackStatus.PREPARING
                hasCurrentItem -> PlaybackStatus.PAUSED
                else -> PlaybackStatus.IDLE
            }

            else -> PlaybackStatus.IDLE
        }
    }
}

internal object Media3PlaybackFailureMapper {
    fun from(errorCode: Int): PlaybackFailure = PlaybackFailure(
        code = when (errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackFailureCode.SOURCE_NOT_FOUND
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackFailureCode.ACCESS_DENIED
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackFailureCode.UNSUPPORTED_FORMAT

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackFailureCode.DECODING_FAILED

            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> PlaybackFailureCode.AUDIO_OUTPUT_FAILED

            in 2000..2999 -> PlaybackFailureCode.IO_ERROR
            else -> PlaybackFailureCode.UNKNOWN
        },
    )
}
