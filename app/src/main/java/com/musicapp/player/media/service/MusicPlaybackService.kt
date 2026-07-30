package com.musicapp.player.media.service

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.musicapp.player.MainActivity
import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.playback.fade.FadePlaybackEvent
import com.musicapp.player.core.playback.fade.FadeSwitchReason
import com.musicapp.player.core.playback.fade.FadeThroughCoordinator
import com.musicapp.player.core.playback.fade.FadeThroughDuration
import com.musicapp.player.core.playback.fade.FadeThroughOutput
import com.musicapp.player.core.playback.history.PlayHistoryRecorder
import com.musicapp.player.core.playback.recovery.AudioInterruption
import com.musicapp.player.core.playback.recovery.AudioInterruptionPolicy
import com.musicapp.player.core.playback.recovery.PlaybackErrorRecovery
import com.musicapp.player.core.playback.recovery.PlaybackRecoveryAction
import com.musicapp.player.data.repository.HistoryRepository
import com.musicapp.player.data.settings.SettingsRepository
import com.musicapp.player.media.playback.QueueMediaIdCodec
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaLibraryService() {
    @Inject
    internal lateinit var callbackFactory: MusicLibrarySessionCallbackFactory

    @Inject
    internal lateinit var randomSource: RandomSource

    @Inject
    internal lateinit var settingsRepository: SettingsRepository

    @Inject
    internal lateinit var historyRepository: HistoryRepository

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var queueCoordinator: PlaybackQueueCoordinator? = null
    private var playerListener: Player.Listener? = null
    private var fadeCoordinator: FadeThroughCoordinator<FadeNavigationRequest>? = null
    private var historyRecorder: PlayHistoryRecorder? = null
    private var automaticTransitionJob: Job? = null
    private var settingsJob: Job? = null
    private var historyTickerJob: Job? = null
    private var historyItemId: QueueItemId? = null
    private var ignoreNextPauseChange = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val errorRecovery = PlaybackErrorRecovery<QueueItemId>()
    private val interruptionPolicy = AudioInterruptionPolicy()

    override fun onCreate() {
        super.onCreate()
        val servicePlayer =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        val coordinator = PlaybackQueueCoordinator(Media3QueuePlayer(servicePlayer), randomSource)
        val fade = FadeThroughCoordinator(
            scope = serviceScope,
            output = object : FadeThroughOutput<FadeNavigationRequest> {
                override fun setVolume(volume: Float) {
                    servicePlayer.volume = volume
                }

                override fun switchTo(target: FadeNavigationRequest): Boolean {
                    automaticTransitionJob?.cancel()
                    automaticTransitionJob = null
                    val currentId = coordinator.currentState.queue.currentItemId
                    if (currentId != target.sourceItemId) return currentId != null
                    when (target.action) {
                        FadeNavigationAction.NATURAL_NEXT -> coordinator.naturalNext()
                        FadeNavigationAction.MANUAL_NEXT -> coordinator.manualNext()
                        FadeNavigationAction.MANUAL_PREVIOUS -> coordinator.manualPrevious()
                        FadeNavigationAction.RECOVER -> {
                            val recoveryTarget = target.recoveryTarget ?: return false
                            if (!coordinator.recoverTo(recoveryTarget)) return false
                        }
                    }
                    return coordinator.currentState.queue.currentItemId != null
                }

                override fun requestPause() {
                    servicePlayer.pause()
                }
            },
            durationProvider = {
                FadeThroughDuration.of(settingsRepository.settings.value.fadeThroughDurationMs)
            },
        )
        val managedPlayer = QueueManagedPlayer(
            player = servicePlayer,
            coordinator = coordinator,
            requestNext = { requestFade(FadeNavigationAction.MANUAL_NEXT, FadeSwitchReason.MANUAL_NEXT) },
            requestPrevious = {
                requestFade(FadeNavigationAction.MANUAL_PREVIOUS, FadeSwitchReason.MANUAL_PREVIOUS)
            },
        )
        val recorder = PlayHistoryRecorder(
            monotonicNowMs = SystemClock::elapsedRealtime,
            wallClockNowMs = System::currentTimeMillis,
            onHistoryThresholdReached = { trackId, playedAtMs ->
                serviceScope.launch {
                    runCatching { historyRepository.recordPlayback(trackId, playedAtMs) }
                }
            },
        )
        val callback = callbackFactory.create(coordinator)
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                coordinator.onMediaItemTransition(mediaItem)
                startHistoryInstance(servicePlayer, mediaItem, reason)
                scheduleNaturalTransition(servicePlayer)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        errorRecovery.onReady()
                        startHistoryInstance(servicePlayer, servicePlayer.currentMediaItem, null)
                        scheduleNaturalTransition(servicePlayer)
                    }

                    Player.STATE_BUFFERING -> recorder.updateIsPlaying(false)
                    Player.STATE_ENDED -> requestFade(
                        FadeNavigationAction.NATURAL_NEXT,
                        FadeSwitchReason.NATURAL_END,
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                recorder.updateIsPlaying(isPlaying)
                if (isPlaying) scheduleNaturalTransition(servicePlayer) else cancelNaturalTransition()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                recorder.onSeekStarted()
                recorder.onSeekCompleted(servicePlayer.isPlaying)
                fade.onPlaybackEvent(FadePlaybackEvent.SEEK)
                scheduleNaturalTransition(servicePlayer)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    fade.onPlaybackEvent(FadePlaybackEvent.RESUME)
                    return
                }
                if (ignoreNextPauseChange) {
                    ignoreNextPauseChange = false
                    return
                }
                when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                        interruptionPolicy.onInterruption(
                            AudioInterruption.PERMANENT_FOCUS_LOSS,
                            wasPlaying = servicePlayer.isPlaying,
                        )
                        interruptFade(FadePlaybackEvent.AUDIO_FOCUS_LOSS, servicePlayer)
                    }

                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                        interruptionPolicy.onInterruption(
                            AudioInterruption.PRIVATE_OUTPUT_LOST,
                            wasPlaying = servicePlayer.isPlaying,
                        )
                        interruptFade(FadePlaybackEvent.PRIVATE_OUTPUT_LOST, servicePlayer)
                    }

                    else -> {
                        interruptionPolicy.onUserPause()
                        interruptFade(FadePlaybackEvent.PAUSE, servicePlayer)
                    }
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                when (playbackSuppressionReason) {
                    Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> {
                        interruptionPolicy.onInterruption(
                            AudioInterruption.TRANSIENT_FOCUS_LOSS,
                            wasPlaying = servicePlayer.playWhenReady,
                        )
                        interruptFade(FadePlaybackEvent.AUDIO_FOCUS_LOSS, servicePlayer)
                    }

                    Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> {
                        interruptionPolicy.onInterruption(
                            AudioInterruption.PRIVATE_OUTPUT_LOST,
                            wasPlaying = servicePlayer.playWhenReady,
                        )
                        interruptFade(FadePlaybackEvent.PRIVATE_OUTPUT_LOST, servicePlayer)
                    }

                    Player.PLAYBACK_SUPPRESSION_REASON_NONE -> {
                        val action = interruptionPolicy.onInterruption(
                            AudioInterruption.FOCUS_GAIN,
                            wasPlaying = false,
                        )
                        fade.onPlaybackEvent(FadePlaybackEvent.AUDIO_FOCUS_GAIN)
                        if (action.resume) servicePlayer.play()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelNaturalTransition()
                fade.onTargetFailure()
                val failedId = coordinator.currentState.queue.currentItemId ?: return
                when (
                    val action = errorRecovery.onFailure(
                        playbackOrder = coordinator.currentState.queue.playbackOrder.map { it.id },
                        failedItem = failedId,
                    )
                ) {
                    is PlaybackRecoveryAction.TryNext -> requestFade(
                        action = FadeNavigationAction.RECOVER,
                        reason = FadeSwitchReason.NATURAL_END,
                        recoveryTarget = action.target,
                    )

                    PlaybackRecoveryAction.StopAndRestoreVolume -> {
                        coordinator.stopPlayback()
                        fade.onPlaybackEvent(FadePlaybackEvent.PAUSE)
                    }
                }
            }
        }
        servicePlayer.addListener(listener)
        player = servicePlayer
        queueCoordinator = coordinator
        playerListener = listener
        fadeCoordinator = fade
        historyRecorder = recorder
        val session =
            MediaLibrarySession.Builder(this, managedPlayer, callback)
                .setSessionActivity(createSessionActivity())
                .build()
        mediaLibrarySession = session
        coordinator.attachStatePublisher { extras -> callback.publishState(session, extras) }
        settingsJob = serviceScope.launch {
            settingsRepository.settings.drop(1).collect { scheduleNaturalTransition(servicePlayer) }
        }
        historyTickerJob = serviceScope.launch {
            while (true) {
                delay(HISTORY_TICK_MS)
                recorder.tick()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        cancelNaturalTransition()
        settingsJob?.cancel()
        settingsJob = null
        historyTickerJob?.cancel()
        historyTickerJob = null
        historyRecorder?.stopInstance()
        historyRecorder = null
        fadeCoordinator?.dispose()
        fadeCoordinator = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        playerListener?.let { listener -> player?.removeListener(listener) }
        playerListener = null
        queueCoordinator = null
        player?.release()
        player = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun requestFade(
        action: FadeNavigationAction,
        reason: FadeSwitchReason,
        recoveryTarget: QueueItemId? = null,
    ) {
        val sourceItemId = queueCoordinator?.currentState?.queue?.currentItemId ?: return
        cancelNaturalTransition()
        fadeCoordinator?.requestSwitch(
            FadeNavigationRequest(sourceItemId, action, recoveryTarget),
            reason,
        )
    }

    private fun scheduleNaturalTransition(servicePlayer: ExoPlayer) {
        cancelNaturalTransition()
        if (!servicePlayer.isPlaying) return
        val current = QueueMediaIdCodec.decode(servicePlayer.currentMediaItem?.mediaId.orEmpty()) ?: return
        val durationMs = servicePlayer.duration.takeUnless { it == C.TIME_UNSET }?.takeIf { it > 0 } ?: return
        val fadeOutMs = settingsRepository.settings.value.fadeThroughDurationMs / 2
        val remainingMs = (durationMs - servicePlayer.currentPosition.coerceAtLeast(0)).coerceAtLeast(0)
        val waitMs = (remainingMs - fadeOutMs).coerceAtLeast(0)
        automaticTransitionJob = serviceScope.launch {
            delay(waitMs)
            if (!servicePlayer.isPlaying) return@launch
            val stillCurrent = QueueMediaIdCodec.decode(servicePlayer.currentMediaItem?.mediaId.orEmpty())
            if (stillCurrent?.id != current.id) return@launch
            requestFade(FadeNavigationAction.NATURAL_NEXT, FadeSwitchReason.NATURAL_END)
        }
    }

    private fun cancelNaturalTransition() {
        automaticTransitionJob?.cancel()
        automaticTransitionJob = null
    }

    private fun startHistoryInstance(
        servicePlayer: ExoPlayer,
        mediaItem: androidx.media3.common.MediaItem?,
        transitionReason: Int?,
    ) {
        val item = QueueMediaIdCodec.decode(mediaItem?.mediaId.orEmpty()) ?: return
        val durationMs = mediaItem?.mediaMetadata?.durationMs
            ?.takeIf { it > 0 }
            ?: servicePlayer.duration.takeUnless { it == C.TIME_UNSET }?.takeIf { it > 0 }
            ?: return
        val startsNewInstance = historyItemId != item.id ||
            transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
            transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        if (startsNewInstance) {
            historyRecorder?.stopInstance()
            historyItemId = item.id
        }
        historyRecorder?.startInstance(item.id, item.trackId, durationMs, servicePlayer.isPlaying)
    }

    private fun interruptFade(event: FadePlaybackEvent, servicePlayer: ExoPlayer) {
        if (servicePlayer.playWhenReady) ignoreNextPauseChange = true
        fadeCoordinator?.onPlaybackEvent(event)
    }

    private fun createSessionActivity(): PendingIntent =
        PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val SESSION_ACTIVITY_REQUEST_CODE = 100
        const val HISTORY_TICK_MS = 250L
    }
}

private enum class FadeNavigationAction {
    NATURAL_NEXT,
    MANUAL_NEXT,
    MANUAL_PREVIOUS,
    RECOVER,
}

private data class FadeNavigationRequest(
    val sourceItemId: QueueItemId,
    val action: FadeNavigationAction,
    val recoveryTarget: QueueItemId? = null,
)
