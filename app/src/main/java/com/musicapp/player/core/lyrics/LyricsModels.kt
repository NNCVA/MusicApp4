package com.musicapp.player.core.lyrics

enum class LyricsSource {
    EXTERNAL_LRC,
    EMBEDDED_SYLT,
    EMBEDDED_USLT,
}

data class TimedLyricLine(
    val timestampMs: Long,
    val text: String,
) {
    init {
        require(timestampMs >= 0) { "timestampMs must not be negative" }
        require(text.isNotBlank()) { "text must not be blank" }
    }
}

data class LyricsCandidate(
    val source: LyricsSource,
    val rawText: String?,
    val timedLines: List<TimedLyricLine> = emptyList(),
) {
    init {
        require(rawText == null || rawText.isNotBlank()) { "rawText must be null or non-blank" }
    }
}

data class LyricsCandidates(
    val externalLrc: LyricsCandidate? = null,
    val embeddedSylt: LyricsCandidate? = null,
    val embeddedUslt: LyricsCandidate? = null,
) {
    init {
        require(externalLrc == null || externalLrc.source == LyricsSource.EXTERNAL_LRC)
        require(embeddedSylt == null || embeddedSylt.source == LyricsSource.EMBEDDED_SYLT)
        require(embeddedUslt == null || embeddedUslt.source == LyricsSource.EMBEDDED_USLT)
    }
}

sealed interface ResolvedLyrics

data class SynchronizedLyrics(
    val source: LyricsSource,
    val lines: List<TimedLyricLine>,
) : ResolvedLyrics {
    init {
        require(lines.isNotEmpty()) { "synchronized lyrics must contain lines" }
        require(lines.zipWithNext().all { (first, second) -> first.timestampMs <= second.timestampMs }) {
            "synchronized lyric lines must be ordered"
        }
    }
}

data class StaticLyrics(
    val source: LyricsSource,
    val text: String,
) : ResolvedLyrics {
    init {
        require(text.isNotBlank()) { "static lyric text must not be blank" }
    }
}

data object MissingLyrics : ResolvedLyrics {
    const val stringResourceKey: String = "lyrics_not_found"
}
