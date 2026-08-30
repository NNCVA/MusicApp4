package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LrcParser
import com.musicapp.player.core.lyrics.LyricsCandidate
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.LyricsTextDecoder
import com.musicapp.player.data.lyrics.LyricsIoUtils.readExactly
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt32BE
import com.musicapp.player.data.lyrics.LyricsIoUtils.skipFully
import java.io.InputStream

internal class Mp4LyricsExtractor(
    private val lrcParser: LrcParser = LrcParser(),
) {
    fun extract(input: InputStream): Pair<LyricsCandidate?, LyricsCandidate?> {
        val bounded = BoundedInputStream(input, MAX_SCAN_BYTES)
        val result = parseBoxes(bounded)
        return result
    }

    private fun parseBoxes(input: InputStream): Pair<LyricsCandidate?, LyricsCandidate?> {
        while (true) {
            val header = input.readExactly(BOX_HEADER_SIZE) ?: break
            val size = header.readUInt32BE(0)
            val type = header.copyOfRange(4, 8)

            val payloadSize: Long = when (size) {
                1L -> {
                    val largeSizeHeader = input.readExactly(8) ?: break
                    val high = largeSizeHeader.readUInt32BE(0)
                    val low = largeSizeHeader.readUInt32BE(4)
                    val largeSize = (high shl 32) or (low and 0xFFFFFFFFL)
                    if (largeSize < 16) break
                    largeSize - 16
                }
                0L -> {
                    // Extends to EOF or end of bounded stream
                    if (input is BoundedInputStream) input.remaining() else MAX_PAYLOAD_SIZE
                }
                in 8L..MAX_PAYLOAD_SIZE -> size - 8
                else -> break
            }

            if (payloadSize < 0 || payloadSize > MAX_PAYLOAD_SIZE) break

            when {
                isBoxType(type, MOOV_TYPE) || isBoxType(type, UDTA_TYPE) || isBoxType(type, ILST_TYPE) -> {
                    val containerStream = BoundedInputStream(input, payloadSize)
                    val result = parseBoxes(containerStream)
                    containerStream.skipRemaining()
                    if (result.first != null || result.second != null) return result
                }
                isBoxType(type, META_TYPE) -> {
                    if (payloadSize < 4) {
                        input.skipFully(payloadSize)
                        continue
                    }
                    // meta is a FullBox (1 byte version + 3 bytes flags)
                    input.skipFully(4)
                    val containerStream = BoundedInputStream(input, payloadSize - 4)
                    val result = parseBoxes(containerStream)
                    containerStream.skipRemaining()
                    if (result.first != null || result.second != null) return result
                }
                isLyricsBoxType(type) -> {
                    val lyricsStream = BoundedInputStream(input, payloadSize)
                    val result = parseLyricsItem(lyricsStream, payloadSize)
                    lyricsStream.skipRemaining()
                    if (result.first != null || result.second != null) return result
                }
                else -> {
                    input.skipFully(payloadSize)
                }
            }
        }
        return null to null
    }

    private fun parseLyricsItem(input: InputStream, totalSize: Long): Pair<LyricsCandidate?, LyricsCandidate?> {
        var remaining = totalSize
        while (remaining >= BOX_HEADER_SIZE) {
            val header = input.readExactly(BOX_HEADER_SIZE) ?: break
            remaining -= BOX_HEADER_SIZE
            val size = header.readUInt32BE(0)
            val type = header.copyOfRange(4, 8)

            val payloadSize = when (size) {
                1L -> {
                    val largeSizeHeader = input.readExactly(8) ?: break
                    remaining -= 8
                    val high = largeSizeHeader.readUInt32BE(0)
                    val low = largeSizeHeader.readUInt32BE(4)
                    ((high shl 32) or (low and 0xFFFFFFFFL)) - 16
                }
                0L -> remaining
                in 8L..MAX_PAYLOAD_SIZE -> size - 8
                else -> break
            }

            if (payloadSize < 0) break

            if (isBoxType(type, DATA_TYPE)) {
                // data box format: 4 bytes type/flags, 4 bytes locale, followed by text payload
                if (payloadSize < DATA_HEADER_SIZE) {
                    input.skipFully(payloadSize)
                    remaining -= payloadSize
                    continue
                }
                input.skipFully(DATA_HEADER_SIZE.toLong())
                val textLength = (payloadSize - DATA_HEADER_SIZE).toInt()
                val textBytes = input.readExactly(textLength) ?: break
                remaining -= payloadSize

                val text = LyricsTextDecoder.decode(textBytes).trim()
                if (text.isNotBlank()) {
                    val parsedSylt = lrcParser.parse(text, LyricsSource.EMBEDDED_SYLT)
                    val sylt = if (parsedSylt.timedLines.isNotEmpty()) parsedSylt else null
                    val uslt = LyricsCandidate(LyricsSource.EMBEDDED_USLT, parsedSylt.rawText ?: text)
                    return sylt to uslt
                }
            } else {
                input.skipFully(payloadSize)
                remaining -= payloadSize
            }
        }
        return null to null
    }

    private fun isBoxType(actual: ByteArray, expected: ByteArray): Boolean =
        actual.size == 4 && expected.size == 4 &&
            actual[0] == expected[0] &&
            actual[1] == expected[1] &&
            actual[2] == expected[2] &&
            actual[3] == expected[3]

    private fun isLyricsBoxType(type: ByteArray): Boolean =
        isBoxType(type, LYRICS_A9_TYPE) ||
            isBoxType(type, LYRICS_LYR_TYPE) ||
            isBoxType(type, LYRICS_CLYR_TYPE) ||
            isBoxType(type, LYRICS_FULL_TYPE)

    private class BoundedInputStream(
        private val delegate: InputStream,
        private var limit: Long,
    ) : InputStream() {
        override fun read(): Int {
            if (limit <= 0) return -1
            val result = delegate.read()
            if (result >= 0) limit--
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (limit <= 0) return -1
            val maxToRead = minOf(len.toLong(), limit).toInt()
            val count = delegate.read(b, off, maxToRead)
            if (count > 0) limit -= count
            return count
        }

        override fun skip(n: Long): Long {
            if (limit <= 0 || n <= 0) return 0
            val maxToSkip = minOf(n, limit)
            val count = delegate.skip(maxToSkip)
            if (count > 0) limit -= count
            return count
        }

        override fun available(): Int = minOf(delegate.available().toLong(), limit).toInt()

        fun remaining(): Long = limit

        fun skipRemaining() {
            if (limit > 0) {
                delegate.skipFully(limit)
                limit = 0
            }
        }
    }

    private companion object {
        const val BOX_HEADER_SIZE = 8
        const val DATA_HEADER_SIZE = 8
        const val MAX_PAYLOAD_SIZE = 64 * 1024 * 1024L
        const val MAX_SCAN_BYTES = 32 * 1024 * 1024L

        val MOOV_TYPE = byteArrayOf('m'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte())
        val UDTA_TYPE = byteArrayOf('u'.code.toByte(), 'd'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
        val META_TYPE = byteArrayOf('m'.code.toByte(), 'e'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
        val ILST_TYPE = byteArrayOf('i'.code.toByte(), 'l'.code.toByte(), 's'.code.toByte(), 't'.code.toByte())
        val DATA_TYPE = byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())

        val LYRICS_A9_TYPE = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val LYRICS_LYR_TYPE = byteArrayOf('l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte(), ' '.code.toByte())
        val LYRICS_CLYR_TYPE = byteArrayOf('c'.code.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val LYRICS_FULL_TYPE = byteArrayOf('l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte(), 'i'.code.toByte())
    }
}
