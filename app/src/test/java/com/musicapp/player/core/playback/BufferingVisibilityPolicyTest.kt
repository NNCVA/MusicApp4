package com.musicapp.player.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferingVisibilityPolicyTest {
    private val policy = BufferingVisibilityPolicy()

    @Test
    fun `buffering becomes visible only after three hundred milliseconds`() {
        assertFalse(policy.update(isBuffering = true, nowMs = 1_000))
        assertFalse(policy.update(isBuffering = true, nowMs = 1_299))
        assertTrue(policy.update(isBuffering = true, nowMs = 1_300))
    }

    @Test
    fun `ready state cancels a pending buffering delay`() {
        policy.update(isBuffering = true, nowMs = 1_000)
        assertFalse(policy.update(isBuffering = false, nowMs = 1_100))

        assertFalse(policy.update(isBuffering = true, nowMs = 2_000))
        assertFalse(policy.update(isBuffering = true, nowMs = 2_299))
    }
}
