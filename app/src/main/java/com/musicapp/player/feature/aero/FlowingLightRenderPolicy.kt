package com.musicapp.player.feature.aero

import android.graphics.Color

internal data class FlowingLightBufferSpec(val width: Int, val height: Int)

internal data class FlowingLightViewportSpec(
    val offsetX: Int,
    val offsetY: Int,
    val width: Int,
    val height: Int,
)

internal data class FlowingLightCrossfade(
    val previousAlpha: Float,
    val currentAlpha: Float,
)

internal object FlowingLightRenderPolicy {
    const val HIGH_DENSITY_DPI = 420
    const val HIGH_DENSITY_DIVISOR = 32
    const val LOW_DENSITY_DIVISOR = 20
    const val BUFFER_PADDING_PX = 58
    const val SATURATION = 2.5f
    const val REFERENCE_BLUR_RADIUS_PX = 25
    const val BLUR_PASSES = 3
    const val FRAME_DELAY_MS = 42L
    const val PRIMARY_PERIOD_MS = 100_000L
    const val SECONDARY_PERIOD_MS = 70_000L
    const val TERTIARY_PERIOD_MS = 40_000L
    const val ARTWORK_COVERAGE = 1.3f
    const val SECONDARY_TRANSLATION_X = -0.95f
    const val SECONDARY_TRANSLATION_Y = -0.7f
    const val TERTIARY_TRANSLATION_X = -0.5f
    const val TERTIARY_TRANSLATION_Y = 0.7f

    val DARK_OVERLAYS: IntArray = intArrayOf(0x52000000, 0x1A000000)
    val LIGHT_OVERLAYS: IntArray = intArrayOf(0x95FFFFFF.toInt(), 0x2AFFFFFF)
    val BOX_BLUR_RADII: IntArray = intArrayOf(9, 9, 10)

    // Stable M1 mesh from the reference implementation. A 5 x 5 mesh has 6 x 6 vertices.
    val M1_VERTICES: FloatArray = floatArrayOf(
        0f, 0f, .2f, 0f, .4f, 0f, .6f, 0f, .8f, 0f, 1f, 0f,
        0f, .2f, -.0933f, .4f, .4f, .2f, .6f, .2f, .3653f, .1335f, 1f, .2f,
        0f, .4f, .4232f, .359f, .3429f, .5349f, .6f, .4f, .832f, .4148f, 1f, .4f,
        0f, .6f, .2f, .6f, .2293f, .7775f, .7829f, .5595f, .6514f, .7302f, 1f, .6f,
        0f, .8f, .2f, .8f, .28f, .9195f, .4773f, .8f, .8f, .8f, 1f, .8f,
        0f, 1f, .6514f, 1.1073f, .4f, 1f, 1f, 1.0317f, 1f, 1.1302f, 1f, 1f,
    )

    fun bufferSpec(screenWidth: Int, screenHeight: Int, densityDpi: Int): FlowingLightBufferSpec {
        val divisor = densityDivisor(densityDpi)
        return FlowingLightBufferSpec(
            width = (screenWidth / divisor + BUFFER_PADDING_PX).coerceAtLeast(1),
            height = (screenHeight / divisor + BUFFER_PADDING_PX).coerceAtLeast(1),
        )
    }

    fun viewportSpec(screenWidth: Int, screenHeight: Int, densityDpi: Int): FlowingLightViewportSpec {
        val divisor = densityDivisor(densityDpi)
        val buffer = bufferSpec(screenWidth, screenHeight, densityDpi)
        val width = (screenWidth / divisor).coerceAtLeast(1)
        val height = (screenHeight / divisor).coerceAtLeast(1)
        return FlowingLightViewportSpec(
            offsetX = (buffer.width - width) / 2,
            offsetY = (buffer.height - height) / 2,
            width = width,
            height = height,
        )
    }

    fun angleDegrees(elapsedRealtimeMs: Long, layer: Int): Float {
        val period = when (layer) {
            0 -> PRIMARY_PERIOD_MS
            1 -> SECONDARY_PERIOD_MS
            2 -> TERTIARY_PERIOD_MS
            else -> error("Unknown flowing-light layer: $layer")
        }
        val direction = if (layer == 0) 1f else -1f
        return (elapsedRealtimeMs.mod(period).toFloat() / period * 360f) * direction
    }

    fun shouldScheduleFrames(
        dynamic: Boolean,
        isPlaying: Boolean,
        isVisible: Boolean,
        runtimeAllowsFrames: Boolean,
        transitionRunning: Boolean,
    ): Boolean = dynamic && isPlaying && isVisible && runtimeAllowsFrames && !transitionRunning

