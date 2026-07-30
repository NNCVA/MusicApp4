package com.musicapp.player.media.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Routes every controller's queue navigation through the Service-owned queue state machine. */
@OptIn(UnstableApi::class)
internal class QueueManagedPlayer(
    player: Player,
    private val coordinator: PlaybackQueueCoordinator,
    private val requestNext: () -> Unit = coordinator::manualNext,
    private val requestPrevious: () -> Unit = coordinator::manualPrevious,
) : ForwardingPlayer(player) {
    override fun seekToNextMediaItem() = requestNext()

    override fun seekToNext() = requestNext()

    override fun seekToPreviousMediaItem() = requestPrevious()

    override fun seekToPrevious() = requestPrevious()

    override fun hasNextMediaItem(): Boolean = coordinator.canNavigate

    override fun hasPreviousMediaItem(): Boolean = coordinator.canNavigate
}
