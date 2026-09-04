package com.musicapp.player.data.metadata

import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.ArtworkResult
import com.musicapp.player.core.metadata.MetadataCacheKey
import com.musicapp.player.core.metadata.TrackMetadataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CachedTrackMetadataRepository @Inject constructor(
    private val coordinator: MetadataReadCoordinator,
) : TrackMetadataRepository {
    override suspend fun read(track: Track): AdvancedTrackMetadata =
        when (val result = coordinator.readMetadata(track.cacheKey())) {
            is MetadataLoadResult.Readable ->
                AdvancedTrackMetadata(
                    encoding = result.value.encoding,
                    bitrateBps = result.value.bitrateBps,
                    sampleRateHz = result.value.sampleRateHz,
                    fileSizeBytes = track.sizeBytes,
                    isReadable = true,
                    bitDepth = result.value.bitDepth,
                )
            MetadataLoadResult.Unavailable ->
                AdvancedTrackMetadata(
                    encoding = null,
                    bitrateBps = null,
                    sampleRateHz = null,
                    fileSizeBytes = track.sizeBytes,
                    isReadable = false,
                )
        }
}

@Singleton
internal class CachedArtworkRepository @Inject constructor(
    private val coordinator: MetadataReadCoordinator,
) : ArtworkRepository {
    override suspend fun artwork(track: Track, targetPx: Int): ArtworkResult {
        val normalizedTarget = targetPx.coerceIn(MIN_TARGET_PX, MAX_TARGET_PX)
        return when (
            val result = coordinator.readArtwork(ArtworkCacheKey(track.cacheKey(), normalizedTarget))
        ) {
            is ArtworkLoadResult.Present -> ArtworkResult.Embedded(result.image)
            ArtworkLoadResult.Missing,
            ArtworkLoadResult.Unavailable,
            -> ArtworkResult.Placeholder
        }
    }

    private companion object {
        const val MIN_TARGET_PX = 32
        const val MAX_TARGET_PX = 2_048
    }
}

private fun Track.cacheKey(): MetadataCacheKey = MetadataCacheKey(id, dateModifiedMs)
