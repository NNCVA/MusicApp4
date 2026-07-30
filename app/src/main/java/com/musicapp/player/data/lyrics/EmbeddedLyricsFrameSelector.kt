package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LyricsCandidate

internal data class EmbeddedLyricsFrame(
    val id: String,
    val data: ByteArray,
)

internal class EmbeddedLyricsFrameSelector(
    private val parser: Id3LyricsFrameParser = Id3LyricsFrameParser(),
) {
    fun select(frames: List<EmbeddedLyricsFrame>): Pair<LyricsCandidate?, LyricsCandidate?> {
        val sylt = frames.mapNotNull { frame ->
            frame.takeIf { it.id == "SYLT" || it.id == "SLT" }?.let { parser.parseSylt(it.data) }
        }.preferSynchronized()
        val uslt = frames.mapNotNull { frame ->
            frame.takeIf { it.id == "USLT" || it.id == "ULT" }?.let { parser.parseUslt(it.data) }
        }.preferSynchronized()
        return sylt to uslt
    }

    private fun List<LyricsCandidate>.preferSynchronized(): LyricsCandidate? =
        firstOrNull { it.timedLines.isNotEmpty() } ?: firstOrNull()
}
