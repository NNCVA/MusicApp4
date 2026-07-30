package com.musicapp.player.core.lyrics

data class LyricsSyncState(
    val isSynchronized: Boolean = false,
    val activeLineIndex: Int? = null,
    val previousLine: String = "",
    val currentLine: String = "",
    val nextLine: String = "",
)

class LyricsSynchronizer {
    fun synchronize(lyrics: ResolvedLyrics, positionMs: Long): LyricsSyncState {
        if (lyrics !is SynchronizedLyrics) return LyricsSyncState()
        val activeIndex = findActiveIndex(lyrics.lines, positionMs.coerceAtLeast(0))
        return LyricsSyncState(
            isSynchronized = true,
            activeLineIndex = activeIndex.takeIf { it >= 0 },
            previousLine = lyrics.lines.getOrNull(activeIndex - 1)?.text.orEmpty(),
            currentLine = lyrics.lines.getOrNull(activeIndex)?.text.orEmpty(),
            nextLine = lyrics.lines.getOrNull(activeIndex + 1)?.text.orEmpty(),
        )
    }

    fun seekPositionMs(lyrics: ResolvedLyrics, lineIndex: Int): Long? =
        (lyrics as? SynchronizedLyrics)?.lines?.getOrNull(lineIndex)?.timestampMs

    private fun findActiveIndex(lines: List<TimedLyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].timestampMs <= positionMs) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }
}

class LyricsAutoCenterController(
    private val resumeDelayMs: Long = DEFAULT_RESUME_DELAY_MS,
) {
    private var resumeAtMs = Long.MIN_VALUE

    init {
        require(resumeDelayMs >= 0) { "resumeDelayMs must not be negative" }
    }

    fun onManualScroll(nowMs: Long) {
        require(nowMs >= 0) { "nowMs must not be negative" }
        resumeAtMs = if (nowMs > Long.MAX_VALUE - resumeDelayMs) Long.MAX_VALUE else nowMs + resumeDelayMs
    }

    fun returnToCurrentLine() {
        resumeAtMs = Long.MIN_VALUE
    }

    fun shouldAutoCenter(nowMs: Long): Boolean {
        require(nowMs >= 0) { "nowMs must not be negative" }
        return nowMs >= resumeAtMs
    }

    companion object {
        const val DEFAULT_RESUME_DELAY_MS = 5_000L
    }
}
