package com.musicapp.player.data.metadata

import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.core.metadata.ArtworkResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ArtworkRepositoryTest {
    @Test
    fun `cache hit and same key concurrent requests share one read`() = runTest {
        var callCount = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = artworkRepository(
            FakeEmbeddedMetadataReader(artworkBlock = { _, _ ->
                callCount += 1
                started.complete(Unit)
                release.await()
                presentArtwork(4)
            }),
        )
        val requestedTrack = track()
        val first = launch { repository.artwork(requestedTrack, 128) }
        started.await()
        val second = launch { repository.artwork(requestedTrack, 128) }
        yield()

        assertEquals(1, callCount)
        release.complete(Unit)
        first.join()
        second.join()
        val cached = repository.artwork(requestedTrack, 128) as ArtworkResult.Embedded
        assertArrayEquals(intArrayOf(4, 4, 4, 4), cached.image.argbPixels)
        assertEquals(1, callCount)
    }

    @Test
    fun `same track id with changed modification time gets a new cache entry`() = runTest {
        var callCount = 0
        val repository = artworkRepository(
            FakeEmbeddedMetadataReader(artworkBlock = { _, _ ->
                callCount += 1
                presentArtwork(callCount)
            }),
        )

        val original = repository.artwork(track(dateModifiedMs = 2_000), 128) as ArtworkResult.Embedded
        val modified = repository.artwork(track(dateModifiedMs = 3_000), 128) as ArtworkResult.Embedded

        assertArrayEquals(intArrayOf(1, 1, 1, 1), original.image.argbPixels)
        assertArrayEquals(intArrayOf(2, 2, 2, 2), modified.image.argbPixels)
        assertEquals(2, callCount)
    }

    @Test
    fun `target size is part of artwork cache key`() = runTest {
        var callCount = 0
        val repository = artworkRepository(
            FakeEmbeddedMetadataReader(artworkBlock = { _, targetPx ->
                callCount += 1
                presentArtwork(targetPx)
            }),
        )

        repository.artwork(track(), 128)
        repository.artwork(track(), 256)
        repository.artwork(track(), 128)

        assertEquals(2, callCount)
    }

    @Test
    fun `missing embedded artwork returns placeholder and is negatively cached`() = runTest {
        var callCount = 0
        val repository = artworkRepository(
            FakeEmbeddedMetadataReader(artworkBlock = { _, _ ->
                callCount += 1
                PlatformArtworkResult.Missing
            }),
        )
        val requestedTrack = track()

        assertSame(ArtworkResult.Placeholder, repository.artwork(requestedTrack, 128))
        assertSame(ArtworkResult.Placeholder, repository.artwork(requestedTrack, 128))
        assertEquals(1, callCount)
    }

    @Test
    fun `transient damaged read returns placeholder but is not negatively cached`() = runTest {
        var callCount = 0
        val repository = artworkRepository(
            FakeEmbeddedMetadataReader(artworkBlock = { _, _ ->
                callCount += 1
                if (callCount == 1) error("temporarily unreadable") else presentArtwork(9)
            }),
        )
        val requestedTrack = track()

        assertSame(ArtworkResult.Placeholder, repository.artwork(requestedTrack, 128))
        val recovered = repository.artwork(requestedTrack, 128) as ArtworkResult.Embedded

        assertArrayEquals(intArrayOf(9, 9, 9, 9), recovered.image.argbPixels)
        assertEquals(2, callCount)
    }

    private fun artworkRepository(reader: EmbeddedMetadataReader): CachedArtworkRepository =
        CachedArtworkRepository(
            MetadataReadCoordinator(reader, InMemoryMetadataPayloadCache(), MetadataReadLimiter()),
        )

    private fun presentArtwork(pixel: Int): PlatformArtworkResult.Present =
        PlatformArtworkResult.Present(
            ArtworkImage(width = 2, height = 2, argbPixels = IntArray(4) { pixel }),
        )
}
