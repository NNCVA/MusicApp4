package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LrcParser
import com.musicapp.player.core.lyrics.LyricsCandidate
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.TimedLyricLine
import java.nio.charset.Charset

internal class Id3LyricsFrameParser(
    private val lrcParser: LrcParser = LrcParser(),
) {
    fun parseSylt(data: ByteArray): LyricsCandidate? {
        if (data.size < SYLT_HEADER_SIZE) return null
        val textCodec = Id3TextCodec.from(data[0].toInt()) ?: return null
        val timestampFormat = data[4].toInt() and 0xFF
        var cursor = textCodec.endOfString(data, SYLT_HEADER_SIZE) ?: return null
        cursor += textCodec.terminatorSize
        val rawLines = mutableListOf<String>()
        val timedLines = mutableListOf<TimedLyricLine>()
        while (cursor < data.size) {
            val textEnd = textCodec.endOfString(data, cursor) ?: break
            val text = textCodec.decode(data, cursor, textEnd).trim()
            cursor = textEnd + textCodec.terminatorSize
            if (cursor + TIMESTAMP_SIZE > data.size) break
            val timestamp = data.readUnsignedInt(cursor)
            cursor += TIMESTAMP_SIZE
            if (text.isNotEmpty()) {
                rawLines += text
                if (timestampFormat == MILLISECOND_TIMESTAMP_FORMAT) {
                    timedLines += TimedLyricLine(timestamp.coerceAtMost(Long.MAX_VALUE), text)
                }
            }
        }
        val rawText = rawLines.joinToString("\n").takeIf(String::isNotBlank)
        if (rawText == null && timedLines.isEmpty()) return null
        return LyricsCandidate(LyricsSource.EMBEDDED_SYLT, rawText, timedLines)
    }

    fun parseUslt(data: ByteArray): LyricsCandidate? {
        if (data.size < USLT_HEADER_SIZE) return null
        val textCodec = Id3TextCodec.from(data[0].toInt()) ?: return null
        val descriptorEnd = textCodec.endOfString(data, USLT_HEADER_SIZE) ?: return null
        val lyricsStart = descriptorEnd + textCodec.terminatorSize
        if (lyricsStart > data.size) return null
        val text = textCodec.decode(data, lyricsStart, data.size).trimEnd('\u0000')
        if (text.isBlank()) return null
        return lrcParser.parse(text, LyricsSource.EMBEDDED_USLT)
    }

    private fun ByteArray.readUnsignedInt(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)

    private enum class Id3TextCodec(
        val charset: Charset,
        val terminatorSize: Int,
    ) {
        ISO_8859_1(Charsets.ISO_8859_1, 1),
        UTF_16(Charsets.UTF_16, 2),
        UTF_16_BE(Charsets.UTF_16BE, 2),
        UTF_8(Charsets.UTF_8, 1);

        fun endOfString(data: ByteArray, start: Int): Int? {
            if (start > data.size) return null
            if (terminatorSize == 1) {
                for (index in start until data.size) if (data[index] == 0.toByte()) return index
                return data.size
            }
            var index = start
            while (index + 1 < data.size) {
                if (data[index] == 0.toByte() && data[index + 1] == 0.toByte()) return index
                index += 2
            }
            return data.size - (data.size - start) % 2
        }

        fun decode(data: ByteArray, start: Int, end: Int): String =
            if (end <= start) "" else String(data, start, end - start, charset).trimStart('\uFEFF')

        companion object {
            fun from(encoded: Int): Id3TextCodec? = entries.getOrNull(encoded and 0xFF)
        }
    }

    private companion object {
        const val SYLT_HEADER_SIZE = 6
        const val USLT_HEADER_SIZE = 4
        const val TIMESTAMP_SIZE = 4
        const val MILLISECOND_TIMESTAMP_FORMAT = 2
    }
}
