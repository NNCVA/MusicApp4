package com.musicapp.player.data.metadata

import com.musicapp.player.core.metadata.ArtworkRepository
import com.musicapp.player.core.metadata.TrackMetadataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MetadataModule {
    @Binds
    @Singleton
    abstract fun bindEmbeddedMetadataReader(implementation: AndroidEmbeddedMetadataReader): EmbeddedMetadataReader

    @Binds
    @Singleton
    abstract fun bindTrackContentUriResolver(implementation: AndroidTrackContentUriResolver): TrackContentUriResolver

    @Binds
    @Singleton
    abstract fun bindMetadataPayloadCache(implementation: InMemoryMetadataPayloadCache): MetadataPayloadCache

    @Binds
    @Singleton
    abstract fun bindTrackMetadataRepository(implementation: CachedTrackMetadataRepository): TrackMetadataRepository

    @Binds
    @Singleton
    abstract fun bindArtworkRepository(implementation: CachedArtworkRepository): ArtworkRepository
}
