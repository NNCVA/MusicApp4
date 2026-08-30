package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LyricsSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OggLyricsExtractorTest {
    private val extractor = OggLyricsExtractor()

    @Test
    fun `extract synced lyrics from Ogg Vorbis stream`() {
        val vorbisCommentBlock = VorbisCommentParserTest.buildVorbisCommentBlock(
            vendor = "Xiph.Org libVorbis",
            comments = listOf(
                "LYRICS" to "[00:01.20]Ogg Vorbis synced line 1\n[00:04.80]Ogg Vorbis synced line 2",
            ),
        )

        val oggBytes = buildOggVorbisStream(vorbisCommentBlock)

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(oggBytes))

        assertNotNull(sylt)
        assertEquals(LyricsSource.EMBEDDED_SYLT, sylt!!.source)
        assertEquals(2, sylt.timedLines.size)
        assertEquals(1200L, sylt.timedLines[0].timestampMs)
        assertEquals("Ogg Vorbis synced line 1", sylt.timedLines[0].text)
        assertEquals(4800L, sylt.timedLines[1].timestampMs)
        assertEquals("Ogg Vorbis synced line 2", sylt.timedLines[1].text)

        assertNotNull(uslt)
        assertEquals(LyricsSource.EMBEDDED_USLT, uslt!!.source)
    }

    @Test
    fun `extract static plain lyrics from Ogg Vorbis stream`() {
        val vorbisCommentBlock = VorbisCommentParserTest.buildVorbisCommentBlock(
            vendor = "libVorbis",
            comments = listOf(
                "UNSYNCEDLYRICS" to "Static Ogg Vorbis lyrics",
            ),
        )

        val oggBytes = buildOggVorbisStream(vorbisCommentBlock)

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(oggBytes))

        assertNull(sylt)
        assertNotNull(uslt)
        assertEquals("Static Ogg Vorbis lyrics", uslt!!.rawText)
    }

    @Test
    fun `extract synced lyrics from Ogg Opus stream`() {
        val opusCommentBlock = VorbisCommentParserTest.buildVorbisCommentBlock(
            vendor = "libopus 1.3.1",
            comments = listOf(
                "SYNCEDLYRICS" to "[00:02.50]Opus synced line 1\n[00:05.00]Opus synced line 2",
            ),
        )

        val oggBytes = buildOggOpusStream(opusCommentBlock)

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(oggBytes))

        assertNotNull(sylt)
        assertEquals(LyricsSource.EMBEDDED_SYLT, sylt!!.source)
        assertEquals(2, sylt.timedLines.size)
        assertEquals(2500L, sylt.timedLines[0].timestampMs)
        assertEquals("Opus synced line 1", sylt.timedLines[0].text)
        assertEquals(5000L, sylt.timedLines[1].timestampMs)
        assertEquals("Opus synced line 2", sylt.timedLines[1].text)
    }

    @Test
    fun `extract lyrics from multi-segment large Vorbis Comment packet`() {
        // Build a comment string larger than 600 bytes to test multi-segment lacing values
        val longLyrics = (1..30).joinToString("\n") { i ->
            val sec = i * 2
            val minStr = (sec / 60).toString().padStart(2, '0')
            val secStr = (sec % 60).toString().padStart(2, '0')
            "[$minStr:$secStr.00]Long lyrics line number $i in multi-segment packet test"
        }

        val commentBlock = VorbisCommentParserTest.buildVorbisCommentBlock(
            vendor = "libVorbis",
            comments = listOf("LYRICS" to longLyrics),
        )

        val oggBytes = buildOggVorbisStream(commentBlock)

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(oggBytes))

        assertNotNull(sylt)
        assertEquals(30, sylt!!.timedLines.size)
        assertEquals("Long lyrics line number 1 in multi-segment packet test", sylt.timedLines[0].text)
        assertEquals(2000L, sylt.timedLines[0].timestampMs)
        assertEquals("Long lyrics line number 30 in multi-segment packet test", sylt.timedLines[29].text)
        assertEquals(60000L, sylt.timedLines[29].timestampMs)
    }

    @Test
    fun `Ogg stream with no comments returns null candidates`() {
        val bosHeader = byteArrayOf(1, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte()) + ByteArray(23)
        val bosPage = buildOggPage(headerType = 0x02, sequenceNumber = 0, packets = listOf(bosHeader))

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(bosPage))
        assertNull(sylt)
        assertNull(uslt)
    }

    @Test
    fun `malformed or empty stream returns null candidates`() {
        assertNull(extractor.extract(ByteArrayInputStream(ByteArray(0))).first)
        assertNull(extractor.extract(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))).first)
    }

    companion object {
        fun buildOggVorbisStream(vorbisCommentPayload: ByteArray): ByteArray {
            val stream = ByteArrayOutputStream()

            // Page 0: BOS identification header
            val identPacket = byteArrayOf(
                0x01,
                'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(),
                'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(),
            ) + ByteArray(23)
            val page0 = buildOggPage(headerType = 0x02, sequenceNumber = 0, packets = listOf(identPacket))
            stream.write(page0)

            // Page 1: Comment header
            val commentPacket = byteArrayOf(
                0x03,
                'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(),
                'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(),
            ) + vorbisCommentPayload + byteArrayOf(0x01) // framing bit
            val page1 = buildOggPage(headerType = 0x00, sequenceNumber = 1, packets = listOf(commentPacket))
            stream.write(page1)

            return stream.toByteArray()
        }

        fun buildOggOpusStream(opusTagsPayload: ByteArray): ByteArray {
            val stream = ByteArrayOutputStream()

            // Page 0: BOS OpusHead
            val opusHeadPacket = byteArrayOf(
                'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
                'H'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte(),
            ) + ByteArray(11)
            val page0 = buildOggPage(headerType = 0x02, sequenceNumber = 0, packets = listOf(opusHeadPacket))
            stream.write(page0)

            // Page 1: OpusTags comment header
            val opusTagsPacket = byteArrayOf(
                'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
                'T'.code.toByte(), 'a'.code.toByte(), 'g'.code.toByte(), 's'.code.toByte(),
            ) + opusTagsPayload
            val page1 = buildOggPage(headerType = 0x00, sequenceNumber = 1, packets = listOf(opusTagsPacket))
            stream.write(page1)

            return stream.toByteArray()
        }

        fun buildOggPage(
            headerType: Int,
            sequenceNumber: Int,
            packets: List<ByteArray>,
        ): ByteArray {
            val stream = ByteArrayOutputStream()
            val segmentTable = ByteArrayOutputStream()
            val payload = ByteArrayOutputStream()

            for (packet in packets) {
                var remaining = packet.size
                var offset = 0
                while (remaining >= 255) {
                    segmentTable.write(255)
                    payload.write(packet, offset, 255)
                    offset += 255
                    remaining -= 255
                }
                segmentTable.write(remaining)
                if (remaining > 0) {
                    payload.write(packet, offset, remaining)
                }
            }

            val segmentBytes = segmentTable.toByteArray()
            val payloadBytes = payload.toByteArray()

            // 4 bytes magic "OggS"
            stream.write(byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte()))
            // 1 byte version = 0
            stream.write(0)
            // 1 byte header type flag
            stream.write(headerType and 0xFF)
            // 8 bytes granule position
            stream.write(ByteArray(8))
            // 4 bytes serial number (0x12345678)
            stream.write(byteArrayOf(0x78, 0x56, 0x34, 0x12))
            // 4 bytes sequence number
            stream.write(sequenceNumber and 0xFF)
            stream.write((sequenceNumber shr 8) and 0xFF)
            stream.write((sequenceNumber shr 16) and 0xFF)
            stream.write((sequenceNumber shr 24) and 0xFF)
            // 4 bytes CRC checksum (dummy 0)
            stream.write(ByteArray(4))
            // 1 byte page segments count
            stream.write(segmentBytes.size)
            // segment table
            stream.write(segmentBytes)
            // page payload
            stream.write(payloadBytes)

            return stream.toByteArray()
        }
    }
}
