package com.musicapp.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSynchronizerTest {
    private val synchronizer = LyricsSynchronizer()

    @Test
    fun `three line state follows playback and exact duplicate time selects last duplicate`() {
        val lyrics = SynchronizedLyrics(
            source = LyricsSource.EXTERNAL_LRC,
            lines = listOf(
                TimedLyricLine(1_000, "first"),
                TimedLyricLine(2_000, "duplicate one"),
                TimedLyricLine(2_000, "duplicate two"),
                TimedLyricLine(3_000, "next"),
            ),
        )

        val state = synchronizer.synchronize(lyrics, 2_000)

        assertEquals(2, state.activeLineIndex)
        assertEquals("duplicate one", state.previousLine)
        assertEquals("duplicate two", state.currentLine)
        assertEquals("next", state.nextLine)
        assertEquals(3_000L, synchronizer.seekPositionMs(lyrics, 3))
    }

    @Test
    fun `static and missing lyrics clear all synchronized line values`() {
        listOf<ResolvedLyrics>(
            StaticLyrics(LyricsSource.EXTERNAL_LRC, "plain"),
            MissingLyrics,
        ).forEach { lyrics ->
            val state = synchronizer.synchronize(lyrics, 8_000)
            assertFalse(state.isSynchronized)
            assertEquals("", state.previousLine)
            assertEquals("", state.currentLine)
            assertEquals("", state.nextLine)
            assertNull(state.activeLineIndex)
        }
    }

    @Test
    fun `manual scroll pauses centering for five seconds and explicit return resumes immediately`() {
        val controller = LyricsAutoCenterController()
        controller.onManualScroll(nowMs = 1_000)

        assertFalse(controller.shouldAutoCenter(nowMs = 5_999))
        assertTrue(controller.shouldAutoCenter(nowMs = 6_000))
        controller.onManualScroll(nowMs = 7_000)
        controller.returnToCurrentLine()
        assertTrue(controller.shouldAutoCenter(nowMs = 7_001))
    }
}
