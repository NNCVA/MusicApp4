package com.musicapp.player.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Id3LyricsFrameParserTest {
    private val parser = Id3LyricsFrameParser()

    @Test
    fun `sylt millisecond payload keeps ordered text timestamp pairs`() {
        val data = byteArrayOf(
            3,
            'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
            2,
            1,
            0,
        ) + entry("first", 1_000) + entry("second", 2_500)

        val parsed = requireNotNull(parser.parseSylt(data))

        assertEquals(listOf(1_000L, 2_500L), parsed.timedLines.map { it.timestampMs })
        assertEquals(listOf("first", "second"), parsed.timedLines.map { it.text })
    }

    @Test
    fun `sylt non millisecond format remains usable as static embedded text`() {
        val data = byteArrayOf(
            3,
            'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
            1,
            1,
            0,
        ) + entry("plain", 120)

        val parsed = requireNotNull(parser.parseSylt(data))

        assertTrue(parsed.timedLines.isEmpty())
        assertEquals("plain", parsed.rawText)
    }

    @Test
    fun `uslt can carry lrc timestamps or remain raw static text`() {
        val timed = byteArrayOf(3, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0) +
            "[00:01.50]timed".toByteArray()
        val plain = byteArrayOf(3, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0) +
            "plain embedded".toByteArray()

        assertEquals(1_500L, requireNotNull(parser.parseUslt(timed)).timedLines.single().timestampMs)
        assertTrue(requireNotNull(parser.parseUslt(plain)).timedLines.isEmpty())
    }

    private fun entry(text: String, timestampMs: Int): ByteArray =
        text.toByteArray() + byteArrayOf(
            0,
            (timestampMs ushr 24).toByte(),
            (timestampMs ushr 16).toByte(),
            (timestampMs ushr 8).toByte(),
            timestampMs.toByte(),
        )
}
