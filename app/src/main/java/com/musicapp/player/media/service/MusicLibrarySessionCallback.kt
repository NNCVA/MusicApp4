package com.musicapp.player.media.service

import android.content.Context
import android.os.Bundle
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musicapp.player.R
import com.musicapp.player.media.playback.PlaybackSessionProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MusicLibrarySessionCallbackFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun create(queueCoordinator: PlaybackQueueCoordinator): MusicLibrarySessionCallback =
        MusicLibrarySessionCallback(
            connectionPolicy = ControllerConnectionPolicy(context.packageName, Process.myUid()),
            libraryRootTitle = context.getString(R.string.media_library_root_title),
            queueCoordinator = queueCoordinator,
        )
}

@OptIn(UnstableApi::class)
internal class MusicLibrarySessionCallback(
    private val connectionPolicy: ControllerConnectionPolicy,
    libraryRootTitle: String,
    private val queueCoordinator: PlaybackQueueCoordinator,
) : MediaLibrarySession.Callback {
    private val applicationControllers = linkedSetOf<MediaSession.ControllerInfo>()
    private val libraryRoot =
        MediaItem.Builder()
            .setMediaId(LIBRARY_ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(libraryRootTitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        when (
            connectionPolicy.accessFor(
                ControllerIdentity(
                    packageName = controller.packageName,
                    uid = controller.uid,
                    isTrusted = controller.isTrusted,
                ),
            )
        ) {
            ControllerAccess.APPLICATION ->
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(ApplicationControllerCommands.sessionCommands)
                    .setAvailablePlayerCommands(ApplicationControllerCommands.playerCommands)
                    .setSessionExtras(queueCoordinator.stateExtras())
                    .build()
            ControllerAccess.TRUSTED_SYSTEM ->
                MediaSession.ConnectionResult.accept(
                    TrustedSystemControllerCommands.sessionCommands,
                    TrustedSystemControllerCommands.playerCommands,
                )
            ControllerAccess.REJECTED -> MediaSession.ConnectionResult.reject()
        }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (accessFor(controller) == ControllerAccess.APPLICATION) {
            applicationControllers += controller
            session.setSessionExtras(controller, queueCoordinator.stateExtras())
        }
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        applicationControllers -= controller
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (accessFor(controller) != ControllerAccess.APPLICATION) return badCommandResult()
        val accepted = when (customCommand.customAction) {
            PlaybackSessionProtocol.replaceQueueCommand.customAction -> {
                val tracks = PlaybackSessionProtocol.decodeTracks(args)
                val startIndex = PlaybackSessionProtocol.startIndex(args)
                if (tracks == null || tracks.isEmpty() || startIndex == null || startIndex !in tracks.indices) {
                    false
                } else {
                    queueCoordinator.replaceQueue(
                        tracks = tracks,
                        startIndex = startIndex,
                        playWhenReady = PlaybackSessionProtocol.playWhenReady(args),
                    )
                    true
                }
            }
            PlaybackSessionProtocol.setModeCommand.customAction ->
                PlaybackSessionProtocol.decodeMode(args)?.let { queueCoordinator.setMode(it); true } ?: false
            PlaybackSessionProtocol.addToQueueCommand.customAction ->
                PlaybackSessionProtocol.decodeTracks(args)?.let { queueCoordinator.addToQueue(it); true } ?: false
            PlaybackSessionProtocol.playNextCommand.customAction ->
                PlaybackSessionProtocol.decodeTracks(args)?.let { queueCoordinator.playNext(it); true } ?: false
            PlaybackSessionProtocol.removeFromQueueCommand.customAction ->
                PlaybackSessionProtocol.decodeQueueItemId(args)?.let { queueCoordinator.remove(it); true } ?: false
            else -> false
        }
        return if (accepted) {
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        } else {
            badCommandResult()
        }
    }

    fun publishState(session: MediaSession, extras: Bundle) {
        applicationControllers.forEach { session.setSessionExtras(it, extras) }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(libraryRoot, params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        if (parentId == LIBRARY_ROOT_ID) {
            Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        } else {
            Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

    private companion object {
        const val LIBRARY_ROOT_ID = "musicapp:root"
    }

    private fun accessFor(controller: MediaSession.ControllerInfo): ControllerAccess =
        connectionPolicy.accessFor(
            ControllerIdentity(
                packageName = controller.packageName,
                uid = controller.uid,
                isTrusted = controller.isTrusted,
            ),
        )

    private fun badCommandResult(): ListenableFuture<SessionResult> =
        Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
}

@OptIn(UnstableApi::class)
internal object ApplicationControllerCommands {
    val sessionCommands: SessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .apply { PlaybackSessionProtocol.applicationCommands.forEach(::add) }
            .build()

    val playerCommands: Player.Commands = TrustedSystemControllerCommands.playerCommands
}

@OptIn(UnstableApi::class)
internal object TrustedSystemControllerCommands {
    val sessionCommands: SessionCommands =
        SessionCommands.Builder()
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
            .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
            .build()

    val playerCommands: Player.Commands =
        Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .build()
}
