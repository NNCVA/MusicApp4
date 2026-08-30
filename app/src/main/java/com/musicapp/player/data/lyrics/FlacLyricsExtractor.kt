package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LrcParser
import com.musicapp.player.core.lyrics.LyricsCandidate
import com.musicapp.player.data.lyrics.LyricsIoUtils.readExactly
import com.musicapp.player.data.lyrics.LyricsIoUtils.readUInt24BE
import com.musicapp.player.data.lyrics.LyricsIoUtils.skipFully
import java.io.InputStream

internal class FlacLyricsExtractor(
    private val vorbisCommentParser: VorbisCommentParser = VorbisCommentParser(LrcParser()),
) {
    fun extract(input: InputStream): Pair<LyricsCandidate?, LyricsCandidate?> {
        // Read initial 4 bytes to check for FLAC magic
        val magic = input.readExactly(FLAC_MAGIC_SIZE) ?: return null to null

        // If prepended with ID3v2 tag, skip ID3v2 tag to locate FLAC header
        if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
            val restHeader = input.readExactly(6) ?: return null to null
            if ((0..3).any { restHeader[it + 2].toInt() and 0x80 != 0 }) return null to null
            val id3PayloadSize = ((restHeader[2].toInt() and 0x7F) shl 21) or
                ((restHeader[3].toInt() and 0x7F) shl 14) or
                ((restHeader[4].toInt() and 0x7F) shl 7) or
                (restHeader[5].toInt() and 0x7F)
            input.skipFully(id3PayloadSize.toLong())
            val flacMagic = input.readExactly(FLAC_MAGIC_SIZE) ?: return null to null
            if (!isFlacMagic(flacMagic)) return null to null
        } else if (!isFlacMagic(magic)) {
            return null to null
        }

        var resultSylt: LyricsCandidate? = null
        var resultUslt: LyricsCandidate? = null

        var blockIndex = 0
        while (blockIndex < MAX_METADATA_BLOCK_COUNT) {
            blockIndex++
            val blockHeader = input.readExactly(BLOCK_HEADER_SIZE) ?: break
            val isLast = (blockHeader[0].toInt() and 0x80) != 0
            val blockType = blockHeader[0].toInt() and 0x7F
            val blockLength = blockHeader.readUInt24BE(1)

            if (blockLength < 0 || blockLength > MAX_BLOCK_SIZE) {
                break
            }

            if (blockType == BLOCK_TYPE_VORBIS_COMMENT) {
                val commentData = input.readExactly(blockLength) ?: break
                val (sylt, uslt) = vorbisCommentParser.parse(commentData)
                if (sylt != null || uslt != null) {
                    resultSylt = sylt
                    resultUslt = uslt
                }
            } else {
                val skipped = input.skipFully(blockLength.toLong())
                if (skipped < blockLength.toLong()) break
            }

            if (isLast) break
        }

        return resultSylt to resultUslt
    }

    private fun isFlacMagic(bytes: ByteArray): Boolean =
        bytes.size == 4 &&
            bytes[0] == 'f'.code.toByte() &&
            bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() &&
            bytes[3] == 'C'.code.toByte()

    private companion object {
        const val FLAC_MAGIC_SIZE = 4
        const val BLOCK_HEADER_SIZE = 4
        const val BLOCK_TYPE_VORBIS_COMMENT = 4
        const val MAX_BLOCK_SIZE = 8 * 1024 * 1024
        const val MAX_METADATA_BLOCK_COUNT = 100
    }
}
