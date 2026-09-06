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
    fun `calculateRevealRadius expands linearly and clamps properly`() {
        val minDimension = 300f
        val maxRadius = 150f

        assertEquals(0f, ArtworkDiscMotion.calculateRevealRadius(minDimension, 0f), 0.001f)
        assertEquals(75f, ArtworkDiscMotion.calculateRevealRadius(minDimension, 0.5f), 0.001f)
        assertEquals(maxRadius, ArtworkDiscMotion.calculateRevealRadius(minDimension, 1.0f), 0.001f)
        // 边界保护
        assertEquals(0f, ArtworkDiscMotion.calculateRevealRadius(minDimension, -0.5f), 0.001f)
        assertEquals(maxRadius, ArtworkDiscMotion.calculateRevealRadius(minDimension, 1.5f), 0.001f)
    }

    @Test
    fun `motion duration constants match specification`() {
        assertEquals(20_000, ArtworkDiscMotion.ROTATION_CYCLE_MS)
        assertEquals(350, ArtworkDiscMotion.REWIND_DURATION_MS)
        assertEquals(350, ArtworkDiscMotion.REVEAL_DURATION_MS)
    }
}
