package com.musicapp.player.core.playback.history

import com.musicapp.player.core.domain.model.QueueItemId
import com.musicapp.player.core.domain.model.PlaybackInstance
import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryRecorderTest {
    @Test
    fun `long track records at thirty seconds and only once`() {
        val fixture = Fixture()
        fixture.recorder.startInstance(item(1), track(1), durationMs = 180_000, isPlaying = true)

        fixture.advanceMonotonic(29_999)
        fixture.recorder.tick()
        assertTrue(fixture.records.isEmpty())

        fixture.advanceMonotonic(1)
        fixture.wallClockMs = 90_000
        fixture.recorder.tick()
        fixture.advanceMonotonic(60_000)
        fixture.recorder.tick()

        assertEquals(listOf(HistoryRecord(track(1), 90_000)), fixture.records)
        assertEquals(90_000L, fixture.recorder.snapshot()?.actualPlayedDurationMs)
        assertTrue(fixture.recorder.snapshot()?.historyRecorded == true)
    }

    @Test
    fun `odd short duration uses rounded up half threshold`() {
        assertEquals(501L, PlayHistoryRecorder.historyThresholdMs(1_001))
        assertEquals(30_000L, PlayHistoryRecorder.historyThresholdMs(60_001))

        val fixture = Fixture()
        fixture.recorder.startInstance(item(1), track(1), durationMs = 1_001, isPlaying = true)
        fixture.advanceMonotonic(500)
        fixture.recorder.tick()
        assertTrue(fixture.records.isEmpty())

        fixture.advanceMonotonic(1)
        fixture.recorder.tick()
        assertEquals(1, fixture.records.size)
    }

    @Test
    fun `pause buffering and seek time do not count while repeated played segments do`() {
        val fixture = Fixture()
        fixture.recorder.startInstance(item(1), track(1), durationMs = 20_000, isPlaying = true)
        fixture.advanceMonotonic(4_000)
        fixture.recorder.updateIsPlaying(isPlaying = false)

        fixture.advanceMonotonic(20_000)
        fixture.recorder.updateIsPlaying(isPlaying = true)
        fixture.advanceMonotonic(3_000)
        fixture.recorder.onSeekStarted()
        fixture.advanceMonotonic(8_000)
        fixture.recorder.onSeekCompleted(isPlaying = true)
        fixture.advanceMonotonic(3_000)
        fixture.recorder.tick()

        assertEquals(10_000L, fixture.recorder.snapshot()?.actualPlayedDurationMs)
        assertEquals(1, fixture.records.size)
    }

    @Test
    fun `same track in another queue item creates another playback instance`() {
        val fixture = Fixture()
        val track = track(7)
        fixture.recorder.startInstance(item(1), track, durationMs = 2_000, isPlaying = true)
        fixture.advanceMonotonic(1_000)
        fixture.recorder.tick()

        fixture.wallClockMs = 2_000
        fixture.recorder.startInstance(item(2), track, durationMs = 2_000, isPlaying = true)
        fixture.advanceMonotonic(1_000)
        fixture.recorder.tick()

        assertEquals(
            listOf(HistoryRecord(track, 1_000), HistoryRecord(track, 2_000)),
            fixture.records,
        )
        assertEquals(item(2), fixture.recorder.snapshot()?.queueItemId)
        assertEquals(1_000L, fixture.recorder.snapshot()?.actualPlayedDurationMs)
    }

    @Test
    fun `switching before threshold discards old instance without a record`() {
        val fixture = Fixture()
        fixture.recorder.startInstance(item(1), track(1), durationMs = 10_000, isPlaying = true)
        fixture.advanceMonotonic(4_999)
        fixture.recorder.startInstance(item(2), track(2), durationMs = 10_000, isPlaying = false)

        assertTrue(fixture.records.isEmpty())
        assertEquals(item(2), fixture.recorder.snapshot()?.queueItemId)
        assertFalse(fixture.recorder.snapshot()?.historyRecorded ?: true)
    }

    @Test
    fun `switching at threshold records the completed old instance`() {
        val fixture = Fixture()
        fixture.recorder.startInstance(item(1), track(1), durationMs = 10_000, isPlaying = true)
        fixture.advanceMonotonic(5_000)
        fixture.recorder.startInstance(item(2), track(2), durationMs = 10_000, isPlaying = false)

        assertEquals(listOf(HistoryRecord(track(1), 1_000)), fixture.records)
        assertEquals(item(2), fixture.recorder.snapshot()?.queueItemId)
    }

    @Test
    fun `same queue item cannot change track identity`() {
        val fixture = Fixture()
        fixture.recorder.startInstance(item(1), track(1), durationMs = 10_000, isPlaying = false)

        assertThrows(IllegalArgumentException::class.java) {
            fixture.recorder.startInstance(item(1), track(2), durationMs = 10_000, isPlaying = false)
        }
    }

    @Test
    fun `backwards monotonic clock is rejected`() {
        val fixture = Fixture()
        fixture.monotonicMs = 10
        fixture.recorder.startInstance(item(1), track(1), durationMs = 10_000, isPlaying = true)
        fixture.monotonicMs = 9

        assertThrows(IllegalArgumentException::class.java) { fixture.recorder.tick() }
    }

    @Test
    fun `restored playback instance continues accumulated time without duplicate history`() {
        val fixture = Fixture()
        fixture.recorder.restoreInstance(
            instance = PlaybackInstance(
                queueItemId = item(1),
                trackId = track(1),
                startedAtMs = 100,
                actualPlayedDurationMs = 4_000,
                historyRecorded = false,
            ),
            durationMs = 10_000,
            isPlaying = true,
        )
        fixture.advanceMonotonic(1_000)
        fixture.recorder.tick()

        assertEquals(5_000L, fixture.recorder.snapshot()?.actualPlayedDurationMs)
        assertEquals(listOf(HistoryRecord(track(1), 1_000)), fixture.records)
    }

    private class Fixture {
        var monotonicMs = 0L
        var wallClockMs = 1_000L
        val records = mutableListOf<HistoryRecord>()
        val recorder = PlayHistoryRecorder(
            monotonicNowMs = { monotonicMs },
            wallClockNowMs = { wallClockMs },
            onHistoryThresholdReached = { trackId, playedAtMs ->
                records += HistoryRecord(trackId, playedAtMs)
            },
        )

        fun advanceMonotonic(durationMs: Long) {
            monotonicMs += durationMs
        }
    }

    private data class HistoryRecord(val trackId: TrackId, val playedAtMs: Long)

    private fun item(value: Long) = QueueItemId(value)

    private fun track(value: Long) = TrackId("external", value)
}
