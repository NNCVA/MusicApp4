package com.musicapp.player.core.playback.snapshot

import com.musicapp.player.core.domain.model.PlaybackInstance
import com.musicapp.player.core.domain.model.PlaybackQueue
import com.musicapp.player.core.domain.model.PlaybackSnapshot
import com.musicapp.player.core.domain.model.QueueItem
import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.TrackId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSnapshotCoordinatorTest {
    @Test
    fun `queue track seek pause and destroy save immediately`() = runTest {
        val sink = MemorySink()
        var now = 1L
        val coordinator = coordinator(sink) { now++ }

        coordinator.onQueueChanged()
        coordinator.onTrackChanged()
        coordinator.onSeekCompleted()
        coordinator.onPaused()
        coordinator.onDestroyed()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PlaybackSnapshotTrigger.QUEUE_CHANGED,
                PlaybackSnapshotTrigger.TRACK_CHANGED,
                PlaybackSnapshotTrigger.SEEK_COMPLETED,
                PlaybackSnapshotTrigger.PAUSED,
                PlaybackSnapshotTrigger.DESTROYED,
            ),
            sink.writes.map { it.trigger },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sink.writes.map { it.snapshot.updatedAtMs })
    }

    @Test
    fun `playing writes position every five seconds and pause stops timer`() = runTest {
        val sink = MemorySink()
        var position = 0L
        val coordinator = coordinator(sink) { testScheduler.currentTime }
        coordinatorState = { snapshot(positionMs = position) }

        coordinator.onPlaying()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(0, sink.writes.size)
        position = 5_000
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PlaybackSnapshotTrigger.PLAYING_INTERVAL, sink.writes.single().trigger)
        assertEquals(5_000L, sink.writes.single().snapshot.positionMs)

        coordinator.onPaused()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, sink.writes.size)
        assertEquals(PlaybackSnapshotTrigger.PAUSED, sink.writes.last().trigger)
    }

    @Test
    fun `buffering stops periodic writes without emitting a pause snapshot`() = runTest {
        val sink = MemorySink()
        val coordinator = coordinator(sink) { testScheduler.currentTime }
        coordinator.onPlaying()
        advanceTimeBy(2_000)

        coordinator.onNotPlaying()
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(sink.writes.isEmpty())
    }

    @Test
    fun `restore prunes missing items and keeps original and shuffle order consistent`() {
        val source = snapshot(
            queue = PlaybackQueue(
                originalQueue = items(1, 2, 3, 4),
                stableShuffleSequence = ids(3, 1, 4, 2),
                currentItemId = id(1),
                shuffleRound = 3,
                shuffleCursor = 1,
            ),
            positionMs = 2_500,
            playbackInstance = PlaybackInstance(id(1), track(1), startedAtMs = 10),
        )

        val restored = PlaybackSnapshotRestorer.restore(source, ids(2, 3, 4).toSet())!!

        assertEquals(ids(2, 3, 4), restored.snapshot.queue.originalQueue.map { it.id })
        assertEquals(ids(3, 4, 2), restored.snapshot.queue.stableShuffleSequence)
        assertEquals(id(4), restored.snapshot.queue.currentItemId)
        assertEquals(1, restored.snapshot.queue.shuffleCursor)
        assertEquals(0L, restored.snapshot.positionMs)
        assertNull(restored.snapshot.playbackInstance)
        assertFalse(restored.playWhenReady)
    }

    @Test
    fun `restore preserves current position and instance when current item exists`() {
        val instance = PlaybackInstance(id(2), track(2), startedAtMs = 20)
        val source = snapshot(
            queue = PlaybackQueue(items(1, 2, 3), ids(3, 2, 1), id(2), 1, 1),
            positionMs = 9_000,
            playbackInstance = instance,
        )

        val restored = PlaybackSnapshotRestorer.restore(source, ids(2, 3).toSet())!!

        assertEquals(ids(2, 3), restored.snapshot.queue.originalQueue.map { it.id })
        assertEquals(ids(3, 2), restored.snapshot.queue.stableShuffleSequence)
        assertEquals(id(2), restored.snapshot.queue.currentItemId)
        assertEquals(1, restored.snapshot.queue.shuffleCursor)
        assertEquals(9_000L, restored.snapshot.positionMs)
        assertEquals(instance, restored.snapshot.playbackInstance)
        assertFalse(restored.playWhenReady)
    }

    @Test
    fun `restore of fully missing queue is empty and never auto plays`() {
        val restored = PlaybackSnapshotRestorer.restore(snapshot(), emptySet())!!

        assertEquals(PlaybackQueue(), restored.snapshot.queue)
        assertEquals(0L, restored.snapshot.positionMs)
        assertNull(restored.snapshot.playbackInstance)
        assertFalse(restored.playWhenReady)
    }

    private var coordinatorState: () -> PlaybackSnapshot = { snapshot() }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        sink: MemorySink,
        now: () -> Long,
    ): PlaybackSnapshotCoordinator = PlaybackSnapshotCoordinator(
        scope = this,
        snapshotProvider = { coordinatorState() },
        sink = sink,
        clock = PlaybackSnapshotClock(now),
        scheduler = PlaybackSnapshotScheduler { delay(it) },
    )

    private fun snapshot(
        queue: PlaybackQueue = PlaybackQueue(items(1, 2, 3), currentItemId = id(1)),
        positionMs: Long = 500,
        playbackInstance: PlaybackInstance? = null,
    ) = PlaybackSnapshot(queue = queue, positionMs = positionMs, playbackInstance = playbackInstance)

    private fun items(vararg values: Long) = values.map { QueueItem(id(it), track(it)) }
    private fun ids(vararg values: Long) = values.map(::id)
    private fun id(value: Long) = QueueItemId(value)
    private fun track(value: Long) = TrackId("external", value)
}

private class MemorySink : PlaybackSnapshotSink {
    val writes = mutableListOf<PlaybackSnapshotWrite>()

    override suspend fun save(write: PlaybackSnapshotWrite) {
        writes += write
    }
}
