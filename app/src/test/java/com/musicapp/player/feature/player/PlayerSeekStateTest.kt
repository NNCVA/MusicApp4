package com.musicapp.player.feature.player

import com.musicapp.player.core.domain.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSeekStateTest {
    private val trackId = TrackId("external", 1)
    private val otherTrackId = TrackId("external", 2)

    @Test
    fun `backward seek remains pending while stale position is still ahead`() {
        val commit = PlayerSeekState()
            .beginDrag(0.35f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)

        assertEquals(35_000L, commit.targetMs)
        assertFalse(PlayerSeekPolicy.isAcknowledged(commit.state.pending!!, 60_000))
        val requestId = checkNotNull(commit.state.pending).requestId
        assertEquals(
            commit.state,
            commit.state.acknowledgePosition(requestId, trackId, 100_000, 60_000),
        )
    }

    @Test
    fun `backward seek is acknowledged after position moves near target`() {
        val commit = PlayerSeekState()
            .beginDrag(0.35f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)

        val acknowledged = commit.state.acknowledgePosition(
            commit.state.pending!!.requestId,
            trackId,
            100_000,
            35_400,
        )

        assertNull(acknowledged.pending)
    }

    @Test
    fun `forward seek is acknowledged when observed position is near target`() {
        val commit = PlayerSeekState()
            .beginDrag(0.70f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 20_000)

        val acknowledged = commit.state.acknowledgePosition(
            commit.state.pending!!.requestId,
            trackId,
            100_000,
            69_600,
        )

        assertNull(acknowledged.pending)
    }

    @Test
    fun `small seek can acknowledge on the next position sample`() {
        val commit = PlayerSeekState()
            .beginDrag(0.501f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 50_000)

        val acknowledged = commit.state.acknowledgePosition(
            commit.state.pending!!.requestId,
            trackId,
            100_000,
            50_200,
        )

        assertNull(acknowledged.pending)
    }

    @Test
    fun `same position seek is immediately acknowledged`() {
        val commit = PlayerSeekState()
            .beginDrag(0.5f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 50_000)

        assertTrue(PlayerSeekPolicy.isAcknowledged(commit.state.pending!!, 50_000))
    }

    @Test
    fun `old timeout cannot clear a newer pending seek`() {
        val first = PlayerSeekState()
            .beginDrag(0.2f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)
        val second = first.state
            .beginDrag(0.4f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)

        val afterOldTimeout = second.state.timeout(first.state.pending!!.requestId)

        assertEquals(second.state.pending, afterOldTimeout.pending)
        assertEquals(2L, afterOldTimeout.pending!!.requestId)
    }

    @Test
    fun `old position acknowledgement cannot clear a newer pending seek`() {
        val first = PlayerSeekState()
            .beginDrag(0.2f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)
        val second = first.state
            .beginDrag(0.4f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)

        val afterOldAcknowledgement = second.state.acknowledgePosition(
            requestId = first.state.pending!!.requestId,
            trackId = trackId,
            durationMs = 100_000,
            positionMs = first.targetMs,
        )

        assertEquals(second.state.pending, afterOldAcknowledgement.pending)
        assertEquals(2L, afterOldAcknowledgement.pending!!.requestId)
    }

    @Test
    fun `matching timeout clears pending silently`() {
        val commit = PlayerSeekState()
            .beginDrag(0.4f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)

        assertNull(commit.state.timeout(commit.state.pending!!.requestId).pending)
    }

    @Test
    fun `cancelled second drag preserves an earlier pending seek`() {
        val first = PlayerSeekState()
            .beginDrag(0.2f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)
        val cancelled = first.state.beginDrag(0.4f).cancelDrag()

        assertEquals(first.state.pending, cancelled.pending)
        assertNull(cancelled.dragFraction)
    }

    @Test
    fun `source reset clears drag and pending state`() {
        val state = PlayerSeekState()
            .beginDrag(0.4f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)
            .state
            .beginDrag(0.6f)

        val reset = state.resetForSource()

        assertNull(reset.dragFraction)
        assertNull(reset.pending)
    }

    @Test
    fun `position from another source cannot acknowledge pending seek`() {
        val commit = PlayerSeekState()
            .beginDrag(0.4f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)

        val unchanged = commit.state.acknowledgePosition(
            commit.state.pending!!.requestId,
            otherTrackId,
            100_000,
            40_000,
        )

        assertSame(commit.state, unchanged)
    }

    @Test
    fun `commit clamps target and drag fraction to track bounds`() {
        val commit = PlayerSeekState()
            .beginDrag(2f)
            .commit(trackId = trackId, durationMs = 100_000, baselineMs = 60_000)

        assertEquals(100_000L, commit.targetMs)
        assertEquals(100_000L, commit.state.pending!!.targetMs)
    }
}
