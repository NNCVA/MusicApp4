package com.musicapp.player.core.playback.recovery

sealed interface PlaybackRecoveryAction<out T : Any> {
    data class TryNext<T : Any>(val target: T) : PlaybackRecoveryAction<T>

    data object StopAndRestoreVolume : PlaybackRecoveryAction<Nothing>
}

/** Traverses each queue identity at most once before declaring the current round unreadable. */
class PlaybackErrorRecovery<T : Any> {
    private val failedItems = linkedSetOf<T>()

    fun onFailure(
        playbackOrder: List<T>,
        failedItem: T,
    ): PlaybackRecoveryAction<T> {
        if (playbackOrder.isEmpty() || failedItem !in playbackOrder) {
            failedItems.clear()
            return PlaybackRecoveryAction.StopAndRestoreVolume
        }
        failedItems += failedItem
        val failedIndex = playbackOrder.indexOf(failedItem)
        val next = (1 until playbackOrder.size)
            .asSequence()
            .map { offset -> playbackOrder[(failedIndex + offset) % playbackOrder.size] }
            .firstOrNull { it !in failedItems }
        return if (next == null) {
            failedItems.clear()
            PlaybackRecoveryAction.StopAndRestoreVolume
        } else {
            PlaybackRecoveryAction.TryNext(next)
        }
    }

    fun onReady() {
        failedItems.clear()
    }

    fun onQueueReplaced() {
        failedItems.clear()
    }
}
