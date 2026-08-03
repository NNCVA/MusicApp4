package com.musicapp.player.core.aero

import com.musicapp.player.core.metadata.ArtworkImage

object ArtworkColorSampler {
    fun dominantArgb(
        artwork: ArtworkImage,
        maximumColorCount: Int = DEFAULT_MAXIMUM_COLOR_COUNT,
    ): List<Int> {
        require(maximumColorCount in 1..DEFAULT_MAXIMUM_COLOR_COUNT) {
            "maximumColorCount must be between 1 and $DEFAULT_MAXIMUM_COLOR_COUNT"
        }
        val pixels = artwork.argbPixels
        val sampleStep = (pixels.size / MAXIMUM_SAMPLES).coerceAtLeast(1)
        val bins = mutableMapOf<Int, ColorBin>()
        pixels.indices.step(sampleStep).forEach { index ->
            val color = pixels[index]
            val alpha = color ushr 24 and CHANNEL_MASK
            if (alpha < MINIMUM_ALPHA) return@forEach
            val red = color ushr 16 and CHANNEL_MASK
            val green = color ushr 8 and CHANNEL_MASK
            val blue = color and CHANNEL_MASK
            val key =
                ((red shr QUANTIZATION_SHIFT) shl 8) or
                    ((green shr QUANTIZATION_SHIFT) shl 4) or
                    (blue shr QUANTIZATION_SHIFT)
            val previous = bins[key]
            bins[key] =
                ColorBin(
                    count = (previous?.count ?: 0) + 1,
                    redTotal = (previous?.redTotal ?: 0L) + red,
                    greenTotal = (previous?.greenTotal ?: 0L) + green,
                    blueTotal = (previous?.blueTotal ?: 0L) + blue,
                )
        }
        return bins.values
            .sortedByDescending(ColorBin::count)
            .take(maximumColorCount)
            .map(ColorBin::averageArgb)
    }

    private data class ColorBin(
        val count: Int,
        val redTotal: Long,
        val greenTotal: Long,
        val blueTotal: Long,
    ) {
        fun averageArgb(): Int {
            val red = (redTotal / count).toInt()
            val green = (greenTotal / count).toInt()
            val blue = (blueTotal / count).toInt()
            return (CHANNEL_MASK shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }

    private const val DEFAULT_MAXIMUM_COLOR_COUNT = 3
    private const val MAXIMUM_SAMPLES = 4_096
    private const val MINIMUM_ALPHA = 128
    private const val CHANNEL_MASK = 0xFF
    private const val QUANTIZATION_SHIFT = 4
}
