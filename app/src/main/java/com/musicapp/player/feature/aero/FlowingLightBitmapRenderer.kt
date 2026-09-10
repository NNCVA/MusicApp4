package com.musicapp.player.feature.aero

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.musicapp.player.core.domain.model.AeroMode
import com.musicapp.player.core.metadata.ArtworkImage
import kotlin.math.max

internal class FlowingLightBitmapRenderer {
    private val canvas = Canvas()
    private val artworkPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrices = Array(3) { Matrix() }
    private val outputBuffers = arrayOfNulls<Bitmap>(2)
    private var meshSource: Bitmap? = null
    private val scaledMeshVertices = FloatArray(FlowingLightRenderPolicy.M1_VERTICES.size)
    private var pixels = IntArray(0)
    private var scratchPixels = IntArray(0)
    private var currentBufferIndex = 1
    private var bufferSpec: FlowingLightBufferSpec? = null
    private var sourceArtwork: ArtworkImage? = null
    private var sourceBitmap: Bitmap? = null
    private var sourceClearColor: Int = 0

    init {
        artworkPaint.colorFilter = ColorMatrixColorFilter(
            ColorMatrix().apply { setSaturation(FlowingLightRenderPolicy.SATURATION) },
        )
    }

    @Synchronized
    fun render(
        artwork: ArtworkImage,
        screenWidth: Int,
        screenHeight: Int,
        densityDpi: Int,
        darkTheme: Boolean,
        mode: AeroMode,
        elapsedRealtimeMs: Long,
    ): Bitmap {
        require(mode == AeroMode.FLUID_MESH || mode == AeroMode.GLOW_AURA)
        val spec = FlowingLightRenderPolicy.bufferSpec(screenWidth, screenHeight, densityDpi)
        ensureArtwork(artwork)
        ensureBuffers(spec)

        val source = checkNotNull(sourceBitmap)
        currentBufferIndex = 1 - currentBufferIndex
        val output = checkNotNull(outputBuffers[currentBufferIndex])
        canvas.setBitmap(output)
        output.eraseColor(sourceClearColor)
        drawArtworkLayers(source, spec, elapsedRealtimeMs)
        if (mode == AeroMode.FLUID_MESH) applyMesh(output)

        val overlays = if (darkTheme) FlowingLightRenderPolicy.DARK_OVERLAYS else FlowingLightRenderPolicy.LIGHT_OVERLAYS
        overlays.forEach { color ->
            overlayPaint.color = color
            canvas.drawPaint(overlayPaint)
        }
        output.getPixels(pixels, 0, spec.width, 0, 0, spec.width, spec.height)
        FlowingLightRenderPolicy.blurThreePasses(pixels, scratchPixels, spec.width, spec.height)
        output.setPixels(pixels, 0, spec.width, 0, 0, spec.width, spec.height)
        canvas.setBitmap(null)
        return output
    }

    @Synchronized
    fun close() {
        canvas.setBitmap(null)
        sourceBitmap?.recycle()
        sourceBitmap = null
        sourceArtwork = null
        sourceClearColor = 0
        outputBuffers.forEachIndexed { index, bitmap ->
            bitmap?.recycle()
            outputBuffers[index] = null
        }
        meshSource?.recycle()
        meshSource = null
        bufferSpec = null
        pixels = IntArray(0)
        scratchPixels = IntArray(0)
    }

    private fun ensureArtwork(artwork: ArtworkImage) {
        if (sourceArtwork === artwork) return
        sourceBitmap?.recycle()
        val artworkPixels = artwork.argbPixels
        sourceBitmap = Bitmap.createBitmap(artworkPixels, artwork.width, artwork.height, Bitmap.Config.ARGB_8888)
        sourceClearColor = FlowingLightRenderPolicy.centerSampleArgb(artworkPixels, artwork.width, artwork.height)
        sourceArtwork = artwork
    }

    private fun ensureBuffers(spec: FlowingLightBufferSpec) {
        if (bufferSpec == spec) return
        outputBuffers.forEachIndexed { index, bitmap ->
            bitmap?.recycle()
            outputBuffers[index] = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        }
        meshSource?.recycle()
        meshSource = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        pixels = IntArray(spec.width * spec.height)
        scratchPixels = IntArray(pixels.size)
        FlowingLightRenderPolicy.M1_VERTICES.forEachIndexed { index, coordinate ->
            scaledMeshVertices[index] = coordinate * if (index % 2 == 0) spec.width else spec.height
        }
        currentBufferIndex = 1
        bufferSpec = spec
    }

    private fun drawArtworkLayers(source: Bitmap, spec: FlowingLightBufferSpec, elapsedRealtimeMs: Long) {
        val side = max(spec.width, spec.height) * FlowingLightRenderPolicy.ARTWORK_COVERAGE
        val scale = max(side / source.width, side / source.height)
        val centerOffsetX = -(source.width * scale - spec.width) / 2f
        val centerOffsetY = -(source.height * scale - spec.height) / 2f

        for (layer in 0..2) {
            val matrix = matrices[layer]
            matrix.reset()
            matrix.setScale(scale, scale)
            val angle = FlowingLightRenderPolicy.angleDegrees(elapsedRealtimeMs, layer)
            matrix.postRotate(angle, source.width * scale / 2f, source.height * scale / 2f)
            matrix.postTranslate(centerOffsetX, centerOffsetY)
            when (layer) {
                1 -> matrix.postTranslate(
                    spec.width * FlowingLightRenderPolicy.SECONDARY_TRANSLATION_X,
                    spec.height * FlowingLightRenderPolicy.SECONDARY_TRANSLATION_Y,
                )
                2 -> {
                    matrix.postTranslate(
                        spec.width * FlowingLightRenderPolicy.TERTIARY_TRANSLATION_X,
                        spec.height * FlowingLightRenderPolicy.TERTIARY_TRANSLATION_Y,
                    )
                    matrix.postRotate(angle, spec.width / 2f, spec.height / 2f)
                }
            }
            canvas.drawBitmap(source, matrix, artworkPaint)
        }
    }

    private fun applyMesh(output: Bitmap) {
        val source = checkNotNull(meshSource)
        val meshCanvas = Canvas(source)
        source.eraseColor(0)
        meshCanvas.drawBitmap(output, 0f, 0f, null)
        output.eraseColor(0)
        canvas.drawBitmapMesh(source, 5, 5, scaledMeshVertices, 0, null, 0, null)
    }
}
