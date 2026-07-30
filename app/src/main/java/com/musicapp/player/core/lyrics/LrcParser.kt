package com.musicapp.player.core.lyrics

class LrcParser {
    fun parse(bytes: ByteArray, source: LyricsSource): LyricsCandidate =
        parse(LyricsTextDecoder.decode(bytes), source)

    fun parse(text: String, source: LyricsSource): LyricsCandidate {
        val rawText = text.takeIf(String::isNotBlank)
        val offsetMs = OFFSET.findAll(text)
            .mapNotNull { match -> match.groupValues[1].toLongOrNull() }
            .lastOrNull()
            ?: 0L
        val timed = buildList {
            text.lineSequence().forEach { line ->
                val matches = TIMESTAMP.findAll(line).toList()
                if (matches.isEmpty()) return@forEach
                val lyricText = TIMESTAMP.replace(line, "").trim()
                if (lyricText.isEmpty()) return@forEach
                matches.forEach { match ->
                    parseTimestamp(match)?.let { timestamp ->
                        add(IndexedLine((timestamp + offsetMs).coerceAtLeast(0), lyricText, size))
                    }
                }
            }
        }.sortedWith(compareBy<IndexedLine> { it.timestampMs }.thenBy { it.order })
            .map { TimedLyricLine(it.timestampMs, it.text) }
        return LyricsCandidate(source = source, rawText = rawText, timedLines = timed)
    }

    private fun parseTimestamp(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        val fraction = match.groupValues[3]
        val milliseconds = when (fraction.length) {
            0 -> 0
            1 -> fraction.toInt() * 100
            2 -> fraction.toInt() * 10
            else -> fraction.take(3).toInt()
        }
        return minutes * 60_000 + seconds * 1_000 + milliseconds
    }

    private data class IndexedLine(
        val timestampMs: Long,
        val text: String,
        val order: Int,
    )

    private companion object {
        val OFFSET = Regex("\\[offset\\s*:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
        val TIMESTAMP = Regex("\\[(\\d{1,4}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    }
}
