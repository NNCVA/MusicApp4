package com.musicapp.player.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class RotatingArtworkDiscTest {

    @Test
    fun `normalizeAngle keeps angles within 0 to 360 degrees`() {
        assertEquals(0f, ArtworkDiscMotion.normalizeAngle(0f), 0.001f)
        assertEquals(90f, ArtworkDiscMotion.normalizeAngle(90f), 0.001f)
        assertEquals(180f, ArtworkDiscMotion.normalizeAngle(180f), 0.001f)
        assertEquals(0f, ArtworkDiscMotion.normalizeAngle(360f), 0.001f)
        assertEquals(45f, ArtworkDiscMotion.normalizeAngle(405f), 0.001f)
        assertEquals(350f, ArtworkDiscMotion.normalizeAngle(-10f), 0.001f)
        assertEquals(270f, ArtworkDiscMotion.normalizeAngle(-90f), 0.001f)
    }

    @Test
    fun `rewindAngle moves counter clockwise to zero and clamps progress`() {
        assertEquals(270f, ArtworkDiscMotion.rewindAngle(270f, 0f), 0.001f)
        assertEquals(135f, ArtworkDiscMotion.rewindAngle(270f, 0.5f), 0.001f)
        assertEquals(0f, ArtworkDiscMotion.rewindAngle(270f, 1f), 0.001f)
        assertEquals(270f, ArtworkDiscMotion.rewindAngle(270f, -0.5f), 0.001f)
        assertEquals(0f, ArtworkDiscMotion.rewindAngle(270f, 1.5f), 0.001f)
    }

    @Test
    fun `motion constants match specification`() {
        assertEquals(20_000, ArtworkDiscMotion.ROTATION_CYCLE_MS)
        assertEquals(500, ArtworkDiscMotion.REWIND_DURATION_MS)
        assertEquals(20f, ArtworkDiscMotion.MAX_BLUR_DP, 0.001f)
    }

    @Test
    fun `alpha and blur mappings share the same progress`() {
        assertEquals(1f, ArtworkDiscMotion.outgoingAlpha(0f), 0.001f)
        assertEquals(0.5f, ArtworkDiscMotion.outgoingAlpha(0.5f), 0.001f)
        assertEquals(0f, ArtworkDiscMotion.outgoingAlpha(1f), 0.001f)
        assertEquals(0f, ArtworkDiscMotion.incomingAlpha(0f), 0.001f)
        assertEquals(1f, ArtworkDiscMotion.incomingAlpha(1f), 0.001f)
        assertEquals(0f, ArtworkDiscMotion.outgoingBlurRadiusDp(0f), 0.001f)
        assertEquals(10f, ArtworkDiscMotion.outgoingBlurRadiusDp(0.5f), 0.001f)
        assertEquals(20f, ArtworkDiscMotion.incomingBlurRadiusDp(0f), 0.001f)
        assertEquals(0f, ArtworkDiscMotion.incomingBlurRadiusDp(1f), 0.001f)
    }
}
