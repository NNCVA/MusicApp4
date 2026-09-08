package com.musicapp.player.media.service

import com.musicapp.player.core.playback.timer.SleepTimerStatus
import com.musicapp.player.core.playback.timer.formatRemainingTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerCoordinatorTest {

    @Test
    fun `start emits initial status and ticks remaining time`() = runTest {
        var isPlaying = true
        var pauseCount = 0
        var latestStatus: SleepTimerStatus? = null

        val coordinator = SleepTimerCoordinator(
            scope = backgroundScope,
            isCurrentlyPlaying = { isPlaying },
            onFadeAndPause = { pauseCount++ },
            onStatusChanged = { latestStatus = it },
            tickIntervalMs = 1_000L,
        )

        coordinator.start(durationMinutes = 15, extendToEndOfTrack = false)
        runCurrent()

        assertNotNull(latestStatus)
        assertEquals(15 * 60_000L, latestStatus?.remainingMs)
        assertEquals(15 * 60_000L, latestStatus?.totalDurationMs)
        assertFalse(latestStatus!!.extendToEndOfTrack)
        assertFalse(latestStatus!!.isWaitingForTrackEnd)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(15 * 60_000L - 1_000L, latestStatus?.remainingMs)

        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(15 * 60_000L - 6_000L, latestStatus?.remainingMs)
        assertEquals(0, pauseCount)

        coordinator.stop()
        assertNull(coordinator.currentStatus)
    }

    @Test
    fun `expires without extend calls onFadeAndPause and clears status`() = runTest {
        var pauseCount = 0
        var latestStatus: SleepTimerStatus? = null

        val coordinator = SleepTimerCoordinator(
            scope = backgroundScope,
            isCurrentlyPlaying = { true },
            onFadeAndPause = { pauseCount++ },
            onStatusChanged = { latestStatus = it },
            tickIntervalMs = 100L,
        )

        // 1 minute duration = 60_000ms
        coordinator.start(durationMinutes = 1, extendToEndOfTrack = false)
        runCurrent()
        assertEquals(60_000L, latestStatus?.remainingMs)

        advanceTimeBy(60_100L)
        runCurrent()

        assertEquals(1, pauseCount)
        assertNull(latestStatus)
        assertNull(coordinator.currentStatus)
    }

    @Test
    fun `expires with extend and playing enters waiting state then pauses on natural track end`() = runTest {
        var pauseCount = 0
        var latestStatus: SleepTimerStatus? = null

        val coordinator = SleepTimerCoordinator(
            scope = backgroundScope,
            isCurrentlyPlaying = { true },
            onFadeAndPause = { pauseCount++ },
            onStatusChanged = { latestStatus = it },
            tickIntervalMs = 100L,
        )

        coordinator.start(durationMinutes = 1, extendToEndOfTrack = true)
        runCurrent()

        // Advance until initial timer expires
        advanceTimeBy(60_000L)
        runCurrent()
        assertNotNull(latestStatus)
        assertTrue(latestStatus!!.isWaitingForTrackEnd)
        assertEquals(0L, latestStatus!!.remainingMs)
        assertEquals(0L, latestStatus!!.extendedElapsedMs)
        assertEquals(0, pauseCount)

        // Extended time continues ticking
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(500L, latestStatus!!.extendedElapsedMs)
        assertEquals(0, pauseCount)

        // Track finishes naturally
        coordinator.onTrackEndedNaturally()
        runCurrent()

        assertEquals(1, pauseCount)
        assertNull(coordinator.currentStatus)
    }

    @Test
    fun `waiting for track end cancels on manual interruption and notifies`() = runTest {
        var pauseCount = 0
        var cancelNotified = false
        var latestStatus: SleepTimerStatus? = null

        val coordinator = SleepTimerCoordinator(
            scope = backgroundScope,
            isCurrentlyPlaying = { true },
            onFadeAndPause = { pauseCount++ },
            onStatusChanged = { latestStatus = it },
            onManualCancellationNotice = { cancelNotified = true },
            tickIntervalMs = 100L,
        )

        coordinator.start(durationMinutes = 1, extendToEndOfTrack = true)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue(latestStatus!!.isWaitingForTrackEnd)

        // User manually pauses or changes track
        coordinator.onManualInterruption()
        advanceUntilIdle()

        assertTrue(cancelNotified)
        assertEquals(0, pauseCount)
        assertNull(coordinator.currentStatus)
    }

    @Test
    fun `formatRemainingTime formats normal and waiting states accurately`() {
        val normalStatus = SleepTimerStatus(
            remainingMs = 15 * 60_000L + 53_000L,
            totalDurationMs = 20 * 60_000L,
            extendToEndOfTrack = false,
        )
        assertEquals("15:53", normalStatus.formatRemainingTime())

        val lowSecondsStatus = SleepTimerStatus(
            remainingMs = 5_000L,
            totalDurationMs = 60_000L,
            extendToEndOfTrack = false,
        )
        assertEquals("00:05", lowSecondsStatus.formatRemainingTime())

        val waitingStatus = SleepTimerStatus(
            remainingMs = 0L,
            totalDurationMs = 60_000L,
            extendToEndOfTrack = true,
            isWaitingForTrackEnd = true,
            extendedElapsedMs = 2 * 60_000L + 15_000L,
        )
        assertEquals("+02:15", waitingStatus.formatRemainingTime())

        val nullStatus: SleepTimerStatus? = null
        assertEquals("", nullStatus.formatRemainingTime())
    }

    @Test
    fun `expires without extend triggers onTimerExpired callback`() = runTest {
        var pauseCount = 0
        var expiredCount = 0

        val coordinator = SleepTimerCoordinator(
            scope = backgroundScope,
            isCurrentlyPlaying = { true },
            onFadeAndPause = { pauseCount++ },
            onStatusChanged = {},
            onTimerExpired = { expiredCount++ },
            tickIntervalMs = 100L,
        )

        coordinator.start(durationMinutes = 1, extendToEndOfTrack = false)
        runCurrent()
        assertEquals(0, expiredCount)

        advanceTimeBy(60_100L)
        runCurrent()

        assertEquals(1, pauseCount)
        assertEquals(1, expiredCount)
        assertNull(coordinator.currentStatus)
    }

    @Test
    fun `expires with extend triggers onTimerExpired only after natural track end`() = runTest {
        var pauseCount = 0
        var expiredCount = 0

        val coordinator = SleepTimerCoordinator(
            scope = backgroundScope,
            isCurrentlyPlaying = { true },
            onFadeAndPause = { pauseCount++ },
            onStatusChanged = {},
            onTimerExpired = { expiredCount++ },
            tickIntervalMs = 100L,
        )

        coordinator.start(durationMinutes = 1, extendToEndOfTrack = true)
        runCurrent()

        // Initial timer duration finishes -> waiting for track end
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(0, expiredCount)
        assertEquals(0, pauseCount)

        // Naturally ends track
        coordinator.onTrackEndedNaturally()
        runCurrent()

        assertEquals(1, pauseCount)
        assertEquals(1, expiredCount)
        assertNull(coordinator.currentStatus)
    }

    @Test
    fun `manual stop and interruption do not trigger onTimerExpired`() = runTest {
        var expiredCount = 0

        val coordinator = SleepTimerCoordinator(
            scope = backgroundScope,
            isCurrentlyPlaying = { true },
            onFadeAndPause = {},
            onStatusChanged = {},
            onTimerExpired = { expiredCount++ },
            tickIntervalMs = 100L,
        )

        // 1. Manual stop before expiry
        coordinator.start(durationMinutes = 1, extendToEndOfTrack = false)
        runCurrent()
        advanceTimeBy(30_000L)
        coordinator.stop()
        runCurrent()
        assertEquals(0, expiredCount)

        // 2. Manual interruption during waiting for track end
        coordinator.start(durationMinutes = 1, extendToEndOfTrack = true)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()
        coordinator.onManualInterruption()
        runCurrent()
        assertEquals(0, expiredCount)
    }
}
