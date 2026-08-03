package com.musicapp.player.core.lyrics

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    private val parser = LrcParser()

    @Test
    fun `parser applies offset expands multiple tags skips bad lines and preserves duplicate times`() {
        val parsed = parser.parse(
            """
            [offset:+250]
            [00:01.00][00:02.500]First
            [00:01.000]Duplicate
            [not-a-time]ignored
            [00:77.00]invalid seconds
            """.trimIndent(),
            LyricsSource.EXTERNAL_LRC,
        )

        assertEquals(listOf(1_250L, 1_250L, 2_750L), parsed.timedLines.map { it.timestampMs })
        assertEquals(listOf("First", "Duplicate", "First"), parsed.timedLines.map { it.text })
    }

    @Test
    fun `decoder supports utf8 bom utf16 bom and gb18030 fallback`() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "[00:01]UTF8".toByteArray()
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "[00:02]中文".toByteArray(Charsets.UTF_16LE)
        val gb18030 = "[00:03]歌词".toByteArray(Charset.forName("GB18030"))

        assertEquals("UTF8", parser.parse(utf8, LyricsSource.EXTERNAL_LRC).timedLines.single().text)
        assertEquals("中文", parser.parse(utf16, LyricsSource.EXTERNAL_LRC).timedLines.single().text)
        assertEquals("歌词", parser.parse(gb18030, LyricsSource.EXTERNAL_LRC).timedLines.single().text)
    }

    @Test
    fun `negative offset clamps timestamps while keeping original text for static fallback`() {
        val raw = "[offset:-2000]\n[00:01.00]Early\nPlain text"
        val parsed = parser.parse(raw, LyricsSource.EXTERNAL_LRC)

        assertEquals(0L, parsed.timedLines.single().timestampMs)
        assertTrue(parsed.rawText?.contains("Plain text") == true)
    }
}
