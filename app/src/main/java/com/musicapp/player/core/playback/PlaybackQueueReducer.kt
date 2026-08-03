package com.musicapp.player.core.playback

import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.policy.PlaybackModePolicy

/** Pure state machine for queue identity, mode changes, navigation, and queue edits. */
class PlaybackQueueReducer(
    private val shuffleSequence: ShuffleSequence,
) {
    constructor(randomSource: RandomSource) : this(ShuffleSequence(randomSource))

    fun replaceQueue(
        items: List<QueueItem>,
        currentItemId: QueueItemId?,
        mode: PlaybackMode = PlaybackMode.DEFAULT,
    ): PlaybackQueueState {
        require(currentItemId == null || items.any { it.id == currentItemId }) {
            "currentItemId must refer to items"
        }
        val base = PlaybackQueueState(
            queue = PlaybackQueue(originalQueue = items, currentItemId = currentItemId),
            mode = PlaybackMode.LIST_REPEAT,
        )
        return setMode(base, mode)
    }

    fun setMode(
        state: PlaybackQueueState,
        targetMode: PlaybackMode,
    ): PlaybackQueueState = PlaybackModeReducer.reduce(state, targetMode, shuffleSequence)

    fun naturalEnd(state: PlaybackQueueState): PlaybackQueueState {
        val queue = state.queue
        val currentIndex = queue.currentPlaybackIndex() ?: return state
        if (state.mode == PlaybackMode.SHUFFLE && currentIndex == queue.playbackOrder.lastIndex) {
            return nextShuffleRound(state)
        }
        return moveToIndex(
            state,
            PlaybackModePolicy.indexAfterNaturalEnd(state.mode, currentIndex, queue.originalQueue.size),
        )
    }

    fun manualNext(state: PlaybackQueueState): PlaybackQueueState {
        val currentIndex = state.queue.currentPlaybackIndex() ?: return state
        if (state.mode == PlaybackMode.SHUFFLE && currentIndex == state.queue.playbackOrder.lastIndex) {
            return nextShuffleRound(state)
        }
        return moveToIndex(
            state,
            PlaybackModePolicy.indexAfterManualNext(state.mode, currentIndex, state.queue.originalQueue.size),
        )
    }

    fun manualPrevious(state: PlaybackQueueState): PlaybackQueueState {
        val currentIndex = state.queue.currentPlaybackIndex() ?: return state
        return moveToIndex(
            state,
            PlaybackModePolicy.indexAfterManualPrevious(state.mode, currentIndex, state.queue.originalQueue.size),
        )
    }

    fun append(
        state: PlaybackQueueState,
        newItems: List<QueueItem>,
    ): PlaybackQueueState {
        if (newItems.isEmpty()) return state
        validateNewItems(state.queue, newItems)
        val queue = state.queue
        val originalQueue = queue.originalQueue + newItems
        if (state.mode != PlaybackMode.SHUFFLE) {
            return state.copy(queue = queue.copy(originalQueue = originalQueue))
        }
        val currentIndex = queue.shuffleCursor ?: -1
        return state.copy(
            queue = queue.copy(
                originalQueue = originalQueue,
                stableShuffleSequence = shuffleSequence.insertIntoUnplayed(
                    sequence = queue.stableShuffleSequence,
                    currentIndex = currentIndex,
                    newItemIds = newItems.map(QueueItem::id),
                ),
            ),
        )
    }

    fun playNext(
        state: PlaybackQueueState,
        newItems: List<QueueItem>,
    ): PlaybackQueueState {
        if (newItems.isEmpty()) return state
        validateNewItems(state.queue, newItems)
        val queue = state.queue
        val currentId = queue.currentItemId
        if (state.mode == PlaybackMode.SHUFFLE) {
            val currentIndex = queue.shuffleCursor ?: queue.stableShuffleSequence.lastIndex
            val updatedSequence = queue.stableShuffleSequence.toMutableList().apply {
                addAll(currentIndex + 1, newItems.map(QueueItem::id))
            }
            return state.copy(
                queue = queue.copy(
                    originalQueue = queue.originalQueue + newItems,
                    stableShuffleSequence = updatedSequence,
                ),
            )
        }

        val insertionIndex = queue.originalQueue.indexOfFirst { it.id == currentId }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: queue.originalQueue.size
        val updatedOriginal = queue.originalQueue.toMutableList().apply {
            addAll(insertionIndex, newItems)
        }
        return state.copy(queue = queue.copy(originalQueue = updatedOriginal))
    }

    fun remove(
        state: PlaybackQueueState,
        itemId: QueueItemId,
    ): PlaybackQueueState {
        val queue = state.queue
        if (queue.originalQueue.none { it.id == itemId }) return state
        if (queue.originalQueue.size == 1) return state.copy(queue = PlaybackQueue())

        val removingCurrent = queue.currentItemId == itemId
        val successorId = if (removingCurrent) {
            val order = queue.playbackOrder
            val currentIndex = order.indexOfFirst { it.id == itemId }
            order[(currentIndex + 1) % order.size].id
        } else {
            queue.currentItemId
        }
        val originalQueue = queue.originalQueue.filterNot { it.id == itemId }
        val removedAtShuffleEnd = removingCurrent &&
            state.mode == PlaybackMode.SHUFFLE &&
            queue.shuffleCursor == queue.stableShuffleSequence.lastIndex
        val stableSequence = if (removedAtShuffleEnd) {
            val itemsById = queue.originalQueue.associateBy(QueueItem::id)
            shuffleSequence.nextRound(
                itemIds = originalQueue.map(QueueItem::id),
                previousLastItemId = itemId,
                representsSameTrack = { firstId, secondId ->
                    itemsById.getValue(firstId).trackId == itemsById.getValue(secondId).trackId
                },
            )
        } else {
            queue.stableShuffleSequence.filterNot { it == itemId }
        }
        val resolvedSuccessorId = if (removedAtShuffleEnd) stableSequence.first() else successorId
        val newCursor = stableSequence.indexOf(resolvedSuccessorId).takeIf { it >= 0 }
        return state.copy(
            queue = queue.copy(
                originalQueue = originalQueue,
                stableShuffleSequence = stableSequence,
                currentItemId = resolvedSuccessorId,
                shuffleRound = if (removedAtShuffleEnd) queue.shuffleRound + 1 else queue.shuffleRound,
                shuffleCursor = newCursor,
            ),
        )
    }

    private fun nextShuffleRound(state: PlaybackQueueState): PlaybackQueueState {
        val queue = state.queue
        val itemsById = queue.originalQueue.associateBy(QueueItem::id)
        val nextSequence = shuffleSequence.nextRound(
            itemIds = queue.originalQueue.map(QueueItem::id),
            previousLastItemId = requireNotNull(queue.currentItemId),
            representsSameTrack = { firstId, secondId ->
                itemsById.getValue(firstId).trackId == itemsById.getValue(secondId).trackId
            },
        )
        return state.copy(
            queue = queue.copy(
                stableShuffleSequence = nextSequence,
                currentItemId = nextSequence.firstOrNull(),
                shuffleRound = queue.shuffleRound + 1,
                shuffleCursor = nextSequence.indices.firstOrNull(),
            ),
        )
    }

    private fun moveToIndex(
        state: PlaybackQueueState,
        targetIndex: Int?,
    ): PlaybackQueueState {
        val targetId = targetIndex?.let { state.queue.playbackOrder[it].id } ?: return state
        return state.copy(
            queue = state.queue.copy(
                currentItemId = targetId,
                shuffleCursor = if (state.mode == PlaybackMode.SHUFFLE) targetIndex else null,
            ),
        )
    }

    private fun PlaybackQueue.currentPlaybackIndex(): Int? =
        currentItemId?.let { current -> playbackOrder.indexOfFirst { it.id == current }.takeIf { it >= 0 } }

    private fun validateNewItems(
        queue: PlaybackQueue,
        newItems: List<QueueItem>,
    ) {
        val newIds = newItems.map(QueueItem::id)
        require(newIds.size == newIds.distinct().size) { "new QueueItemIds must be unique" }
        val existingIds = queue.originalQueue.map(QueueItem::id).toSet()
        require(newIds.none(existingIds::contains)) { "new QueueItemIds must be unique in the queue" }
    }
}
