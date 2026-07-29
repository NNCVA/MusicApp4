package com.musicapp.player.core.domain

import com.musicapp.player.core.domain.policy.PlayHistoryThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryThresholdTest {
    @Test
    fun thresholdIsHalfDurationRoundedUpAndCappedAtThirtySeconds() {
        assertEquals(500L, PlayHistoryThreshold.thresholdMillis(1_000L))
        assertEquals(501L, PlayHistoryThreshold.thresholdMillis(1_001L))
        assertEquals(30_000L, PlayHistoryThreshold.thresholdMillis(120_000L))
    }

    @Test
    fun reachingThresholdIsInclusive() {
        assertFalse(PlayHistoryThreshold.isReached(durationMillis = 1_001L, actualPlaybackMillis = 500L))
        assertTrue(PlayHistoryThreshold.isReached(durationMillis = 1_001L, actualPlaybackMillis = 501L))
    }

    @Test
    fun durationMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            PlayHistoryThreshold.thresholdMillis(0L)
        }
    }
}
