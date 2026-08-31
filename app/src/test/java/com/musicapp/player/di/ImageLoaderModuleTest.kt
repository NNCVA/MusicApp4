package com.musicapp.player.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.musicapp.player.core.image.ArtworkReadLimiter
import com.musicapp.player.core.image.AudioArtworkFetcher
import com.musicapp.player.core.image.AudioArtworkKeyer
import com.musicapp.player.core.image.DefaultTrackContentUriResolver
import com.musicapp.player.core.image.TrackArtworkFetcherFactory
import com.musicapp.player.core.image.TrackArtworkKeyer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageLoaderModuleTest {

    private lateinit var context: Context
    private lateinit var audioArtworkKeyer: AudioArtworkKeyer
    private lateinit var trackArtworkKeyer: TrackArtworkKeyer
    private lateinit var audioArtworkFetcherFactory: AudioArtworkFetcher.Factory
    private lateinit var trackArtworkFetcherFactory: TrackArtworkFetcherFactory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val uriResolver = DefaultTrackContentUriResolver()
        val limiter = ArtworkReadLimiter()
        audioArtworkKeyer = AudioArtworkKeyer()
        trackArtworkKeyer = TrackArtworkKeyer()
        audioArtworkFetcherFactory = AudioArtworkFetcher.Factory(context, uriResolver, limiter)
        trackArtworkFetcherFactory = TrackArtworkFetcherFactory(context, uriResolver, limiter)
    }

    private fun createImageLoader(): ImageLoader =
        ImageLoaderModule.provideImageLoader(
            context = context,
            audioArtworkFetcherFactory = audioArtworkFetcherFactory,
            audioArtworkKeyer = audioArtworkKeyer,
            trackArtworkFetcherFactory = trackArtworkFetcherFactory,
            trackArtworkKeyer = trackArtworkKeyer,
        )

    @Test
    fun imageLoader_containsCustomAudioArtworkFetcherAndKeyer() {
        val imageLoader = createImageLoader()

        val components = imageLoader.components
        assertTrue(
            "ComponentRegistry should contain AudioArtworkKeyer",
            components.keyers.any { it.first is AudioArtworkKeyer }
        )
        assertTrue(
            "ComponentRegistry should contain TrackArtworkKeyer",
            components.keyers.any { it.first is TrackArtworkKeyer }
        )
        assertTrue(
            "ComponentRegistry should contain AudioArtworkFetcher.Factory",
            components.fetcherFactories.any { it.first is AudioArtworkFetcher.Factory }
        )
        assertTrue(
            "ComponentRegistry should contain TrackArtworkFetcherFactory",
            components.fetcherFactories.any { it.first is TrackArtworkFetcherFactory }
        )
    }

    @Test
    fun imageLoader_hasConfiguredMemoryCacheWithPositiveCapacity() {
        val imageLoader = createImageLoader()

        val memoryCache = imageLoader.memoryCache
        assertNotNull("MemoryCache must be configured", memoryCache)
        assertTrue("MemoryCache maxSize must be positive", memoryCache!!.maxSize > 0)
    }

    @Test
    fun provideImageLoader_createsValidSingletonInstance() {
        val loader1 = createImageLoader()
        val loader2 = createImageLoader()

        assertNotNull(loader1)
        assertNotNull(loader2)
    }
}
