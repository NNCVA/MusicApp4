package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.MissingLyrics
import com.musicapp.player.core.lyrics.ResolvedLyrics
import com.musicapp.player.core.metadata.MetadataCacheKey
import com.musicapp.player.data.metadata.MetadataReadLimiter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class LyricsLoadCoordinator @Inject constructor(
    private val readLimiter: MetadataReadLimiter,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<MetadataCacheKey, ResolvedLyrics>(0, 0.75f, true)
    private val inFlight = mutableMapOf<MetadataCacheKey, Deferred<ResolvedLyrics>>()

    suspend fun load(
        key: MetadataCacheKey,
        loader: suspend () -> ResolvedLyrics,
    ): ResolvedLyrics {
        mutex.withLock { cache[key] }?.let { return it }
        val proposed = CompletableDeferred<ResolvedLyrics>()
        val active = mutex.withLock { inFlight.getOrPut(key) { proposed } }
        if (active !== proposed) return active.await()
        return try {
            val loaded = readLimiter.run(loader)
            if (loaded !is MissingLyrics) {
                mutex.withLock {
                    cache[key] = loaded
                    while (cache.size > MAX_CACHE_ENTRIES) {
                        cache.entries.iterator().run {
                            next()
                            remove()
                        }
                    }
                }
            }
            loaded.also(proposed::complete)
        } catch (exception: CancellationException) {
            proposed.completeExceptionally(exception)
            throw exception
        } catch (_: Exception) {
            MissingLyrics.also(proposed::complete)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { inFlight.remove(key, proposed) }
            }
        }
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 128
    }
}
