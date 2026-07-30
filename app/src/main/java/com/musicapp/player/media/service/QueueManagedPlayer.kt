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
) : ForwardingPlayer(player) {
    override fun seekToNextMediaItem() = coordinator.manualNext()

    override fun seekToNext() = coordinator.manualNext()

    override fun seekToPreviousMediaItem() = coordinator.manualPrevious()

    override fun seekToPrevious() = coordinator.manualPrevious()

    override fun hasNextMediaItem(): Boolean = coordinator.canNavigate

    override fun hasPreviousMediaItem(): Boolean = coordinator.canNavigate
}
