package com.musicapp.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSourceResolverTest {
    private val resolver = LyricsSourceResolver()

    @Test
    fun `untimed external lrc never hides timestamped sylt`() {
        val result = resolver.resolve(
            LyricsCandidates(
                externalLrc = candidate(LyricsSource.EXTERNAL_LRC, "plain external"),
                embeddedSylt = candidate(
                    LyricsSource.EMBEDDED_SYLT,
                    "embedded",
                    TimedLyricLine(1_000, "synced"),
                ),
            ),
        )

        assertEquals(LyricsSource.EMBEDDED_SYLT, (result as SynchronizedLyrics).source)
        assertEquals("synced", result.lines.single().text)
    }

    @Test
    fun `timestamped sources follow external sylt uslt priority`() {
        val result = resolver.resolve(
            LyricsCandidates(
                externalLrc = candidate(LyricsSource.EXTERNAL_LRC, "external", TimedLyricLine(2_000, "external")),
                embeddedSylt = candidate(LyricsSource.EMBEDDED_SYLT, "sylt", TimedLyricLine(1_000, "sylt")),
                embeddedUslt = candidate(LyricsSource.EMBEDDED_USLT, "uslt", TimedLyricLine(500, "uslt")),
            ),
        )

        assertEquals(LyricsSource.EXTERNAL_LRC, (result as SynchronizedLyrics).source)
    }

    @Test
    fun `static fallback prefers external text then raw embedded text`() {
        val external = resolver.resolve(
            LyricsCandidates(
                externalLrc = candidate(LyricsSource.EXTERNAL_LRC, "external text"),
                embeddedUslt = candidate(LyricsSource.EMBEDDED_USLT, "embedded text"),
            ),
        ) as StaticLyrics
        val embedded = resolver.resolve(
            LyricsCandidates(embeddedUslt = candidate(LyricsSource.EMBEDDED_USLT, "embedded text")),
        ) as StaticLyrics

        assertEquals("external text", external.text)
        assertEquals(LyricsSource.EXTERNAL_LRC, external.source)
        assertEquals("embedded text", embedded.text)
    }

    @Test
    fun `no lyric text resolves to missing resource key`() {
        val result = resolver.resolve(LyricsCandidates())

        assertTrue(result is MissingLyrics)
        assertEquals("lyrics_not_found", (result as MissingLyrics).stringResourceKey)
    }

    private fun candidate(
        source: LyricsSource,
        rawText: String,
        vararg lines: TimedLyricLine,
    ) = LyricsCandidate(source, rawText, lines.toList())
}
