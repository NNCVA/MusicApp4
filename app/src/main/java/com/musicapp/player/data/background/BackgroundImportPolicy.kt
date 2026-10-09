package com.musicapp.player.data.background

import java.io.IOException

internal object BackgroundImportPolicy {
    const val MAX_INPUT_BYTES = 100L * 1024 * 1024
    const val MAX_STORED_BYTES = 32L * 1024 * 1024
    const val MAX_LONG_EDGE = 2560
    private const val MAX_SOURCE_DIMENSION = 65_536
    private const val MAX_SOURCE_PIXELS = 128_000_000L

    fun sampleSize(width: Int, height: Int, targetLongEdge: Int = MAX_LONG_EDGE): Int {
        if (
            width <= 0 || height <= 0 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION ||
            width.toLong() * height > MAX_SOURCE_PIXELS
        ) {
            throw IOException("Unsupported image dimensions")
        }
        require(targetLongEdge > 0)
        val longEdge = maxOf(width, height)
        var sampleSize = 1
        // BitmapFactory rounds down non-power-of-two samples, so use only exact powers of two.
        while ((longEdge.toLong() + sampleSize - 1) / sampleSize > targetLongEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun checkInputSize(size: Long) {
        if (size > MAX_INPUT_BYTES) throw IOException("Background image is too large")
    }
}
