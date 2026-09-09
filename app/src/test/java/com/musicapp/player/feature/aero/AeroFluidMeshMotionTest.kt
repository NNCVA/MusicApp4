package com.musicapp.player.feature.aero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AeroFluidMeshMotionTest {

    @Test
    fun constants_matchSpecification() {
        assertEquals(36_000, AeroFluidMeshMotion.FLUID_MESH_CYCLE_MS)
        assertEquals(500, AeroFluidMeshMotion.COLOR_CROSSFADE_DURATION_MS)
    }

    @Test
    fun calculateCenter_isIdenticalAtPhase0AndPhase1_forAllColors() {
        val width = 1080f
        val height = 2400f
        val colorCount = 3

        for (colorIndex in 0 until colorCount) {
            val start = AeroFluidMeshMotion.calculateCenter(0f, colorIndex, colorCount, width, height)
            val end = AeroFluidMeshMotion.calculateCenter(1f, colorIndex, colorCount, width, height)

            assertEquals("X offset mismatch at cycle boundary for color $colorIndex", start.x, end.x, 0.001f)
            assertEquals("Y offset mismatch at cycle boundary for color $colorIndex", start.y, end.y, 0.001f)
        }
    }

    @Test
    fun calculateCenter_velocityIsContinuousAcrossCycleBoundary() {
        val width = 1000f
        val height = 1000f
        val colorCount = 3
        val delta = 0.001f

        for (colorIndex in 0 until colorCount) {
            val dStartX = (AeroFluidMeshMotion.calculateCenter(delta, colorIndex, colorCount, width, height).x -
                AeroFluidMeshMotion.calculateCenter(-delta, colorIndex, colorCount, width, height).x) / (2 * delta)
            val dEndX = (AeroFluidMeshMotion.calculateCenter(1f + delta, colorIndex, colorCount, width, height).x -
                AeroFluidMeshMotion.calculateCenter(1f - delta, colorIndex, colorCount, width, height).x) / (2 * delta)

            val dStartY = (AeroFluidMeshMotion.calculateCenter(delta, colorIndex, colorCount, width, height).y -
                AeroFluidMeshMotion.calculateCenter(-delta, colorIndex, colorCount, width, height).y) / (2 * delta)
            val dEndY = (AeroFluidMeshMotion.calculateCenter(1f + delta, colorIndex, colorCount, width, height).y -
                AeroFluidMeshMotion.calculateCenter(1f - delta, colorIndex, colorCount, width, height).y) / (2 * delta)

            assertEquals("X velocity discontinuity at cycle boundary for color $colorIndex", dStartX, dEndX, 0.5f)
            assertEquals("Y velocity discontinuity at cycle boundary for color $colorIndex", dStartY, dEndY, 0.5f)
        }
    }

    @Test
    fun calculateCenter_distinctColorsHaveSeparatedCenters() {
        val width = 1080f
        val height = 2400f

        val c0 = AeroFluidMeshMotion.calculateCenter(0f, 0, 3, width, height)
        val c1 = AeroFluidMeshMotion.calculateCenter(0f, 1, 3, width, height)
        val c2 = AeroFluidMeshMotion.calculateCenter(0f, 2, 3, width, height)

        assertNotEquals(c0, c1)
        assertNotEquals(c1, c2)
        assertNotEquals(c0, c2)
    }

    @Test
    fun calculateCenter_remainsWithinSafeCanvasBoundsThroughoutCycle() {
        val width = 1080f
        val height = 2400f
        val colorCount = 3

        var phase = 0f
        while (phase <= 1f) {
            for (colorIndex in 0 until colorCount) {
                val center = AeroFluidMeshMotion.calculateCenter(phase, colorIndex, colorCount, width, height)
                assertTrue("Center X should be positive: ${center.x}", center.x > 0f)
                assertTrue("Center X should be within width: ${center.x}", center.x < width)
                assertTrue("Center Y should be positive: ${center.y}", center.y > 0f)
                assertTrue("Center Y should be within height: ${center.y}", center.y < height)
            }
            phase += 0.02f
        }
    }
}
