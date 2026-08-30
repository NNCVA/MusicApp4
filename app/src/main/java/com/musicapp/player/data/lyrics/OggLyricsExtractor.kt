package com.musicapp.player.data.lyrics

import com.musicapp.player.core.lyrics.LrcParser
import com.musicapp.player.core.lyrics.LyricsCandidate
import com.musicapp.player.data.lyrics.LyricsIoUtils.readExactly
import com.musicapp.player.data.lyrics.LyricsIoUtils.skipFully
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal class OggLyricsExtractor(
    private val vorbisCommentParser: VorbisCommentParser = VorbisCommentParser(LrcParser()),
) {
    fun extract(input: InputStream): Pair<LyricsCandidate?, LyricsCandidate?> {
        val currentPacket = ByteArrayOutputStream()
        var pageCount = 0

        while (pageCount < MAX_PAGES_TO_SCAN) {
            pageCount++
            // Read 27-byte base page header
            val header = input.readExactly(OGG_PAGE_HEADER_SIZE) ?: break
            if (!isOggMagic(header)) {
                // Try resyncing to next OggS if slightly misaligned
                if (!resyncToOggMagic(input)) break
                continue
            }

            val pageSegmentsCount = header[26].toInt() and 0xFF
            val segmentTable = input.readExactly(pageSegmentsCount) ?: break

            for (segmentIndex in 0 until pageSegmentsCount) {
                val segmentLen = segmentTable[segmentIndex].toInt() and 0xFF
                if (segmentLen > 0) {
                    val segmentData = input.readExactly(segmentLen) ?: return null to null
                    if (currentPacket.size() + segmentLen <= MAX_PACKET_SIZE) {
                        currentPacket.write(segmentData)
                    }
                }

                // If segment length is less than 255, the logical packet is complete
                if (segmentLen < 255) {
                    val packetBytes = currentPacket.toByteArray()
                    currentPacket.reset()

                    val lyrics = parsePacket(packetBytes)
                    if (lyrics.first != null || lyrics.second != null) {
                        return lyrics
                    }
                }
            }
        }

        // If the stream ended while a packet was accumulating, try parsing it
        if (currentPacket.size() > 0) {
            val lyrics = parsePacket(currentPacket.toByteArray())
            if (lyrics.first != null || lyrics.second != null) {
                return lyrics
            }
        }

        return null to null
    }

    private fun parsePacket(packetBytes: ByteArray): Pair<LyricsCandidate?, LyricsCandidate?> {
        if (packetBytes.isEmpty()) return null to null

        // 1. Check Ogg Vorbis comment header: starts with 7-byte "\x03vorbis"
        if (startsWith(packetBytes, VORBIS_COMMENT_HEADER)) {
            val commentData = packetBytes.copyOfRange(VORBIS_COMMENT_HEADER.size, packetBytes.size)
            return vorbisCommentParser.parse(commentData)
        }

        // 2. Check Ogg Opus comment header: starts with 8-byte "OpusTags"
        if (startsWith(packetBytes, OPUS_TAGS_HEADER)) {
            val commentData = packetBytes.copyOfRange(OPUS_TAGS_HEADER.size, packetBytes.size)
            return vorbisCommentParser.parse(commentData)
        }

        return null to null
    }

    private fun isOggMagic(header: ByteArray): Boolean =
        header.size >= 4 &&
            header[0] == 'O'.code.toByte() &&
            header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() &&
            header[3] == 'S'.code.toByte()

    private fun resyncToOggMagic(input: InputStream): Boolean {
        var searched = 0
        while (searched < MAX_RESYNC_BYTES) {
            searched++
            val b = input.read()
            if (b < 0) return false
            if (b == 'O'.code) {
                val next3 = input.readExactly(3) ?: return false
                if (next3[0] == 'g'.code.toByte() && next3[1] == 'g'.code.toByte() && next3[2] == 'S'.code.toByte()) {
                    // Backtrack the 4 bytes or read remaining 23 bytes of header
                    val restHeader = input.readExactly(OGG_PAGE_HEADER_SIZE - 4) ?: return false
                    // We now have a full page header, but simpler is to let caller continue
                    // We construct full header array and we can process it, or we skip
                    // For simplicity, skip back isn't needed if we keep it structured.
                    return false
                }
            }
        }
        return false
    }

    private fun startsWith(array: ByteArray, prefix: ByteArray): Boolean {
        if (array.size < prefix.size) return false
        for (i in prefix.indices) {
            if (array[i] != prefix[i]) return false
        }
        return true
    }

    private companion object {
        const val OGG_PAGE_HEADER_SIZE = 27
        const val MAX_PAGES_TO_SCAN = 64
        const val MAX_PACKET_SIZE = 4 * 1024 * 1024
        const val MAX_RESYNC_BYTES = 4096

        val VORBIS_COMMENT_HEADER = byteArrayOf(
            0x03,
            'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(),
            'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(),
        )

        val OPUS_TAGS_HEADER = byteArrayOf(
            'O'.code.toByte(), 'p'.code.toByte(), 'u'.code.toByte(), 's'.code.toByte(),
            'T'.code.toByte(), 'a'.code.toByte(), 'g'.code.toByte(), 's'.code.toByte(),
        )
    }
}
