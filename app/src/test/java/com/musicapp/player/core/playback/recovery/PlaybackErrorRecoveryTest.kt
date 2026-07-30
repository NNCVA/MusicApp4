package com.musicapp.player.core.playback.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorRecoveryTest {
    private val recovery = PlaybackErrorRecovery<Long>()
    private val order = listOf(10L, 20L, 30L)

    @Test
    fun `bad files are visited cyclically once before stopping`() {
        assertEquals(PlaybackRecoveryAction.TryNext(20L), recovery.onFailure(order, 10L))
        assertEquals(PlaybackRecoveryAction.TryNext(30L), recovery.onFailure(order, 20L))
        assertEquals(PlaybackRecoveryAction.StopAndRestoreVolume, recovery.onFailure(order, 30L))
    }

    @Test
    fun `ready item starts a fresh failure round`() {
        recovery.onFailure(order, 10L)
        recovery.onReady()

        assertEquals(PlaybackRecoveryAction.TryNext(20L), recovery.onFailure(order, 10L))
    }

    @Test
    fun `queue replacement discards attempts from the old queue`() {
        recovery.onFailure(order, 10L)
        recovery.onQueueReplaced()

        assertEquals(PlaybackRecoveryAction.TryNext(20L), recovery.onFailure(order, 10L))
    }

    @Test
    fun `unknown or sole failed item stops without looping`() {
        assertEquals(PlaybackRecoveryAction.StopAndRestoreVolume, recovery.onFailure(order, 99L))
        assertEquals(PlaybackRecoveryAction.StopAndRestoreVolume, recovery.onFailure(listOf(10L), 10L))
    }
}
