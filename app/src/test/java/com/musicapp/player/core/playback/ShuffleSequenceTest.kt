package com.musicapp.player.core.playback

import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.domain.model.QueueItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShuffleSequenceTest {
    @Test
    fun enteringShuffleKeepsCurrentItemAtStableCursorStart() {
        val shuffle = ShuffleSequence(ScriptedRandomSource(0, 1, 0))

        val sequence = shuffle.enter(ids(1, 2, 3, 4), id(3))

        assertEquals(id(3), sequence.first())
        assertEquals(ids(1, 2, 3, 4).toSet(), sequence.toSet())
    }

    @Test
    fun generatedSequenceIsStableUntilExplicitlyRegenerated() {
        val shuffle = ShuffleSequence(ScriptedRandomSource(0, 0, 0))
        val sequence = shuffle.enter(ids(1, 2, 3), id(1))

        assertEquals(sequence, sequence.toList())
    }

    @Test
    fun nextRoundCannotRepeatBoundaryItemWhenQueueHasAlternatives() {
        val shuffle = ShuffleSequence(ScriptedRandomSource(1, 1, 1))

        val nextRound = shuffle.nextRound(ids(1, 2, 3), previousLastItemId = id(1))

        assertNotEquals(id(1), nextRound.first())
        assertEquals(ids(1, 2, 3).toSet(), nextRound.toSet())
    }

    @Test
    fun nextRoundCannotRepeatTrackAcrossDifferentQueueItems() {
        val shuffle = ShuffleSequence(ScriptedRandomSource(1, 1, 1))
        val trackByItem = mapOf(id(1) to "repeated", id(2) to "repeated", id(3) to "other")

        val nextRound = shuffle.nextRound(
            itemIds = ids(1, 2, 3),
            previousLastItemId = id(1),
            representsSameTrack = { first, second -> trackByItem.getValue(first) == trackByItem.getValue(second) },
        )

        assertEquals(id(3), nextRound.first())
    }

    @Test
    fun insertionChangesOnlyUnplayedSuffix() {
        val shuffle = ShuffleSequence(ScriptedRandomSource(0, 1))
        val original = ids(1, 2, 3, 4)

        val updated = shuffle.insertIntoUnplayed(original, currentIndex = 1, newItemIds = ids(5, 6))

        assertEquals(ids(1, 2), updated.take(2))
        assertEquals(ids(1, 2, 3, 4, 5, 6).toSet(), updated.toSet())
    }

    @Test
    fun insertionBeforePlaybackStartsCanUseWholeSequence() {
        val shuffle = ShuffleSequence(ScriptedRandomSource(0))

        val updated = shuffle.insertIntoUnplayed(ids(1, 2), currentIndex = -1, newItemIds = ids(3))

        assertEquals(ids(3, 1, 2), updated)
    }

    private fun ids(vararg values: Long): List<QueueItemId> = values.map(::id)

    private fun id(value: Long) = QueueItemId(value)
}

private class ScriptedRandomSource(
    vararg values: Int,
) : RandomSource {
    private val values = ArrayDeque(values.toList())

    override fun nextInt(untilExclusive: Int): Int {
        require(untilExclusive > 0)
        val value = if (values.isEmpty()) 0 else values.removeFirst()
        return Math.floorMod(value, untilExclusive)
    }
}
