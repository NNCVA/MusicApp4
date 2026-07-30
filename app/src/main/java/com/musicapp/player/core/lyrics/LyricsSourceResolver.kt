package com.musicapp.player.core.lyrics

class LyricsSourceResolver {
    fun resolve(candidates: LyricsCandidates): ResolvedLyrics {
        val ordered = listOfNotNull(
            candidates.externalLrc,
            candidates.embeddedSylt,
            candidates.embeddedUslt,
        )
        ordered.firstOrNull { it.timedLines.isNotEmpty() }?.let { candidate ->
            val sortedLines = candidate.timedLines.withIndex()
                .sortedWith(compareBy<IndexedValue<TimedLyricLine>> { it.value.timestampMs }.thenBy { it.index })
                .map { it.value }
            return SynchronizedLyrics(candidate.source, sortedLines)
        }
        ordered.firstOrNull { it.source == LyricsSource.EXTERNAL_LRC && !it.rawText.isNullOrBlank() }
            ?.let { return StaticLyrics(it.source, checkNotNull(it.rawText)) }
        ordered.firstOrNull { it.source != LyricsSource.EXTERNAL_LRC && !it.rawText.isNullOrBlank() }
            ?.let { return StaticLyrics(it.source, checkNotNull(it.rawText)) }
        return MissingLyrics
    }
}
