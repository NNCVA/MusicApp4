package com.musicapp.player.core.playback

import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeReducerTest {
    @Test
    fun enteringAndLeavingShufflePreservesCurrentAndRestoresOriginalOrder() {
        val reducer = reducer(0, 0, 0)
        val original = items(1, 2, 3, 4)
        val listState = reducer.replaceQueue(original, id(3))

        val shuffled = reducer.setMode(listState, PlaybackMode.SHUFFLE)
        val restored = reducer.setMode(shuffled, PlaybackMode.SINGLE_REPEAT)

        assertEquals(PlaybackMode.SHUFFLE, shuffled.mode)
        assertEquals(id(3), shuffled.queue.currentItemId)
        assertEquals(id(3), shuffled.queue.stableShuffleSequence.first())
        assertEquals(original, restored.queue.playbackOrder)
        assertEquals(id(3), restored.queue.currentItemId)
        assertTrue(restored.queue.stableShuffleSequence.isEmpty())
    }

    @Test
    fun naturalEndCreatesNewShuffleRoundWithoutBoundaryRepeat() {
        val reducer = reducer(0, 0, 1, 1)
        val entered = reducer.replaceQueue(items(1, 2, 3), id(1), PlaybackMode.SHUFFLE)
        val atEndId = entered.queue.stableShuffleSequence.last()
        val atEnd = entered.copy(
            queue = entered.queue.copy(
                currentItemId = atEndId,
                shuffleCursor = entered.queue.stableShuffleSequence.lastIndex,
            ),
        )

        val nextRound = reducer.naturalEnd(atEnd)

        assertEquals(atEnd.queue.shuffleRound + 1, nextRound.queue.shuffleRound)
        assertNotEquals(atEndId, nextRound.queue.currentItemId)
        assertEquals(0, nextRound.queue.shuffleCursor)
    }

    @Test
    fun manualNextAtShuffleBoundaryCreatesNewRound() {
        val reducer = reducer(0, 0, 1, 1)
        val state = reducer.replaceQueue(items(1, 2, 3), id(1), PlaybackMode.SHUFFLE)
        val atEnd = state.copy(
            queue = state.queue.copy(
                currentItemId = state.queue.stableShuffleSequence.last(),
                shuffleCursor = state.queue.stableShuffleSequence.lastIndex,
            ),
        )

        val next = reducer.manualNext(atEnd)

        assertNotEquals(atEnd.queue.currentItemId, next.queue.currentItemId)
        assertEquals(atEnd.queue.shuffleRound + 1, next.queue.shuffleRound)
        assertEquals(0, next.queue.shuffleCursor)
    }

    @Test
    fun randomAppendKeepsPlayedPrefixAndOriginalTail() {
        val reducer = reducer(0, 0, 0, 0)
        val entered = reducer.replaceQueue(items(1, 2, 3), id(2), PlaybackMode.SHUFFLE)
        val currentPrefix = entered.queue.stableShuffleSequence.take(1)

        val updated = reducer.append(entered, items(4, 5))

        assertEquals(currentPrefix, updated.queue.stableShuffleSequence.take(1))
        assertEquals(listOf(4L, 5L), updated.queue.originalQueue.takeLast(2).map { it.id.value })
        assertEquals(ids(1, 2, 3, 4, 5).toSet(), updated.queue.stableShuffleSequence.toSet())
    }

    @Test
    fun randomPlayNextPreservesSelectionOrderImmediatelyAfterCurrent() {
        val reducer = reducer(0, 0)
        val entered = reducer.replaceQueue(items(1, 2, 3), id(2), PlaybackMode.SHUFFLE)
        val currentIndex = entered.queue.shuffleCursor!!

        val updated = reducer.playNext(entered, items(4, 5))

        assertEquals(ids(4, 5), updated.queue.stableShuffleSequence.drop(currentIndex + 1).take(2))
        assertEquals(ids(4, 5), updated.queue.originalQueue.takeLast(2).map(QueueItem::id))
        assertEquals(entered.queue.currentItemId, updated.queue.currentItemId)
    }

    @Test
    fun removingCurrentShuffleItemSelectsStableSuccessor() {
        val reducer = reducer(0, 0)
        val entered = reducer.replaceQueue(items(1, 2, 3), id(1), PlaybackMode.SHUFFLE)
        val expected = entered.queue.stableShuffleSequence[1]

        val updated = reducer.remove(entered, id(1))

        assertEquals(expected, updated.queue.currentItemId)
        assertEquals(expected, updated.queue.stableShuffleSequence[updated.queue.shuffleCursor!!])
        assertTrue(id(1) !in updated.queue.stableShuffleSequence)
    }

    @Test
    fun removingCurrentAtShuffleEndStartsRemainingItemsAsNewRound() {
        val reducer = reducer(0, 0, 1)
        val entered = reducer.replaceQueue(items(1, 2, 3), id(1), PlaybackMode.SHUFFLE)
        val removedId = entered.queue.stableShuffleSequence.last()
        val atEnd = entered.copy(
            queue = entered.queue.copy(
                currentItemId = removedId,
                shuffleCursor = entered.queue.stableShuffleSequence.lastIndex,
            ),
        )

        val updated = reducer.remove(atEnd, removedId)

        assertEquals(entered.queue.shuffleRound + 1, updated.queue.shuffleRound)
        assertEquals(0, updated.queue.shuffleCursor)
        assertEquals(updated.queue.stableShuffleSequence.first(), updated.queue.currentItemId)
        assertTrue(removedId !in updated.queue.stableShuffleSequence)
    }

    @Test
    fun removingShuffleEndAvoidsSameTrackFromAnotherQueueItemAtNewRoundStart() {
        val repeatedTrack = TrackId("external", 100)
        val original = listOf(
            QueueItem(id(1), repeatedTrack),
            QueueItem(id(2), TrackId("external", 200)),
            QueueItem(id(3), repeatedTrack),
        )
        val atEnd = PlaybackQueueState(
            queue = PlaybackQueue(
                originalQueue = original,
                stableShuffleSequence = ids(2, 3, 1),
                currentItemId = id(1),
                shuffleRound = 4,
                shuffleCursor = 2,
            ),
            mode = PlaybackMode.SHUFFLE,
        )

        val updated = reducer(0).remove(atEnd, id(1))

        assertEquals(id(2), updated.queue.currentItemId)
        assertEquals(id(2), updated.queue.stableShuffleSequence.first())
        assertEquals(5, updated.queue.shuffleRound)
    }

    @Test
    fun modeSelectionRemainsOneMutuallyExclusiveValue() {
        val reducer = reducer(0, 0)
        val initial = reducer.replaceQueue(items(1, 2), id(1))

        val single = reducer.setMode(initial, PlaybackMode.SINGLE_REPEAT)
        val shuffle = reducer.setMode(single, PlaybackMode.SHUFFLE)
        val list = reducer.setMode(shuffle, PlaybackMode.LIST_REPEAT)

        assertEquals(PlaybackMode.SINGLE_REPEAT, single.mode)
        assertEquals(PlaybackMode.SHUFFLE, shuffle.mode)
        assertEquals(PlaybackMode.LIST_REPEAT, list.mode)
    }

    private fun reducer(vararg randomValues: Int): PlaybackQueueReducer =
        PlaybackQueueReducer(QueueRandomSource(*randomValues))

    private fun items(vararg values: Long): List<QueueItem> = values.map { value ->
        QueueItem(id(value), TrackId("external", value))
    }

    private fun ids(vararg values: Long): List<QueueItemId> = values.map(::id)

    private fun id(value: Long) = QueueItemId(value)
}

private class QueueRandomSource(
    vararg values: Int,
) : RandomSource {
    private val values = ArrayDeque(values.toList())

    override fun nextInt(untilExclusive: Int): Int {
        require(untilExclusive > 0)
        val value = if (values.isEmpty()) 0 else values.removeFirst()
        return Math.floorMod(value, untilExclusive)
    }
}
