package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LyricsSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FlacLyricsExtractorTest {
    private val extractor = FlacLyricsExtractor()

    @Test
    fun `extract synced lyrics from standard FLAC stream`() {
        val flacBytes = buildFlacFile(
            blocks = listOf(
                MetadataBlock(type = 0, data = ByteArray(34), isLast = false), // STREAMINFO
                MetadataBlock(
                    type = 4,
                    data = VorbisCommentParserTest.buildVorbisCommentBlock(
                        vendor = "reference libFLAC 1.4.0",
                        comments = listOf("LYRICS" to "[00:02.00]FLAC synced line 1\n[00:06.00]FLAC synced line 2"),
                    ),
                    isLast = true,
                ),
            ),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(flacBytes))

        assertNotNull(sylt)
        assertEquals(LyricsSource.EMBEDDED_SYLT, sylt!!.source)
        assertEquals(2, sylt.timedLines.size)
        assertEquals(2000L, sylt.timedLines[0].timestampMs)
        assertEquals("FLAC synced line 1", sylt.timedLines[0].text)
        assertEquals(6000L, sylt.timedLines[1].timestampMs)
        assertEquals("FLAC synced line 2", sylt.timedLines[1].text)

        assertNotNull(uslt)
        assertEquals(LyricsSource.EMBEDDED_USLT, uslt!!.source)
    }

    @Test
    fun `extract static plain lyrics from FLAC stream`() {
        val flacBytes = buildFlacFile(
            blocks = listOf(
                MetadataBlock(type = 0, data = ByteArray(34), isLast = false),
                MetadataBlock(
                    type = 4,
                    data = VorbisCommentParserTest.buildVorbisCommentBlock(
                        vendor = "libFLAC",
                        comments = listOf("UNSYNCEDLYRICS" to "Static FLAC lyrics content"),
                    ),
                    isLast = true,
                ),
            ),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(flacBytes))

        assertNull(sylt)
        assertNotNull(uslt)
        assertEquals("Static FLAC lyrics content", uslt!!.rawText)
    }

    @Test
    fun `extract from FLAC with multiple metadata blocks`() {
        val flacBytes = buildFlacFile(
            blocks = listOf(
                MetadataBlock(type = 0, data = ByteArray(34), isLast = false), // STREAMINFO
                MetadataBlock(type = 1, data = ByteArray(128), isLast = false), // PADDING
                MetadataBlock(
                    type = 4,
                    data = VorbisCommentParserTest.buildVorbisCommentBlock(
                        vendor = "libFLAC",
                        comments = listOf("LYRICS" to "[00:03.50]Line in multi-block FLAC"),
                    ),
                    isLast = false,
                ),
                MetadataBlock(type = 6, data = ByteArray(256), isLast = true), // PICTURE
            ),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(flacBytes))

        assertNotNull(sylt)
        assertEquals(3500L, sylt!!.timedLines[0].timestampMs)
        assertEquals("Line in multi-block FLAC", sylt.timedLines[0].text)
    }

    @Test
    fun `extract from FLAC with ID3v2 header prepended`() {
        val id3Header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            3, 0, 0,
            0, 0, 0, 10, // 10 bytes payload
        )
        val id3Payload = ByteArray(10)

        val flacBytes = id3Header + id3Payload + buildFlacFile(
            blocks = listOf(
                MetadataBlock(
                    type = 4,
                    data = VorbisCommentParserTest.buildVorbisCommentBlock(
                        vendor = "libFLAC",
                        comments = listOf("LYRICS" to "[00:01.00]Prepended ID3 FLAC lyrics"),
                    ),
                    isLast = true,
                ),
            ),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(flacBytes))

        assertNotNull(sylt)
        assertEquals("Prepended ID3 FLAC lyrics", sylt!!.timedLines[0].text)
    }

    @Test
    fun `non FLAC stream returns null candidates`() {
        val invalidBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(invalidBytes))
        assertNull(sylt)
        assertNull(uslt)
    }

    @Test
    fun `FLAC without vorbis comment returns null candidates`() {
        val flacBytes = buildFlacFile(
            blocks = listOf(
                MetadataBlock(type = 0, data = ByteArray(34), isLast = true),
            ),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(flacBytes))
        assertNull(sylt)
        assertNull(uslt)
    }

    data class MetadataBlock(
        val type: Int,
        val data: ByteArray,
        val isLast: Boolean,
    )

    companion object {
        fun buildFlacFile(blocks: List<MetadataBlock>): ByteArray {
            val stream = ByteArrayOutputStream()
            // FLAC Magic
            stream.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))

            for (block in blocks) {
                val headerByte0 = (if (block.isLast) 0x80 else 0x00) or (block.type and 0x7F)
                val length = block.data.size
                stream.write(headerByte0)
                stream.write((length shr 16) and 0xFF)
                stream.write((length shr 8) and 0xFF)
                stream.write(length and 0xFF)
                stream.write(block.data)
            }
            return stream.toByteArray()
        }
    }
}
