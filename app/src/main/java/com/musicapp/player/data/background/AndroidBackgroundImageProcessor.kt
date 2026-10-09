package com.musicapp.player.data.background

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun interface BackgroundImageProcessor {
    suspend fun process(source: File, destination: File)
}

internal class AndroidBackgroundImageProcessor : BackgroundImageProcessor {
    override suspend fun process(source: File, destination: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = BackgroundImportPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        currentCoroutineContext().ensureActive()
        // BitmapFactory decodes a static frame even for an animated source.
        var bitmap = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: throw IOException("Unable to decode background image")
        try {
            currentCoroutineContext().ensureActive()
            val orientation = readOrientation(source)
            if (orientation.isFlipped || orientation.rotationDegrees != 0) {
                val matrix = Matrix().apply {
                    if (orientation.isFlipped) setScale(-1f, 1f)
                    postRotate(orientation.rotationDegrees.toFloat())
                }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            val longEdge = maxOf(bitmap.width, bitmap.height)
            if (longEdge > BackgroundImportPolicy.MAX_LONG_EDGE) {
                val scale = BackgroundImportPolicy.MAX_LONG_EDGE.toDouble() / longEdge
                val scaled = bitmap.scale(
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            currentCoroutineContext().ensureActive()
            FileOutputStream(destination).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Unable to encode background image")
                }
                output.fd.sync()
            }
            if (destination.length() !in 1..BackgroundImportPolicy.MAX_STORED_BYTES) {
                throw IOException("Invalid processed background image")
            }
            currentCoroutineContext().ensureActive()
        } finally {
            bitmap.recycle()
        }
    }

    private fun readOrientation(source: File): Orientation = try {
        val exif = ExifInterface(source)
        Orientation(exif.isFlipped, exif.rotationDegrees)
    } catch (_: IOException) {
        // A valid image format may not support EXIF. Decoding still determines image validity.
        Orientation()
    }

    private data class Orientation(val isFlipped: Boolean = false, val rotationDegrees: Int = 0)
}
