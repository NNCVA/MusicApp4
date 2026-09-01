package com.musicapp.player.core.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioArtworkFetcherTest {

    private lateinit var context: Context
    private lateinit var options: Options
    private lateinit var imageLoader: ImageLoader
    private lateinit var uriResolver: TrackContentUriResolver
    private lateinit var limiter: ArtworkReadLimiter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        options = Options(context = context)
        imageLoader = ImageLoader.Builder(context).build()
        uriResolver = DefaultTrackContentUriResolver()
        limiter = ArtworkReadLimiter()
    }

    private fun createSampleTrack(
        volume: String = "external",
        id: Long = 101L,
        title: String = "Test Song",
        artist: String = "Test Artist",
        modified: Long = 1_000_000L,
    ) = Track(
        id = TrackId(volume, id),
        title = title,
        artistName = artist,
        durationMs = 200_000L,
        dateAddedMs = modified - 1000L,
        dateModifiedMs = modified,
        relativePath = "Music/$title.mp3",
        displayName = "$title.mp3",
    )

    @Test
    fun factory_createsFetcherForAudioArtworkRequest() {
        val factory = AudioArtworkFetcher.Factory(context, uriResolver, limiter)
        val request = AudioArtworkRequest.TrackArtworkRequest(
            trackId = TrackId("external", 101L),
            dateModifiedMs = 1000L,
        )

        val fetcher = factory.create(request, options, imageLoader)

        assertNotNull(fetcher)
        assertTrue(fetcher is AudioArtworkFetcher)
    }

    @Test
    fun factory_createsFetcherForTrack() {
        val factory = TrackArtworkFetcherFactory(context, uriResolver, limiter)
        val track = createSampleTrack(id = 102L)

        val fetcher = factory.create(track, options, imageLoader)

        assertNotNull(fetcher)
        assertTrue(fetcher is AudioArtworkFetcher)
    }

    @Test
    fun factory_returnsNullForUnsupportedTypes_viaCreateFromAny() {
        val factory = AudioArtworkFetcher.Factory(context, uriResolver, limiter)

        assertNull(factory.createFromAny("unsupported_string", options, imageLoader))
        assertNull(factory.createFromAny(12345, options, imageLoader))
    }

    @Test
    fun fetch_returnsValidSourceResult_whenEmbeddedArtworkExists() = runTest {
        val validJpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x01, 0x02)
        val fakeExtractor = ArtworkExtractor { _, _ -> validJpegBytes }

        val fetcher = AudioArtworkFetcher(
            data = createSampleTrack(id = 201L),
            options = options,
            extractor = fakeExtractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()

        assertNotNull(result)
        assertTrue(result is SourceFetchResult)
        val sourceResult = result as SourceFetchResult
        assertEquals(DataSource.DISK, sourceResult.dataSource)
        val extractedBytes = sourceResult.source.source().readByteArray()
        assertArrayEquals(validJpegBytes, extractedBytes)
    }

    @Test
    fun fetch_returnsNull_whenEmbeddedArtworkIsMissing() = runTest {
        val fakeExtractor = ArtworkExtractor { _, _ -> null }

        val fetcher = AudioArtworkFetcher(
            data = createSampleTrack(id = 202L),
            options = options,
            extractor = fakeExtractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()

        assertNull(result)
    }

    @Test
    fun fetch_returnsNull_whenEmbeddedArtworkIsEmpty() = runTest {
        val fakeExtractor = ArtworkExtractor { _, _ -> ByteArray(0) }

        val fetcher = AudioArtworkFetcher(
            data = createSampleTrack(id = 203L),
            options = options,
            extractor = fakeExtractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()

        assertNull(result)
    }

    @Test
    fun fetch_returnsNull_whenEmbeddedArtworkExceedsMaximumSizeThreshold() = runTest {
        val oversizedBytes = ByteArray(17 * 1024 * 1024)
        val fakeExtractor = ArtworkExtractor { _, _ -> oversizedBytes }

        val fetcher = AudioArtworkFetcher(
            data = createSampleTrack(id = 204L),
            options = options,
            extractor = fakeExtractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()

        assertNull(result)
    }

    @Test
    fun fetch_handlesSecurityExceptionAndIoExceptionGracefully() = runTest {
        val fakeExtractor = ArtworkExtractor { _, _ ->
            throw SecurityException("Permission Denial")
        }

        val fetcher = AudioArtworkFetcher(
            data = createSampleTrack(id = 205L),
            options = options,
            extractor = fakeExtractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()

        assertNull(result)
    }

    @Test
    fun fetch_returnsNull_whenTrackIdIsNullInAlbumArtworkRequest() = runTest {
        val fakeExtractor = ArtworkExtractor { _, _ -> byteArrayOf(1, 2, 3) }
        val albumRequestNoRep = AudioArtworkRequest.AlbumArtworkRequest(
            albumId = AlbumId("external", 999L),
            representativeTrackId = null,
            dateModifiedMs = 1000L,
        )

        val fetcher = AudioArtworkFetcher(
            data = albumRequestNoRep,
            options = options,
            extractor = fakeExtractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()

        assertNull(result)
    }

    @Test
    fun fetch_rethrowsCancellationException_andReleasesResources() = runTest {
        val started = CompletableDeferred<Unit>()
        var resourceReleased = false
        val fakeExtractor = ArtworkExtractor { _, _ ->
            started.complete(Unit)
            try {
                delay(5_000L)
                byteArrayOf(1, 2, 3)
            } finally {
                resourceReleased = true
            }
        }

        val fetcher = AudioArtworkFetcher(
            data = createSampleTrack(id = 206L),
            options = options,
            extractor = fakeExtractor,
            dispatcher = Dispatchers.IO,
        )

        val job = launch(Dispatchers.IO) {
            fetcher.fetch()
        }
        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(resourceReleased)
    }

    @Test
    fun fetch_throttlesConcurrency_underHighLoad() = runTest {
        val maxConcurrency = ArtworkReadLimiter.MAX_CONCURRENT_READS
        val sharedLimiter = ArtworkReadLimiter()
        var activeWorkers = 0
        var peakWorkers = 0
        val mutex = Mutex()

        val jobs = (1..10).map {
            launch(Dispatchers.IO) {
                sharedLimiter.withPermit {
                    mutex.withLock {
                        activeWorkers++
                        peakWorkers = maxOf(peakWorkers, activeWorkers)
                    }
                    delay(20)
                    mutex.withLock { activeWorkers-- }
                }
            }
        }
        jobs.joinAll()

        assertTrue("Peak workers ($peakWorkers) must not exceed max allowed ($maxConcurrency)", peakWorkers <= maxConcurrency)
    }
}
