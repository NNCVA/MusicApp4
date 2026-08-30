package com.musicapp.player.data.lyrics

import com.musicapp.player.data.lyrics.LyricsIoUtils.readExactly
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt16BE
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt16LE
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt24BE
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt32BE
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt32LE
import com.musicapp.player.data.lyrics.LyricsIoUtils.skipFully
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsIoUtilsTest {

    @Test
    fun `readExactly reads exact bytes or returns null on EOF`() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))

        val first = stream.readExactly(3)
        assertArrayEquals(byteArrayOf(1, 2, 3), first)

        val second = stream.readExactly(5) // Only 2 bytes remaining
        assertNull(second)

        val empty = stream.readExactly(0)
        assertArrayEquals(ByteArray(0), empty)

        val negative = stream.readExactly(-1)
        assertNull(negative)
    }

    @Test
    fun `skipFully skips expected bytes across multiple chunks`() {
        val data = ByteArray(20000) { (it and 0xFF).toByte() }
        val stream = ByteArrayInputStream(data)

        val skipped = stream.skipFully(15000)
        assertEquals(15000L, skipped)

        val remaining = stream.readExactly(5000)
        assertEquals(5000, remaining?.size)

        val skippedAtEof = stream.skipFully(100)
        assertEquals(0L, skippedAtEof)
    }

    @Test
    fun `skipFully handles stream where skip returns zero`() {
        val data = byteArrayOf(10, 20, 30, 40, 50)
        val customStream = object : InputStream() {
            private val delegate = ByteArrayInputStream(data)
            override fun read(): Int = delegate.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
            override fun skip(n: Long): Long = 0L // forces skipFully to fall back to read buffer
        }

        val skipped = customStream.skipFully(3)
        assertEquals(3L, skipped)

        val nextByte = customStream.read()
        assertEquals(40, nextByte)
    }

    @Test
    fun `read integer methods decode big endian and little endian values correctly`() {
        val bytes = byteArrayOf(
            0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(),
            0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), 0x98.toByte(),
        )

        // UInt32 BE: 0x12345678
        assertEquals(0x12345678L, bytes.readUInt32BE(0))
        // UInt32 LE: 0x78563412
        assertEquals(0x78563412L, bytes.readUInt32LE(0))

        // UInt24 BE: 0x123456
        assertEquals(0x123456, bytes.readUInt24BE(0))
        // UInt16 BE: 0x1234
        assertEquals(0x1234, bytes.readUInt16BE(0))
        // UInt16 LE: 0x3412
        assertEquals(0x3412, bytes.readUInt16LE(0))

        // Negative signed byte representation treated as unsigned
        assertEquals(0xFEDCBA98L, bytes.readUInt32BE(4))
    }
}
