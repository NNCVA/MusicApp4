package com.musicapp.player.core.playback.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResumptionPolicyTest {
    @Test
    fun `notification dismissal revokes resumption and never starts playback`() {
        val decision = PlaybackResumptionPolicy.decide(
            PlaybackResumptionEvent.NotificationDismissed,
        )

        assertFalse(decision.playbackResumptionAllowed)
        assertFalse(decision.resumeEntryVisible)
        assertFalse(decision.playWhenReady)
    }

    @Test
    fun `explicit stop revokes resumption and never starts playback`() {
        val decision = PlaybackResumptionPolicy.decide(PlaybackResumptionEvent.PlaybackStopped)

        assertFalse(decision.playbackResumptionAllowed)
        assertFalse(decision.resumeEntryVisible)
        assertFalse(decision.playWhenReady)
    }

    @Test
    fun `next user initiated play restores resumption eligibility`() {
        PlaybackResumptionPolicy.decide(PlaybackResumptionEvent.NotificationDismissed)

        val decision = PlaybackResumptionPolicy.decide(PlaybackResumptionEvent.UserPlayRequested)

        assertTrue(decision.playbackResumptionAllowed)
        assertFalse(decision.resumeEntryVisible)
        assertTrue(decision.playWhenReady)
    }

    @Test
    fun `process restoration exposes entry without auto playback`() {
        val decision = PlaybackResumptionPolicy.decide(
            PlaybackResumptionEvent.Restored(
                origin = PlaybackRestoreOrigin.PROCESS_RECREATION,
                hasRestorableSnapshot = true,
                storedResumptionAllowed = true,
            ),
        )

        assertTrue(decision.playbackResumptionAllowed)
        assertTrue(decision.resumeEntryVisible)
        assertFalse(decision.playWhenReady)
        assertEquals(PlaybackRestoreOrigin.PROCESS_RECREATION, decision.restoreOrigin)
    }

    @Test
    fun `device restart exposes entry without auto playback`() {
        val decision = PlaybackResumptionPolicy.decide(
            PlaybackResumptionEvent.Restored(
                origin = PlaybackRestoreOrigin.DEVICE_RESTART,
                hasRestorableSnapshot = true,
                storedResumptionAllowed = true,
            ),
        )

        assertTrue(decision.resumeEntryVisible)
        assertFalse(decision.playWhenReady)
        assertEquals(PlaybackRestoreOrigin.DEVICE_RESTART, decision.restoreOrigin)
    }

    @Test
    fun `restoration suppresses entry when snapshot is absent or ineligible`() {
        val absent = PlaybackResumptionPolicy.decide(
            PlaybackResumptionEvent.Restored(
                origin = PlaybackRestoreOrigin.PROCESS_RECREATION,
                hasRestorableSnapshot = false,
                storedResumptionAllowed = true,
            ),
        )
        val ineligible = PlaybackResumptionPolicy.decide(
            PlaybackResumptionEvent.Restored(
                origin = PlaybackRestoreOrigin.DEVICE_RESTART,
                hasRestorableSnapshot = true,
                storedResumptionAllowed = false,
            ),
        )

        assertFalse(absent.resumeEntryVisible)
        assertFalse(absent.playWhenReady)
        assertFalse(ineligible.playbackResumptionAllowed)
        assertFalse(ineligible.resumeEntryVisible)
        assertFalse(ineligible.playWhenReady)
    }
}
