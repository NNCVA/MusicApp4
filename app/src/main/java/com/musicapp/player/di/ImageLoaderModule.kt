package com.musicapp.player.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.crossfade
import com.musicapp.player.core.image.AudioArtworkFetcher
import com.musicapp.player.core.image.AudioArtworkKeyer
import com.musicapp.player.core.image.DefaultTrackContentUriResolver
import com.musicapp.player.core.image.TrackArtworkFetcherFactory
import com.musicapp.player.core.image.TrackArtworkKeyer
import com.musicapp.player.core.image.TrackContentUriResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okio.Path.Companion.toOkioPath
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageBindingModule {
    @Binds
    @Singleton
    abstract fun bindTrackContentUriResolver(
        implementation: DefaultTrackContentUriResolver
    ): TrackContentUriResolver
}

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        audioArtworkFetcherFactory: AudioArtworkFetcher.Factory,
        audioArtworkKeyer: AudioArtworkKeyer,
        trackArtworkFetcherFactory: TrackArtworkFetcherFactory,
        trackArtworkKeyer: TrackArtworkKeyer,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(audioArtworkKeyer)
                add(audioArtworkFetcherFactory)
                add(trackArtworkKeyer)
                add(trackArtworkFetcherFactory)
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("artwork_cache").toOkioPath())
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .allowHardware(true)
            .build()
    }
}
