package com.musicapp.player.feature.albums

import com.musicapp.player.core.domain.model.AlbumId
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.metadata.AdvancedTrackMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumDetailAggregatorTest {
    private val defaultAlbumId = AlbumId("external", 50L)

    @Test
    fun `stats aggregates total duration and uniform release year correctly`() {
        val tracks = listOf(
            createTrack(1, durationMs = 60_000L, releaseYear = 2002),
            createTrack(2, durationMs = 120_000L, releaseYear = 2002),
        )

        val stats = AlbumDetailAggregator.aggregateStats(tracks)

        assertEquals(2, stats.trackCount)
        assertEquals(180_000L, stats.totalDurationMs)
        assertEquals(2002, stats.releaseYear)
    }

    @Test
    fun `stats returns null release year when conflicting or missing years exist`() {
        val conflicting = listOf(
            createTrack(1, durationMs = 60_000L, releaseYear = 2001),
            createTrack(2, durationMs = 60_000L, releaseYear = 2002),
        )
        assertNull(AlbumDetailAggregator.aggregateStats(conflicting).releaseYear)

        val missing = listOf(
            createTrack(1, durationMs = 60_000L, releaseYear = 2002),
            createTrack(2, durationMs = 60_000L, releaseYear = null),
        )
        assertNull(AlbumDetailAggregator.aggregateStats(missing).releaseYear)
    }

    @Test
    fun `technical summary returns uniform bitDepth and sampleRate, or null when conflicting or incomplete`() {
        val tracks = listOf(
            createTrack(1),
            createTrack(2),
        )
        val uniformMetadata = mapOf(
            tracks[0].id to createMetadata(bitDepth = 16, sampleRate = 44100),
            tracks[1].id to createMetadata(bitDepth = 16, sampleRate = 44100),
        )

        val summary = AlbumDetailAggregator.aggregateTechnicalSummary(tracks, uniformMetadata)
        assertEquals(16, summary.bitDepth)
        assertEquals(44100, summary.sampleRateHz)

        val conflictingMetadata = mapOf(
            tracks[0].id to createMetadata(bitDepth = 16, sampleRate = 44100),
            tracks[1].id to createMetadata(bitDepth = 24, sampleRate = 48000),
        )
        val conflictedSummary = AlbumDetailAggregator.aggregateTechnicalSummary(tracks, conflictingMetadata)
        assertNull(conflictedSummary.bitDepth)
        assertNull(conflictedSummary.sampleRateHz)

        val incompleteMetadata = mapOf(
            tracks[0].id to createMetadata(bitDepth = 16, sampleRate = 44100),
            tracks[1].id to null,
        )
        val incompleteSummary = AlbumDetailAggregator.aggregateTechnicalSummary(tracks, incompleteMetadata)
        assertNull(incompleteSummary.bitDepth)
        assertNull(incompleteSummary.sampleRateHz)
    }

    @Test
    fun `artists aggregation deduplicates and splits multiple artists and counts tracks per artist`() {
        val tracks = listOf(
            createTrack(1, artist = "周杰伦", artistMediaStoreId = 101L),
            createTrack(2, artist = "周杰伦", artistMediaStoreId = 101L),
            createTrack(3, artist = "周杰伦 feat. 温岚", artistMediaStoreId = null),
        )

        val artists = AlbumDetailAggregator.aggregateArtists(tracks)

        assertEquals(2, artists.size)
        assertEquals("周杰伦", artists[0].artistName)
        assertEquals(3, artists[0].trackCount)
        assertEquals(101L, artists[0].artistMediaStoreId)

        assertEquals("温岚", artists[1].artistName)
        assertEquals(1, artists[1].trackCount)
        assertNull(artists[1].artistMediaStoreId)
    }

    private fun createTrack(
        id: Long,
        artist: String = "Artist",
        artistMediaStoreId: Long? = 1L,
        durationMs: Long = 1000L,
        releaseYear: Int? = null,
    ) = Track(
        id = TrackId(defaultAlbumId.volumeName, id),
        title = "Track $id",
        artistName = artist,
        artistMediaStoreId = artistMediaStoreId,
        albumTitle = "Album",
        albumId = defaultAlbumId,
        durationMs = durationMs,
        dateAddedMs = id,
        dateModifiedMs = id,
        relativePath = "Music/",
        displayName = "$id.mp3",
        releaseYear = releaseYear,
    )

    private fun createMetadata(bitDepth: Int?, sampleRate: Int?) = AdvancedTrackMetadata(
        encoding = "audio/flac",
        bitrateBps = 1411000L,
        sampleRateHz = sampleRate,
        fileSizeBytes = 10_000_000L,
        isReadable = true,
        bitDepth = bitDepth,
    )
}
