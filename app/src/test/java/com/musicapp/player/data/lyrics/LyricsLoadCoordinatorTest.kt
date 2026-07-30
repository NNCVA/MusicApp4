package com.musicapp.player.data.lyrics

import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.StaticLyrics
import com.musicapp.player.core.metadata.MetadataCacheKey
import com.musicapp.player.data.metadata.MetadataReadLimiter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLoadCoordinatorTest {
    @Test
    fun `cancelled owner is removed and the same track can retry`() = runTest {
        val coordinator = LyricsLoadCoordinator(MetadataReadLimiter())
        val key = MetadataCacheKey(TrackId("external", 7), dateModifiedMs = 9)
        val started = CompletableDeferred<Unit>()
        val cancelled = launch {
            coordinator.load(key) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        cancelled.cancelAndJoin()

        var retries = 0
        val result = coordinator.load(key) {
            retries += 1
            StaticLyrics(LyricsSource.EXTERNAL_LRC, "retry succeeded")
        }
        assertEquals(1, retries)
        assertEquals("retry succeeded", (result as StaticLyrics).text)
    }
}
