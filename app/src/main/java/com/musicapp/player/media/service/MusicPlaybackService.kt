package com.musicapp.player.media.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.musicapp.player.MainActivity
import com.musicapp.player.core.common.random.RandomSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaLibraryService() {
    @Inject
    internal lateinit var callbackFactory: MusicLibrarySessionCallbackFactory

    @Inject
    internal lateinit var randomSource: RandomSource

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var queueCoordinator: PlaybackQueueCoordinator? = null
    private var playerListener: Player.Listener? = null

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
                .build()
        val coordinator = PlaybackQueueCoordinator(Media3QueuePlayer(servicePlayer), randomSource)
        val managedPlayer = QueueManagedPlayer(servicePlayer, coordinator)
        val callback = callbackFactory.create(coordinator)
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                coordinator.onMediaItemTransition(mediaItem)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                coordinator.onPlaybackStateChanged(playbackState)
            }
        }
        servicePlayer.addListener(listener)
        player = servicePlayer
        queueCoordinator = coordinator
        playerListener = listener
        val session =
            MediaLibrarySession.Builder(this, managedPlayer, callback)
                .setSessionActivity(createSessionActivity())
                .build()
        mediaLibrarySession = session
        coordinator.attachStatePublisher { extras -> callback.publishState(session, extras) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        playerListener?.let { listener -> player?.removeListener(listener) }
        playerListener = null
        queueCoordinator = null
        player?.release()
        player = null
        super.onDestroy()
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
    }
}
