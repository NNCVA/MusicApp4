package com.musicapp.player.core.domain

import com.musicapp.player.core.domain.model.PlaybackMode
import com.musicapp.player.core.domain.policy.PlaybackModePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackModePolicyTest {
    @Test
    fun defaultModeIsListRepeat() {
        assertEquals(PlaybackMode.LIST_REPEAT, PlaybackModePolicy.defaultMode)
    }

    @Test
    fun naturalEndRepeatsOnlyInSingleRepeatMode() {
        assertEquals(1, PlaybackModePolicy.indexAfterNaturalEnd(PlaybackMode.SINGLE_REPEAT, 1, 3))
        assertEquals(2, PlaybackModePolicy.indexAfterNaturalEnd(PlaybackMode.LIST_REPEAT, 1, 3))
        assertEquals(0, PlaybackModePolicy.indexAfterNaturalEnd(PlaybackMode.SHUFFLE, 2, 3))
    }

    @Test
    fun listRepeatAndShuffleManualNavigationWrapAtBothBoundaries() {
        listOf(PlaybackMode.LIST_REPEAT, PlaybackMode.SHUFFLE).forEach { mode ->
            assertEquals(0, PlaybackModePolicy.indexAfterManualNext(mode, 2, 3))
            assertEquals(2, PlaybackModePolicy.indexAfterManualPrevious(mode, 0, 3))
        }
    }

    @Test
    fun singleRepeatManualNavigationStopsAtBoundariesAndMovesBetweenAdjacentItems() {
        assertEquals(2, PlaybackModePolicy.indexAfterManualNext(PlaybackMode.SINGLE_REPEAT, 2, 3))
        assertEquals(2, PlaybackModePolicy.indexAfterManualNext(PlaybackMode.SINGLE_REPEAT, 1, 3))
        assertEquals(0, PlaybackModePolicy.indexAfterManualPrevious(PlaybackMode.SINGLE_REPEAT, 0, 3))
        assertEquals(0, PlaybackModePolicy.indexAfterManualPrevious(PlaybackMode.SINGLE_REPEAT, 1, 3))
    }

    @Test
    fun emptyQueueHasNoTarget() {
        assertNull(PlaybackModePolicy.indexAfterNaturalEnd(PlaybackMode.LIST_REPEAT, 0, 0))
    }
}
