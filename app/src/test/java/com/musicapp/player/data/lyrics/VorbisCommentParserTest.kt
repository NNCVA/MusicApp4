package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LyricsSource
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VorbisCommentParserTest {
    private val parser = VorbisCommentParser()

    @Test
    fun `parse synced lyrics tag produces synchronized sylt and unsynced uslt candidate`() {
        val payload = buildVorbisCommentBlock(
            vendor = "reference libFLAC",
            comments = listOf(
                "LYRICS" to "[00:01.00]Line 1\n[00:05.50]Line 2",
            ),
        )

        val (sylt, uslt) = parser.parse(payload)

        assertNotNull(sylt)
        assertEquals(LyricsSource.EMBEDDED_SYLT, sylt!!.source)
        assertEquals(2, sylt.timedLines.size)
        assertEquals(1000L, sylt.timedLines[0].timestampMs)
        assertEquals("Line 1", sylt.timedLines[0].text)
        assertEquals(5500L, sylt.timedLines[1].timestampMs)
        assertEquals("Line 2", sylt.timedLines[1].text)

        assertNotNull(uslt)
        assertEquals(LyricsSource.EMBEDDED_USLT, uslt!!.source)
        assertEquals(0, uslt.timedLines.size)
        assertEquals("[00:01.00]Line 1\n[00:05.50]Line 2", uslt.rawText)
    }

    @Test
    fun `parse static plain lyrics produces only unsynced uslt candidate`() {
        val payload = buildVorbisCommentBlock(
            vendor = "reference libFLAC",
            comments = listOf(
                "LYRICS" to "Just plain static lyrics line 1\nLine 2",
            ),
        )

        val (sylt, uslt) = parser.parse(payload)

        assertNull(sylt)
        assertNotNull(uslt)
        assertEquals(LyricsSource.EMBEDDED_USLT, uslt!!.source)
        assertEquals(0, uslt.timedLines.size)
        assertEquals("Just plain static lyrics line 1\nLine 2", uslt.rawText)
    }

    @Test
    fun `separate synced and unsynced tags correctly assign respective candidates`() {
        val payload = buildVorbisCommentBlock(
            vendor = "MusicBee",
            comments = listOf(
                "UNSYNCEDLYRICS" to "Static plain lyrics text",
                "SYNCEDLYRICS" to "[00:02.00]Synced lyrics text",
            ),
        )

        val (sylt, uslt) = parser.parse(payload)

        assertNotNull(sylt)
        assertEquals(1, sylt!!.timedLines.size)
        assertEquals(2000L, sylt.timedLines[0].timestampMs)
        assertEquals("Synced lyrics text", sylt.timedLines[0].text)

        assertNotNull(uslt)
        assertEquals("Static plain lyrics text", uslt!!.rawText)
    }

    @Test
    fun `case insensitive tag names and alternative field names work correctly`() {
        val payload = buildVorbisCommentBlock(
            vendor = "Xiph.Org",
            comments = listOf(
                "lyrics_synchronised" to "[00:03.00]Synced text",
                "Unsynced Lyrics" to "Unsynced text",
            ),
        )

        val (sylt, uslt) = parser.parse(payload)

        assertNotNull(sylt)
        assertEquals(3000L, sylt!!.timedLines[0].timestampMs)
        assertEquals("Synced text", sylt.timedLines[0].text)

        assertNotNull(uslt)
        assertEquals("Unsynced text", uslt!!.rawText)
    }

    @Test
    fun `handles UTF-8 multi-byte characters and GB18030 fallback encoding`() {
        val utf8Text = "[00:01.00]你好世界，歌词测试 🎵\n[00:04.00]第二行"
        val payload = buildVorbisCommentBlock(
            vendor = "TestVendor",
            comments = listOf("LYRICS" to utf8Text),
        )

        val (sylt, uslt) = parser.parse(payload)

        assertNotNull(sylt)
        assertEquals(2, sylt!!.timedLines.size)
        assertEquals("你好世界，歌词测试 🎵", sylt.timedLines[0].text)
        assertEquals("第二行", sylt.timedLines[1].text)
    }

    @Test
    fun `empty or malformed comments return null candidates safely`() {
        assertNull(parser.parse(ByteArray(0)).first)
        assertNull(parser.parse(byteArrayOf(1, 2, 3)).first)

        val emptyBlock = buildVorbisCommentBlock(
            vendor = "Vendor",
            comments = emptyList(),
        )
        val (sylt, uslt) = parser.parse(emptyBlock)
        assertNull(sylt)
        assertNull(uslt)
    }

    @Test
    fun `comments with no lyrics tags return null candidates`() {
        val payload = buildVorbisCommentBlock(
            vendor = "Test",
            comments = listOf(
                "TITLE" to "Song Title",
                "ARTIST" to "Artist Name",
                "ALBUM" to "Album Name",
            ),
        )

        val (sylt, uslt) = parser.parse(payload)
        assertNull(sylt)
        assertNull(uslt)
    }

    companion object {
        fun buildVorbisCommentBlock(
            vendor: String,
            comments: List<Pair<String, String>>,
        ): ByteArray {
            val stream = ByteArrayOutputStream()
            val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
            writeUInt32LE(stream, vendorBytes.size.toLong())
            stream.write(vendorBytes)

            writeUInt32LE(stream, comments.size.toLong())
            for ((key, value) in comments) {
                val commentStr = "$key=$value"
                val commentBytes = commentStr.toByteArray(Charsets.UTF_8)
                writeUInt32LE(stream, commentBytes.size.toLong())
                stream.write(commentBytes)
            }
            return stream.toByteArray()
        }

        fun writeUInt32LE(stream: ByteArrayOutputStream, value: Long) {
            stream.write((value and 0xFF).toInt())
            stream.write(((value shr 8) and 0xFF).toInt())
            stream.write(((value shr 16) and 0xFF).toInt())
            stream.write(((value shr 24) and 0xFF).toInt())
        }
    }
}
