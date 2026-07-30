package com.musicapp.player.core.playback.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioInterruptionPolicyTest {
    private val policy = AudioInterruptionPolicy()

    @Test
    fun `transient focus loss pauses and resumes only when playback was active`() {
        val loss = policy.onInterruption(AudioInterruption.TRANSIENT_FOCUS_LOSS, wasPlaying = true)
        assertTrue(loss.pause)
        assertTrue(loss.cancelPendingFade)
        assertTrue(loss.restoreUnityVolume)
        assertTrue(policy.onInterruption(AudioInterruption.FOCUS_GAIN, wasPlaying = false).resume)

        policy.onInterruption(AudioInterruption.TRANSIENT_FOCUS_LOSS, wasPlaying = false)
        assertFalse(policy.onInterruption(AudioInterruption.FOCUS_GAIN, wasPlaying = false).resume)
    }

    @Test
    fun `permanent loss and private output loss never auto resume`() {
        policy.onInterruption(AudioInterruption.TRANSIENT_FOCUS_LOSS, wasPlaying = true)
        val permanent = policy.onInterruption(AudioInterruption.PERMANENT_FOCUS_LOSS, wasPlaying = true)
        assertEquals(AudioInterruptionAction(pause = true, cancelPendingFade = true, restoreUnityVolume = true), permanent)
        assertFalse(policy.onInterruption(AudioInterruption.FOCUS_GAIN, wasPlaying = false).resume)

        val disconnected = policy.onInterruption(AudioInterruption.PRIVATE_OUTPUT_LOST, wasPlaying = true)
        assertTrue(disconnected.pause)
        assertFalse(policy.onInterruption(AudioInterruption.FOCUS_GAIN, wasPlaying = false).resume)
    }

    @Test
    fun `duck delegates volume handling without transition or pause`() {
        val action = policy.onInterruption(AudioInterruption.DUCK, wasPlaying = true)

        assertTrue(action.allowSystemDuck)
        assertFalse(action.pause)
        assertFalse(action.cancelPendingFade)
    }

    @Test
    fun `manual pause clears pending transient resume`() {
        policy.onInterruption(AudioInterruption.TRANSIENT_FOCUS_LOSS, wasPlaying = true)
        policy.onUserPause()

        assertFalse(policy.onInterruption(AudioInterruption.FOCUS_GAIN, wasPlaying = false).resume)
    }
}
