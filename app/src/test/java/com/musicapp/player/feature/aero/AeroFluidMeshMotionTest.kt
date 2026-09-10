package com.musicapp.player.feature.aero

import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AeroFluidMeshMotionTest {
    @Test
    fun `buffer size follows density-specific reference formula`() {
        assertEquals(FlowingLightBufferSpec(91, 133), FlowingLightRenderPolicy.bufferSpec(1080, 2400, 440))
        assertEquals(FlowingLightBufferSpec(112, 178), FlowingLightRenderPolicy.bufferSpec(1080, 2400, 419))
    }

    @Test
    fun `viewport crops the centered content and keeps padding outside the screen`() {
        assertEquals(
            FlowingLightViewportSpec(offsetX = 29, offsetY = 29, width = 40, height = 89),
            FlowingLightRenderPolicy.viewportSpec(1280, 2856, 480),
        )
        assertEquals(FlowingLightRenderPolicy.BUFFER_PADDING_PX / 2, 29)
    }

    @Test
    fun `three box kernels approximate one reference radius instead of repeating it`() {
        assertEquals(25, FlowingLightRenderPolicy.REFERENCE_BLUR_RADIUS_PX)
        assertTrue(FlowingLightRenderPolicy.BOX_BLUR_RADII.contentEquals(intArrayOf(9, 9, 10)))
        assertTrue(FlowingLightRenderPolicy.BOX_BLUR_RADII.all { it < FlowingLightRenderPolicy.REFERENCE_BLUR_RADIUS_PX })
    }

    @Test
    fun `center sample is alpha weighted across five by five positions`() {
        val pixels = IntArray(25) { Color.argb(255, 200, 100, 50) }
        pixels[12] = Color.argb(0, 255, 255, 255)
        assertEquals(Color.rgb(200, 100, 50), FlowingLightRenderPolicy.centerSampleArgb(pixels, 5, 5))
    }

    @Test
    fun `overlay order matches light and dark reference layers`() {
        assertTrue(FlowingLightRenderPolicy.DARK_OVERLAYS.contentEquals(intArrayOf(0x52000000, 0x1A000000)))
        assertTrue(FlowingLightRenderPolicy.LIGHT_OVERLAYS.contentEquals(intArrayOf(0x95FFFFFF.toInt(), 0x2AFFFFFF)))
        val source = Color.rgb(180, 90, 30)
        val dark = FlowingLightRenderPolicy.DARK_OVERLAYS.fold(source, FlowingLightRenderPolicy::compositeOver)
        val light = FlowingLightRenderPolicy.LIGHT_OVERLAYS.fold(source, FlowingLightRenderPolicy::compositeOver)
        assertTrue(Color.red(light) > Color.red(source))
        assertTrue(Color.red(dark) < Color.red(source))
    }

    @Test
    fun `three artwork layers use independent reference periods`() {
        assertEquals(180f, FlowingLightRenderPolicy.angleDegrees(50_000, 0), .001f)
        assertEquals(-180f, FlowingLightRenderPolicy.angleDegrees(35_000, 1), .001f)
        assertEquals(-180f, FlowingLightRenderPolicy.angleDegrees(20_000, 2), .001f)
        assertEquals(0f, FlowingLightRenderPolicy.angleDegrees(100_000, 0), .001f)
    }

    @Test
    fun `M1 contains stable six by six mesh vertices`() {
        assertEquals(72, FlowingLightRenderPolicy.M1_VERTICES.size)
        assertEquals(-.0933f, FlowingLightRenderPolicy.M1_VERTICES[14], .00001f)
        assertEquals(1.1302f, FlowingLightRenderPolicy.M1_VERTICES[69], .00001f)
    }

    @Test
    fun `blur clamps edges and suppresses a high frequency impulse`() {
        val pixels = IntArray(9) { Color.BLACK }
        pixels[4] = Color.WHITE
        val scratch = IntArray(pixels.size)
        FlowingLightRenderPolicy.blurThreePasses(pixels, scratch, 3, 3, uniformRadiusForTest = 1)
        assertTrue(Color.red(pixels[4]) in 20..200)
        assertTrue(Color.red(pixels[0]) > 0)
        assertEquals(pixels[0], pixels[8])
    }

    @Test
    fun `scheduler pauses for every frozen state`() {
        fun schedules(
            dynamic: Boolean = true,
            playing: Boolean = true,
            visible: Boolean = true,
            runtime: Boolean = true,
            transition: Boolean = false,
        ) = FlowingLightRenderPolicy.shouldScheduleFrames(dynamic, playing, visible, runtime, transition)

        assertTrue(schedules())
        assertFalse(schedules(dynamic = false))
        assertFalse(schedules(playing = false))
        assertFalse(schedules(visible = false))
        assertFalse(schedules(runtime = false))
        assertFalse(schedules(transition = true))
    }

    @Test
    fun `crossfade endpoints retain old frame until new frame completes`() {
        assertEquals(FlowingLightCrossfade(1f, 0f), FlowingLightRenderPolicy.crossfade(0f, true))
        assertEquals(FlowingLightCrossfade(0f, 1f), FlowingLightRenderPolicy.crossfade(1f, true))
        assertEquals(FlowingLightCrossfade(0f, 1f), FlowingLightRenderPolicy.crossfade(0f, false))
    }

    @Test
    fun `palette transition uses shared progress endpoints`() {
        val start = AeroPalette(ComposeColor.Black, ComposeColor.Red, ComposeColor.Green, ComposeColor.Blue)
        val end = AeroPalette(ComposeColor.White, ComposeColor.Cyan, ComposeColor.Magenta, ComposeColor.Yellow)
        assertEquals(start, lerpAeroPalette(start, end, 0f))
        assertEquals(end, lerpAeroPalette(start, end, 1f))
        assertNotEquals(start.base, lerpAeroPalette(start, end, .5f).base)
    }
}
