package com.musicapp.player.media.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackConnectionState
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.media.service.MusicPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
internal class Media3PlaybackControllerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PlaybackControllerConnection {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val pendingCommands = ArrayDeque<(MediaController) -> Unit>()
    private val mutableState = MutableStateFlow(PlaybackControllerState())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override val state: StateFlow<PlaybackControllerState> = mutableState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
        }
    }

    override fun connect() {
        mainExecutor.execute {
            if (controller != null || controllerFuture != null) return@execute
            mutableState.value = PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTING,
            )
            val future =
                MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, MusicPlaybackService::class.java)),
                ).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    if (controllerFuture !== future) return@addListener
                    runCatching { future.get() }
                        .onSuccess { connectedController ->
                            controller = connectedController
                            connectedController.addListener(playerListener)
                            updateState(connectedController)
                            while (pendingCommands.isNotEmpty()) {
                                pendingCommands.removeFirst().invoke(connectedController)
                            }
                        }
                        .onFailure {
                            controllerFuture = null
                            pendingCommands.clear()
                            mutableState.value = PlaybackControllerState(
                                connectionState = PlaybackConnectionState.UNAVAILABLE,
                            )
                        }
                },
                mainExecutor,
            )
        }
    }

    override fun disconnect() {
        mainExecutor.execute {
            pendingCommands.clear()
            controller?.removeListener(playerListener)
            controller = null
            val future = controllerFuture
            controllerFuture = null
            future?.let(MediaController::releaseFuture)
            mutableState.value = PlaybackControllerState(
                connectionState = PlaybackConnectionState.DISCONNECTED,
            )
        }
    }

    override fun replaceQueue(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean,
    ) {
        require(tracks.isNotEmpty()) { "tracks must not be empty" }
        require(startIndex in tracks.indices) { "startIndex must be within tracks" }
        dispatch { mediaController ->
            mediaController.setMediaItems(tracks.map(::toMediaItem), startIndex, 0)
            mediaController.repeatMode = Player.REPEAT_MODE_ALL
            mediaController.prepare()
            if (playWhenReady) mediaController.play()
        }
    }

    override fun play() = dispatch(MediaController::play)

    override fun pause() = dispatch(MediaController::pause)

    override fun skipToPrevious() = dispatch(MediaController::seekToPreviousMediaItem)

    override fun skipToNext() = dispatch(MediaController::seekToNextMediaItem)

    override fun seekTo(positionMs: Long) = dispatch { it.seekTo(positionMs.coerceAtLeast(0)) }

    private fun dispatch(command: (MediaController) -> Unit) {
        mainExecutor.execute {
            controller?.let(command)
                ?: if (controllerFuture != null) pendingCommands.addLast(command) else Unit
        }
    }

    private fun updateState(player: Player) {
        val duration = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0)
        mutableState.value = PlaybackControllerState(
            connectionState = PlaybackConnectionState.CONNECTED,
            currentTrackId = TrackMediaIdCodec.decode(player.currentMediaItem?.mediaId.orEmpty()),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            canSkipPrevious = player.hasPreviousMediaItem(),
            canSkipNext = player.hasNextMediaItem(),
        )
    }

    private fun toMediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(TrackMediaIdCodec.encode(track.id))
            .setUri(track.id.toContentUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName)
                    .setAlbumTitle(track.albumTitle)
                    .setDurationMs(track.durationMs)
                    .build(),
            )
            .build()

    private fun TrackId.toContentUri() =
        ContentUris.withAppendedId(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(volumeName)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            },
            mediaStoreId,
        )
}
