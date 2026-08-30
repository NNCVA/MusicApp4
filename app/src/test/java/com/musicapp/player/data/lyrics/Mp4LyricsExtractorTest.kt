package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LyricsSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Mp4LyricsExtractorTest {
    private val extractor = Mp4LyricsExtractor()

    @Test
    fun `extract synced lyrics from standard M4A iTunes atom hierarchy`() {
        val lyricsText = "[00:01.50]M4A synced line 1\n[00:04.20]M4A synced line 2"
        val m4aBytes = buildM4aFile(
            lyricsText = lyricsText,
            lyricsBoxType = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(m4aBytes))

        assertNotNull(sylt)
        assertEquals(LyricsSource.EMBEDDED_SYLT, sylt!!.source)
        assertEquals(2, sylt.timedLines.size)
        assertEquals(1500L, sylt.timedLines[0].timestampMs)
        assertEquals("M4A synced line 1", sylt.timedLines[0].text)
        assertEquals(4200L, sylt.timedLines[1].timestampMs)
        assertEquals("M4A synced line 2", sylt.timedLines[1].text)

        assertNotNull(uslt)
        assertEquals(lyricsText, uslt!!.rawText)
    }

    @Test
    fun `extract static plain lyrics from M4A iTunes atom`() {
        val lyricsText = "Plain M4A lyrics without any timestamps"
        val m4aBytes = buildM4aFile(
            lyricsText = lyricsText,
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(m4aBytes))

        assertNull(sylt)
        assertNotNull(uslt)
        assertEquals(LyricsSource.EMBEDDED_USLT, uslt!!.source)
        assertEquals(lyricsText, uslt.rawText)
    }

    @Test
    fun `extract lyrics when sibling metadata atoms exist`() {
        val titleBox = buildItemBox("©nam", buildDataBox(1, "Sample Title".toByteArray()))
        val artistBox = buildItemBox("©ART", buildDataBox(1, "Sample Artist".toByteArray()))
        val lyricsBox = buildItemBox(
            typeBytes = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()),
            payload = buildDataBox(1, "[00:03.00]Sibling atom lyrics".toByteArray()),
        )
        val albumBox = buildItemBox("©alb", buildDataBox(1, "Sample Album".toByteArray()))

        val ilstPayload = ByteArrayOutputStream().apply {
            write(titleBox)
            write(artistBox)
            write(lyricsBox)
            write(albumBox)
        }.toByteArray()

        val ilstBox = buildBox("ilst".toByteArray(), ilstPayload)
        val metaPayload = byteArrayOf(0, 0, 0, 0) + ilstBox // FullBox 4 bytes
        val metaBox = buildBox("meta".toByteArray(), metaPayload)
        val udtaBox = buildBox("udta".toByteArray(), metaBox)
        val moovBox = buildBox("moov".toByteArray(), udtaBox)
        val ftypBox = buildBox("ftyp".toByteArray(), "M4A ".toByteArray() + byteArrayOf(0, 0, 0, 0))

        val fullM4a = ftypBox + moovBox

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(fullM4a))

        assertNotNull(sylt)
        assertEquals("Sibling atom lyrics", sylt!!.timedLines[0].text)
    }

    @Test
    fun `extract lyrics with alternative lyr atom name`() {
        val lyricsText = "[00:02.00]Alternative lyr atom"
        val m4aBytes = buildM4aFile(
            lyricsText = lyricsText,
            lyricsBoxType = byteArrayOf('l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte(), ' '.code.toByte()),
        )

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(m4aBytes))

        assertNotNull(sylt)
        assertEquals("Alternative lyr atom", sylt!!.timedLines[0].text)
    }

    @Test
    fun `extract lyrics with 64-bit extended box size`() {
        val lyricsData = buildDataBox(1, "[00:01.00]Extended box lyrics".toByteArray())
        val lyricsBox = buildItemBox(
            typeBytes = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()),
            payload = lyricsData,
        )
        val ilstBox = buildBox("ilst".toByteArray(), lyricsBox)
        val metaPayload = byteArrayOf(0, 0, 0, 0) + ilstBox
        val metaBox = buildBox("meta".toByteArray(), metaPayload)
        val udtaBox = buildBox("udta".toByteArray(), metaBox)

        // Build moov box with 64-bit extended size
        val moovHeader = ByteArrayOutputStream()
        val totalSize = 16L + udtaBox.size
        moovHeader.write(byteArrayOf(0, 0, 0, 1)) // size == 1 indicates 64-bit size follows
        moovHeader.write("moov".toByteArray())
        // 8 bytes 64-bit size
        moovHeader.write((totalSize shr 56 and 0xFF).toInt())
        moovHeader.write((totalSize shr 48 and 0xFF).toInt())
        moovHeader.write((totalSize shr 40 and 0xFF).toInt())
        moovHeader.write((totalSize shr 32 and 0xFF).toInt())
        moovHeader.write((totalSize shr 24 and 0xFF).toInt())
        moovHeader.write((totalSize shr 16 and 0xFF).toInt())
        moovHeader.write((totalSize shr 8 and 0xFF).toInt())
        moovHeader.write((totalSize and 0xFF).toInt())
        moovHeader.write(udtaBox)

        val m4aBytes = moovHeader.toByteArray()

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(m4aBytes))

        assertNotNull(sylt)
        assertEquals("Extended box lyrics", sylt!!.timedLines[0].text)
    }

    @Test
    fun `M4A file without lyrics returns null candidates`() {
        val ftypBox = buildBox("ftyp".toByteArray(), "M4A ".toByteArray() + byteArrayOf(0, 0, 0, 0))
        val mvhdBox = buildBox("mvhd".toByteArray(), ByteArray(100))
        val moovBox = buildBox("moov".toByteArray(), mvhdBox)

        val (sylt, uslt) = extractor.extract(ByteArrayInputStream(ftypBox + moovBox))

        assertNull(sylt)
        assertNull(uslt)
    }

    @Test
    fun `malformed or empty stream returns null candidates safely`() {
        assertNull(extractor.extract(ByteArrayInputStream(ByteArray(0))).first)
        assertNull(extractor.extract(ByteArrayInputStream(byteArrayOf(0, 0, 0, 10, 'm'.code.toByte()))).first)
    }

    companion object {
        fun buildM4aFile(
            lyricsText: String,
            lyricsBoxType: ByteArray = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()),
        ): ByteArray {
            val ftypPayload = "M4A ".toByteArray() + byteArrayOf(0, 0, 0, 0)
            val ftypBox = buildBox("ftyp".toByteArray(), ftypPayload)

            val dataBox = buildDataBox(typeCode = 1, textBytes = lyricsText.toByteArray(Charsets.UTF_8))
            val lyricsBox = buildItemBox(lyricsBoxType, dataBox)
            val ilstBox = buildBox("ilst".toByteArray(), lyricsBox)

            // meta box is a FullBox (4 bytes version and flags = 0)
            val metaPayload = byteArrayOf(0, 0, 0, 0) + ilstBox
            val metaBox = buildBox("meta".toByteArray(), metaPayload)

            val udtaBox = buildBox("udta".toByteArray(), metaBox)
            val moovBox = buildBox("moov".toByteArray(), udtaBox)

            return ftypBox + moovBox
        }

        fun buildBox(type: ByteArray, payload: ByteArray): ByteArray {
            val stream = ByteArrayOutputStream()
            val totalSize = 8 + payload.size
            stream.write((totalSize shr 24) and 0xFF)
            stream.write((totalSize shr 16) and 0xFF)
            stream.write((totalSize shr 8) and 0xFF)
            stream.write(totalSize and 0xFF)
            stream.write(type)
            stream.write(payload)
            return stream.toByteArray()
        }

        fun buildItemBox(typeStr: String, payload: ByteArray): ByteArray =
            buildItemBox(typeStr.toByteArray(Charsets.ISO_8859_1), payload)

        fun buildItemBox(typeBytes: ByteArray, payload: ByteArray): ByteArray =
            buildBox(typeBytes, payload)

        fun buildDataBox(typeCode: Int, textBytes: ByteArray): ByteArray {
            val dataPayload = ByteArrayOutputStream()
            // 4 bytes type / flags
            dataPayload.write(0)
            dataPayload.write(0)
            dataPayload.write(0)
            dataPayload.write(typeCode and 0xFF)
            // 4 bytes locale
            dataPayload.write(0)
            dataPayload.write(0)
            dataPayload.write(0)
            dataPayload.write(0)
            // text payload
            dataPayload.write(textBytes)

            return buildBox("data".toByteArray(), dataPayload.toByteArray())
        }
    }
}
