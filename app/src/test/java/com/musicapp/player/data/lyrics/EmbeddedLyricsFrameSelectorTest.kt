package com.musicapp.player.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedLyricsFrameSelectorTest {
    private val selector = EmbeddedLyricsFrameSelector()

    @Test
    fun `later synchronized frames win over earlier static frames within each embedded type`() {
        val (sylt, uslt) = selector.select(
            listOf(
                EmbeddedLyricsFrame("SYLT", syltFrame(timestampFormat = 1, text = "static sylt")),
                EmbeddedLyricsFrame("SYLT", syltFrame(timestampFormat = 2, text = "synced sylt")),
                EmbeddedLyricsFrame("USLT", usltFrame("static uslt")),
                EmbeddedLyricsFrame("USLT", usltFrame("[00:02.00]synced uslt")),
            ),
        )

        assertEquals("synced sylt", requireNotNull(sylt).timedLines.single().text)
        assertEquals(1_000L, sylt.timedLines.single().timestampMs)
        assertEquals("synced uslt", requireNotNull(uslt).timedLines.single().text)
        assertEquals(2_000L, uslt.timedLines.single().timestampMs)
    }

    private fun syltFrame(timestampFormat: Int, text: String): ByteArray =
        byteArrayOf(
            3,
            'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
            timestampFormat.toByte(),
            1,
            0,
        ) + text.toByteArray() + byteArrayOf(0, 0, 0, 3, 0xE8.toByte())

    private fun usltFrame(text: String): ByteArray =
        byteArrayOf(3, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0) + text.toByteArray()
}
