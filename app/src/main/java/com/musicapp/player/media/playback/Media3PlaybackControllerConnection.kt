package com.musicapp.player.media.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.playback.PlaybackConnectionState
import com.musicapp.player.core.playback.PlaybackControllerState
import com.musicapp.player.core.playback.PlaybackFailure
import com.musicapp.player.core.playback.PlaybackStatus
import com.musicapp.player.core.playback.BufferingVisibilityPolicy
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bufferingPolicy = BufferingVisibilityPolicy()
    private var bufferingUpdateScheduled = false
    private var positionUpdateScheduled = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connectionRequested = false
    private var playbackMode = PlaybackMode.DEFAULT
    private var playbackQueue = PlaybackQueue()
    private var serviceFailure: PlaybackFailure? = null

    override val state: StateFlow<PlaybackControllerState> = mutableState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateBuffering(player)
            updateState(player)
            updatePositionRefresh(player)
        }
    }

    private val showBuffering = Runnable {
        bufferingUpdateScheduled = false
        val connectedController = controller ?: return@Runnable
        if (connectedController.playbackState != Player.STATE_BUFFERING) return@Runnable
        updateState(connectedController)
    }

    private val refreshPosition = object : Runnable {
        override fun run() {
            positionUpdateScheduled = false
            val connectedController = controller ?: return
            updateState(connectedController)
            updatePositionRefresh(connectedController)
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            updateQueueState(extras)
            updateState(controller)
        }

        override fun onDisconnected(controller: MediaController) {
            mainExecutor.execute {
                if (this@Media3PlaybackControllerConnection.controller !== controller) return@execute
                controller.removeListener(playerListener)
                resetBuffering()
                stopPositionRefresh()
                serviceFailure = null
                this@Media3PlaybackControllerConnection.controller = null
                val disconnectedFuture = controllerFuture
                controllerFuture = null
                disconnectedFuture?.let(MediaController::releaseFuture)
                mutableState.value = PlaybackControllerState(
                    connectionState = if (connectionRequested) {
                        PlaybackConnectionState.CONNECTING
                    } else {
                        PlaybackConnectionState.DISCONNECTED
                    },
                )
                if (connectionRequested) buildController()
            }
        }
    }

    override fun connect() {
        mainExecutor.execute {
            connectionRequested = true
            if (controller != null || controllerFuture != null) return@execute
            mutableState.value = PlaybackControllerState(
                connectionState = PlaybackConnectionState.CONNECTING,
            )
            buildController()
        }
    }

    private fun buildController() {
        val future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, MusicPlaybackService::class.java)),
            ).setListener(controllerListener).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                runCatching { future.get() }
                    .onSuccess { connectedController ->
                        controller = connectedController
                        connectedController.addListener(playerListener)
                        updateQueueState(connectedController.sessionExtras)
                        updateState(connectedController)
                        updatePositionRefresh(connectedController)
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

    override fun disconnect() {
        mainExecutor.execute {
            connectionRequested = false
            pendingCommands.clear()
            controller?.removeListener(playerListener)
            resetBuffering()
            stopPositionRefresh()
            serviceFailure = null
            controller = null
            val future = controllerFuture
            controllerFuture = null
            future?.let(MediaController::releaseFuture)
            mutableState.value = PlaybackControllerState(
                connectionState = PlaybackConnectionState.DISCONNECTED,
            )
            playbackMode = PlaybackMode.DEFAULT
            playbackQueue = PlaybackQueue()
        }
    }

    override fun replaceQueue(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean,
    ) {
        require(tracks.isNotEmpty()) { "tracks must not be empty" }
        require(startIndex in tracks.indices) { "startIndex must be within tracks" }
        dispatchPreparing(tracks[startIndex].id) { mediaController ->
            mediaController.sendCustomCommand(
                PlaybackSessionProtocol.replaceQueueCommand,
                PlaybackSessionProtocol.tracksArgs(tracks, startIndex, playWhenReady),
            )
        }
    }

    override fun play() = dispatchPreparing(mutableState.value.currentTrackId, MediaController::play)

    override fun pause() = dispatch(MediaController::pause)

    override fun skipToPrevious() = dispatch(MediaController::seekToPreviousMediaItem)

    override fun skipToNext() = dispatch(MediaController::seekToNextMediaItem)

    override fun seekTo(positionMs: Long) = dispatch { it.seekTo(positionMs.coerceAtLeast(0)) }

    override fun setPlaybackMode(mode: PlaybackMode) = dispatch {
        it.sendCustomCommand(PlaybackSessionProtocol.setModeCommand, PlaybackSessionProtocol.modeArgs(mode))
    }

    override fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        dispatch {
            it.sendCustomCommand(
                PlaybackSessionProtocol.addToQueueCommand,
                PlaybackSessionProtocol.tracksArgs(tracks),
            )
        }
    }

    override fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        dispatch {
            it.sendCustomCommand(
                PlaybackSessionProtocol.playNextCommand,
                PlaybackSessionProtocol.tracksArgs(tracks),
            )
        }
    }

    override fun jumpToQueueItem(queueItemId: QueueItemId) = dispatchPreparing(
        playbackQueue.originalQueue.firstOrNull { it.id == queueItemId }?.trackId,
    ) {
        it.sendCustomCommand(
            PlaybackSessionProtocol.jumpToQueueItemCommand,
            PlaybackSessionProtocol.queueItemArgs(queueItemId),
        )
    }

    override fun removeFromQueue(queueItemId: QueueItemId) = dispatch {
        it.sendCustomCommand(
            PlaybackSessionProtocol.removeFromQueueCommand,
            PlaybackSessionProtocol.queueItemArgs(queueItemId),
        )
    }

    private fun dispatch(command: (MediaController) -> Unit) {
        mainExecutor.execute {
            controller?.let(command)
                ?: if (controllerFuture != null) pendingCommands.addLast(command) else Unit
        }
    }

    private fun dispatchPreparing(
        trackId: TrackId?,
        command: (MediaController) -> Unit,
    ) {
        serviceFailure = null
        mutableState.value = mutableState.value.preparingFor(trackId)
        dispatch(command)
    }

    private fun updateState(player: Player) {
        val duration = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0)
        val bufferingVisible = bufferingPolicy.update(
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            nowMs = SystemClock.elapsedRealtime(),
        )
        val playbackFailure = serviceFailure
            ?: player.playerError?.let { Media3PlaybackFailureMapper.from(it.errorCode) }
        val playbackStatus = Media3PlaybackStatusResolver.resolve(
            playerState = player.playbackState,
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            hasCurrentItem = player.currentMediaItem != null || playbackQueue.currentItem != null,
            bufferingVisible = bufferingVisible,
            failure = playbackFailure,
        )
        mutableState.value = PlaybackControllerState(
            connectionState = PlaybackConnectionState.CONNECTED,
            currentTrackId = QueueMediaIdCodec.decode(player.currentMediaItem?.mediaId.orEmpty())?.trackId
                ?: playbackQueue.currentItem?.trackId,
            playbackStatus = playbackStatus,
            playbackFailure = playbackFailure,
            isPlaying = player.isPlaying,
            isBuffering = playbackStatus == PlaybackStatus.BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            canSkipPrevious = playbackQueue.originalQueue.size > 1,
            canSkipNext = playbackQueue.originalQueue.size > 1,
            playbackMode = playbackMode,
            queue = playbackQueue,
        )
    }

    private fun updateQueueState(extras: Bundle) {
        serviceFailure = PlaybackSessionProtocol.decodePlaybackFailure(extras)
        PlaybackSessionProtocol.decodeState(extras)?.let { (mode, queue) ->
            playbackMode = mode
            playbackQueue = queue
        }
    }

    private fun updateBuffering(player: Player) {
        if (player.playbackState != Player.STATE_BUFFERING) {
            resetBuffering()
            return
        }
        if (bufferingUpdateScheduled) return
        bufferingPolicy.update(isBuffering = true, nowMs = SystemClock.elapsedRealtime())
        bufferingUpdateScheduled = true
        mainHandler.postDelayed(showBuffering, BufferingVisibilityPolicy.DEFAULT_DELAY_MS)
    }

    private fun resetBuffering() {
        mainHandler.removeCallbacks(showBuffering)
        bufferingUpdateScheduled = false
        bufferingPolicy.reset()
    }

    private fun updatePositionRefresh(player: Player) {
        val delayMs = PlaybackPositionRefreshPolicy.nextDelayMs(
            isConnected = controller != null,
            isPlaying = player.isPlaying,
        )
        if (delayMs == null) {
            stopPositionRefresh()
            return
        }
        if (positionUpdateScheduled) return
        positionUpdateScheduled = true
        mainHandler.postDelayed(refreshPosition, delayMs)
    }

    private fun stopPositionRefresh() {
        mainHandler.removeCallbacks(refreshPosition)
        positionUpdateScheduled = false
    }
}
