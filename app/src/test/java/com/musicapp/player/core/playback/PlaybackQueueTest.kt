package com.musicapp.player.core.playback

import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackQueueTest {
    private val reducer = PlaybackQueueReducer { 0 }

    @Test
    fun repeatedTrackUsesIndependentQueueItemIdentity() {
        val repeatedTrack = TrackId("external", 10)
        val state = reducer.replaceQueue(
            items = listOf(item(1, repeatedTrack), item(2, repeatedTrack)),
            currentItemId = id(1),
        )

        assertEquals(listOf(id(1), id(2)), state.queue.originalQueue.map(QueueItem::id))
        assertEquals(listOf(repeatedTrack, repeatedTrack), state.queue.originalQueue.map(QueueItem::trackId))
    }

    @Test
    fun duplicateQueueItemIdentityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue(
                originalQueue = listOf(item(1), item(1, TrackId("external", 2))),
                currentItemId = id(1),
            )
        }
    }

    @Test
    fun listRepeatWrapsManualNavigationAtBothBoundaries() {
        val first = reducer.replaceQueue(items(1, 2, 3), id(1))

        assertEquals(id(3), reducer.manualPrevious(first).queue.currentItemId)
        val last = first.copy(queue = first.queue.copy(currentItemId = id(3)))
        assertEquals(id(1), reducer.manualNext(last).queue.currentItemId)
    }

    @Test
    fun singleRepeatOnlyRepeatsNaturalEnd() {
        val state = reducer.replaceQueue(items(1, 2, 3), id(2), PlaybackMode.SINGLE_REPEAT)

        assertEquals(id(2), reducer.naturalEnd(state).queue.currentItemId)
        assertEquals(id(3), reducer.manualNext(state).queue.currentItemId)
        assertEquals(id(1), reducer.manualPrevious(state).queue.currentItemId)
    }

    @Test
    fun playNextInOriginalOrderPreservesSelectionOrder() {
        val state = reducer.replaceQueue(items(1, 2, 3), id(1))

        val updated = reducer.playNext(state, items(4, 5))

        assertEquals(listOf(1L, 4L, 5L, 2L, 3L), updated.queue.originalQueue.map { it.id.value })
        assertEquals(id(1), updated.queue.currentItemId)
    }

    @Test
    fun appendAddsItemsAtOriginalQueueTail() {
        val state = reducer.replaceQueue(items(1, 2), id(1))

        val updated = reducer.append(state, items(3, 4))

        assertEquals(listOf(1L, 2L, 3L, 4L), updated.queue.originalQueue.map { it.id.value })
    }

    @Test
    fun removingCurrentSelectsNextAndRemovingOnlyItemClearsQueue() {
        val state = reducer.replaceQueue(items(1, 2, 3), id(2))
        val removed = reducer.remove(state, id(2))

        assertEquals(id(3), removed.queue.currentItemId)
        assertEquals(listOf(id(1), id(3)), removed.queue.originalQueue.map(QueueItem::id))

        val only = reducer.replaceQueue(items(9), id(9))
        val empty = reducer.remove(only, id(9))
        assertEquals(emptyList<QueueItem>(), empty.queue.originalQueue)
        assertNull(empty.queue.currentItemId)
    }

    @Test
    fun removingUnrelatedItemKeepsCurrentItem() {
        val state = reducer.replaceQueue(items(1, 2, 3), id(2))

        val updated = reducer.remove(state, id(1))

        assertEquals(id(2), updated.queue.currentItemId)
    }

    @Test
    fun removingOneRepeatedTrackOccurrenceKeepsTheOtherOccurrence() {
        val repeatedTrack = TrackId("external", 10)
        val state = reducer.replaceQueue(
            items = listOf(item(1, repeatedTrack), item(2, repeatedTrack), item(3)),
            currentItemId = id(1),
        )

        val updated = reducer.remove(state, id(1))

        assertEquals(listOf(id(2), id(3)), updated.queue.originalQueue.map(QueueItem::id))
        assertEquals(repeatedTrack, updated.queue.originalQueue.first().trackId)
    }

    private fun items(vararg values: Long): List<QueueItem> = values.map(::item)

    private fun item(value: Long): QueueItem = item(value, TrackId("external", value))

    private fun item(value: Long, trackId: TrackId): QueueItem = QueueItem(id(value), trackId)

    private fun id(value: Long) = QueueItemId(value)
}
