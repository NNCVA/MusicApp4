package com.musicapp.player.core.playback

import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.domain.model.QueueItemId

/** Builds and updates a stable shuffle order without depending on a platform player. */
class ShuffleSequence(
    private val randomSource: RandomSource,
) {
    fun enter(
        itemIds: List<QueueItemId>,
        currentItemId: QueueItemId?,
    ): List<QueueItemId> {
        validateUnique(itemIds)
        require(currentItemId == null || currentItemId in itemIds) {
            "currentItemId must belong to itemIds"
        }
        if (itemIds.isEmpty()) return emptyList()
        if (currentItemId == null) return shuffled(itemIds)
        return listOf(currentItemId) + shuffled(itemIds.filterNot { it == currentItemId })
    }

    fun nextRound(
        itemIds: List<QueueItemId>,
        previousLastItemId: QueueItemId,
        representsSameTrack: (QueueItemId, QueueItemId) -> Boolean = { first, second -> first == second },
    ): List<QueueItemId> {
        validateUnique(itemIds)
        if (itemIds.isEmpty()) return emptyList()
        val next = shuffled(itemIds).toMutableList()
        if (next.size > 1 && representsSameTrack(next.first(), previousLastItemId)) {
            val replacementIndex = next.indexOfFirst { !representsSameTrack(it, previousLastItemId) }
            if (replacementIndex > 0) {
                val first = next.first()
                next[0] = next[replacementIndex]
                next[replacementIndex] = first
            }
        }
        return next
    }

    fun insertIntoUnplayed(
        sequence: List<QueueItemId>,
        currentIndex: Int,
        newItemIds: List<QueueItemId>,
    ): List<QueueItemId> {
        validateUnique(sequence)
        validateNewIds(sequence, newItemIds)
        require(currentIndex in -1..sequence.lastIndex) { "currentIndex must refer to sequence or its start" }
        if (newItemIds.isEmpty()) return sequence

        val result = sequence.toMutableList()
        newItemIds.forEach { itemId ->
            val insertionIndex = currentIndex + 1 + randomSource.nextInt(result.size - currentIndex)
            result.add(insertionIndex, itemId)
        }
        return result
    }

    private fun shuffled(itemIds: List<QueueItemId>): List<QueueItemId> {
        val result = itemIds.toMutableList()
        for (index in result.lastIndex downTo 1) {
            val replacementIndex = randomSource.nextInt(index + 1)
            val value = result[index]
            result[index] = result[replacementIndex]
            result[replacementIndex] = value
        }
        return result
    }

    private fun validateNewIds(
        existingIds: List<QueueItemId>,
        newItemIds: List<QueueItemId>,
    ) {
        validateUnique(newItemIds)
        require(newItemIds.none(existingIds::contains)) { "new QueueItemIds must be unique in the queue" }
    }

    private fun validateUnique(itemIds: List<QueueItemId>) {
        require(itemIds.size == itemIds.distinct().size) { "QueueItemIds must be unique" }
    }
}
