package com.musicapp.player.data.metadata

import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataReaderTest {
    @Test
    fun `missing optional tags still returns readable metadata and file size`() = runTest {
        val reader = FakeEmbeddedMetadataReader(metadataBlock = {
            EmbeddedMetadataPayload(
                encoding = null,
                bitrateBps = null,
                sampleRateHz = null,
            )
        })
        val repository = metadataRepository(reader)

        val metadata = repository.read(track(sizeBytes = 8_192))

        assertTrue(metadata.isReadable)
        assertNull(metadata.encoding)
        assertNull(metadata.bitrateBps)
        assertNull(metadata.sampleRateHz)
        assertEquals(8_192, metadata.fileSizeBytes)
    }

    @Test
    fun `damaged source becomes unavailable without negative caching`() = runTest {
        var callCount = 0
        val repository = metadataRepository(
            FakeEmbeddedMetadataReader(metadataBlock = {
                callCount += 1
                if (callCount == 1) error("damaged") else readablePayload()
            }),
        )

        val unavailable = repository.read(track(sizeBytes = 4_096))
        val recovered = repository.read(track(sizeBytes = 4_096))

        assertFalse(unavailable.isReadable)
        assertNull(unavailable.encoding)
        assertEquals(4_096, unavailable.fileSizeBytes)
        assertTrue(recovered.isReadable)
        assertEquals(2, callCount)
    }

    @Test
    fun `reader concurrency never exceeds two`() = runTest {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val reader = FakeEmbeddedMetadataReader(metadataBlock = {
            val current = active.incrementAndGet()
            maximumActive.updateAndGet { previous -> maxOf(previous, current) }
            try {
                release.await()
                readablePayload()
            } finally {
                active.decrementAndGet()
            }
        })
        val repository = metadataRepository(reader)

        val reads = (1L..3L).map { id -> launch { repository.read(track(id = id)) } }
        while (active.get() < 2) yield()

        assertEquals(2, active.get())
        assertEquals(2, maximumActive.get())
        release.complete(Unit)
        reads.forEach { it.join() }
        assertEquals(2, maximumActive.get())
    }

    @Test
    fun `cancellation propagates and does not cache an unreadable result`() = runTest {
        val started = CompletableDeferred<Unit>()
        var callCount = 0
        val reader = FakeEmbeddedMetadataReader(metadataBlock = {
            callCount += 1
            if (callCount == 1) {
                started.complete(Unit)
                awaitCancellation()
            }
            readablePayload()
        })
        val repository = metadataRepository(reader)
        val requestedTrack = track()

        val firstRead = launch { repository.read(requestedTrack) }
        started.await()
        firstRead.cancelAndJoin()
        val metadata = repository.read(requestedTrack)

        assertTrue(firstRead.isCancelled)
        assertTrue(metadata.isReadable)
        assertEquals(2, callCount)
    }

    @Test
    fun `readable payload exposes encoding bitrate and sample rate`() = runTest {
        val repository = metadataRepository(FakeEmbeddedMetadataReader(metadataBlock = { readablePayload() }))

        val metadata = repository.read(track())

        assertEquals("audio/flac", metadata.encoding)
        assertEquals(1_000_000L, metadata.bitrateBps)
        assertEquals(96_000, metadata.sampleRateHz)
    }

    private fun metadataRepository(reader: EmbeddedMetadataReader): CachedTrackMetadataRepository =
        CachedTrackMetadataRepository(
            MetadataReadCoordinator(reader, InMemoryMetadataPayloadCache(), MetadataReadLimiter()),
        )
}

internal class FakeEmbeddedMetadataReader(
    private val metadataBlock: suspend (TrackId) -> EmbeddedMetadataPayload = { readablePayload() },
    private val artworkBlock: suspend (TrackId, Int) -> PlatformArtworkResult = { _, _ ->
        PlatformArtworkResult.Missing
    },
) : EmbeddedMetadataReader {
    override suspend fun readMetadata(trackId: TrackId): EmbeddedMetadataPayload = metadataBlock(trackId)

    override suspend fun readArtwork(trackId: TrackId, targetPx: Int): PlatformArtworkResult =
        artworkBlock(trackId, targetPx)
}

internal fun readablePayload(): EmbeddedMetadataPayload =
    EmbeddedMetadataPayload(
        encoding = "audio/flac",
        bitrateBps = 1_000_000,
        sampleRateHz = 96_000,
    )

internal fun track(
    id: Long = 41,
    dateModifiedMs: Long = 2_000,
    sizeBytes: Long = 1_024,
): Track =
    Track(
        id = TrackId("external_primary", id),
        title = "Track $id",
        artistName = "Artist",
        durationMs = 60_000,
        dateAddedMs = 1_000,
        dateModifiedMs = dateModifiedMs,
        relativePath = "Music/",
        displayName = "track-$id.flac",
        mimeType = "audio/flac",
        sizeBytes = sizeBytes,
    )
