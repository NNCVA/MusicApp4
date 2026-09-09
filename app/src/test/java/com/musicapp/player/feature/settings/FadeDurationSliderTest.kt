package com.musicapp.player.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeDurationSliderTest {
    @Test
    fun `snap uses 250 ms units within the supported range`() {
        assertEquals(0L, snapFadeThroughDurationMs(-1f))
        assertEquals(250L, snapFadeThroughDurationMs(125f))
        assertEquals(1_000L, snapFadeThroughDurationMs(1_124f))
        assertEquals(2_000L, snapFadeThroughDurationMs(2_001f))
        assertEquals(7, fadeThroughSliderSteps)
    }
}
