package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LyricsSource
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedLyricsExtractorTest {
    private val extractor = EmbeddedLyricsExtractor()

    @Test
    fun `auto detect FLAC stream by magic bytes`() {
        val flacBytes = FlacLyricsExtractorTest.buildFlacFile(
            blocks = listOf(
                FlacLyricsExtractorTest.MetadataBlock(
                    type = 4,
                    data = VorbisCommentParserTest.buildVorbisCommentBlock(
                        vendor = "libFLAC",
                        comments = listOf("LYRICS" to "[00:01.00]FLAC auto detected"),
                    ),
                    isLast = true,
                ),
            ),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(flacBytes))

        assertNotNull(sylt)
        assertEquals("FLAC auto detected", sylt!!.timedLines[0].text)
        assertEquals(LyricsSource.EMBEDDED_SYLT, sylt.source)
    }

    @Test
    fun `auto detect M4A stream by atom magic bytes`() {
        val m4aBytes = Mp4LyricsExtractorTest.buildM4aFile(
            lyricsText = "[00:02.00]M4A auto detected",
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(m4aBytes))

        assertNotNull(sylt)
        assertEquals("M4A auto detected", sylt!!.timedLines[0].text)
    }

    @Test
    fun `auto detect Ogg stream by magic bytes`() {
        val commentBlock = VorbisCommentParserTest.buildVorbisCommentBlock(
            vendor = "libVorbis",
            comments = listOf("LYRICS" to "[00:03.00]Ogg auto detected"),
        )
        val oggBytes = OggLyricsExtractorTest.buildOggVorbisStream(commentBlock)

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(oggBytes))

        assertNotNull(sylt)
        assertEquals("Ogg auto detected", sylt!!.timedLines[0].text)
    }

    @Test
    fun `detect FLAC by displayName extension when stream requires fallback`() {
        val flacBytes = FlacLyricsExtractorTest.buildFlacFile(
            blocks = listOf(
                FlacLyricsExtractorTest.MetadataBlock(
                    type = 4,
                    data = VorbisCommentParserTest.buildVorbisCommentBlock(
                        vendor = "libFLAC",
                        comments = listOf("LYRICS" to "Static fallback FLAC"),
                    ),
                    isLast = true,
                ),
            ),
        )

        val (sylt, uslt) = extractor.extract(
            input = ByteArrayInputStream(flacBytes),
            displayName = "song.flac",
        )

        assertNull(sylt)
        assertNotNull(uslt)
        assertEquals("Static fallback FLAC", uslt!!.rawText)
    }

    @Test
    fun `detect M4A by MIME type`() {
        val m4aBytes = Mp4LyricsExtractorTest.buildM4aFile(
            lyricsText = "[00:04.00]MIME typed M4A",
        )

        val (sylt, uslt) = extractor.extract(
            input = ByteArrayInputStream(m4aBytes),
            mimeType = "audio/mp4",
        )

        assertNotNull(sylt)
        assertEquals("MIME typed M4A", sylt!!.timedLines[0].text)
    }

    @Test
    fun `detect Opus by MIME type audio opus`() {
        val opusCommentBlock = VorbisCommentParserTest.buildVorbisCommentBlock(
            vendor = "libopus",
            comments = listOf("LYRICS" to "[00:05.00]MIME typed Opus"),
        )
        val oggBytes = OggLyricsExtractorTest.buildOggOpusStream(opusCommentBlock)

        val (sylt, uslt) = extractor.extract(
            input = ByteArrayInputStream(oggBytes),
            mimeType = "audio/opus",
        )

        assertNotNull(sylt)
        assertEquals("MIME typed Opus", sylt!!.timedLines[0].text)
    }
}
