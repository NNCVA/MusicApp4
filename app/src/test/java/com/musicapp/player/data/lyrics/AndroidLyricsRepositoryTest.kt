package com.musicapp.player.data.lyrics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.musicapp.player.core.domain.model.Availability
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.MissingLyrics
import com.musicapp.player.core.lyrics.StaticLyrics
import com.musicapp.player.core.lyrics.SynchronizedLyrics
import com.musicapp.player.data.metadata.MetadataReadLimiter
import com.musicapp.player.data.metadata.TrackContentUriResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidLyricsRepositoryTest {

    @Test
    fun `load FLAC track with embedded synced lyrics returns SynchronizedLyrics`() = runTest {
        val flacData = FlacLyricsExtractorTest.buildFlacFile(
            blocks = listOf(
                FlacLyricsExtractorTest.MetadataBlock(
                    type = 4,
                    data = VorbisCommentParserTest.buildVorbisCommentBlock(
                        vendor = "libFLAC",
                        comments = listOf("LYRICS" to "[00:01.00]Line 1\n[00:03.00]Line 2"),
                    ),
                    isLast = true,
                ),
            ),
        )

        val track = sampleTrack(id = 1L, name = "song.flac", mimeType = "audio/flac")
        val repository = createRepository(mapOf(1L to flacData))

        val result = repository.load(track)

        assertTrue(result is SynchronizedLyrics)
        val synced = result as SynchronizedLyrics
        assertEquals(LyricsSource.EMBEDDED_SYLT, synced.source)
        assertEquals(2, synced.lines.size)
        assertEquals("Line 1", synced.lines[0].text)
        assertEquals(1000L, synced.lines[0].timestampMs)
        assertEquals("Line 2", synced.lines[1].text)
        assertEquals(3000L, synced.lines[1].timestampMs)
    }

    @Test
    fun `load M4A track with embedded static lyrics returns StaticLyrics`() = runTest {
        val m4aData = Mp4LyricsExtractorTest.buildM4aFile(
            lyricsText = "Static M4A lyrics text",
        )

        val track = sampleTrack(id = 2L, name = "song.m4a", mimeType = "audio/mp4")
        val repository = createRepository(mapOf(2L to m4aData))

        val result = repository.load(track)

        assertTrue(result is StaticLyrics)
        val staticLyrics = result as StaticLyrics
        assertEquals(LyricsSource.EMBEDDED_USLT, staticLyrics.source)
        assertEquals("Static M4A lyrics text", staticLyrics.text)
    }

    @Test
    fun `load Ogg Opus track with embedded synced lyrics returns SynchronizedLyrics`() = runTest {
        val opusData = OggLyricsExtractorTest.buildOggOpusStream(
            VorbisCommentParserTest.buildVorbisCommentBlock(
                vendor = "libopus",
                comments = listOf("LYRICS" to "[00:02.00]Opus line"),
            ),
        )

        val track = sampleTrack(id = 3L, name = "song.opus", mimeType = "audio/opus")
        val repository = createRepository(mapOf(3L to opusData))

        val result = repository.load(track)

        assertTrue(result is SynchronizedLyrics)
        val synced = result as SynchronizedLyrics
        assertEquals("Opus line", synced.lines.single().text)
        assertEquals(2000L, synced.lines.single().timestampMs)
    }

    @Test
    fun `track with no lyrics returns MissingLyrics`() = runTest {
        val flacNoLyrics = FlacLyricsExtractorTest.buildFlacFile(
            blocks = listOf(
                FlacLyricsExtractorTest.MetadataBlock(type = 0, data = ByteArray(34), isLast = true),
            ),
        )

        val track = sampleTrack(id = 4L, name = "empty.flac", mimeType = "audio/flac")
        val repository = createRepository(mapOf(4L to flacNoLyrics))

        val result = repository.load(track)

        assertTrue(result is MissingLyrics)
    }

    private fun createRepository(
        trackDataMap: Map<Long, ByteArray>,
    ): AndroidLyricsRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TestMediaProvider.dataMap.clear()
        trackDataMap.forEach { (id, data) ->
            val uri = Uri.parse("content://test_media/audio/$id")
            TestMediaProvider.dataMap[uri] = data
        }

        Robolectric.buildContentProvider(TestMediaProvider::class.java).create("test_media")

        val testUriResolver = object : TrackContentUriResolver {
            override fun resolve(trackId: TrackId): Uri =
                Uri.parse("content://test_media/audio/${trackId.mediaStoreId}")
        }

        return AndroidLyricsRepository(
            context = context,
            trackUriResolver = testUriResolver,
            loadCoordinator = LyricsLoadCoordinator(MetadataReadLimiter()),
        )
    }

    private fun sampleTrack(
        id: Long,
        name: String,
        mimeType: String,
    ): Track = Track(
        id = TrackId("external", id),
        title = "Title $id",
        artistName = "Artist",
        durationMs = 180_000L,
        dateAddedMs = 1_000L,
        dateModifiedMs = 2_000L,
        relativePath = "Music/",
        displayName = name,
        mimeType = mimeType,
        sizeBytes = 1024L,
        availability = Availability.AVAILABLE,
    )

    class TestMediaProvider : ContentProvider() {
        companion object {
            val dataMap = mutableMapOf<Uri, ByteArray>()
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
            val bytes = dataMap[uri] ?: return null
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { it.write(bytes) }
            return AssetFileDescriptor(readSide, 0, bytes.size.toLong())
        }
    }
}