    fun crossfade(progress: Float, hasPreviousFrame: Boolean): FlowingLightCrossfade {
        val current = progress.coerceIn(0f, 1f)
        return if (hasPreviousFrame) {
            FlowingLightCrossfade(previousAlpha = 1f - current, currentAlpha = current)
        } else {
            FlowingLightCrossfade(previousAlpha = 0f, currentAlpha = 1f)
        }
    }

    fun centerSampleArgb(pixels: IntArray, width: Int, height: Int): Int {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong())
        var alphaWeight = 0L
        var red = 0L
        var green = 0L
        var blue = 0L
        for (sampleY in 0 until 5) {
            val y = (((sampleY + .5f) * height) / 5f).toInt().coerceIn(0, height - 1)
            for (sampleX in 0 until 5) {
                val x = (((sampleX + .5f) * width) / 5f).toInt().coerceIn(0, width - 1)
                val color = pixels[y * width + x]
                val alpha = Color.alpha(color).toLong()
                alphaWeight += alpha
                red += Color.red(color) * alpha
                green += Color.green(color) * alpha
                blue += Color.blue(color) * alpha
            }
        }
        if (alphaWeight == 0L) return Color.BLACK
        return Color.rgb(
            (red / alphaWeight).toInt(),
            (green / alphaWeight).toInt(),
            (blue / alphaWeight).toInt(),
        )
    }

    fun compositeOver(base: Int, overlay: Int): Int {
        val overlayAlpha = Color.alpha(overlay)
        val inverse = 255 - overlayAlpha
        return Color.rgb(
            (Color.red(overlay) * overlayAlpha + Color.red(base) * inverse + 127) / 255,
            (Color.green(overlay) * overlayAlpha + Color.green(base) * inverse + 127) / 255,
            (Color.blue(overlay) * overlayAlpha + Color.blue(base) * inverse + 127) / 255,
        )
    }

    fun blurThreePasses(
        pixels: IntArray,
        scratch: IntArray,
        width: Int,
        height: Int,
        uniformRadiusForTest: Int? = null,
    ) {
        require(pixels.size == width * height && scratch.size == pixels.size)
        require(uniformRadiusForTest == null || uniformRadiusForTest >= 0)
        if (uniformRadiusForTest == 0 || pixels.isEmpty()) return
        repeat(BLUR_PASSES) { pass ->
            val radius = uniformRadiusForTest ?: BOX_BLUR_RADII[pass]
            boxBlurHorizontal(pixels, scratch, width, height, radius)
            boxBlurVertical(scratch, pixels, width, height, radius)
        }
    }

    private fun densityDivisor(densityDpi: Int): Int =
        if (densityDpi >= HIGH_DENSITY_DPI) HIGH_DENSITY_DIVISOR else LOW_DENSITY_DIVISOR

    private fun boxBlurHorizontal(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        for (y in 0 until height) {
            val row = y * width
            var left = 0
            var right = radius.coerceAtMost(width - 1)
            var red = 0L
            var green = 0L
            var blue = 0L
            for (x in left..right) {
                val color = source[row + x]
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
            }
            for (x in 0 until width) {
                val count = right - left + 1
                target[row + x] = Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
                val nextLeft = (x - radius + 1).coerceAtLeast(0)
                val nextRight = (x + radius + 1).coerceAtMost(width - 1)
                while (left < nextLeft) {
                    val color = source[row + left++]
                    red -= Color.red(color)
                    green -= Color.green(color)
                    blue -= Color.blue(color)
                }
                while (right < nextRight) {
                    val color = source[row + ++right]
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
            }
        }
    }

    private fun boxBlurVertical(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        for (x in 0 until width) {
            var top = 0
            var bottom = radius.coerceAtMost(height - 1)
            var red = 0L
            var green = 0L
            var blue = 0L
            for (y in top..bottom) {
                val color = source[y * width + x]
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
            }
            for (y in 0 until height) {
                val count = bottom - top + 1
                target[y * width + x] = Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
                val nextTop = (y - radius + 1).coerceAtLeast(0)
                val nextBottom = (y + radius + 1).coerceAtMost(height - 1)
                while (top < nextTop) {
                    val color = source[top++ * width + x]
                    red -= Color.red(color)
                    green -= Color.green(color)
                    blue -= Color.blue(color)
                }
                while (bottom < nextBottom) {
                    val color = source[++bottom * width + x]
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
            }
        }
    }
}
