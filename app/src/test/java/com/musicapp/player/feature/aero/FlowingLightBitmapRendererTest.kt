package com.musicapp.player.feature.aero

import android.graphics.Color
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.metadata.ArtworkImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlowingLightBitmapRendererTest {
    @Test
    fun `synthetic four-color artwork retains low-frequency zones and suppresses hard edges`() {
        val renderer = FlowingLightBitmapRenderer()
        try {
            val output = renderer.render(
                artwork = fourColorArtwork(128),
                screenWidth = 1080,
                screenHeight = 2400,
                densityDpi = 440,
                darkTheme = true,
                mode = AeroMode.FLUID_MESH,
                elapsedRealtimeMs = 17_000,
            )
            val pixels = IntArray(output.width * output.height)
            output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            val viewport = FlowingLightRenderPolicy.viewportSpec(1080, 2400, 440)
            val sampled = sampleViewportGrid(pixels, output.width, viewport)
            assertTrue(sampled.maxOf(::luma) - sampled.minOf(::luma) > 8)
            assertTrue("Expected at least three retained hue families", hueFamilyCount(sampled) >= 3)
            assertTrue("Expected a retained cool green-gray region", sampled.any(::isCoolGreen))
            assertTrue("Expected a retained warm orange region", sampled.any(::isWarmOrange))
            val middleY = viewport.offsetY + viewport.height / 2
            val adjacentDelta = (viewport.offsetX + 1 until viewport.offsetX + viewport.width).maxOf { x ->
                channelDistance(pixels[middleY * output.width + x - 1], pixels[middleY * output.width + x])
            }
            assertTrue(adjacentDelta < 45)
        } finally {
            renderer.close()
        }
    }

    @Test
    fun `light mask output is brighter than dark mask output`() {
        val artwork = fourColorArtwork(64)
        val renderer = FlowingLightBitmapRenderer()
        try {
            val dark = renderer.render(artwork, 1080, 2400, 440, true, AeroMode.GLOW_AURA, 0)
            val darkPixels = IntArray(dark.width * dark.height).also {
                dark.getPixels(it, 0, dark.width, 0, 0, dark.width, dark.height)
            }
            val light = renderer.render(artwork, 1080, 2400, 440, false, AeroMode.GLOW_AURA, 0)
            val lightPixels = IntArray(light.width * light.height).also {
                light.getPixels(it, 0, light.width, 0, 0, light.width, light.height)
            }
            val viewport = FlowingLightRenderPolicy.viewportSpec(1080, 2400, 440)
            val darkSamples = sampleViewportGrid(darkPixels, dark.width, viewport)
            val lightSamples = sampleViewportGrid(lightPixels, light.width, viewport)
            assertTrue(lightSamples.map(::luma).average() > darkSamples.map(::luma).average())
            assertTrue("Light mask must retain regional color differences", lightSamples.maxOf(::luma) - lightSamples.minOf(::luma) > 5)
        } finally {
            renderer.close()
        }
    }

    private fun fourColorArtwork(side: Int): ArtworkImage = ArtworkImage(
        width = side,
        height = side,
        argbPixels = IntArray(side * side) { index ->
            val x = index % side
            val y = index / side
            when {
                x < side / 2 && y < side / 2 -> Color.rgb(225, 82, 28)
                x >= side / 2 && y < side / 2 -> Color.rgb(46, 130, 75)
                x < side / 2 -> Color.rgb(105, 115, 125)
                else -> Color.rgb(20, 24, 30)
            }
        },
    )

    private fun luma(color: Int): Int = (Color.red(color) * 3 + Color.green(color) * 6 + Color.blue(color)) / 10

    private fun sampleViewportGrid(
        pixels: IntArray,
        stride: Int,
        viewport: FlowingLightViewportSpec,
    ): List<Int> = buildList {
        for (row in 1..5) {
            val y = viewport.offsetY + viewport.height * row / 6
            for (column in 1..4) {
                val x = viewport.offsetX + viewport.width * column / 5
                add(pixels[y * stride + x])
            }
        }
    }

    private fun hueFamilyCount(colors: List<Int>): Int = colors
        .mapNotNull { color ->
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0].takeIf { hsv[1] >= .08f }?.let { (it / 30f).toInt() }
        }
        .distinct()
        .size

    private fun isCoolGreen(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0] in 60f..180f && hsv[1] >= .08f
    }

    private fun isWarmOrange(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0] in 5f..55f && hsv[1] >= .12f
    }

    private fun channelDistance(first: Int, second: Int): Int = maxOf(
        abs(Color.red(first) - Color.red(second)),
        abs(Color.green(first) - Color.green(second)),
        abs(Color.blue(first) - Color.blue(second)),
    )
}
