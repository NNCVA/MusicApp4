package com.musicapp.player.data.metadata

import com.musicapp.player.core.metadata.ArtworkImage
import com.musicapp.player.core.metadata.MetadataCacheKey
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class ArtworkCacheKey(
    val metadataKey: MetadataCacheKey,
    val targetPx: Int,
)

internal sealed interface MetadataLoadResult {
    data class Readable(val value: EmbeddedMetadataPayload) : MetadataLoadResult

    data object Unavailable : MetadataLoadResult
}

internal sealed interface ArtworkLoadResult {
    data class Present(val image: ArtworkImage) : ArtworkLoadResult

    data object Missing : ArtworkLoadResult

    data object Unavailable : ArtworkLoadResult
}

@Singleton
class MetadataReadLimiter @Inject constructor() {
    private val permits = Semaphore(permits = MAX_CONCURRENT_READS)

    suspend fun <T> run(block: suspend () -> T): T = permits.withPermit { block() }

    private companion object {
        const val MAX_CONCURRENT_READS = 2
    }
}

internal interface MetadataPayloadCache {
    suspend fun getMetadata(key: MetadataCacheKey): EmbeddedMetadataPayload?

    suspend fun putMetadata(key: MetadataCacheKey, value: EmbeddedMetadataPayload)

    suspend fun getArtwork(key: ArtworkCacheKey): ArtworkLoadResult?

    suspend fun putArtwork(key: ArtworkCacheKey, value: ArtworkLoadResult)
}

@Singleton
internal class InMemoryMetadataPayloadCache @Inject constructor() : MetadataPayloadCache {
    private val mutex = Mutex()
    private val metadataEntries = LinkedHashMap<MetadataCacheKey, EmbeddedMetadataPayload>(0, 0.75f, true)
    private val artworkEntries = LinkedHashMap<ArtworkCacheKey, ArtworkLoadResult>(0, 0.75f, true)

    override suspend fun getMetadata(key: MetadataCacheKey): EmbeddedMetadataPayload? =
        mutex.withLock { metadataEntries[key] }

    override suspend fun putMetadata(key: MetadataCacheKey, value: EmbeddedMetadataPayload) {
        mutex.withLock { metadataEntries.putBounded(key, value, MAX_METADATA_ENTRIES) }
    }

    override suspend fun getArtwork(key: ArtworkCacheKey): ArtworkLoadResult? =
        mutex.withLock { artworkEntries[key] }

    override suspend fun putArtwork(key: ArtworkCacheKey, value: ArtworkLoadResult) {
        require(value != ArtworkLoadResult.Unavailable) { "transient artwork failures must not be cached" }
        mutex.withLock {
            artworkEntries[key] = value
            while (
                artworkEntries.size > MAX_ARTWORK_ENTRIES ||
                    artworkEntries.values.sumOf { it.pixelCount() } > MAX_CACHED_ARTWORK_PIXELS
            ) {
                artworkEntries.entries.iterator().run {
                    next()
                    remove()
                }
            }
        }
    }

    private fun <K, V> LinkedHashMap<K, V>.putBounded(key: K, value: V, maximum: Int) {
        put(key, value)
        while (size > maximum) {
            entries.iterator().run {
                next()
                remove()
            }
        }
    }

    private fun ArtworkLoadResult.pixelCount(): Long =
        when (this) {
            is ArtworkLoadResult.Present -> image.width.toLong() * image.height
            ArtworkLoadResult.Missing,
            ArtworkLoadResult.Unavailable,
            -> 0
        }

    private companion object {
        const val MAX_METADATA_ENTRIES = 128
        const val MAX_ARTWORK_ENTRIES = 32
        const val MAX_CACHED_ARTWORK_PIXELS = 8_388_608L
    }
}

@Singleton
internal class MetadataReadCoordinator @Inject constructor(
    private val reader: EmbeddedMetadataReader,
    private val cache: MetadataPayloadCache,
    private val limiter: MetadataReadLimiter,
) {
    private val inFlightMutex = Mutex()
    private val metadataInFlight = mutableMapOf<MetadataCacheKey, Deferred<MetadataLoadResult>>()
    private val artworkInFlight = mutableMapOf<ArtworkCacheKey, Deferred<ArtworkLoadResult>>()

    suspend fun readMetadata(key: MetadataCacheKey): MetadataLoadResult {
        cache.getMetadata(key)?.let { return MetadataLoadResult.Readable(it) }
        val proposed = CompletableDeferred<MetadataLoadResult>()
        val active = inFlightMutex.withLock { metadataInFlight.getOrPut(key) { proposed } }
        if (active !== proposed) return active.await()
        return try {
            cache.getMetadata(key)?.let { cached ->
                return MetadataLoadResult.Readable(cached).also(proposed::complete)
            }
            val payload = limiter.run { reader.readMetadata(key.trackId) }
            cache.putMetadata(key, payload)
            MetadataLoadResult.Readable(payload).also(proposed::complete)
        } catch (exception: CancellationException) {
            proposed.completeExceptionally(exception)
            throw exception
        } catch (_: Exception) {
            MetadataLoadResult.Unavailable.also(proposed::complete)
        } finally {
            withContext(NonCancellable) {
                inFlightMutex.withLock { metadataInFlight.remove(key, proposed) }
            }
        }
    }

    suspend fun readArtwork(key: ArtworkCacheKey): ArtworkLoadResult {
        cache.getArtwork(key)?.let { return it }
        val proposed = CompletableDeferred<ArtworkLoadResult>()
        val active = inFlightMutex.withLock { artworkInFlight.getOrPut(key) { proposed } }
        if (active !== proposed) return active.await()
        return try {
            cache.getArtwork(key)?.let { cached -> return cached.also(proposed::complete) }
            val result =
                when (val platformResult = limiter.run {
                    reader.readArtwork(key.metadataKey.trackId, key.targetPx)
                }) {
                    is PlatformArtworkResult.Present -> ArtworkLoadResult.Present(platformResult.image)
                    PlatformArtworkResult.Missing -> ArtworkLoadResult.Missing
                }
            cache.putArtwork(key, result)
            result.also(proposed::complete)
        } catch (exception: CancellationException) {
            proposed.completeExceptionally(exception)
            throw exception
        } catch (_: Exception) {
            ArtworkLoadResult.Unavailable.also(proposed::complete)
        } finally {
            withContext(NonCancellable) {
                inFlightMutex.withLock { artworkInFlight.remove(key, proposed) }
            }
        }
    }
}
