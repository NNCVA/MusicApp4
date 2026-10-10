package com.musicapp.player.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSheetStateTest {
    @Test
    fun `drag follows pointer and remains between the two anchors`() {
        val half = PlayerSheetState().dragBy(deltaYPx = -400f, travelPx = 800f)
        assertEquals(0.5f, half.expansionProgress)
        assertEquals(1f, half.dragBy(-1_000f, 800f).expansionProgress)
        assertEquals(0f, half.dragBy(1_000f, 800f).expansionProgress)
    }

    @Test
    fun `settle selects only collapsed or expanded and never hidden`() {
        assertEquals(PlayerSheetValue.EXPANDED, PlayerSheetState(0.2f).settle(-700f).value)
        assertEquals(0f, PlayerSheetState(0.4f).settle(0f).expansionProgress)
        assertEquals(1f, PlayerSheetState(0.6f).settle(0f).expansionProgress)
        assertTrue(PlayerLayerAlpha.mini(0f) > PlayerLayerAlpha.full(0f))
        assertTrue(PlayerLayerAlpha.full(1f) > PlayerLayerAlpha.mini(1f))
    }

    @Test
    fun `player layer alpha follows progressive transition curve`() {
        // Collapsed (progress = 0)
        assertEquals(1f, PlayerLayerAlpha.mini(0f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.background(0f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.full(0f), 0.0001f)

        // Mini player fades out in 0..0.25
        assertEquals(0.5f, PlayerLayerAlpha.mini(0.125f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.mini(0.25f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.mini(0.5f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.mini(1f), 0.0001f)

        // Full player background fades in completely in 0..0.25
        assertEquals(0.5f, PlayerLayerAlpha.background(0.125f), 0.0001f)
        assertEquals(1f, PlayerLayerAlpha.background(0.25f), 0.0001f)
        assertEquals(1f, PlayerLayerAlpha.background(0.5f), 0.0001f)
        assertEquals(1f, PlayerLayerAlpha.background(0.75f), 0.0001f)
        assertEquals(1f, PlayerLayerAlpha.background(1f), 0.0001f)

        // Full player content fades in 0.25..1.0
        assertEquals(0f, PlayerLayerAlpha.full(0.25f), 0.0001f)
        assertEquals(0.3333f, PlayerLayerAlpha.full(0.5f), 0.001f)
        assertEquals(1f, PlayerLayerAlpha.full(1f), 0.0001f)

        // Clamping bounds
        assertEquals(1f, PlayerLayerAlpha.mini(-0.1f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.mini(1.5f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.background(-0.1f), 0.0001f)
        assertEquals(1f, PlayerLayerAlpha.background(1.5f), 0.0001f)
        assertEquals(0f, PlayerLayerAlpha.full(-0.1f), 0.0001f)
        assertEquals(1f, PlayerLayerAlpha.full(1.5f), 0.0001f)
    }

    @Test
    fun `calculate settle duration scales with distance and velocity`() {
        // Zero distance
        assertEquals(0, PlayerSheetState.calculateSettleDurationMs(0f, 0f))
        assertEquals(0, PlayerSheetState.calculateSettleDurationMs(1f, 1f))

        // Full travel with zero velocity
        val fullTravelDuration = PlayerSheetState.calculateSettleDurationMs(
            currentProgress = 0f,
            targetProgress = 1f,
            velocityYPxPerSecond = 0f,
            travelPx = 1000f,
        )
        assertEquals(500, fullTravelDuration)

        // Partial travel with zero velocity
        val partialTravelDuration = PlayerSheetState.calculateSettleDurationMs(
            currentProgress = 0f,
            targetProgress = 0.5f,
            velocityYPxPerSecond = 0f,
            travelPx = 1000f,
        )
        assertEquals(375, partialTravelDuration)
        assertTrue(partialTravelDuration in 350..400)
        assertTrue(partialTravelDuration < fullTravelDuration)

        // High fling velocity reduces duration smoothly
        val highVelocityDuration = PlayerSheetState.calculateSettleDurationMs(
            currentProgress = 0.5f,
            targetProgress = 1f,
            velocityYPxPerSecond = -2500f,
            travelPx = 1000f,
        )
        assertTrue(highVelocityDuration < partialTravelDuration)
        assertTrue(highVelocityDuration >= PlayerSheetState.MIN_SETTLE_DURATION_MS)

        // Extreme velocity is clamped
        val extremeVelocityDuration = PlayerSheetState.calculateSettleDurationMs(
            currentProgress = 0f,
            targetProgress = 1f,
            velocityYPxPerSecond = -100_000f,
            travelPx = 1000f,
        )
        assertEquals(PlayerSheetState.MIN_SETTLE_DURATION_MS, extremeVelocityDuration)
    }
}
