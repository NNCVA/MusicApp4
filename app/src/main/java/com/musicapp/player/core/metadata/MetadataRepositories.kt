package com.musicapp.player.core.metadata

import com.musicapp.player.core.domain.model.Track

interface TrackMetadataRepository {
    suspend fun read(track: Track): AdvancedTrackMetadata
}

interface ArtworkRepository {
    suspend fun artwork(track: Track, targetPx: Int): ArtworkResult
}
