package com.musicapp.player.core.playback

import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue

data class PlaybackQueueState(
    val queue: PlaybackQueue = PlaybackQueue(),
    val mode: PlaybackMode = PlaybackMode.DEFAULT,
)

object PlaybackModeReducer {
    fun reduce(
        state: PlaybackQueueState,
        targetMode: PlaybackMode,
        shuffleSequence: ShuffleSequence,
    ): PlaybackQueueState {
        if (state.mode == targetMode) return state

        val queue = state.queue
        val updatedQueue = when {
            targetMode == PlaybackMode.SHUFFLE -> {
                val sequence = shuffleSequence.enter(
                    itemIds = queue.originalQueue.map { it.id },
                    currentItemId = queue.currentItemId,
                )
                queue.copy(
                    stableShuffleSequence = sequence,
                    shuffleRound = if (sequence.isEmpty()) queue.shuffleRound else queue.shuffleRound + 1,
                    shuffleCursor = queue.currentItemId?.let(sequence::indexOf)?.takeIf { it >= 0 },
                )
            }

            state.mode == PlaybackMode.SHUFFLE -> queue.copy(
                stableShuffleSequence = emptyList(),
                shuffleCursor = null,
            )

            else -> queue
        }
        return PlaybackQueueState(queue = updatedQueue, mode = targetMode)
    }
}
