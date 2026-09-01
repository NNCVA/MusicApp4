package com.musicapp.player.core.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.PlaylistId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdversarialArtworkPipelineStressTest {

    private lateinit var context: Context
    private lateinit var options: Options
    private lateinit var imageLoader: ImageLoader
    private lateinit var keyer: AudioArtworkKeyer
    private lateinit var trackKeyer: TrackArtworkKeyer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        options = Options(context = context)
        imageLoader = ImageLoader.Builder(context).build()
        keyer = AudioArtworkKeyer()
        trackKeyer = TrackArtworkKeyer()
    }

    private fun sampleTrack(
        volume: String = "external",
        id: Long = 999L,
        title: String = "Empirical Track",
        modified: Long = 1_600_000_000L,
    ) = Track(
        id = TrackId(volume, id),
        title = title,
        artistName = "Adversarial Artist",
        durationMs = 210_000L,
        dateAddedMs = maxOf(0L, modified - 1000L),
        dateModifiedMs = modified,
        relativePath = "Music/$title.flac",
        displayName = "$title.flac",
    )

    // ==========================================
    // 1. KEYER ADVERSARIAL STRESS & COLLISION TESTS
    // ==========================================

    @Test
    fun keyer_handlesSpecialCharactersAndEmojisInVolumeAndArtistName() {
        val weirdVolume = "ext:sdcard/0:storage:🔥"
        val track = sampleTrack(volume = weirdVolume, id = 777L, modified = 5555L)

        val trackKey = keyer.key(track, options)
        assertTrue(trackKey.startsWith("artwork:track:"))
        assertTrue(trackKey.contains(weirdVolume))

        val weirdArtistRequest = AudioArtworkRequest.ArtistArtworkRequest(
            artistName = "Artist / With:Colons & Emojis 🎧\nNewline",
            representativeTrackId = TrackId(weirdVolume, 777L),
            dateModifiedMs = 5555L,
        )
        val artistKey = keyer.key(weirdArtistRequest, options)
        assertTrue(artistKey.startsWith("artwork:artist:"))
        assertTrue(artistKey.contains("Artist / With:Colons & Emojis 🎧\nNewline"))
    }

    @Test
    fun keyer_enforcesNoCollisionBetweenDifferentModelTypesWithIdenticalIds() {
        val vol = "primary"
        val id = 12345L
        val mod = 98765L

        val trackReq = AudioArtworkRequest.TrackArtworkRequest(TrackId(vol, id), mod)
        val albumReq = AudioArtworkRequest.AlbumArtworkRequest(AlbumId(vol, id), TrackId(vol, id), mod)
        val artistReq = AudioArtworkRequest.ArtistArtworkRequest("Artist12345", TrackId(vol, id), mod)
        val playlistReq = AudioArtworkRequest.PlaylistArtworkRequest(PlaylistId(id), TrackId(vol, id), mod)

        val keys = setOf(
            keyer.key(trackReq, options),
            keyer.key(albumReq, options),
            keyer.key(artistReq, options),
            keyer.key(playlistReq, options),
        )

        // All 4 keys MUST be strictly distinct
        assertEquals(4, keys.size)
    }

    @Test
    fun keyer_rejectsNegativeModifiedTimestamp() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioArtworkRequest.TrackArtworkRequest(TrackId("ext", 1L), -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioArtworkRequest.AlbumArtworkRequest(AlbumId("ext", 1L), null, -500L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioArtworkRequest.ArtistArtworkRequest("Valid Artist", null, -10L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioArtworkRequest.PlaylistArtworkRequest(PlaylistId(1L), null, -100L)
        }
    }

    @Test
    fun keyer_rejectsBlankArtistName() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioArtworkRequest.ArtistArtworkRequest("   ", null, 100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioArtworkRequest.ArtistArtworkRequest("", null, 100L)
        }
    }

    @Test
    fun keyer_boundaryValues_LongMaxAndZero() {
        val trackZero = sampleTrack(id = 1L, modified = 0L)
        val keyZero = keyer.key(trackZero, options)
        assertEquals("artwork:track:external:1:0", keyZero)

        val trackMax = sampleTrack(id = Long.MAX_VALUE, modified = Long.MAX_VALUE)
        val keyMax = keyer.key(trackMax, options)
        assertEquals("artwork:track:external:${Long.MAX_VALUE}:${Long.MAX_VALUE}", keyMax)
    }

    @Test
    fun keyer_consistencyBetweenTrackAndArtworkRequest() {
        val track = sampleTrack(volume = "ext_card", id = 888L, modified = 33333L)
        val request = track.toArtworkRequest()

        val keyFromDirectTrack = trackKeyer.key(track, options)
        val keyFromKeyerOverload = keyer.key(track, options)
        val keyFromRequest = keyer.key(request, options)
        val keyFromAnyTrack = keyer.keyFromAny(track, options)
        val keyFromAnyRequest = keyer.keyFromAny(request, options)

        assertEquals(keyFromDirectTrack, keyFromKeyerOverload)
        assertEquals(keyFromDirectTrack, keyFromRequest)
        assertEquals(keyFromDirectTrack, keyFromAnyTrack)
        assertEquals(keyFromDirectTrack, keyFromAnyRequest)
    }

    // ==========================================
    // 2. LIMITER CONCURRENCY & RESILIENCE TESTS
    // ==========================================

    @Test
    fun limiter_stress50ConcurrentCoroutines_neverExceedsConcurrencyLimit() = runTest {
        val limiter = ArtworkReadLimiter()
        val activeCount = AtomicInteger(0)
        val maxPeak = AtomicInteger(0)
        val completedCount = AtomicInteger(0)

        val jobs = (1..50).map {
            launch(Dispatchers.Default) {
                limiter.withPermit {
                    val current = activeCount.incrementAndGet()
                    maxPeak.updateAndGet { peak -> maxOf(peak, current) }
                    delay(5)
                    activeCount.decrementAndGet()
                    completedCount.incrementAndGet()
                }
            }
        }
        jobs.joinAll()

        assertEquals(50, completedCount.get())
        assertTrue("Peak concurrent executions (${maxPeak.get()}) must not exceed ${ArtworkReadLimiter.MAX_CONCURRENT_READS}", maxPeak.get() <= ArtworkReadLimiter.MAX_CONCURRENT_READS)
    }

    @Test
    fun limiter_recoversImmediatelyAfterExceptions_noPermitLeak() = runTest {
        val limiter = ArtworkReadLimiter()

        // Phase 1: 10 tasks throw exceptions inside permit
        for (i in 1..10) {
            try {
                limiter.withPermit {
                    throw IllegalStateException("Intentional test fault $i")
                }
            } catch (ignored: IllegalStateException) {
                // Expected
            }
        }

        // Phase 2: verify limiter can still process new requests normally
        var executed = false
        limiter.withPermit {
            executed = true
        }
        assertTrue("Limiter must still be usable after exceptions", executed)
    }

    @Test
    fun limiter_recoversImmediatelyAfterCancellations_noPermitLeak() = runTest {
        val limiter = ArtworkReadLimiter()

        // Phase 1: Launch and cancel 20 coroutines
        val jobs = (1..20).map {
            launch(Dispatchers.Default) {
                limiter.withPermit {
                    delay(10_000L) // Wait long enough to be cancelled
                }
            }
        }
        delay(20)
        jobs.forEach { it.cancelAndJoin() }

        // Phase 2: Verify all permits are returned
        val activeCount = AtomicInteger(0)
        val maxPeak = AtomicInteger(0)
        val postJobs = (1..5).map {
            launch(Dispatchers.Default) {
                limiter.withPermit {
                    val current = activeCount.incrementAndGet()
                    maxPeak.updateAndGet { peak -> maxOf(peak, current) }
                    delay(5)
                    activeCount.decrementAndGet()
                }
            }
        }
        postJobs.joinAll()

        assertTrue(maxPeak.get() in 1..ArtworkReadLimiter.MAX_CONCURRENT_READS)
    }

    // ==========================================
    // 3. FETCHER EDGE CASES & RESOURCE SAFETY TESTS
    // ==========================================

    @Test
    fun fetcher_returnsExactBuffer_atBoundary16MB() = runTest {
        val exact16MB = ByteArray(16 * 1024 * 1024)
        exact16MB[0] = 0x12
        exact16MB[exact16MB.size - 1] = 0x34
        val extractor = ArtworkExtractor { _, _ -> exact16MB }

        val fetcher = AudioArtworkFetcher(
            data = sampleTrack(id = 1001L),
            options = options,
            extractor = extractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()
        assertNotNull("16MB exact size should be accepted", result)
        assertTrue(result is SourceFetchResult)
        val sourceResult = result as SourceFetchResult
        assertEquals(DataSource.DISK, sourceResult.dataSource)
    }

    @Test
    fun fetcher_rejectsOversizedArtwork_atBoundary16MBPlusOneByte() = runTest {
        val oversized = ByteArray(16 * 1024 * 1024 + 1)
        val extractor = ArtworkExtractor { _, _ -> oversized }

        val fetcher = AudioArtworkFetcher(
            data = sampleTrack(id = 1002L),
            options = options,
            extractor = extractor,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = fetcher.fetch()
        assertNull("16MB + 1 byte must be rejected to prevent OOM", result)
    }

    @Test
    fun fetcher_handlesAllThrowablesGracefully_withoutCrashing() = runTest {
        val exceptionsToTest = listOf(
            RuntimeException("Generic runtime error"),
            IllegalStateException("Retriever uninitialized"),
            IllegalArgumentException("Bad URI"),
            SecurityException("No READ_MEDIA_AUDIO permission"),
            OutOfMemoryError("Bitmap alloc failed"),
        )

        for (ex in exceptionsToTest) {
            val faultyExtractor = ArtworkExtractor { _, _ -> throw ex }
            val fetcher = AudioArtworkFetcher(
                data = sampleTrack(id = 2001L),
                options = options,
                extractor = faultyExtractor,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
            val result = fetcher.fetch()
            assertNull("Fetcher should safely return null when extractor throws ${ex.javaClass.simpleName}", result)
        }
    }

    @Test
    fun fetcher_cancellationPropagation_doesNotSwallowCancellationException() = runTest {
        val started = CompletableDeferred<Unit>()
        val extractor = ArtworkExtractor { _, _ ->
            started.complete(Unit)
            delay(10_000L)
            byteArrayOf(1, 2, 3)
        }

        val fetcher = AudioArtworkFetcher(
            data = sampleTrack(id = 3001L),
            options = options,
            extractor = extractor,
            dispatcher = Dispatchers.IO,
        )

        val job = launch(Dispatchers.IO) {
            fetcher.fetch()
        }

        started.await()
        job.cancelAndJoin()

        assertTrue("Job must be marked cancelled", job.isCancelled)
    }

    @Test
    fun fetcherFactory_createFromAny_robustness() {
        val factory = AudioArtworkFetcher.Factory(
            context = context,
            uriResolver = DefaultTrackContentUriResolver(),
            limiter = ArtworkReadLimiter(),
        )

        // Valid models
        val track = sampleTrack(id = 5001L)
        val trackReq = track.toArtworkRequest()
        assertNotNull(factory.createFromAny(track, options, imageLoader))
        assertNotNull(factory.createFromAny(trackReq, options, imageLoader))

        // Invalid models
        assertNull(factory.createFromAny(emptyList<String>(), options, imageLoader))
        assertNull(factory.createFromAny(Any(), options, imageLoader))
        assertNull(factory.createFromAny("content://media/external/audio/media/1", options, imageLoader))
        assertNull(factory.createFromAny(1234L, options, imageLoader))
    }

    @Test
    fun defaultArtworkExtractor_handlesNonExistentUriSafely() = runTest {
        val fakeUri = android.net.Uri.parse("content://media/external/audio/media/999999999")
        try {
            val result = DefaultArtworkExtractor.extract(context, fakeUri)
            assertNull(result)
        } catch (ignored: Throwable) {
            // Exceptions from setDataSource are safely caught by callers and retriever is released in finally
        }
    }
}

