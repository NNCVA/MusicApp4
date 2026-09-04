package com.musicapp.player.core.metadata

import com.musicapp.player.core.domain.model.TrackId

data class MetadataCacheKey(
    val trackId: TrackId,
    val dateModifiedMs: Long,
) {
    init {
        require(dateModifiedMs >= 0) { "dateModifiedMs must not be negative" }
    }
}

data class AdvancedTrackMetadata(
    val encoding: String?,
    val bitrateBps: Long?,
    val sampleRateHz: Int?,
    val fileSizeBytes: Long,
    val isReadable: Boolean,
    val bitDepth: Int? = null,
) {
    init {
        require(encoding == null || encoding.isNotBlank()) { "encoding must be null or non-blank" }
        require(bitrateBps == null || bitrateBps > 0) { "bitrateBps must be null or positive" }
        require(sampleRateHz == null || sampleRateHz > 0) { "sampleRateHz must be null or positive" }
        require(fileSizeBytes >= 0) { "fileSizeBytes must not be negative" }
        require(bitDepth == null || bitDepth > 0) { "bitDepth must be null or positive" }
    }
}

sealed interface ArtworkResult {
    data class Embedded(val image: ArtworkImage) : ArtworkResult

    data object Placeholder : ArtworkResult
}

class ArtworkImage(
    val width: Int,
    val height: Int,
    argbPixels: IntArray,
) {
    private val pixels: IntArray = argbPixels.copyOf()

    val argbPixels: IntArray
        get() = pixels.copyOf()

    init {
        require(width > 0 && height > 0) { "artwork dimensions must be positive" }
        require(width.toLong() * height == pixels.size.toLong()) {
            "pixel count must match artwork dimensions"
        }
    }
}
